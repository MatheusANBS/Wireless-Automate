package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A cópia do perfil de máquina do jogador na coluna da tela do roteador ({@link MachineColumn}): "Cópia: Fornalha #2"
 * com o ✕ que a esquece, numa linha embaixo da lista das máquinas ou, sem a lista (sem o Upgrade de Alcance), numa
 * faixa no alto do visor, por cima do modelo. Também decide o que Ctrl+C, Ctrl+V e Shift + Ctrl+V pedem
 * ({@link #key}). O texto é medido quando a cópia ou a largura mudam; quem manda o pedido ao servidor é a coluna.
 */
final class CopyFooter {
    static final int H = 12;
    /** O que uma tecla pede: copiar, colar no alvo ou colar no grupo inteiro. */
    enum Request { COPY, PASTE, PASTE_GROUP }

    private int x;
    private int y;
    private int w;
    /** Na faixa do visor (com o fundo translúcido) em vez da linha da lista. */
    private boolean overlay;
    private @Nullable Component preparedFor;
    private int preparedW = -1;
    private GuiText.Fitted text = new GuiText.Fitted(Component.empty(), FormattedCharSequence.EMPTY, 0, false);

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    /** Há uma cópia: a linha aparece. */
    static boolean shown(Component clipboard) {
        return !clipboard.getString().isEmpty();
    }

    /**
     * O pedido de uma tecla na tela do roteador, fora de um campo de texto: Ctrl+C copia e Ctrl+V cola (com Shift, no
     * grupo inteiro; no Mac, Cmd); {@code null} quando a tecla não é delas.
     */
    static @Nullable Request key(int keyCode, int modifiers) {
        int ctrl = Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL;
        if ((modifiers & ctrl) == 0 || (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            return null;
        }
        if (keyCode == GLFW.GLFW_KEY_C) {
            return Request.COPY;
        }
        if (keyCode == GLFW.GLFW_KEY_V) {
            return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? Request.PASTE_GROUP : Request.PASTE;
        }
        return null;
    }

    /**
     * Põe a linha em ({@code x}, {@code y}) com a largura {@code w} ({@code overlay}: a faixa no alto do visor) e mede
     * o texto da cópia {@code clipboard}, se ele ou a largura mudaram; a coluna chama antes do desenho e dos cliques.
     */
    void place(Font font, Component clipboard, int x, int y, int w, boolean overlay) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.overlay = overlay;
        if (clipboard != preparedFor || preparedW != w) {
            preparedFor = clipboard;
            preparedW = w;
            text = GuiText.fit(font, tr("range.copy.footer", clipboard), w - RangeRowButtons.SIZE - 7);
        }
    }

    private int forgetX() {
        return x + w - RangeRowButtons.SIZE - 2;
    }

    private boolean overForget(double mx, double my) {
        return mx >= forgetX() && mx < forgetX() + RangeRowButtons.SIZE && my >= y + 1 && my < y + 1 + RangeRowButtons.SIZE;
    }

    private boolean over(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + H;
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        if (overlay) {
            g.fill(x, y, x + w, y + H, GuiPaint.alpha(GuiPaint.INSET, 0xD8));
        }
        GuiText.draw(g, font, text, x + 3, y + 2, GuiPaint.MUTED);
        int fx = forgetX();
        GuiPaint.box(g, fx, y + 1, RangeRowButtons.SIZE, RangeRowButtons.SIZE, GuiPaint.BUTTON,
                overForget(mx, my) ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        RangeRowButtons.cross(g, fx + 2, y + 3, GuiPaint.FG);
    }

    /** O clique no ✕ (esquecer a cópia). */
    boolean forgetClicked(double mx, double my) {
        return overForget(mx, my);
    }

    /** A dica do ✕ ou da linha sob o mouse; {@code null} fora dela. */
    @Nullable Component tooltip(Component clipboard, double mx, double my) {
        if (overForget(mx, my)) {
            return tr("range.copy.forget").copy().append("\n")
                    .append(tr("range.copy.forget.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
        }
        if (!over(mx, my)) {
            return null;
        }
        return tr("range.copy.footer", clipboard).copy().append("\n")
                .append(tr("range.copy.footer.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** O centro do ✕ (gancho do e2e). */
    int[] forgetCenter() {
        return new int[] {forgetX() + RangeRowButtons.SIZE / 2, y + 1 + RangeRowButtons.SIZE / 2};
    }
}
