package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.function.Consumer;

/** Ganchos que o código de {@code client/resonator} preenche; o código comum não referencia nada do cliente. */
public final class CoreClientHooks {
    /** Chamado no cliente quando o update tag de um Núcleo chega com uma câmara (a view nova já está nele). */
    public static volatile Consumer<ResonanceCoreBlockEntity> changed = core -> {
    };

    private CoreClientHooks() {
    }
}
