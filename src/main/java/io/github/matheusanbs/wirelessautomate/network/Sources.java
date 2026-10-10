package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.compat.arsnouveau.ArsSources;
import io.github.matheusanbs.wirelessautomate.compat.arsnouveau.ArsStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlockEntity;
import java.util.function.Function;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
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

    /**
     * Porte 1.20.1 (Ars 4.12, D5, sem capability de Source): o {@code ISourceTile} de um block entity (como
     * {@code Object}, ou {@code null} se não for um), ou a função {@code null} sem o Ars. O
     * {@code RouterBlockEntity.arsSource} guarda o resultado num {@code CapCache.ofBlockEntity}. (A tarefa 8
     * implementa {@code ArsSources.sourceTile}.)
     */
    public static @Nullable Function<BlockEntity, @Nullable Object> sourceTile() {
        return LOADED ? ArsSources::sourceTile : null;
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
