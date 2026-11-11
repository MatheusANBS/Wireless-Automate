package io.github.matheusanbs.wirelessautomate.client.dock;

import io.github.matheusanbs.wirelessautomate.dock.DockStatus;

/**
 * As medidas da tela da Doca de Padrões, em pixels de interface a partir do canto da tela (as do mockup aprovado,
 * {@code docs/preview/doca-de-padroes-tela.html}): o cabeçalho, o resumo e as seis linhas, cada uma com a etiqueta do
 * lado, o ícone da máquina e duas linhas de texto (o nome com a contagem; o estado com as notas). O ícone ocupa as
 * duas linhas, e a segunda começa na coluna do nome. Lógica pura, com JUnit.
 */
final class DockLayout {
    static final int W = 336;
    static final int X0 = 8;
    static final int ROWS = 6;

    /** O Olho (5 × 5) e o título, na altura do texto do cabeçalho. */
    static final int HEAD_TEXT_Y = 11;
    static final int EYE_X = X0;
    static final int TITLE_X = EYE_X + 9;
    static final int PILL_Y = 9;
    static final int PILL_H = 11;
    static final int THEME_X = W - X0 - 11;
    static final int THEME_Y = 9;

    /** O resumo: "30/32 máquinas", a barra fina e "raio 16" à direita. */
    static final int SUMMARY_Y = 27;
    static final int BAR_H = 4;
    static final int BAR_DY = 2;
    static final int SUMMARY_GAP = 6;

    /** A área afundada das linhas. */
    static final int ROWS_X = X0;
    static final int ROWS_Y = 41;
    static final int ROWS_W = W - 2 * X0;
    static final int ROW_H = 27;
    static final int ROWS_H = ROWS * ROW_H;
    static final int H = ROWS_Y + ROWS_H + X0;

    /** As colunas de uma linha (x a partir do canto da tela; y a partir do topo da linha). */
    static final int TAG_X = ROWS_X + 3;
    static final int TAG_W = 42;
    static final int TAG_DY = 3;
    static final int TAG_H = ROW_H - 2 * TAG_DY;
    static final int ICON_X = TAG_X + TAG_W + 5;
    static final int ICON_DY = (ROW_H - 16) / 2;
    static final int NAME_X = ICON_X + 16 + 5;
    static final int RIGHT = ROWS_X + ROWS_W - 5;
    static final int TEXT_W = RIGHT - NAME_X;
    static final int LINE1_DY = 5;
    static final int LINE2_DY = 16;
    /** Entre o nome e a contagem, e entre o estado e cada nota. */
    static final int GAP = 8;
    /** O sinal de forma (7 × 7) e o texto depois dele. */
    static final int SIGN = 7;
    static final int SIGN_TEXT = SIGN + 3;
    /** A largura da quebra da dica da linha. */
    static final int TIP_W = 220;

    /** Os sinais de forma do estado: ponto cheio, anel, triângulo e o ponto pequeno cinza. */
    static final int SIGNAL_OK = 0;
    static final int SIGNAL_WARN = 1;
    static final int SIGNAL_ERROR = 2;
    static final int SIGNAL_IDLE = 3;

    private DockLayout() {
    }

    /** O topo da linha {@code index} (0 a 5, na ordem da tela). */
    static int rowTop(int index) {
        return ROWS_Y + index * ROW_H;
    }

    /** A linha sob o ponto ({@code x}, {@code y}) relativo ao canto da tela, ou −1. */
    static int rowAt(double x, double y) {
        if (x < ROWS_X || x >= ROWS_X + ROWS_W || y < ROWS_Y || y >= ROWS_Y + ROWS_H) {
            return -1;
        }
        return (int) ((y - ROWS_Y) / ROW_H);
    }

    /** O espaço do nome da máquina, com a contagem de largura {@code countWidth} à direita. */
    static int nameWidth(int countWidth) {
        return Math.max(0, countWidth > 0 ? TEXT_W - countWidth - GAP : TEXT_W);
    }

    /** O sinal do estado: erro nos que pedem o jogador, atenção nos que esperam, ok trabalhando, cinza no resto. */
    static int signal(DockStatus.State state) {
        return switch (state) {
            case NO_PROVIDER, NO_MACHINES, CRAFTING_PATTERN, MIXED -> SIGNAL_ERROR;
            case PROVIDER_FULL, ALL_FULL -> SIGNAL_WARN;
            case WORKING -> SIGNAL_OK;
            case READY, FREE -> SIGNAL_IDLE;
        };
    }

    /** A largura da barra do resumo entre o texto da esquerda e o raio, nunca negativa. */
    static int barWidth(int leftWidth, int radiusWidth) {
        return Math.max(0, ROWS_W - leftWidth - radiusWidth - 2 * SUMMARY_GAP);
    }

    /** Quantos pixels da barra {@code width} ficam cheios com {@code total} de {@code max}. */
    static int barFill(int width, int total, int max) {
        if (max <= 0) {
            return total > 0 ? width : 0;
        }
        return (int) Math.min(width, (long) width * total / max);
    }
}
