package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.range.OwnerIndex;
import org.jetbrains.annotations.Nullable;

/**
 * O {@link OwnerIndex} das Docas carregadas por posição de máquina vinculada, ativa ou não (fora do alcance e além do
 * limite também): a unicidade entre Docas ({@link DockLinks#otherDock}), o Ctrl + clique e o Configurador
 * ({@link DockTuner}) consultam aqui. Um por servidor, descartado na parada. Mantido pela Doca a cada registro refeito
 * ({@code onLoad}, vínculo, desvínculo, tier, config, carga de NBT) e ao sair ({@code setRemoved},
 * {@code onChunkUnloaded}).
 */
public final class DockIndex {
    private static @Nullable OwnerIndex<PatternDockBlockEntity> instance;

    private DockIndex() {
    }

    public static OwnerIndex<PatternDockBlockEntity> get() {
        if (instance == null) {
            instance = new OwnerIndex<>();
        }
        return instance;
    }

    /** Descarta o estado ao parar o servidor. */
    public static void reset() {
        instance = null;
    }
}
