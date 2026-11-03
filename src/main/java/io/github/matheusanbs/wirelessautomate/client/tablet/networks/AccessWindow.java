package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.client.access.AccessPanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.TabletModal;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A janela "Acesso a …" da rede escolhida (o "+ convidar" e o "+N" das fichas a abrem): o {@link AccessPanel} de
 * sempre (convidados, times e os dois campos com sugestões) dentro de uma janela sobre o véu. Os campos são widgets da
 * tela, desenhados por baixo da janela no passe dos widgets; a janela os desenha de novo por cima, e como a
 * {@code ModalLayer} engole o mouse e o teclado, ela mesma dá o foco ao campo clicado e repassa as teclas a ele.
 */
final class AccessWindow implements TabletModal {
    private static final int ROW = 13;

    private final TabletLayout layout;
    private final AccessPanel access;
    private final List<EditBox> boxes;
    private final GuiText.Fitted title;

    AccessWindow(TabletLayout layout, AccessPanel access, List<EditBox> boxes, Font font, String network) {
        this.layout = layout;
        this.access = access;
        this.boxes = boxes;
        this.title = GuiText.fit(font, NetworksText.tr("nets.access.window", network).copy()
                .withStyle(ChatFormatting.BOLD), width(layout) - 14);
    }

    private static int x(TabletLayout layout) {
        return layout.left + TabletLayout.PANEL_X0 - 1;
    }

    private static int y(TabletLayout layout) {
        return layout.top + 50;
    }

    private static int width(TabletLayout layout) {
        return layout.panelX1() - TabletLayout.PANEL_X0 + 2;
    }

    private static int height(TabletLayout layout) {
        return layout.h - 50 - 30;
    }

    /** As medidas do {@link AccessPanel} dentro da janela, lidas a cada uso (a tela muda de tamanho). */
    static AccessPanel.Frame frame(TabletLayout layout) {
        return new AccessPanel.Frame() {
            @Override
            public int x() {
                return AccessWindow.x(layout) + 7;
            }

            @Override
            public int labelY() {
                return AccessWindow.y(layout) + 20;
            }

            @Override
            public int width() {
                return AccessWindow.width(layout) - 14;
            }

            /** As linhas que sobram entre o rótulo e o fundo, depois dos rótulos e dos campos. */
            private int rows() {
                int room = bottom() - labelY() - AccessPanel.height(0, 0);
                return Math.max(AccessPanel.withTeams() ? 2 : 1, room / ROW);
            }

            @Override
            public int guestRows() {
                return AccessPanel.withTeams() ? (rows() + 1) / 2 : rows();
            }

            @Override
            public int teamRows() {
                return Math.max(1, rows() / 2);
            }

            @Override
            public int bottom() {
                return AccessWindow.y(layout) + height(layout) - 6;
            }
        };
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my) {
        int x = x(layout);
        int y = y(layout);
        PanelPaint.window(g, x, y, width(layout), height(layout));
        GuiText.draw(g, font, title, x + 7, y + 6, GuiPaint.FG);
        access.render(g, font, mx, my);
        for (EditBox box : boxes) {
            if (box.visible) {
                box.render(g, mx, my, 0);
            }
        }
        access.renderOverlay(g, font);
    }

    @Override
    public boolean contains(double mx, double my) {
        int x = x(layout);
        int y = y(layout);
        return mx >= x && mx < x + width(layout) && my >= y && my < y + height(layout);
    }

    @Override
    public void mouseClicked(double mx, double my, int button) {
        if (access.mouseClicked(mx, my)) {
            return;
        }
        for (EditBox box : boxes) {
            boolean over = box.visible && box.isMouseOver(mx, my);
            box.setFocused(over);
            if (over) {
                box.mouseClicked(mx, my, button);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        access.mouseScrolled(my, -(int) Math.signum(delta));
        return true;
    }

    private @Nullable EditBox focused() {
        for (EditBox box : boxes) {
            if (box.visible && box.isFocused()) {
                return box;
            }
        }
        return null;
    }

    /** As teclas vão ao campo com o cursor (a lista de sugestões primeiro); Esc sem lista aberta fecha a janela. */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        EditBox box = focused();
        if (box == null) {
            return false;
        }
        if (access.keyPressed(box, key)) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        box.keyPressed(key, scan, mods);
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        EditBox box = focused();
        return box != null && box.charTyped(c, mods);
    }

    @Override
    public @Nullable Component tooltip(double mx, double my) {
        return access.tooltip((int) mx, (int) my);
    }

    @Override
    public void closed() {
        for (EditBox box : boxes) {
            box.setFocused(false);
        }
        access.refresh(false);
    }
}
