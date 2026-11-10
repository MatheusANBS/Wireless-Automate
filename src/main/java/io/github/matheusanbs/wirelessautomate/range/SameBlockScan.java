package io.github.matheusanbs.wirelessautomate.range;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * A varredura do Ctrl + clique do Vinculador, sem dono: as posições de um bloco no cubo de um raio em volta de uma
 * origem (o roteador em {@link SameBlockLinks}, a Doca em {@code dock/DockSameBlock}), da mais perto para a mais
 * longe (Chebyshev; empate pela posição).
 *
 * <p>Custo, só no clique: só os chunks carregados ({@code getChunkNow}), seção a seção, pulando a seção cuja paleta
 * não tem o bloco ({@code maybeHas}) e lendo só o estado de cada posição (sem block entity, sem capability). As seções
 * vão da mais perto para a mais longe; a varredura para quando já há candidatas para as vagas e a próxima seção não tem
 * como ter uma mais perto, ou quando lê {@link #MAX_READS} posições (o aviso diz "mais longe não foi procurado").
 */
public final class SameBlockScan {
    /**
     * Teto de posições lidas por clique: 32 seções inteiras. O cubo do raio chega a milhões de
     * posições (129³ no raio 64 do roteador, 193³ no 96 da Doca Ultimate), mas só as seções que têm o bloco na paleta
     * são lidas.
     */
    public static final int MAX_READS = 32 * 4096;

    private SameBlockScan() {
    }

    /**
     * O que a varredura achou, já sem as vinculadas, as excluídas e as protegidas pelo {@code mayInteract}.
     *
     * @param already  as posições do bloco que já estavam no conjunto {@code linked}
     * @param complete o cubo inteiro foi lido (sem parar cedo nem no teto)
     * @param capped   parou no {@link #MAX_READS}
     */
    public record Scan(LongArrayList candidates, int already, int protectedCount, int unloadedChunks, boolean complete,
            boolean capped, int reads) {
    }

    /** O cubo do raio, cortado na altura do mundo; limites inclusivos. */
    private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    /** Uma seção que cruza o cubo e tem o bloco na paleta, com a menor distância de Chebyshev dela à origem. */
    private record Section(LevelChunkSection section, int x, int y, int z, int distance) {
    }

    /**
     * As posições de {@code block} no cubo de raio {@code radius} em volta de {@code origin} que podem entrar, em ordem
     * de distância (empate pela posição). As de {@code linked} contam em {@link Scan#already}; as de {@code excluded}
     * ficam de fora sem contar. Para quando já há {@code needed} candidatas e nenhuma seção que falta tem como ter uma
     * mais perto que a {@code needed}-ésima.
     */
    public static Scan scan(ServerPlayer player, BlockPos origin, int radius, Block block, LongSet linked,
            LongSet excluded, int needed) {
        ServerLevel level = player.serverLevel();
        Box box = new Box(origin.getX() - radius, Math.max(level.getMinBuildHeight(), origin.getY() - radius),
                origin.getZ() - radius, origin.getX() + radius,
                Math.min(level.getMaxBuildHeight() - 1, origin.getY() + radius), origin.getZ() + radius);
        int[] unloaded = new int[1];
        List<Section> sections = sections(level, origin, box, block, unloaded);

        LongArrayList candidates = new LongArrayList();
        // quantas candidatas há em cada distância: a needed-ésima mais perto sem ordenar a lista a cada seção
        int[] atDistance = new int[radius + 1];
        int already = 0;
        int protectedCount = 0;
        int reads = 0;
        boolean complete = true;
        boolean capped = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        scan:
        for (Section entry : sections) {
            if (candidates.size() >= needed && entry.distance() > neededDistance(atDistance, needed)) {
                complete = false;
                break;
            }
            int x0 = Math.max(box.minX(), SectionPos.sectionToBlockCoord(entry.x()));
            int x1 = Math.min(box.maxX(), SectionPos.sectionToBlockCoord(entry.x(), 15));
            int y0 = Math.max(box.minY(), SectionPos.sectionToBlockCoord(entry.y()));
            int y1 = Math.min(box.maxY(), SectionPos.sectionToBlockCoord(entry.y(), 15));
            int z0 = Math.max(box.minZ(), SectionPos.sectionToBlockCoord(entry.z()));
            int z1 = Math.min(box.maxZ(), SectionPos.sectionToBlockCoord(entry.z(), 15));
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        if (++reads > MAX_READS) {
                            complete = false;
                            capped = true;
                            break scan;
                        }
                        if (!entry.section().getBlockState(x & 15, y & 15, z & 15).is(block)) {
                            continue;
                        }
                        long key = BlockPos.asLong(x, y, z);
                        if (excluded.contains(key)) {
                            continue;
                        }
                        if (linked.contains(key)) {
                            already++;
                        } else if (!level.mayInteract(player, cursor.set(x, y, z))) {
                            protectedCount++;
                        } else {
                            candidates.add(key);
                            atDistance[RangeRules.chebyshev(origin.getX(), origin.getY(), origin.getZ(), x, y, z)]++;
                        }
                    }
                }
            }
        }
        sortByDistance(candidates, origin);
        return new Scan(candidates, already, protectedCount, unloaded[0], complete, capped, Math.min(reads, MAX_READS));
    }

    /**
     * As seções dos chunks carregados que cruzam {@code box} e têm {@code block} na paleta, da mais perto de
     * {@code origin} para a mais longe; {@code unloaded[0]} conta os chunks descarregados (que não são lidos).
     */
    private static List<Section> sections(ServerLevel level, BlockPos origin, Box box, Block block, int[] unloaded) {
        List<Section> sections = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ());
                    cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    unloaded[0]++;
                    continue;
                }
                for (int sy = SectionPos.blockToSectionCoord(box.minY()); sy <= SectionPos.blockToSectionCoord(box.maxY());
                        sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(block))) {
                        continue;
                    }
                    sections.add(new Section(section, cx, sy, cz, sectionDistance(origin, cx, sy, cz)));
                }
            }
        }
        sections.sort(Comparator.comparingInt(Section::distance));
        return sections;
    }

    /** A distância da {@code needed}-ésima candidata mais perto, pela contagem por distância. */
    private static int neededDistance(int[] atDistance, int needed) {
        int seen = 0;
        for (int d = 0; d < atDistance.length; d++) {
            seen += atDistance[d];
            if (seen >= needed) {
                return d;
            }
        }
        return atDistance.length;
    }

    /** A menor distância de Chebyshev de {@code origin} a algum bloco da seção. */
    private static int sectionDistance(BlockPos origin, int sx, int sy, int sz) {
        return Math.max(axisDistance(origin.getX(), sx), Math.max(axisDistance(origin.getY(), sy),
                axisDistance(origin.getZ(), sz)));
    }

    private static int axisDistance(int coord, int section) {
        int min = SectionPos.sectionToBlockCoord(section);
        int max = min + 15;
        return coord < min ? min - coord : coord > max ? coord - max : 0;
    }

    private static int distance(BlockPos origin, long pos) {
        return RangeRules.chebyshev(origin.getX(), origin.getY(), origin.getZ(), BlockPos.getX(pos),
                BlockPos.getY(pos), BlockPos.getZ(pos));
    }

    private static void sortByDistance(LongArrayList positions, BlockPos origin) {
        positions.sort((a, b) -> {
            int byDistance = Integer.compare(distance(origin, a), distance(origin, b));
            return byDistance != 0 ? byDistance : Long.compare(a, b);
        });
    }
}
