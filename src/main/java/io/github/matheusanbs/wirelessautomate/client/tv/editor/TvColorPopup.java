package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.color.ColorLook;
import io.github.matheusanbs.wirelessautomate.client.color.ColorPopup;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * O seletor de cor do editor da Tela de Sinal: o {@link ColorPopup} comum com tudo ligado (cores do tema, alfa,
 * conta-gotas e "nenhuma" quando a cor pode faltar), no visual {@link ColorLook#SIGNAL}, posto ao lado da amostra que
 * o abriu. Só existe aberta; os ganchos do e2e passam direto. Sem tema (o editor de pixel art, cuja paleta guarda ARGB
 * fixo), a linha "do tema" some e o resto sobe.
 */
final class TvColorPopup implements TvPopup {
    private static final ColorPopup.Eyedropper EYE = new ColorPopup.Eyedropper() {
        private final Component tip = TvEditorText.key("color.eyedropper");

        @Override
        public void draw(GuiGraphics g, int x, int y) {
            TvIcons.draw(g, TvIcons.Icon.EYEDROPPER, x, y);
        }

        @Override
        public int size() {
            return TvIcons.SIZE;
        }

        @Override
        public Component tip() {
            return tip;
        }
    };

    private final ColorPopup popup;

    /** Com {@code theme} nulo, sem a linha das cores do tema. */
    TvColorPopup(Font font, TvRect anchor, TvRect bounds, int value, boolean allowNone, boolean alpha,
            @Nullable TvTheme theme, IntConsumer set) {
        int[] themeColors = null;
        if (theme != null) {
            themeColors = new int[6];
            for (int i = 0; i < 6; i++) {
                themeColors[i] = theme.color(i + 1) | 0xFF000000;
            }
        }
        ColorPopup.Options options = new ColorPopup.Options(ColorLook.SIGNAL, allowNone, alpha, themeColors, EYE);
        int w = ColorLook.SIGNAL.width();
        int h = ColorPopup.height(options);
        int left = anchor.x() - w - 4;
        if (left < bounds.x()) {
            left = anchor.right() + 4;
        }
        int x = Mth.clamp(left, bounds.x(), Math.max(bounds.x(), bounds.right() - w));
        int y = Mth.clamp(anchor.y(), bounds.y(), Math.max(bounds.y(), bounds.bottom() - h));
        this.popup = new ColorPopup(font, x, y, value, options, set);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        popup.render(g, mouseX, mouseY);
    }

    @Override
    public boolean contains(double mouseX, double mouseY) {
        return popup.contains(mouseX, mouseY);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        popup.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        return popup.mouseDragged(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY) {
        return popup.mouseReleased();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return popup.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return popup.charTyped(c, modifiers);
    }

    @Override
    public boolean wantsKeys() {
        return popup.wantsKeys();
    }

    @Override
    public @Nullable Component tooltip(double mouseX, double mouseY) {
        return popup.tooltip(mouseX, mouseY);
    }

    @Override
    public void closed() {
        popup.closed();
    }

    boolean picking() {
        return popup.picking();
    }

    void picked(int argb) {
        popup.picked(argb);
    }

    int[] themeCenter(int role) {
        return popup.themeCenter(role);
    }

    int[] hexCenter() {
        return popup.hexCenter();
    }

    void focusHex() {
        popup.focusHex();
    }

    int[] svCenter() {
        return popup.svCenter();
    }

    int[] eyeCenter() {
        return popup.eyeCenter();
    }

    int value() {
        return popup.value();
    }
}
