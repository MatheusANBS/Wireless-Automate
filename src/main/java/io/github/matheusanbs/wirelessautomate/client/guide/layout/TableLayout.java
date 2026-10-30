package io.github.matheusanbs.wirelessautomate.client.guide.layout;

import io.github.matheusanbs.wirelessautomate.client.guide.doc.Block;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.Span;
import java.util.ArrayList;
import java.util.List;

/** Calcula as colunas e as linhas de uma tabela e escreve cada linha numa folha; a divisão entre folhas é do chamador. */
final class TableLayout {
    /** Uma linha pronta: a altura (sem a regra) e as linhas de texto de cada célula. */
    record Row(int height, List<List<Line>> cells) {}

    /** A tabela medida: a posição e a largura das colunas, o cabeçalho e as linhas do corpo. */
    record Plan(int[] colX, int[] colW, Row header, List<Row> body) {
        int totalWidth() {
            return colX.length == 0 ? 0 : colX[colX.length - 1] + colW[colW.length - 1];
        }
    }

    private final int width;
    private final TextMeasure measure;

    TableLayout(int width, TextMeasure measure) {
        this.width = width;
        this.measure = measure;
    }

    Plan plan(Block.Table t) {
        int cols = t.header().size();
        int[] colW = columnWidths(t, cols);
        int[] colX = new int[cols];
        int x = 0;
        for (int c = 0; c < cols; c++) {
            colX[c] = x;
            x += colW[c];
        }
        Row header = row(t.header(), colW, TextStyle.BOLD);
        List<Row> body = new ArrayList<>(t.rows().size());
        for (List<List<Span>> r : t.rows()) {
            body.add(row(r, colW, TextStyle.NORMAL));
        }
        return new Plan(colX, colW, header, body);
    }

    /** Largura pelo conteúdo; se a soma passar da folha, encolhe na proporção, sem passar do mínimo. */
    private int[] columnWidths(Block.Table t, int cols) {
        int[] nat = new int[cols];
        int total = 0;
        for (int c = 0; c < cols; c++) {
            int w = naturalWidth(t.header().get(c), TextStyle.BOLD);
            for (List<List<Span>> r : t.rows()) {
                if (c < r.size()) {
                    w = Math.max(w, naturalWidth(r.get(c), TextStyle.NORMAL));
                }
            }
            nat[c] = w + 2 * GuideMetrics.CELL_PAD_X;
            total += nat[c];
        }
        if (total <= width) {
            return nat;
        }
        int[] colW = new int[cols];
        int sum = 0;
        for (int c = 0; c < cols; c++) {
            colW[c] = Math.max(GuideMetrics.MIN_COL, (int) ((long) nat[c] * width / total));
            sum += colW[c];
        }
        while (sum > width) {
            int widest = 0;
            for (int c = 1; c < cols; c++) {
                if (colW[c] > colW[widest]) {
                    widest = c;
                }
            }
            if (colW[widest] <= GuideMetrics.MIN_COL) {
                break;
            }
            colW[widest]--;
            sum--;
        }
        return colW;
    }

    private int naturalWidth(List<Span> spans, TextStyle style) {
        Line l = new TextWrap(spans, style, 1.0, measure).next(Integer.MAX_VALUE / 2);
        return l == null ? 0 : l.width();
    }

    private Row row(List<List<Span>> cells, int[] colW, TextStyle style) {
        List<List<Line>> out = new ArrayList<>(colW.length);
        int content = measure.lineHeight();
        for (int c = 0; c < colW.length; c++) {
            List<Line> lines = new ArrayList<>(2);
            int h = 0;
            if (c < cells.size()) {
                TextWrap wrap = new TextWrap(cells.get(c), style, 1.0, measure);
                int avail = Math.max(1, colW[c] - 2 * GuideMetrics.CELL_PAD_X);
                for (Line l = wrap.next(avail); l != null; l = wrap.next(avail)) {
                    lines.add(l);
                    h += l.height();
                }
            }
            content = Math.max(content, h);
            out.add(lines);
        }
        return new Row(content + 2 * GuideMetrics.CELL_PAD_Y, out);
    }

    /** Escreve a linha com o topo em {@code y} e a regra embaixo; devolve o espaço que ela ocupa (altura + regra). */
    int place(Plan plan, Row row, int y, List<SheetOp> out) {
        for (int c = 0; c < plan.colX().length; c++) {
            int ly = y + GuideMetrics.CELL_PAD_Y;
            for (Line l : row.cells().get(c)) {
                l.emit(out, plan.colX()[c] + GuideMetrics.CELL_PAD_X, ly);
                ly += l.height();
            }
        }
        out.add(new SheetOp.Rule(0, y + row.height(), plan.totalWidth(), GuideMetrics.RULE));
        return row.height() + GuideMetrics.RULE;
    }

    /** A altura que o cabeçalho e a primeira linha do corpo pedem juntos. */
    static int firstChunk(Plan plan) {
        int h = plan.header().height() + GuideMetrics.RULE;
        if (!plan.body().isEmpty()) {
            h += plan.body().get(0).height() + GuideMetrics.RULE;
        }
        return h;
    }
}
