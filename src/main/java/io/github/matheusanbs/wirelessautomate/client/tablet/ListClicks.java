package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickFilter;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * O mouse da Lista do Tablet: o clique (painel e listas abertas primeiro, depois as sugestões, depois as áreas
 * do {@link ListLayout}: etiquetas, filtros, cabeçalhos, linhas e botões do painel, com o duplo clique que abre
 * o nó, e as da árvore, que vão ao {@link TreeClicks}) e a roda (o painel, ou a rolagem da lista).
 */
final class ListClicks {
    private static final long DOUBLE_CLICK_MS = 400;

    private final TabletList list;
    private final ListLayout layout;
    private @Nullable NodeKey lastClicked;
    private long lastClickAt;

    ListClicks(TabletList list, ListLayout layout) {
        this.list = list;
        this.layout = layout;
    }

    boolean mouseClicked(double mx, double my, int button) {
        layout.arrange();
        NodePanel p = list.chosenPanel.current();
        if (p != null && p.labelClick(mx, my, button)) {
            return true;
        }
        if (p != null && p.popupOpen()) {
            return p.mouseClicked(mx, my, button);
        }
        SearchSuggestions suggestions = list.search.suggestions;
        if (suggestions.contains(mx, my)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                suggestions.click(mx, my, list.search.picker);
            }
            return true;
        }
        EditBox searchBox = list.search.box();
        if (suggestions.shown() && !(searchBox != null && searchBox.isMouseOver(mx, my))) {
            suggestions.close();
        }
        if (p != null && mx >= layout.panelX && p.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Hot hot = layout.hotAt(mx, my);
        if (hot == null) {
            return clickEmpty(mx, my);
        }
        clickHot(hot);
        return true;
    }

    private void clickHot(Hot hot) {
        List<NodeView> nodeViews = list.snapshot().nodes();
        ListProfileActions profile = list.profileActions;
        switch (hot.kind()) {
            case LABEL -> list.search.removeLabel(hot.arg());
            case QUICK -> list.search.toggleQuick(QuickFilter.values()[hot.arg()]);
            case TYPE_CHIP -> list.search.clearType();
            case CLEAR -> list.clearAll();
            case FORGET -> profile.forget();
            case HEADER -> list.model.toggle(list.model.tree().lines().get(hot.arg()).group());
            case PASTE_GROUP -> profile.pasteGroup(list.model.tree().lines().get(hot.arg()).group());
            case ROW -> clickRow(nodeViews.get(hot.arg()).key());
            case TREE, TREE_TOGGLE, TREE_EYE, TREE_FEW -> list.treeClicks.click(hot);
            case FULL_SCREEN -> {
                if (list.chosen != null) {
                    list.actions.openNode(list.chosen);
                }
            }
            case COPY -> profile.copy();
            case PASTE -> profile.paste();
            case UNDO -> {
                PanelNotices.clear();
                profile.undo();
            }
        }
    }

    /** O "Limpar a busca" da lista vazia. */
    private boolean clickEmpty(double mx, double my) {
        int[] clear = layout.emptyClearCenter();
        if (clear != null && Math.abs(mx - clear[0]) <= TabletList.font().width(tr("list.clear_search")) / 2 + 2
                && Math.abs(my - clear[1]) <= 6) {
            list.clearAll();
            return true;
        }
        return mx >= layout.x0 && mx < layout.x0 + layout.listW && my >= layout.rowsTop && my < layout.rowsBottom;
    }

    private void clickRow(NodeKey key) {
        long now = System.currentTimeMillis();
        boolean twice = key.equals(lastClicked) && now - lastClickAt <= DOUBLE_CLICK_MS;
        lastClicked = key;
        lastClickAt = now;
        if (list.multi) {
            if (!list.selected.remove(key)) {
                list.selected.add(key);
            }
            return;
        }
        if (twice) {
            lastClicked = null;
            list.actions.openNode(key);
            return;
        }
        list.treeNav.clear();
        list.choose(key);
    }

    boolean mouseScrolled(double mx, double my, double scrollY) {
        NodePanel p = list.chosenPanel.current();
        if (p != null && p.mouseScrolled(mx, my, scrollY)) {
            return true;
        }
        if (mx < layout.x0 + layout.listW) {
            list.scroll = Math.max(0, Math.min(layout.maxScroll(), list.scroll - (int) Math.signum(scrollY)));
        }
        return true;
    }
}
