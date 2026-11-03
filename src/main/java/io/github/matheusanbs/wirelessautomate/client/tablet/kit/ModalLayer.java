package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * A camada das janelas do Tablet: o véu cobre o corpo (coordenadas da tela), a janela fica por cima dele, e enquanto
 * há uma aberta ela consome o mouse e o teclado. Clique fora da janela e Esc a fecham.
 */
public final class ModalLayer {
    private int x;
    private int y;
    private int w;
    private int h;
    private @Nullable TabletModal modal;

    /** O retângulo que o véu cobre. */
    public void bounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void open(TabletModal m) {
        close();
        modal = m;
    }

    public void close() {
        TabletModal old = modal;
        modal = null;
        if (old != null) {
            old.closed();
        }
    }

    public boolean isOpen() {
        return modal != null;
    }

    public @Nullable TabletModal current() {
        return modal;
    }

    public void render(GuiGraphics g, Font font, int mx, int my) {
        if (modal == null) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        PanelPaint.scrim(g, x, y, w, h);
        g.pose().translate(0, 0, 100);
        modal.render(g, font, mx, my);
        g.pose().popPose();
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (modal == null) {
            return false;
        }
        if (modal.contains(mx, my)) {
            modal.mouseClicked(mx, my, button);
            closeIfDone();
        } else {
            close();
        }
        return true;
    }

    public boolean mouseDragged(double mx, double my) {
        if (modal == null) {
            return false;
        }
        modal.mouseDragged(mx, my);
        return true;
    }

    public boolean mouseReleased(double mx, double my) {
        if (modal == null) {
            return false;
        }
        modal.mouseReleased(mx, my);
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double d) {
        if (modal == null) {
            return false;
        }
        modal.mouseScrolled(mx, my, d);
        return true;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        if (modal == null) {
            return false;
        }
        if (!modal.keyPressed(key, scan, mods) && key == InputConstants.KEY_ESCAPE) {
            close();
        }
        closeIfDone();
        return true;
    }

    public boolean charTyped(char c, int mods) {
        if (modal == null) {
            return false;
        }
        modal.charTyped(c, mods);
        return true;
    }

    private void closeIfDone() {
        if (modal != null && modal.done()) {
            close();
        }
    }
}
