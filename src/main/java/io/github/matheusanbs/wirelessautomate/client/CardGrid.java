package io.github.matheusanbs.wirelessautomate.client;

/** Colunas de uma grade de cartões (lógica pura): quantas cabem, sem passar do número de cartões. */
public final class CardGrid {
    private CardGrid() {
    }

    public static int columns(int count, int available, int minWidth, int gap) {
        int fit = (available + gap) / (minWidth + gap);
        return Math.max(1, Math.min(count, fit));
    }
}
