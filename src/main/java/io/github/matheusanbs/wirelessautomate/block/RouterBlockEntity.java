package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Dados de um nó. Não faz tick: quem move recursos é o {@link NetworkManager}, que só conhece
 * os nós carregados no servidor.
 *
 * <p>A configuração das faces é guardada por {@link RelativeSide}, então girar o bloco gira a
 * configuração junto. A API recebe e devolve faces absolutas da máquina.
 */
public class RouterBlockEntity extends BlockEntity {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();
    private static final int FACES = Direction.values().length;

    private @Nullable UUID networkId;
    /** [tipo][lado relativo], tudo alocado de início: consultas não alocam. */
    private final FaceConfig[][] faces = new FaceConfig[TYPES.length][SIDES.length];
    private boolean powered;

    /** Caches por face absoluta da máquina, criados sob demanda e válidos para {@link #cacheFacing}. */
    private final BlockCapabilityCache<IItemHandler, @Nullable Direction>[] itemCaches = newCaches();
    private final BlockCapabilityCache<IFluidHandler, @Nullable Direction>[] fluidCaches = newCaches();
    private final BlockCapabilityCache<IEnergyStorage, @Nullable Direction>[] energyCaches = newCaches();
    private @Nullable Direction cacheFacing;

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROUTER.get(), pos, state);
        for (FaceConfig[] row : faces) {
            for (int i = 0; i < row.length; i++) {
                row[i] = new FaceConfig();
            }
        }
    }

    public RouterTier tier() {
        return getBlockState().getValue(RouterBlock.TIER);
    }

    public @Nullable UUID networkId() {
        return networkId;
    }

    public void setNetworkId(@Nullable UUID networkId) {
        if (Objects.equals(this.networkId, networkId)) {
            return;
        }
        this.networkId = networkId;
        changed();
    }

    public Direction facing() {
        return getBlockState().getValue(RouterBlock.FACING);
    }

    /** Posição da máquina onde o roteador está preso. */
    public BlockPos machinePos() {
        return RouterBlock.attachedPos(getBlockState(), worldPosition);
    }

    /** Configuração da face {@code machineFace} (absoluta) da máquina para {@code type}. Nunca nula. */
    public FaceConfig face(ResourceType type, Direction machineFace) {
        return face(type, RelativeSide.fromAbsolute(facing(), machineFace));
    }

    /** Configuração pelo lado relativo ao {@code facing}, como presets a guardam. Nunca nula. */
    public FaceConfig face(ResourceType type, RelativeSide side) {
        return faces[type.ordinal()][side.ordinal()];
    }

    public void setMode(ResourceType type, Direction machineFace, PortMode mode) {
        if (face(type, machineFace).setMode(mode)) {
            changed();
        }
    }

    public void setPriority(ResourceType type, Direction machineFace, int priority) {
        if (face(type, machineFace).setPriority(priority)) {
            changed();
        }
    }

    public void setRedstone(ResourceType type, Direction machineFace, RedstoneMode redstone) {
        if (face(type, machineFace).setRedstone(redstone)) {
            changed();
        }
    }

    /** Copia a configuração de uma face inteira, pelo lado relativo (para presets e o Configurador). */
    public void setFace(ResourceType type, RelativeSide side, FaceConfig config) {
        if (face(type, side).copyFrom(config)) {
            changed();
        }
    }

    /** Sinal de redstone no roteador, guardado e atualizado em neighborChanged (sem consulta por tick). */
    public boolean powered() {
        return powered;
    }

    /** Relê o sinal de redstone. Chamado pelo bloco quando um vizinho muda. */
    public void updatePowered() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean now = level.hasNeighborSignal(worldPosition);
        if (now != powered) {
            powered = now;
            setChanged();
            NetworkManager.get().nodeChanged(this);
        }
    }

    /** Inventário da máquina pela face {@code machineFace}, via BlockCapabilityCache. */
    public @Nullable IItemHandler items(Direction machineFace) {
        return capability(itemCaches, Capabilities.ItemHandler.BLOCK, machineFace);
    }

    public @Nullable IFluidHandler fluids(Direction machineFace) {
        return capability(fluidCaches, Capabilities.FluidHandler.BLOCK, machineFace);
    }

    public @Nullable IEnergyStorage energy(Direction machineFace) {
        return capability(energyCaches, Capabilities.EnergyStorage.BLOCK, machineFace);
    }

    /**
     * Devolve a capability pelo cache da face, criando o cache na primeira consulta. Se o roteador
     * foi girado, a máquina mudou de lugar e os caches são refeitos. Só no servidor.
     */
    private <T> @Nullable T capability(BlockCapabilityCache<T, @Nullable Direction>[] caches,
            BlockCapability<T, @Nullable Direction> capability, Direction machineFace) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return null;
        }
        Direction facing = facing();
        if (facing != cacheFacing) {
            clearCaches();
            cacheFacing = facing;
        }
        BlockCapabilityCache<T, @Nullable Direction> cache = caches[machineFace.ordinal()];
        if (cache == null) {
            cache = BlockCapabilityCache.create(capability, serverLevel, machinePos(), machineFace,
                    () -> !isRemoved() && facing() == facing,
                    () -> NetworkManager.get().nodeChanged(this));
            caches[machineFace.ordinal()] = cache;
        }
        return cache.getCapability();
    }

    private void clearCaches() {
        Arrays.fill(itemCaches, null);
        Arrays.fill(fluidCaches, null);
        Arrays.fill(energyCaches, null);
        cacheFacing = null;
    }

    @SuppressWarnings("unchecked")
    private static <T> BlockCapabilityCache<T, @Nullable Direction>[] newCaches() {
        return (BlockCapabilityCache<T, @Nullable Direction>[]) new BlockCapabilityCache[FACES];
    }

    /** Salva e, no servidor, avisa o gerenciador que as rotas deste nó precisam ser refeitas. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(this);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        Direction oldFacing = facing();
        RouterTier oldTier = tier();
        super.setBlockState(state);
        boolean rotated = facing() != oldFacing;
        if (rotated) {
            // Girado: a configuração relativa acompanha, mas a máquina mudou de lugar.
            clearCaches();
        }
        // Upgrade de tier muda vazão e alcance das rotas.
        if ((rotated || tier() != oldTier) && level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(this);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            // Ao carregar o chunk os vizinhos podem não estar carregados; aí fica o valor salvo.
            if (level.isAreaLoaded(worldPosition, 1)) {
                powered = level.hasNeighborSignal(worldPosition);
            }
            NetworkManager.get().addNode(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        removeFromManager();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        removeFromManager();
    }

    private void removeFromManager() {
        if (level != null && !level.isClientSide) {
            NetworkManager.get().removeNode(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (networkId != null) {
            tag.putUUID("network", networkId);
        }
        if (powered) {
            tag.putBoolean("powered", true);
        }
        CompoundTag facesTag = new CompoundTag();
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = new CompoundTag();
            for (RelativeSide side : SIDES) {
                FaceConfig config = face(type, side);
                if (!config.isDefault()) {
                    typeTag.put(side.key(), config.save());
                }
            }
            if (!typeTag.isEmpty()) {
                facesTag.put(typeKey(type), typeTag);
            }
        }
        if (!facesTag.isEmpty()) {
            tag.put("faces", facesTag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        networkId = tag.hasUUID("network") ? tag.getUUID("network") : null;
        powered = tag.getBoolean("powered");
        CompoundTag facesTag = tag.getCompound("faces");
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = facesTag.getCompound(typeKey(type));
            for (RelativeSide side : SIDES) {
                FaceConfig config = face(type, side);
                if (typeTag.contains(side.key(), CompoundTag.TAG_COMPOUND)) {
                    config.copyFrom(FaceConfig.load(typeTag.getCompound(side.key())));
                } else {
                    config.reset();
                }
            }
        }
        // Carga sobre um nó já no mundo (ex.: /data merge): as rotas mudam.
        if (level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(this);
        }
    }

    private static String typeKey(ResourceType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
