package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A barra de botões embaixo da miniatura: o desfazer e o refazer (ícones) na ponta esquerda, Agrupar, Desagrupar,
 * Salvar como componente, Duplicar e Remover, e Salvar na ponta direita, na cor de destaque enquanto há mudança não salva. Quando os
 * nomes não cabem (janela estreita), os cinco viram símbolos com o nome na dica. Medida e disposta só no
 * {@link #layout}.
 */
final class TvEditorBar {
    /** O que cada botão faz, na ordem da barra. */
    enum Action { UNDO, REDO, GROUP, UNGROUP, SAVE_COMPONENT, DUPLICATE, REMOVE, SAVE }

    private static final List<Action> TOOLS = List.of(Action.GROUP, Action.UNGROUP, Action.SAVE_COMPONENT, Action.DUPLICATE,
            Action.REMOVE);
    private static final String[] KEYS = {"undo", "redo", "group_selection", "ungroup", "save_component", "duplicate",
        "remove", "save"};
    /** O ícone de cada botão da barra, na ordem do {@link Action} (o Salvar é só texto). */
    private static final TvIcons.Icon[] ICONS = {TvIcons.Icon.UNDO, TvIcons.Icon.REDO, TvIcons.Icon.GROUP,
        TvIcons.Icon.UNGROUP, TvIcons.Icon.SAVE_COMPONENT, TvIcons.Icon.DUPLICATE, TvIcons.Icon.REMOVE};
    /** O lugar dos dois botões do desfazer e do refazer na ponta esquerda. */
    private static final int RESERVED = 34;
    private static final int HISTORY_W = 16;
    private static final int GAP = 2;
    private static final int SYMBOL_W = 16;

    private final TvEditorDoc doc;
    private final TvRect[] rects = new TvRect[Action.values().length];
    private final TvEditorPaint.Label[] labels = new TvEditorPaint.Label[Action.values().length];
    private final Component[] names = new Component[Action.values().length];
    private Font font;
    private boolean symbols;

    TvEditorBar(TvEditorDoc doc) {
        this.doc = doc;
    }

    void layout(Font font, TvRect bar) {
        this.font = font;
        for (Action action : Action.values()) {
            names[action.ordinal()] = TvEditorText.key(KEYS[action.ordinal()]);
        }
        int saveW = font.width(names[Action.SAVE.ordinal()]) + 12;
        int textW = 0;
        for (Action tool : TOOLS) {
            textW += font.width(names[tool.ordinal()]) + 10 + GAP;
        }
        symbols = textW + saveW + RESERVED > bar.w();
        for (int k = 0; k < 2; k++) {
            Action history = Action.values()[k];
            rects[history.ordinal()] = new TvRect(bar.x() + k * (HISTORY_W + GAP), bar.y(), HISTORY_W, bar.h());
        }
        int right = bar.right();
        rects[Action.SAVE.ordinal()] = new TvRect(right - saveW, bar.y(), saveW, bar.h());
        labels[Action.SAVE.ordinal()] = TvEditorPaint.label(font, names[Action.SAVE.ordinal()], saveW - 4);
        right -= saveW + GAP * 2;
        for (int k = TOOLS.size() - 1; k >= 0; k--) {
            Action tool = TOOLS.get(k);
            int w = symbols ? SYMBOL_W : font.width(names[tool.ordinal()]) + 10;
            rects[tool.ordinal()] = new TvRect(right - w, bar.y(), w, bar.h());
            labels[tool.ordinal()] = TvEditorPaint.label(font, names[tool.ordinal()], w - 4);
            right -= w + GAP;
        }
    }

    private boolean enabled(Action action) {
        return switch (action) {
            case UNDO -> doc.model().history().canUndo();
            case REDO -> doc.model().history().canRedo();
            case GROUP -> doc.canGroup();
            case UNGROUP -> doc.canUngroup();
            case SAVE_COMPONENT, DUPLICATE, REMOVE -> doc.hasSelection();
            case SAVE -> doc.model().dirty();
        };
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        for (int k = 0; k < 2; k++) {
            Action history = Action.values()[k];
            TvRect r = rects[history.ordinal()];
            TvEditorPaint.iconButton(g, r, ICONS[history.ordinal()], r.contains(mouseX, mouseY), enabled(history));
        }
        for (int k = 0; k < TOOLS.size(); k++) {
            Action action = TOOLS.get(k);
            TvRect r = rects[action.ordinal()];
            if (symbols) {
                TvEditorPaint.iconButton(g, r, ICONS[action.ordinal()], r.contains(mouseX, mouseY), enabled(action));
            } else {
                TvEditorPaint.button(g, font, r, labels[action.ordinal()], r.contains(mouseX, mouseY), enabled(action));
            }
        }
        TvRect save = rects[Action.SAVE.ordinal()];
        TvEditorPaint.primary(g, font, save, labels[Action.SAVE.ordinal()], save.contains(mouseX, mouseY),
                enabled(Action.SAVE), GuiPaint.ACCENT);
    }

    /** O botão sob o mouse que vale agora, ou {@code null}. */
    @Nullable Action clicked(double mouseX, double mouseY) {
        for (Action action : Action.values()) {
            if (rects[action.ordinal()].contains(mouseX, mouseY)) {
                return enabled(action) ? action : null;
            }
        }
        return null;
    }

    boolean contains(double mouseX, double mouseY) {
        for (TvRect r : rects) {
            if (r.contains(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /** O nome do botão sob o mouse: o desfazer e o refazer sempre, os outros quando a barra está em símbolos. */
    @Nullable Component tooltip(double mouseX, double mouseY) {
        for (int k = 0; k < 2; k++) {
            if (rects[k].contains(mouseX, mouseY)) {
                return names[k];
            }
        }
        if (!symbols) {
            return null;
        }
        for (int k = 0; k < TOOLS.size(); k++) {
            Action tool = TOOLS.get(k);
            if (rects[tool.ordinal()].contains(mouseX, mouseY)) {
                return names[tool.ordinal()];
            }
        }
        return null;
    }

    int[] center(Action action) {
        return rects[action.ordinal()].center();
    }
}
