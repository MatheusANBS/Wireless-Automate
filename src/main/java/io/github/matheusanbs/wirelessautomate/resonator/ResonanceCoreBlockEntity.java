package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
    /** As Portas que este Núcleo pôs no índice; não vai para o NBT (a revalidação do {@code onLoad} refaz). */
    private List<BlockPos> ports = List.of();
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
    }

    /** A eficiência da sintonia agora, pelo desvio entre a estação e o dial. */
    public double efficiency() {
        return Tuning.efficiency(station - dial);
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

    /** As {@link #MAX_CHAMBERS} câmaras; só as {@link #chambers()} primeiras valem. */
    public NonNullList<ItemStack> crystals() {
        return crystals;
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
        }
        return added;
    }

    /** Só para os GameTests: põe a redstone num valor conhecido. */
    public void setRedstoneForTest(int count) {
        redstone = Math.max(0, Math.min(MAX_REDSTONE, count));
        setChanged();
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
            setChanged();
        }
        return taken;
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
        return CoreMath.perTick(Config.resonatorFePerCrystal(tier()), yieldSum, efficiency());
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

    int rechargeProgress() {
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

    List<BlockPos> ports() {
        return ports;
    }

    void setPorts(List<BlockPos> ports) {
        this.ports = List.copyOf(ports);
    }

    void setLastProblem(@Nullable ResonatorLayout.Broken problem) {
        lastProblem = problem;
    }

    void setFormed(boolean formed, List<BlockPos> members) {
        this.formed = formed;
        this.members = List.copyOf(members);
        setChanged();
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
    }
}
