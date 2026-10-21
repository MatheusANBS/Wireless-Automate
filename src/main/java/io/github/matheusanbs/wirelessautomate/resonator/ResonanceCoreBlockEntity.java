package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * O estado do Núcleo de Ressonância (sem ticker: o gerenciador trabalha em lote). Guarda se a estrutura está
 * montada, as 98 posições da casca, a sintonia, os cristais, o catalisador da recarga ({@link CoreCatalyst}) e a
 * energia; quem monta e desmonta é a {@link ResonatorFormation} e quem gera, gasta e recarrega é o
 * {@link ResonatorBatch}.
 */
public class ResonanceCoreBlockEntity extends BlockEntity {
    /** O dial começa no meio da faixa até o jogador mexer. */
    static final double DEFAULT_DIAL = 98.0;
    /** As câmaras do último tier; as do tier do Núcleo são as primeiras. */
    public static final int MAX_CHAMBERS = CoreMath.chambers(RouterTier.values().length - 1);
    public static final int MAX_REDSTONE = CoreCatalyst.MAX;

    private boolean formed;
    private List<BlockPos> members = List.of();
    /** O canto mínimo da caixa, refeito só quando os membros mudam ({@link ChamberView#origin}). */
    private int[] origin = ChamberView.origin(List.of(), worldPosition);
    /** As Portas que este Núcleo pôs no índice; não vai para o NBT (a revalidação do {@code onLoad} refaz). */
    private List<BlockPos> ports = List.of();
    /** A vazão por Porta de Energia e o "cheio" do último lote, que vão à câmara; fora do NBT. */
    private final CoreFlow flow = new CoreFlow();
    private final CoreInterference interference = new CoreInterference();
    private final CoreEcoState eco = new CoreEcoState(this);
    private @Nullable ResonatorLayout.Broken lastProblem;
    private boolean hasStation;
    private double station;
    private double velocity;
    private double dial = DEFAULT_DIAL;
    private final NonNullList<ItemStack> crystals = NonNullList.withSize(MAX_CHAMBERS, ItemStack.EMPTY);
    private final CoreCatalyst catalyst = new CoreCatalyst();
    /** Ticks já andados da recarga do próximo Opaco. */
    private int recharge;
    private long energy;
    /** Lotes desde o último passo da deriva. */
    private int batches;
    /** Modo automático: o Sintonizador anda o dial até a estação, com a eficiência no teto do tier. */
    private boolean auto;
    private final CoreShell shell = new CoreShell();
    /** A câmara no cliente e, no servidor, a última view mandada com o início fixado da recarga. */
    private final CoreSync sync = new CoreSync();
    private final CoreInventory inventory = new CoreInventory(this);

    public ResonanceCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONANCE_CORE.get(), pos, state);
    }

    public RouterTier tier() {
        return getBlockState().getValue(RouterBlock.TIER);
    }

    public boolean formed() {
        return formed;
    }

    /** O canto mínimo da caixa (x, y, z), guardado; quem lê não muda o array. */
    int[] origin() {
        return origin;
    }

    /** As posições da casca montada (vazia sem estrutura). */
    public List<BlockPos> members() {
        return members;
    }

    /** O que impediu a última revalidação de montar, ou {@code null} se montou ou ainda não revalidou. */
    public @Nullable ResonatorLayout.Broken lastProblem() {
        return lastProblem;
    }

    public double station() {
        return station;
    }

    public double dial() {
        return dial;
    }

    public void setDial(double mhz) {
        dial = Tuning.clampDial(mhz);
        setChanged();
        syncIfChanged();
    }

    /** A eficiência da sintonia agora, pelo desvio entre a estação e o dial, no teto do tier no modo automático. */
    public double efficiency() {
        return Tuning.capped(rawEfficiency(), auto, tier().ordinal());
    }

    /** A eficiência pelo desvio, sem o teto do Sintonizador. */
    public double rawEfficiency() {
        return Tuning.efficiency(station - dial);
    }

    public boolean auto() {
        return auto;
    }

    /** Liga ou desliga o modo automático e devolve se valeu: sem Sintonizador na estrutura, ligar é recusado. */
    public boolean setAuto(boolean auto) {
        if (auto && !shell.hasTuner()) {
            return false;
        }
        if (this.auto != auto) {
            this.auto = auto;
            setChanged();
            syncIfChanged();
        }
        return true;
    }

    public boolean hasTuner() {
        return shell.hasTuner();
    }

    public int energyPorts() {
        return shell.energyPorts();
    }

    public int crystalPorts() {
        return shell.crystalPorts();
    }

    /** A montagem reconhecida (Vitrine ou Faixa), ou {@code null} se é própria ou nunca montou. */
    public ResonatorBlueprint.@Nullable Layout layout() {
        return shell.layout();
    }

    /** O tempo de jogo da última montagem, -1 se nunca montou, 0 se montou antes de o tempo ser gravado. */
    public long formedAt() {
        return shell.formedAt();
    }

    /**
     * Guarda o que a montagem achou na casca; sem Sintonizador o modo volta a Manual com o dial onde parou. O tempo
     * só muda quando a estrutura estava desmontada: a revalidação de uma estrutura de pé não reacende a câmara. Um
     * Núcleo salvo montado sem o tempo ganha 0, para a carga do chunk não acender a câmara em sequência. Não manda a
     * câmara: quem monta manda uma vez só depois do {@code setFormed}, já com a origem nova.
     */
    void setStructure(boolean hasTuner, int energyPorts, int crystalPorts, ResonatorBlueprint.@Nullable Layout layout,
            long gameTime) {
        boolean changed = shell.set(hasTuner, energyPorts, crystalPorts, layout, formed, gameTime);
        if (!hasTuner && auto) {
            auto = false;
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    /** Só para os GameTests: põe a estação num valor conhecido, parada. */
    public void setStationForTest(double mhz) {
        hasStation = true;
        station = mhz;
        velocity = 0;
        setChanged();
    }

    public int chambers() {
        return CoreMath.chambers(tier().ordinal());
    }

    /** As câmaras do tier e o catalisador como {@link net.minecraft.world.Container}, para o menu. */
    public CoreInventory inventory() {
        return inventory;
    }

    /**
     * As {@link #MAX_CHAMBERS} câmaras, só leitura; só as {@link #chambers()} primeiras valem. As pilhas são as do
     * Núcleo: o lote grava a carga nelas.
     */
    public List<ItemStack> crystals() {
        return Collections.unmodifiableList(crystals);
    }

    /**
     * Põe {@code stack} (vazio ou uma unidade de cristal) na câmara {@code chamber}, de qualquer tier; o resto é
     * ignorado. O slot do menu entra por aqui.
     */
    public void setCrystalAt(int chamber, ItemStack stack) {
        if (chamber < 0 || chamber >= MAX_CHAMBERS
                || !stack.isEmpty() && (stack.getCount() != 1 || !(stack.getItem() instanceof SignalCrystalItem))) {
            return;
        }
        boolean emptied = stack.isEmpty() && !crystals.get(chamber).isEmpty();
        crystals.set(chamber, stack);
        setChanged();
        if (emptied) {
            notifyPorts(false, true);
        }
        syncIfChanged();
    }

    /** Põe um cristal não rachado na primeira câmara vazia e devolve o que sobrou da pilha (sem mexer nela). */
    public ItemStack insertCrystal(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof SignalCrystalItem) || SignalCrystalItem.isCracked(stack)) {
            return stack.copy();
        }
        for (int i = 0; i < chambers(); i++) {
            if (crystals.get(i).isEmpty()) {
                crystals.set(i, stack.copyWithCount(1));
                setChanged();
                syncIfChanged();
                return stack.copyWithCount(stack.getCount() - 1);
            }
        }
        return stack.copy();
    }

    /**
     * Põe uma unidade do cristal na câmara {@code chamber}, se ela é do tier e está vazia e o cristal não está rachado;
     * devolve se pôs. A Porta
     * de Cristais entra por aqui, slot a slot.
     */
    public boolean insertCrystalAt(int chamber, ItemStack stack) {
        if (chamber < 0 || chamber >= chambers() || !crystals.get(chamber).isEmpty()
                || !(stack.getItem() instanceof SignalCrystalItem) || SignalCrystalItem.isCracked(stack)) {
            return false;
        }
        crystals.set(chamber, stack.copyWithCount(1));
        setChanged();
        syncIfChanged();
        return true;
    }

    /** Tira o cristal da câmara {@code chamber}, ou {@link ItemStack#EMPTY} se ela está vazia ou fora do tier. */
    public ItemStack takeCrystalAt(int chamber) {
        if (chamber < 0 || chamber >= chambers() || crystals.get(chamber).isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = crystals.get(chamber);
        crystals.set(chamber, ItemStack.EMPTY);
        setChanged();
        notifyPorts(false, true);
        syncIfChanged();
        return stack;
    }

    /** Tira o cristal da última câmara ocupada, ou {@link ItemStack#EMPTY} se não há nenhum. */
    public ItemStack takeLastCrystal() {
        for (int i = MAX_CHAMBERS - 1; i >= 0; i--) {
            ItemStack stack = crystals.get(i);
            if (!stack.isEmpty()) {
                crystals.set(i, ItemStack.EMPTY);
                setChanged();
                notifyPorts(false, true);
                syncIfChanged();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** A contagem do catalisador da recarga (redstone ou Pó de Ressonância; o nome vem de antes do Pó). */
    public int redstone() {
        return catalyst.count();
    }

    /** O catalisador guardado e a regra do slot; os mutadores dele são do pacote. */
    public CoreCatalyst catalyst() {
        return catalyst;
    }

    /** Soma o que cabe da pilha, se o slot a aceita ({@link CoreCatalyst#canAdd}); devolve quanto entrou, sem mexer nela. */
    public int addCatalyst(ItemStack stack) {
        return catalyst.canAdd(tier(), stack) ? catalystAdded(catalyst.add(stack.getItem(), stack.getCount())) : 0;
    }

    /** Soma até {@code count} de redstone, sem a regra do tier (testes, bench e e2e), e devolve quanto entrou. */
    public int addRedstone(int count) {
        return catalystAdded(catalyst.add(Items.REDSTONE, count));
    }

    private int catalystAdded(int added) {
        if (added > 0) {
            setChanged();
            syncIfChanged();
        }
        return added;
    }

    /** Põe o catalisador em {@code count} de {@code item}, entre 0 e {@link #MAX_REDSTONE}; o slot do menu entra por aqui. */
    public void setCatalyst(Item item, int count) {
        catalyst.set(item, count);
        setChanged();
        syncIfChanged();
    }

    /** Põe a redstone em {@code count}, sem a regra do tier (testes e e2e). */
    public void setRedstone(int count) {
        setCatalyst(Items.REDSTONE, count);
    }

    /** Só para os GameTests: põe a redstone num valor conhecido. */
    public void setRedstoneForTest(int count) {
        setRedstone(count);
    }

    public long energy() {
        return energy;
    }

    /** A geração máxima por tick com o Eco; o buffer guarda {@link CoreMath#BUFFER_TICKS} dela. */
    public long maxOutput() {
        return eco.apply(CoreMath.maxOutput(chambers(), Config.resonatorFePerCrystal(tier())));
    }

    public long capacity() {
        return CoreMath.mulSat(maxOutput(), CoreMath.BUFFER_TICKS);
    }

    public long extractEnergy(long max, boolean simulate) {
        long taken = Math.max(0, Math.min(max, energy));
        if (!simulate && taken > 0) {
            energy -= taken;
            markUnsaved();
        }
        return taken;
    }

    /**
     * Marca o chunk para gravar sem avisar os vizinhos, como o {@code setChanged} faria: os vizinhos do Núcleo são a
     * casca, sem capability nem comparador, e quem espera nas Portas é avisado pelo {@link #notifyPorts}. Para o que
     * muda a cada extração e a cada lote.
     */
    void markUnsaved() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    /** FE por tick agora: os quartos dos cristais ({@link CoreYield}) na sintonia, na interferência e no Eco. */
    public long generationPerTick() {
        int quarters = formed ? CoreYield.quarters(crystals, chambers(), tier(), true) : 0;
        long fePerCrystal = Config.resonatorFePerCrystal(tier());
        return eco.apply(CoreMath.perTick(fePerCrystal, quarters, efficiency() * interference.factor()));
    }

    public CoreInterference interference() {
        return interference;
    }

    public CoreEcoState eco() {
        return eco;
    }

    double velocity() {
        return velocity;
    }

    void setDrift(Tuning.Drift drift) {
        station = drift.station();
        velocity = drift.velocity();
    }

    /** Conta um lote e diz se chegou o passo da deriva, a cada {@code every} lotes. */
    boolean countBatch(int every) {
        batches++;
        if (batches >= every) {
            batches = 0;
            return true;
        }
        return false;
    }

    /** Soma energia sem passar da capacidade. */
    void addEnergy(long amount) {
        energy += Math.max(0, Math.min(amount, capacity() - energy));
    }

    /** Ticks já andados da recarga do próximo Opaco (parado sem Opaco recarregável ou sem catalisador). */
    public int rechargeProgress() {
        return recharge;
    }

    void setRechargeProgress(int ticks) {
        recharge = ticks;
    }

    void spendCatalyst() {
        catalyst.spend();
    }

    boolean hasStation() {
        return hasStation;
    }

    void initStation(double initial) {
        hasStation = true;
        station = initial;
        velocity = 0;
        setChanged();
        syncIfChanged();
    }

    /**
     * Avisa os vizinhos das Portas do tipo dado, para acordar quem dorme nelas: o {@code setChanged} do Núcleo
     * só avisa os vizinhos dele. Só nas transições (energia que aparece, câmara que esvazia, catalisador que baixa,
     * Opaco ou rachado novo), nunca a cada lote.
     */
    void notifyPorts(boolean energyPorts, boolean crystalPorts) {
        if (level != null && !level.isClientSide) {
            CorePresence.notifyPorts(level, ports, energyPorts, crystalPorts);
        }
    }

    CoreFlow flow() {
        return flow;
    }

    List<BlockPos> ports() {
        return ports;
    }

    void setPorts(List<BlockPos> ports) {
        this.ports = List.copyOf(ports);
    }

    void setLastProblem(@Nullable ResonatorLayout.Broken problem) {
        lastProblem = problem;
    }

    /** Só marca o chunk quando algo muda: a revalidação da carga, com a estrutura intacta, não grava à toa. */
    void setFormed(boolean formed, List<BlockPos> members) {
        if (this.formed == formed && this.members.equals(members)) {
            return;
        }
        this.formed = formed;
        this.members = List.copyOf(members);
        origin = ChamberView.origin(this.members, worldPosition);
        setChanged();
        syncIfChanged();
    }

    /**
     * Manda a câmara aos clientes só se a {@link ChamberView} mudou desde a última vez (cristais, redstone, faixa da
     * eficiência, lâmpada, montagem); devolve se mandou. Só no servidor.
     */
    public boolean syncIfChanged() {
        return sync.sendIfChanged(this);
    }

    /** O início da recarga guardado na última câmara montada no servidor, ou -1 (para os GameTests). */
    public long syncedRechargeStart() {
        return sync.rechargeStart();
    }

    /** Quantas vezes o servidor mandou a câmara (para os GameTests). */
    public int syncCount() {
        return sync.syncCount();
    }

    /** A câmara recebida no cliente ({@link ChamberView#EMPTY} antes da primeira). */
    public ChamberView clientView() {
        return sync.clientView();
    }

    /** No cliente, o tempo de jogo em que a lâmpada acendeu pela última vez, ou -1 antes da primeira sintonia vista. */
    public long tunedAt() {
        return sync.tunedAt();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return sync.tag(this);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        sync.receive(tag, level);
    }

    /** O pacote leva só a view: não pode passar pelo {@code loadWithComponents}, que zeraria o resto. */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            CorePresence.enter(this, level);
            // A estrutura pode ter mudado com o chunk descarregado: confere no tick seguinte, fora da carga do chunk.
            // O tick agendado só roda com os chunks vizinhos carregados, então as posições a até 4 blocos estão
            // todas lá e nenhuma vira OTHER por estar descarregada (o que desmontaria à toa).
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    @Override
    public void setRemoved() {
        forget();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        forget();
        super.onChunkUnloaded();
    }

    /** Sai dos índices sem acordar ninguém: na carga, o {@code onLoad} devolve os membros antes da revalidação. */
    private void forget() {
        if (level != null && !level.isClientSide) {
            CorePresence.leave(this, level);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("formed", formed);
        CoreNbt.saveMembers(tag, members);
        tag.putBoolean("has_station", hasStation);
        tag.putDouble("station", station);
        tag.putDouble("velocity", velocity);
        tag.putDouble("dial", dial);
        CoreNbt.saveCrystals(tag, crystals, registries);
        catalyst.save(tag);
        tag.putInt("recharge", recharge);
        tag.putLong("energy", energy);
        tag.putInt("batches", batches);
        tag.putBoolean("auto", auto);
        shell.save(tag);
        eco.save(tag);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        eco.collectComponents(components);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        eco.restore(input.get(ModDataComponents.RESONATOR_ECO.get()));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        CoreEcoState.removeFrom(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("formed");
        members = CoreNbt.loadMembers(tag);
        origin = ChamberView.origin(members, worldPosition);
        hasStation = tag.getBoolean("has_station");
        station = tag.getDouble("station");
        velocity = tag.getDouble("velocity");
        dial = tag.contains("dial") ? Tuning.clampDial(tag.getDouble("dial")) : DEFAULT_DIAL;
        CoreNbt.loadCrystals(tag, crystals, registries);
        catalyst.load(tag);
        recharge = Math.max(0, tag.getInt("recharge"));
        energy = Math.max(0, tag.getLong("energy"));
        batches = Math.max(0, tag.getInt("batches"));
        shell.load(tag);
        auto = tag.getBoolean("auto") && shell.hasTuner();
        eco.load(tag);
    }
}
