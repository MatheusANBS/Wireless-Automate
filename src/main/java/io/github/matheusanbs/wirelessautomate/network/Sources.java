package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.compat.arsnouveau.ArsSources;
import io.github.matheusanbs.wirelessautomate.compat.arsnouveau.ArsStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlockEntity;
import net.minecraft.core.Direction;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Ponte para a Source do Ars Nouveau sem carregar classes dele quando ele não está instalado. Quem
 * chama confere {@link #LOADED} (ou chama só com um handler que veio daqui); {@link ArsSources} só é
 * carregada pela JVM quando um destes métodos é chamado com o Ars presente.
 */
public final class Sources {
    /** O Ars Nouveau está instalado: a aba Source existe e o motor move Source. */
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("ars_nouveau");

    /** A capability de bloco da Source ({@code ars_nouveau:source}), ou {@code null} sem o Ars. */
    public static @Nullable BlockCapability<?, @Nullable Direction> capability() {
        return LOADED ? ArsSources.BLOCK : null;
    }

    /** Uma visita de uma origem de Source; sem o Ars não há portas de Source. */
    static boolean move(Port source, long now) {
        return LOADED && ScalarTransfer.move(source, now, ArsSources.ACCESS);
    }

    /** A capability do Ars no Tanque de Source; sem o Ars, nada. */
    public static void registerStorage(RegisterCapabilitiesEvent event) {
        if (LOADED) {
            ArsStorage.register(event);
        }
    }

    /** Põe o Tanque de Source no {@code SourceManager} do Ars (as máquinas dele tiram dali); sem o Ars, nada. */
    public static void registerProvider(StorageSourceTankBlockEntity tank) {
        if (LOADED) {
            ArsStorage.registerProvider(tank);
        }
    }

    private Sources() {
    }
}
