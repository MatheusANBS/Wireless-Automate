package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevScreenshot.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotFilter.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotGalleries.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotLinker.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotTablet.*;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import io.github.matheusanbs.wirelessautomate.client.ConfiguratorWheelScreen;

/**
 * As capturas da roda do Configurador, nas escalas de GUI 2 e 3, sem nada sob o mouse e com o mouse num tipo.
 */
final class ScreenshotWheel {
    private ScreenshotWheel() {
    }

    /** A roda desenhada sobre a tela de título ({@code WA_SCREENSHOT_ONLY=roda}). */
    static @Nullable ConfiguratorWheelScreen wheelScreen;
    static boolean wheelGallery = ONLY != null && ONLY.startsWith("roda");

    /**
     * Roda do Configurador em escala de GUI 2 e 3: sem nada sob o mouse e com o mouse em Itens e no último
     * tipo (o centro mostra o nome e a descrição). Tipo atual Itens, para aparecer o contorno de destaque. Com
     * {@code WA_SCREENSHOT_LANG=pt_br}, em português.
     */
    static List<Step> wheelSteps() {
        List<Step> steps = new ArrayList<>();
        for (int scale : new int[] {2, 3}) {
            steps.add(new Step(() -> {
                linkerScreen = null;
                tabletScreen = null;
                filterScreen = null;
                spinGallery = blockGallery = coverGallery = false;
                wheelGallery = true;
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.options.guiScale().set(scale);
                minecraft.resizeDisplay();
                wheelScreen = new ConfiguratorWheelScreen();
                wheelScreen.choose(wheelScreen.sliceOf(ResourceType.ITEM));
                mouseX = mouseY = -1;
            }, "roda-g" + scale + "-centro"));
            steps.add(new Step(() -> wheelMouse(wheelScreen.sliceOf(ResourceType.ITEM)),
                    "roda-g" + scale + "-itens"));
            steps.add(new Step(() -> wheelMouse(wheelScreen.types().size() - 1),
                    "roda-g" + scale + "-tipo"));
        }
        return steps;
    }

    /** Mouse no meio da fatia {@code slice}. */
    private static void wheelMouse(int slice) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (wheelScreen.width != width || wheelScreen.height != height) {
            wheelScreen.init(minecraft, width, height);
        }
        int[] point = wheelScreen.pointOf(slice);
        mouseX = point[0];
        mouseY = point[1];
    }
}
