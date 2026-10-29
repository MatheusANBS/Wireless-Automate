package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvArtThumbs;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Pixel art da coluna da esquerda do editor: as artes da biblioteca do jogador ({@link TvLibraryClient}) numa
 * lista que rola, com a miniatura (a textura da arte, {@link TvArtThumbs}), o nome e o tamanho, e o uso {@code n /
 * tv.maxArts} em cima. Um clique escolhe e dois abrem o editor de pixel art; embaixo, Nova (16 × 16 com a paleta
 * padrão; desligada com a biblioteca cheia), Editar, Duplicar e Remover. As linhas e os textos são refeitos quando a
 * biblioteca ou a janela mudam, nunca por quadro; só as linhas à vista são desenhadas.
 */
final class TvArtsPanel {
    /** Um botão embaixo da lista. */
    enum Button { NEW, EDIT, DUPLICATE, REMOVE }

    private static final int ROW_H = TvIcons.SIZE + 2;
    private static final int THUMB = TvIcons.SIZE;
    private static final int BUTTON_H = 14;
    private static final int HEAD_H = 12;
    private static final long DOUBLE_CLICK_MS = 400;

    /** Uma linha: a arte, o nome medido e o tamanho. */
    private record Row(TvPixelArt art, GuiText.Fitted name, Component size, int sizeWidth) {
    }

    private final TvEditorActions actions;
    private final TvPixelEditor pixel;
    private final TvRect[] buttons = new TvRect[Button.values().length];
    private final TvEditorPaint.Label[] labels = new TvEditorPaint.Label[Button.values().length];
    private final Component empty = TvEditorText.key("arts.empty");
    private Font font;
    private TvRect box = TvRect.NONE;
    private TvRect list = TvRect.NONE;
    private GuiText.Fitted title;
    private List<Row> rows = List.of();
    private int builtLibrary = -1;
    private Component count = Component.empty();
    private int countWidth;
    private int scroll;
    private @Nullable UUID chosen;
    private long lastClickAt;

    TvArtsPanel(TvEditorActions actions, TvPixelEditor pixel) {
        this.actions = actions;
        this.pixel = pixel;
    }

    void layout(Font font, TvRect box) {
        this.font = font;
        this.box = box;
        int half = (box.w() - 2) / 2;
        int bottom = box.bottom();
        buttons[Button.NEW.ordinal()] = new TvRect(box.x(), bottom - 2 * BUTTON_H - 2, half, BUTTON_H);
        buttons[Button.EDIT.ordinal()] = new TvRect(box.right() - half, bottom - 2 * BUTTON_H - 2, half, BUTTON_H);
        buttons[Button.DUPLICATE.ordinal()] = new TvRect(box.x(), bottom - BUTTON_H, half, BUTTON_H);
        buttons[Button.REMOVE.ordinal()] = new TvRect(box.right() - half, bottom - BUTTON_H, half, BUTTON_H);
        for (Button button : Button.values()) {
            labels[button.ordinal()] = TvEditorPaint.label(font, TvEditorText.key("arts." + button.name()
                    .toLowerCase(Locale.ROOT)), half - 4);
        }
        list = new TvRect(box.x(), box.y() + HEAD_H, box.w(), Math.max(0, bottom - 2 * BUTTON_H - 5 - box.y() - HEAD_H));
        builtLibrary = -1;
    }

    private void rebuild() {
        if (builtLibrary == TvLibraryClient.version()) {
            return;
        }
        builtLibrary = TvLibraryClient.version();
        List<TvPixelArt> arts = TvLibraryClient.arts();
        List<Row> out = new ArrayList<>(arts.size());
        for (TvPixelArt art : arts) {
            Component size = TvEditorText.key("arts.size", art.width(), art.height());
            int sizeWidth = font.width(size);
            out.add(new Row(art, GuiText.fit(font, Component.literal(art.name()), list.w() - THUMB - 14 - sizeWidth),
                    size, sizeWidth));
        }
        rows = out;
        count = TvEditorText.key("arts.count", arts.size(), actions.maxArts());
        countWidth = font.width(count);
        title = GuiText.fit(font, TvEditorText.key("arts.title"), box.w() - countWidth - 6);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        if (chosen != null && arts.stream().noneMatch(a -> a.id().equals(chosen))) {
            chosen = null;
        }
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * ROW_H - (list.h() - 4));
    }

    private @Nullable TvPixelArt chosenArt() {
        for (Row row : rows) {
            if (row.art.id().equals(chosen)) {
                return row.art;
            }
        }
        return null;
    }

    private boolean enabled(Button button) {
        return switch (button) {
            case NEW -> rows.size() < actions.maxArts();
            case EDIT -> chosenArt() != null;
            case DUPLICATE, REMOVE -> false;
        };
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        rebuild();
        GuiText.draw(g, font, title, box.x() + 1, box.y() + 1, GuiPaint.MUTED);
        GuiPaint.text(g, font, count, box.right() - countWidth - 1, box.y() + 1, GuiPaint.MUTED);
        GuiPaint.inset(g, list.x(), list.y(), list.w(), list.h());
        int top = list.y() + 2;
        int bottom = list.bottom() - 2;
        g.enableScissor(list.x() + 1, top, list.right() - 1, bottom);
        int w = list.w() - 2 - (maxScroll() > 0 ? 3 : 0);
        for (int i = 0; i < rows.size(); i++) {
            int y = top + i * ROW_H - scroll;
            if (y + ROW_H <= top || y >= bottom) {
                continue;
            }
            Row row = rows.get(i);
            int x = list.x() + 1;
            boolean selected = row.art.id().equals(chosen);
            if (selected) {
                g.fill(x, y, x + w, y + ROW_H, GuiPaint.alpha(GuiPaint.ACCENT, 0x40));
                g.fill(x, y, x + 2, y + ROW_H, GuiPaint.ACCENT);
            } else if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H && mouseY >= top
                    && mouseY < bottom) {
                g.fill(x, y, x + w, y + ROW_H, GuiPaint.alpha(GuiPaint.ACCENT, 0x20));
            }
            thumb(g, row.art, x + 4, y + 1);
            GuiText.draw(g, font, row.name, x + THUMB + 8, y + 5, GuiPaint.FG);
            GuiPaint.text(g, font, row.size, x + w - row.sizeWidth - 3, y + 5, GuiPaint.MUTED);
        }
        g.disableScissor();
        if (rows.isEmpty()) {
            GuiText.wrapCentered(g, font, empty, list.centerX(), list.y() + 8, list.w() - 8,
                    4, GuiPaint.MUTED);
        }
        for (Button button : Button.values()) {
            TvRect r = buttons[button.ordinal()];
            TvEditorPaint.button(g, font, r, labels[button.ordinal()], r.contains(mouseX, mouseY), enabled(button));
        }
    }

    /** A miniatura na maior escala que cabe em {@link #THUMB} px (inteira quando a arte é menor), centrada. */
    private static void thumb(GuiGraphics g, TvPixelArt art, int x, int y) {
        int side = Math.max(art.width(), art.height());
        float scale = side <= THUMB ? THUMB / side : THUMB / (float) side;
        int w = Math.max(1, Math.round(art.width() * scale));
        int h = Math.max(1, Math.round(art.height() * scale));
        GuiPaint.outline(g, x - 1, y - 1, THUMB + 2, THUMB + 2, GuiPaint.BUTTON_BORDER);
        TvArtThumbs.draw(g, art, x + (THUMB - w) / 2, y + (THUMB - h) / 2, w, h);
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!list.contains(mouseX, mouseY) || mouseY < list.y() + 2 || mouseY >= list.bottom() - 2) {
            return -1;
        }
        int i = (int) (mouseY - list.y() - 2 + scroll) / ROW_H;
        return i >= 0 && i < rows.size() ? i : -1;
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!box.contains(mouseX, mouseY)) {
            return false;
        }
        rebuild();
        int i = rowAt(mouseX, mouseY);
        if (i >= 0) {
            UUID id = rows.get(i).art.id();
            long now = System.currentTimeMillis();
            boolean twice = id.equals(chosen) && now - lastClickAt < DOUBLE_CLICK_MS;
            chosen = id;
            lastClickAt = now;
            if (twice) {
                pixel.open(rows.get(i).art);
            }
            return true;
        }
        for (Button button : Button.values()) {
            if (buttons[button.ordinal()].contains(mouseX, mouseY) && enabled(button)) {
                if (button == Button.NEW) {
                    pixel.open(null);
                } else if (button == Button.EDIT) {
                    pixel.open(chosenArt());
                }
            }
        }
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!list.contains(mouseX, mouseY) || maxScroll() == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * ROW_H));
        return true;
    }

    // ------------------------------------------------------------------ ganchos do e2e

    int[] buttonCenter(Button button) {
        return buttons[button.ordinal()].center();
    }

    /** O centro da linha da arte de nome {@code name} (rolada até aparecer), ou {@code null}. */
    int @Nullable [] rowCenter(String name) {
        rebuild();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).art.name().equals(name)) {
                int y = i * ROW_H;
                int view = list.h() - 4;
                if (y < scroll || y + ROW_H > scroll + view) {
                    scroll = Math.max(0, Math.min(maxScroll(), y - 2));
                }
                return new int[] {list.centerX(), list.y() + 2 + y - scroll + ROW_H / 2};
            }
        }
        return null;
    }
}
