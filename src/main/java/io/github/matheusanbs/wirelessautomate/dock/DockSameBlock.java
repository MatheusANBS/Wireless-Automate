package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.dock.DockLinks.Refusal;
import io.github.matheusanbs.wirelessautomate.dock.DockLinks.Result;
import io.github.matheusanbs.wirelessautomate.dock.DockLinks.Status;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.SameBlockLinks;
import io.github.matheusanbs.wirelessautomate.range.SameBlockScan;
import io.github.matheusanbs.wirelessautomate.range.SameBlockScan.Scan;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * O Ctrl + clique do Vinculador com uma Doca escolhida ({@code LinkSameBlockPayload}). Com a clicada já no lado
 * escolhido, desvincula as do lado que se tocam com ela ({@link #unlink}). Senão, vincula ao lado as máquinas do mesmo
 * bloco que se tocam com a clicada (pela face, pela aresta ou pela ponta, {@link SameBlockScan}) dentro do cubo do
 * tier, da mais perto da clicada para a mais longe, até o limite. O grupo passa pelas que não entram: as já no lado,
 * as dos outros lados desta Doca e as de outra Doca ({@link DockLinks#otherDock}, que contam no aviso
 * {@code same.other_dock}). Refaz as conferências do clique (item, modo, distância do jogador ao bloco, Doca
 * escolhida, {@code mayBuild}/{@code mayInteract} e as recusas do {@link DockLinks}), com o mesmo descanso do
 * roteador ({@link SameBlockLinks#COOLDOWN_TICKS}).
 *
 * <p>Custo, só no clique: a busca do {@link SameBlockScan} pelo grupo, até 26 leituras por posição, com o teto
 * {@link SameBlockScan#MAX_READS}; a outra Doca é conferida em cada uma do grupo que não é desta Doca. A capability e
 * o evento de clique das proteções só são conferidos nas que de fato seriam vinculadas, e o registro no índice é
 * refeito uma vez, no fim ({@link PatternDockBlockEntity#batchChanges}).
 */
public final class DockSameBlock {
    private static final String KEY = "item.wirelessautomate.linker.dock.same.";
    private static final String RANGE_KEY = "item.wirelessautomate.linker.range.same.";

    private DockSameBlock() {
    }

    /**
     * O pacote do Ctrl + clique em {@code pos} com uma Doca escolhida. Sem o Vinculador no modo Vincular na mão
     * principal com uma Doca escolhida, com o bloco descarregado ou longe da mão do jogador, ou no descanso, ignora
     * (devolve {@code null}, sem mensagem).
     */
    public static @Nullable Result link(@Nullable ServerPlayer player, BlockPos pos) {
        if (player == null) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        ServerLevel level = player.serverLevel();
        if (!(stack.getItem() instanceof LinkerItem) || LinkerItem.mode(stack) != LinkerMode.LINK
                || LinkerItem.dock(stack) == null || LinkerItem.tv(stack) != null || !level.isLoaded(pos)
                || !player.canInteractWithBlock(pos, 1.0) || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return null;
        }
        // o descanso segura um cliente que mande o pacote em rajada: a varredura custa até MAX_READS leituras
        player.getCooldowns().addCooldown(stack.getItem(), SameBlockLinks.COOLDOWN_TICKS);
        DockChoice choice = LinkerItem.dock(stack);
        PatternDockBlockEntity dock = DockLinks.chosen(player, stack);
        if (choice == null || dock == null) {
            return DockLinks.missing(player, stack);
        }
        int side = choice.side();
        DockMachine owned = dock.machineAt(pos);
        if (owned != null && owned.side() == side) {
            return unlink(player, dock, side, pos);
        }
        if (!RangeLinks.mayEdit(player, pos) || !RangeLinks.mayEdit(player, dock.getBlockPos())) {
            return DockLinks.refuse(player, Result.refused(Refusal.PROTECTED), dock, side, pos, 0);
        }
        // a clicada serve de modelo: se ela é de outra Doca, fica de fora, e as que se tocam com ela entram
        Refusal refusal = DockLinks.machineRefusal(level, dock, side, pos);
        if (refusal == Refusal.OTHER_DOCK) {
            refusal = Refusal.NONE;
        }
        int distance = DockLinks.distance(dock, pos);
        if (refusal == Refusal.NONE && distance > DockLinks.radius(dock)) {
            refusal = Refusal.OUT_OF_RANGE;
        }
        if (refusal != Refusal.NONE) {
            return DockLinks.refuse(player, Result.refused(refusal), dock, side, pos, distance);
        }

        Block block = level.getBlockState(pos).getBlock();
        int room = DockLinks.max(dock) - dock.activeCount();
        LongOpenHashSet inSide = new LongOpenHashSet();
        LongOpenHashSet otherSides = new LongOpenHashSet();
        for (int other = 0; other < PatternDockBlockEntity.SIDES; other++) {
            DockSide dockSide = dock.side(other);
            if (dockSide != null) {
                for (DockMachine machine : dockSide.machines()) {
                    (other == side ? inSide : otherSides).add(machine.pos().asLong());
                }
            }
        }
        // uma além das vagas: o "limite do tier" só aparece quando há de fato uma que ficou de fora
        Scan scan = SameBlockScan.scan(player, pos, dock.getBlockPos(), DockLinks.radius(dock), block, inSide,
                otherSides, key -> DockLinks.otherDock(level, dock, BlockPos.of(key)) != null, Math.max(1, room + 1));
        LongArrayList candidates = scan.candidates();
        if (candidates.isEmpty() && scan.protectedCount() > 0) {
            return DockLinks.refuse(player, Result.refused(Refusal.PROTECTED), dock, side, pos, 0);
        }
        if (!candidates.isEmpty() && room <= 0) {
            return DockLinks.refuse(player, Result.refused(Refusal.LIMIT), dock, side, pos, 0);
        }

        boolean first = DockLinks.isEmpty(dock.side(side));
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        // vinculadas, além do limite, protegidas pelo evento de clique; e a primeira vinculada
        int[] counts = new int[3];
        long[] firstLinked = {Long.MIN_VALUE};
        dock.batchChanges(() -> {
            for (int i = 0; i < candidates.size(); i++) {
                BlockPos machine = BlockPos.of(candidates.getLong(i));
                if (counts[0] >= room) {
                    counts[1]++;
                } else if (!RangeLinks.isMachine(level, machine, LoadedTypes.LIST)) {
                    // o mesmo bloco sem capability nesta posição (o estado conta): não ocupa vaga
                    continue;
                } else if (!RangeLinks.clickAllowed(player, InteractionHand.MAIN_HAND, machine)) {
                    counts[2]++;
                } else {
                    BlockState state = level.getBlockState(machine);
                    if (dock.link(side, machine, id, RemoteMachines.frontOf(state))) {
                        if (counts[0] == 0) {
                            firstLinked[0] = machine.asLong();
                        }
                        counts[0]++;
                    }
                }
            }
        });
        if (counts[0] == 0 && counts[2] > 0) {
            // todas as achadas recusadas pelo evento de clique das proteções
            return DockLinks.refuse(player, Result.refused(Refusal.PROTECTED), dock, side, pos, 0);
        }
        Component profile = first && counts[0] > 0
                ? DockLinks.tune(player, dock, side, BlockPos.of(firstLinked[0])) : null;
        Result result = new Result(Status.SAME_BLOCK_DONE, Refusal.NONE, counts[0], scan.already(), counts[1],
                scan.protectedCount() + counts[2], scan.taken(), scan.unloadedChunks());
        MutableComponent text = message(dock, side, block, result, candidates.size(), scan);
        if (profile != null) {
            text.append(" · ").append(profile);
        }
        player.displayClientMessage(text, true);
        if (result.linked() > 0) {
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.2F);
        }
        DockReachSync.send(player, dock, 0);
        return result;
    }

    /**
     * O Ctrl + clique numa máquina do lado escolhido: tira do lado as do grupo que se toca com a clicada
     * ({@link SameBlockScan}, sem o corte do raio: uma encostada fora do cubo também sai). Como o desvincular de um
     * clique, basta poder mexer na Doca. Lê o grupo, com o teto {@link SameBlockScan#MAX_READS}.
     */
    private static Result unlink(ServerPlayer player, PatternDockBlockEntity dock, int side, BlockPos pos) {
        if (!RangeLinks.mayEdit(player, dock.getBlockPos())) {
            return DockLinks.refuse(player, Result.refused(Refusal.PROTECTED), dock, side, pos, 0);
        }
        ServerLevel level = player.serverLevel();
        Block block = level.getBlockState(pos).getBlock();
        LongOpenHashSet inSide = new LongOpenHashSet();
        DockSide dockSide = dock.side(side);
        if (dockSide != null) {
            for (DockMachine machine : dockSide.machines()) {
                inSide.add(machine.pos().asLong());
            }
        }
        LongArrayList machines = SameBlockScan.scan(player, pos, dock.getBlockPos(), SameBlockScan.NO_RADIUS, block,
                inSide, LongSets.EMPTY_SET, key -> false, Integer.MAX_VALUE).linkedPositions();
        dock.batchChanges(() -> {
            for (int i = 0; i < machines.size(); i++) {
                dock.unlink(side, BlockPos.of(machines.getLong(i)));
            }
        });
        Component single = RouterBlock.machineName(block);
        int count = machines.size();
        player.displayClientMessage(Component.translatable(KEY + (count == 1 ? "unlinked.one" : "unlinked"), count,
                count == 1 ? single : SameBlockLinks.plural(block, single), DockLinks.sideName(side),
                dock.activeCount(), DockLinks.max(dock)), true);
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
        DockReachSync.send(player, dock, 0);
        return new Result(Status.SAME_BLOCK_UNLINKED, Refusal.NONE, count, 0, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ mensagens

    /**
     * {@code Vinculou 8 Fornalhas ao lado Norte · 8/8 máquinas · limite do tier}, {@code Vinculou 8 de 9 …} (com o
     * grupo inteiro lido) ou, sem nenhuma nova, as chaves {@code none} do roteador; mais as protegidas, as de outra
     * Doca, os chunks descarregados e o teto.
     */
    static MutableComponent message(PatternDockBlockEntity dock, int side, Block block, Result result, int found,
            Scan scan) {
        Component single = RouterBlock.machineName(block);
        MutableComponent text;
        int linked = result.linked();
        boolean limit = result.overLimit() > 0;
        if (found == 0) {
            text = Component.translatable(RANGE_KEY + (scan.capped() ? "none.capped" : "none"), single);
        } else {
            boolean of = limit && scan.complete() && found > linked;
            Component name = linked == 1 && !of ? single : SameBlockLinks.plural(block, single);
            String key = of ? (linked == 1 ? "linked_of.one" : "linked_of") : (linked == 1 ? "linked.one" : "linked");
            text = of ? Component.translatable(KEY + key, linked, found, name, DockLinks.sideName(side),
                    dock.activeCount(), DockLinks.max(dock))
                    : Component.translatable(KEY + key, linked, name, DockLinks.sideName(side), dock.activeCount(),
                            DockLinks.max(dock));
            if (limit) {
                text.append(" · ").append(Component.translatable(RANGE_KEY + "limit"));
            }
        }
        RangeLinks.appendCount(text, "area.protected", result.protectedCount());
        DockLinks.appendCount(text, "same.other_dock", result.otherDock());
        RangeLinks.appendCount(text, "area.unloaded", result.unloadedChunks());
        if (scan.capped()) {
            text.append(" · ").append(Component.translatable(RANGE_KEY + "capped"));
        }
        return text;
    }
}
