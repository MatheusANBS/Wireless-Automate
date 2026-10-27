package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;

/**
 * O que as abas de propriedades precisam para montar os controles: o documento do editor, as redes que o
 * jogador pode usar e a peça escolhida (a primeira, quando só uma vale). Toda mudança passa por {@link #change}, que
 * troca a peça escolhida pelo {@link TvEditorDoc} (entra no desfazer; a mesma {@code key} em mudanças seguidas de um
 * campo vira uma só).
 */
record TvPropsCtx(TvEditorDoc doc, List<TvMenu.NetworkChoice> networks, TvEditorActions actions, TvPopups popups) {
    /** A primeira peça escolhida. */
    TvPiece piece() {
        return doc.pieces().get(doc.selection().first());
    }

    TvTheme theme() {
        return doc.draft().theme();
    }

    void change(String key, UnaryOperator<TvPiece> change) {
        doc.changeSelected(change, key);
    }

    static Component key(String key, Object... args) {
        return TvEditorText.key(key, args);
    }
}
