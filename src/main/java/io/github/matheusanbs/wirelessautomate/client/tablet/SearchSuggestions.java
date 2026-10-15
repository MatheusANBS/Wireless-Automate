package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletSearch.Candidate;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletSearch.Group;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletSearch.Suggestion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A lista de sugestões aberta embaixo da busca da Lista do Tablet: os grupos Máquinas, Redes e Nomes do
 * {@link TabletSearch}, com o trecho digitado em coral, quantos nós cada uma pega, a sugestão realçada pelas
 * setas e o rodapé "Enter: procurar … · ↑↓ escolher". Escolher uma passa o valor ao {@code Picker}; o que isso
 * faz (etiqueta, texto da busca) fica com a {@link TabletList}.
 */
final class SearchSuggestions {
    /** O que fazer com a sugestão escolhida, por grupo. */
    interface Picker {
        void machine(ResourceLocation id);

        void network(UUID id);

        void name(String name);
    }

    private static final int ROW = 12;
    private static final int TITLE = 10;
    private static final int FOOT = 11;

    /** Uma linha da lista: o título de um grupo ou uma sugestão (índice em {@link #list}). */
    private record Line(@Nullable Group title, int index) {
    }

    private List<Suggestion<?>> list = List.of();
    private String text = "";
    private boolean open;
    private int highlight = -1;
    private int x;
    private int y;
    private int w;

    /** As sugestões do texto; o realce volta para nenhum se a sugestão dele sumiu. */
    void update(String text, List<Candidate<ResourceLocation>> machines, List<Candidate<UUID>> networks,
            List<Candidate<String>> names) {
        this.text = text;
        list = TabletSearch.suggest(text, machines, networks, names);
        if (highlight >= list.size()) {
            highlight = -1;
        }
    }

    /** Abre a lista (se há o que sugerir), sem nada realçado: o jogador digitou. */
    void reopen() {
        open = !list.isEmpty();
        highlight = -1;
    }

    void close() {
        open = false;
        highlight = -1;
    }

    /** A lista está à vista. */
    boolean shown() {
        return open && !list.isEmpty();
    }

    /** O canto e a largura da lista (a altura vem das linhas). */
    void place(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
    }

    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        Group last = null;
        for (int i = 0; i < list.size(); i++) {
            Group group = list.get(i).group();
            if (group != last) {
                lines.add(new Line(group, -1));
                last = group;
            }
            lines.add(new Line(null, i));
        }
        return lines;
    }

    private int height() {
        int h = 0;
        for (Line line : lines()) {
            h += line.title() != null ? TITLE : ROW;
        }
        return h + FOOT + 2;
    }

    boolean contains(double mx, double my) {
        return shown() && mx >= x && mx < x + w && my >= y && my < y + height();
    }

    /** O topo da linha da sugestão {@code index}, ou -1. */
    private int rowY(int index) {
        int sy = y + 1;
        for (Line line : lines()) {
            if (line.title() != null) {
                sy += TITLE;
            } else {
                if (line.index() == index) {
                    return sy;
                }
                sy += ROW;
            }
        }
        return -1;
    }

    private int indexAt(double mx, double my) {
        if (!contains(mx, my)) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            int top = rowY(i);
            if (my >= top && my < top + ROW) {
                return i;
            }
        }
        return -1;
    }

    /** As setas: a sugestão de baixo ou de cima (nenhuma acima da primeira). */
    void move(int direction) {
        highlight = Math.max(-1, Math.min(list.size() - 1, highlight + direction));
    }

    boolean hasHighlight() {
        return shown() && highlight >= 0;
    }

    void pickHighlighted(Picker picker) {
        if (hasHighlight()) {
            pick(highlight, picker);
        }
    }

    /** Um clique dentro da lista: escolhe a sugestão sob o mouse (o resto da lista só engole o clique). */
    void click(double mx, double my, Picker picker) {
        int index = indexAt(mx, my);
        if (index >= 0) {
            pick(index, picker);
        }
    }

    private void pick(int index, Picker picker) {
        Suggestion<?> suggestion = list.get(index);
        close();
        switch (suggestion.group()) {
            case MACHINES -> picker.machine((ResourceLocation) suggestion.id());
            case NETWORKS -> picker.network((UUID) suggestion.id());
            case NAMES -> picker.name(suggestion.label());
        }
    }

    /** Centro da sugestão do valor {@code id}, ou {@code null} (lista fechada ou sem ele). */
    int @Nullable [] center(Object id) {
        if (!shown()) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return new int[] {x + w / 2, rowY(i) + ROW / 2};
            }
        }
        return null;
    }

    /** A lista por cima de tudo; {@code right} dá o texto da direita de cada sugestão. */
    void render(GuiGraphics g, Font font, int mouseX, int mouseY, Function<Suggestion<?>, Component> right) {
        if (!shown()) {
            return;
        }
        int hovered = indexAt(mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        GuiPaint.box(g, x, y, w, height(), GuiPaint.PANEL, GuiPaint.BUTTON_BORDER);
        int sy = y + 1;
        for (Line line : lines()) {
            if (line.title() != null) {
                GuiText.draw(g, font, TabletText.tr("list.suggest." + line.title().name().toLowerCase(Locale.ROOT)),
                        x + 5, sy + 1, w - 10, GuiPaint.MUTED);
                sy += TITLE;
                continue;
            }
            Suggestion<?> s = list.get(line.index());
            if (line.index() == highlight || line.index() == hovered) {
                g.fill(x + 1, sy, x + w - 1, sy + ROW, GuiPaint.ROW_SELECTED);
                g.fill(x + 1, sy, x + 3, sy + ROW, GuiPaint.ACCENT);
            }
            Component side = right.apply(s);
            int sideW = Math.min(font.width(side), w / 3);
            GuiText.draw(g, font, side, x + w - 5 - sideW, sy + 2, sideW, GuiPaint.MUTED);
            GuiText.draw(g, font, highlighted(s), x + 8, sy + 2, w - 18 - sideW, GuiPaint.FG);
            sy += ROW;
        }
        g.fill(x + 3, sy, x + w - 3, sy + 1, GuiPaint.LINE);
        GuiText.draw(g, font, TabletText.tr("list.suggest.footer", text.strip()), x + 5, sy + 2, w - 10,
                GuiPaint.DISABLED);
        g.pose().popPose();
    }

    /** O nome com o trecho casado em coral e sublinhado. */
    private static Component highlighted(Suggestion<?> s) {
        String label = s.label();
        return Component.literal(label.substring(0, s.start()))
                .append(Component.literal(label.substring(s.start(), s.end()))
                        .withStyle(Style.EMPTY.withColor(GuiPaint.ACCENT).withUnderlined(true)))
                .append(Component.literal(label.substring(s.end())));
    }
}
