package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
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

    /** O tamanho de uma imagem enviada, com "GIF" na animada (a lista, que tem pouco espaço). */
    static Component pictureSize(TvPicture picture) {
        return picture.animated() ? key("pictures.size.gif", picture.width(), picture.height())
                : key("pictures.size", picture.width(), picture.height());
    }

    /** O mesmo com o número de quadros do GIF (as dicas). */
    static Component pictureSizeLong(TvPicture picture) {
        return picture.animated() ? key("pictures.size.gif.long", picture.width(), picture.height(), picture.frames())
                : key("pictures.size", picture.width(), picture.height());
    }

    /** O nome que o jogador deu à imagem na biblioteca, ou o padrão se ela não está lá. */
    static String pictureName(TvPicture picture) {
        for (TvLibraryPicture entry : TvLibraryClient.pictures()) {
            if (entry.picture().hash().equals(picture.hash())) {
                return entry.name();
            }
        }
        return key("pictures.unnamed").getString();
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
        if (piece.type() == TvPieceType.ICON && piece.style().part().item().isPresent()) {
            return Component.literal(piece.style().part().item().get().getPath());
        }
        return typeName(piece.type());
    }
}
