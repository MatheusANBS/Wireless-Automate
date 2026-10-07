package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.FluidEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ItemEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.FilterView;
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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
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

    // ------------------------------------------------------------------ tela de filtro

    /** Desenhada no lugar da {@link #screen} a partir do primeiro passo de filtro. */
    private static FilterScreen filterScreen;

    private static final List<Step> FILTER_STEPS = List.of(
            new Step(() -> {
                // resumo do filtro na tela do roteador: lista branca embaixo, negra no norte
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); // sai da renomeação do passo anterior
                RouterSnapshot s = screen.getMenu().snapshot();
                List<FaceView> faces = new ArrayList<>(s.faces());
                int down = RouterSnapshot.index(ResourceType.ITEM, Direction.DOWN);
                int north = RouterSnapshot.index(ResourceType.ITEM, Direction.NORTH);
                FaceView d = faces.get(down);
                FaceView n = faces.get(north);
                faces.set(down, new FaceView(d.mode(), d.priority(), d.redstone(), d.slots(), 12, false));
                faces.set(north, new FaceView(n.mode(), n.priority(), n.redstone(), n.slots(), 3, true));
                screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), s.name(), s.tier(), s.facing(), s.network(),
                        s.networks(), s.powered(), s.machine(), s.machineState(), List.copyOf(faces)));
                screen.previewType(ResourceType.ITEM);
                screen.previewFace(Direction.DOWN);
                mouseX = mouseY = -1;
            }, "f1-roteador-filtro"),
            new Step(() -> {
                mouseX = mouseY = -1;
                filterScreen = filterScreen(itemFilter(Filter.ListMode.WHITELIST), Direction.DOWN);
                filterScreen.previewSelect(2);
                int[] center = filterScreen.previewEntryCenter(18);
                mouseX = center[0];
                mouseY = center[1];
            }, "f2-filtro-itens"),
            new Step(() -> {
                mouseX = mouseY = -1;
                filterScreen.previewMore(true, "#c:ores");
            }, "f3-filtro-mais"),
            new Step(() -> {
                mouseX = mouseY = -1;
                filterScreen = filterScreen(fluidFilter(), Direction.NORTH);
                filterScreen.previewSelect(0);
            }, "f4-filtro-fluidos"),
            new Step(() -> {
                filterScreen = filterScreen(itemFilter(Filter.ListMode.BLACKLIST).withMatchComponents(true), Direction.UP);
                filterScreen.previewArmClear();
            }, "f5-filtro-negra"),
            new Step(() -> {
                mouseX = mouseY = -1;
                filterScreen = filterScreen(Filter.EMPTY, null);
            }, "f6-filtro-cartao-vazio"));

    private static final List<Step> ALL_STEPS = Stream.concat(STEPS.stream(), FILTER_STEPS.stream()).toList();

    /** Filtro de uma face (ou de um cartão, sem face) com o inventário de exemplo e a cor Elite. */
    private static FilterScreen filterScreen(Filter filter, @Nullable Direction face) {
        ResourceType type = filter.entries().stream().anyMatch(e -> e instanceof FluidEntry) ? ResourceType.FLUID
                : ResourceType.ITEM;
        FilterView view = new FilterView(type, face == null ? Optional.empty() : Optional.of(new BlockPos(0, 64, 0)),
                Optional.ofNullable(face), filter, face == null);
        Inventory inventory = new Inventory(null);
        ItemStack[] items = {new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.COAL, 23),
                new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.REDSTONE, 41), new ItemStack(Items.DIAMOND, 7),
                new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH, 50)};
        for (int i = 0; i < items.length; i++) {
            inventory.setItem(i < 5 ? i : 9 + i, items[i]);
        }
        FilterScreen created = new FilterScreen(new FilterMenu(0, inventory, view), inventory,
                Component.translatable("gui.wirelessautomate.filter.title"), true);
        created.previewTrim(face == null ? 0xFF45D6CC : GuiPaint.tierColor(RouterTier.ELITE));
        // os passos mexem em widgets: a tela precisa estar montada antes do primeiro quadro
        Minecraft minecraft = Minecraft.getInstance();
        created.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        return created;
    }

    private static Filter itemFilter(Filter.ListMode mode) {
        List<FilterEntry> entries = new ArrayList<>();
        entries.add(new ItemEntry(new ItemStack(Items.IRON_INGOT), 0));
        entries.add(new ItemEntry(new ItemStack(Items.GOLD_INGOT), 0));
        entries.add(new ItemEntry(new ItemStack(Items.COAL), 64));
        entries.add(new TagEntry(ResourceLocation.parse("c:ingots"), 0));
        entries.add(new ModEntry("mekanism", 0));
        ItemStack[] more = {new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.EMERALD), new ItemStack(Items.LAPIS_LAZULI), new ItemStack(Items.QUARTZ),
                new ItemStack(Items.RAW_IRON), new ItemStack(Items.RAW_GOLD), new ItemStack(Items.RAW_COPPER),
                new ItemStack(Items.NETHERITE_SCRAP), new ItemStack(Items.ANCIENT_DEBRIS), new ItemStack(Items.OBSIDIAN),
                new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.SAND), new ItemStack(Items.GRAVEL), new ItemStack(Items.CLAY_BALL),
                new ItemStack(Items.SLIME_BALL), new ItemStack(Items.BONE), new ItemStack(Items.STRING),
                new ItemStack(Items.GUNPOWDER), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.BLAZE_ROD),
                new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.PRISMARINE_SHARD), new ItemStack(Items.SPONGE)};
        for (ItemStack stack : more) {
            entries.add(new ItemEntry(stack, 0));
        }
        entries.add(new TagEntry(ResourceLocation.parse("c:ores"), 0));
        return new Filter(mode, false, entries);
    }

    private static Filter fluidFilter() {
        return new Filter(Filter.ListMode.WHITELIST, false, List.of(
                new FluidEntry(new FluidStack(Fluids.WATER, 1), 8_000),
                new FluidEntry(new FluidStack(Fluids.LAVA, 1), 0),
                new TagEntry(ResourceLocation.parse("c:oil"), 0),
                new ModEntry("mekanism", 500)));
    }

    // ------------------------------------------------------------------ eventos

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
        AbstractContainerScreen<?> active = filterScreen != null ? filterScreen : screen;
        if (active.width != title.width || active.height != title.height) {
            active.init(minecraft, title.width, title.height);
        }
        // a tela de título já desenhou textos na frente; limpa a profundidade para a nossa ficar por cima
        event.getGuiGraphics().flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        active.renderWithTooltip(event.getGuiGraphics(), mouseX, mouseY, event.getPartialTick());
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
            save(minecraft, ALL_STEPS.get(step - 1).file());
        }
        if (step >= ALL_STEPS.size()) {
            minecraft.stop();
            return;
        }
        ALL_STEPS.get(step).setup().run();
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
