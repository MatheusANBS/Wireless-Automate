package io.github.matheusanbs.wirelessautomate.storage;

import it.unimi.dsi.fastutil.Hash;
import java.util.function.LongSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Conteúdo do Tanque: fluidos (fluido e componentes) com quantidade {@code long} em mB, quantos
 * fluidos diferentes couberem na capacidade. As regras estão no {@link KeyedStorage}. É também a
 * {@link BulkFluids} que o roteador usa.
 */
public final class FluidStorage extends KeyedStorage<FluidStack> implements BulkFluids {
    /** Mesmo fluido e mesmos componentes, como o {@link FluidStack#isFluidEqual}. */
    public static final Hash.Strategy<FluidStack> FLUID_AND_COMPONENTS = new Hash.Strategy<>() {
        @Override
        public int hashCode(@Nullable FluidStack stack) {
            return stack == null ? 0 : stack.hashCode();
        }

        @Override
        public boolean equals(@Nullable FluidStack a, @Nullable FluidStack b) {
            return a == b || a != null && b != null && a.isFluidEqual(b);
        }
    };

    public FluidStorage(Runnable onChange, LongSupplier capacity) {
        super(FLUID_AND_COMPONENTS, onChange, capacity, "Tanque");
    }

    @Override
    protected boolean isEmptyKey(FluidStack key) {
        return key.isEmpty();
    }

    @Override
    protected FluidStack normalize(FluidStack key) {
        return new FluidStack(key, 1);
    }

    @Override
    protected Tag saveKey(FluidStack key) {
        return key.writeToNBT(new CompoundTag());
    }

    @Override
    protected @Nullable FluidStack loadKey(Tag tag) {
        if (!(tag instanceof CompoundTag compound)) {
            return null;
        }
        FluidStack stack = FluidStack.loadFluidStackFromNBT(compound);
        return stack.isEmpty() ? null : stack;
    }

    /** Quanto há do fluido, com quaisquer componentes (O(tipos)). */
    @Override
    public long countFluid(FluidStack key) {
        return countWhere(stored -> stored.getFluid() == key.getFluid());
    }
}
