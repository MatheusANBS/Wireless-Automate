package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.menu.StorageBatteryMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Bateria: energia em FE ({@link EnergyStore}), vista por todo mundo como um
 * {@link net.neoforged.neoforge.energy.IEnergyStorage} comum ({@link EnergyStoreHandler}), que já
 * passa até {@link Integer#MAX_VALUE} FE por chamada. Sem tipos, então sem filtro.
 */
public class StorageBatteryBlockEntity extends StorageBlockEntity {
    private final EnergyStore store = new EnergyStore(this::setChanged, this::capacity);
    private final EnergyStoreHandler handler = new EnergyStoreHandler(store);

    public StorageBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY.get(), StorageKind.BATTERY, pos, state);
    }

    public EnergyStore store() {
        return store;
    }

    public EnergyStoreHandler handler() {
        return handler;
    }

    @Override
    public boolean isEmptyContents() {
        return store.isEmpty();
    }

    @Override
    public long total() {
        return store.stored();
    }

    @Override
    public int types() {
        return 0;
    }

    @Override
    public int contentsVersion() {
        return store.version();
    }

    @Override
    protected Tag saveContents(HolderLookup.Provider registries) {
        return LongTag.valueOf(store.stored());
    }

    @Override
    protected void loadContents(@Nullable Tag tag, HolderLookup.Provider registries) {
        store.set(tag instanceof NumericTag number ? number.getAsLong() : 0);
    }

    @Override
    protected void clearContents() {
        store.set(0);
    }

    @Override
    protected String contentsKey() {
        return "energy";
    }

    @Override
    public void open(ServerPlayer player) {
        StorageBatteryMenu.open(player, this);
    }
}
