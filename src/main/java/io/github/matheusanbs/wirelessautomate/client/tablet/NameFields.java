package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.RX;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.SIDE_W;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
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
 * Os nomes da aba Redes: a nova rede ou o novo grupo (os dois embaixo da lista lateral, um campo só, com Criar), o
 * título da coluna da direita (a rede ou o grupo escolhido) que vira o campo de renomear, e o Remover que pede dois
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
    private final Consumer<Boolean> created;
    /** O que o campo da nova está criando ({@link Rename#NONE}: fechado). */
    private Rename newKind = Rename.NONE;
    private EditBox newBox;
    private EditBox renameBox;
    private Rename renaming = Rename.NONE;
    private final ArmedClick removeArm = new ArmedClick();

    /**
     * @param network a rede escolhida na aba Redes (a que o nome renomeia)
     * @param group o grupo escolhido na aba Redes
     * @param focus dá o foco da tela ao campo (ou a nenhum)
     * @param refresh põe a visibilidade dos botões e campos em dia já, sem esperar o próximo quadro
     * @param created avisa que um grupo ({@code true}) ou uma rede foi pedida ao servidor
     */
    NameFields(TabletLayout layout, TabletActions actions, Supplier<@Nullable UUID> network,
            Supplier<@Nullable UUID> group, Consumer<@Nullable GuiEventListener> focus, Runnable refresh,
            Consumer<Boolean> created) {
        this.layout = layout;
        this.actions = actions;
        this.network = network;
        this.group = group;
        this.focus = focus;
        this.refresh = refresh;
        this.created = created;
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

    /**
     * O "Nova rede" ({@code kind} {@link Rename#NETWORK}, à esquerda) ou o "Novo grupo" (à direita), lado a lado
     * embaixo da lista: abre e fecha o campo; o outro aberto passa a este.
     */
    FlatButton newToggle(Rename kind, Component label, Font font) {
        int w = Math.min(SIDE_W / 2 + 4, font.width(label) + 2);
        int x = kind == Rename.NETWORK ? layout.left + X0 : layout.left + X0 + SIDE_W - w;
        return new FlatButton(x, layout.top + layout.newY(), w, 12, label,
                (g, b, hovered) -> TabletButtons.paintNew(g, b, hovered, label, newKind == kind), () -> {
                    newKind = newKind == kind ? Rename.NONE : kind;
                    newBox.setValue("");
                    // o campo fica visível já, não só no próximo quadro: digitar logo depois funciona
                    refresh.run();
                    if (newKind != Rename.NONE) {
                        focus.accept(newBox);
                    }
                }).tooltip(() -> tr("new.tooltip"));
    }

    /** O Criar ao lado do campo da nova rede ou grupo. */
    FlatButton createButton() {
        return new FlatButton(layout.left + X0 + SIDE_W - 40, layout.top + layout.newBoxY(), 40, ROW_H, tr("create"),
                (g, b, hovered) -> TabletButtons.paintPrimary(g, b, hovered, tr("create")), this::create);
    }

    /** Campos visíveis conforme a aba ({@code shown}: Redes). */
    void refresh(boolean shown) {
        newBox.visible = newKind != Rename.NONE && shown;
        renameBox.visible = renaming != Rename.NONE && shown;
    }

    boolean newOpen() {
        return newKind != Rename.NONE;
    }

    /** O campo da nova está criando um grupo. */
    boolean creatingGroup() {
        return newKind == Rename.GROUP;
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
        newKind = Rename.NONE;
        removeArm.disarm();
    }

    boolean removeArmed() {
        return removeArm.armed(System.currentTimeMillis());
    }

    void disarm() {
        removeArm.disarm();
    }

    /** Remover pede dois cliques: o primeiro arma por 3 s. */
    void removeSelected(Action action, @Nullable UUID id) {
        if (id == null) {
            return;
        }
        if (removeArm.click(System.currentTimeMillis())) {
            actions.remove(action, id);
        }
    }

    /** Cria a rede ou o grupo com o nome do campo; a lista escolhe o novo quando ele chega ({@code created}). */
    void create() {
        String name = newBox.getValue().strip();
        if (name.isEmpty() || newKind == Rename.NONE) {
            return;
        }
        boolean group = newKind == Rename.GROUP;
        actions.create(group ? Action.GROUP_CREATE : Action.NETWORK_CREATE, name);
        created.accept(group);
        newBox.setValue("");
        newKind = Rename.NONE;
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
        newKind = Rename.NETWORK;
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
        if (newKind != Rename.NONE) {
            GuiPaint.box(g, layout.left + X0, layout.top + layout.newBoxY(), SIDE_W - 44, ROW_H, GuiPaint.INSET,
                    newBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);
        }
    }
}
