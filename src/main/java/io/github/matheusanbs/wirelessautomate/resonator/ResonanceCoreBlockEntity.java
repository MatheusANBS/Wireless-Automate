package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * O estado do Núcleo de Ressonância (sem ticker: o gerenciador trabalha em lote). Guarda se a estrutura está
 * montada, as 98 posições da casca e a sintonia; quem monta e desmonta é a {@link ResonatorFormation}.
 */
public class ResonanceCoreBlockEntity extends BlockEntity {
    /** O dial começa no meio da faixa até o jogador mexer. */
    static final double DEFAULT_DIAL = 98.0;

    private boolean formed;
    private List<BlockPos> members = List.of();
    /** As Portas que este Núcleo pôs no índice; não vai para o NBT (a revalidação do {@code onLoad} refaz). */
    private List<BlockPos> ports = List.of();
    private @Nullable ResonatorLayout.Broken lastProblem;
    private boolean hasStation;
    private double station;
    private double velocity;
    private double dial = DEFAULT_DIAL;

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

    boolean hasStation() {
        return hasStation;
    }

    void initStation(double initial) {
        hasStation = true;
        station = initial;
        velocity = 0;
        setChanged();
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
            // A estrutura pode ter mudado com o chunk descarregado: confere no tick seguinte, fora da carga do chunk.
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

    /** Sai do gerenciador e tira as Portas do índice, sem mexer nos blocos. */
    private void forget() {
        if (level != null && !level.isClientSide) {
            ResonatorManager.remove(this);
            for (BlockPos port : ports) {
                ResonatorPorts.unregister(level.dimension(), port);
            }
            ports = List.of();
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
    }
}
