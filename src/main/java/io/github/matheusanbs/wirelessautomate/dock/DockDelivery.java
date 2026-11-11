package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.TickBudget;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A entrega de um pedido do provedor a uma máquina de um lado da Doca, sem tipos do AE2 (a ponte do AE2 e a do RS2
 * chamam por aqui). Estática: o estado (a máquina da simulação, o cursor, o sono de cada máquina, o motivo da última
 * recusa e o pendente) fica no {@link DockSide} e na {@link DockMachine}.
 *
 * <p>Os passos da spec ("A entrega"): recusa logo um pedido vazio, um pedido com item e fluido
 * ({@link DockStatus#MIXED}), um lado sem máquina ativa ({@link DockStatus#NO_MACHINES}) e um tick sem orçamento
 * ({@link DockStatus#BUDGET}); tenta o pendente e, se ele
 * fica, recusa ({@link DockStatus#PENDING}); escolhe a máquina pelo cursor ({@link DockPick#pick}, no máximo
 * {@link #MAX_TRIES} candidatas, pulando as dormindo, as descarregadas e as de fora do alcance) simulando cada
 * ingrediente nas faces Insere do perfil; executa nela, cada ingrediente na face mais seletiva
 * ({@link DockPick#selectiveFace}); o que não couber (a simulação não soma) vai para o pendente do lado e o pedido conta
 * como aceito; e o cursor passa para depois dela. A máquina que recusa dorme até mudar
 * ({@link PatternDockBlockEntity#machineChanged}) ou até o teto de sono do motor.
 *
 * <p>Simular e executar: {@code simulate} faz até a escolha e guarda a máquina no lado, valendo só no mesmo tick do
 * servidor; a execução seguinte usa essa máquina, sem recusar por nada (o RS2 dá por entregue o que a execução recusa).
 * Uma execução sem simulação (o AE2) escolhe e executa de uma vez.
 *
 * <p>Contrato dos stacks: cada {@code ItemStack} do pedido já vem partido em pilhas válidas (no máximo o
 * {@code getMaxStackSize} do item; quem recebe em {@code long}, como o AE2, parte antes). Os stacks são do chamador e não
 * são mudados; o que vai ao pendente é uma cópia ou a sobra que a máquina devolveu.
 *
 * <p>Custo: o tempo de cada oferta vai para o orçamento do motor ({@link TickBudget#charge}); por oferta, no máximo
 * {@link #MAX_TRIES} candidatas, cada uma com uma simulação por ingrediente e por face Insere, pelos caches de capability
 * da máquina ({@link MachineCaches}, que conferem o chunk antes). Sem alocação além das sobras das simulações e do que
 * vai ao pendente: os rascunhos (faces por ingrediente, a tentativa) ficam no lado.
 */
public final class DockDelivery {
    /** Candidatas tentadas por pedido, no máximo. */
    public static final int MAX_TRIES = 8;

    private static final RemoteSide[] SIDES = RemoteSide.values();
    private static final Direction[] DIRECTIONS = Direction.values();

    public enum Result {
        ACCEPTED, REFUSED
    }

    private DockDelivery() {
    }

    /**
     * O lado aceita pedidos agora: tem máquina ativa e não tem pendente (o {@code acceptsPlans} do AE2, o
     * {@code isEmpty} do RS2). Não consulta máquina nenhuma.
     */
    public static boolean acceptsOrders(PatternDockBlockEntity dock, int side) {
        DockSide dockSide = dock.side(side);
        return dockSide != null && !dockSide.hasPending() && dockSide.activeMachines() > 0;
    }

    /**
     * Oferece um pedido ao lado {@code side}: itens ou fluidos, nunca os dois. Grava no lado o motivo de uma recusa.
     *
     * @param simulate só escolhe a máquina (e a guarda para a execução deste tick); nada entra nela
     */
    public static Result offer(PatternDockBlockEntity dock, int side, List<ItemStack> items, List<FluidStack> fluids,
            boolean simulate) {
        DockSide dockSide = dock.side(side);
        if (dockSide == null || dock.isRemoved() || !(dock.getLevel() instanceof ServerLevel level)) {
            return Result.REFUSED;
        }
        boolean hasItems = hasItem(items);
        boolean hasFluids = hasFluid(fluids);
        if (!hasItems && !hasFluids) {
            return Result.REFUSED;
        }
        if (hasItems && hasFluids) {
            // a simulação do mesmo pedido já recusaria: nunca há máquina guardada para um misto
            return refuse(dockSide, DockStatus.MIXED);
        }
        long now = level.getServer().getTickCount();
        DockMachine planned = simulate ? null : dockSide.planned(now);
        if (planned != null && dock.machineAt(planned.pos()) != planned) {
            // desvinculada entre a simulação e a execução: escolhe de novo
            planned = null;
        }
        TickBudget budget = NetworkManager.get().budget();
        if (planned == null) {
            if (dockSide.activeMachines() == 0) {
                return refuse(dockSide, DockStatus.NO_MACHINES);
            }
            if (!budget.outsideRoom()) {
                return refuse(dockSide, DockStatus.BUDGET);
            }
        }
        long started = System.nanoTime();
        try {
            return planned == null ? choose(dock, dockSide, items, fluids, hasItems, simulate, now)
                    : execute(dock, dockSide, planned, plannedIndex(dockSide, planned), items, fluids, hasItems, now,
                            false);
        } finally {
            budget.charge(System.nanoTime() - started);
        }
    }

    /**
     * Tenta entregar o pendente do lado {@code side} na máquina que recebeu o pedido. Devolve {@code true} se o lado
     * ficou sem pendente. Não cobra o orçamento: quem chama de fora do laço do motor cobra (a oferta e o
     * {@link #flushSoon} cobram; o visitante da volta roda dentro do laço, que já mede o tempo).
     */
    public static boolean flushPending(PatternDockBlockEntity dock, int side) {
        DockSide dockSide = dock.side(side);
        if (dockSide == null || !dockSide.hasPending()) {
            return true;
        }
        return dock.getLevel() instanceof ServerLevel level
                && DockPending.flush(dock, dockSide, level.getServer().getTickCount());
    }

    /**
     * Recusa um pedido antes da oferta, por um motivo que só quem chama conhece (o padrão de bancada do AE2), e o grava
     * no lado, se ele existe.
     */
    public static Result refuse(PatternDockBlockEntity dock, int side, int reason) {
        DockSide dockSide = dock.side(side);
        return dockSide == null ? Result.REFUSED : refuse(dockSide, reason);
    }

    private static Result refuse(DockSide side, int reason) {
        side.setLastRefusal(reason);
        return Result.REFUSED;
    }

    /** Os passos 1 (o pendente) e 2 (a escolha) e, sem simular, o 3 na escolhida. */
    private static Result choose(PatternDockBlockEntity dock, DockSide side, List<ItemStack> items,
            List<FluidStack> fluids, boolean hasItems, boolean simulate, long now) {
        if (side.hasPending() && !DockPending.flush(dock, side, now)) {
            return refuse(side, DockStatus.PENDING);
        }
        List<DockMachine> machines = side.machines();
        Attempt attempt = side.attempt();
        attempt.start(dock, side, hasItems ? items : null, hasItems ? null : fluids, now);
        int index;
        try {
            index = DockPick.pick(machines.size(), side.cursor(), attempt.usable, attempt.accepts, MAX_TRIES);
        } finally {
            attempt.clear();
        }
        if (index < 0) {
            return refuse(side, DockStatus.ALL_FULL);
        }
        DockMachine machine = machines.get(index);
        if (simulate) {
            side.plan(machine, index, now);
            side.setLastRefusal(DockStatus.NO_REFUSAL);
            return Result.ACCEPTED;
        }
        return execute(dock, side, machine, index, items, fluids, hasItems, now, true);
    }

    /** O índice da máquina da simulação na lista do lado (o guardado, ou procurado se a lista mudou). */
    private static int plannedIndex(DockSide side, DockMachine planned) {
        List<DockMachine> machines = side.machines();
        int index = side.plannedIndex();
        return index < machines.size() && machines.get(index) == planned ? index : machines.indexOf(planned);
    }

    /**
     * O passo 3: entrega o pedido em {@code machine}, cada ingrediente na face mais seletiva, e o que sobra vai ao
     * pendente. {@code masksReady}: as faces por ingrediente já estão no rascunho do lado (a escolha acabou de
     * simular nesta máquina); senão são simuladas de novo, aceitando parte (a execução nunca recusa).
     */
    private static Result execute(PatternDockBlockEntity dock, DockSide side, DockMachine machine, int index,
            List<ItemStack> items, List<FluidStack> fluids, boolean hasItems, long now, boolean masksReady) {
        side.clearPlan();
        MachineCaches caches = machine.caches(dock);
        MachineConfig profile = side.profile();
        int front = machine.front();
        int count = hasItems ? items.size() : fluids.size();
        int[] masks = side.masks(count);
        if (!masksReady) {
            if (hasItems) {
                itemMasks(caches, profile, front, items, count, masks, false);
            } else {
                fluidMasks(caches, profile, front, fluids, count, masks, false);
            }
        }
        int[] loads = side.loads();
        DockPick.loads(masks, count, loads);
        for (int i = 0; i < count; i++) {
            if (hasItems) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack rest = placeItem(caches, front, stack, masks[i], loads);
                if (!rest.isEmpty()) {
                    side.addPending(rest == stack ? stack.copy() : rest, machine.pos());
                }
            } else {
                FluidStack stack = fluids.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                FluidStack rest = placeFluid(caches, front, stack, masks[i], loads);
                if (!rest.isEmpty()) {
                    side.addPending(rest == stack ? stack.copy() : rest, machine.pos());
                }
            }
        }
        int size = side.machines().size();
        side.setCursor(index < 0 || size == 0 ? 0 : (index + 1) % size);
        machine.setLastActive(now);
        dock.returns().wake(machine);
        side.setLastRefusal(DockStatus.NO_REFUSAL);
        dock.saveChanged();
        return Result.ACCEPTED;
    }

    // ------------------------------------------------------------------ faces

    /** A face {@code face} do perfil ({@code RemoteSide.ordinal()}) na máquina virada para {@code front}. */
    private static Direction absolute(int face, int front) {
        return DIRECTIONS[SIDES[face].toAbsolute(front)];
    }

    /** A máquina tem inventário ({@code type} item) ou tanque (fluido) em alguma face Insere desse tipo no perfil. */
    static boolean hasInsertFace(MachineCaches caches, MachineConfig profile, int front, ResourceType type) {
        for (int face = 0; face < SIDES.length; face++) {
            if (profile.face(type, SIDES[face]).mode().inserts() && (type == ResourceType.FLUID
                    ? caches.fluids(absolute(face, front)) != null : caches.items(absolute(face, front)) != null)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simula cada item nas faces Insere de itens do perfil e escreve em {@code masks[i]} as faces que o aceitam (um bit
     * por face, na ordem do perfil): {@code whole}, a que aceita a pilha inteira; senão, a que aceita parte. Uma pilha
     * vazia fica com a máscara 0 e não conta. Devolve {@code true} se toda pilha não vazia tem face.
     */
    static boolean itemMasks(MachineCaches caches, MachineConfig profile, int front, List<ItemStack> items, int count,
            int[] masks, boolean whole) {
        for (int i = 0; i < count; i++) {
            masks[i] = 0;
        }
        for (int face = 0; face < SIDES.length; face++) {
            if (!profile.face(ResourceType.ITEM, SIDES[face]).mode().inserts()) {
                continue;
            }
            IItemHandler handler = caches.items(absolute(face, front));
            if (handler == null) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, stack, true);
                if (whole ? rest.isEmpty() : rest.getCount() < stack.getCount()) {
                    masks[i] |= 1 << face;
                }
            }
        }
        for (int i = 0; i < count; i++) {
            if (masks[i] == 0 && !items.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** O {@link #itemMasks} dos fluidos, pelo {@code fill} simulado nas faces Insere de fluidos. */
    static boolean fluidMasks(MachineCaches caches, MachineConfig profile, int front, List<FluidStack> fluids,
            int count, int[] masks, boolean whole) {
        for (int i = 0; i < count; i++) {
            masks[i] = 0;
        }
        for (int face = 0; face < SIDES.length; face++) {
            if (!profile.face(ResourceType.FLUID, SIDES[face]).mode().inserts()) {
                continue;
            }
            IFluidHandler handler = caches.fluids(absolute(face, front));
            if (handler == null) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                FluidStack stack = fluids.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                int filled = handler.fill(stack, IFluidHandler.FluidAction.SIMULATE);
                if (whole ? filled >= stack.getAmount() : filled > 0) {
                    masks[i] |= 1 << face;
                }
            }
        }
        for (int i = 0; i < count; i++) {
            if (masks[i] == 0 && !fluids.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Insere {@code stack} de verdade: primeiro na face mais seletiva de {@code mask}, e a sobra nas outras faces da
     * máscara, na ordem do perfil. Devolve a sobra ({@code stack} mesmo, se nada entrou).
     */
    static ItemStack placeItem(MachineCaches caches, int front, ItemStack stack, int mask, int[] loads) {
        int first = DockPick.selectiveFace(mask, loads);
        if (first < 0) {
            return stack;
        }
        ItemStack rest = insertItem(caches, front, first, stack);
        for (int face = 0; face < SIDES.length && !rest.isEmpty(); face++) {
            if (face != first && (mask & (1 << face)) != 0) {
                rest = insertItem(caches, front, face, rest);
            }
        }
        return rest;
    }

    private static ItemStack insertItem(MachineCaches caches, int front, int face, ItemStack stack) {
        IItemHandler handler = caches.items(absolute(face, front));
        return handler == null ? stack : ItemHandlerHelper.insertItemStacked(handler, stack, false);
    }

    /** O {@link #placeItem} dos fluidos. */
    static FluidStack placeFluid(MachineCaches caches, int front, FluidStack stack, int mask, int[] loads) {
        int first = DockPick.selectiveFace(mask, loads);
        if (first < 0) {
            return stack;
        }
        FluidStack rest = fillFluid(caches, front, first, stack);
        for (int face = 0; face < SIDES.length && !rest.isEmpty(); face++) {
            if (face != first && (mask & (1 << face)) != 0) {
                rest = fillFluid(caches, front, face, rest);
            }
        }
        return rest;
    }

    private static FluidStack fillFluid(MachineCaches caches, int front, int face, FluidStack stack) {
        IFluidHandler handler = caches.fluids(absolute(face, front));
        int filled = handler == null ? 0 : handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) {
            return stack;
        }
        return filled >= stack.getAmount() ? FluidStack.EMPTY : stack.copyWithAmount(stack.getAmount() - filled);
    }

    private static boolean hasItem(List<ItemStack> items) {
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFluid(List<FluidStack> fluids) {
        for (int i = 0; i < fluids.size(); i++) {
            if (!fluids.get(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Os dois testes da escolha ({@link DockPick#pick}) com o pedido em curso, um por lado e reaproveitado, para a
     * escolha não criar lambdas por pedido. Larga o pedido no fim ({@link #clear}).
     */
    static final class Attempt {
        final IntPredicate usable = this::usable;
        final IntPredicate accepts = this::accepts;
        private @Nullable PatternDockBlockEntity dock;
        private @Nullable DockSide side;
        private @Nullable List<ItemStack> items;
        private @Nullable List<FluidStack> fluids;
        private long now;

        void start(PatternDockBlockEntity dock, DockSide side, @Nullable List<ItemStack> items,
                @Nullable List<FluidStack> fluids, long now) {
            this.dock = dock;
            this.side = side;
            this.items = items;
            this.fluids = fluids;
            this.now = now;
        }

        void clear() {
            dock = null;
            side = null;
            items = null;
            fluids = null;
        }

        /** Ativa (no alcance e no limite), acordada e com o chunk carregado. */
        private boolean usable(int index) {
            DockMachine machine = side.machines().get(index);
            return machine.active() && !machine.asleep(now) && machine.caches(dock).available();
        }

        /** Aceita o pedido inteiro (cada pilha numa face); senão dorme. Deixa as faces no rascunho do lado. */
        private boolean accepts(int index) {
            DockMachine machine = side.machines().get(index);
            MachineCaches caches = machine.caches(dock);
            boolean accepted;
            ResourceType type;
            if (items != null) {
                accepted = itemMasks(caches, side.profile(), machine.front(), items, items.size(),
                        side.masks(items.size()), true);
                type = ResourceType.ITEM;
            } else {
                accepted = fluidMasks(caches, side.profile(), machine.front(), fluids, fluids.size(),
                        side.masks(fluids.size()), true);
                type = ResourceType.FLUID;
            }
            if (!accepted) {
                machine.sleepUntil(now + NetworkManager.maxSleepTicks(type));
            }
            return accepted;
        }
    }
}
