package io.github.matheusanbs.wirelessautomate.client.color;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O seletor de cor comum: uma janela com, se houver, as seis cores do tema (marcadas "do tema"), o quadrado de saturação
 * e brilho, a faixa de matiz, a de alfa, o campo hexadecimal, o conta-gotas (quando quem abre dá um), "nenhuma" e as
 * últimas cores usadas ({@link RecentColors}). O quadrado é pintado por colunas de degradê vertical (o brilho é linear,
 * então cada coluna vai da cor cheia ao preto), sem textura; as cores das colunas só são refeitas quando o matiz muda.
 * O valor mora no {@link ColorState}. Só existe aberta; cada mudança vai ao {@code set} na hora, e ao fechar a cor fixa
 * escolhida entra nas recentes. As medidas e o desenho vêm do {@link ColorLook}.
 */
public final class ColorPopup {
    /** O conta-gotas de quem abre: o ícone, o tamanho e a dica; o clique na miniatura quem trata é a tela. */
    public interface Eyedropper {
        void draw(GuiGraphics g, int x, int y);

        int size();

        Component tip();
    }

    /**
     * O que o seletor liga: {@code allowNone} (a cor pode faltar), {@code alpha} (a faixa de alfa), {@code themeColors}
     * (as seis cores ARGB do tema; nulo, sem a linha do tema) e o {@code eyedropper} (nulo, sem ele).
     */
    public record Options(ColorLook look, boolean allowNone, boolean alpha, int @Nullable [] themeColors,
            @Nullable Eyedropper eyedropper) {
    }

    private enum Drag { NONE, SV, HUE, ALPHA }

    private static final int COLUMN = 2;
    private static final int THEME_SWATCH_W = 14;
    private static final int THEME_SWATCH_H = 11;
    private static final int THEME_STEP = THEME_SWATCH_W + 3;
    private static final int NONE = 11;

    private final Font font;
    private final ColorLook look;
    private final Options options;
    private final int x;
    private final int y;
    private final boolean hasTheme;
    private final int svX;
    private final int svY;
    private final int hueX;
    private final int alphaX;
    private final int eyeX;
    private final int eyeY;
    private final int noneX;
    private final int noneY;
    private final int recentY;
    private final int height;
    private final ColorState state;
    private final int[] themeColors;
    private final IntConsumer set;
    private final int initial;
    private final HexField hex;
    private final int[] columns;
    private final GuiText.Fitted themeLabel;
    private final GuiText.Fitted recentLabel;
    private float columnsHue = -1;
    private Drag drag = Drag.NONE;
    private boolean picking;

    public ColorPopup(Font font, int x, int y, int value, Options options, IntConsumer set) {
        this.font = font;
        this.options = options;
        this.look = options.look();
        this.x = x;
        this.y = y;
        this.set = set;
        this.initial = value;
        this.hasTheme = options.themeColors() != null;
        int top = hasTheme ? look.themeRow() : 0;
        this.themeColors = hasTheme ? options.themeColors() : new int[6];
        this.state = new ColorState(value, options.alpha(), themeColors);
        this.svX = x + look.pad();
        this.svY = y + top + look.svY();
        this.hueX = svX + look.svW() + look.stripGap();
        this.alphaX = hueX + look.strip() + look.stripGap();
        this.eyeX = x + look.pad() + look.eyeDx();
        this.eyeY = y + top + look.eyeY();
        this.noneX = x + look.pad() + look.noneDx();
        this.noneY = y + top + look.hexY();
        this.recentY = y + top + look.recentY();
        this.height = height(options);
        this.columns = new int[look.svW() / COLUMN];
        this.hex = new HexField(font, x + look.pad(), y + top + look.hexY(), look.hexW(), look.hexH(), look.framed(),
                () -> TvColor.hex(state.value()), this::typed);
        this.themeLabel = GuiText.fit(font, Component.translatable("gui.wirelessautomate.color.from_theme"),
                look.width() - 2 * look.pad());
        this.recentLabel = GuiText.fit(font, Component.translatable("gui.wirelessautomate.color.recent"),
                look.width() - 2 * look.pad());
    }

    /** A altura da janela com essas opções, para quem a posiciona. */
    public static int height(Options options) {
        ColorLook look = options.look();
        return look.baseHeight() + (options.themeColors() != null ? look.themeRow() : 0);
    }

    private void typed(String text) {
        if (state.typed(text, options.allowNone())) {
            set.accept(state.value());
        }
    }

    private void changed() {
        set.accept(state.value());
        hex.refresh();
    }

    private static boolean in(double mx, double my, int left, int top, int w, int h) {
        return mx >= left && mx < left + w && my >= top && my < top + h;
    }

    private int eyeSize() {
        Eyedropper eye = options.eyedropper();
        return eye == null ? 0 : eye.size();
    }

    private int recentX(int i) {
        return x + look.pad() + i * look.recentStep();
    }

    private int themeX(int i) {
        return x + look.pad() + i * THEME_STEP;
    }

    // ------------------------------------------------------------------ desenho

    public boolean contains(double mouseX, double mouseY) {
        return in(mouseX, mouseY, x, y, look.width(), height);
    }

    private void refreshColumns() {
        if (columnsHue == state.h()) {
            return;
        }
        columnsHue = state.h();
        for (int i = 0; i < columns.length; i++) {
            columns[i] = 0xFF000000 | ColorHsv.toRgb(state.h(), i / (float) (columns.length - 1), 1);
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        refreshColumns();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        if (look.framed()) {
            PanelPaint.window(g, x, y, look.width(), height);
        } else {
            GuiPaint.box(g, x, y, look.width(), height, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
        }
        if (hasTheme) {
            GuiText.draw(g, font, themeLabel, x + look.pad(), y + 4, GuiPaint.MUTED);
            for (int i = 0; i < 6; i++) {
                fillSwatch(g, themeX(i), y + look.themeY(), THEME_SWATCH_W, THEME_SWATCH_H, themeColors[i],
                        state.value() == i + 1, mouseX, mouseY);
            }
        }
        renderSquare(g);
        ColorStrips.hue(g, hueX, svY, look.strip(), look.svH(), state.h(), look.framed());
        if (options.alpha()) {
            ColorStrips.alpha(g, alphaX, svY, look.strip(), look.svH(), state.rgb(), state.a(), look.framed());
        }
        hex.render(g, mouseX, mouseY);
        renderButtons(g, mouseX, mouseY);
        renderRecents(g, mouseX, mouseY);
        g.pose().popPose();
    }

    private void renderSquare(GuiGraphics g) {
        for (int i = 0; i < columns.length; i++) {
            g.fillGradient(svX + i * COLUMN, svY, svX + (i + 1) * COLUMN, svY + look.svH(), columns[i], 0xFF000000);
        }
        int markX = svX + Math.round(state.s() * (look.svW() - 1));
        int markY = svY + Math.round((1 - state.v()) * (look.svH() - 1));
        if (look.framed()) {
            GuiPaint.outline(g, svX - 1, svY - 1, look.svW() + 2, look.svH() + 2, GuiPaint.FRAME);
            GuiPaint.outline(g, markX - 2, markY - 2, 5, 5, 0xFFFFFFFF);
        } else {
            GuiPaint.outline(g, markX - 2, markY - 2, 5, 5, 0xFFFFFFFF);
            GuiPaint.outline(g, markX - 3, markY - 3, 7, 7, 0xFF000000);
        }
    }

    private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
        Eyedropper eye = options.eyedropper();
        if (eye != null) {
            boolean over = in(mouseX, mouseY, eyeX, eyeY, eye.size(), eye.size());
            GuiPaint.box(g, eyeX, eyeY, eye.size(), eye.size(), picking ? GuiPaint.SELECTED : GuiPaint.BUTTON,
                    picking ? GuiPaint.ACCENT : over ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            eye.draw(g, eyeX, eyeY);
        }
        if (options.allowNone()) {
            fillSwatch(g, noneX, noneY, NONE, NONE, 0, state.value() == 0, mouseX, mouseY);
        }
    }

    private void renderRecents(GuiGraphics g, int mouseX, int mouseY) {
        if (look.recentLabel() && RecentColors.size() > 0) {
            GuiText.draw(g, font, recentLabel, x + look.pad(), recentY - 10, GuiPaint.MUTED);
        }
        for (int i = 0; i < RecentColors.size(); i++) {
            int color = RecentColors.get(i);
            if (look.framed()) {
                PanelPaint.swatch(g, recentX(i), recentY, look.recent(), color);
                if (color == state.value()) {
                    GuiPaint.outline(g, recentX(i) - 2, recentY - 2, look.recent() + 4, look.recent() + 4, GuiPaint.FG);
                }
            } else {
                fillSwatch(g, recentX(i), recentY, look.recent(), look.recent(), color, color == state.value(), mouseX,
                        mouseY);
            }
        }
    }

    /** Uma amostra: o xadrez com a cor por cima (0 é só o xadrez com uma risca), a borda coral se for a atual. */
    private void fillSwatch(GuiGraphics g, int left, int top, int w, int h, int color, boolean on, int mouseX,
            int mouseY) {
        ColorStrips.checker(g, left + 1, top + 1, w - 2, h - 2);
        if (color != 0) {
            g.fill(left + 1, top + 1, left + w - 1, top + h - 1, color);
        } else {
            for (int i = 1; i < Math.min(w, h) - 1; i++) {
                g.fill(left + i, top + i, left + i + 1, top + i + 1, GuiPaint.DANGER);
            }
        }
        GuiPaint.outline(g, left, top, w, h, on ? GuiPaint.ACCENT
                : in(mouseX, mouseY, left, top, w, h) ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
    }

    // ------------------------------------------------------------------ mouse

    public void mouseClicked(double mx, double my, int button) {
        if (hex.contains(mx, my)) {
            hex.mouseClicked(mx, my, button);
            return;
        }
        hex.blur();
        for (int i = 0; hasTheme && i < 6; i++) {
            if (in(mx, my, themeX(i), y + look.themeY(), THEME_SWATCH_W, THEME_SWATCH_H)) {
                state.stored(i + 1);
                changed();
                return;
            }
        }
        if (in(mx, my, svX, svY, look.svW(), look.svH())) {
            drag = Drag.SV;
            dragTo(mx, my);
        } else if (in(mx, my, hueX, svY, look.strip(), look.svH())) {
            drag = Drag.HUE;
            dragTo(mx, my);
        } else if (options.alpha() && in(mx, my, alphaX, svY, look.strip(), look.svH())) {
            drag = Drag.ALPHA;
            dragTo(mx, my);
        } else if (options.eyedropper() != null && in(mx, my, eyeX, eyeY, eyeSize(), eyeSize())) {
            picking = !picking;
        } else if (options.allowNone() && in(mx, my, noneX, noneY, NONE, NONE)) {
            state.stored(0);
            changed();
        } else {
            clickRecent(mx, my);
        }
    }

    private void clickRecent(double mx, double my) {
        for (int i = 0; i < RecentColors.size(); i++) {
            if (in(mx, my, recentX(i), recentY, look.recent(), look.recent())) {
                if (state.picked(RecentColors.get(i))) {
                    changed();
                }
                return;
            }
        }
    }

    private void dragTo(double mx, double my) {
        double fy = (my - svY) / (look.svH() - 1);
        switch (drag) {
            case SV -> state.square((mx - svX) / (look.svW() - 1), fy);
            case HUE -> state.hue(fy);
            case ALPHA -> state.alpha(fy);
            default -> {
                return;
            }
        }
        changed();
    }

    public boolean mouseDragged(double mx, double my) {
        if (drag == Drag.NONE) {
            return false;
        }
        dragTo(mx, my);
        return true;
    }

    public boolean mouseReleased() {
        boolean was = drag != Drag.NONE;
        drag = Drag.NONE;
        return was;
    }

    /** O conta-gotas está esperando um clique na miniatura. */
    public boolean picking() {
        return picking;
    }

    /** A cor do pixel pego na miniatura (ARGB). */
    public void picked(int argb) {
        picking = false;
        if (state.picked(argb)) {
            changed();
        }
    }

    // ------------------------------------------------------------------ teclado e dicas

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return hex.wantsKeys() && hex.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char c, int modifiers) {
        return hex.wantsKeys() && hex.charTyped(c, modifiers);
    }

    public boolean wantsKeys() {
        return hex.wantsKeys();
    }

    public @Nullable Component tooltip(double mx, double my) {
        Eyedropper eye = options.eyedropper();
        return eye != null && in(mx, my, eyeX, eyeY, eye.size(), eye.size()) ? eye.tip() : null;
    }

    /** A janela fechou: solta o campo e, se a cor mudou, a põe nas recentes. */
    public void closed() {
        hex.blur();
        if (state.value() != initial) {
            RecentColors.push(state.value());
        }
    }

    public int value() {
        return state.value();
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return look.width();
    }

    public int height() {
        return height;
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** O centro da amostra do papel {@code role} (1 a 6) do tema. */
    public int[] themeCenter(int role) {
        return new int[] {themeX(role - 1) + THEME_SWATCH_W / 2, y + look.themeY() + 5};
    }

    public int[] hexCenter() {
        return new int[] {hex.centerX(), hex.centerY()};
    }

    public void focusHex() {
        hex.focus();
    }

    public int[] svCenter() {
        return new int[] {svX + look.svW() * 3 / 4, svY + look.svH() / 4};
    }

    public int[] eyeCenter() {
        return new int[] {eyeX + eyeSize() / 2, eyeY + eyeSize() / 2};
    }
}
