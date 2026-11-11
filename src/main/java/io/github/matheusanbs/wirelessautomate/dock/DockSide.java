package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Um lado da Doca de Padrões: o bloco das máquinas (todas do mesmo bloco), o perfil das faces (o {@link MachineConfig}
 * de sempre, que vale para todas pela frente de cada uma), as máquinas, o cursor da próxima candidata e o pendente.
 *
 * <p>O pendente é por máquina ({@link DockMachine#pending}): o resto de um pedido que não coube fica na máquina que o
 * recebeu, e só ela deixa de receber pedidos até ele sair; as outras do lado continuam. O lado conta as máquinas com
 * pendente nas transições, para o "tem máquina livre?" ({@link #freeMachines}) ser uma conta, sem laço. O pendente de
 * uma máquina que saiu do lado fica solto ({@link #loose}) até outra máquina ativa que o sirva.
 *
 * <p>Nasce no primeiro vínculo do lado. Mude as máquinas pelo {@link PatternDockBlockEntity}, que mantém o índice das
 * remotas. NBT: {@code side} (byte), {@code block} (id, só com bloco), {@code profile}, {@code machines} (lista de
 * {@link DockMachine}, cada uma com o {@code pending} dela, um {@link DockPendingLot}), {@code cursor} e
 * {@code loose_pending} (o lote solto, com os químicos crus junto; só com algum). O formato antigo ({@code pending},
 * uma lista de pilhas, e {@code pending_at}) é lido e convertido ({@link #loadLegacyPending}).
 *
 * <p>Os químicos pendentes são {@code ChemicalStack} do Mekanism guardados como {@code Object} e passam pela ponte
 * {@link Chemicals}. Uma entrada de químico que não dá para ler (sem o Mekanism, ou o químico que não existe mais) fica
 * crua: volta igual ao NBT e ao item da Doca, nunca é descartada, e não segura nada, porque não há como entregá-la; com
 * o Mekanism de volta, a próxima carga a lê e ela vai ao pendente solto.
 */
public final class DockSide {
    /** As faces de uma máquina no perfil ({@code RemoteSide}). */
    static final int FACES = 6;
    /** Por quanto tempo uma oferta de um provedor conta como "tem provedor" no lado (60 s). */
    static final long OFFER_TICKS = 60 * 20;

    private final int side;
    private @Nullable ResourceLocation block;
    /** O {@link #block} no registro, lido ao atribuir; nulo sem bloco ou com um id que não existe mais. Não é salvo. */
    private @Nullable Block blockObject;
    private final MachineConfig profile;
    private final List<DockMachine> machines = new ArrayList<>();
    private final List<DockMachine> machinesView = Collections.unmodifiableList(machines);
    private int cursor;
    /**
     * O pendente sem máquina: o que veio no item da Doca, o que os químicos crus viraram ao serem lidos e o de uma
     * máquina que saiu do lado ou não o serve mais. Vai à próxima máquina ativa que o sirva
     * ({@link DockPending#adopt}).
     */
    private @Nullable DockPendingLot loose;
    /** As entradas de químico que não deu para ler (sem o Mekanism), guardadas como vieram. */
    private final List<CompoundTag> rawChemicals = new ArrayList<>(0);
    /** Quantas máquinas do lado guardam pendente (ativas ou não). */
    private int pendingMachines;
    /** Quantas máquinas ativas do lado guardam pendente ({@link #freeMachines} é a diferença das ativas). */
    private int busyActive;

    // O que não é salvo: o motivo da última recusa, a máquina escolhida por uma simulação, as ativas, o estado da
    // volta, a última oferta de um provedor e o rascunho da entrega.
    /** O motivo da última recusa ({@link DockStatus#NO_REFUSAL} depois de uma aceita). */
    private int lastRefusal = DockStatus.NO_REFUSAL;
    /** A máquina que a última simulação aceita escolheu, o índice dela e o tick do servidor em que vale. */
    private @Nullable DockMachine planned;
    private int plannedIndex;
    private long plannedTick = Long.MIN_VALUE;
    /** Quantas máquinas do lado estão ativas (no alcance e no limite); refeito com o registro no índice. */
    private int activeMachines;
    /** O provedor deste lado recusou, na última visita da volta, o que uma máquina tinha para dar. */
    private boolean providerFull;
    /** Itens e mB que a volta deu ao provedor deste lado desde a carga da Doca. */
    private long returned;
    private long returnedFluid;
    private long returnedChemical;
    /**
     * O tick do servidor da última oferta de um provedor do AE2 ou do RS2 a este lado ({@link DockAe2#hasProvider},
     * {@link DockRs2#hasProvider}).
     */
    private long providerOfferTick = Long.MIN_VALUE;
    /** Uma tentativa do pendente solto na fila do servidor, para o aviso seguinte não enfileirar outra. */
    private boolean flushQueued;
    /**
     * Rascunho da entrega: as faces Insere que aceitam cada ingrediente (um bit por face) e quantos ingredientes cada
     * face aceita. Criado na primeira entrega e reaproveitado; só cresce com um pedido maior que os anteriores.
     */
    private int @Nullable [] masks;
    private final int[] loads = new int[FACES];
    private DockDelivery.@Nullable Attempt attempt;
    private @Nullable DockStacker stacker;

    public DockSide(int side) {
        this(side, new MachineConfig());
    }

    private DockSide(int side, MachineConfig profile) {
        this.side = side;
        this.profile = profile;
    }

    /** O lado ({@code Direction.ordinal()}). */
    public int side() {
        return side;
    }

    /** O bloco das máquinas deste lado, ou {@code null} antes do primeiro vínculo. */
    public @Nullable ResourceLocation block() {
        return block;
    }

    void assignBlock(@Nullable ResourceLocation block) {
        this.block = block;
        blockObject = block == null ? null : BuiltInRegistries.BLOCK.getOptional(block).orElse(null);
    }

    /**
     * {@code candidate} é o bloco deste lado. Uma comparação de referência, para o caminho quente dos avisos das
     * máquinas; um lado sem bloco, ou com um id que não existe mais no registro, não casa com nenhum.
     */
    boolean isSideBlock(Block candidate) {
        return blockObject != null && candidate == blockObject;
    }

    /** O perfil das faces; mudar o devolvido muda o lado (quem muda salva a Doca). */
    public MachineConfig profile() {
        return profile;
    }

    /** As máquinas na ordem de vínculo, só para leitura. */
    public List<DockMachine> machines() {
        return machinesView;
    }

    void addMachine(DockMachine machine) {
        machines.add(machine);
    }

    @Nullable DockMachine removeMachine(BlockPos pos) {
        long key = pos.asLong();
        for (int i = 0; i < machines.size(); i++) {
            if (machines.get(i).pos().asLong() == key) {
                // o pendente dela fica no lado, solto, até outra máquina ativa que o sirva
                loosen(machines.get(i));
                DockMachine removed = machines.remove(i);
                if (cursor > i) {
                    cursor--;
                }
                if (cursor >= machines.size()) {
                    cursor = 0;
                }
                return removed;
            }
        }
        return null;
    }

    /** Índice da próxima candidata na lista de máquinas. */
    public int cursor() {
        return cursor;
    }

    public void setCursor(int cursor) {
        this.cursor = cursor;
    }

    /**
     * O lado guarda o resto de algum pedido para entregar: uma máquina com pendente ou o pendente solto. Uma leitura de
     * campo. Não recusa pedidos por isso: quem recusa é o lado sem máquina livre ({@link #freeMachines}).
     */
    public boolean hasPending() {
        return pendingMachines > 0 || loose != null;
    }

    /**
     * O lado guarda algo do pendente: o que {@link #hasPending} conta ou uma entrada de químico crua. É o que vai ao
     * NBT e ao item da Doca, e o que faz o lado guardar o bloco sem máquinas.
     */
    public boolean keepsPending() {
        return hasPending() || !rawChemicals.isEmpty();
    }

    /**
     * Quantas máquinas ativas não têm pendente e podem receber um pedido. Uma conta de dois campos, mantidos nas
     * transições (o pendente que entra ou sai, o registro no índice): o {@code acceptsPlans} do AE2 pergunta a cada
     * tick, sem laço.
     */
    public int freeMachines() {
        return activeMachines - busyActive;
    }

    /**
     * Todas as máquinas ativas do lado guardam o resto de um pedido (e o lado espera uma delas terminar): o estado
     * "Pedido pendente" da tela.
     */
    public boolean waitingOnPending() {
        return busyActive > 0 && freeMachines() <= 0;
    }

    /** O pendente solto: o que veio no item da Doca ou o de uma máquina que saiu do lado; {@code null} sem nenhum. */
    public @Nullable DockPendingLot loose() {
        return loose;
    }

    /**
     * O pendente de {@code machine} (deste lado), criado se ela ainda não tem: para a sobra de um pedido que ela
     * recebeu. Crie só com algo a guardar (um lote vazio conta a máquina como ocupada até a próxima tentativa).
     */
    DockPendingLot lotFor(DockMachine machine) {
        DockPendingLot lot = machine.pending();
        if (lot == null) {
            lot = new DockPendingLot();
            attach(machine, lot);
        }
        return lot;
    }

    /** {@code lot} passa a ser o pendente de {@code machine} (junto ao que ela já tinha). */
    void adopt(DockMachine machine, DockPendingLot lot) {
        DockPendingLot current = machine.pending();
        if (current == null) {
            attach(machine, lot);
        } else {
            current.merge(lot);
        }
    }

    private void attach(DockMachine machine, DockPendingLot lot) {
        machine.setPending(lot);
        pendingMachines++;
        if (machine.active()) {
            busyActive++;
        }
    }

    /** {@code machine} fica sem pendente (o dela saiu inteiro, ou foi para outro lugar). */
    void release(DockMachine machine) {
        if (machine.pending() == null) {
            return;
        }
        machine.setPending(null);
        pendingMachines--;
        if (machine.active()) {
            busyActive--;
        }
    }

    /** O pendente de {@code machine} vira solto (ela saiu do lado, foi quebrada ou trocada, ou não serve mais). */
    void loosen(DockMachine machine) {
        DockPendingLot lot = machine.pending();
        if (lot == null) {
            return;
        }
        release(machine);
        addLoose(lot);
    }

    private void addLoose(DockPendingLot lot) {
        if (lot.isEmpty()) {
            return;
        }
        if (loose == null) {
            loose = lot;
        } else {
            loose.merge(lot);
        }
    }

    /** O pendente solto foi para uma máquina, ou saiu inteiro. */
    void clearLoose() {
        loose = null;
    }

    /**
     * Guarda o pendente de itens e fluidos na máquina em {@code at} deste lado (ou solto, sem ela), no lugar de todo o
     * pendente do lado (os químicos também saem); listas vazias limpam. Para os testes e o e2e montarem o estado.
     */
    public void setPending(List<ItemStack> items, List<FluidStack> fluids, @Nullable BlockPos at) {
        clearPending();
        DockPendingLot lot = new DockPendingLot();
        for (ItemStack stack : items) {
            lot.addItem(stack, stack.getCount());
        }
        for (FluidStack stack : fluids) {
            lot.addFluid(stack, stack.getAmount());
        }
        if (lot.isEmpty()) {
            return;
        }
        DockMachine machine = at == null ? null : machineAt(at.asLong());
        if (machine != null) {
            attach(machine, lot);
        } else {
            loose = lot;
        }
    }

    /** Esvazia o pendente do lado inteiro: o das máquinas, o solto e os químicos crus. */
    public void clearPending() {
        for (DockMachine machine : machines) {
            release(machine);
        }
        loose = null;
        rawChemicals.clear();
    }

    private @Nullable DockMachine machineAt(long key) {
        for (DockMachine machine : machines) {
            if (machine.pos().asLong() == key) {
                return machine;
            }
        }
        return null;
    }

    /**
     * O pendente do lado inteiro num lote novo: o de cada máquina e o solto (sem os crus). Para o item da Doca, a tela
     * dos testes e o drop; O(máquinas).
     */
    public DockPendingLot pendingTotal() {
        DockPendingLot total = new DockPendingLot();
        for (DockMachine machine : machines) {
            if (machine.pending() != null) {
                total.merge(machine.pending());
            }
        }
        if (loose != null) {
            total.merge(loose);
        }
        return total;
    }

    /** Põe {@code lot} no pendente solto do lado (o lote passa a ser do lado); os químicos crus vão à parte. */
    void addLooseLot(DockPendingLot lot, List<CompoundTag> raw) {
        addLoose(lot);
        for (CompoundTag tag : raw) {
            rawChemicals.add(tag.copy());
        }
    }

    /**
     * Acrescenta ao pendente solto os químicos salvos em {@code tags} sem lê-los, como entradas cruas: a Doca
     * recolocada fora do mundo, sem os registros. O {@link #readRawChemicals} os lê quando ela entra no mundo.
     */
    public void keepRawChemicals(List<CompoundTag> tags) {
        for (CompoundTag tag : tags) {
            if (!tag.isEmpty()) {
                rawChemicals.add(tag.copy());
            }
        }
    }

    /** Tenta ler as entradas cruas de químico, que vão ao pendente solto; a que continua ilegível fica crua. */
    public void readRawChemicals(HolderLookup.Provider registries) {
        if (rawChemicals.isEmpty()) {
            return;
        }
        List<CompoundTag> raw = new ArrayList<>(rawChemicals);
        rawChemicals.clear();
        DockPendingLot lot = new DockPendingLot();
        for (CompoundTag tag : raw) {
            lot.addChemicalTag(tag, registries, rawChemicals::add);
        }
        addLoose(lot);
    }

    /** As entradas de químico cruas (sem o Mekanism), guardadas como vieram. */
    public List<CompoundTag> rawChemicals() {
        return Collections.unmodifiableList(rawChemicals);
    }

    /** O motivo da última recusa ({@code DockStatus}), ou {@link DockStatus#NO_REFUSAL}. */
    public int lastRefusal() {
        return lastRefusal;
    }

    void setLastRefusal(int reason) {
        lastRefusal = reason;
    }

    /** Um provedor ofereceu um pedido a este lado nos últimos {@link #OFFER_TICKS}, contados até {@code now}. */
    boolean offeredWithin(long now) {
        return providerOfferTick != Long.MIN_VALUE && now - providerOfferTick < OFFER_TICKS;
    }

    void setProviderOfferTick(long tick) {
        providerOfferTick = tick;
    }

    /** Guarda a máquina escolhida por uma simulação aceita no tick {@code tick}. */
    void plan(DockMachine machine, int index, long tick) {
        planned = machine;
        plannedIndex = index;
        plannedTick = tick;
    }

    /** A máquina da simulação aceita neste tick, ou {@code null}; ver {@link #plannedIndex}. */
    @Nullable DockMachine planned(long tick) {
        return plannedTick == tick ? planned : null;
    }

    int plannedIndex() {
        return plannedIndex;
    }

    void clearPlan() {
        planned = null;
        plannedTick = Long.MIN_VALUE;
    }

    /** Quantas máquinas do lado estão ativas. */
    public int activeMachines() {
        return activeMachines;
    }

    /** As ativas e, entre elas, as que guardam pendente: refeito com o registro no índice. */
    void setActiveMachines(int count, int busy) {
        activeMachines = count;
        busyActive = busy;
    }

    /** Ver {@link #providerFull}. */
    public boolean providerFull() {
        return providerFull;
    }

    /** Pela volta; público para o e2e da tela montar o estado "Provedor cheio". */
    public void setProviderFull(boolean full) {
        providerFull = full;
    }

    /** Itens devolvidos ao provedor deste lado desde a carga (não é salvo). */
    public long returned() {
        return returned;
    }

    /** mB de fluido devolvidos ao provedor deste lado desde a carga (não é salvo). */
    public long returnedFluid() {
        return returnedFluid;
    }

    /** mB de químico devolvidos ao provedor deste lado desde a carga (não é salvo). */
    public long returnedChemical() {
        return returnedChemical;
    }

    void addReturned(long count) {
        returned += count;
    }

    void addReturnedFluid(long amount) {
        returnedFluid += amount;
    }

    void addReturnedChemical(long amount) {
        returnedChemical += amount;
    }

    boolean flushQueued() {
        return flushQueued;
    }

    void setFlushQueued(boolean queued) {
        flushQueued = queued;
    }

    /** O rascunho das faces por ingrediente, com lugar para {@code count} ingredientes. */
    int[] masks(int count) {
        if (masks == null || masks.length < count) {
            masks = new int[Math.max(count, 9)];
        }
        return masks;
    }

    int[] loads() {
        return loads;
    }

    /** A inserção com a dica de slot da entrega e do pendente, criada na primeira entrega do lado. */
    DockStacker stacker() {
        if (stacker == null) {
            stacker = new DockStacker();
        }
        return stacker;
    }

    /** Os testes da escolha, criados na primeira entrega do lado. */
    DockDelivery.Attempt attempt() {
        if (attempt == null) {
            attempt = new DockDelivery.Attempt();
        }
        return attempt;
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("side", (byte) side);
        if (block != null) {
            tag.putString("block", block.toString());
        }
        tag.put("profile", profile.save(registries));
        ListTag list = new ListTag();
        for (DockMachine machine : machines) {
            CompoundTag saved = machine.save();
            if (machine.pending() != null) {
                saved.put("pending", machine.pending().save(registries, List.of()));
            }
            list.add(saved);
        }
        tag.put("machines", list);
        tag.putInt("cursor", cursor);
        if (loose != null || !rawChemicals.isEmpty()) {
            tag.put("loose_pending", (loose != null ? loose : new DockPendingLot()).save(registries, rawChemicals));
        }
        return tag;
    }

    /** O lado salvo em {@code tag}, ou {@code null} com um lado fora de 0 a 5. */
    static @Nullable DockSide load(CompoundTag tag, HolderLookup.Provider registries) {
        int side = tag.getByte("side");
        if (side < 0 || side > 5) {
            return null;
        }
        DockSide loaded = new DockSide(side, MachineConfig.load(tag.getCompound("profile"), registries));
        loaded.assignBlock(tag.contains("block", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("block"))
                : null);
        ListTag list = tag.getList("machines", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompound(i);
            DockMachine machine = DockMachine.load(side, saved);
            loaded.machines.add(machine);
            if (saved.contains("pending", Tag.TAG_COMPOUND)) {
                DockPendingLot lot = DockPendingLot.load(saved.getCompound("pending"), registries,
                        loaded.rawChemicals::add);
                if (!lot.isEmpty()) {
                    loaded.attach(machine, lot);
                }
            }
        }
        int cursor = tag.getInt("cursor");
        loaded.cursor = cursor >= 0 && cursor < loaded.machines.size() ? cursor : 0;
        if (tag.contains("loose_pending", Tag.TAG_COMPOUND)) {
            loaded.addLoose(DockPendingLot.load(tag.getCompound("loose_pending"), registries,
                    loaded.rawChemicals::add));
        }
        if (tag.contains("pending", Tag.TAG_LIST)) {
            loaded.loadLegacyPending(tag, registries);
        }
        return loaded;
    }

    /**
     * A migração do formato antigo (até o 7470507): {@code pending}, uma lista de {@code {item}} (pilhas de até 99),
     * {@code {fluid}} ou {@code {chemical}}, e {@code pending_at}, a posição da máquina que recebeu o pedido. Vira um
     * lote com uma linha por item, na máquina de {@code pending_at} se ela é deste lado, senão solto (como a posição da
     * Doca que o item recolocado gravava). O químico que não dá para ler fica cru.
     */
    private void loadLegacyPending(CompoundTag tag, HolderLookup.Provider registries) {
        DockPendingLot lot = new DockPendingLot();
        ListTag pending = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            if (entry.contains("item", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("item"));
                lot.addItem(stack, stack.getCount());
            } else if (entry.contains("fluid", Tag.TAG_COMPOUND)) {
                FluidStack stack = FluidStack.parseOptional(registries, entry.getCompound("fluid"));
                lot.addFluid(stack, stack.getAmount());
            } else if (entry.contains("chemical", Tag.TAG_COMPOUND)) {
                lot.addChemicalTag(entry.getCompound("chemical"), registries, rawChemicals::add);
            }
        }
        if (lot.isEmpty()) {
            return;
        }
        DockMachine machine = tag.contains("pending_at", Tag.TAG_LONG) ? machineAt(tag.getLong("pending_at")) : null;
        if (machine != null) {
            adopt(machine, lot);
        } else {
            addLoose(lot);
        }
    }
}
