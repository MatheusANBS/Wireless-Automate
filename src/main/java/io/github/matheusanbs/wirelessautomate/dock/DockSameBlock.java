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
import java.util.ArrayList;
import java.util.List;
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
 * escolhido, desvincula todas as máquinas do lado (são todas do mesmo bloco). Senão, vincula ao lado as máquinas do
 * mesmo bloco da clicada no cubo do tier, da mais perto da Doca para a mais longe, até o limite. Refaz as conferências
 * do clique (item, modo, distância do jogador ao bloco, Doca escolhida, {@code mayBuild}/{@code mayInteract} e as
 * recusas do {@link DockLinks}), com o mesmo descanso do roteador ({@link SameBlockLinks#COOLDOWN_TICKS}).
 *
 * <p>Custo, só no clique: a varredura do {@link SameBlockScan} no cubo do tier (até o raio 96 do Ultimate), com o teto
 * {@link SameBlockScan#MAX_READS}. As máquinas dos outros lados desta Doca ficam de fora da varredura; a capability, a
 * outra Doca (o {@code RemoteIndex}) e o evento de clique das proteções só são conferidos nas que de fato seriam
 * vinculadas, e o registro no índice é refeito uma vez, no fim ({@link PatternDockBlockEntity#batchChanges}).
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
        // o descanso segura um cliente que mande o pacote em rajada: a varredura custa até MAX_READS posições
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
        // a clicada serve de modelo: se ela é de outra Doca, fica de fora, e as outras do mesmo bloco entram
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
        Scan scan = SameBlockScan.scan(player, dock.getBlockPos(), DockLinks.radius(dock), block, inSide, otherSides,
                Math.max(1, room + 1));
        LongArrayList candidates = scan.candidates();
        if (candidates.isEmpty() && scan.protectedCount() > 0) {
            return DockLinks.refuse(player, Result.refused(Refusal.PROTECTED), dock, side, pos, 0);
        }
        if (!candidates.isEmpty() && room <= 0) {
            return DockLinks.refuse(player, Result.refused(Refusal.LIMIT), dock, side, pos, 0);
        }

        boolean first = DockLinks.isEmpty(dock.side(side));
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        // vinculadas, além do limite, protegidas pelo evento de clique, de outra Doca; e a primeira vinculada
        int[] counts = new int[4];
        long[] firstLinked = {Long.MIN_VALUE};
        dock.batchChanges(() -> {
            for (int i = 0; i < candidates.size(); i++) {
                BlockPos machine = BlockPos.of(candidates.getLong(i));
                if (counts[0] >= room) {
                    counts[1]++;
                } else if (!RangeLinks.isMachine(level, machine, LoadedTypes.LIST)) {
                    // o mesmo bloco sem capability nesta posição (o estado conta): não ocupa vaga
                    continue;
                } else if (DockLinks.otherDock(level, dock, machine) != null) {
                    counts[3]++;
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
                scan.protectedCount() + counts[2], counts[3], scan.unloadedChunks());
        MutableComponent text = message(dock, side, block, result, candidates.size(), scan);
        if (profile != null) {
            text.append(" · ").append(profile);
        }
        player.displayClientMessage(text, true);
        if (result.linked() > 0) {
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.2F);
        }
        return result;
    }

    /**
     * O Ctrl + clique numa máquina do lado escolhido: tira todas as máquinas do lado. Como o desvincular de um clique,
     * basta poder mexer na Doca, e não lê mundo além do bloco clicado.
     */
    private static Result unlink(ServerPlayer player, PatternDockBlockEntity dock, int side, BlockPos pos) {
        if (!RangeLinks.mayEdit(player, dock.getBlockPos())) {
            return DockLinks.refuse(player, Result.refused(Refusal.PROTECTED), dock, side, pos, 0);
        }
        ServerLevel level = player.serverLevel();
        Block block = level.getBlockState(pos).getBlock();
        DockSide dockSide = dock.side(side);
        List<BlockPos> machines = new ArrayList<>();
        if (dockSide != null) {
            for (DockMachine machine : dockSide.machines()) {
                machines.add(machine.pos());
            }
        }
        dock.batchChanges(() -> {
            for (BlockPos machine : machines) {
                dock.unlink(side, machine);
            }
        });
        Component single = RouterBlock.machineName(block);
        int count = machines.size();
        player.displayClientMessage(Component.translatable(KEY + (count == 1 ? "unlinked.one" : "unlinked"), count,
                count == 1 ? single : SameBlockLinks.plural(block, single), DockLinks.sideName(side),
                dock.activeCount(), DockLinks.max(dock)), true);
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
        return new Result(Status.SAME_BLOCK_UNLINKED, Refusal.NONE, count, 0, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ mensagens

    /**
     * {@code Vinculou 8 Fornalhas ao lado Norte · 8/8 máquinas · limite do tier}, {@code Vinculou 8 de 10 …} (com o
     * cubo inteiro lido) ou, sem nenhuma nova, as chaves {@code none} do roteador; mais as protegidas, as de outra
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
