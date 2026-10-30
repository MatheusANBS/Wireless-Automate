package io.github.matheusanbs.wirelessautomate.client.guide.layout;

import io.github.matheusanbs.wirelessautomate.client.guide.doc.Block;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.Flow;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideDoc;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.Span;
import java.util.List;

/**
 * A paginação: distribui os blocos de uma página pelas folhas, todas do mesmo tamanho. Parágrafo, lista e tabela
 * partem entre linhas; receita, imagem, cena e linha de ícones vão inteiras para a folha seguinte; um título nunca
 * fica sozinho no pé da folha. Sem Minecraft: a medida do texto entra por {@link TextMeasure}. Roda uma vez por
 * página e idioma, e a tela guarda o resultado.
 */
public final class GuideLayout {
    private final int width;
    private final TextMeasure measure;
    private final SheetFlow flow;
    private final Atomic atomic;
    private final TableLayout tables;

    private GuideLayout(int width, int height, TextMeasure measure) {
        this.width = width;
        this.measure = measure;
        this.flow = new SheetFlow(height);
        this.atomic = new Atomic(width, height, measure);
        this.tables = new TableLayout(width, measure);
    }

    public static List<Sheet> paginate(GuideDoc doc, int width, int height, TextMeasure measure) {
        GuideLayout layout = new GuideLayout(width, height, measure);
        List<Block> blocks = doc.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            layout.block(blocks.get(i), i + 1 < blocks.size() ? blocks.get(i + 1) : null);
        }
        return layout.flow.finish();
    }

    private void block(Block b, Block next) {
        switch (b) {
            case Block.Heading h -> heading(h, next);
            case Block.Paragraph p -> {
                lines(p.spans(), TextStyle.NORMAL, 0);
                flow.gap(GuideMetrics.BLOCK_GAP);
            }
            case Block.ListBlock l -> list(l);
            case Block.Table t -> table(t);
            case Block.ItemImage i -> icon(b, i.flow());
            case Block.BlockImage i -> icon(b, i.flow());
            case Block.Row r -> row(r);
            case Block.Break br -> flow.clearFloat();
            case Block.RecipeFor r -> place(b);
            case Block.Recipe r -> place(b);
            case Block.Image i -> place(b);
            case Block.Scene s -> place(b);
            case Block.SubPages s -> place(b);
        }
    }

    private void heading(Block.Heading h, Block next) {
        flow.clearFloat();
        TextStyle style = switch (h.level()) {
            case 1 -> TextStyle.TITLE1;
            case 2 -> TextStyle.TITLE2;
            default -> TextStyle.TITLE3;
        };
        double scale = h.level() == 1 ? GuideMetrics.H1_SCALE : 1.0;
        TextWrap wrap = new TextWrap(h.spans(), style, scale, measure);
        int ornament = h.level() == 1 ? GuideMetrics.ORNAMENT_H : 0;
        boolean first = true;
        for (Line l = wrap.next(width); l != null; l = wrap.next(width)) {
            int need = first ? l.height() + ornament + GuideMetrics.HEADING_GAP + firstChunk(next) : l.height();
            int y = flow.begin(need);
            l.emit(flow.ops(), 0, y);
            flow.advance(l.height());
            first = false;
        }
        if (ornament > 0) {
            int y = flow.begin(ornament);
            flow.ops().add(new SheetOp.Ornament(0, y, width, ornament));
            flow.advance(ornament);
        }
        flow.gap(GuideMetrics.HEADING_GAP);
    }

    /** O que o bloco seguinte a um título pede, no mínimo, para ficar na mesma folha. */
    private int firstChunk(Block next) {
        if (next == null) {
            return 0;
        }
        return switch (next) {
            case Block.Table t -> t.header().isEmpty() ? 0 : TableLayout.firstChunk(tables.plan(t));
            case Block.Row r -> atomic.row(r, 0, null);
            case Block.Heading h -> measure.lineHeight();
            case Block.Paragraph p -> measure.lineHeight();
            case Block.ListBlock l -> measure.lineHeight();
            case Block.ItemImage i -> i.flow() == Flow.NONE ? atomic.size(i).h() : measure.lineHeight();
            case Block.BlockImage i -> i.flow() == Flow.NONE ? atomic.size(i).h() : measure.lineHeight();
            case Block.Break br -> 0;
            default -> atomic.size(next).h();
        };
    }

    /** Escreve o texto linha a linha, com {@code indent} à esquerda e a largura menor ao lado de um ícone flutuante. */
    private void lines(List<Span> spans, TextStyle style, int indent) {
        TextWrap wrap = new TextWrap(spans, style, 1.0, measure);
        for (;;) {
            int left = flow.leftInset();
            int avail = width - indent - left - flow.rightInset();
            Line l = wrap.next(Math.max(1, avail));
            if (l == null) {
                return;
            }
            int y = flow.begin(l.height());
            l.emit(flow.ops(), indent + left, y);
            flow.advance(l.height());
        }
    }

    private void list(Block.ListBlock list) {
        int n = 1;
        for (List<Span> item : list.items()) {
            String marker = list.numbered() ? n + "." : "•";
            n++;
            TextWrap wrap = new TextWrap(item, TextStyle.NORMAL, 1.0, measure);
            boolean first = true;
            for (;;) {
                int left = flow.leftInset();
                int avail = width - GuideMetrics.LIST_INDENT - left - flow.rightInset();
                Line l = wrap.next(Math.max(1, avail));
                if (l == null) {
                    break;
                }
                int y = flow.begin(l.height());
                if (first) {
                    int mw = measure.width(marker, TextStyle.NORMAL);
                    flow.ops().add(new SheetOp.Text(left, y, mw, measure.lineHeight(), marker, TextStyle.NORMAL, 1.0));
                    first = false;
                }
                l.emit(flow.ops(), GuideMetrics.LIST_INDENT + left, y);
                flow.advance(l.height());
            }
            flow.gap(GuideMetrics.LIST_ITEM_GAP);
        }
        flow.gap(GuideMetrics.BLOCK_GAP);
    }

    private void table(Block.Table t) {
        if (t.header().isEmpty()) {
            return;
        }
        flow.clearFloat();
        TableLayout.Plan plan = tables.plan(t);
        int y = flow.begin(TableLayout.firstChunk(plan));
        flow.advance(tables.place(plan, plan.header(), y, flow.ops()));
        for (TableLayout.Row r : plan.body()) {
            int sheets = flow.sheetCount();
            y = flow.begin(r.height() + GuideMetrics.RULE);
            if (flow.sheetCount() != sheets) {
                flow.advance(tables.place(plan, plan.header(), y, flow.ops()));
                y = flow.begin(r.height() + GuideMetrics.RULE);
            }
            flow.advance(tables.place(plan, r, y, flow.ops()));
        }
        flow.gap(GuideMetrics.BLOCK_GAP);
    }

    private void icon(Block b, Flow mode) {
        if (mode == Flow.NONE) {
            place(b);
            return;
        }
        Atomic.Size s = atomic.size(b);
        int y = flow.begin(s.h());
        boolean left = mode == Flow.LEFT;
        int x = left ? 0 : width - s.w();
        flow.ops().add(atomic.op(b, x, y, s));
        flow.setFloat(left, s.w() + GuideMetrics.FLOAT_GAP, y + s.h());
    }

    private void place(Block b) {
        flow.clearFloat();
        Atomic.Size s = atomic.size(b);
        int y = flow.begin(s.h());
        flow.ops().add(atomic.op(b, atomic.x(b, s), y, s));
        flow.advance(s.h());
        flow.gap(GuideMetrics.BLOCK_GAP);
    }

    private void row(Block.Row r) {
        flow.clearFloat();
        int h = atomic.row(r, 0, null);
        if (h == 0) {
            return;
        }
        int y = flow.begin(h);
        atomic.row(r, y, flow.ops());
        flow.advance(h);
        flow.gap(GuideMetrics.BLOCK_GAP);
    }
}
