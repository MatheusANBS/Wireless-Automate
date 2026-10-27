package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.MAX_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.MAX_W;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.MIN_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.MIN_W;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.ROW;

import io.github.matheusanbs.wirelessautomate.client.ResizeHandle;

/**
 * O tamanho da tela de filtro: o padrão lembrado na sessão, os limites pela janela do jogo e o arrasto
 * da borda ou da alça. A altura anda de linha em linha da lista.
 */
final class FilterSize {
    // tamanho padrão, lembrado na sessão
    private static int savedW = 392;
    private static int savedH = 282;

    private FilterSize() {
    }

    static int savedWidth() {
        return savedW;
    }

    static int savedHeight() {
        return savedH;
    }

    /** O tamanho lembrado, dentro dos limites da janela do jogo. */
    static int width(int screenW) {
        return Math.max(MIN_W, Math.min(savedW, maxW(screenW)));
    }

    static int height(int screenH) {
        return Math.max(MIN_H, Math.min(savedH, maxH(screenH)));
    }

    private static int maxW(int screenW) {
        return Math.max(MIN_W, Math.min(MAX_W, screenW - 8));
    }

    private static int maxH(int screenH) {
        int room = Math.max(MIN_H, Math.min(MAX_H, screenH - 8));
        return MIN_H + (room - MIN_H) / ROW * ROW;
    }

    /**
     * O tamanho {largura, altura} que o arrasto pede: a borda arrastada segue o mouse e a oposta se move
     * igual (o painel continua centralizado). Um tamanho diferente do atual fica lembrado.
     */
    static int[] drag(ResizeHandle handle, double mouseX, double mouseY, int w, int h, int screenW, int screenH) {
        int newW = w;
        int newH = h;
        if (handle.changesWidth()) {
            double wanted = handle.wantedWidth(mouseX);
            newW = (int) Math.max(MIN_W, Math.min(maxW(screenW), Math.round(wanted / 2) * 2));
        }
        if (handle.changesHeight()) {
            double wanted = handle.wantedHeight(mouseY);
            int steps = (int) Math.round((wanted - MIN_H) / ROW);
            newH = Math.max(MIN_H, Math.min(maxH(screenH), MIN_H + steps * ROW));
        }
        if (newW != w || newH != h) {
            savedW = newW;
            savedH = newH;
        }
        return new int[] {newW, newH};
    }
}
