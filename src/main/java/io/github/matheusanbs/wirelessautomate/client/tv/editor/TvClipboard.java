package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * A área de transferência do editor: as peças copiadas ou recortadas (posições relativas ao canto da escolha, com os
 * grupos) e as cópias das pixel arts que as Imagens delas mostram, guardadas na memória do cliente e válidas para
 * qualquer Tela até sair do jogo: colar noutra Tela leva as artes junto ({@link TvImageOps#withMissingArts}). Não usa
 * a área de transferência do sistema. Referências a cópias imutáveis: copiar é um evento, nada por quadro.
 */
final class TvClipboard {
    private static @Nullable TvEditOps.Clip<TvPiece> clip;
    private static List<TvPixelArt> arts = List.of();

    private TvClipboard() {
    }

    static void put(TvEditOps.Clip<TvPiece> copied, List<TvPixelArt> copiedArts) {
        clip = copied;
        arts = List.copyOf(copiedArts);
    }

    static @Nullable TvEditOps.Clip<TvPiece> get() {
        return clip;
    }

    /** As artes das Imagens copiadas, como estavam na Tela de origem. */
    static List<TvPixelArt> arts() {
        return arts;
    }

    static boolean has() {
        return clip != null;
    }

    /** Esvazia a área (os testes do e2e). */
    static void clear() {
        clip = null;
        arts = List.of();
    }
}
