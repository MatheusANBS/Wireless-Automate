package io.github.matheusanbs.wirelessautomate.compat.refinedstorage;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import io.github.matheusanbs.wirelessautomate.dock.DockReturnNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A Doca para o Refined Storage 2: uma fábrica de destinos externos, que o Autocrafter chama uma vez (ao entrar na
 * rede) com a posição do vizinho para que aponta e a face dele encostada no Autocrafter. O RS2 junta os destinos de
 * todas as fábricas para essa posição; o da Doca ({@link Rs2DockSink}) é preguiçoso e só olha a posição a cada pedido.
 * A volta usa a rede do Autocrafter ({@link Rs2DockNetwork}). Só carrega com o RS2 (pela ponte {@code dock/DockRs2}).
 */
public final class Rs2Dock {
    private Rs2Dock() {
    }

    public static void register() {
        RefinedStorageApi.INSTANCE.addPatternProviderExternalPatternSinkFactory(Rs2DockSink::new);
    }

    /** O destino da volta para a rede do Autocrafter em {@code pos} ({@link Rs2DockNetwork}). */
    public static DockReturnNetwork target(ServerLevel level, BlockPos pos) {
        return new Rs2DockNetwork(level, pos);
    }
}
