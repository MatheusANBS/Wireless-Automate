package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Uma janela solta do editor (o seletor de cor, a lista de fontes), desenhada por cima de tudo e aberta uma por vez
 * pelo {@link TvPopups}. Quem a abriu entrega o que ela precisa; ela devolve o valor por um callback.
 */
interface TvPopup {
    void render(GuiGraphics g, int mouseX, int mouseY);

    boolean contains(double mouseX, double mouseY);

    /** Clique dentro da janela. */
    void mouseClicked(double mouseX, double mouseY, int button);

    default boolean mouseDragged(double mouseX, double mouseY) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return contains(mouseX, mouseY);
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char c, int modifiers) {
        return false;
    }

    /** Um campo de texto da janela está com o teclado. */
    default boolean wantsKeys() {
        return false;
    }

    /** A dica do ponto sob o mouse, ou nulo. */
    default @Nullable Component tooltip(double mouseX, double mouseY) {
        return null;
    }

    /** A janela fechou (por clique fora, Esc ou troca): guarda o que precisa. */
    default void closed() {
    }
}
