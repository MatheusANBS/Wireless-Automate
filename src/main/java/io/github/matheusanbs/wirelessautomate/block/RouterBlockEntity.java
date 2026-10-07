package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Dados de um nó. Não faz tick: quem move recursos é o {@link NetworkManager}, que só conhece
 * os nós carregados no servidor.
 */
public class RouterBlockEntity extends BlockEntity {
    private @Nullable UUID networkId;

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROUTER.get(), pos, state);
    }

    public RouterTier tier() {
        return getBlockState().getValue(RouterBlock.TIER);
    }

    public @Nullable UUID networkId() {
        return networkId;
    }

    public void setNetworkId(@Nullable UUID networkId) {
        this.networkId = networkId;
        setChanged();
        // TODO(contrato): avisar NetworkManager.get().nodeChanged(this) no servidor.
    }

    // TODO(contrato): as assinaturas abaixo são o contrato entre as partes; implementar.

    public Direction facing() {
        return getBlockState().getValue(RouterBlock.FACING);
    }

    /** Posição da máquina onde o roteador está preso. */
    public BlockPos machinePos() {
        return RouterBlock.attachedPos(getBlockState(), worldPosition);
    }

    /** Configuração da face {@code machineFace} (absoluta) da máquina para {@code type}. Nunca nula. */
    public FaceConfig face(ResourceType type, Direction machineFace) {
        throw new UnsupportedOperationException("TODO");
    }

    public void setMode(ResourceType type, Direction machineFace, PortMode mode) {
        throw new UnsupportedOperationException("TODO");
    }

    public void setPriority(ResourceType type, Direction machineFace, int priority) {
        throw new UnsupportedOperationException("TODO");
    }

    public void setRedstone(ResourceType type, Direction machineFace, RedstoneMode redstone) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Sinal de redstone no roteador, guardado e atualizado em neighborChanged (sem consulta por tick). */
    public boolean powered() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Inventário da máquina pela face {@code machineFace}, via BlockCapabilityCache. */
    public @Nullable IItemHandler items(Direction machineFace) {
        throw new UnsupportedOperationException("TODO");
    }

    public @Nullable IFluidHandler fluids(Direction machineFace) {
        throw new UnsupportedOperationException("TODO");
    }

    public @Nullable IEnergyStorage energy(Direction machineFace) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        networkId = tag.hasUUID("network") ? tag.getUUID("network") : null;
    }
}
