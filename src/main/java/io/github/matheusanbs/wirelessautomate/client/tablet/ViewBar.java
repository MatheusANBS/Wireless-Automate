package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.GAP;
import static io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.QUICK_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
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
 * A linha embaixo da busca na Lista do Tablet: o seletor "Roteadores · Máquinas" ({@link Kind#VIEW}, argumento 0 ou
 * 1) e, à direita dele, o filtro de tipo e os filtros rápidos, que só aparecem quando pedem atenção e passam para a
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

    /** O clique no seletor: pede a visão por máquinas ({@code true}) ou por roteadores. */
    void choose(boolean machines) {
        if (shown() == machines) {
            return;
        }
        pending = machines;
        list.scroll = 0;
        list.treeNav.clear();
        list.machineNav.clear();
        list.actions.machines(machines ? TabletMachinesPayload.Action.MACHINES : TabletMachinesPayload.Action.ROUTERS,
                Optional.empty());
    }

    private static Component label(boolean machines) {
        return tr(machines ? "view.machines" : "view.routers");
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
            for (int i = 0; i < 2; i++) {
                int w = font.width(label(i == 1)) + 8;
                layout.hots.add(new Hot(Kind.VIEW, x, y, w, QUICK_H, i));
                x += w;
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
        for (int i = 0; i < kinds.size(); i++) {
            int extra = kinds.get(i) < 0 ? ResourceStyle.ICON + 3 : 8;
            int w = Math.min(font.width(shown.get(i)) + 4 + extra, x1 - x);
            if (w <= extra + 6) {
                break;
            }
            layout.quickLabels.add(shown.get(i));
            layout.hots.add(new Hot(kinds.get(i) < 0 ? Kind.TYPE_CHIP : Kind.QUICK, x, y, w, QUICK_H,
                    kinds.get(i) < 0 ? 0 : kinds.get(i)));
            x += w + 3;
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

    /** O seletor: dois segmentos colados, o da visão aberta em destaque. */
    void render(GuiGraphics g, Font font, int mx, int my) {
        boolean machines = shown();
        for (int i = 0; i < 2; i++) {
            Hot hot = layout.hot(Kind.VIEW, i);
            if (hot == null) {
                return;
            }
            boolean on = machines == (i == 1);
            boolean hovered = hot.contains(mx, my);
            GuiPaint.box(g, hot.x(), hot.y(), hot.w(), hot.h(), on ? GuiPaint.BUTTON : GuiPaint.PANEL,
                    on ? GuiPaint.FG : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.LINE);
            if (on) {
                g.fill(hot.x() + 1, hot.y() + hot.h() - 2, hot.x() + hot.w() - 1, hot.y() + hot.h() - 1, GuiPaint.ACCENT);
            }
            GuiPaint.textCentered(g, font, label(i == 1), hot.x() + hot.w() / 2 + 1, hot.y() + 2,
                    on || hovered ? GuiPaint.FG : GuiPaint.MUTED);
        }
    }
}
