package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import net.minecraft.world.level.Level;

/**
 * O aviso de que a configuração de um {@link RouterBlockEntity} mudou: salvar, a tela, o motor (com os tipos
 * que mudaram) e o índice. Dentro de um lote ({@link RouterBlockEntity#batchChanges}) só acumula os tipos e
 * avisa uma vez ao sair do lote mais de fora.
 */
final class RouterChanges {
    private final RouterBlockEntity router;
    /** Profundidade do lote e os tipos que mudaram dentro dele. */
    private int batchDepth;
    private int batchTypes;

    RouterChanges(RouterBlockEntity router) {
        this.router = router;
    }

    /** Ver {@link RouterBlockEntity#batchChanges}. */
    void batch(Runnable changes) {
        batchDepth++;
        try {
            changes.run();
        } finally {
            if (--batchDepth == 0 && batchTypes != 0) {
                int types = batchTypes;
                batchTypes = 0;
                notifyChanged(types);
            }
        }
    }

    /** Ver {@link RouterBlockEntity#changed}. */
    void changed(int types) {
        if (batchDepth > 0) {
            batchTypes |= types;
            return;
        }
        notifyChanged(types);
    }

    private void notifyChanged(int types) {
        router.bumpChangeVersion();
        router.setChanged();
        Level level = router.getLevel();
        if (level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(router, types);
            NodeIndex.track(router);
        }
    }
}
