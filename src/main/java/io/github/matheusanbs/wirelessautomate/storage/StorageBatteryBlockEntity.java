package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.menu.StorageScalarMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

/**
 * Bateria: energia em FE ({@link ScalarStore}), vista por todo mundo como um
 * {@link net.minecraftforge.energy.IEnergyStorage} comum ({@link EnergyStoreHandler}), que já
 * passa até {@link Integer#MAX_VALUE} FE por chamada. Sem tipos, então sem filtro.
 */
public class StorageBatteryBlockEntity extends ScalarStorageBlockEntity {
    private final EnergyStoreHandler handler = new EnergyStoreHandler(store());

    public StorageBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY.get(), StorageKind.BATTERY, pos, state);
    }

    public EnergyStoreHandler handler() {
        return handler;
    }

    /** A de energia do Forge (para todo mundo) e a {@link BulkEnergy} (para o roteador, sem o teto do {@code int}). */
    @Override
    protected @Nullable Object exposed(Capability<?> capability) {
        if (capability == ForgeCapabilities.ENERGY) {
            return handler;
        }
        return capability == BulkEnergy.BLOCK ? store() : null;
    }

    @Override
    protected String contentsKey() {
        return "energy";
    }

    @Override
    public void open(ServerPlayer player) {
        StorageScalarMenu.open(player, this);
    }
}
