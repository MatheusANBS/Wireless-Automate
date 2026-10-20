package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * O estado do Núcleo de Ressonância (sem ticker: o gerenciador trabalha em lote). Guarda se a estrutura está
 * montada, as 98 posições da casca, a sintonia, os cristais, a redstone e a energia; quem monta e desmonta é a
 * {@link ResonatorFormation} e quem gera, gasta e recarrega é o {@link ResonatorBatch}.
 */
public class ResonanceCoreBlockEntity extends BlockEntity {
    /** O dial começa no meio da faixa até o jogador mexer. */
    static final double DEFAULT_DIAL = 98.0;
    /** As câmaras do último tier; as do tier do Núcleo são as primeiras. */
    public static final int MAX_CHAMBERS = CoreMath.chambers(RouterTier.values().length - 1);
    public static final int MAX_REDSTONE = 64;

    private boolean formed;
    private List<BlockPos> members = List.of();
    /** O canto mínimo da caixa, refeito só quando os membros mudam ({@link ChamberView#origin}). */
    private int[] origin = ChamberView.origin(List.of(), worldPosition);
    /** As Portas que este Núcleo pôs no índice; não vai para o NBT (a revalidação do {@code onLoad} refaz). */
    private List<BlockPos> ports = List.of();
    /** A vazão por Porta de Energia e o "cheio" do último lote, que vão à câmara; fora do NBT. */
    private final CoreFlow flow = new CoreFlow();
    private @Nullable ResonatorLayout.Broken lastProblem;
    private boolean hasStation;
    private double station;
    private double velocity;
    private double dial = DEFAULT_DIAL;
    private final NonNullList<ItemStack> crystals = NonNullList.withSize(MAX_CHAMBERS, ItemStack.EMPTY);
    private int redstone;
    /** Ticks já andados da recarga do próximo Opaco. */
    private int recharge;
    private long energy;
    /** Lotes desde o último passo da deriva. */
    private int batches;
    /** Modo automático: o Sintonizador anda o dial até a estação, com a eficiência no teto do tier. */
    private boolean auto;
    /** O que a última montagem achou na casca; a ligação do Sintonizador fica fixa até a próxima. */
    private boolean hasTuner;
    private int energyPorts;
    private int crystalPorts;
    private ResonatorBlueprint.@Nullable Layout layout;
    /**
     * O tempo de jogo da última montagem, ou -1 se nunca montou; 0 num Núcleo que já estava montado antes de o tempo
     * ser gravado (a sequência de acender fica no passado).
     */
    private long formedAt = -1;
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

    /** As posições da casca montada (vazia sem estrutura). */
    /** O canto mínimo da caixa (x, y, z), guardado; quem lê não muda o array. */
    int[] origin() {
        return origin;
    }

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
        if (auto && !hasTuner) {
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
        return hasTuner;
    }

    public int energyPorts() {
        return energyPorts;
    }

    public int crystalPorts() {
        return crystalPorts;
    }

    /** A montagem reconhecida (Vitrine ou Faixa), ou {@code null} se é própria ou nunca montou. */
    public ResonatorBlueprint.@Nullable Layout layout() {
        return layout;
    }

    public long formedAt() {
        return formedAt;
    }

    /**
     * Guarda o que a montagem achou na casca; sem Sintonizador o modo volta a Manual com o dial onde parou. O tempo
     * só muda quando a estrutura estava desmontada: a revalidação de uma estrutura de pé não reacende a câmara. Um
     * Núcleo salvo montado sem o tempo ganha 0, para a carga do chunk não acender a câmara em sequência. Não manda a
     * câmara: quem monta manda uma vez só depois do {@code setFormed}, já com a origem nova.
     */
    void setStructure(boolean hasTuner, int energyPorts, int crystalPorts, ResonatorBlueprint.@Nullable Layout layout,
            long gameTime) {
        boolean changed = this.hasTuner != hasTuner || this.energyPorts != energyPorts
                || this.crystalPorts != crystalPorts || this.layout != layout;
        this.hasTuner = hasTuner;
        this.energyPorts = energyPorts;
        this.crystalPorts = crystalPorts;
        this.layout = layout;
        if (!hasTuner && auto) {
            auto = false;
            changed = true;
        }
        if (!formed) {
            formedAt = gameTime;
            changed = true;
        } else if (formedAt < 0) {
            formedAt = 0;
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

    /** As câmaras do tier e a redstone como {@link net.minecraft.world.Container}, para o menu. */
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

    /** Põe um cristal na primeira câmara vazia e devolve o que sobrou da pilha (sem mexer nela). */
    public ItemStack insertCrystal(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof SignalCrystalItem)) {
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
     * Põe uma unidade do cristal na câmara {@code chamber}, se ela é do tier e está vazia; devolve se pôs. A Porta
     * de Cristais entra por aqui, slot a slot.
     */
    public boolean insertCrystalAt(int chamber, ItemStack stack) {
        if (chamber < 0 || chamber >= chambers() || !crystals.get(chamber).isEmpty()
                || !(stack.getItem() instanceof SignalCrystalItem)) {
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

    public int redstone() {
        return redstone;
    }

    /** Soma até {@code count} de redstone sem passar de {@link #MAX_REDSTONE} e devolve quanto entrou. */
    public int addRedstone(int count) {
        int added = Math.max(0, Math.min(count, MAX_REDSTONE - redstone));
        if (added > 0) {
            redstone += added;
            setChanged();
            syncIfChanged();
        }
        return added;
    }

    /** Põe a redstone em {@code count}, entre 0 e {@link #MAX_REDSTONE}; o slot do menu entra por aqui. */
    public void setRedstone(int count) {
        redstone = Math.max(0, Math.min(MAX_REDSTONE, count));
        setChanged();
        syncIfChanged();
    }

    /** Só para os GameTests: põe a redstone num valor conhecido. */
    public void setRedstoneForTest(int count) {
        setRedstone(count);
    }

    public long energy() {
        return energy;
    }

    public long capacity() {
        return CoreMath.buffer(chambers(), Config.resonatorFePerCrystal(tier()));
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

    /** FE por tick agora: os cristais não Opacos das câmaras do tier, na sintonia atual; 0 desmontado. */
    public long generationPerTick() {
        if (!formed) {
            return 0;
        }
        int yieldSum = 0;
        for (int i = 0; i < chambers(); i++) {
            ItemStack stack = crystals.get(i);
            if (stack.getItem() instanceof SignalCrystalItem item && !SignalCrystalItem.isDull(stack)) {
                yieldSum += item.yield();
            }
        }
        return CoreMath.perTick(Config.resonatorFePerCrystal(tier()), yieldSum * 4, efficiency());
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

    /** Ticks já andados da recarga do próximo Opaco (parado sem Opaco ou sem redstone). */
    public int rechargeProgress() {
        return recharge;
    }

    void setRechargeProgress(int ticks) {
        recharge = ticks;
    }

    void spendRedstone() {
        redstone = Math.max(0, redstone - 1);
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
     * só avisa os vizinhos dele. Só nas transições (energia que aparece, câmara que esvazia, redstone que baixa,
     * Opaco novo), nunca a cada lote.
     */
    void notifyPorts(boolean energyPorts, boolean crystalPorts) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (BlockPos port : ports) {
            if (!level.isLoaded(port)) {
                continue;
            }
            BlockState state = level.getBlockState(port);
            if (state.getBlock() instanceof ResonatorPartBlock part
                    && (energyPorts && part.kind() == ResonatorPartBlock.Kind.ENERGY_PORT
                            || crystalPorts && part.kind() == ResonatorPartBlock.Kind.CRYSTAL_PORT)) {
                level.updateNeighbourForOutputSignal(port, state.getBlock());
            }
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
            ResonatorManager.add(this);
            if (formed) {
                for (BlockPos member : members) {
                    ResonatorMembers.claimIfFree(level.dimension(), member, worldPosition);
                }
            }
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

    /**
     * Sai do gerenciador e tira as Portas e os membros dos índices, sem mexer nos blocos nem acordar ninguém: na
     * carga, o {@code onLoad} devolve os membros antes de qualquer revalidação.
     */
    private void forget() {
        if (level != null && !level.isClientSide) {
            ResonatorManager.remove(this);
            for (BlockPos port : ports) {
                ResonatorPorts.unregister(level.dimension(), port, worldPosition);
            }
            ports = List.of();
            for (BlockPos member : members) {
                ResonatorMembers.release(level.dimension(), member, worldPosition);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("formed", formed);
        long[] packed = new long[members.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = members.get(i).asLong();
        }
        tag.putLongArray("members", packed);
        tag.putBoolean("has_station", hasStation);
        tag.putDouble("station", station);
        tag.putDouble("velocity", velocity);
        tag.putDouble("dial", dial);
        ListTag list = new ListTag();
        for (int i = 0; i < MAX_CHAMBERS; i++) {
            ItemStack stack = crystals.get(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("slot", (byte) i);
                list.add(stack.save(registries, entry));
            }
        }
        tag.put("crystals", list);
        tag.putInt("redstone", redstone);
        tag.putInt("recharge", recharge);
        tag.putLong("energy", energy);
        tag.putInt("batches", batches);
        tag.putBoolean("auto", auto);
        tag.putBoolean("has_tuner", hasTuner);
        tag.putInt("energy_ports", energyPorts);
        tag.putInt("crystal_ports", crystalPorts);
        tag.putByte("layout", layoutByte(layout));
        tag.putLong("formed_at", formedAt);
    }

    /** 0 montagem própria (ou nenhuma), 1 Vitrine, 2 Faixa. */
    private static byte layoutByte(ResonatorBlueprint.@Nullable Layout layout) {
        return layout == null ? 0 : (byte) (layout.ordinal() + 1);
    }

    private static ResonatorBlueprint.@Nullable Layout layoutOf(byte value) {
        ResonatorBlueprint.Layout[] all = ResonatorBlueprint.Layout.values();
        return value >= 1 && value <= all.length ? all[value - 1] : null;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("formed");
        List<BlockPos> loaded = new ArrayList<>();
        for (long packed : tag.getLongArray("members")) {
            loaded.add(BlockPos.of(packed));
        }
        members = List.copyOf(loaded);
        origin = ChamberView.origin(members, worldPosition);
        hasStation = tag.getBoolean("has_station");
        station = tag.getDouble("station");
        velocity = tag.getDouble("velocity");
        dial = tag.contains("dial") ? Tuning.clampDial(tag.getDouble("dial")) : DEFAULT_DIAL;
        for (int i = 0; i < MAX_CHAMBERS; i++) {
            crystals.set(i, ItemStack.EMPTY);
        }
        ListTag list = tag.getList("crystals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("slot") & 0xFF;
            if (slot < MAX_CHAMBERS) {
                crystals.set(slot, ItemStack.parse(registries, entry).orElse(ItemStack.EMPTY));
            }
        }
        redstone = Math.max(0, Math.min(MAX_REDSTONE, tag.getInt("redstone")));
        recharge = Math.max(0, tag.getInt("recharge"));
        energy = Math.max(0, tag.getLong("energy"));
        batches = Math.max(0, tag.getInt("batches"));
        hasTuner = tag.getBoolean("has_tuner");
        auto = tag.getBoolean("auto") && hasTuner;
        energyPorts = Math.max(0, tag.getInt("energy_ports"));
        crystalPorts = Math.max(0, tag.getInt("crystal_ports"));
        layout = layoutOf(tag.getByte("layout"));
        formedAt = tag.contains("formed_at") ? tag.getLong("formed_at") : -1;
    }
}
