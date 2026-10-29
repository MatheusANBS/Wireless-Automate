package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelCanvas;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelFrames;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A faixa de quadros do editor de pixel art, embaixo do quadro de desenho (o mockup da animação). Na primeira linha:
 * Quadro novo, Duplicar e Remover, e as miniaturas dos quadros, com o escolhido marcado (é o que se desenha) e, com a
 * prévia tocando, o da vez contornado; um clique escolhe, arrastar muda a ordem e a roda rola a faixa quando os
 * quadros não cabem. Na segunda: a velocidade em quadros por segundo (‹ › e o campo, sem a roda), o modo numa lista
 * ({@link TvDropdown}), Tocar ou Pausar a prévia, o papel vegetal e "n/total". Tudo passa pelo
 * {@link TvPixelFrames} da sessão, que põe no desfazer o que muda a arte. As miniaturas são as texturas dos quadros da
 * {@link TvPixelTexture} (o escolhido, a da arte), refeitas só quando o quadro muda; os textos, só quando o número
 * muda.
 */
final class TvPixelStrip {
    /** Um botão da faixa. */
    enum Button { ADD, DUPLICATE, REMOVE, PLAY, ONION }

    static final int HEIGHT = 38;
    /** A largura em que a faixa cabe inteira (a da janela mínima do editor). */
    static final int MIN_W = 190;
    private static final int CELL = 22;
    private static final int THUMB = CELL - 2;
    private static final int GAP = 2;
    private static final int ROW2 = 24;
    private static final int SPEED_W = 40;
    private static final int DRAG_START = 3;
    private static final int PLAYING = 0xFF3DB56E;
    private static final Button[] ALL = Button.values();
    private static final List<TvPixels.Mode> MODES = List.of(TvPixels.Mode.values());

    private final TvPixelSession session;
    private final TvPixelTexture texture;
    private final TvPopups popups;
    private final TvRect[] rects = new TvRect[ALL.length];
    private final Component[] tips = new Component[ALL.length];
    private Font font;
    private TvRect box = TvRect.NONE;
    private int thumbsX;
    private int visible;
    private int first;
    private int shownCurrent = -1;
    private TvNumberField speed;
    private TvDropdown<TvPixels.Mode> mode;
    private GuiText.Fitted perSecond;
    private int perSecondX;
    private int counterX;
    private int shownCount = -1;
    private int shownIndex = -1;
    private Component counter = Component.empty();
    private int counterW;
    private int refreshed = -1;
    private Component pauseTip = Component.empty();
    private Component speedTip = Component.empty();
    /** A dica da miniatura sob o mouse, refeita só quando o mouse passa a outro quadro. */
    private int tipFrame = -1;
    private Component frameTip = Component.empty();
    private int pressed = -1;
    private boolean dragging;
    private double pressX;
    private int target = -1;
    private boolean speedPressed;

    TvPixelStrip(TvPixelSession session, TvPixelTexture texture, TvPopups popups) {
        this.session = session;
        this.texture = texture;
        this.popups = popups;
    }

    private TvPixelFrames frames() {
        return session.frames();
    }

    void layout(Font font, TvRect box) {
        this.font = font;
        this.box = box;
        int s = TvIcons.SIZE;
        int y = box.y() + (CELL - s) / 2;
        for (int i = 0; i <= Button.REMOVE.ordinal(); i++) {
            rects[i] = new TvRect(box.x() + i * (s + GAP), y, s, s);
        }
        thumbsX = rects[Button.REMOVE.ordinal()].right() + 6;
        visible = Math.max(1, (box.right() - thumbsX + GAP) / (CELL + GAP));
        int row = box.y() + ROW2;
        speed = new TvNumberField(font, "pixel.anim.speed", 0, 0, SPEED_W, TvPixels.MIN_FPS, TvPixels.MAX_FPS, 1,
                () -> session.canvas().fps(), value -> frames().setFps(value));
        speed.place(box.x(), row + 1);
        Component unit = TvEditorText.key("pixel.anim.fps");
        perSecond = GuiText.fit(font, unit, font.width(unit));
        perSecondX = box.x() + SPEED_W + 3;
        int modeX = perSecondX + font.width(unit) + 6;
        int modeW = Math.max(font.width(modeName(TvPixels.Mode.LOOP)), font.width(modeName(TvPixels.Mode.PINGPONG)))
                + 18;
        mode = new TvDropdown<>(font, "pixel.anim.mode", 0, 0, modeW, popups, () -> MODES,
                TvPixelStrip::modeName, () -> session.canvas().mode(), value -> frames().setMode(value));
        mode.place(modeX, row);
        rects[Button.PLAY.ordinal()] = new TvRect(modeX + modeW + 4, row - 1, s, s);
        rects[Button.ONION.ordinal()] = new TvRect(rects[Button.PLAY.ordinal()].right() + GAP, row - 1, s, s);
        counterX = rects[Button.ONION.ordinal()].right() + 6;
        for (Button button : ALL) {
            tips[button.ordinal()] = TvEditorText.key("pixel.anim." + button.name().toLowerCase(Locale.ROOT));
        }
        pauseTip = TvEditorText.key("pixel.anim.pause");
        speedTip = TvEditorText.key("pixel.anim.speed.tip", TvPixels.MIN_FPS, TvPixels.MAX_FPS);
        tipFrame = -1;
        shownCount = -1;
        shownCurrent = -1;
        refreshed = -1;
    }

    private static Component modeName(TvPixels.Mode value) {
        return TvEditorText.key("pixel.anim.mode." + value.name().toLowerCase(Locale.ROOT));
    }

    private boolean enabled(Button button) {
        return switch (button) {
            case ADD, DUPLICATE -> frames().canAdd();
            case REMOVE -> session.canvas().frames() > 1;
            default -> true;
        };
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        TvPixelCanvas canvas = session.canvas();
        refresh(canvas);
        for (int i = 0; i <= Button.REMOVE.ordinal(); i++) {
            TvEditorPaint.iconButton(g, rects[i], icon(ALL[i]), rects[i].contains(mouseX, mouseY), enabled(ALL[i]));
        }
        thumbs(g, canvas, mouseX, mouseY);
        speed.render(g, mouseX, mouseY);
        GuiText.draw(g, font, perSecond, perSecondX, box.y() + ROW2 + 3, GuiPaint.MUTED);
        mode.render(g, mouseX, mouseY);
        TvRect play = rects[Button.PLAY.ordinal()];
        TvEditorPaint.iconButton(g, play, frames().playing() ? TvIcons.Icon.FRAME_PAUSE : TvIcons.Icon.FRAME_PLAY,
                play.contains(mouseX, mouseY), true);
        TvRect onion = rects[Button.ONION.ordinal()];
        boolean lit = frames().onion();
        GuiPaint.box(g, onion.x(), onion.y(), onion.w(), onion.h(), GuiPaint.BUTTON, lit ? GuiPaint.ACCENT
                : onion.contains(mouseX, mouseY) ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        if (lit) {
            g.fill(onion.x() + 1, onion.y() + 1, onion.right() - 1, onion.bottom() - 1,
                    GuiPaint.alpha(GuiPaint.ACCENT, 0x40));
        }
        TvIcons.draw(g, TvIcons.Icon.ONION, onion.x(), onion.y());
        if (counterX + counterW <= box.right()) {
            GuiPaint.text(g, font, counter, box.right() - counterW, box.y() + ROW2 + 3, GuiPaint.MUTED);
        }
    }

    private static TvIcons.Icon icon(Button button) {
        return switch (button) {
            case ADD -> TvIcons.Icon.ADD;
            case DUPLICATE -> TvIcons.Icon.DUPLICATE;
            default -> TvIcons.Icon.REMOVE;
        };
    }

    /** Os números da faixa, só quando a arte mudou: a velocidade, o modo, o contador e o quadro à vista. */
    private void refresh(TvPixelCanvas canvas) {
        if (refreshed != canvas.version()) {
            refreshed = canvas.version();
            if (!speed.wantsKeys()) {
                speed.refresh();
            }
            mode.refresh();
        }
        if (shownCount != canvas.frames() || shownIndex != canvas.current()) {
            shownCount = canvas.frames();
            shownIndex = canvas.current();
            counter = TvEditorText.key("pixel.anim.counter", shownIndex + 1, shownCount);
            counterW = font.width(counter);
        }
        if (shownCurrent != canvas.current()) {
            shownCurrent = canvas.current();
            if (shownCurrent < first) {
                first = shownCurrent;
            } else if (shownCurrent >= first + visible) {
                first = shownCurrent - visible + 1;
            }
        }
        first = Math.max(0, Math.min(first, canvas.frames() - visible));
    }

    private void thumbs(GuiGraphics g, TvPixelCanvas canvas, int mouseX, int mouseY) {
        int playingFrame = frames().playing() && canvas.frames() > 1 ? frames().previewFrame(Util.getMillis()) : -1;
        int last = Math.min(canvas.frames(), first + visible);
        float scale = Math.min(THUMB / (float) canvas.width(), THUMB / (float) canvas.height());
        int w = Math.max(1, Math.round(canvas.width() * scale));
        int h = Math.max(1, Math.round(canvas.height() * scale));
        for (int i = first; i < last; i++) {
            int x = cellX(i);
            int y = box.y();
            boolean chosen = i == canvas.current();
            int border = chosen ? GuiPaint.ACCENT : i == playingFrame ? PLAYING
                    : cellAt(mouseX, mouseY) == i ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
            GuiPaint.outline(g, x, y, CELL, CELL, border);
            texture.drawChecker(g, x + 1, y + 1, x + 1 + THUMB, y + 1 + THUMB, x + 1, y + 1, 2);
            int tx = x + 1 + (THUMB - w) / 2;
            int ty = y + 1 + (THUMB - h) / 2;
            texture.drawFrame(g, canvas, i, 1, tx, ty, tx + w, ty + h, 0, 0, 1, 1);
            if (chosen) {
                g.fill(x + 1, y + CELL - 3, x + CELL - 1, y + CELL - 1, GuiPaint.ACCENT);
            }
            if (i == playingFrame && !chosen) {
                GuiPaint.outline(g, x + 1, y + 1, CELL - 2, CELL - 2, PLAYING);
            }
        }
        if (dragging && target >= 0) {
            int x = target > pressed ? cellX(target) + CELL : cellX(target) - GAP;
            g.fill(x, box.y() - 1, x + GAP, box.y() + CELL + 1, GuiPaint.ACCENT);
        }
        if (first > 0) {
            g.fill(thumbsX - 3, box.y() + 4, thumbsX - 2, box.y() + CELL - 4, GuiPaint.MUTED);
        }
        if (last < canvas.frames()) {
            int right = cellX(last - 1) + CELL + 1;
            g.fill(right, box.y() + 4, right + 1, box.y() + CELL - 4, GuiPaint.MUTED);
        }
    }

    private int cellX(int index) {
        return thumbsX + (index - first) * (CELL + GAP);
    }

    /** O quadro sob o ponto, ou -1. */
    private int cellAt(double mouseX, double mouseY) {
        if (mouseY < box.y() || mouseY >= box.y() + CELL || mouseX < thumbsX) {
            return -1;
        }
        int slot = (int) (mouseX - thumbsX) / (CELL + GAP);
        int index = first + slot;
        boolean inside = mouseX - thumbsX - slot * (CELL + GAP) < CELL;
        return inside && slot < visible && index < session.canvas().frames() ? index : -1;
    }

    // ------------------------------------------------------------------ mouse e teclado

    boolean contains(double mouseX, double mouseY) {
        return box.contains(mouseX, mouseY);
    }

    /** O clique na faixa; devolve se foi nela. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!box.contains(mouseX, mouseY)) {
            speed.blur();
            return false;
        }
        if (speed.mouseClicked(mouseX, mouseY, button)) {
            speedPressed = true;
            return true;
        }
        speed.blur();
        if (mode.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return true;
        }
        for (Button each : ALL) {
            if (rects[each.ordinal()].contains(mouseX, mouseY)) {
                press(each);
                return true;
            }
        }
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0) {
            frames().select(cell);
            pressed = cell;
            pressX = mouseX;
            dragging = false;
            target = -1;
        }
        return true;
    }

    /** Aperta um botão, pelo mouse ou pelo atalho. */
    void press(Button button) {
        switch (button) {
            case ADD -> frames().add();
            case DUPLICATE -> frames().duplicate();
            case REMOVE -> frames().remove();
            case PLAY -> frames().togglePlaying();
            case ONION -> frames().toggleOnion();
        }
    }

    /** Arrastar uma miniatura muda a ordem: a marca mostra onde o quadro vai ficar. */
    boolean mouseDragged(double mouseX, double mouseY) {
        if (speedPressed) {
            speed.mouseDragged(mouseX, mouseY);
            return true;
        }
        if (pressed < 0) {
            return false;
        }
        dragging = dragging || Math.abs(mouseX - pressX) > DRAG_START;
        if (dragging) {
            int slot = (int) Math.floor((mouseX - thumbsX) / (CELL + GAP));
            target = Math.max(0, Math.min(session.canvas().frames() - 1, first + slot));
        }
        return true;
    }

    boolean mouseReleased(double mouseX, double mouseY) {
        if (speedPressed) {
            speedPressed = false;
            speed.mouseReleased(mouseX, mouseY);
            return true;
        }
        if (pressed < 0) {
            return false;
        }
        if (dragging && target >= 0 && target != pressed) {
            frames().move(pressed, target);
        }
        pressed = -1;
        dragging = false;
        target = -1;
        return true;
    }

    /** A roda sobre as miniaturas rola a faixa (é uma lista que rola); sobre o resto, nada. */
    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!box.contains(mouseX, mouseY) || mouseY >= box.y() + CELL) {
            return false;
        }
        first = Math.max(0, Math.min(session.canvas().frames() - visible, first - (int) Math.signum(delta)));
        return true;
    }

    boolean wantsKeys() {
        return speed.wantsKeys();
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return speed.keyPressed(keyCode, scanCode, modifiers);
    }

    boolean charTyped(char c, int modifiers) {
        return speed.charTyped(c, modifiers);
    }

    void blur() {
        speed.blur();
    }

    @Nullable Component tooltip(double mouseX, double mouseY) {
        for (Button each : ALL) {
            if (rects[each.ordinal()].contains(mouseX, mouseY)) {
                return each == Button.PLAY && frames().playing() ? pauseTip : tips[each.ordinal()];
            }
        }
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0) {
            if (cell != tipFrame) {
                tipFrame = cell;
                frameTip = TvEditorText.key("pixel.anim.frame", cell + 1);
            }
            return frameTip;
        }
        return speed.contains(mouseX, mouseY) ? speedTip : null;
    }

    // ------------------------------------------------------------------ ganchos do e2e

    int[] center(Button button) {
        return rects[button.ordinal()].center();
    }

    /** O centro da miniatura do quadro {@code index} (rolando a faixa até ela). */
    int[] thumbCenter(int index) {
        if (index < first) {
            first = index;
        } else if (index >= first + visible) {
            first = index - visible + 1;
        }
        return new int[] {cellX(index) + CELL / 2, box.y() + CELL / 2};
    }

    int[] speedArrow(boolean right) {
        return speed.arrowCenter(right);
    }

    int[] modeCenter() {
        return new int[] {mode.centerX(), mode.centerY()};
    }
}
