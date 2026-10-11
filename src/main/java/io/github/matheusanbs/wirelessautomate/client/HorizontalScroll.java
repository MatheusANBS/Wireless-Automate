package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWScrollCallbackI;

/**
 * Roda horizontal para as telas do mod (o {@code scrollX} do {@code mouseScrolled} do main). Porte 1.20.1: o
 * {@code MouseHandler.onScroll} recebe o x da roda, mas só o usa no macOS com y = 0 (patch do Forge para o
 * MC-121772), e o {@code mouseScrolled} da tela só tem o y. Aqui um callback de roda do GLFW se encaixa na
 * frente do do jogo: chama sempre o anterior (o jogo e o que outro mod tenha posto) e, com y = 0 e x ≠ 0 fora
 * do macOS (lá o próprio jogo já trata o x como y), repassa o x, na thread do jogo, à tela aberta que
 * implementar esta interface. Instalado uma vez, quando abre a primeira tela que a implementa; o callback fica
 * num campo estático, para o coletor não liberar o ponteiro nativo.
 */
public interface HorizontalScroll {
    /**
     * A roda horizontal sobre a tela, com a sensibilidade e o "roda discreta" das opções aplicados como na
     * vertical; positivo é o mesmo sentido do {@code scrollX} do main. Devolve se tratou.
     */
    boolean mouseScrolledHorizontal(double mouseX, double mouseY, double scrollX);

    /** A instalação do callback (no barramento do Forge, só no cliente). */
    @Mod.EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
    final class Installer {
        private static @Nullable GLFWScrollCallback callback;
        private static @Nullable GLFWScrollCallbackI previous;

        private Installer() {
        }

        @SubscribeEvent
        public static void onInit(ScreenEvent.Init.Post event) {
            if (callback == null && event.getScreen() instanceof HorizontalScroll) {
                install(Minecraft.getInstance());
            }
        }

        private static void install(Minecraft minecraft) {
            long window = minecraft.getWindow().getWindow();
            callback = GLFWScrollCallback.create((handle, x, y) -> {
                GLFWScrollCallbackI before = previous;
                if (before != null) {
                    before.invoke(handle, x, y);
                }
                if (y == 0 && x != 0 && !Minecraft.ON_OSX) {
                    minecraft.execute(() -> deliver(minecraft, handle, x));
                }
            });
            previous = GLFW.glfwSetScrollCallback(window, callback);
        }

        private static void deliver(Minecraft minecraft, long handle, double x) {
            var window = minecraft.getWindow();
            if (handle != window.getWindow() || minecraft.getOverlay() != null
                    || !(minecraft.screen instanceof HorizontalScroll target)) {
                return;
            }
            double amount = (minecraft.options.discreteMouseScroll().get() ? Math.signum(x) : x)
                    * minecraft.options.mouseWheelSensitivity().get();
            double mouseX = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
            double mouseY = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
            target.mouseScrolledHorizontal(mouseX, mouseY, amount);
        }
    }
}
