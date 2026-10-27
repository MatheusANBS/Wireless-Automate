package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import org.jetbrains.annotations.Nullable;

/**
 * A área de transferência do editor: as peças copiadas ou recortadas (posições relativas ao canto da escolha, com os
 * grupos), guardadas na memória do cliente e válidas para qualquer Tela até sair do jogo. Não usa a área de
 * transferência do sistema. Uma referência a uma cópia imutável: copiar é um evento, nada por quadro.
 */
final class TvClipboard {
    private static @Nullable TvEditOps.Clip<TvPiece> clip;

    private TvClipboard() {
    }

    static void put(TvEditOps.Clip<TvPiece> copied) {
        clip = copied;
    }

    static @Nullable TvEditOps.Clip<TvPiece> get() {
        return clip;
    }

    static boolean has() {
        return clip != null;
    }

    /** Esvazia a área (os testes do e2e). */
    static void clear() {
        clip = null;
    }
}
