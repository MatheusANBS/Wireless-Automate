package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

/**
 * O teclado da Lista do Tablet (especificação, "Teclado"): Ctrl+C, Ctrl+V e Ctrl+Z do perfil, Esc, as setas nas
 * sugestões e nas linhas, Enter, abrir e fechar na árvore ({@link TreeNav}) e apagar a última etiqueta.
 */
final class ListKeys {
    private final TabletList list;

    ListKeys(TabletList list) {
        this.list = list;
    }

    /** {@code false} deixa a tecla para a busca ou a tela. */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (list.rename.editor().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
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
                } else if (list.machineView()) {
                    list.machineNav.upDown(direction);
                } else {
                    list.treeNav.upDown(direction);
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (search.suggestions.hasHighlight()) {
                    search.suggestions.pickHighlighted(search.picker);
                } else if (suggesting || search.pending()) {
                    search.suggestions.close();
                    search.sendQuery();
                } else if (!(list.machineView() ? list.machineNav.enter() : list.treeNav.enter())
                        && list.chosen != null) {
                    list.actions.openNode(list.chosen);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                // com texto na busca focada, as setas andam no texto; com uma linha da árvore marcada, na árvore
                EditBox box = search.box();
                boolean machines = list.machineView();
                boolean cursor = machines ? list.machineNav.hasCursor() : list.treeNav.hasCursor();
                if (!search.text.isEmpty() && box != null && box.isFocused() && !cursor) {
                    return false;
                }
                if (machines) {
                    list.machineNav.arrow(keyCode == GLFW.GLFW_KEY_LEFT);
                } else {
                    list.treeNav.arrow(keyCode == GLFW.GLFW_KEY_LEFT);
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
