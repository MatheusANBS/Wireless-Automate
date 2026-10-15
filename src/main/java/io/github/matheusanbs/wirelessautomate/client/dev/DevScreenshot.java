package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotFilter.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotGalleries.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotLinker.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotResonator.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotRouter.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotTablet.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotWheel.*;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.opengl.GL11;
import io.github.matheusanbs.wirelessautomate.client.GuiText;

/**
 * Modo de captura para conferir a tela sem monitor. Só roda com a variável de ambiente
 * {@code WA_SCREENSHOT} (diretório de saída):
 *
 * <pre>WA_SCREENSHOT=$PWD/run/shots xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient</pre>
 *
 * Na tela de título desenha uma {@link RouterScreen} com um snapshot de exemplo (fornalha com Itens
 * na rede "Linha 5x", Fluidos sem rede e Energia na "Base"), passa por alguns estados, salva um PNG de cada e fecha o jogo. A tela não é aberta com
 * {@code setScreen} porque, sem mundo, o {@code tick} de uma tela de contêiner falha sem jogador.
 * Com {@code WA_SCREENSHOT_ONLY=giro} tira só a galeria do giro do roteador ({@code giro-parede}); com
 * {@code WA_SCREENSHOT_ONLY=blocos}, só a galeria dos armazenamentos, com o contorno da colisão e a fileira
 * dos itens como aparecem no inventário ({@code blocos-galeria}); com {@code WA_SCREENSHOT_ONLY=capa}, só os
 * renders do roteador sobre magenta para a capa do CurseForge ({@code capa-renders}, ver
 * {@code scripts/curseforge/gerar_capa.py}).
 *
 * <p>Esta classe é o motor (a sequência, o desenho sobre a tela de título e o PNG de cada passo); os passos de
 * cada tela ficam nas classes {@code Screenshot*} deste pacote.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class DevScreenshot {
    private static final String OUTPUT = System.getenv("WA_SCREENSHOT");
    /** Prefixo opcional ({@code WA_SCREENSHOT_ONLY=giro}): só as capturas cujo nome começa com ele. */
    static final String ONLY = System.getenv("WA_SCREENSHOT_ONLY");
    /** Tiques entre um estado e a captura dele (vários quadros desenhados no meio). */
    private static final int STEP_TICKS = 10;
    /** Rede de nome comprido do exemplo. */
    static final UUID LONG = UUID.nameUUIDFromBytes("longa".getBytes());
    /** O tema de antes das capturas escuras, devolvido depois delas. */
    static GuiTheme themeBefore = GuiTheme.LIGHT;
    private static int ticks;
    /** Tiques contados só sem a tela de carregamento por cima. */
    private static int ready;
    static int mouseX = -1;
    static int mouseY = -1;

    private DevScreenshot() {
    }

    /** Um estado da tela e o nome do PNG tirado depois dele. */
    record Step(Runnable setup, String file) {
    }

    /**
     * Todos os passos, na ordem: roteador, visor 3D, filtro, cartões, upgrade, Tablet, Vinculador, Núcleo do
     * Ressonador e as galerias (giro, blocos, capa, roda).
     */
    private static final List<Step> SEQUENCE = Stream.of(STEPS, viewSteps(), filterSteps(), cardSteps(),
                    upgradeSteps(), tabletSteps(), linkerSteps(), resonatorSteps(), spinSteps(), blockSteps(), coverSteps(), wheelSteps())
            .flatMap(List::stream)
            .filter(step -> ONLY == null || step.file().startsWith(ONLY))
            .toList();

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        if (OUTPUT == null || !(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (wheelGallery && wheelScreen != null) {
            if (wheelScreen.width != title.width || wheelScreen.height != title.height) {
                wheelScreen.init(minecraft, title.width, title.height);
            }
            event.getGuiGraphics().flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            wheelScreen.render(event.getGuiGraphics(), mouseX, mouseY, event.getPartialTick());
            if (GuiText.clipCount() > 0) {
                WirelessAutomate.LOGGER.warn("Roda: {} texto(s) cortado(s)", GuiText.clipCount());
            }
            drawCursor(event.getGuiGraphics());
            return;
        }
        if (wheelGallery) {
            return;
        }
        if (spinGallery || blockGallery || coverGallery) {
            event.getGuiGraphics().flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            if (coverGallery) {
                renderCoverGallery(event.getGuiGraphics(), title.width, title.height);
            } else if (blockGallery) {
                renderBlockGallery(event.getGuiGraphics(), title.width, title.height);
            } else {
                renderSpinGallery(event.getGuiGraphics(), title.width, title.height);
            }
            return;
        }
        if (screen == null) {
            RouterMenu menu = new RouterMenu(0, new Inventory(null), sample());
            menu.applyThroughput(new long[] {1_240, 0, 0, 0});
            screen = new RouterScreen(menu, new Inventory(null), Component.translatable("block.wirelessautomate.router"),
                    true);
        }
        AbstractContainerScreen<?> active = resonatorScreen != null ? resonatorScreen
                : linkerScreen != null ? linkerScreen
                : tabletScreen != null ? tabletScreen
                : filterScreen != null ? filterScreen : screen;
        if (active.width != title.width || active.height != title.height) {
            active.init(minecraft, title.width, title.height);
        }
        // a tela de título já desenhou textos na frente; limpa a profundidade para a nossa ficar por cima
        event.getGuiGraphics().flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        active.renderWithTooltip(event.getGuiGraphics(), mouseX, mouseY, event.getPartialTick());
        drawCursor(event.getGuiGraphics());
    }

    /** Marcador de 5×5 px no ponto do mouse simulado (as capturas não mostram o cursor). */
    private static void drawCursor(GuiGraphics g) {
        if (mouseX < 0) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(mouseX - 2, mouseY - 2, mouseX + 3, mouseY + 3, 0xFF000000);
        g.fill(mouseX - 1, mouseY - 1, mouseX + 2, mouseY + 2, 0xFFFF2BD6);
        g.pose().popPose();
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (OUTPUT == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ticks++;
        if (ticks == 1) {
            minecraft.getWindow().setWindowed(1280, 800);
            // sem options.txt o jogo abre a tela de acessibilidade no lugar da de título (como no e2e)
            minecraft.options.onboardAccessibility = false;
            String language = System.getenv("WA_SCREENSHOT_LANG");
            if (language != null) {
                // só nesta sessão: o options.txt não é salvo
                minecraft.getLanguageManager().setSelected(language);
                minecraft.reloadResourcePacks();
            }
        }
        if (minecraft.getOverlay() != null) {
            return; // carregando recursos (a troca de idioma acima): os passos esperam
        }
        ready++;
        if (minecraft.screen instanceof AccessibilityOnboardingScreen) {
            // a flag acima chega tarde quando a tela já foi escolhida: troca pela de título
            minecraft.setScreen(new TitleScreen(true));
        }
        if (screen == null && !spinGallery && !blockGallery && !coverGallery && !wheelGallery
                || ready < 60 || ready % STEP_TICKS != 0) {
            return;
        }
        int step = (ready - 60) / STEP_TICKS;
        // o último quadro mostra o estado do passo anterior: salva, depois prepara o próximo
        if (step > 0) {
            save(minecraft, SEQUENCE.get(step - 1).file());
        }
        if (step >= SEQUENCE.size()) {
            minecraft.stop();
            return;
        }
        SEQUENCE.get(step).setup().run();
    }

    private static void save(Minecraft minecraft, String name) {
        Path dir = Path.of(OUTPUT);
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Files.createDirectories(dir);
            image.writeToFile(dir.resolve(name + ".png"));
            WirelessAutomate.LOGGER.info("Captura salva em {}", dir.resolve(name + ".png"));
        } catch (IOException e) {
            WirelessAutomate.LOGGER.error("Falha ao salvar a captura {}", name, e);
        }
    }
}
