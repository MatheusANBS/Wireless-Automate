package io.github.matheusanbs.wirelessautomate.compat.refinedstorage;

import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import io.github.matheusanbs.wirelessautomate.compat.rsmek.RsMekDock;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Ponte da Doca do RS2 para os químicos da integração do RS2 com o Mekanism, sem tipos do Mekanism: só esta classe,
 * fora do {@code compat/rsmek}, conhece o {@link RsMekDock}, e só o chama com {@link #LOADED}. O químico passa como
 * {@code Object} ({@code ChemicalStack}), como na ponte {@code network/Chemicals}.
 */
public final class Rs2Chemicals {
    /** O Mekanism e a integração estão instalados: a rede do RS2 guarda químicos e o Autocrafter os entrega. */
    public static final boolean LOADED = Chemicals.LOADED && ModList.get() != null
            && ModList.get().isLoaded("refinedstorage_mekanism_integration");

    private Rs2Chemicals() {
    }

    /** O recurso é um químico (sempre falso sem a integração). */
    public static boolean isChemical(ResourceKey resource) {
        return LOADED && RsMekDock.isChemical(resource);
    }

    /** O químico ({@code ChemicalStack}) de {@code amount} mB do recurso; {@code null} se não é químico. */
    public static @Nullable Object chemical(ResourceKey resource, long amount) {
        return LOADED ? RsMekDock.chemical(resource, amount) : null;
    }

    /** O recurso do químico {@code stack} ({@code last} se é o mesmo). Só com {@link #LOADED}. */
    public static ResourceKey resource(Object stack, @Nullable ResourceKey last) {
        return RsMekDock.resource(stack, last);
    }
}
