package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * O teclado da Lista do Tablet (especificação, "Teclado"): Ctrl+C, Ctrl+V e Ctrl+Z do perfil, Esc, as setas nas
 * sugestões e nos nós, Enter, recolher e abrir o grupo e apagar a última etiqueta.
 */
final class ListKeys {
    private final TabletList list;

    ListKeys(TabletList list) {
        this.list = list;
    }

    /** {@code false} deixa a tecla para a busca ou a tela. */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        NodePanel p = list.chosenPanel.current();
        if (p != null && p.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (ctrl) {
            Boolean handled = profileKey(keyCode);
            if (handled != null) {
                return handled;
            }
        }
        ListSearch search = list.search;
        boolean suggesting = search.suggestions.shown();
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (suggesting) {
                    search.suggestions.close();
                    return true;
                }
                if (search.filtering()) {
                    search.clearAll();
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                int direction = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                if (suggesting) {
                    search.suggestions.move(direction);
                } else {
                    int next = list.model.groups().next(list.model.indexOf(list.chosen), direction);
                    if (next >= 0) {
                        list.choose(list.snapshot().nodes().get(next).key());
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (search.suggestions.hasHighlight()) {
                    search.suggestions.pickHighlighted(search.picker);
                } else if (suggesting || search.pending()) {
                    search.suggestions.close();
                    search.sendQuery();
                } else if (list.chosen != null) {
                    list.actions.openNode(list.chosen);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                ResourceLocation group = list.model.groups().group(list.model.indexOf(list.chosen));
                if (!search.text.isEmpty() || group == null) {
                    return false;
                }
                if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    list.model.collapse(group);
                } else {
                    list.model.expand(group);
                }
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (search.text.isEmpty() && !search.labels.isEmpty()) {
                    search.removeLabel(search.labels.size() - 1);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** Ctrl+C, Ctrl+V e Ctrl+Z do perfil; {@code null} quando a tecla não é deles. */
    private Boolean profileKey(int keyCode) {
        EditBox searchBox = list.search.box();
        if (keyCode == GLFW.GLFW_KEY_C) {
            if (searchBox != null && !searchBox.getHighlighted().isEmpty()) {
                return false;
            }
            list.profileActions.copy();
            return true;
        }
        // com texto na busca focada, Ctrl+V cola texto
        boolean typing = searchBox != null && searchBox.isFocused() && !list.search.text.isEmpty();
        if (keyCode == GLFW.GLFW_KEY_V && list.profile().isPresent() && list.chosen != null && !typing) {
            list.profileActions.paste();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Z) {
            PanelNotices.clear();
            list.profileActions.undo();
            return true;
        }
        return null;
    }
}
