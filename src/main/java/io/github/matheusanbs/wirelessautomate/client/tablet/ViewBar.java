package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.GAP;
import static io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.QUICK_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickFilter;
import io.github.matheusanbs.wirelessautomate.packet.TabletMachinesPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A linha embaixo da busca na Lista do Tablet: o seletor "Roteadores · Máquinas · Grade" ({@link Kind#VIEW}, argumento
 * 0, 1 ou 2) e, à direita dele, o filtro de tipo e os filtros rápidos, que só aparecem quando pedem atenção e passam para a
 * linha de baixo quando não cabem ao lado. A geometria entra nas áreas do {@link ListLayout}; o desenho dos filtros
 * é do {@link ListPainter}, o do seletor é daqui.
 */
final class ViewBar {
    private final TabletList list;
    private final ListLayout layout;
    /** A visão pedida ao servidor ({@code true}: por máquinas) até a página dela chegar; {@code null}: nenhuma. */
    private @Nullable Boolean pending;

    ViewBar(TabletList list, ListLayout layout) {
        this.list = list;
        this.layout = layout;
    }

    /** A visão que o seletor mostra: a pedida, até a página dela chegar. */
    boolean shown() {
        return pending != null ? pending : list.machineView();
    }

    /** Snapshot novo: a visão pedida chegou. */
    void onSnapshot() {
        if (pending != null && pending == list.machineView()) {
            pending = null;
        }
    }

    /** A parte do seletor acesa: 0 Roteadores, 1 Máquinas, 2 Grade. */
    int selected() {
        return list.gridView() ? 2 : shown() ? 1 : 0;
    }

    /** O clique no seletor: a parte {@code view} (0 Roteadores, 1 Máquinas, 2 Grade). */
    void choose(int view) {
        if (view == 2) {
            // a Grade é só do cliente; vem sobre a página dos roteadores
            if (list.gridView()) {
                return;
            }
            if (list.multi()) {
                list.toggleMulti();
            }
            list.setGrid(true);
            list.scroll = 0;
            list.treeNav.clear();
            list.machineNav.clear();
            if (shown()) {
                pending = false;
                list.actions.machines(TabletMachinesPayload.Action.ROUTERS, Optional.empty());
            }
            return;
        }
        boolean machines = view == 1;
        boolean wasGrid = list.gridView();
        list.setGrid(false);
        if (shown() == machines) {
            if (wasGrid) {
                list.scroll = 0;
            }
            return;
        }
        pending = machines;
        list.scroll = 0;
        list.treeNav.clear();
        list.machineNav.clear();
        list.actions.machines(machines ? TabletMachinesPayload.Action.MACHINES : TabletMachinesPayload.Action.ROUTERS,
                Optional.empty());
    }

    /** Saiu da Grade pelo clique numa linha: o seletor volta aos Roteadores, que é a página que já está. */
    void showRouters() {
        list.scroll = 0;
    }

    private static Component label(int view) {
        return tr(view == 2 ? "view.grid" : view == 1 ? "view.machines" : "view.routers");
    }

    /** O seletor aparece com algum nó na base (sem nenhum, a Lista mostra o que fazer). */
    private boolean selectorShown() {
        return list.snapshot().totalNodes() > 0 || list.machineView();
    }

    /** Arranja o seletor e os filtros a partir de {@code y}; devolve o fim da última linha (ou {@code y} sem nada). */
    int arrange(Font font, int y) {
        int x = layout.x0;
        int bottom = y;
        if (selectorShown()) {
            int[] widths = new int[3];
            int sum = 0;
            for (int i = 0; i < 3; i++) {
                widths[i] = font.width(label(i)) + 8;
                sum += widths[i];
            }
            // numa lista estreita o seletor encolhe os segmentos mais largos em vez de vazar da lista
            for (; sum > layout.listW; sum--) {
                int widest = 0;
                for (int i = 1; i < 3; i++) {
                    widest = widths[i] > widths[widest] ? i : widest;
                }
                widths[widest]--;
            }
            for (int i = 0; i < 3; i++) {
                layout.hots.add(new Hot(Kind.VIEW, x, y, widths[i], QUICK_H, i));
                x += widths[i];
            }
            x += 6;
            bottom = y + QUICK_H;
        }
        return arrangeQuick(font, x, y, bottom);
    }

    private int arrangeQuick(Font font, int x, int y, int bottom) {
        List<Integer> kinds = new ArrayList<>();
        List<Component> texts = new ArrayList<>();
        List<Component> shorts = new ArrayList<>();
        TabletSnapshot s = list.snapshot();
        if (list.search.typeFilter.isPresent()) {
            kinds.add(-1);
            texts.add(tr("list.type", ResourceStyle.name(list.search.typeFilter.get())));
            shorts.add(tr("list.type", ResourceStyle.name(list.search.typeFilter.get())));
        }
        for (QuickFilter filter : QuickFilter.values()) {
            int count = s.quickCounts().count(filter);
            if (count > 0 || list.search.quick.contains(filter)) {
                String key = filter.name().toLowerCase(Locale.ROOT);
                kinds.add(filter.ordinal());
                texts.add(tr("list.quick." + key, count));
                shorts.add(tr("list.quick." + key + ".short", count));
            }
        }
        layout.quickLabels.clear();
        if (kinds.isEmpty()) {
            return bottom;
        }
        int x1 = layout.x0 + layout.listW;
        if (width(font, texts) > x1 - x) {
            // não cabem ao lado do seletor: a linha de baixo, a largura toda
            if (bottom > y) {
                y = bottom + GAP;
            }
            x = layout.x0;
        }
        List<Component> shown = quickTexts(font, kinds, texts, shorts, x1 - x);
        boolean sameRow = y == layout.y0 + ListLayout.SEARCH_H + GAP;
        for (int i = 0; i < kinds.size(); i++) {
            boolean problem = kinds.get(i) == QuickFilter.PROBLEM.ordinal();
            int extra = kinds.get(i) < 0 ? ResourceStyle.ICON + 3 : problem ? 0 : 8;
            int w = Math.min(font.width(shown.get(i)) + 4 + extra + (problem ? 6 : 0), x1 - x);
            if (w <= extra + 6) {
                break;
            }
            layout.quickLabels.add(shown.get(i));
            // o "! N" fica na ponta direita da linha do seletor
            int hx = problem && sameRow ? x1 - w : x;
            layout.hots.add(new Hot(kinds.get(i) < 0 ? Kind.TYPE_CHIP : Kind.QUICK, hx, y, w, QUICK_H,
                    kinds.get(i) < 0 ? 0 : kinds.get(i)));
            if (!(problem && sameRow)) {
                x += w + 3;
            }
        }
        return y + QUICK_H;
    }

    private static int width(Font font, List<Component> texts) {
        int total = 3 * (texts.size() - 1);
        for (Component t : texts) {
            total += font.width(t) + 12;
        }
        return total;
    }

    /** Os textos dos filtros que cabem em {@code room}: os inteiros, os curtos ou só o número. */
    private List<Component> quickTexts(Font font, List<Integer> kinds, List<Component> texts, List<Component> shorts,
            int room) {
        if (width(font, texts) <= room) {
            return texts;
        }
        if (width(font, shorts) <= room) {
            return shorts;
        }
        // nem os nomes curtos cabem: o tipo continua, os filtros ficam só com o número (o nome vai na dica)
        List<Component> counts = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            counts.add(kinds.get(i) < 0 ? shorts.get(i) : Component.literal(
                    Integer.toString(list.snapshot().quickCounts().count(QuickFilter.values()[kinds.get(i)]))));
        }
        return counts;
    }

    /** O seletor: três segmentos colados, o da visão aberta escuro. */
    void render(GuiGraphics g, Font font, int mx, int my) {
        int on = selected();
        for (int i = 0; i < 3; i++) {
            Hot hot = layout.hot(Kind.VIEW, i);
            if (hot == null) {
                return;
            }
            boolean hovered = hot.contains(mx, my);
            GuiPaint.box(g, hot.x(), hot.y(), hot.w(), hot.h(), on == i ? GuiPaint.FG : GuiPaint.BUTTON,
                    on == i ? GuiPaint.FG : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            int ink = on == i ? GuiPaint.BUTTON : hovered ? GuiPaint.FG : GuiPaint.MUTED;
            if (font.width(label(i)) + 4 <= hot.w()) {
                GuiPaint.textCentered(g, font, label(i), hot.x() + hot.w() / 2 + 1, hot.y() + 2, ink);
            } else {
                GuiText.draw(g, font, label(i), hot.x() + 2, hot.y() + 2, hot.w() - 4, ink);
            }
        }
    }
}
