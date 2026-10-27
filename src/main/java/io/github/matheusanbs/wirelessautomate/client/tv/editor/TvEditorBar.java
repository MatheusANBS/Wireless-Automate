package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A barra de botões embaixo da miniatura: Agrupar, Desagrupar, Duplicar e Remover, e Salvar na ponta direita, na
 * cor de destaque enquanto há mudança não salva. A ponta esquerda fica livre para o desfazer e o refazer. Quando os
 * nomes não cabem (janela estreita), os quatro viram símbolos com o nome na dica. Medida e disposta só no
 * {@link #layout}.
 */
final class TvEditorBar {
    /** O que cada botão faz, na ordem da barra. */
    enum Action { GROUP, UNGROUP, DUPLICATE, REMOVE, SAVE }

    private static final List<Action> TOOLS = List.of(Action.GROUP, Action.UNGROUP, Action.DUPLICATE, Action.REMOVE);
    private static final String[] KEYS = {"group_selection", "ungroup", "duplicate", "remove", "save"};
    private static final String[] SYMBOLS = {"⊞", "⊟", "⧉", "✕"};
    /** O lugar reservado na ponta esquerda (↶ ↷). */
    private static final int RESERVED = 34;
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
        int right = bar.right();
        rects[Action.SAVE.ordinal()] = new TvRect(right - saveW, bar.y(), saveW, bar.h());
        labels[Action.SAVE.ordinal()] = TvEditorPaint.label(font, names[Action.SAVE.ordinal()], saveW - 4);
        right -= saveW + GAP * 2;
        for (int k = TOOLS.size() - 1; k >= 0; k--) {
            Action tool = TOOLS.get(k);
            Component shown = symbols ? Component.literal(SYMBOLS[k]) : names[tool.ordinal()];
            int w = symbols ? SYMBOL_W : font.width(shown) + 10;
            rects[tool.ordinal()] = new TvRect(right - w, bar.y(), w, bar.h());
            labels[tool.ordinal()] = TvEditorPaint.label(font, shown, w - 4);
            right -= w + GAP;
        }
    }

    private boolean enabled(Action action) {
        return switch (action) {
            case GROUP -> doc.canGroup();
            case UNGROUP -> doc.canUngroup();
            case DUPLICATE, REMOVE -> doc.hasSelection();
            case SAVE -> doc.model().dirty();
        };
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        for (int k = 0; k < TOOLS.size(); k++) {
            Action action = TOOLS.get(k);
            TvRect r = rects[action.ordinal()];
            TvEditorPaint.button(g, font, r, labels[action.ordinal()], r.contains(mouseX, mouseY), enabled(action));
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

    /** O nome do botão sob o mouse quando a barra está em símbolos, ou {@code null}. */
    @Nullable Component tooltip(double mouseX, double mouseY) {
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
