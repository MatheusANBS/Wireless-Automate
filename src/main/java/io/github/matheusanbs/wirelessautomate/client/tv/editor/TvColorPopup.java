package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * O seletor de cor: uma janela com as seis cores do tema (marcadas "do tema"), o quadrado de saturação e brilho, a
 * faixa de matiz, a de alfa, o campo hexadecimal, o conta-gotas (o próximo clique na miniatura pega a cor do pixel) e
 * as últimas cores usadas ({@link TvRecentColors}). O quadrado é pintado por colunas de degradê vertical (o brilho é
 * linear, então cada coluna vai da cor cheia ao preto), sem textura; as cores das colunas só são refeitas quando o
 * matiz muda. Só existe aberta; cada mudança vai ao {@code set} na hora, e ao fechar a cor fixa escolhida entra nas
 * recentes.
 */
final class TvColorPopup implements TvPopup {
    static final int W = 124;
    static final int H = 134;
    private static final int PAD = 6;
    private static final int SV_W = 84;
    private static final int SV_H = 56;
    private static final int SV_Y = 32;
    private static final int STRIP = 8;
    private static final int COLUMN = 2;
    private static final int ROW2_Y = 94;
    private static final int EYE_Y = ROW2_Y - 2;
    private static final int THEME_Y = 14;
    private static final int SWATCH = 14;
    private static final int RECENT_Y = 119;
    private static final int RECENT = 11;

    private enum Drag { NONE, SV, HUE, ALPHA }

    private final Font font;
    private final int x;
    private final int y;
    private final boolean allowNone;
    private final boolean alpha;
    private final int[] themeColors = new int[6];
    private final IntConsumer set;
    private final int initial;
    private final TvTextField hex;
    private final int[] columns = new int[SV_W / COLUMN];
    private final GuiText.Fitted themeLabel;
    private final GuiText.Fitted recentLabel;
    private final Component eyeTip;
    private float columnsHue = -1;
    private int value;
    private float h;
    private float s;
    private float v = 1;
    private int a = 255;
    private Drag drag = Drag.NONE;
    private boolean picking;

    TvColorPopup(Font font, TvRect anchor, TvRect bounds, int value, boolean allowNone, boolean alpha, TvTheme theme,
            IntConsumer set) {
        this.font = font;
        this.allowNone = allowNone;
        this.alpha = alpha;
        this.set = set;
        this.initial = value;
        int left = anchor.x() - W - 4;
        if (left < bounds.x()) {
            left = anchor.right() + 4;
        }
        this.x = Mth.clamp(left, bounds.x(), Math.max(bounds.x(), bounds.right() - W));
        this.y = Mth.clamp(anchor.y(), bounds.y(), Math.max(bounds.y(), bounds.bottom() - H));
        for (int i = 0; i < 6; i++) {
            themeColors[i] = theme.color(i + 1) | 0xFF000000;
        }
        take(value);
        hex = new TvTextField(font, "color.hex", PAD, ROW2_Y, 70, 9, text -> text.matches("#?[0-9a-fA-F]*"),
                Component.literal("#RRGGBB"), () -> TvColor.hex(this.value), this::typed);
        hex.place(x, y);
        themeLabel = GuiText.fit(font, TvEditorText.key("color.from_theme"), W - 2 * PAD);
        recentLabel = GuiText.fit(font, TvEditorText.key("color.recent"), W - 2 * PAD);
        eyeTip = TvEditorText.key("color.eyedropper");
    }

    /** Lê o matiz, a saturação, o brilho e o alfa de um valor guardado (papel do tema, 0 ou ARGB). */
    private void take(int stored) {
        value = stored;
        int argb = TvColor.isTheme(stored) ? themeColors[stored - 1] : stored;
        if (argb == 0) {
            return;
        }
        float[] hsv = TvHsv.fromRgb(argb & 0xFFFFFF);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
        a = alpha ? Math.max(1, argb >>> 24) : 255;
    }

    private void typed(String text) {
        Integer parsed = TvColor.fromHex(text);
        if (parsed != null && (parsed != 0 || allowNone)) {
            take(parsed);
            set.accept(value);
        }
    }

    private void applyHsv() {
        value = a << 24 | TvHsv.toRgb(h, s, v);
        set.accept(value);
        hex.refresh();
    }

    private void applyColor(int argb) {
        if (argb >>> 24 == 0) {
            return;
        }
        take(alpha ? argb : argb | 0xFF000000);
        set.accept(value);
        hex.refresh();
    }

    // ------------------------------------------------------------------ geometria

    private int svX() {
        return x + PAD;
    }

    private int svY() {
        return y + SV_Y;
    }

    private int hueX() {
        return svX() + SV_W + 4;
    }

    private int alphaX() {
        return hueX() + STRIP + 4;
    }

    private boolean in(double mx, double my, int left, int top, int w, int h) {
        return mx >= left && mx < left + w && my >= top && my < top + h;
    }

    private int eyeX() {
        return x + PAD + 74;
    }

    private int noneX() {
        return x + PAD + 92;
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public boolean contains(double mouseX, double mouseY) {
        return in(mouseX, mouseY, x, y, W, H);
    }

    private void refreshColumns() {
        if (columnsHue == h) {
            return;
        }
        columnsHue = h;
        for (int i = 0; i < columns.length; i++) {
            columns[i] = 0xFF000000 | TvHsv.toRgb(h, i / (float) (columns.length - 1), 1);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        refreshColumns();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        GuiPaint.box(g, x, y, W, H, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
        GuiText.draw(g, font, themeLabel, x + PAD, y + 4, GuiPaint.MUTED);
        for (int i = 0; i < 6; i++) {
            int left = x + PAD + i * (SWATCH + 3);
            fillSwatch(g, left, y + THEME_Y, SWATCH, 11, themeColors[i], value == i + 1, mouseX, mouseY);
        }
        int svX = svX();
        int svY = svY();
        for (int i = 0; i < columns.length; i++) {
            g.fillGradient(svX + i * COLUMN, svY, svX + (i + 1) * COLUMN, svY + SV_H, columns[i], 0xFF000000);
        }
        int markX = svX + Math.round(s * (SV_W - 1));
        int markY = svY + Math.round((1 - v) * (SV_H - 1));
        GuiPaint.outline(g, markX - 2, markY - 2, 5, 5, 0xFFFFFFFF);
        GuiPaint.outline(g, markX - 3, markY - 3, 7, 7, 0xFF000000);
        hueStrip(g);
        if (alpha) {
            alphaStrip(g);
        }
        hex.render(g, mouseX, mouseY);
        boolean eyeOver = in(mouseX, mouseY, eyeX(), y + EYE_Y, TvIcons.SIZE, TvIcons.SIZE);
        GuiPaint.box(g, eyeX(), y + EYE_Y, TvIcons.SIZE, TvIcons.SIZE, picking ? GuiPaint.SELECTED : GuiPaint.BUTTON,
                picking ? GuiPaint.ACCENT : eyeOver ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        TvIcons.draw(g, TvIcons.Icon.EYEDROPPER, eyeX(), y + EYE_Y);
        if (allowNone) {
            fillSwatch(g, noneX(), y + ROW2_Y, 11, 11, 0, value == 0, mouseX, mouseY);
        }
        if (TvRecentColors.size() > 0) {
            GuiText.draw(g, font, recentLabel, x + PAD, y + RECENT_Y - 10, GuiPaint.MUTED);
        }
        for (int i = 0; i < TvRecentColors.size(); i++) {
            int color = TvRecentColors.get(i);
            fillSwatch(g, x + PAD + i * (RECENT + 2), y + RECENT_Y, RECENT, RECENT, color, color == value, mouseX,
                    mouseY);
        }
        g.pose().popPose();
    }

    private void hueStrip(GuiGraphics g) {
        int left = hueX();
        int top = svY();
        for (int k = 0; k < 6; k++) {
            int from = top + Math.round(k * SV_H / 6f);
            int to = top + Math.round((k + 1) * SV_H / 6f);
            g.fillGradient(left, from, left + STRIP, to, 0xFF000000 | TvHsv.toRgb(k / 6f, 1, 1),
                    0xFF000000 | TvHsv.toRgb((k + 1) / 6f, 1, 1));
        }
        int at = top + Math.round(h * (SV_H - 1));
        g.fill(left - 1, at, left + STRIP + 1, at + 1, 0xFFFFFFFF);
        g.fill(left - 1, at + 1, left + STRIP + 1, at + 2, 0xFF000000);
    }

    private void alphaStrip(GuiGraphics g) {
        int left = alphaX();
        int top = svY();
        checker(g, left, top, STRIP, SV_H);
        int rgb = 0xFF000000 | TvHsv.toRgb(h, s, v);
        g.fillGradient(left, top, left + STRIP, top + SV_H, rgb, rgb & 0xFFFFFF);
        int at = top + Math.round((1 - a / 255f) * (SV_H - 1));
        g.fill(left - 1, at, left + STRIP + 1, at + 1, 0xFFFFFFFF);
        g.fill(left - 1, at + 1, left + STRIP + 1, at + 2, 0xFF000000);
    }

    private static void checker(GuiGraphics g, int left, int top, int w, int h) {
        g.fill(left, top, left + w, top + h, 0xFFFFFFFF);
        for (int row = 0; row * 4 < h; row++) {
            for (int col = row & 1; col * 4 < w; col += 2) {
                g.fill(left + col * 4, top + row * 4, Math.min(left + w, left + col * 4 + 4),
                        Math.min(top + h, top + row * 4 + 4), 0xFFB5B0A6);
            }
        }
    }

    /** Uma amostra: o xadrez com a cor por cima (0 é só o xadrez com uma risca), a borda coral se for a atual. */
    private void fillSwatch(GuiGraphics g, int left, int top, int w, int h, int color, boolean on, int mouseX,
            int mouseY) {
        checker(g, left + 1, top + 1, w - 2, h - 2);
        if (color != 0) {
            g.fill(left + 1, top + 1, left + w - 1, top + h - 1, color);
        } else {
            for (int i = 1; i < Math.min(w, h) - 1; i++) {
                g.fill(left + i, top + i, left + i + 1, top + i + 1, 0xFFD04A4A);
            }
        }
        GuiPaint.outline(g, left, top, w, h, on ? GuiPaint.ACCENT
                : in(mouseX, mouseY, left, top, w, h) ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
    }

    // ------------------------------------------------------------------ mouse

    @Override
    public void mouseClicked(double mx, double my, int button) {
        if (hex.contains(mx, my)) {
            hex.mouseClicked(mx, my, button);
            return;
        }
        hex.blur();
        for (int i = 0; i < 6; i++) {
            if (in(mx, my, x + PAD + i * (SWATCH + 3), y + THEME_Y, SWATCH, 11)) {
                value = i + 1;
                set.accept(value);
                hex.refresh();
                return;
            }
        }
        if (in(mx, my, svX(), svY(), SV_W, SV_H)) {
            drag = Drag.SV;
            dragTo(mx, my);
        } else if (in(mx, my, hueX(), svY(), STRIP, SV_H)) {
            drag = Drag.HUE;
            dragTo(mx, my);
        } else if (alpha && in(mx, my, alphaX(), svY(), STRIP, SV_H)) {
            drag = Drag.ALPHA;
            dragTo(mx, my);
        } else if (in(mx, my, eyeX(), y + EYE_Y, TvIcons.SIZE, TvIcons.SIZE)) {
            picking = !picking;
        } else if (allowNone && in(mx, my, noneX(), y + ROW2_Y, 11, 11)) {
            value = 0;
            set.accept(0);
            hex.refresh();
        } else {
            for (int i = 0; i < TvRecentColors.size(); i++) {
                if (in(mx, my, x + PAD + i * (RECENT + 2), y + RECENT_Y, RECENT, RECENT)) {
                    applyColor(TvRecentColors.get(i));
                    return;
                }
            }
        }
    }

    private void dragTo(double mx, double my) {
        switch (drag) {
            case SV -> {
                s = (float) Mth.clamp((mx - svX()) / (SV_W - 1), 0, 1);
                v = 1 - (float) Mth.clamp((my - svY()) / (SV_H - 1), 0, 1);
            }
            case HUE -> h = (float) Mth.clamp((my - svY()) / (SV_H - 1), 0, 0.9999);
            case ALPHA -> a = Mth.clamp(Math.round((1 - (float) Mth.clamp((my - svY()) / (SV_H - 1), 0, 1)) * 255), 1,
                    255);
            default -> {
                return;
            }
        }
        applyHsv();
    }

    @Override
    public boolean mouseDragged(double mx, double my) {
        if (drag == Drag.NONE) {
            return false;
        }
        dragTo(mx, my);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my) {
        boolean was = drag != Drag.NONE;
        drag = Drag.NONE;
        return was;
    }

    /** O conta-gotas está esperando um clique na miniatura. */
    boolean picking() {
        return picking;
    }

    /** A cor do pixel pego na miniatura (ARGB). */
    void picked(int argb) {
        picking = false;
        applyColor(argb);
    }

    // ------------------------------------------------------------------ teclado e dicas

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return hex.wantsKeys() && hex.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return hex.wantsKeys() && hex.charTyped(c, modifiers);
    }

    @Override
    public boolean wantsKeys() {
        return hex.wantsKeys();
    }

    @Override
    public @Nullable Component tooltip(double mx, double my) {
        return in(mx, my, eyeX(), y + EYE_Y, TvIcons.SIZE, TvIcons.SIZE) ? eyeTip : null;
    }

    @Override
    public void closed() {
        hex.blur();
        if (value != initial) {
            TvRecentColors.push(value);
        }
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** O centro da amostra do papel {@code role} (1 a 6) do tema. */
    int[] themeCenter(int role) {
        return new int[] {x + PAD + (role - 1) * (SWATCH + 3) + SWATCH / 2, y + THEME_Y + 5};
    }

    int[] hexCenter() {
        return new int[] {hex.centerX(), hex.centerY()};
    }

    TvTextField hex() {
        return hex;
    }

    int[] svCenter() {
        return new int[] {svX() + SV_W * 3 / 4, svY() + SV_H / 4};
    }

    int[] eyeCenter() {
        return new int[] {eyeX() + TvIcons.SIZE / 2, y + EYE_Y + TvIcons.SIZE / 2};
    }

    int value() {
        return value;
    }
}
