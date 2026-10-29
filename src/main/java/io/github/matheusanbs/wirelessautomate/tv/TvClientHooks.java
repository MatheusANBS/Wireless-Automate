package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvArtsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvImageDataPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvImageReplyPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutAckPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvPreviewValuesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvStorageContentsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvValuesPayload;
import java.util.function.Consumer;

/** Ganchos que o código de {@code client/tv} preenche; o código comum não referencia nada do cliente. */
public final class TvClientHooks {
    /**
     * Chamado no cliente quando o update tag de uma Tela chega, para pôr no layout as artes que o cliente já tem e
     * marcar a imagem dela como suja.
     */
    public static volatile Consumer<TvBlockEntity> changed = tv -> {
    };

    /** Chamado no cliente quando os números de uma Tela chegam do servidor. */
    public static volatile Consumer<TvValuesPayload> values = payload -> {
    };

    /** Chamado no cliente quando os pixels das artes de uma Tela chegam. */
    public static volatile Consumer<TvArtsPayload> arts = payload -> {
    };

    /** Chamado no cliente quando um pedaço de uma imagem enviada chega. */
    public static volatile Consumer<TvImageDataPayload> imageData = payload -> {
    };

    /** Chamado no cliente quando a resposta a um envio de imagem chega. */
    public static volatile Consumer<TvImageReplyPayload> imageReply = payload -> {
    };

    /** Chamado no cliente quando a resposta a um salvar do layout chega. */
    public static volatile Consumer<TvLayoutAckPayload> layoutAck = payload -> {
    };

    /** Chamado no cliente quando a biblioteca do jogador chega, com o editor aberto. */
    public static volatile Consumer<TvLibraryPayload> library = payload -> {
    };

    /** Chamado no cliente quando os números da prévia do editor chegam. */
    public static volatile Consumer<TvPreviewValuesPayload> preview = payload -> {
    };

    /** Chamado no cliente quando os itens de um Baú ligado chegam, com o editor aberto. */
    public static volatile Consumer<TvStorageContentsPayload> storageContents = payload -> {
    };

    private TvClientHooks() {
    }
}
