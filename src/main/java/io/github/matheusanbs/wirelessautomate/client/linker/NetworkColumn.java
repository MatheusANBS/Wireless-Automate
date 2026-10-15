package io.github.matheusanbs.wirelessautomate.client.linker;

import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.LIST_Y;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.LW;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.ROW;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A rede ativa, na coluna da esquerda: a linha "Nenhuma (desvincular)", a lista das redes do jogador
 * com rolagem e o Nova rede, que abre o campo do nome.
 */
final class NetworkColumn {
    private final LinkerScreen screen;
    private final LinkerScreenActions actions;
    private final List<FlatButton> rowButtons = new ArrayList<>();
    private int scroll;
    private boolean creating;
    /** O nome começado para a rede nova, lembrado na sessão (como o tamanho): volta ao reabrir o campo ou a tela. */
    private static String draft = "";

    private FlatButton newButton;
    private FlatButton createButton;
    private EditBox nameBox;

    NetworkColumn(LinkerScreen screen, LinkerScreenActions actions) {
        this.screen = screen;
        this.actions = actions;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private LinkerSnapshot snapshot() {
        return screen.snapshot();
    }

    /** Linhas da lista: "Nenhuma (desvincular)" e as redes. */
    int rowCount() {
        return snapshot().networks().size() + 1;
    }

    /** A rede da linha {@code index} da lista; {@code null} na linha 0, "Nenhuma (desvincular)". */
    private @Nullable NetworkEntry entryAt(int index) {
        return index == 0 ? null : snapshot().networks().get(index - 1);
    }

    boolean creating() {
        return creating;
    }

    EditBox nameBox() {
        return nameBox;
    }

    /** As linhas da lista (o máximo que a maior altura comporta), o Nova rede, o Criar e o campo do nome. */
    void addWidgets(int x, int y) {
        rowButtons.clear();
        for (int i = 0; i < screen.maxRows(); i++) {
            int row = i;
            FlatButton button = screen.add(new FlatButton(x + X0 + 1, y + LIST_Y + 2 + i * ROW, LW - 2, ROW, Component.empty(),
                    (g, b, hovered) -> paintRow(g, b, hovered, row), () -> chooseRow(row))
                    .tooltip(() -> rowTooltip(row)));
            rowButtons.add(button);
        }
        newButton = screen.add(new FlatButton(x + X0, y + screen.newY(), LW, 14, tr("network.new"),
                (g, b, hovered) -> LinkerPaint.paintText(g, b, hovered, tr("network.new"), GuiPaint.FG), this::startCreate)
                .tooltip(() -> tr("network.new.tooltip")));
        createButton = screen.add(new FlatButton(x + X0 + LW - 26, y + screen.newY(), 26, 14, tr("network.create"),
                (g, b, hovered) -> LinkerPaint.paintText(g, b, hovered, tr("network.create"), GuiPaint.FG),
                () -> finishCreate(true)));
        nameBox = new EditBox(font(), x + X0 + 4, y + screen.newY() + 3, LW - 34, 9, tr("network.name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(LinkerActionPayload.MAX_NAME_LENGTH);
        nameBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
        nameBox.setValue(draft);
        nameBox.setResponder(value -> draft = value);
        screen.resetInk();
        screen.addBox(nameBox);
        if (creating) {
            screen.setFocused(nameBox);
        }
    }

    /** As linhas visíveis e o lugar do Nova rede, do Criar e do campo. */
    void refresh() {
        int rows = rowCount();
        int listRows = screen.listRows();
        scroll = Math.max(0, Math.min(scroll, rows - listRows));
        for (int i = 0; i < rowButtons.size(); i++) {
            FlatButton row = rowButtons.get(i);
            int index = i + scroll;
            row.visible = index < rows;
            NetworkEntry entry = row.visible ? entryAt(index) : null;
            row.setMessage(!row.visible ? Component.empty()
                    : entry == null ? tr("network.unlink") : Component.literal(entry.name()));
        }
        // a lista encolhe quando os tipos precisam de mais linhas; os botões que passam dela somem
        for (int i = listRows; i < rowButtons.size(); i++) {
            rowButtons.get(i).visible = false;
        }
    }

    /** Depois do lugar dos tipos: o Nova rede, o Criar e o campo seguem a altura da lista. */
    void refreshCreate(int top) {
        newButton.setY(top + screen.newY());
        createButton.setY(top + screen.newY());
        nameBox.setY(top + screen.newY() + 3);
        newButton.visible = !creating;
        createButton.visible = creating;
        createButton.active = !draft.strip().isEmpty();
        nameBox.visible = creating;
    }

    private void chooseRow(int row) {
        int index = row + scroll;
        if (index >= rowCount()) {
            return;
        }
        actions.choose(entryAt(index));
    }

    void startCreate() {
        creating = true;
        nameBox.setValue(draft);
        screen.setFocused(nameBox);
        screen.refresh();
    }

    void finishCreate(boolean commit) {
        if (!creating) {
            return;
        }
        creating = false;
        screen.setFocused(null);
        String name = nameBox.getValue().strip();
        if (commit && !name.isEmpty() && actions.createNetwork(name)) {
            draft = "";
            nameBox.setValue("");
        }
        screen.refresh();
    }

    /**
     * Fecha o campo sem tocar no rascunho ({@link #draft}, lembrado na sessão), que volta ao reabrir o
     * campo ou a tela; criada a rede, o rascunho fica vazio.
     */
    void close() {
        creating = false;
    }

    /** Captura: esquece o rascunho. */
    void forgetDraft() {
        draft = "";
    }

    /** Um clique fora do campo e do Criar fecha o campo sem criar. */
    void clickOutside(double mouseX, double mouseY) {
        if (creating && !nameBox.isMouseOver(mouseX, mouseY) && !createButton.isMouseOver(mouseX, mouseY)) {
            finishCreate(false);
        }
    }

    /** Com o campo aberto, toda tecla é dele: nenhuma fecha a tela enquanto se digita (nem a do inventário). */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!creating) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            finishCreate(false);
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finishCreate(true);
        } else {
            nameBox.keyPressed(keyCode, scanCode, modifiers);
            screen.refresh();
        }
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int left = screen.left();
        int top = screen.top();
        if (mouseX >= left + X0 && mouseX < left + X0 + LW && mouseY >= top + LIST_Y
                && mouseY < top + LIST_Y + screen.listH()) {
            scroll -= (int) Math.signum(scrollY);
            screen.refresh();
            return true;
        }
        return false;
    }

    void render(GuiGraphics g, int x, int y) {
        Font font = font();
        LinkerSnapshot s = snapshot();
        GuiPaint.text(g, font, tr("network.label"), x + X0, y + BODY_Y, GuiPaint.MUTED);
        GuiPaint.box(g, x + X0, y + LIST_Y, LW, screen.listH(), GuiPaint.INSET, GuiPaint.LINE);
        if (s.networks().isEmpty() && scroll == 0) {
            // embaixo da linha "Nenhuma (desvincular)"
            GuiText.draw(g, font, tr("network.empty"), x + X0 + 5, y + LIST_Y + 4 + ROW, LW - 10, GuiPaint.MUTED);
        }
        if (scroll > 0) {
            GuiPaint.text(g, font, Component.literal("▲"), x + X0 + LW - 9, y + LIST_Y + 2, GuiPaint.MUTED);
        }
        if (scroll + screen.listRows() < rowCount()) {
            GuiPaint.text(g, font, Component.literal("▼"), x + X0 + LW - 9, y + LIST_Y + screen.listH() - 10, GuiPaint.MUTED);
        }
        if (creating) {
            GuiPaint.box(g, x + X0, y + screen.newY(), LW - 28, 14, GuiPaint.INSET, GuiPaint.ACCENT);
        }
    }

    private void paintRow(GuiGraphics g, FlatButton b, boolean hovered, int row) {
        int index = row + scroll;
        if (index >= rowCount()) {
            return;
        }
        Font font = font();
        boolean unlink = snapshot().unlink();
        int scrollSpace = rowCount() > screen.listRows() ? 10 : 0;
        NetworkEntry entry = entryAt(index);
        if (entry == null) {
            // "Nenhuma (desvincular)": anel vazio na cor do modo
            boolean selected = unlink;
            if (selected || hovered) {
                g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(),
                        selected ? GuiPaint.ROW_SELECTED : GuiPaint.ROW_HOVER);
            }
            if (selected) {
                g.fill(b.getX(), b.getY(), b.getX() + 1, b.getY() + b.getHeight(), GuiPaint.DANGER);
            }
            GuiPaint.outline(g, b.getX() + 5, b.getY() + 3, 6, 6, GuiPaint.DANGER);
            GuiText.draw(g, font, tr("network.unlink"), b.getX() + 14, b.getY() + 2, b.getWidth() - 18 - scrollSpace,
                    selected ? GuiPaint.FG : GuiPaint.MUTED);
            return;
        }
        boolean selected = !unlink && Optional.of(entry.id()).equals(snapshot().active());
        if (selected) {
            g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), GuiPaint.ROW_SELECTED);
            g.fill(b.getX(), b.getY(), b.getX() + 1, b.getY() + b.getHeight(), GuiPaint.ACCENT);
        } else if (hovered && entry.owned()) {
            g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), GuiPaint.ROW_HOVER);
        }
        GuiPaint.eye(g, b.getX() + 5, b.getY() + 3, 0xFF000000 | entry.color());
        GuiText.draw(g, font, Component.literal(entry.name()), b.getX() + 14, b.getY() + 2,
                b.getWidth() - 18 - scrollSpace,
                entry.owned() ? (selected ? GuiPaint.FG : GuiPaint.MUTED) : GuiPaint.DISABLED);
    }

    private @Nullable Component rowTooltip(int row) {
        int index = row + scroll;
        if (index >= rowCount()) {
            return null;
        }
        NetworkEntry entry = entryAt(index);
        if (entry == null) {
            return tr("network.unlink").copy().append("\n")
                    .append(tr("network.unlink.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
        }
        Component name = Component.literal(entry.name());
        if (!entry.owned()) {
            return name.copy().append("\n").append(tr("network.foreign").copy().withColor(GuiPaint.TOOLTIP_MUTED));
        }
        if (!snapshot().unlink() && Optional.of(entry.id()).equals(snapshot().active())) {
            return name.copy().append("\n").append(tr("network.active").copy().withColor(GuiPaint.TOOLTIP_MUTED));
        }
        return name.copy().append("\n").append(tr("network.choose").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }
}
