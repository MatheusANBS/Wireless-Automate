package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.RX;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.SIDE_W;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os nomes das abas Redes e Grupos, divididos pelas duas: a nova rede ou grupo (recolhida embaixo da lista
 * lateral, com Criar), o título da coluna da direita que vira o campo de renomear, e o Remover que pede dois
 * cliques.
 */
final class NameFields {
    /** O que o campo de nome está renomeando. */
    enum Rename { NONE, NETWORK, GROUP }

    private final TabletLayout layout;
    private final TabletActions actions;
    private final Supplier<@Nullable UUID> network;
    private final Supplier<@Nullable UUID> group;
    private final Consumer<@Nullable GuiEventListener> focus;
    private final Runnable refresh;
    private boolean newOpen;
    private EditBox newBox;
    private EditBox renameBox;
    private Rename renaming = Rename.NONE;
    private long removeArmedUntil;

    /**
     * @param network a rede escolhida na aba Redes (a que o nome renomeia)
     * @param group o grupo escolhido na aba Grupos
     * @param focus dá o foco da tela ao campo (ou a nenhum)
     * @param refresh põe a visibilidade dos botões e campos em dia já, sem esperar o próximo quadro
     */
    NameFields(TabletLayout layout, TabletActions actions, Supplier<@Nullable UUID> network,
            Supplier<@Nullable UUID> group, Consumer<@Nullable GuiEventListener> focus, Runnable refresh) {
        this.layout = layout;
        this.actions = actions;
        this.network = network;
        this.group = group;
        this.focus = focus;
        this.refresh = refresh;
    }

    /** Os dois campos, refeitos a cada {@code init}; a tela os põe entre os widgets dela. */
    void createBoxes(Font font) {
        int x = layout.left;
        int y = layout.top;
        newBox = new EditBox(font, x + X0 + 4, y + layout.newBoxY() + 3, SIDE_W - 52, 9, tr("name"));
        newBox.setBordered(false);
        newBox.setMaxLength(32);
        newBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
        renameBox = new EditBox(font, x + RX + 4, y + BODY_Y + 3, layout.rw() - 8, 9, tr("rename"));
        renameBox.setBordered(false);
        renameBox.setMaxLength(32);
        renameBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
    }

    @Nullable EditBox newBox() {
        return newBox;
    }

    EditBox renameBox() {
        return renameBox;
    }

    /** O "Nova rede" ou "Novo grupo": abre e fecha o campo. */
    FlatButton newToggle(Component label) {
        return new FlatButton(layout.left + X0, layout.top + layout.newY(), SIDE_W, 12, label,
                (g, b, hovered) -> TabletButtons.paintMore(g, b, hovered, label, newOpen), () -> {
                    newOpen = !newOpen;
                    newBox.setValue("");
                    // o campo fica visível já, não só no próximo quadro: digitar logo depois funciona
                    refresh.run();
                    if (newOpen) {
                        focus.accept(newBox);
                    }
                }).tooltip(() -> tr("new.tooltip"));
    }

    /** O Criar ao lado do campo da nova rede ou grupo. */
    FlatButton createButton(Action action) {
        return new FlatButton(layout.left + X0 + SIDE_W - 40, layout.top + layout.newBoxY(), 40, ROW_H, tr("create"),
                (g, b, hovered) -> TabletButtons.paintPrimary(g, b, hovered, tr("create")), () -> create(action));
    }

    /** Campos visíveis conforme a aba ({@code manageTab}: Redes ou Grupos). */
    void refresh(boolean manageTab) {
        newBox.visible = newOpen && manageTab;
        renameBox.visible = renaming != Rename.NONE && manageTab;
    }

    boolean newOpen() {
        return newOpen;
    }

    boolean renaming() {
        return renaming != Rename.NONE;
    }

    /** Larga o nome em edição sem gravar e sem mexer no foco. */
    void clearRename() {
        renaming = Rename.NONE;
    }

    /** Outra aba: fecha a nova rede ou grupo, o nome em edição e o Remover armado. */
    void reset() {
        renaming = Rename.NONE;
        newOpen = false;
        removeArmedUntil = 0;
    }

    boolean removeArmed() {
        return System.currentTimeMillis() < removeArmedUntil;
    }

    void disarm() {
        removeArmedUntil = 0;
    }

    /** Remover pede dois cliques: o primeiro arma por 3 s. */
    void removeSelected(Action action, @Nullable UUID id) {
        if (id == null) {
            return;
        }
        if (!removeArmed()) {
            removeArmedUntil = System.currentTimeMillis() + 3000;
            return;
        }
        removeArmedUntil = 0;
        actions.remove(action, id);
    }

    void create(Action action) {
        String name = newBox.getValue().strip();
        if (name.isEmpty()) {
            return;
        }
        actions.create(action, name);
        newBox.setValue("");
        newOpen = false;
        focus.accept(null);
    }

    void startRename(Rename what, String current) {
        renaming = what;
        renameBox.setValue(current);
        renameBox.moveCursorToEnd(false);
        refresh.run();
        focus.accept(renameBox);
    }

    void finishRename(boolean commit) {
        if (renaming == Rename.NONE) {
            return;
        }
        String name = renameBox.getValue().strip();
        if (commit && !name.isEmpty()) {
            UUID selectedNetwork = network.get();
            UUID selectedGroup = group.get();
            if (renaming == Rename.NETWORK && selectedNetwork != null) {
                actions.rename(Action.NETWORK_RENAME, selectedNetwork, name);
            } else if (renaming == Rename.GROUP && selectedGroup != null) {
                actions.rename(Action.GROUP_RENAME, selectedGroup, name);
            }
        }
        renaming = Rename.NONE;
        focus.accept(null);
    }

    void previewOpen(String text) {
        newOpen = true;
        newBox.setValue(text);
        focus.accept(newBox);
    }

    // ------------------------------------------------------------------ título e campo da nova

    boolean overTitle(double mouseX, double mouseY) {
        return mouseX >= layout.left + RX && mouseX < layout.left + layout.x1() && mouseY >= layout.top + BODY_Y
                && mouseY < layout.top + BODY_Y + ROW_H;
    }

    /** Nome da rede ou do grupo no alto da coluna da direita; clicável para renomear quando pode. */
    void renderTitle(GuiGraphics g, Font font, int mouseX, int mouseY, Component name, int color, boolean canRename) {
        int x = layout.left + RX;
        int y = layout.top + BODY_Y;
        if (renaming != Rename.NONE) {
            GuiPaint.box(g, x, y, layout.rw(), ROW_H, GuiPaint.INSET, GuiPaint.ACCENT);
            return;
        }
        boolean hovered = canRename && overTitle(mouseX, mouseY);
        GuiPaint.eye(g, x, y + 4, color);
        GuiText.draw(g, font, name, x + 9, y + 3, layout.rw() - 10, hovered ? GuiPaint.ACCENT : GuiPaint.FG);
        if (hovered) {
            int width = Math.min(font.width(name), layout.rw() - 10);
            for (int i = 0; i < width; i += 2) {
                g.fill(x + 9 + i, y + 12, x + 10 + i, y + 13, GuiPaint.ACCENT);
            }
        }
    }

    /** A caixa da nova rede ou grupo, aberta, embaixo da lista lateral. */
    void renderNewFrame(GuiGraphics g) {
        if (newOpen) {
            GuiPaint.box(g, layout.left + X0, layout.top + layout.newBoxY(), SIDE_W - 44, ROW_H, GuiPaint.INSET,
                    newBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);
        }
    }
}
