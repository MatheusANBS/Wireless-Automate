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
 * de sempre, que vale para todas pela frente de cada uma), as máquinas, o cursor da próxima candidata e o pendente (o
 * que sobrou de um pedido que não coube inteiro, com a posição da máquina que o recebeu).
 *
 * <p>Nasce no primeiro vínculo do lado. Mude as máquinas pelo {@link PatternDockBlockEntity}, que mantém o índice das
 * remotas. NBT: {@code side} (byte), {@code block} (id, só com bloco), {@code profile}, {@code machines} (lista de
 * {@link DockMachine}), {@code cursor}, {@code pending} (lista de {@code {item}}, {@code {fluid}} ou
 * {@code {chemical}}) e {@code pending_at} (long, só com pendente).
 *
 * <p>Os químicos pendentes são {@code ChemicalStack} do Mekanism guardados como {@code Object} e passam pela ponte
 * {@link Chemicals}. Uma entrada {@code {chemical}} que não dá para ler (sem o Mekanism, ou o químico que não existe
 * mais) fica crua: volta igual ao NBT e ao item da Doca, nunca é descartada, e não segura o lado, porque não há como
 * entregá-la; com o Mekanism de volta, a próxima carga a lê.
 */
public final class DockSide {
    /** As faces de uma máquina no perfil ({@code RemoteSide}). */
    static final int FACES = 6;
    /** Por quanto tempo uma oferta de um provedor conta como "tem provedor" no lado (60 s). */
    static final long OFFER_TICKS = 60 * 20;
    /** O teto de {@code count} no codec do {@code ItemStack}: um pendente maior é salvo em partes. */
    private static final int SAVED_STACK_MAX = 99;

    private final int side;
    private @Nullable ResourceLocation block;
    /** O {@link #block} no registro, lido ao atribuir; nulo sem bloco ou com um id que não existe mais. Não é salvo. */
    private @Nullable Block blockObject;
    private final MachineConfig profile;
    private final List<DockMachine> machines = new ArrayList<>();
    private final List<DockMachine> machinesView = Collections.unmodifiableList(machines);
    private int cursor;
    private final List<ItemStack> pendingItems = new ArrayList<>();
    private final List<FluidStack> pendingFluids = new ArrayList<>();
    private final List<Object> pendingChemicals = new ArrayList<>(0);
    /** As entradas {@code {chemical}} que não deu para ler, guardadas como vieram. */
    private final List<CompoundTag> rawChemicals = new ArrayList<>(0);
    private long pendingAt;

    // O que não é salvo: o motivo da última recusa, a máquina escolhida por uma simulação, as ativas, o estado da volta,
    // a última oferta de um provedor e o rascunho da entrega.
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
    /** Uma tentativa do pendente na fila do servidor, para o aviso seguinte não enfileirar outra. */
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

    /** Os itens pendentes; vazio sem pendente. */
    public List<ItemStack> pendingItems() {
        return pendingItems;
    }

    /** Os fluidos pendentes; vazio sem pendente. */
    public List<FluidStack> pendingFluids() {
        return pendingFluids;
    }

    /** Os químicos pendentes ({@code ChemicalStack}, pela ponte {@link Chemicals}); vazio sem pendente. */
    public List<Object> pendingChemicals() {
        return pendingChemicals;
    }

    /** O lado tem o resto de um pedido para entregar (e recusa pedidos novos até ele sair). */
    public boolean hasPending() {
        return !pendingItems.isEmpty() || !pendingFluids.isEmpty() || !pendingChemicals.isEmpty();
    }

    /**
     * O lado guarda algo do pendente: o que {@link #hasPending} conta ou uma entrada de químico crua. É o que vai ao NBT
     * e ao item da Doca, e o que faz o lado guardar o bloco sem máquinas.
     */
    public boolean keepsPending() {
        return hasPending() || !rawChemicals.isEmpty();
    }

    /** A posição da máquina que recebeu o pedido pendente; só vale com {@link #hasPending}. */
    public BlockPos pendingAt() {
        return BlockPos.of(pendingAt);
    }

    /**
     * Guarda o pendente de itens e fluidos de um pedido entregue a {@code at}, no lugar de todo o pendente (os químicos
     * também saem); listas vazias limpam. Os stacks passam a ser do lado.
     */
    public void setPending(List<ItemStack> items, List<FluidStack> fluids, BlockPos at) {
        clearPending();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                pendingItems.add(stack);
            }
        }
        for (FluidStack stack : fluids) {
            if (!stack.isEmpty()) {
                pendingFluids.add(stack);
            }
        }
        pendingAt = at.asLong();
    }

    public void clearPending() {
        pendingItems.clear();
        pendingFluids.clear();
        pendingChemicals.clear();
        rawChemicals.clear();
    }

    /**
     * Acrescenta ao pendente os químicos salvos em {@code tags} (as entradas do {@link #pendingChemicalTags}); a que não
     * dá para ler fica crua.
     */
    public void addPendingChemicals(List<CompoundTag> tags, HolderLookup.Provider registries) {
        for (CompoundTag tag : tags) {
            addPendingChemical(tag, registries);
        }
    }

    /**
     * Acrescenta ao pendente os químicos salvos em {@code tags} sem lê-los, como entradas cruas: a Doca recolocada fora
     * do mundo, sem os registros. O {@link #readRawChemicals} os lê quando ela entra no mundo.
     */
    public void keepRawChemicals(List<CompoundTag> tags) {
        for (CompoundTag tag : tags) {
            if (!tag.isEmpty()) {
                rawChemicals.add(tag.copy());
            }
        }
    }

    /** Tenta ler as entradas cruas de químico; a que continua ilegível (sem o Mekanism) fica crua. */
    public void readRawChemicals(HolderLookup.Provider registries) {
        if (rawChemicals.isEmpty()) {
            return;
        }
        List<CompoundTag> raw = new ArrayList<>(rawChemicals);
        rawChemicals.clear();
        for (CompoundTag tag : raw) {
            addPendingChemical(tag, registries);
        }
    }

    private void addPendingChemical(CompoundTag tag, HolderLookup.Provider registries) {
        Object stack = Chemicals.parseStack(tag, registries);
        if (stack != null) {
            pendingChemicals.add(stack);
        } else if (!tag.isEmpty()) {
            rawChemicals.add(tag.copy());
        }
    }

    /** Os químicos pendentes em NBT, os lidos e os crus, para o item da Doca. */
    public List<CompoundTag> pendingChemicalTags(HolderLookup.Provider registries) {
        if (pendingChemicals.isEmpty() && rawChemicals.isEmpty()) {
            return List.of();
        }
        List<CompoundTag> tags = new ArrayList<>(pendingChemicals.size() + rawChemicals.size());
        for (Object stack : pendingChemicals) {
            tags.add(Chemicals.saveStack(stack, registries));
        }
        for (CompoundTag raw : rawChemicals) {
            tags.add(raw.copy());
        }
        return tags;
    }

    /** Põe {@code stack} (que passa a ser do lado) no pendente do pedido entregue a {@code at}. */
    void addPending(ItemStack stack, BlockPos at) {
        pendingItems.add(stack);
        pendingAt = at.asLong();
    }

    void addPending(FluidStack stack, BlockPos at) {
        pendingFluids.add(stack);
        pendingAt = at.asLong();
    }

    /** Põe o químico {@code stack} ({@code ChemicalStack}, que passa a ser do lado) no pendente. */
    void addPendingChemical(Object stack, BlockPos at) {
        pendingChemicals.add(stack);
        pendingAt = at.asLong();
    }

    /** O pendente passa para a máquina em {@code at} (a que o recebeu saiu do lado). */
    void movePending(BlockPos at) {
        pendingAt = at.asLong();
    }

    /** {@link #pendingAt} como {@code long}, sem criar a posição. */
    long pendingAtKey() {
        return pendingAt;
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

    void setActiveMachines(int count) {
        activeMachines = count;
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
            list.add(machine.save());
        }
        tag.put("machines", list);
        tag.putInt("cursor", cursor);
        if (keepsPending()) {
            ListTag pending = new ListTag();
            for (ItemStack stack : pendingItems) {
                savePendingItem(pending, stack, registries);
            }
            for (FluidStack stack : pendingFluids) {
                CompoundTag entry = new CompoundTag();
                entry.put("fluid", stack.save(registries));
                pending.add(entry);
            }
            for (CompoundTag chemical : pendingChemicalTags(registries)) {
                CompoundTag entry = new CompoundTag();
                entry.put("chemical", chemical);
                pending.add(entry);
            }
            tag.put("pending", pending);
            tag.putLong("pending_at", pendingAt);
        }
        return tag;
    }

    /** Um item pendente, em partes de até {@link #SAVED_STACK_MAX} (o codec não aceita mais que isso num stack). */
    private static void savePendingItem(ListTag pending, ItemStack stack, HolderLookup.Provider registries) {
        int left = stack.getCount();
        while (left > 0) {
            int part = Math.min(left, SAVED_STACK_MAX);
            CompoundTag entry = new CompoundTag();
            entry.put("item", stack.copyWithCount(part).save(registries));
            pending.add(entry);
            left -= part;
        }
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
            loaded.machines.add(DockMachine.load(side, list.getCompound(i)));
        }
        int cursor = tag.getInt("cursor");
        loaded.cursor = cursor >= 0 && cursor < loaded.machines.size() ? cursor : 0;
        ListTag pending = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            if (entry.contains("item", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("item"));
                if (!stack.isEmpty()) {
                    loaded.pendingItems.add(stack);
                }
            } else if (entry.contains("fluid", Tag.TAG_COMPOUND)) {
                FluidStack stack = FluidStack.parseOptional(registries, entry.getCompound("fluid"));
                if (!stack.isEmpty()) {
                    loaded.pendingFluids.add(stack);
                }
            } else if (entry.contains("chemical", Tag.TAG_COMPOUND)) {
                loaded.addPendingChemical(entry.getCompound("chemical"), registries);
            }
        }
        loaded.pendingAt = tag.getLong("pending_at");
        return loaded;
    }
}
