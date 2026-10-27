package io.github.matheusanbs.wirelessautomate.tv;

import java.util.function.Consumer;

/** Ganchos que o código de {@code client/tv} preenche; o código comum não referencia nada do cliente. */
public final class TvClientHooks {
    /** Chamado no cliente quando o update tag de uma Tela chega, para marcar a imagem dela como suja. */
    public static volatile Consumer<TvBlockEntity> changed = tv -> {
    };

    private TvClientHooks() {
    }
}
