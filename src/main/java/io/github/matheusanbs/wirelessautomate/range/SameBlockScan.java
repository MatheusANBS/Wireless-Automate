package io.github.matheusanbs.wirelessautomate.range;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * A varredura do Ctrl + clique do Vinculador, sem dono: o grupo de posições de um bloco que se tocam com a clicada
 * (pela face, pela aresta ou pela ponta), em largura a partir dela, da mais perto da clicada para a mais longe. Quem
 * chama é o roteador ({@link SameBlockLinks}) ou a Doca ({@code dock/DockSameBlock}), para vincular (com o corte do
 * raio de quem vincula) e para desvincular (sem o corte).
 *
 * <p>Custo, só no clique: até 26 leituras por posição do grupo (os vizinhos de cada uma), só em chunks carregados
 * ({@code getChunkNow}, com o último chunk guardado: o grupo fica quase sempre no mesmo), lendo só o estado pela seção
 * (sem block entity, sem capability). Para quando já há candidatas para as vagas ou quando lê {@link #MAX_READS}
 * vizinhos (o aviso diz "o resto do grupo não foi procurado").
 */
public final class SameBlockScan {
    /**
     * Teto de leituras de vizinho por clique, 131.072: um grupo de umas 5.000 posições. Uma posição pulada pela altura,
     * pelo raio ou por um chunk descarregado também conta.
     */
    public static final int MAX_READS = 32 * 4096;
    /** Sem o corte do raio (o desvincular). */
    public static final int NO_RADIUS = -1;
    /** Os 26 vizinhos (dx, dy, dz em sequência), numa ordem fixa: a ordem do resultado é sempre a mesma. */
    private static final int[] NEIGHBORS = neighbors();

    private SameBlockScan() {
    }

    /**
     * O que a varredura achou.
     *
     * @param candidates      as que podem entrar, na ordem da busca (da mais perto da clicada)
     * @param linkedPositions as posições do grupo que estão em {@code linked}, na ordem da busca
     * @param taken           as do grupo que o {@code taken} da chamada recusou (de outro dono)
     * @param complete        o grupo inteiro foi lido (sem parar cedo nem no teto)
     * @param capped          parou no {@link #MAX_READS}
     * @param reads           quantas leituras de vizinho fez
     */
    public record Scan(LongArrayList candidates, LongArrayList linkedPositions, int taken, int protectedCount,
            int unloadedChunks, boolean complete, boolean capped, int reads) {
        /** As do grupo que já estavam em {@code linked}. */
        public int already() {
            return linkedPositions.size();
        }
    }

    /**
     * O grupo de {@code block} que se toca com {@code start} (os 26 vizinhos), em largura a partir dele; só dentro do
     * cubo de Chebyshev {@code radius} em volta de {@code origin} ({@link #NO_RADIUS}: sem corte) e na altura do
     * mundo; só em chunks carregados. O {@code start} é do bloco (ele o define) e entra sempre, primeiro. Cada posição
     * do grupo: em {@code excluded}, nada; em {@code linked}, vai para {@code linkedPositions}; com
     * {@code taken.test(pos)} (outro dono), conta em {@code taken}; sem {@code mayInteract}, conta em
     * {@code protectedCount}; senão é candidata. O grupo passa por todas elas. Para quando já há {@code needed}
     * candidatas ou no {@link #MAX_READS}.
     */
    public static Scan scan(ServerPlayer player, BlockPos start, BlockPos origin, int radius, Block block,
            LongSet linked, LongSet excluded, LongPredicate taken, int needed) {
        ServerLevel level = player.serverLevel();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        LongArrayList candidates = new LongArrayList();
        LongArrayList linkedPositions = new LongArrayList();
        int[] counts = new int[2]; // de outro dono, protegidas
        LongOpenHashSet visited = new LongOpenHashSet();
        Chunks chunks = new Chunks(level);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long startKey = start.asLong();
        visited.add(startKey);
        queue.enqueue(startKey);
        classify(player, level, startKey, cursor, linked, excluded, taken, candidates, linkedPositions, counts);

        int reads = 0;
        boolean complete = true;
        boolean capped = false;
        search:
        while (!queue.isEmpty()) {
            if (candidates.size() >= needed) {
                complete = false;
                break;
            }
            long pos = queue.dequeueLong();
            int px = BlockPos.getX(pos);
            int py = BlockPos.getY(pos);
            int pz = BlockPos.getZ(pos);
            for (int i = 0; i < NEIGHBORS.length; i += 3) {
                if (++reads > MAX_READS) {
                    complete = false;
                    capped = true;
                    break search;
                }
                int x = px + NEIGHBORS[i];
                int y = py + NEIGHBORS[i + 1];
                int z = pz + NEIGHBORS[i + 2];
                long key = BlockPos.asLong(x, y, z);
                if (visited.contains(key) || y < minY || y > maxY || radius != NO_RADIUS
                        && RangeRules.chebyshev(origin.getX(), origin.getY(), origin.getZ(), x, y, z) > radius
                        || !chunks.is(x, y, z, block)) {
                    continue;
                }
                visited.add(key);
                queue.enqueue(key);
                classify(player, level, key, cursor, linked, excluded, taken, candidates, linkedPositions, counts);
                if (candidates.size() >= needed) {
                    complete = false;
                    break search;
                }
            }
        }
        return new Scan(candidates, linkedPositions, counts[0], counts[1], chunks.unloaded.size(), complete, capped,
                Math.min(reads, MAX_READS));
    }

    /** Põe a posição do grupo onde ela vai: nada, vinculada, de outro dono, protegida ou candidata. */
    private static void classify(ServerPlayer player, ServerLevel level, long key, BlockPos.MutableBlockPos cursor,
            LongSet linked, LongSet excluded, LongPredicate taken, LongArrayList candidates,
            LongArrayList linkedPositions, int[] counts) {
        if (excluded.contains(key)) {
            return;
        }
        if (linked.contains(key)) {
            linkedPositions.add(key);
        } else if (taken.test(key)) {
            counts[0]++;
        } else if (!level.mayInteract(player, cursor.set(key))) {
            counts[1]++;
        } else {
            candidates.add(key);
        }
    }

    /** O último chunk lido (o grupo fica quase sempre no mesmo) e os descarregados achados, cada um contado uma vez. */
    private static final class Chunks {
        private final ServerLevel level;
        private final LongOpenHashSet unloaded = new LongOpenHashSet();
        private long key = ChunkPos.INVALID_CHUNK_POS;
        private @Nullable LevelChunk chunk;

        Chunks(ServerLevel level) {
            this.level = level;
        }

        /** O estado em (x, y, z) é de {@code block}, lido pela seção; num chunk descarregado, não (e o chunk conta). */
        boolean is(int x, int y, int z, Block block) {
            long next = ChunkPos.asLong(x >> 4, z >> 4);
            if (next != key) {
                key = next;
                chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            }
            if (chunk == null) {
                unloaded.add(key);
                return false;
            }
            return chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x & 15, y & 15, z & 15).is(block);
        }
    }

    /** Os 26 deslocamentos em volta de uma posição: y, depois z, depois x, de −1 a 1, sem o (0, 0, 0). */
    private static int[] neighbors() {
        int[] offsets = new int[26 * 3];
        int i = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        offsets[i++] = dx;
                        offsets[i++] = dy;
                        offsets[i++] = dz;
                    }
                }
            }
        }
        return offsets;
    }
}
