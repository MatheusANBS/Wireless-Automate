package io.github.matheusanbs.wirelessautomate.storage;

import it.unimi.dsi.fastutil.Hash;
import java.util.function.LongSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Conteúdo do Tanque: fluidos (fluido e componentes) com quantidade {@code long} em mB, quantos
 * fluidos diferentes couberem na capacidade. As regras estão no {@link KeyedStorage}.
 */
public final class FluidStorage extends KeyedStorage<FluidStack> {
    /** Mesmo fluido e mesmos componentes, como o {@link FluidStack#isSameFluidSameComponents}. */
    public static final Hash.Strategy<FluidStack> FLUID_AND_COMPONENTS = new Hash.Strategy<>() {
        @Override
        public int hashCode(@Nullable FluidStack stack) {
            return stack == null ? 0 : FluidStack.hashFluidAndComponents(stack);
        }

        @Override
        public boolean equals(@Nullable FluidStack a, @Nullable FluidStack b) {
            return a == b || a != null && b != null && FluidStack.isSameFluidSameComponents(a, b);
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
        return key.copyWithAmount(1);
    }

    @Override
    protected Tag saveKey(FluidStack key, HolderLookup.Provider registries) {
        return key.save(registries);
    }

    @Override
    protected @Nullable FluidStack loadKey(Tag tag, HolderLookup.Provider registries) {
        return FluidStack.parse(registries, tag).orElse(null);
    }

    /** Quanto há do fluido, com quaisquer componentes (O(tipos)). */
    public long countFluid(FluidStack key) {
        return countWhere(stored -> FluidStack.isSameFluid(stored, key));
    }
}
