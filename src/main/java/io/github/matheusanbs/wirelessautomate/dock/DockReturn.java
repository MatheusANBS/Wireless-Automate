package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.EngineVisitor;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.RateLimiter;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayDeque;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
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
 * acordada (itens, fluidos e, com o Mekanism, químicos) e põe no destino daquele lado da Doca: direto na rede ME, se o
 * vizinho é um nó do AE2 ({@link DockAe2#target}: a CPU conta o resultado ali, sem o retorno de 9 espaços do Pattern
 * Provider); na rede do Autocrafter, se é um do RS2 ({@link DockRs2#target}); senão no
 * {@link IItemHandler}/{@link IFluidHandler} (ou no tanque de químico, pela ponte {@link Chemicals}) do vizinho, pela
 * face encostada nela. A rede é guardada no lado e esquecida quando o vizinho muda. O químico só vai a uma rede que o
 * aceita ({@link DockReturnNetwork#acceptsChemicals}); numa que não aceita, fica na máquina.
 *
 * <p>Acordadas são as máquinas que receberam um pedido ({@link DockDelivery}) ou avisaram mudança
 * ({@link PatternDockBlockEntity#machineChanged}), numa fila por lado. Numa visita, cada uma dá até a vazão do tier por
 * tipo (um balde por lado e tipo, a mesma tabela do roteador): a que ainda tem o que dar volta ao fim da fila; a que
 * não tem nada para dar pelas faces Extrai vai ao recuo ({@link Lane#resting}): é revisitada depois de um intervalo
 * que dobra a cada visita vazia, de 1 tick até o teto de sono do motor. Uma máquina lenta pode não avisar quando a
 * saída fica pronta (as do Mekanism nunca chamam {@code Level.blockEntityChanged}, então o mixin não as vê), e por
 * isso só sai da fila quando o recuo chegou ao teto, as faces Insere estão vazias (nada mais para processar) e a última
 * atividade foi há mais de {@link #IDLE_GRACE_TICKS} (a folga de uma receita que consome a entrada no começo). O aviso
 * da máquina e uma entrega nova a trazem de volta na hora e zeram o recuo. Só sai o que a face Extrai entrega: o
 * combustível e as entradas ficam (a face de baixo da fornalha só dá o produto). A inserção é simulada antes de tirar
 * da máquina, então nada sai sem caber. Na rede do RS2 a simulação não vale (a tarefa só pega o resultado que espera
 * na execução, e com os discos cheios a simulação daria zero): ali a volta simula a extração, insere de verdade e
 * tira da máquina o que entrou ({@link DockReturnNetwork#insertsFirst}).
 *
 * <p>Provedor cheio (o destino recusou algo que a máquina tinha para dar: o inventário cheio, a rede sem espaço ou o
 * nó dela sem energia ou canal): o lado marca {@link DockSide#providerFull} e dorme até o vizinho mudar
 * ({@link #providerChanged}, o {@code setChanged} dele; ou o cache invalidado, o bloco trocou) ou o teto de sono do
 * motor. Sem destino, o lado dorme até o cache avisar ou o teto.
 *
 * <p>Custo: parado, nada (sem acordada, {@link #wakeAt} é nunca, e o laço só lê esse número). Em recuo, uma visita
 * por intervalo, que dobra até o teto (uma máquina ociosa que guarda algo nas faces Insere, como o combustível da
 * fornalha, fica no teto: uma visita a cada 100 ticks, 20 com fluido ou químico, como uma porta do roteador). A
 * passagem do recuo para a fila percorre o recuo do lado só quando o primeiro prazo dele vence. Por visita,
 * proporcional às acordadas, até {@link #MAX_MACHINES_PER_VISIT} (mais que isso pede outra volta, {@link #MORE});
 * por máquina, uma simulação de extração e uma de inserção por pilha tirada de cada slot (ou tanque) da face Extrai
 * (um slot maior que uma extração rende até {@link #MAX_EXTRACTIONS_PER_SLOT} por visita), pelos caches de
 * capability da máquina ({@link MachineCaches}) e do vizinho ({@link BlockCapabilityCache}, um por lado e tipo,
 * criado na primeira visita).
 * Só a thread do servidor mexe aqui.
 */
public final class DockReturn implements EngineVisitor {
    /** Máquinas olhadas por visita, somando os lados; com mais acordadas, a visita para e pede outra volta. */
    static final int MAX_MACHINES_PER_VISIT = 32;
    /**
     * Extrações por slot da face Extrai numa visita, no máximo: 64 pilhas de 64, um slot de saída inteiro da Infinite
     * Factory do Mekanism Extras (4.096), que entrega uma pilha por chamada e a 256× produz 256 por slot por tick.
     */
    static final int MAX_EXTRACTIONS_PER_SLOT = 64;
    /**
     * A folga desde a última entrega ou volta da máquina antes que ela possa sair da fila (1 min): cobre a receita
     * longa de uma máquina que consome a entrada no começo, com as faces Insere já vazias e a saída ainda por vir.
     */
    public static final int IDLE_GRACE_TICKS = 1200;

    private static final RemoteSide[] FACES = RemoteSide.values();
    private static final Direction[] DIRECTIONS = Direction.values();

    private final PatternDockBlockEntity dock;
    /** A fila e o destino de cada lado ({@code Direction.ordinal()}), criados na primeira máquina acordada dele. */
    private final Lane[] lanes = new Lane[PatternDockBlockEntity.SIDES];
    /**
     * Máquinas acordadas somando os lados, na fila ou em recuo (as filas podem ter também desvinculadas, largadas na
     * visita).
     */
    private int awake;
    /** A vazão do tier por segundo (0 = sem limite), lida da config no {@link #refreshRates}. */
    private long itemRate;
    private long fluidRate;
    private long chemicalRate;
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

    /** Quantas máquinas estão na fila da volta (para já ou em recuo), somando os lados. */
    public int awakeCount() {
        return awake;
    }

    /**
     * Põe {@code machine} na fila da volta do lado dela (só as ativas) e zera o recuo; a que está em recuo volta na
     * próxima visita do lado.
     */
    void wake(DockMachine machine) {
        if (!machine.active()) {
            return;
        }
        machine.setReturnInterval(0);
        if (machine.returnAwake()) {
            if (machine.returnAt() != 0) {
                machine.setReturnAt(0);
                Lane lane = lanes[machine.side()];
                if (lane != null) {
                    lane.restingDue = 0;
                }
            }
            return;
        }
        Lane lane = lanes[machine.side()];
        if (lane == null) {
            lane = new Lane(machine.side(), itemRate, fluidRate, chemicalRate);
            lanes[machine.side()] = lane;
        }
        machine.setReturnAwake(true);
        lane.queue.addLast(machine);
        awake++;
    }

    /**
     * O block entity em {@code neighbor}, encostado na Doca em {@code dockPos}, mudou
     * ({@link PatternDockBlock#onNeighborChange}): se o lado dele dorme com "provedor cheio", acorda para a próxima
     * volta (o motor lê o {@link #wakeAt} a cada tick). Custo: uma conta de direção e duas leituras; nada com o lado
     * acordado ou sem fila.
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
        chemicalRate = tier.rate(ResourceType.CHEMICAL);
        for (Lane lane : lanes) {
            if (lane != null) {
                lane.items.setRatePerSecond(itemRate);
                lane.fluid.setRatePerSecond(fluidRate);
                lane.chemical.setRatePerSecond(chemicalRate);
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
            for (int m = 0, n = lane.resting.size(); m < n; m++) {
                DockMachine machine = lane.resting.get(m);
                machine.setReturnAwake(false);
                machine.setReturnAt(0);
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
            if (lane == null) {
                continue;
            }
            long laneAt;
            if (!lane.queue.isEmpty()) {
                laneAt = lane.sleepUntil;
            } else if (!lane.resting.isEmpty()) {
                // O lado que dorme (provedor cheio) segura também o recuo.
                laneAt = Math.max(lane.sleepUntil, lane.restingDue);
            } else {
                continue;
            }
            at = Math.min(at, laneAt);
        }
        return at;
    }

    @Override
    public int visit(long now) {
        DockProbe.Listener probe = DockProbe.listener;
        if (probe == null) {
            return visitLanes(now);
        }
        long started = System.nanoTime();
        int result = visitLanes(now);
        probe.returned(System.nanoTime() - started);
        return result;
    }

    /** A visita: as filas de cada lado, até {@link #MAX_MACHINES_PER_VISIT} máquinas. */
    private int visitLanes(long now) {
        visits++;
        if (dock.isRemoved() || !(dock.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        left = MAX_MACHINES_PER_VISIT;
        int result = 0;
        for (Lane lane : lanes) {
            if (lane == null || now < lane.sleepUntil) {
                continue;
            }
            if (now >= lane.restingDue) {
                promote(lane, now);
            }
            if (lane.queue.isEmpty()) {
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
        boolean wantsChemicals = profile != null && Chemicals.LOADED && extracts(profile, ResourceType.CHEMICAL);
        if (!wantsItems && !wantsFluids && !wantsChemicals) {
            // Sem lado ou sem face Extrai no perfil: nada a devolver.
            drop(lane);
            return false;
        }
        IItemHandler itemsOut = wantsItems ? destinationItems(level, lane) : null;
        IFluidHandler fluidOut = wantsFluids ? destinationFluids(level, lane) : null;
        Object chemicalOut = wantsChemicals ? destinationChemicals(level, lane) : null;
        boolean scalar = wantsFluids || wantsChemicals;
        if (itemsOut == null && fluidOut == null && chemicalOut == null) {
            // Sem provedor (ou com o chunk dele descarregado): dorme até o vizinho mudar ou o teto.
            lane.sleepUntil = now + sleepTicks(scalar);
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
            if (chemicalOut != null && (outcome & FULL) == 0) {
                outcome |= giveChemicals(side, caches, profile, machine.front(), lane, chemicalOut, now);
            }
            if ((outcome & GAVE) != 0) {
                moved = true;
                machine.setLastActive(now);
                // A saída andou, então a entrada também: a máquina que recusou um pedido volta à escolha da entrega
                // (a do Mekanism nunca avisa pelo setChanged, e sem isto dormiria o teto inteiro com lugar livre).
                machine.wake();
            }
            if ((outcome & HAS_MORE) != 0) {
                machine.setReturnInterval(0);
                lane.queue.addLast(machine);
            } else {
                rest(lane, machine, caches, profile, (outcome & GAVE) != 0, scalar, now);
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
            lane.sleepUntil = now + sleepTicks(scalar);
        }
        if (moved) {
            dock.signalWork(lane.side, now);
        }
        return moved;
    }

    /**
     * O teto do sono do lado: o dos fluidos (mais curto, o mesmo dos químicos) se o perfil devolve fluido ou químico,
     * senão o dos itens.
     */
    private static int sleepTicks(boolean scalar) {
        return NetworkManager.maxSleepTicks(scalar ? ResourceType.FLUID : ResourceType.ITEM);
    }

    /**
     * Passa as máquinas do recuo de {@code lane} cujo prazo venceu para o fim da fila e refaz o
     * {@link Lane#restingDue}. O(recuo do lado), só quando o primeiro prazo vence; sem alocar (troca com o último).
     */
    private static void promote(Lane lane, long now) {
        ArrayList<DockMachine> resting = lane.resting;
        long due = Long.MAX_VALUE;
        for (int i = resting.size() - 1; i >= 0; i--) {
            DockMachine machine = resting.get(i);
            if (machine.returnAt() <= now) {
                int last = resting.size() - 1;
                resting.set(i, resting.get(last));
                resting.remove(last);
                machine.setReturnAt(0);
                lane.queue.addLast(machine);
            } else if (machine.returnAt() < due) {
                due = machine.returnAt();
            }
        }
        lane.restingDue = due;
    }

    /**
     * A visita não achou nada para dar em {@code machine}: vai ao recuo pelo intervalo seguinte (1 tick depois de dar
     * algo, senão o dobro do último, até o teto do lado), ou sai da fila se o último intervalo já foi o teto, a última
     * atividade passou da folga ({@link #IDLE_GRACE_TICKS}) e as faces Insere estão vazias ({@link #inputsEmpty}, lido
     * só nesse caso).
     */
    private void rest(Lane lane, DockMachine machine, MachineCaches caches, MachineConfig profile, boolean gave,
            boolean scalar, long now) {
        int cap = sleepTicks(scalar);
        int interval = gave ? 0 : machine.returnInterval();
        if (interval >= cap && now - machine.lastActive() >= IDLE_GRACE_TICKS
                && inputsEmpty(caches, profile, machine.front())) {
            machine.setReturnInterval(0);
            forget(machine);
            return;
        }
        int next = interval == 0 ? 1 : Math.min(interval * 2, cap);
        machine.setReturnInterval(next);
        machine.setReturnAt(now + next);
        lane.resting.add(machine);
        lane.restingDue = Math.min(lane.restingDue, now + next);
    }

    /**
     * As faces Insere do perfil não guardam nada: todos os slots e tanques (itens, fluidos e, com o Mekanism,
     * químicos) vazios, pela frente da máquina. Uma face sem handler conta como vazia.
     */
    private static boolean inputsEmpty(MachineCaches caches, MachineConfig profile, int front) {
        for (RemoteSide face : FACES) {
            boolean items = profile.face(ResourceType.ITEM, face).mode().inserts();
            boolean fluids = profile.face(ResourceType.FLUID, face).mode().inserts();
            boolean chemicals = Chemicals.LOADED && profile.face(ResourceType.CHEMICAL, face).mode().inserts();
            if (!items && !fluids && !chemicals) {
                continue;
            }
            Direction direction = DIRECTIONS[face.toAbsolute(front)];
            IItemHandler itemHandler = items ? caches.items(direction) : null;
            if (itemHandler != null) {
                for (int slot = 0, slots = itemHandler.getSlots(); slot < slots; slot++) {
                    if (!itemHandler.getStackInSlot(slot).isEmpty()) {
                        return false;
                    }
                }
            }
            IFluidHandler fluidHandler = fluids ? caches.fluids(direction) : null;
            if (fluidHandler != null) {
                for (int tank = 0, tanks = fluidHandler.getTanks(); tank < tanks; tank++) {
                    if (!fluidHandler.getFluidInTank(tank).isEmpty()) {
                        return false;
                    }
                }
            }
            Object chemicalHandler = chemicals ? caches.chemicals(direction) : null;
            if (chemicalHandler != null && Chemicals.holdsAny(chemicalHandler)) {
                return false;
            }
        }
        return true;
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
        DockReturnNetwork network = out instanceof DockReturnNetwork net ? net : null;
        boolean first = network != null && network.insertsFirst();
        for (int face = 0; face < FACES.length; face++) {
            if (!profile.face(ResourceType.ITEM, FACES[face]).mode().extracts()) {
                continue;
            }
            IItemHandler handler = caches.items(DIRECTIONS[FACES[face].toAbsolute(front)]);
            if (handler == null) {
                continue;
            }
            for (int slot = 0, slots = handler.getSlots(); slot < slots; slot++) {
                // Um slot maior que uma extração (a saída de 4.096 da Infinite Factory, que dá uma pilha por chamada;
                // uma gaveta) sai em várias, até MAX_EXTRACTIONS_PER_SLOT pilhas: o resto fica para a próxima visita.
                int slotLeft = -1;
                while (true) {
                    if (limit <= 0) {
                        return outcome | HAS_MORE | NO_RATE;
                    }
                    if (slotLeft == 0) {
                        if (!handler.extractItem(slot, 1, true).isEmpty()) {
                            outcome |= HAS_MORE;
                        }
                        break;
                    }
                    ItemStack offer = handler.extractItem(slot, (int) Math.min(limit, Integer.MAX_VALUE), true);
                    if (offer.isEmpty()) {
                        break;
                    }
                    if (slotLeft < 0) {
                        slotLeft = offer.getCount() * MAX_EXTRACTIONS_PER_SLOT;
                    }
                    int want = Math.min(offer.getCount(), slotLeft);
                    if (network != null) {
                        // A rede aceita uma pilha de qualquer tamanho: o que o slot guarda além de uma extração vai
                        // numa inserção só (o custo da rede é por chamada, não por item).
                        ItemStack inSlot = handler.getStackInSlot(slot);
                        if (inSlot.getCount() > want && ItemStack.isSameItemSameComponents(inSlot, offer)) {
                            want = (int) Math.min(Math.min(inSlot.getCount(), limit), slotLeft);
                            offer = offer.copyWithCount(want);
                        }
                    }
                    int fits;
                    int gave;
                    if (first) {
                        fits = want - network.insertItem(0, offer, false).getCount();
                        if (fits <= 0) {
                            return outcome | HAS_MORE | FULL;
                        }
                        gave = takeInserted(level, network, handler, slot, offer, fits);
                    } else {
                        fits = want - (network != null ? network.insertItem(0, offer, true)
                                : ItemHandlerHelper.insertItemStacked(out, offer, true)).getCount();
                        if (fits <= 0) {
                            return outcome | HAS_MORE | FULL;
                        }
                        ItemStack taken = take(level, handler, slot, offer, fits);
                        if (taken.isEmpty()) {
                            break;
                        }
                        ItemStack rest = network != null ? network.insertItem(0, taken, false)
                                : ItemHandlerHelper.insertItemStacked(out, taken, false);
                        if (!rest.isEmpty()) {
                            // O provedor aceitou na simulação e recusou de verdade: volta para a máquina, ou para o chão.
                            keep(level, handler, rest);
                        }
                        gave = taken.getCount() - rest.getCount();
                    }
                    if (gave > 0) {
                        lane.items.consume(gave);
                        limit -= gave;
                        slotLeft = Math.max(0, slotLeft - gave);
                        side.addReturned(gave);
                        outcome |= GAVE;
                    }
                    if (fits < want) {
                        return outcome | HAS_MORE | FULL;
                    }
                    if (gave < fits) {
                        // A máquina deu menos do que ofereceu: o slot não rende mais nesta visita.
                        break;
                    }
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
     * O {@link #giveFluids} dos químicos, tanque a tanque pela ponte {@link Chemicals}: simula a extração, simula a
     * inserção no destino ({@code out}: o tanque de químico do vizinho ou uma rede que aceita químicos), tira da
     * máquina só o que cabe e insere o que saiu. Na rede que insere antes, o que cabe já entra na simulação e o que a
     * máquina der a menos sai da rede de novo. Só com o Mekanism.
     */
    private int giveChemicals(DockSide side, MachineCaches caches, MachineConfig profile, int front, Lane lane,
            Object out, long now) {
        int outcome = 0;
        long limit = lane.chemical.available(now);
        DockReturnNetwork network = out instanceof DockReturnNetwork net ? net : null;
        boolean first = network != null && network.insertsFirst();
        for (int face = 0; face < FACES.length; face++) {
            if (!profile.face(ResourceType.CHEMICAL, FACES[face]).mode().extracts()) {
                continue;
            }
            Object handler = caches.chemicals(DIRECTIONS[FACES[face].toAbsolute(front)]);
            if (handler == null) {
                continue;
            }
            for (int tank = 0, tanks = Chemicals.tanks(handler); tank < tanks; tank++) {
                if (limit <= 0) {
                    return outcome | HAS_MORE | NO_RATE;
                }
                Object offer = Chemicals.extract(handler, tank, limit, true);
                if (Chemicals.isEmptyStack(offer)) {
                    continue;
                }
                long amount = Chemicals.amount(offer);
                // Na rede que insere antes, o que cabe já entrou: falta tirar da máquina.
                long fits = insertChemical(network, out, offer, !first);
                if (fits <= 0) {
                    return outcome | HAS_MORE | FULL;
                }
                Object taken = Chemicals.extract(handler, tank, fits, false);
                long took = Chemicals.isEmptyStack(taken) ? 0 : Chemicals.amount(taken);
                long gave;
                if (first) {
                    gave = took;
                    if (gave < fits) {
                        reclaimChemical(network, Chemicals.copyWithAmount(offer, fits - gave));
                    }
                } else {
                    gave = took == 0 ? 0 : insertChemical(network, out, taken, false);
                    if (gave < took) {
                        // Recusou de verdade o que aceitou na simulação: o resto volta para a máquina.
                        keepChemical(handler, Chemicals.copyWithAmount(taken, took - gave));
                    }
                }
                if (gave > 0) {
                    lane.chemical.consume(gave);
                    limit -= gave;
                    side.addReturnedChemical(gave);
                    outcome |= GAVE;
                }
                if (fits < amount) {
                    return outcome | HAS_MORE | FULL;
                }
                if (limit <= 0) {
                    // O tanque pode ter mais que o balde: fica para a próxima visita.
                    return outcome | HAS_MORE | NO_RATE;
                }
            }
        }
        return outcome;
    }

    /** Insere o químico no destino da volta (a rede, se é uma, ou o tanque do vizinho); devolve quanto entrou. */
    private static long insertChemical(@Nullable DockReturnNetwork network, Object out, Object stack,
            boolean simulate) {
        return network != null ? network.insertChemical(stack, simulate) : Chemicals.insert(out, stack, simulate);
    }

    private static void reclaimChemical(DockReturnNetwork network, Object extra) {
        long back = network.reclaimChemical(extra);
        long amount = Chemicals.amount(extra);
        if (back < amount) {
            WirelessAutomate.LOGGER.warn("Doca: a máquina deu menos que ofereceu, e {} mB de {} ficaram a mais na rede",
                    amount - back, extra);
        }
    }

    /** O químico que o destino recusou depois de aceitar na simulação: de volta na máquina; o que não voltar se perde. */
    private static void keepChemical(Object machine, Object rest) {
        long back = Chemicals.insert(machine, rest, false);
        long amount = Chemicals.amount(rest);
        if (back < amount) {
            WirelessAutomate.LOGGER.warn("Doca: o destino recusou e a máquina não aceitou de volta {} mB de {}",
                    amount - back, rest);
        }
    }

    /**
     * A volta para uma rede que insere antes ({@link DockReturnNetwork#insertsFirst}): {@code fits} de {@code offer} já
     * entraram na rede; tira esse tanto da máquina e devolve quanto saiu. A máquina dá o que a simulação ofereceu (mesmo
     * slot, mesmo tick, nada no meio); se der menos, o que entrou a mais sai da rede de novo ({@link #reclaimItems}).
     */
    private int takeInserted(ServerLevel level, DockReturnNetwork network, IItemHandler handler, int slot,
            ItemStack offer, int fits) {
        int gave = take(level, handler, slot, offer, fits).getCount();
        if (gave < fits) {
            reclaimItems(network, offer.copyWithCount(fits - gave));
        }
        return gave;
    }

    /**
     * Tira {@code amount} de {@code offer} do slot, em quantas extrações a máquina pedir (a do Mekanism dá uma pilha
     * por chamada); devolve o que saiu, numa pilha só (maior que uma pilha, se a rede é o destino). Para no que vier
     * vazio; o que sair de outro item volta para a máquina, ou para o chão.
     */
    private ItemStack take(ServerLevel level, IItemHandler handler, int slot, ItemStack offer, int amount) {
        ItemStack taken = handler.extractItem(slot, amount, false);
        if (taken.isEmpty() || taken.getCount() >= amount) {
            return sameOrKeep(level, handler, offer, taken);
        }
        if (!ItemStack.isSameItemSameComponents(taken, offer)) {
            keep(level, handler, taken);
            return ItemStack.EMPTY;
        }
        int total = taken.getCount();
        while (total < amount) {
            ItemStack more = handler.extractItem(slot, amount - total, false);
            if (more.isEmpty()) {
                break;
            }
            if (!ItemStack.isSameItemSameComponents(more, offer)) {
                keep(level, handler, more);
                break;
            }
            total += more.getCount();
        }
        return offer.copyWithCount(total);
    }

    /** {@code taken}, se é o item de {@code offer}; senão volta para a máquina, ou para o chão, e não saiu nada. */
    private ItemStack sameOrKeep(ServerLevel level, IItemHandler handler, ItemStack offer, ItemStack taken) {
        if (taken.isEmpty() || ItemStack.isSameItemSameComponents(taken, offer)) {
            return taken;
        }
        keep(level, handler, taken);
        return ItemStack.EMPTY;
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
        // Em pilhas do tamanho do item: o que voltou da rede pode ser maior que uma pilha.
        int max = rest.getMaxStackSize();
        for (int count = rest.getCount(); count > 0; count -= max) {
            ItemStack left = ItemHandlerHelper.insertItemStacked(machine, rest.copyWithCount(Math.min(count, max)),
                    false);
            if (!left.isEmpty()) {
                BlockPos pos = dock.getBlockPos();
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), left);
            }
        }
    }

    private void forget(DockMachine machine) {
        machine.setReturnAwake(false);
        machine.setReturnAt(0);
        awake--;
    }

    private void drop(Lane lane) {
        while (!lane.queue.isEmpty()) {
            forget(lane.queue.pollFirst());
        }
        for (int i = 0, n = lane.resting.size(); i < n; i++) {
            forget(lane.resting.get(i));
        }
        lane.resting.clear();
        lane.restingDue = Long.MAX_VALUE;
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

    /**
     * O {@link #destinationItems} dos químicos (o {@code IChemicalHandler} do vizinho como {@code Object}): numa rede,
     * ela mesma só se aceita químicos ({@link DockReturnNetwork#acceptsChemicals}), senão {@code null}. Só com o
     * Mekanism.
     */
    @SuppressWarnings("unchecked")
    private @Nullable Object destinationChemicals(ServerLevel level, Lane lane) {
        BlockPos pos = lane.neighbor(dock.getBlockPos());
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (lane.chemicalCache == null) {
            int created = lane.generation;
            lane.chemicalCache = BlockCapabilityCache.create(
                    (BlockCapability<Object, @Nullable Direction>) Chemicals.capability(), level, pos,
                    DIRECTIONS[lane.side].getOpposite(), () -> !dock.isRemoved() && lane.generation == created,
                    lane::neighborChanged);
        }
        DockReturnNetwork network = lane.network(level, pos);
        if (network != null) {
            return network.acceptsChemicals() ? network : null;
        }
        return lane.chemicalCache.getCapability();
    }

    /** Um lado na volta: a fila das acordadas, o recuo, o sono, os baldes da vazão e os caches do vizinho. */
    private static final class Lane {
        final int side;
        final ArrayDeque<DockMachine> queue = new ArrayDeque<>();
        /** As máquinas em recuo (a última visita não achou nada), cada uma com o prazo em {@code returnAt}. */
        final ArrayList<DockMachine> resting = new ArrayList<>();
        /** O menor prazo do {@link #resting} ({@link Long#MAX_VALUE} sem nenhuma; 0 com uma acordada pelo aviso). */
        long restingDue = Long.MAX_VALUE;
        final RateLimiter items;
        final RateLimiter fluid;
        final RateLimiter chemical;
        /** Tick do servidor até o qual o lado dorme (provedor cheio ou ausente); 0 acordado. */
        long sleepUntil;
        /** Muda no {@link #discard}: os caches criados antes deixam de valer e de avisar. */
        int generation;
        @Nullable BlockCapabilityCache<IItemHandler, @Nullable Direction> itemCache;
        @Nullable BlockCapabilityCache<IFluidHandler, @Nullable Direction> fluidCache;
        @Nullable BlockCapabilityCache<Object, @Nullable Direction> chemicalCache;
        private @Nullable BlockPos neighbor;
        /** A rede do vizinho (um nó do AE2 ou um Autocrafter do RS2); {@link #networkKnown}: já olhou este vizinho. */
        private @Nullable DockReturnNetwork network;
        private boolean networkKnown;

        Lane(int side, long itemRate, long fluidRate, long chemicalRate) {
            this.side = side;
            this.items = new RateLimiter(itemRate);
            this.fluid = new RateLimiter(fluidRate);
            this.chemical = new RateLimiter(chemicalRate);
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
