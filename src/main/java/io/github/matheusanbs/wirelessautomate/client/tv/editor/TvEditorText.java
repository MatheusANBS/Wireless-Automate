package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import net.minecraft.network.chat.Component;

/** Os textos do editor: as chaves do {@code lang} e os nomes das peças nas Camadas e na escolha. */
final class TvEditorText {
    private static final String PREFIX = "gui.wirelessautomate.tv.editor.";

    private TvEditorText() {
    }

    static Component key(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    /**
     * O nome de um tema ou componente pronto: a chave {@code tv.*} guardada, traduzida com o prefixo do lang. Os do
     * jogador são texto dele e nunca passam por aqui.
     */
    static Component readyName(String stored) {
        return Component.translatable("gui.wirelessautomate." + stored);
    }

    static Component typeName(TvPieceType type) {
        return key("piece." + type.getSerializedName());
    }

    /** O nome da peça nas Camadas: o nome dado, o texto do Texto, o tipo de recurso do Valor ligado, ou o tipo. */
    static Component label(TvPiece piece) {
        if (!piece.name().isEmpty()) {
            return Component.literal(piece.name());
        }
        if (piece.type() == TvPieceType.TEXT && !piece.style().text().isEmpty()) {
            return Component.literal(piece.style().text());
        }
        if (piece.type() == TvPieceType.VALUE && piece.binding().isPresent()) {
            return key("piece.value.bound", ResourceStyle.name(piece.binding().get().type()));
        }
        return typeName(piece.type());
    }
}
