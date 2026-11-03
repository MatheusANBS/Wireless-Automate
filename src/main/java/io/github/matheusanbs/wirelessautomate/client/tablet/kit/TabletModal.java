package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Uma janela do Tablet que abre sobre um véu na {@code ModalLayer}: desenha, recebe o mouse e o teclado. */
public interface TabletModal {
    void render(GuiGraphics g, Font font, int mx, int my);

    /** Se o ponto está na janela; fora dela, um clique a fecha. */
    boolean contains(double mx, double my);

    void mouseClicked(double mx, double my, int button);

    default boolean mouseDragged(double mx, double my) {
        return false;
    }

    default boolean mouseReleased(double mx, double my) {
        return false;
    }

    default boolean mouseScrolled(double mx, double my, double delta) {
        return true;
    }

    default boolean keyPressed(int key, int scan, int mods) {
        return false;
    }

    default boolean charTyped(char c, int mods) {
        return false;
    }

    default @Nullable Component tooltip(double mx, double my) {
        return null;
    }

    /** Se a janela terminou (uma escolha feita); a camada a fecha depois do clique ou da tecla. */
    default boolean done() {
        return false;
    }

    /** A camada a fechou. */
    default void closed() {
    }
}
