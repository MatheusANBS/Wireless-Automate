package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.EngineVisitor;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.RateLimiter;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A volta do resultado: o visitante da Doca no laço de transferência do {@link NetworkManager} (um por Doca, posto no
 * {@code onLoad} do servidor e tirado quando ela sai do mundo). Tira das faces Extrai do perfil de cada máquina
 * acordada (itens e fluidos) e põe no destino daquele lado da Doca: direto na rede ME, se o vizinho é um nó do AE2
 * ({@link DockAe2#target}: a CPU conta o resultado ali, sem o retorno de 9 espaços do Pattern Provider); na rede do
 * Autocrafter, se é um do RS2 ({@link DockRs2#target}); senão no {@link IItemHandler}/{@link IFluidHandler} do vizinho,
 * pela face encostada nela. A rede é guardada no lado e esquecida quando o vizinho muda.
 *
 * <p>Acordadas são as máquinas que receberam um pedido ({@link DockDelivery}) ou avisaram mudança
 * ({@link PatternDockBlockEntity#machineChanged}), numa fila por lado. Numa visita, cada uma dá até a vazão do tier por
 * tipo (um balde por lado e tipo, a mesma tabela do roteador): a que não tem nada para dar pelas faces Extrai sai da
 * fila; a que deu volta ao fim dela. Só sai o que a face Extrai entrega: o combustível e as entradas ficam (a face de
 * baixo da fornalha só dá o produto). A inserção é simulada antes de tirar da máquina, então nada sai sem caber. Na
 * rede do RS2 a simulação não vale (a tarefa só pega o resultado que espera na execução, e com os discos cheios a
 * simulação daria zero): ali a volta simula a extração, insere de verdade e tira da máquina o que entrou
 * ({@link DockReturnNetwork#insertsFirst}).
 *
 * <p>Provedor cheio (o destino recusou algo que a máquina tinha para dar: o inventário cheio, a rede sem espaço ou o
 * nó dela sem energia ou canal): o lado marca {@link DockSide#providerFull} e dorme até o vizinho mudar
 * ({@link #providerChanged}, o {@code setChanged} dele; ou o cache invalidado, o bloco trocou) ou o teto de sono do
 * motor. Sem destino, o lado dorme até o cache avisar ou o teto.
 *
 * <p>Custo: parado, nada (sem acordada, {@link #wakeAt} é nunca, e o laço só lê esse número). Por visita, proporcional
 * às acordadas, até {@link #MAX_MACHINES_PER_VISIT} (mais que isso pede outra volta, {@link #MORE}); por máquina, uma
 * simulação de extração e uma de inserção por slot da face Extrai, pelos caches de capability da máquina
 * ({@link MachineCaches}) e do vizinho ({@link BlockCapabilityCache}, um por lado e tipo, criado na primeira visita).
 * Só a thread do servidor mexe aqui.
 */
public final class DockReturn implements EngineVisitor {
    /** Máquinas olhadas por visita, somando os lados; com mais acordadas, a visita para e pede outra volta. */
    static final int MAX_MACHINES_PER_VISIT = 32;

    private static final RemoteSide[] FACES = RemoteSide.values();
    private static final Direction[] DIRECTIONS = Direction.values();

    private final PatternDockBlockEntity dock;
    /** A fila e o destino de cada lado ({@code Direction.ordinal()}), criados na primeira máquina acordada dele. */
    private final Lane[] lanes = new Lane[PatternDockBlockEntity.SIDES];
    /** Máquinas acordadas somando os lados (as filas podem ter também desvinculadas, largadas na visita). */
    private int awake;
    /** A vazão do tier por segundo (0 = sem limite), lida da config no {@link #refreshRates}. */
    private long itemRate;
    private long fluidRate;
    /** Máquinas que a visita em curso ainda pode olhar. */
    private int left;
    /** Visitas desde a carga; só para os testes. */
    private long visits;

    DockReturn(PatternDockBlockEntity dock) {
        this.dock = dock;
    }

    /** Quantas visitas o motor fez a esta Doca desde que ela carregou (para os testes). */
    public long visits() {
        return visits;
    }

    /** Quantas máquinas estão na fila da volta, somando os lados. */
    public int awakeCount() {
        return awake;
    }

    /** Põe {@code machine} na fila da volta do lado dela, se ainda não está (só as ativas). */
    void wake(DockMachine machine) {
        if (machine.returnAwake() || !machine.active()) {
            return;
        }
        Lane lane = lanes[machine.side()];
        if (lane == null) {
            lane = new Lane(machine.side(), itemRate, fluidRate);
            lanes[machine.side()] = lane;
        }
        machine.setReturnAwake(true);
        lane.queue.addLast(machine);
        awake++;
    }

    /**
     * O block entity em {@code neighbor}, encostado na Doca em {@code dockPos}, mudou ({@link PatternDockBlock#onNeighborChange}):
     * se o lado dele dorme com "provedor cheio", acorda para a próxima volta (o motor lê o {@link #wakeAt} a cada tick).
     * Custo: uma conta de direção e duas leituras; nada com o lado acordado ou sem fila.
     */
    void providerChanged(BlockPos dockPos, BlockPos neighbor) {
        Direction direction = Direction.fromDelta(neighbor.getX() - dockPos.getX(), neighbor.getY() - dockPos.getY(),
                neighbor.getZ() - dockPos.getZ());
        if (direction == null) {
            return;
        }
        Lane lane = lanes[direction.ordinal()];
        DockSide side = dock.side(direction.ordinal());
        if (lane != null && lane.sleepUntil != 0 && side != null && side.providerFull()) {
            lane.sleepUntil = 0;
        }
    }

    /** Relê a vazão do tier da Doca (carga, troca de tier e recarga da config). */
    void refreshRates() {
        Config.TierValues tier = Config.TIERS.get(dock.tier());
        itemRate = tier.rate(ResourceType.ITEM);
        fluidRate = tier.rate(ResourceType.FLUID);
        for (Lane lane : lanes) {
            if (lane != null) {
                lane.items.setRatePerSecond(itemRate);
                lane.fluid.setRatePerSecond(fluidRate);
            }
        }
    }

    /** A Doca saiu do mundo: esquece as filas e os caches dos vizinhos (que deixam de avisar). */
    void discard() {
        for (int i = 0; i < lanes.length; i++) {
            Lane lane = lanes[i];
            if (lane == null) {
                continue;
            }
            for (DockMachine machine : lane.queue) {
                machine.setReturnAwake(false);
            }
            lane.generation++;
            lanes[i] = null;
        }
        awake = 0;
    }

    /** A config recarregou: o alcance, o limite e a vazão do tier podem ter mudado. */
    @Override
    public void configChanged() {
        dock.reachChanged();
    }

    @Override
    public long wakeAt() {
        if (awake == 0) {
            return Long.MAX_VALUE;
        }
        long at = Long.MAX_VALUE;
        for (Lane lane : lanes) {
            if (lane != null && !lane.queue.isEmpty() && lane.sleepUntil < at) {
                at = lane.sleepUntil;
            }
        }
        return at;
    }

    @Override
    public int visit(long now) {
        visits++;
        if (dock.isRemoved() || !(dock.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        left = MAX_MACHINES_PER_VISIT;
        int result = 0;
        for (Lane lane : lanes) {
            if (lane == null || lane.queue.isEmpty() || now < lane.sleepUntil) {
                continue;
            }
            if (left == 0) {
                break;
            }
            if (visitLane(level, lane, now)) {
                result |= MOVED;
            }
        }
        // Só o teto de máquinas pede outra volta: o balde vazio ou o provedor cheio esperam o tick seguinte.
        return left == 0 ? result | MORE : result;
    }

    /** As máquinas da fila de {@code lane}, até o que resta da visita ({@link #left}). Devolve se alguma deu algo. */
    private boolean visitLane(ServerLevel level, Lane lane, long now) {
        DockSide side = dock.side(lane.side);
        MachineConfig profile = side == null ? null : side.profile();
        boolean wantsItems = profile != null && extracts(profile, ResourceType.ITEM);
        boolean wantsFluids = profile != null && extracts(profile, ResourceType.FLUID);
        if (!wantsItems && !wantsFluids) {
            // Sem lado ou sem face Extrai no perfil: nada a devolver.
            drop(lane);
            return false;
        }
        IItemHandler itemsOut = wantsItems ? destinationItems(level, lane) : null;
        IFluidHandler fluidOut = wantsFluids ? destinationFluids(level, lane) : null;
        if (itemsOut == null && fluidOut == null) {
            // Sem provedor (ou com o chunk dele descarregado): dorme até o vizinho mudar ou o teto.
            lane.sleepUntil = now + sleepTicks(wantsFluids);
            side.setProviderFull(false);
            return false;
        }
        boolean moved = false;
        boolean full = false;
        int count = Math.min(lane.queue.size(), left);
        for (int i = 0; i < count; i++) {
            DockMachine machine = lane.queue.pollFirst();
            left--;
            if (dock.machineAt(machine.pos().asLong()) != machine || !machine.active()) {
                forget(machine);
                continue;
            }
            MachineCaches caches = machine.caches(dock);
            if (!caches.available()) {
                // Descarregada: volta pela carga do chunk (o aviso da máquina).
                forget(machine);
                continue;
            }
            if (side.hasPending() && side.pendingAtKey() == machine.pos().asLong()) {
                DockDelivery.flushPending(dock, lane.side);
            }
            int outcome = 0;
            if (itemsOut != null) {
                outcome = giveItems(level, side, caches, profile, machine.front(), lane, itemsOut, now);
            }
            if (fluidOut != null && (outcome & FULL) == 0) {
                outcome |= giveFluids(side, caches, profile, machine.front(), lane, fluidOut, now);
            }
            if ((outcome & GAVE) != 0) {
                moved = true;
                machine.setLastActive(now);
            }
            if ((outcome & HAS_MORE) == 0) {
                forget(machine);
            } else {
                lane.queue.addLast(machine);
            }
            if ((outcome & FULL) != 0) {
                full = true;
                break;
            }
            if ((outcome & NO_RATE) != 0) {
                // O balde do tipo esvaziou: o resto da fila espera o tick seguinte.
                break;
            }
        }
        side.setProviderFull(full);
        if (full) {
            lane.sleepUntil = now + sleepTicks(wantsFluids);
        }
        if (moved) {
            dock.signalWork(lane.side, now);
        }
        return moved;
    }

    /** O teto do sono do lado: o dos fluidos (mais curto) se o perfil devolve fluido, senão o dos itens. */
    private static int sleepTicks(boolean fluids) {
        return NetworkManager.maxSleepTicks(fluids ? ResourceType.FLUID : ResourceType.ITEM);
    }

    /** O perfil tem alguma face Extrai de {@code type}. */
    private static boolean extracts(MachineConfig profile, ResourceType type) {
        for (RemoteSide face : FACES) {
            if (profile.face(type, face).mode().extracts()) {
                return true;
            }
        }
        return false;
    }

    /** Devolveu algo. */
    private static final int GAVE = 1;
    /** A máquina ainda tem o que dar (não coube, ou o balde acabou): fica na fila. */
    private static final int HAS_MORE = 2;
    /** O provedor recusou algo que a máquina tinha para dar. */
    private static final int FULL = 4;
    /** O balde de um tipo acabou no meio da máquina. */
    private static final int NO_RATE = 8;

    private int giveItems(ServerLevel level, DockSide side, MachineCaches caches, MachineConfig profile, int front,
            Lane lane, IItemHandler out, long now) {
        int outcome = 0;
        long limit = lane.items.available(now);
        DockReturnNetwork first = out instanceof DockReturnNetwork network && network.insertsFirst() ? network : null;
        for (int face = 0; face < FACES.length; face++) {
            if (!profile.face(ResourceType.ITEM, FACES[face]).mode().extracts()) {
                continue;
            }
            IItemHandler handler = caches.items(DIRECTIONS[FACES[face].toAbsolute(front)]);
            if (handler == null) {
                continue;
            }
            for (int slot = 0, slots = handler.getSlots(); slot < slots; slot++) {
                if (limit <= 0) {
                    return outcome | HAS_MORE | NO_RATE;
                }
                ItemStack offer = handler.extractItem(slot, (int) Math.min(limit, Integer.MAX_VALUE), true);
                if (offer.isEmpty()) {
                    continue;
                }
                int fits;
                int gave;
                if (first != null) {
                    fits = offer.getCount() - first.insertItem(0, offer, false).getCount();
                    if (fits <= 0) {
                        return outcome | HAS_MORE | FULL;
                    }
                    gave = takeInserted(level, first, handler, slot, offer, fits);
                } else {
                    fits = offer.getCount() - ItemHandlerHelper.insertItemStacked(out, offer, true).getCount();
                    if (fits <= 0) {
                        return outcome | HAS_MORE | FULL;
                    }
                    ItemStack taken = handler.extractItem(slot, fits, false);
                    if (taken.isEmpty()) {
                        continue;
                    }
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(out, taken, false);
                    if (!rest.isEmpty()) {
                        // O provedor aceitou na simulação e recusou de verdade: volta para a máquina, ou para o chão.
                        keep(level, handler, rest);
                    }
                    gave = taken.getCount() - rest.getCount();
                }
                if (gave > 0) {
                    lane.items.consume(gave);
                    limit -= gave;
                    side.addReturned(gave);
                    outcome |= GAVE;
                }
                if (fits < offer.getCount()) {
                    return outcome | HAS_MORE | FULL;
                }
                if (!handler.extractItem(slot, 1, true).isEmpty()) {
                    // Um slot maior que uma extração (gaveta, barril grande): fica para a próxima visita.
                    outcome |= HAS_MORE;
                }
            }
        }
        return outcome;
    }

    private int giveFluids(DockSide side, MachineCaches caches, MachineConfig profile, int front, Lane lane,
            IFluidHandler out, long now) {
        int outcome = 0;
        long limit = lane.fluid.available(now);
        DockReturnNetwork first = out instanceof DockReturnNetwork network && network.insertsFirst() ? network : null;
        for (int face = 0; face < FACES.length; face++) {
            if (!profile.face(ResourceType.FLUID, FACES[face]).mode().extracts()) {
                continue;
            }
            IFluidHandler handler = caches.fluids(DIRECTIONS[FACES[face].toAbsolute(front)]);
            if (handler == null) {
                continue;
            }
            // Um drain por tanque, no máximo (o drain sem tipo tira do primeiro que der); o que sobrar fica para a
            // próxima visita.
            for (int round = 0, tanks = Math.max(1, handler.getTanks()); ; round++) {
                if (limit <= 0) {
                    return outcome | HAS_MORE | NO_RATE;
                }
                FluidStack offer = handler.drain((int) Math.min(limit, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
                if (offer.isEmpty()) {
                    break;
                }
                if (round == tanks) {
                    outcome |= HAS_MORE;
                    break;
                }
                // Na rede que insere antes, o que cabe já entrou: falta tirar da máquina.
                int fits = out.fill(offer, first != null ? IFluidHandler.FluidAction.EXECUTE
                        : IFluidHandler.FluidAction.SIMULATE);
                if (fits <= 0) {
                    return outcome | HAS_MORE | FULL;
                }
                FluidStack taken = handler.drain(offer.copyWithAmount(fits), IFluidHandler.FluidAction.EXECUTE);
                int gave;
                if (first != null) {
                    gave = taken.getAmount();
                    if (gave < fits) {
                        reclaimFluid(first, offer.copyWithAmount(fits - gave));
                    }
                } else {
                    gave = taken.isEmpty() ? 0 : out.fill(taken, IFluidHandler.FluidAction.EXECUTE);
                    if (gave < taken.getAmount()) {
                        // Recusou de verdade o que aceitou na simulação: o resto volta para a máquina.
                        handler.fill(taken.copyWithAmount(taken.getAmount() - gave), IFluidHandler.FluidAction.EXECUTE);
                    }
                }
                if (taken.isEmpty()) {
                    break;
                }
                if (gave > 0) {
                    lane.fluid.consume(gave);
                    limit -= gave;
                    side.addReturnedFluid(gave);
                    outcome |= GAVE;
                }
                if (fits < offer.getAmount()) {
                    return outcome | HAS_MORE | FULL;
                }
            }
        }
        return outcome;
    }

    /**
     * A volta para uma rede que insere antes ({@link DockReturnNetwork#insertsFirst}): {@code fits} de {@code offer} já
     * entraram na rede; tira esse tanto da máquina e devolve quanto saiu. A máquina dá o que a simulação ofereceu (mesmo
     * slot, mesmo tick, nada no meio); se der menos, o que entrou a mais sai da rede de novo ({@link #reclaimItems}).
     */
    private int takeInserted(ServerLevel level, DockReturnNetwork network, IItemHandler handler, int slot,
            ItemStack offer, int fits) {
        ItemStack taken = handler.extractItem(slot, fits, false);
        int gave = taken.getCount();
        if (!taken.isEmpty() && !ItemStack.isSameItemSameComponents(taken, offer)) {
            // Saiu outro item do slot, que não entrou na rede: volta para a máquina, ou para o chão.
            keep(level, handler, taken);
            gave = 0;
        }
        if (gave < fits) {
            reclaimItems(network, offer.copyWithCount(fits - gave));
        }
        return gave;
    }

    /**
     * Tira da rede o que entrou nela e a máquina não deu. O que uma tarefa já pegou não volta (só o armazenamento
     * devolve): fica registrado no log, porque só acontece com uma máquina que promete na simulação e não cumpre.
     */
    private static void reclaimItems(DockReturnNetwork network, ItemStack extra) {
        long back = network.reclaim(extra);
        if (back < extra.getCount()) {
            WirelessAutomate.LOGGER.warn("Doca: a máquina deu menos que ofereceu, e {} {} ficaram a mais na rede",
                    extra.getCount() - back, extra.getItem());
        }
    }

    private static void reclaimFluid(DockReturnNetwork network, FluidStack extra) {
        long back = network.reclaim(extra);
        if (back < extra.getAmount()) {
            WirelessAutomate.LOGGER.warn("Doca: a máquina deu menos que ofereceu, e {} mB de {} ficaram a mais na rede",
                    extra.getAmount() - back, extra.getFluid());
        }
    }

    /** O que o provedor recusou depois de aceitar na simulação: de volta na máquina, e o que não voltar, no chão. */
    private void keep(ServerLevel level, IItemHandler machine, ItemStack rest) {
        ItemStack left = ItemHandlerHelper.insertItemStacked(machine, rest, false);
        if (!left.isEmpty()) {
            BlockPos pos = dock.getBlockPos();
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), left);
        }
    }

    private void forget(DockMachine machine) {
        machine.setReturnAwake(false);
        awake--;
    }

    private void drop(Lane lane) {
        while (!lane.queue.isEmpty()) {
            forget(lane.queue.pollFirst());
        }
    }

    /**
     * O destino dos itens do lado: a rede do vizinho ({@link Lane#network}) ou o inventário dele pela face encostada na
     * Doca; {@code null} sem nenhum dos dois ou com o chunk descarregado. O cache do inventário é criado mesmo com a
     * rede: é ele que avisa a troca do vizinho.
     */
    private @Nullable IItemHandler destinationItems(ServerLevel level, Lane lane) {
        BlockPos pos = lane.neighbor(dock.getBlockPos());
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (lane.itemCache == null) {
            int created = lane.generation;
            lane.itemCache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, pos,
                    DIRECTIONS[lane.side].getOpposite(), () -> !dock.isRemoved() && lane.generation == created,
                    lane::neighborChanged);
        }
        DockReturnNetwork network = lane.network(level, pos);
        return network != null ? network : lane.itemCache.getCapability();
    }

    /** O {@link #destinationItems} dos fluidos. */
    private @Nullable IFluidHandler destinationFluids(ServerLevel level, Lane lane) {
        BlockPos pos = lane.neighbor(dock.getBlockPos());
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (lane.fluidCache == null) {
            int created = lane.generation;
            lane.fluidCache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, level, pos,
                    DIRECTIONS[lane.side].getOpposite(), () -> !dock.isRemoved() && lane.generation == created,
                    lane::neighborChanged);
        }
        DockReturnNetwork network = lane.network(level, pos);
        return network != null ? network : lane.fluidCache.getCapability();
    }

    /** Um lado na volta: a fila das acordadas, o sono, os baldes da vazão e os caches do vizinho. */
    private static final class Lane {
        final int side;
        final ArrayDeque<DockMachine> queue = new ArrayDeque<>();
        final RateLimiter items;
        final RateLimiter fluid;
        /** Tick do servidor até o qual o lado dorme (provedor cheio ou ausente); 0 acordado. */
        long sleepUntil;
        /** Muda no {@link #discard}: os caches criados antes deixam de valer e de avisar. */
        int generation;
        @Nullable BlockCapabilityCache<IItemHandler, @Nullable Direction> itemCache;
        @Nullable BlockCapabilityCache<IFluidHandler, @Nullable Direction> fluidCache;
        private @Nullable BlockPos neighbor;
        /** A rede do vizinho (um nó do AE2 ou um Autocrafter do RS2); {@link #networkKnown}: já olhou este vizinho. */
        private @Nullable DockReturnNetwork network;
        private boolean networkKnown;

        Lane(int side, long itemRate, long fluidRate) {
            this.side = side;
            this.items = new RateLimiter(itemRate);
            this.fluid = new RateLimiter(fluidRate);
        }

        BlockPos neighbor(BlockPos dock) {
            if (neighbor == null) {
                neighbor = dock.relative(DIRECTIONS[side]);
            }
            return neighbor;
        }

        /**
         * A rede do vizinho em {@code pos}, olhada uma vez por vizinho: a ME de um nó do AE2 (antes do inventário, que
         * no Pattern Provider é o retorno de 9 espaços dele) ou a de um Autocrafter do RS2 (que não tem inventário);
         * {@code null} para um vizinho comum.
         */
        @Nullable DockReturnNetwork network(ServerLevel level, BlockPos pos) {
            if (!networkKnown) {
                networkKnown = true;
                network = DockAe2.target(level, pos, DIRECTIONS[side].getOpposite());
                if (network == null) {
                    network = DockRs2.target(level, pos);
                }
            }
            return network;
        }

        /** O bloco do vizinho mudou (o cache foi invalidado): acorda o lado e esquece a rede dele. */
        void neighborChanged() {
            sleepUntil = 0;
            networkKnown = false;
            if (network != null) {
                network.reset();
                network = null;
            }
        }
    }
}
