package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageListScreen;
import java.lang.reflect.Field;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import io.github.matheusanbs.wirelessautomate.client.FlatButton;

/**
 * O mouse e o teclado do e2e: acha widgets pela mensagem ou pela dica, clica pelo mesmo caminho do mouse, digita
 * letra a letra, move o cursor e arrasta a alça de redimensionar.
 */
final class E2eUi {
    private E2eUi() {
    }

    /** Arrasta a alça de redimensionar do Baú como o mouse: aperta, arrasta {@code dx, dy} e solta. */
    static void dragGrip(StorageListScreen screen, int dx, int dy) {
        int[] grip = screen.gripPoint();
        screen.mouseClicked(grip[0], grip[1], 0);
        screen.mouseDragged(grip[0] + dx, grip[1] + dy, 0, dx, dy);
        screen.mouseReleased(grip[0] + dx, grip[1] + dy, 0);
    }

    /** Clique direito do jogador no bloco, pela face de cima, com o que estiver na mão. */
    static void useOn(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    /** O painel está no centro da janela do jogo. */
    static boolean centered(AbstractContainerScreen<?> screen) {
        return Math.abs(screen.getGuiLeft() + screen.getXSize() / 2 - screen.width / 2) <= 1
                && Math.abs(screen.getGuiTop() + screen.getYSize() / 2 - screen.height / 2) <= 1;
    }

    /** Arrasta a alça de redimensionar da tela de filtro como o mouse. */
    static void dragGrip(FilterScreen screen, int dx, int dy) {
        int[] grip = screen.gripPoint();
        screen.mouseClicked(grip[0], grip[1], 0);
        screen.mouseDragged(grip[0] + dx, grip[1] + dy, 0, dx, dy);
        screen.mouseReleased(grip[0] + dx, grip[1] + dy, 0);
    }

    static Predicate<AbstractWidget> byMessage(Component message) {
        return widget -> widget.getMessage().equals(message);
    }

    static Predicate<AbstractWidget> byMessageKey(String key) {
        return widget -> widget.getMessage().getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key);
    }

    static Predicate<AbstractWidget> byTooltip(Component tooltip) {
        return widget -> widget instanceof FlatButton button && tooltip.equals(button.currentTooltip());
    }

    /** O widget visível e ativo da tela aberta que satisfaz o filtro. */
    static AbstractWidget widget(Predicate<AbstractWidget> filter, String what) throws StepFailure {
        AbstractWidget widget = find(filter);
        if (widget == null) {
            throw new StepFailure("não achei " + what + " em " + describe(Minecraft.getInstance().screen));
        }
        if (!widget.active) {
            throw new StepFailure(what + " está desativado");
        }
        return widget;
    }

    static @Nullable AbstractWidget find(Predicate<AbstractWidget> filter) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.visible && filter.test(widget)) {
                    return widget;
                }
            }
        }
        return null;
    }

    /** Clique esquerdo no centro do widget, pelo mesmo caminho do mouse. */
    static void click(AbstractWidget widget) throws StepFailure {
        click(Minecraft.getInstance().screen, widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2);
    }

    static void click(Screen screen, int x, int y) throws StepFailure {
        if (screen == null) {
            throw new StepFailure("sem tela para clicar");
        }
        moveMouse(x, y);
        screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** Digita na tela aberta como o teclado faria ({@code charTyped} letra a letra). */
    static void type(String text) throws StepFailure {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            throw new StepFailure("sem tela para digitar");
        }
        text.codePoints().forEach(c -> screen.charTyped((char) c, 0));
    }

    /**
     * Põe o cursor do jogo no ponto (coordenadas da GUI), para o realce e as dicas das capturas
     * seguirem os cliques. O GLFW ignora o próprio {@code glfwSetCursorPos} no X11, então o campo
     * do {@code MouseHandler} é acertado por reflexão; se falhar, só a aparência das capturas muda.
     */
    static void moveMouse(int guiX, int guiY) {
        Minecraft minecraft = Minecraft.getInstance();
        // o MouseHandler converte pela largura da janela, que fica menor que o framebuffer na vitrine
        var window = minecraft.getWindow();
        double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        double scaleY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
        try {
            Field x = minecraft.mouseHandler.getClass().getDeclaredField("xpos");
            Field y = minecraft.mouseHandler.getClass().getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(minecraft.mouseHandler, guiX * scaleX);
            y.setDouble(minecraft.mouseHandler, guiY * scaleY);
        } catch (ReflectiveOperationException | RuntimeException e) {
            WirelessAutomate.LOGGER.debug("E2E: não deu para mover o cursor", e);
        }
    }
}
