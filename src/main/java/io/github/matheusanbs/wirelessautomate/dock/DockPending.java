package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.TickBudget;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * O pendente de um lado: o resto de um pedido que não coube na execução (a simulação não soma). Enquanto existe, o
 * lado recusa pedidos novos ({@link DockDelivery#acceptsOrders}). Sai na máquina que recebeu o pedido, cada pilha na
 * face mais seletiva entre as que aceitam parte dela, e é tentado em três momentos: no começo de cada oferta, no
 * visitante da volta ({@link DockDelivery#flushPending}) e logo depois de um aviso dessa máquina ({@link #flushSoon}).
 */
final class DockPending {
    private DockPending() {
    }

    /**
     * Tenta o pendente de {@code side} na máquina que recebeu o pedido; se ela saiu do lado, na primeira ativa a partir
     * do cursor, que passa a guardá-lo. Devolve {@code true} se o lado ficou sem pendente. Não cobra o orçamento.
     */
    static boolean flush(PatternDockBlockEntity dock, DockSide side, long now) {
        if (!side.hasPending()) {
            return true;
        }
        DockMachine machine = target(dock, side);
        if (machine == null) {
            return false;
        }
        MachineCaches caches = machine.caches(dock);
        if (!caches.available()) {
            return false;
        }
        int front = machine.front();
        int[] loads = side.loads();
        boolean moved = false;
        List<ItemStack> items = side.pendingItems();
        if (!items.isEmpty()) {
            int count = items.size();
            int[] masks = side.masks(count);
            DockDelivery.itemMasks(caches, side.profile(), front, items, count, masks, false);
            DockPick.loads(masks, count, loads);
            for (int i = count - 1; i >= 0; i--) {
                ItemStack stack = items.get(i);
                ItemStack rest = DockDelivery.placeItem(caches, front, stack, masks[i], loads);
                if (rest == stack) {
                    continue;
                }
                moved = true;
                if (rest.isEmpty()) {
                    items.remove(i);
                } else {
                    items.set(i, rest);
                }
            }
        }
        List<FluidStack> fluids = side.pendingFluids();
        if (!fluids.isEmpty()) {
            int count = fluids.size();
            int[] masks = side.masks(count);
            DockDelivery.fluidMasks(caches, side.profile(), front, fluids, count, masks, false);
            DockPick.loads(masks, count, loads);
            for (int i = count - 1; i >= 0; i--) {
                FluidStack stack = fluids.get(i);
                FluidStack rest = DockDelivery.placeFluid(caches, front, stack, masks[i], loads);
                if (rest == stack) {
                    continue;
                }
                moved = true;
                if (rest.isEmpty()) {
                    fluids.remove(i);
                } else {
                    fluids.set(i, rest);
                }
            }
        }
        if (moved) {
            machine.setLastActive(now);
            dock.saveChanged();
        }
        return !side.hasPending();
    }

    /**
     * A máquina que guarda o pendente: a que recebeu o pedido, se ainda é deste lado; senão a primeira ativa a partir
     * do cursor (o pendente passa para ela). {@code null} sem nenhuma.
     */
    private static @Nullable DockMachine target(PatternDockBlockEntity dock, DockSide side) {
        DockMachine machine = dock.machineAt(side.pendingAtKey());
        if (machine != null && machine.side() == side.side()) {
            return machine;
        }
        List<DockMachine> machines = side.machines();
        int size = machines.size();
        for (int step = 0; step < size; step++) {
            DockMachine candidate = machines.get((side.cursor() + step) % size);
            if (candidate.active()) {
                side.movePending(candidate.pos());
                dock.saveChanged();
                return candidate;
            }
        }
        return null;
    }

    /**
     * Uma tentativa do pendente na fila do servidor, depois do aviso da máquina que o guarda. Não roda na hora: o aviso
     * chega de dentro do {@code setChanged} dela (no meio do tick dela), de uma entrega do motor (que tem o plano dela
     * na mão) ou de um listener de cache (que não aceita consulta); inserir nela ali reentraria. Uma só na fila por
     * lado. A tentativa roda fora do laço do motor, então confere a folga do orçamento antes e cobra o tempo depois;
     * sem folga, fica para o próximo aviso, a próxima oferta ou o visitante da volta. Só aloca a tarefa, e só com
     * pendente nesta máquina.
     */
    static void flushSoon(PatternDockBlockEntity dock, DockSide side) {
        if (side.flushQueued() || !(dock.getLevel() instanceof ServerLevel level)) {
            return;
        }
        side.setFlushQueued(true);
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            side.setFlushQueued(false);
            if (dock.isRemoved() || dock.side(side.side()) != side || !side.hasPending()) {
                return;
            }
            TickBudget budget = NetworkManager.get().budget();
            if (!budget.outsideRoom()) {
                return;
            }
            long started = System.nanoTime();
            flush(dock, side, server.getTickCount());
            budget.charge(System.nanoTime() - started);
        }));
    }
}
