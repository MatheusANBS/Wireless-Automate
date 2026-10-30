package io.github.matheusanbs.wirelessautomate.client.guide.layout;

import io.github.matheusanbs.wirelessautomate.client.guide.doc.Block;
import java.util.List;

/** Os blocos que nunca se partem entre folhas (ícone, receita, cena, imagem, linha de ícones): tamanho e operação. */
final class Atomic {
    record Size(int w, int h) {}

    private final int width;
    private final int height;
    private final TextMeasure measure;

    Atomic(int width, int height, TextMeasure measure) {
        this.width = width;
        this.height = height;
        this.measure = measure;
    }

    /** O tamanho do bloco, ou nulo se não for um desses. */
    Size size(Block b) {
        return switch (b) {
            case Block.ItemImage i -> square(i.scale());
            case Block.BlockImage i -> square(i.scale());
            case Block.Image i -> fit(measure.imageSize(i.path()));
            case Block.RecipeFor r -> new Size(Math.min(width, GuideMetrics.RECIPE_W), GuideMetrics.RECIPE_H);
            case Block.Recipe r -> new Size(Math.min(width, GuideMetrics.RECIPE_W), GuideMetrics.RECIPE_H);
            case Block.Scene s -> new Size(width, GuideMetrics.SCENE_H);
            case Block.SubPages s -> new Size(width, GuideMetrics.SUBPAGES_H);
            default -> null;
        };
    }

    private Size square(double scale) {
        int side = (int) Math.round(GuideMetrics.ICON * scale);
        return new Size(side, side);
    }

    /** Encolhe, sem deformar, o que passa da folha. */
    private Size fit(TextMeasure.Size s) {
        double f = Math.min(1.0, Math.min((double) width / s.w(), (double) height / s.h()));
        return new Size(Math.max(1, (int) (s.w() * f)), Math.max(1, (int) (s.h() * f)));
    }

    /** Receitas e imagens ficam centradas; o resto, à esquerda. */
    int x(Block b, Size s) {
        boolean centered = b instanceof Block.Image || b instanceof Block.RecipeFor || b instanceof Block.Recipe;
        return centered ? (width - s.w()) / 2 : 0;
    }

    SheetOp op(Block b, int x, int y, Size s) {
        return switch (b) {
            case Block.ItemImage i -> new SheetOp.Icon(x, y, s.w(), s.h(), i.id(), i.scale(), i.components());
            case Block.BlockImage i -> new SheetOp.BlockIcon(x, y, s.w(), s.h(), i.id(), i.props());
            case Block.Image i -> new SheetOp.Image(x, y, s.w(), s.h(), i.path());
            case Block.RecipeFor r -> new SheetOp.RecipeFrame(x, y, s.w(), s.h(), r.itemId(), null);
            case Block.Recipe r -> new SheetOp.RecipeFrame(x, y, s.w(), s.h(), null, r.recipeId());
            case Block.Scene sc -> new SheetOp.SceneFrame(x, y, s.w(), s.h(), sc);
            case Block.SubPages sp -> new SheetOp.SubPagesMarker(x, y, s.w(), s.h(), sp.icons());
            default -> throw new IllegalArgumentException("Bloco não atômico: " + b);
        };
    }

    /**
     * Dispõe os filhos da linha de ícones lado a lado, centrados, passando para outra linha se não couberem na
     * largura; devolve a altura. Com {@code out} nulo só mede.
     */
    int row(Block.Row row, int oy, List<SheetOp> out) {
        List<Block> kids = row.children();
        int total = 0;
        int i = 0;
        while (i < kids.size()) {
            int j = i;
            int lineW = 0;
            int lineH = 0;
            while (j < kids.size()) {
                Size s = size(kids.get(j));
                if (s == null) {
                    j++;
                    continue;
                }
                int add = (lineW > 0 ? row.gap() : 0) + s.w();
                if (lineW > 0 && lineW + add > width) {
                    break;
                }
                lineW += add;
                lineH = Math.max(lineH, s.h());
                j++;
            }
            if (lineH > 0) {
                if (out != null) {
                    placeLine(row, kids.subList(i, j), (width - lineW) / 2, oy + total, lineH, out);
                }
                total += lineH + row.gap();
            }
            i = j;
        }
        return Math.max(0, total - row.gap());
    }

    private void placeLine(Block.Row row, List<Block> line, int x0, int y0, int lineH, List<SheetOp> out) {
        int x = x0;
        for (Block kid : line) {
            Size s = size(kid);
            if (s != null) {
                out.add(op(kid, x, y0 + (lineH - s.h()) / 2, s));
                x += s.w() + row.gap();
            }
        }
    }
}
