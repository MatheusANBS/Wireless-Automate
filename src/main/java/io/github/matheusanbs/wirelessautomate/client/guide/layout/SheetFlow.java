package io.github.matheusanbs.wirelessautomate.client.guide.layout;

import java.util.ArrayList;
import java.util.List;

/** O cursor que corre pelas folhas: a posição vertical, o espaço entre blocos, a troca de folha e o ícone flutuante. */
final class SheetFlow {
    private final int height;
    private final List<Sheet> sheets = new ArrayList<>();
    private List<SheetOp> ops = new ArrayList<>();
    private int y;
    private int pendingGap;
    private int floatBottom;
    private int floatLeftInset;
    private int floatRightInset;

    SheetFlow(int height) {
        this.height = height;
    }

    /** As operações da folha atual, para quem escreve nelas. */
    List<SheetOp> ops() {
        return ops;
    }

    int sheetCount() {
        return sheets.size();
    }

    /**
     * Reserva {@code h} unidades: aplica o espaço pendente e, se não couber, passa para a folha seguinte. Devolve o
     * {@code y} da reserva sem avançar o cursor (quem usa chama {@link #advance}).
     */
    int begin(int h) {
        int gap = y > 0 ? pendingGap : 0;
        pendingGap = 0;
        if (y > 0 && y + gap + h > height) {
            newSheet();
            gap = 0;
        }
        y += gap;
        return y;
    }

    void advance(int dy) {
        y += dy;
    }

    /** O espaço que a próxima reserva deixa antes de si (some no topo da folha). */
    void gap(int g) {
        pendingGap = g;
    }

    /** O ícone flutuante ocupa a largura {@code inset} de um dos lados até {@code bottom}. */
    void setFloat(boolean left, int inset, int bottom) {
        floatLeftInset = left ? inset : 0;
        floatRightInset = left ? 0 : inset;
        floatBottom = bottom;
    }

    int leftInset() {
        return floatActive() ? floatLeftInset : 0;
    }

    int rightInset() {
        return floatActive() ? floatRightInset : 0;
    }

    private boolean floatActive() {
        return (y > 0 ? y + pendingGap : 0) < floatBottom;
    }

    /** O {@code <br clear="all">}: desce até passar do ícone flutuante. */
    void clearFloat() {
        y = Math.max(y, floatBottom);
        floatBottom = 0;
    }

    private void newSheet() {
        if (!ops.isEmpty()) {
            sheets.add(new Sheet(ops, y));
        }
        ops = new ArrayList<>();
        y = 0;
        floatBottom = 0;
    }

    /** Fecha a última folha e devolve todas (ao menos uma, mesmo vazia). */
    List<Sheet> finish() {
        if (!ops.isEmpty() || sheets.isEmpty()) {
            sheets.add(new Sheet(ops, y));
        }
        return sheets;
    }
}
