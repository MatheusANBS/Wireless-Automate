package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * Modo de captura para conferir a tela sem monitor. Só roda com a variável de ambiente
 * {@code WA_SCREENSHOT} (diretório de saída):
 *
 * <pre>WA_SCREENSHOT=$PWD/run/shots xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient</pre>
 *
 * Na tela de título desenha uma {@link RouterScreen} com um snapshot de exemplo (fornalha, rede
 * "Base"), passa por alguns estados, salva um PNG de cada e fecha o jogo. A tela não é aberta com
 * {@code setScreen} porque, sem mundo, o {@code tick} de uma tela de contêiner falha sem jogador.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class DevScreenshot {
    private static final String OUTPUT = System.getenv("WA_SCREENSHOT");
    /** Tiques entre um estado e a captura dele (vários quadros desenhados no meio). */
    private static final int STEP_TICKS = 10;

    private static RouterScreen screen;
    private static int ticks;
    private static int mouseX = -1;
    private static int mouseY = -1;

    private DevScreenshot() {
    }

    /** Um estado da tela e o nome do PNG tirado depois dele. */
    private record Step(Runnable setup, String file) {
    }

    private static final List<Step> STEPS = List.of(
            new Step(() -> screen.previewFace(Direction.DOWN), "1-itens"),
            new Step(() -> screen.previewType(ResourceType.ENERGY), "2-energia"),
            new Step(() -> {
                screen.previewType(ResourceType.ITEM);
                screen.previewFace(Direction.NORTH);
                screen.previewExpanded(true);
                int[] center = screen.previewFaceCenter(Direction.UP);
                mouseX = center[0];
                mouseY = center[1];
            }, "3-mais"),
            new Step(() -> {
                mouseX = mouseY = -1;
                screen.previewNetworkList(true);
            }, "4-redes"),
            new Step(() -> {
                screen.previewNetworkList(false);
                screen.previewExpanded(false);
                screen.previewType(ResourceType.FLUID);
                RouterSnapshot s = screen.getMenu().snapshot();
                screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), "Fornalha Norte", s.tier(), s.facing(),
                        s.network(), s.networks(), s.powered(), s.machine(), s.machineState(), s.faces()));
                int[] center = screen.previewEditFilterCenter();
                mouseX = center[0];
                mouseY = center[1];
            }, "5-fluidos"),
            new Step(() -> {
                mouseX = mouseY = -1;
                screen.previewType(ResourceType.ITEM);
                screen.previewRename("Fornalha Norte 2");
            }, "6-renomear"));

    /** Todos os passos, na ordem: os da tela e os do visor 3D. */
    private static final List<Step> SEQUENCE = Stream.concat(STEPS.stream(), viewSteps().stream()).toList();

    /**
     * Visor 3D: a fornalha com o roteador em cima em dois ângulos, com uma face selecionada e o
     * mouse sobre outra (o marcador mostra onde ele está, para conferir a escolha da face); um baú
     * virado para o leste (renderizador de entidade) com o roteador no norte; e sem máquina.
     */
    private static List<Step> viewSteps() {
        return List.of(
                new Step(() -> {
                    screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); // sai da renomeação do passo anterior
                    screen.previewType(ResourceType.ITEM);
                    screen.previewFace(Direction.NORTH);
                    screen.previewViewDefault();
                    int[] point = screen.previewViewFaceCenter(Direction.EAST);
                    mouseX = point[0];
                    mouseY = point[1];
                }, "7-visor"),
                new Step(() -> {
                    screen.previewFace(Direction.EAST);
                    screen.previewView(-120f, -20f, 1.1f);
                    int[] point = screen.previewViewFaceCenter(Direction.NORTH);
                    mouseX = point[0];
                    mouseY = point[1];
                }, "8-visor-de-baixo"),
                new Step(() -> {
                    RouterSnapshot s = screen.getMenu().snapshot();
                    screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), "", RouterTier.ADVANCED, Direction.NORTH,
                            s.network(), s.networks(), s.powered(), new ItemStack(Items.CHEST),
                            Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), s.faces()));
                    screen.previewFace(Direction.UP);
                    screen.previewViewDefault();
                    int[] point = screen.previewViewFaceCenter(Direction.EAST);
                    mouseX = point[0];
                    mouseY = point[1];
                }, "9-visor-bau"),
                new Step(() -> {
                    RouterSnapshot s = screen.getMenu().snapshot();
                    screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), "", RouterTier.BASIC, Direction.WEST,
                            s.network(), s.networks(), s.powered(), ItemStack.EMPTY, Blocks.AIR.defaultBlockState(),
                            s.faces()));
                    screen.previewViewDefault();
                    mouseX = mouseY = -1;
                }, "10-visor-sem-maquina"));
    }

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        if (OUTPUT == null || !(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (screen == null) {
            RouterMenu menu = new RouterMenu(0, new Inventory(null), sample());
            menu.applyThroughput(new long[] {1_240, 0, 0, 0});
            screen = new RouterScreen(menu, new Inventory(null), Component.translatable("block.wirelessautomate.router"),
                    true);
        }
        if (screen.width != title.width || screen.height != title.height) {
            screen.init(minecraft, title.width, title.height);
        }
        // a tela de título já desenhou textos na frente; limpa a profundidade para a nossa ficar por cima
        event.getGuiGraphics().flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        screen.renderWithTooltip(event.getGuiGraphics(), mouseX, mouseY, event.getPartialTick());
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
        }
        if (screen == null || ticks < 60 || ticks % STEP_TICKS != 0) {
            return;
        }
        int step = (ticks - 60) / STEP_TICKS;
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

    /** Fornalha com o roteador Elite preso em cima, na rede "Base". */
    private static RouterSnapshot sample() {
        List<FaceView> faces = new ArrayList<>(Collections.nCopies(ResourceType.values().length * 6,
                new FaceView(PortMode.NONE, 0, RedstoneMode.IGNORE, -1, 0, false)));
        for (Direction direction : Direction.values()) {
            faces.set(RouterSnapshot.index(ResourceType.ITEM, direction),
                    new FaceView(PortMode.NONE, 0, RedstoneMode.IGNORE, 1, 0, false));
        }
        faces.set(RouterSnapshot.index(ResourceType.ITEM, Direction.UP),
                new FaceView(PortMode.INSERT, 0, RedstoneMode.IGNORE, 1, 0, false));
        faces.set(RouterSnapshot.index(ResourceType.ITEM, Direction.DOWN),
                new FaceView(PortMode.EXTRACT, 0, RedstoneMode.IGNORE, 1, 0, false));
        faces.set(RouterSnapshot.index(ResourceType.ITEM, Direction.NORTH),
                new FaceView(PortMode.INSERT, 5, RedstoneMode.HIGH, 1, 0, false));
        faces.set(RouterSnapshot.index(ResourceType.ITEM, Direction.EAST),
                new FaceView(PortMode.BOTH, 0, RedstoneMode.IGNORE, 1, 0, false));
        UUID base = UUID.nameUUIDFromBytes("base".getBytes());
        List<NetworkEntry> networks = List.of(
                new NetworkEntry(base, "Base", 0x3D8BFF, true),
                new NetworkEntry(UUID.nameUUIDFromBytes("fluidos".getBytes()), "Fluidos", 0x45D6CC, true),
                new NetworkEntry(UUID.nameUUIDFromBytes("energia".getBytes()), "Energia", 0xFFB020, false),
                new NetworkEntry(UUID.nameUUIDFromBytes("minerio".getBytes()), "Minério", 0xD8875A, true));
        return new RouterSnapshot(new BlockPos(0, 64, 0), "", RouterTier.ELITE, Direction.UP, Optional.of(base),
                networks, false, new ItemStack(Items.FURNACE), Blocks.FURNACE.defaultBlockState(), List.copyOf(faces));
    }
}
