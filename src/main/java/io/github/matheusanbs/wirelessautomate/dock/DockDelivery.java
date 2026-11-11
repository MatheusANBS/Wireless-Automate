package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.network.Chemicals;
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
 * <p>Um pedido traz itens, fluidos e químicos juntos (o caso comum das máquinas do Mekanism). Os químicos são
 * {@code ChemicalStack} do Mekanism como {@code Object} e passam pela ponte {@link Chemicals}; sem o Mekanism, um
 * pedido com químico é recusado como recurso desconhecido ({@link DockStatus#MIXED}).
 *
 * <p>Os passos da spec ("A entrega"): recusa logo um pedido vazio, um pedido com recurso que a Doca não conhece
 * ({@link DockStatus#MIXED}), um lado sem máquina ativa ({@link DockStatus#NO_MACHINES}) e um tick sem orçamento
 * ({@link DockStatus#BUDGET}); leva o pendente solto a uma máquina ({@link DockPending#adopt}) e recusa o lado em que
 * toda máquina ativa guarda pendente ({@link DockStatus#PENDING}); escolhe a máquina pelo cursor
 * ({@link DockPick#pick}, no máximo {@link #MAX_TRIES} candidatas, pulando as com pendente, as dormindo, as
 * descarregadas e as de fora do alcance) simulando cada ingrediente nas faces Insere do tipo dele no perfil; executa
 * nela, cada ingrediente na face mais seletiva do tipo dele ({@link DockPick#selectiveFace}, com a carga de cada face
 * contada só entre os ingredientes do mesmo tipo); o que não couber (a simulação não soma) vai para o pendente dela,
 * uma linha por item ({@link DockPendingLot}), e o pedido conta como aceito; e o cursor passa para depois dela. A
 * máquina que recusa dorme até mudar ({@link PatternDockBlockEntity#machineChanged}) ou até o teto de sono do motor.
 *
 * <p>Simular e executar: {@code simulate} faz até a escolha e guarda a máquina no lado, valendo só no mesmo tick do
 * servidor; a execução seguinte usa essa máquina, sem recusar por nada (o RS2 dá por entregue o que a execução recusa).
 * Uma execução sem simulação (o AE2) escolhe e executa de uma vez.
 *
 * <p>Contrato dos stacks: cada {@code ItemStack} do pedido já vem partido em pilhas válidas (no máximo o
 * {@code getMaxStackSize} do item; quem recebe em {@code long}, como o AE2, parte antes). Os stacks são do chamador e
 * não são mudados nem guardados: o pendente guarda uma cópia de tamanho 1 por item novo, e a quantidade na linha.
 *
 * <p>Custo: o tempo de cada oferta vai para o orçamento do motor ({@link TickBudget#charge}); por oferta, no máximo
 * {@link #MAX_TRIES} candidatas, cada uma com uma simulação por ingrediente e por face Insere do tipo dele (uma pilha
 * igual à anterior não é simulada de novo, e na execução o item que a máquina já recusou e o que passa de
 * {@link DockPending#FLUSH_WINDOW} pilhas vão direto ao pendente: o custo de um padrão multiplicado não cresce com as
 * pilhas dele), pelos caches de capability da máquina ({@link MachineCaches}, que conferem o chunk antes); a simulação
 * de cada tipo só roda se os anteriores couberam (itens, depois fluidos, depois químicos), e a de um tipo ausente volta
 * sem consultar a máquina. Sem alocação além das sobras das simulações e do que vai ao pendente: os rascunhos (faces
 * por ingrediente, itens, fluidos e químicos no mesmo vetor, e a tentativa) ficam no lado.
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
     * O lado aceita pedidos agora: tem máquina ativa sem pendente (o {@code acceptsPlans} do AE2). Uma conta de dois
     * campos ({@link DockSide#freeMachines}), sem consultar máquina nenhuma.
     */
    public static boolean acceptsOrders(PatternDockBlockEntity dock, int side) {
        DockSide dockSide = dock.side(side);
        return dockSide != null && dockSide.freeMachines() > 0;
    }

    /** O {@link #offer(PatternDockBlockEntity, int, List, List, List, boolean)} de um pedido sem químicos. */
    public static Result offer(PatternDockBlockEntity dock, int side, List<ItemStack> items, List<FluidStack> fluids,
            boolean simulate) {
        return offer(dock, side, items, fluids, List.of(), simulate);
    }

    /**
     * Oferece um pedido ao lado {@code side}: itens, fluidos e químicos, juntos ou não. Grava no lado o motivo de uma
     * recusa.
     *
     * @param chemicals os químicos do pedido ({@code ChemicalStack}); sem o Mekanism, qualquer entrada recusa
     * @param simulate só escolhe a máquina (e a guarda para a execução deste tick); nada entra nela
     */
    public static Result offer(PatternDockBlockEntity dock, int side, List<ItemStack> items, List<FluidStack> fluids,
            List<?> chemicals, boolean simulate) {
        DockSide dockSide = dock.side(side);
        if (dockSide == null || dock.isRemoved() || !(dock.getLevel() instanceof ServerLevel level)) {
            return Result.REFUSED;
        }
        if (!Chemicals.LOADED && !chemicals.isEmpty()) {
            // a simulação do mesmo pedido já recusaria: nunca há máquina guardada para ele
            return refuse(dockSide, DockStatus.MIXED);
        }
        boolean hasItems = hasItem(items);
        boolean hasFluids = hasFluid(fluids);
        boolean hasChemicals = hasChemical(chemicals);
        if (!hasItems && !hasFluids && !hasChemicals) {
            return Result.REFUSED;
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
            return planned == null
                    ? choose(dock, dockSide, items, fluids, chemicals, hasFluids || hasChemicals, simulate, now)
                    : execute(dock, dockSide, planned, plannedIndex(dockSide, planned), items, fluids, chemicals, now,
                            false);
        } finally {
            budget.charge(System.nanoTime() - started);
        }
    }

    /**
     * Tenta entregar todo o pendente do lado {@code side}: o solto e o de cada máquina, cada um na máquina dele.
     * Devolve {@code true} se o lado ficou sem pendente. O(máquinas): para os testes e quem precisa do lado inteiro; a
     * volta tenta o de cada máquina que visita. Não cobra o orçamento.
     */
    public static boolean flushPending(PatternDockBlockEntity dock, int side) {
        DockSide dockSide = dock.side(side);
        if (dockSide == null || !dockSide.hasPending()) {
            return true;
        }
        if (!(dock.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getServer().getTickCount();
        DockPending.adopt(dock, dockSide, now);
        List<DockMachine> machines = dockSide.machines();
        for (int i = 0; i < machines.size(); i++) {
            DockMachine machine = machines.get(i);
            if (machine.pending() != null) {
                DockPending.flush(dock, dockSide, machine, now);
            }
        }
        return !dockSide.hasPending();
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

    /** Os passos 1 (o pendente solto e as máquinas livres) e 2 (a escolha) e, sem simular, o 3 na escolhida. */
    private static Result choose(PatternDockBlockEntity dock, DockSide side, List<ItemStack> items,
            List<FluidStack> fluids, List<?> chemicals, boolean scalar, boolean simulate, long now) {
        if (side.loose() != null) {
            DockPending.adopt(dock, side, now);
        }
        if (side.freeMachines() <= 0) {
            return refuse(side, DockStatus.PENDING);
        }
        List<DockMachine> machines = side.machines();
        Attempt attempt = side.attempt();
        attempt.start(dock, side, items, fluids, chemicals, scalar, now);
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
        return execute(dock, side, machine, index, items, fluids, chemicals, now, true);
    }

    /** O índice da máquina da simulação na lista do lado (o guardado, ou procurado se a lista mudou). */
    private static int plannedIndex(DockSide side, DockMachine planned) {
        List<DockMachine> machines = side.machines();
        int index = side.plannedIndex();
        return index < machines.size() && machines.get(index) == planned ? index : machines.indexOf(planned);
    }

    /**
     * O passo 3: entrega o pedido em {@code machine}, cada ingrediente na face mais seletiva do tipo dele, e o que
     * sobra vai ao pendente dela (que só cresce se ela já tinha um, na execução do RS2 depois da simulação).
     * {@code masksReady}: as faces por ingrediente já estão no rascunho do lado (a escolha acabou de simular nesta
     * máquina); senão são simuladas de novo, aceitando parte (a execução nunca recusa).
     */
    private static Result execute(PatternDockBlockEntity dock, DockSide side, DockMachine machine, int index,
            List<ItemStack> items, List<FluidStack> fluids, List<?> chemicals, long now, boolean masksReady) {
        side.clearPlan();
        MachineCaches caches = machine.caches(dock);
        MachineConfig profile = side.profile();
        int front = machine.front();
        int itemCount = items.size();
        int fluidCount = fluids.size();
        int chemicalCount = chemicals.size();
        int[] masks = side.masks(itemCount + fluidCount + chemicalCount);
        if (!masksReady) {
            itemMasks(caches, profile, front, items, 0, masks, false);
            fluidMasks(caches, profile, front, fluids, itemCount, masks, false);
            chemicalMasks(caches, profile, front, chemicals, itemCount + fluidCount, masks, false);
        }
        int[] loads = side.loads();
        DockPendingLot lot = machine.pending();
        if (itemCount > 0) {
            DockPick.loads(masks, 0, itemCount, loads);
            DockStacker stacker = side.stacker();
            stacker.reset();
            // O item que a máquina já recusou (não coube inteiro) não cabe na pilha igual seguinte, e depois de
            // DockPending.FLUSH_WINDOW pilhas o resto espera as tentativas do pendente: vai direto ao pendente, sem
            // tentar (o pedido de 524.288 são 8.192 pilhas iguais). As pilhas iguais seguidas entram juntas na
            // máquina que aceita a pilha maior (o run): as 64 de um pedido de 4.096 numa inserção só.
            ItemStack refused = ItemStack.EMPTY;
            int tried = 0;
            for (int i = 0; i < itemCount; i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                boolean attempt = tried < DockPending.FLUSH_WINDOW
                        && (refused.isEmpty() || !ItemStack.isSameItemSameComponents(stack, refused));
                if (!attempt) {
                    lot = lot != null ? lot : side.lotFor(machine);
                    lot.addItem(stack, stack.getCount());
                    continue;
                }
                int run = run(caches, front, items, masks, i,
                        Math.min(itemCount, i + DockPending.FLUSH_WINDOW - tried));
                tried += run;
                if (run == 1) {
                    ItemStack rest = placeItem(caches, front, stack, masks[i], loads, stacker);
                    if (!rest.isEmpty()) {
                        refused = stack;
                        lot = lot != null ? lot : side.lotFor(machine);
                        lot.addItem(rest, rest.getCount());
                    }
                    continue;
                }
                int total = 0;
                for (int j = i; j < i + run; j++) {
                    total += items.get(j).getCount();
                }
                ItemStack rest = placeItem(caches, front, stack.copyWithCount(total), masks[i], loads, stacker);
                if (!rest.isEmpty()) {
                    refused = stack;
                    lot = lot != null ? lot : side.lotFor(machine);
                    lot.addItem(rest, rest.getCount());
                }
                i += run - 1;
            }
        }
        if (fluidCount > 0) {
            DockPick.loads(masks, itemCount, fluidCount, loads);
            for (int i = 0; i < fluidCount; i++) {
                FluidStack stack = fluids.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                FluidStack rest = placeFluid(caches, front, stack, masks[itemCount + i], loads);
                if (!rest.isEmpty()) {
                    lot = lot != null ? lot : side.lotFor(machine);
                    lot.addFluid(rest, rest.getAmount());
                }
            }
        }
        if (chemicalCount > 0) {
            int from = itemCount + fluidCount;
            DockPick.loads(masks, from, chemicalCount, loads);
            for (int i = 0; i < chemicalCount; i++) {
                Object stack = chemicals.get(i);
                if (Chemicals.isEmptyStack(stack)) {
                    continue;
                }
                long rest = placeChemical(caches, front, stack, masks[from + i], loads);
                if (rest > 0) {
                    lot = lot != null ? lot : side.lotFor(machine);
                    lot.addChemical(stack, rest);
                }
            }
        }
        int size = side.machines().size();
        side.setCursor(index < 0 || size == 0 ? 0 : (index + 1) % size);
        machine.setLastActive(now);
        dock.returns().wake(machine);
        side.setLastRefusal(DockStatus.NO_REFUSAL);
        dock.saveChanged();
        dock.signalWork(side.side(), now);
        return Result.ACCEPTED;
    }

    /**
     * Quantas pilhas a partir de {@code from} (até {@code to}) entram juntas: a {@code from}, se o item empilha, e as
     * seguintes iguais a ela (item, componentes), com a mesma máscara, se toda face da máscara é de um inventário que
     * aceita a pilha maior que o tamanho do item ({@link DockStacker#takesOversized}); senão 1. O pedido multiplicado
     * do AE2 chega partido no tamanho do item, e a máquina do Mekanism recebe a soma numa inserção, em vez de uma por
     * pilha.
     */
    private static int run(MachineCaches caches, int front, List<ItemStack> items, int[] masks, int from, int to) {
        ItemStack first = items.get(from);
        if (!first.isStackable()) {
            return 1;
        }
        int mask = masks[from];
        int end = from + 1;
        while (end < to && masks[end] == mask && ItemStack.isSameItemSameComponents(items.get(end), first)) {
            end++;
        }
        return end - from == 1 || !oversizedFaces(caches, front, mask) ? 1 : end - from;
    }

    /**
     * Toda face de {@code mask} é de um inventário que aceita a pilha maior que o tamanho do item
     * ({@link DockStacker#takesOversized}): as pilhas iguais podem entrar juntas.
     */
    static boolean oversizedFaces(MachineCaches caches, int front, int mask) {
        for (int face = 0; face < SIDES.length; face++) {
            if ((mask & (1 << face)) != 0) {
                IItemHandler handler = caches.items(absolute(face, front));
                if (handler == null || !DockStacker.takesOversized(handler)) {
                    return false;
                }
            }
        }
        return mask != 0;
    }

    // ------------------------------------------------------------------ faces

    /** A face {@code face} do perfil ({@code RemoteSide.ordinal()}) na máquina virada para {@code front}. */
    private static Direction absolute(int face, int front) {
        return DIRECTIONS[SIDES[face].toAbsolute(front)];
    }

    /**
     * A máquina tem inventário ({@code type} item), tanque (fluido) ou tanque de químico (químico) em alguma face
     * Insere desse tipo no perfil.
     */
    static boolean hasInsertFace(MachineCaches caches, MachineConfig profile, int front, ResourceType type) {
        for (int face = 0; face < SIDES.length; face++) {
            if (profile.face(type, SIDES[face]).mode().inserts() && handler(caches, type, absolute(face, front)) != null) {
                return true;
            }
        }
        return false;
    }

    /** As faces Insere de {@code type} no perfil com inventário (ou tanque) na máquina, um bit por face. */
    static int insertFaces(MachineCaches caches, MachineConfig profile, int front, ResourceType type) {
        int mask = 0;
        for (int face = 0; face < SIDES.length; face++) {
            if (profile.face(type, SIDES[face]).mode().inserts()
                    && handler(caches, type, absolute(face, front)) != null) {
                mask |= 1 << face;
            }
        }
        return mask;
    }

    private static @Nullable Object handler(MachineCaches caches, ResourceType type, Direction face) {
        return switch (type) {
            case FLUID -> caches.fluids(face);
            case CHEMICAL -> caches.chemicals(face);
            default -> caches.items(face);
        };
    }

    /**
     * Simula cada item nas faces Insere de itens do perfil e escreve em {@code masks[offset + i]} as faces que aceitam
     * o item {@code i} (um bit por face, na ordem do perfil): {@code whole}, a que aceita a pilha inteira; senão, a que
     * aceita parte. Uma pilha vazia fica com a máscara 0 e não conta. Devolve {@code true} se toda pilha não vazia tem
     * face; sem itens, {@code true} sem consultar a máquina.
     */
    static boolean itemMasks(MachineCaches caches, MachineConfig profile, int front, List<ItemStack> items, int offset,
            int[] masks, boolean whole) {
        return itemMasks(caches, profile, front, items, 0, offset, masks, whole);
    }

    /**
     * O {@link #itemMasks(MachineCaches, MachineConfig, int, List, int, int[], boolean)} só das pilhas a partir de
     * {@code from}: a pilha {@code i} vai em {@code masks[offset + i - from]}. A pilha igual à anterior (item,
     * componentes e quantidade) não é simulada de novo: a simulação não soma, então a resposta é a mesma.
     */
    static boolean itemMasks(MachineCaches caches, MachineConfig profile, int front, List<ItemStack> items, int from,
            int offset, int[] masks, boolean whole) {
        int count = items.size();
        if (count <= from) {
            return true;
        }
        int base = offset - from;
        for (int i = from; i < count; i++) {
            masks[base + i] = 0;
        }
        for (int face = 0; face < SIDES.length; face++) {
            if (!profile.face(ResourceType.ITEM, SIDES[face]).mode().inserts()) {
                continue;
            }
            IItemHandler handler = caches.items(absolute(face, front));
            if (handler == null) {
                continue;
            }
            ItemStack previous = ItemStack.EMPTY;
            boolean previousFits = false;
            for (int i = from; i < count; i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                boolean fits;
                if (!previous.isEmpty() && ItemStack.matches(stack, previous)) {
                    fits = previousFits;
                } else {
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, stack, true);
                    fits = whole ? rest.isEmpty() : rest.getCount() < stack.getCount();
                    previous = stack;
                    previousFits = fits;
                }
                if (fits) {
                    masks[base + i] |= 1 << face;
                }
            }
        }
        for (int i = from; i < count; i++) {
            if (masks[base + i] == 0 && !items.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * As faces Insere de itens do perfil que aceitam parte de {@code stack} (um bit por face), pela inserção simulada:
     * a máscara de uma linha do pendente.
     */
    static int itemMask(MachineCaches caches, MachineConfig profile, int front, ItemStack stack) {
        int mask = 0;
        for (int face = 0; face < SIDES.length; face++) {
            if (profile.face(ResourceType.ITEM, SIDES[face]).mode().inserts()) {
                IItemHandler handler = caches.items(absolute(face, front));
                if (handler != null && ItemHandlerHelper.insertItemStacked(handler, stack, true).getCount()
                        < stack.getCount()) {
                    mask |= 1 << face;
                }
            }
        }
        return mask;
    }

    /** O {@link #itemMask} de um fluido. */
    static int fluidMask(MachineCaches caches, MachineConfig profile, int front, FluidStack stack) {
        int mask = 0;
        for (int face = 0; face < SIDES.length; face++) {
            if (profile.face(ResourceType.FLUID, SIDES[face]).mode().inserts()) {
                IFluidHandler handler = caches.fluids(absolute(face, front));
                if (handler != null && handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) > 0) {
                    mask |= 1 << face;
                }
            }
        }
        return mask;
    }

    /** O {@link #itemMask} de um químico ({@code ChemicalStack}). Só com o Mekanism. */
    static int chemicalMask(MachineCaches caches, MachineConfig profile, int front, Object stack) {
        int mask = 0;
        for (int face = 0; face < SIDES.length; face++) {
            if (profile.face(ResourceType.CHEMICAL, SIDES[face]).mode().inserts()) {
                Object handler = caches.chemicals(absolute(face, front));
                if (handler != null && Chemicals.insert(handler, stack, true) > 0) {
                    mask |= 1 << face;
                }
            }
        }
        return mask;
    }

    /** O {@link #itemMasks} dos fluidos, pelo {@code fill} simulado nas faces Insere de fluidos. */
    static boolean fluidMasks(MachineCaches caches, MachineConfig profile, int front, List<FluidStack> fluids,
            int offset, int[] masks, boolean whole) {
        int count = fluids.size();
        if (count == 0) {
            return true;
        }
        for (int i = 0; i < count; i++) {
            masks[offset + i] = 0;
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
                    masks[offset + i] |= 1 << face;
                }
            }
        }
        for (int i = 0; i < count; i++) {
            if (masks[offset + i] == 0 && !fluids.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * O {@link #itemMasks} dos químicos ({@code ChemicalStack}, pela ponte {@link Chemicals}), pela inserção simulada
     * nas faces Insere de químicos. Só com o Mekanism (a lista vem vazia sem ele).
     */
    static boolean chemicalMasks(MachineCaches caches, MachineConfig profile, int front, List<?> chemicals,
            int offset, int[] masks, boolean whole) {
        int count = chemicals.size();
        if (count == 0) {
            return true;
        }
        for (int i = 0; i < count; i++) {
            masks[offset + i] = 0;
        }
        for (int face = 0; face < SIDES.length; face++) {
            if (!profile.face(ResourceType.CHEMICAL, SIDES[face]).mode().inserts()) {
                continue;
            }
            Object handler = caches.chemicals(absolute(face, front));
            if (handler == null) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                Object stack = chemicals.get(i);
                if (Chemicals.isEmptyStack(stack)) {
                    continue;
                }
                long inserted = Chemicals.insert(handler, stack, true);
                if (whole ? inserted >= Chemicals.amount(stack) : inserted > 0) {
                    masks[offset + i] |= 1 << face;
                }
            }
        }
        for (int i = 0; i < count; i++) {
            if (masks[offset + i] == 0 && !Chemicals.isEmptyStack(chemicals.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Insere {@code stack} de verdade: primeiro na face mais seletiva de {@code mask}, e a sobra nas outras faces da
     * máscara, na ordem do perfil, pelo {@code stacker} (o helper empilhado com a dica do slot da pilha anterior).
     * Devolve a sobra ({@code stack} mesmo, se nada entrou).
     */
    static ItemStack placeItem(MachineCaches caches, int front, ItemStack stack, int mask, int[] loads,
            DockStacker stacker) {
        int first = DockPick.selectiveFace(mask, loads);
        if (first < 0) {
            return stack;
        }
        ItemStack rest = insertItem(caches, front, first, stack, stacker);
        for (int face = 0; face < SIDES.length && !rest.isEmpty(); face++) {
            if (face != first && (mask & (1 << face)) != 0) {
                rest = insertItem(caches, front, face, rest, stacker);
            }
        }
        return rest;
    }

    private static ItemStack insertItem(MachineCaches caches, int front, int face, ItemStack stack,
            DockStacker stacker) {
        IItemHandler handler = caches.items(absolute(face, front));
        return handler == null ? stack : stacker.insert(handler, stack);
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

    /**
     * O {@link #placeItem} dos químicos: devolve quanto de {@code stack} sobrou (a quantidade dele, se nada entrou).
     * Não muda {@code stack}; só aloca a cópia com a sobra quando a primeira face aceita parte.
     */
    static long placeChemical(MachineCaches caches, int front, Object stack, int mask, int[] loads) {
        long amount = Chemicals.amount(stack);
        int first = DockPick.selectiveFace(mask, loads);
        if (first < 0) {
            return amount;
        }
        long rest = amount - insertChemical(caches, front, first, stack);
        for (int face = 0; face < SIDES.length && rest > 0; face++) {
            if (face != first && (mask & (1 << face)) != 0) {
                rest -= insertChemical(caches, front, face, Chemicals.copyWithAmount(stack, rest));
            }
        }
        return rest;
    }

    private static long insertChemical(MachineCaches caches, int front, int face, Object stack) {
        Object handler = caches.chemicals(absolute(face, front));
        return handler == null ? 0 : Chemicals.insert(handler, stack, false);
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

    /** O pedido traz algum químico não vazio. Só com o Mekanism (sem ele, o pedido com químico já foi recusado). */
    private static boolean hasChemical(List<?> chemicals) {
        for (int i = 0; i < chemicals.size(); i++) {
            if (!Chemicals.isEmptyStack(chemicals.get(i))) {
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
        private @Nullable List<?> chemicals;
        /** O pedido tem fluido ou químico: a máquina que recusa dorme pelo teto mais curto, o deles. */
        private boolean scalar;
        private long now;

        void start(PatternDockBlockEntity dock, DockSide side, List<ItemStack> items, List<FluidStack> fluids,
                List<?> chemicals, boolean scalar, long now) {
            this.dock = dock;
            this.side = side;
            this.items = items;
            this.fluids = fluids;
            this.chemicals = chemicals;
            this.scalar = scalar;
            this.now = now;
        }

        void clear() {
            dock = null;
            side = null;
            items = null;
            fluids = null;
            chemicals = null;
        }

        /** Ativa (no alcance e no limite), sem pendente, acordada e com o chunk carregado. */
        private boolean usable(int index) {
            DockMachine machine = side.machines().get(index);
            return machine.active() && machine.pending() == null && !machine.asleep(now)
                    && machine.caches(dock).available();
        }

        /**
         * Aceita o pedido inteiro (cada pilha numa face Insere do tipo dela); senão dorme, pelo teto de sono dos
         * fluidos se o pedido tem fluido ou químico (o mais curto). Deixa as faces no rascunho do lado: os itens, os
         * fluidos e os químicos, nessa ordem.
         */
        private boolean accepts(int index) {
            DockMachine machine = side.machines().get(index);
            MachineCaches caches = machine.caches(dock);
            int itemCount = items.size();
            int fluidCount = fluids.size();
            int[] masks = side.masks(itemCount + fluidCount + chemicals.size());
            boolean accepted = itemMasks(caches, side.profile(), machine.front(), items, 0, masks, true)
                    && fluidMasks(caches, side.profile(), machine.front(), fluids, itemCount, masks, true)
                    && chemicalMasks(caches, side.profile(), machine.front(), chemicals, itemCount + fluidCount,
                            masks, true);
            if (!accepted) {
                machine.sleepUntil(now + NetworkManager.maxSleepTicks(scalar ? ResourceType.FLUID
                        : ResourceType.ITEM));
            }
            return accepted;
        }
    }
}
