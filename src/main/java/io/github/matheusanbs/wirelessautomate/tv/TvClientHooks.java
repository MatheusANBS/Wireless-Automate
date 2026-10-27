package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvLibraryPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvValuesPayload;
import java.util.function.Consumer;

/** Ganchos que o código de {@code client/tv} preenche; o código comum não referencia nada do cliente. */
public final class TvClientHooks {
    /** Chamado no cliente quando o update tag de uma Tela chega, para marcar a imagem dela como suja. */
    public static volatile Consumer<TvBlockEntity> changed = tv -> {
    };

    /** Chamado no cliente quando os números de uma Tela chegam do servidor. */
    public static volatile Consumer<TvValuesPayload> values = payload -> {
    };

    /** Chamado no cliente quando a biblioteca do jogador chega, com o editor aberto. */
    public static volatile Consumer<TvLibraryPayload> library = payload -> {
    };

    private TvClientHooks() {
    }
}
