package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
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
 * A parte Pixel art da aba Artes da coluna da esquerda do editor ({@link TvLibraryTab}): as artes da biblioteca do
 * jogador ({@link TvLibraryClient}) numa lista que rola, com a miniatura (a textura da arte, {@link TvArtThumbs}), o
 * nome e o tamanho, e o uso {@code n / tv.maxArts} em cima. Um clique escolhe, dois abrem o editor de pixel art e
 * arrastar uma linha até a miniatura cria a peça Imagem ({@link TvArtDrag}, o mesmo da parte Imagens); embaixo, Nova (16 × 16 com a paleta padrão) e Duplicar (uma cópia "… (cópia)" com
 * id novo), desligadas com a biblioteca cheia, Editar e Remover (dois cliques), e Exportar, Importar e Copiar como
 * texto por código ({@link TvArtCode}). Duplicar, Remover e Importar saem pelo {@link TvEditorActions}. As linhas e
 * os textos são refeitos quando a biblioteca ou a janela mudam, nunca por quadro; só as linhas à vista são
 * desenhadas.
 */
final class TvArtsPanel {
    /** Um botão embaixo da lista. */
    enum Button { NEW, EDIT, DUPLICATE, REMOVE, EXPORT, IMPORT, TEXT }

    private static final int ROW_H = TvIcons.SIZE + 2;
    private static final int THUMB = TvIcons.SIZE;
    private static final int BUTTON_H = 14;
    private static final int BUTTON_ROWS = 4;
    private static final int HEAD_H = 12;
    private static final long DOUBLE_CLICK_MS = 400;

    /** Uma linha: a arte, o nome medido e, embaixo dele, o tamanho. */
    private record Row(TvPixelArt art, GuiText.Fitted name, Component size) {
    }

    private final TvEditorActions actions;
    private final TvPixelEditor pixel;
    private final TvArtDrag drag;
    private final ArmedClick removeClick = new ArmedClick();
    private TvEditorPaint.Label removeConfirm;
    private final TvRect[] buttons = new TvRect[Button.values().length];
    private final TvEditorPaint.Label[] labels = new TvEditorPaint.Label[Button.values().length];
    private final Component empty = TvEditorText.key("arts.empty");
    private final Component exportTip = TvEditorText.key("arts.export.tip");
    private final Component importTip = TvEditorText.key("arts.import.tip");
    private final Component textTip = TvEditorText.key("arts.text.tip");
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

    TvArtsPanel(TvEditorActions actions, TvPixelEditor pixel, TvArtDrag drag) {
        this.actions = actions;
        this.pixel = pixel;
        this.drag = drag;
    }

    void layout(Font font, TvRect box) {
        this.font = font;
        this.box = box;
        int half = (box.w() - 2) / 2;
        int bottom = box.bottom();
        int buttonsTop = bottom - BUTTON_ROWS * (BUTTON_H + 2) + 2;
        for (Button button : Button.values()) {
            int row = button.ordinal() / 2;
            int w = button == Button.TEXT ? box.w() : half;
            buttons[button.ordinal()] = new TvRect(button.ordinal() % 2 == 0 ? box.x() : box.right() - half,
                    buttonsTop + row * (BUTTON_H + 2), w, BUTTON_H);
            labels[button.ordinal()] = TvEditorPaint.label(font, TvEditorText.key("arts." + button.name()
                    .toLowerCase(Locale.ROOT)), w - 4);
        }
        removeConfirm = TvEditorPaint.label(font, TvEditorText.key("arts.remove.confirm"), half - 4);
        list = new TvRect(box.x(), box.y() + HEAD_H, box.w(), Math.max(0, buttonsTop - 3 - box.y() - HEAD_H));
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
            out.add(new Row(art, GuiText.fit(font, Component.literal(art.name()), list.w() - THUMB - 14), size));
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

    /** A arte escolhida na lista, ou {@code null}: a Imagem que a paleta de Peças cria nasce com ela. */
    @Nullable TvPixelArt chosenArt() {
        if (box != TvRect.NONE) {
            rebuild();
        }
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
            case DUPLICATE -> chosenArt() != null && rows.size() < actions.maxArts();
            case EDIT, REMOVE, EXPORT, TEXT -> chosenArt() != null;
            case IMPORT -> true;
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
        TvArtThumbs.begin(g);
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
            GuiText.draw(g, font, row.name, x + THUMB + 8, y + 1, GuiPaint.FG);
            GuiPaint.text(g, font, row.size, x + THUMB + 8, y + 10, GuiPaint.MUTED);
        }
        TvArtThumbs.end(g);
        g.disableScissor();
        if (rows.isEmpty()) {
            GuiText.wrapCentered(g, font, empty, list.centerX(), list.y() + 8, list.w() - 8,
                    4, GuiPaint.MUTED);
        }
        for (Button button : Button.values()) {
            TvRect r = buttons[button.ordinal()];
            if (button == Button.REMOVE && removeClick.armed(System.currentTimeMillis()) && enabled(button)) {
                TvEditorPaint.danger(g, font, r, removeConfirm);
            } else {
                TvEditorPaint.button(g, font, r, labels[button.ordinal()], r.contains(mouseX, mouseY),
                        enabled(button));
            }
        }
    }

    /** A miniatura na maior escala que cabe em {@link #THUMB} px (inteira quando a arte é menor), centrada. */
    private static void thumb(GuiGraphics g, TvPixelArt art, int x, int y) {
        GuiPaint.outline(g, x - 1, y - 1, THUMB + 2, THUMB + 2, GuiPaint.BUTTON_BORDER);
        TvArtThumbs.drawFitted(g, art, x, y, THUMB);
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
            TvPixelArt art = rows.get(i).art;
            long now = System.currentTimeMillis();
            boolean twice = art.id().equals(chosen) && now - lastClickAt < DOUBLE_CLICK_MS;
            if (!art.id().equals(chosen)) {
                removeClick.disarm();
            }
            chosen = art.id();
            lastClickAt = now;
            if (twice) {
                pixel.open(art);
            } else {
                drag.press(TvImageSource.of(art), mouseX, mouseY);
            }
            return true;
        }
        for (Button button : Button.values()) {
            if (buttons[button.ordinal()].contains(mouseX, mouseY) && enabled(button)) {
                run(button);
            }
        }
        return true;
    }

    private void run(Button button) {
        TvPixelArt art = chosenArt();
        if (button != Button.REMOVE) {
            removeClick.disarm();
        }
        switch (button) {
            case NEW -> pixel.open(null);
            case EDIT -> pixel.open(art);
            case DUPLICATE -> actions.saveArt(new TvPixelArt(UUID.randomUUID(), copyName(art.name()), art.width(),
                    art.height(), art.palette(), art.pixels()));
            case REMOVE -> {
                if (removeClick.click(System.currentTimeMillis())) {
                    actions.removeArt(art.id());
                }
            }
            case EXPORT -> TvArtCode.copy(art, false);
            case TEXT -> TvArtCode.copy(art, true);
            case IMPORT -> TvArtCode.paste(actions, rows.size());
        }
    }

    /** A dica de Exportar, Importar e Copiar como texto, que dizem o que fazem com o código. */
    @Nullable Component tooltip(double mouseX, double mouseY) {
        return buttons[Button.EXPORT.ordinal()].contains(mouseX, mouseY) ? exportTip
                : buttons[Button.IMPORT.ordinal()].contains(mouseX, mouseY) ? importTip
                : buttons[Button.TEXT.ordinal()].contains(mouseX, mouseY) ? textTip : null;
    }

    /** "Nome (cópia)" em até {@link TvPixelArt#MAX_NAME} caracteres: o nome é cortado para o sufixo caber. */
    private static String copyName(String name) {
        String suffix = TvEditorText.key("arts.copy_name", "").getString();
        int room = Math.max(1, TvPixelArt.MAX_NAME - suffix.codePointCount(0, suffix.length()));
        String base = name.codePointCount(0, name.length()) <= room ? name
                : name.substring(0, name.offsetByCodePoints(0, room)).strip();
        return TvEditorText.key("arts.copy_name", base).getString();
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
                return rowCenter(i);
            }
        }
        return null;
    }

    /** O centro da linha da arte de id {@code id} (rolada até aparecer), ou {@code null}. */
    int @Nullable [] rowCenter(UUID id) {
        rebuild();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).art.id().equals(id)) {
                return rowCenter(i);
            }
        }
        return null;
    }

    private int[] rowCenter(int i) {
        int y = i * ROW_H;
        int view = list.h() - 4;
        if (y < scroll || y + ROW_H > scroll + view) {
            scroll = Math.max(0, Math.min(maxScroll(), y - 2));
        }
        return new int[] {list.centerX(), list.y() + 2 + y - scroll + ROW_H / 2};
    }
}
