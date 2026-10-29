package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvCodes;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Exportar e importar uma pixel art por código de texto ({@link TvCodes}), na aba Pixel art: Exportar põe a arte
 * escolhida na área de transferência, comprimida, e Copiar como texto, na forma legível (as linhas de pixels);
 * Importar lê a área de transferência (as duas formas) e cria uma arte nova na biblioteca (id novo, o nome do código
 * cortado em {@link TvPixelArt#MAX_NAME}) pelo {@link TvEditorActions}, dentro do {@code tv.maxArts}. Só no clique;
 * os avisos vão pelo {@link TvCodeNotice}.
 */
final class TvArtCode {
    private TvArtCode() {
    }

    static void copy(TvPixelArt art, boolean text) {
        String code = text ? TvCodes.artText(art, TvLayoutCode.english()) : TvCodes.art(art);
        if (code == null) {
            TvCodeNotice.show(TvCodeNotice.Kind.TOO_BIG);
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        TvCodeNotice.show(TvCodeNotice.Kind.COPIED);
    }

    /**
     * Cria a arte do código na biblioteca, que tem {@code count} artes agora; um código de imagem ({@code WA-IMAGEM1})
     * vira um envio de imagem ({@link TvPictureSend}).
     */
    static void paste(TvEditorActions actions, int count) {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (TvPictureSend.pasteIfImage(actions, text)) {
            return;
        }
        TvCodes.Read<TvPixelArt> result = TvCodes.readArt(text);
        TvPixelArt read = result.value();
        if (read == null) {
            TvCodeNotice.failed(result);
        } else if (count >= actions.maxArts()) {
            TvCodeNotice.show(TvCodeNotice.Kind.LIBRARY_FULL);
        } else {
            // A biblioteca não guarda arte sem nome: a que veio sem nome ganha o padrão do editor.
            String name = read.name().isBlank() ? TvEditorText.key("pixel.new_name").getString() : read.name();
            actions.saveArt(new TvPixelArt(UUID.randomUUID(), name, read.width(), read.height(),
                    read.palette(), read.pixels()));
        }
    }
}
