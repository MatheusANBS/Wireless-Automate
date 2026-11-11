package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.TickBudget;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * O pendente de uma máquina ({@link DockMachine#pending}): o resto de um pedido que não coube na execução (a simulação
 * não soma, ou o pedido é maior que a máquina inteira). Enquanto existe, só essa máquina deixa de receber pedidos; as
 * outras do lado continuam. Sai só nela, conforme ela consome, cada linha na face mais seletiva entre as que aceitam
 * parte dela, e é tentado no visitante da volta (que visita a máquina) e logo depois de um aviso dela
 * ({@link #flushSoon}).
 *
 * <p>Se ela saiu do lado, foi quebrada ou trocada por outro bloco, ou não tem mais inventário numa face Insere de um
 * tipo do pendente, o pendente fica solto no lado ({@link DockSide#loose}) e vai à próxima ativa que o sirva
 * ({@link #adopt}), de preferência uma sem pendente.
 */
final class DockPending {
    /**
     * Pilhas de item olhadas por tentativa, no máximo: 256 pilhas de 64, 16.384 itens. Um padrão muito maior que a
     * máquina (o de 524.288 numa Infinite Factory deixa 491.520 itens pendentes) não faz cada tentativa custar o
     * pendente inteiro; o resto entra nas tentativas seguintes, conforme a máquina consome.
     */
    static final int FLUSH_WINDOW = 256;

    private DockPending() {
    }

    /**
     * Tenta o pendente de {@code machine} (deste lado) nela. Devolve {@code true} se ela ficou sem pendente. Se ela não
     * serve mais ao pendente, ele fica solto e vai a outra ({@link #adopt}). Não cobra o orçamento.
     */
    static boolean flush(PatternDockBlockEntity dock, DockSide side, DockMachine machine, long now) {
        DockPendingLot lot = machine.pending();
        if (lot == null) {
            return true;
        }
        if (lot.isEmpty()) {
            side.release(machine);
            return true;
        }
        MachineCaches caches = machine.caches(dock);
        if (!caches.available()) {
            // descarregada: fica com o pendente (não dá para saber se ainda serve)
            return false;
        }
        if (!serves(dock, side, machine, lot)) {
            side.loosen(machine);
            dock.saveChanged();
            adopt(dock, side, now);
            return true;
        }
        if (place(side, lot, caches, machine.front())) {
            if (lot.isEmpty()) {
                side.release(machine);
            }
            machine.setLastActive(now);
            dock.saveChanged();
            dock.signalWork(side.side(), now);
        }
        return machine.pending() == null;
    }

    /**
     * O pendente solto vai à primeira ativa, a partir do cursor, que o sirva e não tenha pendente; sem nenhuma livre, à
     * primeira que o sirva (junto ao dela). Lá é tentado na hora. Devolve {@code true} se o lado ficou sem pendente
     * solto. Não cobra o orçamento.
     */
    static boolean adopt(PatternDockBlockEntity dock, DockSide side, long now) {
        DockPendingLot lot = side.loose();
        if (lot == null) {
            return true;
        }
        List<DockMachine> machines = side.machines();
        int size = machines.size();
        DockMachine busy = null;
        DockMachine target = null;
        for (int step = 0; step < size && target == null; step++) {
            DockMachine candidate = machines.get((side.cursor() + step) % size);
            if (!candidate.active() || !candidate.caches(dock).available() || !serves(dock, side, candidate, lot)) {
                continue;
            }
            if (candidate.pending() == null) {
                target = candidate;
            } else if (busy == null) {
                busy = candidate;
            }
        }
        if (target == null) {
            target = busy;
        }
        if (target == null) {
            return false;
        }
        side.clearLoose();
        side.adopt(target, lot);
        dock.saveChanged();
        flush(dock, side, target, now);
        return true;
    }

    /**
     * Põe o que couber de {@code lot} na máquina: cada linha na face mais seletiva entre as que aceitam parte dela (a
     * simulação de cada linha diz quais), e o resto nas outras da máscara; com uma linha só do tipo, sem simular, nas
     * faces Insere do tipo, na ordem do perfil (a mesma ordem que a simulação daria, e a face que recusa só devolve).
     * Os itens vão em pilhas do tamanho do item, até {@link #FLUSH_WINDOW} pilhas por tentativa, parando a linha na
     * primeira recusada; numa face que aceita a pilha maior (o Mekanism, {@link DockStacker#takesOversized}), a linha
     * entra numa inserção só, até a janela. Devolve se algo entrou.
     */
    private static boolean place(DockSide side, DockPendingLot lot, MachineCaches caches, int front) {
        boolean moved = placeItems(side, lot.itemLines(), caches, front);
        moved |= placeFluids(side, lot.fluidLines(), caches, front);
        moved |= placeChemicals(side, lot.chemicalLines(), caches, front);
        if (moved) {
            lot.prune();
        }
        return moved;
    }

    private static boolean placeItems(DockSide side, List<DockPendingLot.Line<ItemStack>> items, MachineCaches caches,
            int front) {
        int count = items.size();
        if (count == 0) {
            return false;
        }
        MachineConfig profile = side.profile();
        int[] loads = side.loads();
        int[] masks = side.masks(count);
        if (count == 1) {
            // uma linha: tenta as faces na ordem, sem simular antes (a face que recusa não muda nada)
            masks[0] = DockDelivery.insertFaces(caches, profile, front, ResourceType.ITEM);
        } else {
            for (int i = 0; i < count; i++) {
                ItemStack key = items.get(i).key();
                masks[i] = DockDelivery.itemMask(caches, profile, front,
                        key.copyWithCount((int) Math.min(items.get(i).amount(), key.getMaxStackSize())));
            }
        }
        DockPick.loads(masks, count, loads);
        DockStacker stacker = side.stacker();
        stacker.reset();
        boolean moved = false;
        int window = FLUSH_WINDOW;
        for (int i = 0; i < count && window > 0; i++) {
            DockPendingLot.Line<ItemStack> line = items.get(i);
            int max = line.key().getMaxStackSize();
            boolean joined = max > 1 && DockDelivery.oversizedFaces(caches, front, masks[i]);
            while (line.amount() > 0 && window > 0) {
                int size = (int) Math.min(line.amount(), (long) (joined ? window : 1) * max);
                ItemStack rest = DockDelivery.placeItem(caches, front, line.key().copyWithCount(size), masks[i],
                        loads, stacker);
                int placed = size - rest.getCount();
                window -= joined ? Math.max(1, (size + max - 1) / max) : 1;
                if (placed > 0) {
                    line.setAmount(line.amount() - placed);
                    moved = true;
                }
                if (!rest.isEmpty()) {
                    // a máquina recusou parte: a pilha igual seguinte também não cabe
                    break;
                }
            }
        }
        return moved;
    }

    private static boolean placeFluids(DockSide side, List<DockPendingLot.Line<FluidStack>> fluids,
            MachineCaches caches, int front) {
        int count = fluids.size();
        if (count == 0) {
            return false;
        }
        int[] loads = side.loads();
        int[] masks = side.masks(count);
        for (int i = 0; i < count; i++) {
            masks[i] = count == 1 ? DockDelivery.insertFaces(caches, side.profile(), front, ResourceType.FLUID)
                    : DockDelivery.fluidMask(caches, side.profile(), front, fluids.get(i).key());
        }
        DockPick.loads(masks, count, loads);
        boolean moved = false;
        for (int i = 0; i < count; i++) {
            DockPendingLot.Line<FluidStack> line = fluids.get(i);
            int size = (int) Math.min(line.amount(), Integer.MAX_VALUE);
            FluidStack rest = DockDelivery.placeFluid(caches, front, line.key().copyWithAmount(size), masks[i],
                    loads);
            int placed = size - rest.getAmount();
            if (placed > 0) {
                line.setAmount(line.amount() - placed);
                moved = true;
            }
        }
        return moved;
    }

    private static boolean placeChemicals(DockSide side, List<DockPendingLot.Line<Object>> chemicals,
            MachineCaches caches, int front) {
        int count = chemicals.size();
        if (count == 0) {
            return false;
        }
        int[] loads = side.loads();
        int[] masks = side.masks(count);
        for (int i = 0; i < count; i++) {
            masks[i] = count == 1 ? DockDelivery.insertFaces(caches, side.profile(), front, ResourceType.CHEMICAL)
                    : DockDelivery.chemicalMask(caches, side.profile(), front, chemicals.get(i).key());
        }
        DockPick.loads(masks, count, loads);
        boolean moved = false;
        for (int i = 0; i < count; i++) {
            DockPendingLot.Line<Object> line = chemicals.get(i);
            long amount = line.amount();
            long rest = DockDelivery.placeChemical(caches, front, Chemicals.copyWithAmount(line.key(), amount),
                    masks[i], loads);
            if (rest < amount) {
                line.setAmount(Math.max(0, rest));
                moved = true;
            }
        }
        return moved;
    }

    /**
     * A máquina carregada ainda serve ao pendente: o bloco dela é o do lado (não foi quebrada nem trocada) e tem
     * inventário (ou tanque, ou tanque de químico) numa face Insere do perfil para cada tipo do lote. Só com pendente,
     * na tentativa.
     */
    private static boolean serves(PatternDockBlockEntity dock, DockSide side, DockMachine machine,
            DockPendingLot lot) {
        ResourceLocation block = side.block();
        if (block != null && dock.getLevel() != null && !block.equals(
                BuiltInRegistries.BLOCK.getKey(dock.getLevel().getBlockState(machine.pos()).getBlock()))) {
            return false;
        }
        MachineCaches caches = machine.caches(dock);
        return (lot.itemLines().isEmpty()
                        || DockDelivery.hasInsertFace(caches, side.profile(), machine.front(), ResourceType.ITEM))
                && (lot.fluidLines().isEmpty()
                        || DockDelivery.hasInsertFace(caches, side.profile(), machine.front(), ResourceType.FLUID))
                && (lot.chemicalLines().isEmpty()
                        || DockDelivery.hasInsertFace(caches, side.profile(), machine.front(), ResourceType.CHEMICAL));
    }

    /**
     * Uma tentativa do pendente de {@code machine} (ou do solto, com {@code null}) na fila do servidor, depois de um
     * aviso. Não roda na hora: o aviso chega de dentro do {@code setChanged} da máquina (no meio do tick dela), de uma
     * entrega do motor (que tem o plano dela na mão) ou de um listener de cache (que não aceita consulta); inserir nela
     * ali reentraria. Uma só na fila por máquina (e uma para o solto, por lado). A tentativa roda fora do laço do
     * motor, então confere a folga do orçamento antes e cobra o tempo depois; sem folga, fica para o próximo aviso ou o
     * visitante da volta. Só aloca a tarefa, e só com pendente.
     */
    static void flushSoon(PatternDockBlockEntity dock, DockSide side, @Nullable DockMachine machine) {
        boolean queued = machine == null ? side.flushQueued() : machine.flushQueued();
        if (queued || !(dock.getLevel() instanceof ServerLevel level)) {
            return;
        }
        setQueued(side, machine, true);
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            setQueued(side, machine, false);
            if (dock.isRemoved() || dock.side(side.side()) != side) {
                return;
            }
            boolean wanted = machine == null ? side.loose() != null
                    : machine.pending() != null && dock.machineAt(machine.pos()) == machine;
            if (!wanted) {
                return;
            }
            TickBudget budget = NetworkManager.get().budget();
            if (!budget.outsideRoom()) {
                return;
            }
            long started = System.nanoTime();
            long now = server.getTickCount();
            if (machine == null) {
                adopt(dock, side, now);
            } else {
                flush(dock, side, machine, now);
            }
            budget.charge(System.nanoTime() - started);
        }));
    }

    private static void setQueued(DockSide side, @Nullable DockMachine machine, boolean queued) {
        if (machine == null) {
            side.setFlushQueued(queued);
        } else {
            machine.setFlushQueued(queued);
        }
    }
}
