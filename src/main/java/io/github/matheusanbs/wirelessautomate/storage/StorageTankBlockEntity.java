package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Tanque: fluidos por tipo ({@link FluidStorage}), em mB, vistos por todo mundo (canos, o
 * roteador) como um handler de fluido comum ({@link FluidStorageHandler}), que já passa até
 * {@link Integer#MAX_VALUE} mB por chamada.
 */
public class StorageTankBlockEntity extends KeyedStorageBlockEntity<FluidStack> {
    private final FluidStorage storage = new FluidStorage(this::markContentsChanged, this::capacity);
    private final FluidStorageHandler handler = new FluidStorageHandler(storage);

    public StorageTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK.get(), StorageKind.TANK, pos, state);
        storage.setAdmission(this::admit);
    }

    public FluidStorage storage() {
        return storage;
    }

    @Override
    public KeyedStorage<FluidStack> keyed() {
        return storage;
    }

    public FluidStorageHandler handler() {
        return handler;
    }

    @Override
    protected String contentsKey() {
        return "fluids";
    }

    /** Só até o Tanque ter N mB do fluido: o limite da entrada que casa, ou o limite por tipo do Tanque. */
    private long admit(FluidStack key, long amount) {
        Filter filter = filter();
        long limit = filter.fluidTypeLimit(key);
        if (limit <= 0) {
            return amount;
        }
        long present = filter.matchComponents() ? storage.count(key) : storage.countFluid(key);
        return StockLimit.acceptable(present, limit, amount);
    }
}
