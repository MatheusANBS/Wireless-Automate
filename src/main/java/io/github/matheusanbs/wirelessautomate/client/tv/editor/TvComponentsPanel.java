package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvTemplates;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Os Componentes, embaixo da coluna da esquerda: os três prontos do mod e, depois, os do jogador (a biblioteca do
 * {@link TvLibraryClient}), numa lista que rola. O clique numa linha insere o componente num grupo novo, no primeiro
 * lugar livre; nos do jogador, o ✎ renomeia (um campo de texto no topo da lista; Enter salva, Esc cancela) e o ✕
 * remove em dois cliques. "Salvar como componente" da barra abre o mesmo campo para dar o nome à escolha. Tudo que
 * vai ao servidor sai pelo {@link TvEditorActions}. Os textos são medidos quando a lista, a janela ou a biblioteca
 * mudam, nunca por quadro; só as linhas à vista são desenhadas.
 */
final class TvComponentsPanel {
    private static final int ROW_H = 12;
    private static final int BTN = 10;
    private static final int EDIT_H = TvTextField.H + 3;

    /** Uma linha da lista: o componente, se é do jogador e o nome medido. */
    private record Row(TvComponent component, boolean mine, GuiText.Fitted name) {
    }

    private final TvEditorDoc doc;
    private final TvEditorActions actions;
    private final List<TvComponent> ready = TvTemplates.components();
    private final ArmedClick armed = new ArmedClick();
    private TvRect box = TvRect.NONE;
    private Font font;
    private List<Row> rows = List.of();
    private int builtLibrary = -1;
    private int scroll;
    private @Nullable String armedName;
    private @Nullable TvTextField edit;
    private @Nullable String editFrom;
    private String pending = "";
    private GuiText.Fitted confirm;
    private final Component renameTip = TvEditorText.key("component.rename");
    private final Component removeTip = TvEditorText.key("component.remove");
    private final Component pencil = Component.literal("✎");
    private final Component cross = Component.literal("✕");

    TvComponentsPanel(TvEditorDoc doc, TvEditorActions actions) {
        this.doc = doc;
        this.actions = actions;
    }

    void layout(Font font, TvEditorLayout layout) {
        this.font = font;
        box = layout.components();
        cancel();
        confirm = GuiText.fit(font, TvEditorText.key("component.remove_confirm"), box.w() - 8 - 2 * BTN);
        builtLibrary = -1;
    }

    boolean contains(double mouseX, double mouseY) {
        return box.contains(mouseX, mouseY);
    }

    private int top() {
        return box.y() + 2 + (edit != null ? EDIT_H : 0);
    }

    private int viewHeight() {
        return box.h() - 4 - (edit != null ? EDIT_H : 0);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * ROW_H - viewHeight());
    }

    private int rowWidth() {
        return box.w() - 2 - (maxScroll() > 0 ? 3 : 0);
    }

    private void rebuild() {
        if (builtLibrary == TvLibraryClient.version() && !rows.isEmpty()) {
            return;
        }
        builtLibrary = TvLibraryClient.version();
        List<TvComponent> mine = TvLibraryClient.components();
        List<Row> out = new ArrayList<>(ready.size() + mine.size());
        int w = box.w() - 2;
        for (TvComponent component : ready) {
            out.add(new Row(component, false, GuiText.fit(font, TvEditorText.readyName(component.name()), w - 8)));
        }
        for (TvComponent component : mine) {
            out.add(new Row(component, true, GuiText.fit(font, Component.literal(component.name()),
                    w - 8 - 2 * BTN - 2)));
        }
        rows = out;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        if (armedName != null && out.stream().noneMatch(r -> r.mine && r.component.name().equals(armedName))) {
            armedName = null;
        }
    }

    /** "Salvar como componente": abre o campo do nome para a escolha atual. */
    void beginSave() {
        if (doc.hasSelection()) {
            openField("", null);
        }
    }

    private void beginRename(TvComponent component) {
        openField(component.name(), component.name());
    }

    private void openField(String text, @Nullable String from) {
        pending = text;
        editFrom = from;
        edit = new TvTextField(font, "component.name", box.x() + 2, box.y() + 2, box.w() - 4, TvComponent.MAX_NAME,
                null, TvEditorText.key("component.name"), () -> pending, value -> pending = value);
        edit.place(0, 0);
        edit.focus();
        scroll = 0;
    }

    private void commit() {
        String name = pending.strip();
        if (!name.isEmpty()) {
            if (editFrom == null) {
                actions.saveComponent(doc.selectionAsComponent(name));
            } else if (!name.equals(editFrom)) {
                actions.renameComponent(editFrom, name);
            }
        }
        cancel();
    }

    /** Fecha o campo do nome sem salvar. */
    void cancel() {
        edit = null;
        editFrom = null;
        pending = "";
    }

    boolean textFocused() {
        return edit != null;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        rebuild();
        if (box.h() <= 0) {
            return;
        }
        GuiPaint.inset(g, box.x(), box.y(), box.w(), box.h());
        if (edit != null) {
            edit.render(g, mouseX, mouseY);
        }
        int top = top();
        int bottom = box.bottom() - 2;
        g.enableScissor(box.x() + 1, top, box.right() - 1, bottom);
        int w = rowWidth();
        long now = armedName != null ? System.currentTimeMillis() : 0;
        for (int i = 0; i < rows.size(); i++) {
            int y = top + i * ROW_H - scroll;
            if (y + ROW_H <= top || y >= bottom) {
                continue;
            }
            Row row = rows.get(i);
            int x = box.x() + 1;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H
                    && mouseY >= top && mouseY < bottom;
            if (hovered) {
                g.fill(x, y, x + w, y + ROW_H, GuiPaint.alpha(GuiPaint.ACCENT, 0x30));
            }
            boolean confirming = row.mine && armedName != null && row.component.name().equals(armedName)
                    && armed.armed(now);
            if (confirming) {
                GuiText.draw(g, font, confirm, x + 4, y + 2, GuiPaint.DANGER);
            } else {
                GuiText.draw(g, font, row.name, x + 4, y + 2, GuiPaint.FG);
            }
            if (row.mine) {
                GuiPaint.text(g, font, pencil, x + w - 2 * BTN + 1, y + 2, GuiPaint.MUTED);
                GuiPaint.text(g, font, cross, x + w - BTN + 1, y + 2, confirming ? GuiPaint.DANGER : GuiPaint.MUTED);
            }
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int track = viewHeight();
            int thumb = Math.max(8, track * track / (rows.size() * ROW_H));
            int thumbY = top + (track - thumb) * scroll / maxScroll();
            g.fill(box.right() - 4, thumbY, box.right() - 2, thumbY + thumb, GuiPaint.BUTTON_BORDER);
        }
    }

    /** A linha sob o ponto, ou -1. */
    private int rowAt(double mouseX, double mouseY) {
        int top = top();
        if (mouseX < box.x() + 1 || mouseX >= box.x() + 1 + rowWidth() || mouseY < top
                || mouseY >= box.bottom() - 2) {
            return -1;
        }
        int i = (int) (mouseY - top + scroll) / ROW_H;
        return i >= 0 && i < rows.size() ? i : -1;
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!box.contains(mouseX, mouseY)) {
            return false;
        }
        rebuild();
        if (edit != null && edit.contains(mouseX, mouseY)) {
            edit.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        if (edit != null) {
            cancel();
        }
        int i = rowAt(mouseX, mouseY);
        if (i < 0) {
            return true;
        }
        Row row = rows.get(i);
        int right = box.x() + 1 + rowWidth();
        if (row.mine && mouseX >= right - BTN) {
            removeClicked(row.component.name());
        } else if (row.mine && mouseX >= right - 2 * BTN) {
            armedName = null;
            beginRename(row.component);
        } else {
            armedName = null;
            doc.insertComponent(row.mine ? row.component : localized(row.component));
        }
        return true;
    }

    /** O ✕ de uma linha do jogador: o primeiro clique arma, o segundo (dentro do prazo) remove. */
    private void removeClicked(String name) {
        long now = System.currentTimeMillis();
        if (name.equals(armedName) && armed.armed(now)) {
            armed.disarm();
            armedName = null;
            actions.removeComponent(name);
        } else {
            armedName = name;
            armed.arm(now);
        }
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!box.contains(mouseX, mouseY) || maxScroll() == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * ROW_H));
        return true;
    }

    boolean keyPressed(int keyCode) {
        if (edit == null) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
        } else {
            edit.keyPressed(keyCode, 0, 0);
        }
        return true;
    }

    boolean charTyped(char c, int modifiers) {
        return edit != null && edit.charTyped(c, modifiers);
    }

    /** As dicas dos botões ✎ e ✕ das linhas do jogador. */
    @Nullable Component tooltip(double mouseX, double mouseY) {
        int i = rowAt(mouseX, mouseY);
        if (i < 0 || !rows.get(i).mine) {
            return null;
        }
        int right = box.x() + 1 + rowWidth();
        return mouseX >= right - BTN ? removeTip : mouseX >= right - 2 * BTN ? renameTip : null;
    }

    /**
     * O componente pronto com os textos de exemplo no idioma de quem insere: o dado guarda "Rótulo" e "Título" em
     * português (ver {@link TvTemplates#LABEL_TEXT}), e aqui os dois viram o texto do lang.
     */
    static TvComponent localized(TvComponent component) {
        List<TvPiece> out = new ArrayList<>(component.pieces().size());
        for (TvPiece piece : component.pieces()) {
            String text = piece.style().text();
            if (piece.type() == TvPieceType.TEXT && text.equals(TvTemplates.LABEL_TEXT)) {
                piece = piece.withStyle(piece.style().withText(
                        Component.translatable("gui.wirelessautomate.tv.component.number_card.label").getString()));
            } else if (piece.type() == TvPieceType.TEXT && text.equals(TvTemplates.TITLE_TEXT)) {
                piece = piece.withStyle(piece.style().withText(
                        Component.translatable("gui.wirelessautomate.tv.component.title.text").getString()));
            }
            out.add(piece);
        }
        return new TvComponent(component.name(), out);
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** O centro da linha do componente de nome guardado {@code name} (rolada até aparecer), ou {@code null}. */
    int @Nullable [] rowCenter(String name) {
        rebuild();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).component.name().equals(name)) {
                int y = i * ROW_H;
                if (y < scroll || y + ROW_H > scroll + viewHeight()) {
                    scroll = Math.max(0, Math.min(maxScroll(), y - 2));
                }
                return new int[] {box.x() + 1 + (rowWidth() - 2 * BTN) / 2, top() + y - scroll + ROW_H / 2};
            }
        }
        return null;
    }

    /** O centro do ✎ ({@code remove} falso) ou do ✕ da linha de {@code name} (do jogador), ou {@code null}. */
    int @Nullable [] buttonCenter(String name, boolean remove) {
        int[] at = rowCenter(name);
        if (at == null) {
            return null;
        }
        int right = box.x() + 1 + rowWidth();
        return new int[] {right - (remove ? BTN / 2 : BTN + BTN / 2), at[1]};
    }
}
