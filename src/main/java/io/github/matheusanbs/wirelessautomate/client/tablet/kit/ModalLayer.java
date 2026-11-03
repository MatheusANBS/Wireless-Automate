package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A camada das janelas do Tablet: o véu cobre o corpo (coordenadas da tela), a janela fica por cima dele, e enquanto
 * há uma aberta ela consome o mouse e o teclado. Clique fora da janela e Esc a fecham. As alturas: véu em z 300,
 * janela em 400, os itens dela por volta de 550 (o {@code renderItem} soma 150) e a dica ({@link #tooltip}) em 700,
 * acima de tudo.
 */
public final class ModalLayer {
    /** O quanto a dica sobe além do z 400 que o próprio desenho da dica usa: fica acima dos itens da janela. */
    private static final int TIP_Z = 300;
    /** A largura em que a dica quebra, a mesma do {@code Tooltip} do Minecraft. */
    private static final int TIP_WIDTH = 170;

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

    /** Fecha sem gravar: o que a janela editava sumiu ({@link TabletModal#dropped}). */
    public void drop() {
        TabletModal old = modal;
        modal = null;
        if (old != null) {
            old.dropped();
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

    /** A dica de uma janela (ou o texto cortado sob ela), quebrada como a da tela, acima dos itens da janela. */
    public static void tooltip(GuiGraphics g, Font font, Component tip, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, TIP_Z);
        g.renderTooltip(font, font.split(tip, TIP_WIDTH), mx, my);
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
