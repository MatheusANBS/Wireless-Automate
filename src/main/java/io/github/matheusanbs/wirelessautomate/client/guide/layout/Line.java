package io.github.matheusanbs.wirelessautomate.client.guide.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Uma linha já quebrada: os trechos de texto e os ícones com a posição na linha, antes de ir para uma folha. */
final class Line {
    enum Kind {
        TEXT, ICON, IMAGE
    }

    private static final class Item {
        final Kind kind;
        final int x;
        int w;
        final int h;
        final StringBuilder text;
        final TextStyle style;
        final String page;
        final String id;
        final double scale;
        final String components;

        Item(TextWrap.Atom a, int x) {
            this.kind = a.kind();
            this.x = x;
            this.w = a.w();
            this.h = a.h();
            this.text = a.text() == null ? null : new StringBuilder(a.text());
            this.style = a.style();
            this.page = a.page();
            this.id = a.id();
            this.scale = a.scale();
            this.components = a.components();
        }
    }

    private final double textScale;
    private final List<Item> items = new ArrayList<>(4);
    private int height;

    Line(double textScale) {
        this.textScale = textScale;
    }

    int height() {
        return height;
    }

    int width() {
        if (items.isEmpty()) {
            return 0;
        }
        Item last = items.get(items.size() - 1);
        return last.x + last.w;
    }

    /** Põe o átomo depois de {@code gap} unidades de espaço a partir de {@code x}; devolve o fim da linha. */
    int add(TextWrap.Atom a, int x, int gap) {
        height = Math.max(height, a.h());
        Item last = items.isEmpty() ? null : items.get(items.size() - 1);
        if (a.kind() == Kind.TEXT && last != null && last.kind == Kind.TEXT && last.style == a.style()
                && Objects.equals(last.page, a.page())) {
            if (gap > 0) {
                last.text.append(' ');
            }
            last.text.append(a.text());
            last.w += gap + a.w();
            return last.x + last.w;
        }
        Item it = new Item(a, x + gap);
        items.add(it);
        return it.x + it.w;
    }

    /** Escreve as operações da linha com o canto em ({@code ox}, {@code oy}); cada item centrado na altura da linha. */
    void emit(List<SheetOp> out, int ox, int oy) {
        for (Item it : items) {
            int x = ox + it.x;
            int y = oy + (height - it.h) / 2;
            switch (it.kind) {
                case TEXT -> {
                    out.add(new SheetOp.Text(x, y, it.w, it.h, it.text.toString(), it.style, textScale));
                    if (it.page != null) {
                        out.add(new SheetOp.LinkArea(x, y, it.w, it.h, it.page));
                    }
                }
                case ICON -> out.add(new SheetOp.Icon(x, y, it.w, it.h, it.id, it.scale, it.components));
                case IMAGE -> out.add(new SheetOp.Image(x, y, it.w, it.h, it.text.toString()));
            }
        }
    }
}
