package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks.Result;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks.Status;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

/**
 * O Ctrl + clique do Vinculador no modo Vincular ({@code LinkSameBlockPayload}). Com a clicada já vinculada ao
 * roteador escolhido, desvincula todas as vinculadas do mesmo bloco ({@link #unlink}). Senão, vincula ao
 * roteador escolhido as máquinas do mesmo bloco (o mesmo id de registro) da clicada que estão no raio do tier, da mais perto do roteador para
 * a mais longe (Chebyshev; empate pela posição), até o limite. O servidor não sabe do Ctrl, então o cliente cancela o
 * uso normal e manda o pacote; aqui se refazem todas as conferências do clique (item, modo, distância do jogador ao
 * bloco, roteador escolhido, {@code mayBuild}/{@code mayInteract}, as recusas do {@link RangeLinks}).
 *
 * <p>Custo, só no clique: o cubo do raio em volta do roteador, só dos chunks carregados ({@code getChunkNow}), seção a
 * seção, pulando a seção cuja paleta não tem o bloco ({@code maybeHas}) e lendo só o estado de cada posição (sem block
 * entity, sem capability). As seções vão da mais perto para a mais longe; a varredura para quando já há candidatas
 * para as vagas e a próxima seção não tem como ter uma mais perto, ou quando lê {@link #MAX_READS} posições (o aviso
 * diz "mais longe não foi procurado"). A capability ({@link RangeLinks#isMachine}) e o evento de clique das proteções
 * ({@link RangeLinks#clickAllowed}) só são conferidos nas que de fato seriam vinculadas.
 */
public final class SameBlockLinks {
    private static final String KEY = "item.wirelessautomate.linker.range.same.";
    /**
     * Teto de posições lidas por clique: 32 seções inteiras, menos de 1 ms. O raio do tier vai até 64 (o cubo tem
     * 129³ ≈ 2,1 milhões de posições), mas só as seções que têm o bloco na paleta são lidas.
     */
    public static final int MAX_READS = 32 * 4096;
    /** O descanso do Vinculador depois de um Ctrl + clique aceito pelo servidor (meio segundo). */
    public static final int COOLDOWN_TICKS = 10;

    private SameBlockLinks() {
    }

    /** O que a varredura achou, já sem as vinculadas, a presa e as protegidas pelo {@code mayInteract}. */
    record Scan(LongArrayList candidates, int already, int protectedCount, int unloadedChunks, boolean complete,
            boolean capped, int reads) {
    }

    /** O cubo do raio, cortado na altura do mundo; limites inclusivos. */
    private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    /** Uma seção que cruza o cubo e tem o bloco na paleta, com a menor distância de Chebyshev dela ao roteador. */
    private record Section(LevelChunkSection section, int x, int y, int z, int distance) {
    }

    /**
     * O pacote do Ctrl + clique em {@code pos}. Sem o Vinculador no modo Vincular na mão principal (ou com uma Tela
     * de Sinal escolhida nele, como o cliente confere), com o bloco
     * descarregado ou longe da mão do jogador, ou com o Vinculador no descanso ({@link #COOLDOWN_TICKS}), ignora
     * (devolve {@code null}, sem mensagem: o cliente não pediria).
     */
    public static @Nullable Result link(@Nullable ServerPlayer player, BlockPos pos) {
        if (player == null) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        ServerLevel level = player.serverLevel();
        if (!(stack.getItem() instanceof LinkerItem) || LinkerItem.mode(stack) != LinkerMode.LINK
                || LinkerItem.tv(stack) != null || !level.isLoaded(pos) || !player.canInteractWithBlock(pos, 1.0)
                || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return null;
        }
        // o descanso segura um cliente que mande o pacote em rajada: a varredura custa até MAX_READS posições
        player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN_TICKS);
        RouterBlockEntity router = RangeLinks.chosen(player, stack);
        if (router == null) {
            return RangeLinks.missing(player, stack);
        }
        if (router.remoteMachines().index(pos) >= 0) {
            return unlink(player, router, pos);
        }
        if (!RangeLinks.mayEdit(player, pos) || !RangeLinks.mayEdit(player, router.getBlockPos())) {
            return RangeLinks.refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        // a presa clicada serve de modelo: ela fica de fora e as outras do mesmo bloco entram
        LinkRefusal refusal = RangeLinks.machineRefusal(level, router, pos, LoadedTypes.LIST);
        if (refusal == LinkRefusal.ATTACHED) {
            refusal = LinkRefusal.NONE;
        }
        if (refusal == LinkRefusal.NONE) {
            refusal = RangeLinks.upgradeRefusal(router);
        }
        int distance = RangeLinks.distance(router, pos);
        if (refusal == LinkRefusal.NONE && distance > RangeLinks.radius(router)) {
            refusal = LinkRefusal.OUT_OF_RANGE;
        }
        if (refusal != LinkRefusal.NONE) {
            return RangeLinks.refuse(player, Result.refused(refusal), router, distance);
        }

        Block block = level.getBlockState(pos).getBlock();
        int room = RangeLinks.max(router) - RangeLinks.used(router);
        // uma além das vagas: o "limite do tier" só aparece quando há de fato uma que ficou de fora
        Scan scan = scan(player, router, block, Math.max(1, room + 1));
        LongArrayList candidates = scan.candidates();
        if (candidates.isEmpty() && scan.protectedCount() > 0) {
            return RangeLinks.refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        if (!candidates.isEmpty() && room <= 0) {
            return RangeLinks.refuse(player, Result.refused(LinkRefusal.LIMIT), router, 0);
        }

        int[] counts = new int[3]; // vinculadas, além do limite, protegidas pelo evento de clique
        router.batchChanges(() -> {
            for (int i = 0; i < candidates.size(); i++) {
                BlockPos machine = BlockPos.of(candidates.getLong(i));
                if (counts[0] >= room) {
                    counts[1]++;
                } else if (!RangeLinks.isMachine(level, machine, LoadedTypes.LIST)) {
                    // o mesmo bloco sem capability nesta posição (o estado conta): não ocupa vaga
                    continue;
                } else if (!RangeLinks.clickAllowed(player, InteractionHand.MAIN_HAND, machine)) {
                    counts[2]++;
                } else if (router.linkMachine(machine)) {
                    counts[0]++;
                }
            }
        });
        if (counts[0] == 0 && counts[2] > 0) {
            // todas as achadas recusadas pelo evento de clique das proteções
            return RangeLinks.refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        Result result = new Result(Status.SAME_BLOCK_DONE, LinkRefusal.NONE, counts[0],
                scan.already(), 0, counts[1], scan.protectedCount() + counts[2], scan.unloadedChunks());
        boolean limit = counts[1] > 0;
        player.displayClientMessage(message(block, result, candidates.size(), scan, limit), true);
        if (result.linked() > 0) {
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.2F);
        }
        return result;
    }

    /**
     * O Ctrl + clique numa máquina já vinculada: tira do roteador todas as máquinas vinculadas cujo grupo é do mesmo
     * bloco da clicada (a presa fica). Como o desvincular de um clique, basta poder
     * mexer no roteador, não exige o upgrade valendo e não lê mundo além do bloco clicado: os cartões dos grupos que
     * esvaziam vão para o jogador.
     */
    private static Result unlink(ServerPlayer player, RouterBlockEntity router, BlockPos pos) {
        if (!RangeLinks.mayEdit(player, router.getBlockPos())) {
            return RangeLinks.refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        ServerLevel level = player.serverLevel();
        Block block = level.getBlockState(pos).getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        RemoteMachines remotes = router.remoteMachines();
        List<BlockPos> same = new ArrayList<>();
        for (RemoteMachine machine : remotes.machines()) {
            MachineGroup group = remotes.group(machine.group());
            if (group != null && group.block().equals(id)) {
                same.add(machine.pos());
            }
        }
        int[] cards = new int[1];
        router.batchChanges(() -> {
            for (BlockPos machine : same) {
                cards[0] += Math.max(0, router.unlinkMachine(remotes.index(machine), player));
            }
        });
        Component single = RouterBlock.machineName(block);
        MutableComponent text = same.isEmpty()
                ? Component.translatable(KEY + "unlinked.none", single, RangeLinks.routerName(router))
                : Component.translatable(KEY + (same.size() == 1 ? "unlinked.one" : "unlinked"), same.size(),
                        same.size() == 1 ? single : plural(block, single), RangeLinks.routerName(router),
                        RangeLinks.used(router), RangeLinks.max(router));
        player.displayClientMessage(RangeLinks.withCards(text, cards[0]), true);
        if (!same.isEmpty()) {
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
        }
        return new Result(Status.SAME_BLOCK_UNLINKED, LinkRefusal.NONE, same.size(), 0, 0, 0, 0,
                0);
    }

    /**
     * As posições de {@code block} no cubo do raio do roteador que podem entrar, em ordem de distância (empate pela
     * posição). Para quando já há {@code needed} candidatas e nenhuma seção que falta tem como ter uma mais perto que a
     * {@code needed}-ésima.
     */
    static Scan scan(ServerPlayer player, RouterBlockEntity router, Block block, int needed) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = router.getBlockPos();
        int radius = RangeLinks.radius(router);
        Box box = new Box(origin.getX() - radius, Math.max(level.getMinBuildHeight(), origin.getY() - radius),
                origin.getZ() - radius, origin.getX() + radius,
                Math.min(level.getMaxBuildHeight() - 1, origin.getY() + radius), origin.getZ() + radius);
        int[] unloaded = new int[1];
        List<Section> sections = sections(level, origin, box, block, unloaded);

        LongOpenHashSet linked = new LongOpenHashSet();
        for (RemoteMachine machine : router.remoteMachines().machines()) {
            linked.add(machine.pos().asLong());
        }
        long attached = router.machinePos().asLong();
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
                        if (key == attached) {
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

    // ------------------------------------------------------------------ mensagens

    /**
     * {@code Vinculou 7 Fornalhas}, {@code Vinculou 32 de 47 Fornalhas · limite do tier} ou, sem nenhuma nova,
     * {@code Fornalha: todas as do raio já estão vinculadas} (com a varredura inteira) ou {@code Fornalha: nenhuma nova
     * nas posições lidas} (parada no teto); mais as protegidas, os chunks descarregados e o teto.
     */
    static MutableComponent message(Block block, Result result, int found, Scan scan, boolean limit) {
        Component single = RouterBlock.machineName(block);
        MutableComponent text;
        int linked = result.linked();
        if (found == 0) {
            text = Component.translatable(KEY + (scan.capped() ? "none.capped" : "none"), single);
        } else {
            boolean of = limit && scan.complete() && found > linked;
            Component name = linked == 1 && !of ? single : plural(block, single);
            String key = of ? (linked == 1 ? "linked_of.one" : "linked_of") : (linked == 1 ? "linked.one" : "linked");
            text = of ? Component.translatable(KEY + key, linked, found, name)
                    : Component.translatable(KEY + key, linked, name);
            if (limit) {
                text.append(" · ").append(Component.translatable(KEY + "limit"));
            }
        }
        RangeLinks.appendCount(text, "area.protected", result.protectedCount());
        RangeLinks.appendCount(text, "area.unloaded", result.unloadedChunks());
        if (scan.capped()) {
            text.append(" · ").append(Component.translatable(KEY + "capped"));
        }
        return text;
    }

    /**
     * O nome no plural ({@code Fornalhas}) se o idioma tem a chave
     * {@code item.wirelessautomate.linker.range.same.plural.<mod>.<bloco>}; senão {@code × Fornalha}.
     */
    static Component plural(Block block, Component single) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String key = KEY + "plural." + id.getNamespace() + "." + id.getPath();
        return Language.getInstance().has(key) ? Component.translatable(key)
                : Component.translatable(KEY + "times", single);
    }
}
