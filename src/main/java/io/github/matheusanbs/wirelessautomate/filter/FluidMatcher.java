package io.github.matheusanbs.wirelessautomate.filter;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/** {@link Filter} compilado para fluidos; mesmo esquema do {@link ItemMatcher}, com {@link Fluid} de chave. */
final class FluidMatcher extends CompiledMatcher<Fluid> {
    /** Fluido + componentes, ignorando a quantidade. */
    static final Hash.Strategy<FluidStack> FLUID_AND_COMPONENTS = new Hash.Strategy<>() {
        @Override
        public int hashCode(@Nullable FluidStack stack) {
            return stack == null ? 0 : stack.hashCode();
        }

        @Override
        public boolean equals(@Nullable FluidStack a, @Nullable FluidStack b) {
            return a == b || a != null && b != null && a.isFluidEqual(b);
        }
    };

    private final @Nullable Object2IntOpenCustomHashMap<FluidStack> exactWithComponents;

    FluidMatcher(List<FilterEntry> entries, boolean matchComponents) {
        super(entries);
        if (matchComponents) {
            exactWithComponents = new Object2IntOpenCustomHashMap<>(FLUID_AND_COMPONENTS);
            exactWithComponents.defaultReturnValue(NONE);
        } else {
            exactWithComponents = null;
        }
        compile(BuiltInRegistries.FLUID, !matchComponents);
    }

    /** Índice da primeira entrada que casa com o fluido, ou {@link #NONE}. */
    int index(FluidStack stack) {
        int index = keyIndex(stack.getFluid());
        if (exactWithComponents != null && !exactWithComponents.isEmpty()) {
            index = first(index, exactWithComponents.getInt(stack));
        }
        return index;
    }

    @Override
    @Nullable Fluid exactKey(FilterEntry entry) {
        return entry instanceof FilterEntry.FluidEntry fluid ? fluid.stack().getFluid() : null;
    }

    @Override
    void addExact(FilterEntry entry, int index) {
        exactWithComponents.putIfAbsent(((FilterEntry.FluidEntry) entry).stack(), index);
    }

    @Override
    @SuppressWarnings("deprecation")
    String namespaceOf(Fluid fluid) {
        // O id pelo holder do registro: sem busca no mapa do registro.
        return fluid.builtInRegistryHolder().key().location().getNamespace();
    }
}
