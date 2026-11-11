package io.github.matheusanbs.wirelessautomate.compat.rsmek;

import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.mekanism.ChemicalResource;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import org.jetbrains.annotations.Nullable;

/**
 * Os químicos da Doca na rede do RS2: o recurso {@link ChemicalResource} da integração do RS2 com o Mekanism, um
 * {@link ResourceKey} como o {@code ItemResource}. Só carrega com o RS2, o Mekanism e a integração, pela ponte
 * {@code compat/refinedstorage/Rs2Chemicals} (que confere o {@code LOADED} dela); o químico entra e sai como
 * {@code Object} ({@link ChemicalStack}).
 */
public final class RsMekDock {
    private RsMekDock() {
    }

    /** O recurso é um químico. */
    public static boolean isChemical(ResourceKey resource) {
        return resource instanceof ChemicalResource;
    }

    /** O químico de {@code amount} mB do recurso {@code resource}, ou {@code null} se ele não é químico. */
    public static @Nullable Object chemical(ResourceKey resource, long amount) {
        return resource instanceof ChemicalResource chemical ? new ChemicalStack(
                MekanismAPI.CHEMICAL_REGISTRY.wrapAsHolder(chemical.chemical()), amount) : null;
    }

    /**
     * O recurso do químico {@code stack}: {@code last} se ele já é o dele (a volta insere a mesma saída várias vezes),
     * senão um novo.
     */
    public static ResourceKey resource(Object stack, @Nullable ResourceKey last) {
        ChemicalStack chemical = (ChemicalStack) stack;
        if (last instanceof ChemicalResource known && known.chemical() == chemical.getChemical()) {
            return last;
        }
        return ChemicalResource.ofChemicalStack(chemical);
    }
}
