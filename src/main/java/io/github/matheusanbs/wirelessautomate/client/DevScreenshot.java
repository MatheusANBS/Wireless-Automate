package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.FluidEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ItemEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorMenu;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorView;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.FilterView;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
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
 * Na tela de título desenha uma {@link RouterScreen} com um snapshot de exemplo (fornalha com Itens
 * na rede "Linha 5x", Fluidos sem rede e Energia na "Base"), passa por alguns estados, salva um PNG de cada e fecha o jogo. A tela não é aberta com
 * {@code setScreen} porque, sem mundo, o {@code tick} de uma tela de contêiner falha sem jogador.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class DevScreenshot {
    private static final String OUTPUT = System.getenv("WA_SCREENSHOT");
    /** Tiques entre um estado e a captura dele (vários quadros desenhados no meio). */
    private static final int STEP_TICKS = 10;
    /** Rede de nome comprido do exemplo. */
    private static final UUID LONG = UUID.nameUUIDFromBytes("longa".getBytes());

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
                // a lista é da aba: na de energia, marca a "Base"
                screen.previewNetworkList(false);
                screen.previewType(ResourceType.ENERGY);
                screen.previewNetworkList(true);
                int[] center = screen.previewNetworkCenter();
                mouseX = center[0];
                mouseY = center[1] + 15 + 2 + 12 + 12 + 6;
            }, "4b-redes-energia"),
            new Step(() -> {
                screen.previewNetworkList(false);
                screen.previewType(ResourceType.ITEM);
                int[] center = screen.previewTabCenter(ResourceType.FLUID);
                mouseX = center[0];
                mouseY = center[1];
            }, "4c-aba-dica"),
            new Step(() -> {
                int[] center = screen.previewModeCenter(PortMode.BOTH);
                mouseX = center[0];
                mouseY = center[1];
            }, "4d-armazem-dica"),
            new Step(() -> {
                // nome de rede comprido: a pílula encolhe o nome para caber à direita das abas
                RouterSnapshot s = screen.getMenu().snapshot();
                List<Optional<UUID>> networks = new ArrayList<>(s.typeNetworks());
                networks.set(ResourceType.ITEM.ordinal(), Optional.of(LONG));
                screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), "Fornalha da linha de processamento", s.tier(),
                        s.facing(), List.copyOf(networks), s.networks(), s.powered(), s.machine(), s.machineState(),
                        s.faces()));
                mouseX = mouseY = -1;
            }, "4e-nome-longo"),
            new Step(() -> {
                screen.previewNetworkList(false);
                screen.previewExpanded(false);
                screen.previewType(ResourceType.FLUID);
                RouterSnapshot s = screen.getMenu().snapshot();
                screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), "Fornalha Norte", s.tier(), s.facing(),
                        sample().typeNetworks(), s.networks(), s.powered(), s.machine(), s.machineState(), s.faces()));
                int[] center = screen.previewEditFilterCenter();
                mouseX = center[0];
                mouseY = center[1];
            }, "5-fluidos"),
            new Step(() -> {
                mouseX = mouseY = -1;
                screen.previewType(ResourceType.ITEM);
                screen.previewRename("Fornalha Norte 2");
            }, "6-renomear"));

    /** Todos os passos, na ordem: os da tela, os do visor 3D, os da tela de filtro e os dos cartões. */
    private static final List<Step> SEQUENCE = Stream.of(STEPS, viewSteps(), filterSteps(), cardSteps(),
                    upgradeSteps(), configuratorSteps(), tabletSteps())
            .flatMap(List::stream).toList();

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
                            s.typeNetworks(), s.networks(), s.powered(), new ItemStack(Items.CHEST),
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
                            s.typeNetworks(), s.networks(), s.powered(), ItemStack.EMPTY, Blocks.AIR.defaultBlockState(),
                            s.faces()));
                    screen.previewViewDefault();
                    mouseX = mouseY = -1;
                }, "10-visor-sem-maquina"));
    }

    // ------------------------------------------------------------------ tela de filtro

    /** Desenhada no lugar da {@link #screen} a partir do primeiro passo de filtro. */
    private static FilterScreen filterScreen;

    /**
     * Tela de filtro: o resumo na tela do roteador e a FilterScreen com um filtro de exemplo (grade
     * com seleção, "Mais" aberto, fluidos, lista negra com Limpar armado e cartão vazio).
     */
    private static List<Step> filterSteps() {
        return List.of(
                new Step(() -> {
                    // resumo do filtro na tela do roteador, de volta à fornalha (o visor terminou sem
                    // máquina): lista branca embaixo, negra no norte
                    RouterSnapshot s = sample();
                    List<FaceView> faces = new ArrayList<>(s.faces());
                    int down = RouterSnapshot.index(ResourceType.ITEM, Direction.DOWN);
                    int north = RouterSnapshot.index(ResourceType.ITEM, Direction.NORTH);
                    FaceView d = faces.get(down);
                    FaceView n = faces.get(north);
                    faces.set(down, new FaceView(d.mode(), d.priority(), d.redstone(), d.slots(), 12, false));
                    faces.set(north, new FaceView(n.mode(), n.priority(), n.redstone(), n.slots(), 3, true));
                    screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), "Fornalha Norte", s.tier(), s.facing(),
                            s.typeNetworks(), s.networks(), s.powered(), s.machine(), s.machineState(), List.copyOf(faces)));
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
                    filterScreen = filterScreen(itemFilter(Filter.ListMode.BLACKLIST).withMatchComponents(true),
                            Direction.UP);
                    filterScreen.previewArmClear();
                }, "f5-filtro-negra"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    filterScreen = filterScreen(Filter.EMPTY, null);
                }, "f6-filtro-cartao-vazio"));
    }

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

    // ------------------------------------------------------------------ slots de cartão

    /**
     * Slots de Cartão de Filtro na tela do roteador: a face de baixo com 12 entradas e um cartão
     * de itens no primeiro slot, com o inventário cheio de exemplo; a dica do slot vazio; a aba
     * de energia com "Mais" aberto (sem slots de cartão); e a de fluidos com um cartão no segundo slot.
     */
    private static List<Step> cardSteps() {
        return List.of(
                new Step(() -> {
                    filterScreen = null;
                    mouseX = mouseY = -1;
                    screen = cardScreen();
                    screen.previewType(ResourceType.ITEM);
                    screen.previewFace(Direction.DOWN);
                }, "c1-cartoes"),
                new Step(() -> {
                    int[] center = screen.previewCardSlotCenter(1);
                    mouseX = center[0];
                    mouseY = center[1];
                }, "c2-cartao-dica"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    screen.previewType(ResourceType.ENERGY);
                    screen.previewFace(Direction.NORTH);
                    screen.previewExpanded(true);
                }, "c3-energia-mais"),
                new Step(() -> {
                    // sem servidor, os slots não trocam sozinhos: põe o que o servidor mandaria
                    screen.previewType(ResourceType.FLUID);
                    screen.previewFace(Direction.UP);
                    screen.previewExpanded(false);
                    screen.getMenu().getSlot(0).set(ItemStack.EMPTY);
                    screen.getMenu().getSlot(1).set(card(ResourceType.FLUID, fluidFilter(), 1));
                }, "c4-fluidos"));
    }

    // ------------------------------------------------------------------ slot de upgrade

    /**
     * Slot do Upgrade de chunk loading, no fim da linha dos cartões: ativo com a dica, vazio com a
     * dica, inativo pelo limite do dono e, na aba de energia, desligado na config.
     */
    private static List<Step> upgradeSteps() {
        return List.of(
                new Step(() -> {
                    screen = cardScreen();
                    screen.previewType(ResourceType.ITEM);
                    screen.previewFace(Direction.DOWN);
                    upgradeState(ChunkLoadState.ACTIVE);
                    int[] center = screen.previewUpgradeSlotCenter();
                    mouseX = center[0];
                    mouseY = center[1];
                }, "u1-upgrade-ativo"),
                new Step(() -> upgradeState(ChunkLoadState.NONE), "u2-upgrade-vazio"),
                new Step(() -> upgradeState(ChunkLoadState.LIMIT), "u3-upgrade-limite"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    screen.previewType(ResourceType.ENERGY);
                    upgradeState(ChunkLoadState.DISABLED);
                }, "u4-upgrade-desligado-energia"));
    }

    /** Põe (ou tira) o upgrade no slot e o estado no snapshot, como o servidor mandaria. */
    private static void upgradeState(ChunkLoadState state) {
        screen.getMenu().getSlot(RouterMenu.UPGRADE_SLOT).set(state == ChunkLoadState.NONE
                ? ItemStack.EMPTY : new ItemStack(ModItems.CHUNK_LOADER_UPGRADE.get()));
        RouterSnapshot s = screen.getMenu().snapshot();
        screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), s.name(), s.tier(), s.facing(), s.typeNetworks(),
                s.networks(), s.powered(), s.machine(), s.machineState(), s.faces(), state));
    }

    /** Tela do roteador com um cartão de itens no primeiro slot e o inventário de exemplo. */
    private static RouterScreen cardScreen() {
        RouterSnapshot s = sample();
        List<FaceView> faces = new ArrayList<>(s.faces());
        int down = RouterSnapshot.index(ResourceType.ITEM, Direction.DOWN);
        FaceView d = faces.get(down);
        faces.set(down, new FaceView(d.mode(), d.priority(), d.redstone(), d.slots(), 12, false));
        RouterSnapshot snapshot = new RouterSnapshot(s.pos(), "Fornalha Norte", s.tier(), s.facing(), s.typeNetworks(),
                s.networks(), s.powered(), s.machine(), s.machineState(), List.copyOf(faces));

        Inventory inventory = new Inventory(null);
        ItemStack[] items = {new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.COAL, 23),
                card(ResourceType.ITEM, itemFilter(Filter.ListMode.WHITELIST), 3), card(ResourceType.FLUID, fluidFilter(), 1),
                new ItemStack(ModItems.FILTER_CARD.get(), 8), new ItemStack(Items.OAK_LOG, 32),
                new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.TORCH, 50)};
        for (int i = 0; i < items.length; i++) {
            inventory.setItem(i < 5 ? i : 9 + i, items[i]);
        }
        RouterMenu menu = new RouterMenu(0, inventory, snapshot);
        menu.applyThroughput(new long[] {1_240, 0, 0, 0});
        menu.getSlot(0).set(card(ResourceType.ITEM, new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new ItemEntry(new ItemStack(Items.DIAMOND), 0))), 1));
        RouterScreen created = new RouterScreen(menu, inventory, Component.translatable("block.wirelessautomate.router"),
                true);
        Minecraft minecraft = Minecraft.getInstance();
        created.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        return created;
    }

    private static ItemStack card(ResourceType type, Filter filter, int count) {
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get(), count);
        FilterCardItem.setContents(card, new FilterCardItem.Contents(type, filter));
        return card;
    }

    // ------------------------------------------------------------------ Tablet de rede

    /** Desenhada no lugar das outras a partir do primeiro passo do Tablet. */
    private static TabletScreen tabletScreen;

    /**
     * Tablet de rede com um snapshot de exemplo (os nós do rascunho visual, em volta do jogador):
     * cada aba, a seleção para mover, o nó escolhido no mapa, a dica de um nó e a nova rede aberta.
     */
    private static List<Step> tabletSteps() {
        return List.of(
                new Step(() -> {
                    mouseX = mouseY = -1;
                    TabletMenu menu = new TabletMenu(0, new Inventory(null), sampleTablet());
                    tabletScreen = new TabletScreen(menu, new Inventory(null),
                            Component.translatable("item.wirelessautomate.network_tablet"), true);
                    Minecraft minecraft = Minecraft.getInstance();
                    tabletScreen.init(minecraft, minecraft.getWindow().getGuiScaledWidth(),
                            minecraft.getWindow().getGuiScaledHeight());
                }, "t1-tablet-lista"),
                new Step(() -> {
                    int[] center = tabletScreen.nodeRowCenter(sampleTablet().nodes().get(3).key());
                    mouseX = center[0];
                    mouseY = center[1];
                }, "t2-tablet-lista-dica"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    tabletScreen.previewMulti(true, 3);
                }, "t3-tablet-selecionar"),
                new Step(() -> {
                    tabletScreen.previewMulti(false, 0);
                    tabletScreen.previewTab(TabletScreen.Tab.MAP);
                    tabletScreen.previewMapSelect(3);
                }, "t4-tablet-mapa"),
                new Step(() -> tabletScreen.previewTab(TabletScreen.Tab.STATS), "t5-tablet-estatisticas"),
                new Step(() -> {
                    tabletScreen.previewTab(TabletScreen.Tab.NETWORKS);
                    tabletScreen.previewNetwork(0);
                }, "t6-tablet-redes"),
                new Step(() -> {
                    tabletScreen.previewNetwork(2);
                    tabletScreen.previewNewOpen("Utilidades");
                }, "t7-tablet-redes-alheia-nova"),
                new Step(() -> tabletScreen.previewTab(TabletScreen.Tab.GROUPS), "t8-tablet-grupos"));
    }

    /** Nós do rascunho visual em volta do jogador em (0, 64, 0), com redes, um grupo e status variados. */
    private static TabletSnapshot sampleTablet() {
        UUID base = UUID.nameUUIDFromBytes("base".getBytes());
        UUID fluids = UUID.nameUUIDFromBytes("fluidos".getBytes());
        UUID energy = UUID.nameUUIDFromBytes("energia".getBytes());
        UUID ore = UUID.nameUUIDFromBytes("minerio".getBytes());
        UUID line = UUID.nameUUIDFromBytes("linha".getBytes());
        List<TabletSnapshot.NetworkView> networks = List.of(
                new TabletSnapshot.NetworkView(base, "Base", 0x45D6CC, "Dev", true, true, false, false, 5, 1, 1, 2,
                        42_000, 61, 1_240, 0, 0),
                new TabletSnapshot.NetworkView(fluids, "Fluidos", 0x3D8BFF, "Dev", true, true, false, false, 2, 0, 0, 0,
                        18_000, 20, 0, 48_000, 0),
                new TabletSnapshot.NetworkView(energy, "Energia", 0xFFB020, "Convidado", false, false, true, false, 3, 0, 0,
                        0, 9_000, 20, 0, 0, 120_000),
                new TabletSnapshot.NetworkView(ore, "Minério", 0xD8875A, "Dev", true, true, true, true, 4, 0, 0, 0,
                        0, 0, 0, 0, 0),
                new TabletSnapshot.NetworkView(line, "Linha 5x", 0xA46CFF, "Dev", true, true, false, true, 8, 0, 0, 0,
                        0, 0, 0, 0, 0));
        int extractItems = NodeIndex.role(ResourceType.ITEM, NodeIndex.EXTRACT);
        int insertItems = NodeIndex.role(ResourceType.ITEM, NodeIndex.INSERT);
        int insertFluids = NodeIndex.role(ResourceType.FLUID, NodeIndex.INSERT);
        int extractEnergy = NodeIndex.role(ResourceType.ENERGY, NodeIndex.EXTRACT);
        int insertEnergy = NodeIndex.role(ResourceType.ENERGY, NodeIndex.INSERT);
        int storageItems = NodeIndex.role(ResourceType.ITEM, NodeIndex.STORAGE);
        List<TabletSnapshot.NodeView> nodes = List.of(
                tabletNode(-5, 66, -2, "Fornalha Norte", "furnace", RouterTier.ELITE, base, base, energy,
                        extractItems | insertItems | insertEnergy, TabletSnapshot.NodeStatus.ACTIVE),
                tabletNode(-5, 66, 2, "Fornalha Sul", "furnace", RouterTier.ELITE, base, base, energy,
                        extractItems | insertItems, TabletSnapshot.NodeStatus.ACTIVE),
                tabletNode(5, 66, -2, "", "chest", RouterTier.ELITE, base, null, null, insertItems,
                        TabletSnapshot.NodeStatus.IDLE),
                tabletNode(5, 66, 2, "Baú de lingotes 2", "chest", RouterTier.ELITE, base, null, null, insertItems,
                        TabletSnapshot.NodeStatus.FULL),
                tabletNode(38, 64, 40, "Tanque de água", "cauldron", RouterTier.ADVANCED, null, fluids, null, insertFluids,
                        TabletSnapshot.NodeStatus.IDLE),
                tabletNode(44, 64, 46, "Separador eletrolítico", "blast_furnace", RouterTier.ELITE, ore, fluids, energy,
                        insertFluids | insertEnergy, TabletSnapshot.NodeStatus.PAUSED),
                tabletNode(-60, 40, -75, "Reator de fissão", "beacon", RouterTier.ULTIMATE, null, null, energy,
                        extractEnergy, TabletSnapshot.NodeStatus.ACTIVE),
                tabletNode(-70, 70, 47, "Fazenda de cana", "hopper", RouterTier.BASIC, base, null, null, extractItems,
                        TabletSnapshot.NodeStatus.UNLOADED),
                tabletNode(12, 64, -20, "Barril de sobras", "barrel", RouterTier.ADVANCED, base, null, null, storageItems,
                        TabletSnapshot.NodeStatus.ACTIVE),
                tabletNode(0, 64, 9, "", "dropper", RouterTier.BASIC, null, null, null, 0,
                        TabletSnapshot.NodeStatus.NO_NETWORK));
        List<TabletSnapshot.GroupView> groups = List.of(
                new TabletSnapshot.GroupView(UUID.nameUUIDFromBytes("g1".getBytes()), "Linha 5x", "Dev", true, true,
                        List.of(ore, line, fluids)),
                new TabletSnapshot.GroupView(UUID.nameUUIDFromBytes("g2".getBytes()), "Base principal", "Dev", true, false,
                        List.of(base)));
        return new TabletSnapshot(false, ResourceLocation.withDefaultNamespace("overworld"), new BlockPos(0, 64, 0),
                Optional.of(base), 80_000, 500_000, TabletSnapshot.Query.DEFAULT, 10, 10, networks, groups, nodes,
                Component.empty(), 0);
    }

    private static TabletSnapshot.NodeView tabletNode(int x, int y, int z, String name, String machine, RouterTier tier,
            @Nullable UUID items, @Nullable UUID fluids, @Nullable UUID energy, int roles, TabletSnapshot.NodeStatus status) {
        List<Optional<UUID>> networks = List.of(Optional.ofNullable(items), Optional.ofNullable(fluids),
                Optional.ofNullable(energy), Optional.empty());
        return new TabletSnapshot.NodeView(new NodeIndex.NodeKey(net.minecraft.world.level.Level.OVERWORLD,
                new BlockPos(x, y, z)), name, ResourceLocation.withDefaultNamespace(machine), tier, networks, roles, status);
    }

    // ------------------------------------------------------------------ Configurador

    /** Desenhada no lugar das outras a partir do primeiro passo do Configurador. */
    private static ConfiguratorScreen configuratorScreen;

    /**
     * Tela do Configurador: a biblioteca com um preset selecionado e o código exportado, "Salvar ou
     * importar" aberto, a biblioteca vazia, e a aba Área copiando, colando (origem escolhida) e
     * aplicando só numa máquina, e com uma área grande demais.
     */
    private static List<Step> configuratorSteps() {
        return List.of(
                new Step(() -> {
                    mouseX = mouseY = -1;
                    configuratorScreen = configuratorScreen(configuratorSample(true, false, false));
                    configuratorScreen.previewSelect(1);
                    int[] center = configuratorScreen.previewCenter(
                            Component.translatable("gui.wirelessautomate.configurator.apply"));
                    mouseX = center[0];
                    mouseY = center[1];
                }, "k1-biblioteca"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    configuratorScreen.previewMore(true, "Fornalhas da linha 5x", "WA1:eNpjYGBgZGBgYGBg");
                }, "k2-salvar-importar"),
                new Step(() -> {
                    configuratorScreen = configuratorScreen(new ConfiguratorView(new ConfiguratorView.Wand(false, -1, 0,
                            Optional.empty(), Optional.empty(), Optional.empty(), 0, 0), List.of(),
                            ConfiguratorView.Area.NONE, Optional.empty(), Optional.empty(), 0));
                }, "k3-biblioteca-vazia"),
                new Step(() -> {
                    configuratorScreen = configuratorScreen(configuratorSample(false, false, false));
                    configuratorScreen.previewTab(true);
                }, "k4-area-copiar"),
                new Step(() -> {
                    configuratorScreen = configuratorScreen(configuratorSample(false, true, false));
                    configuratorScreen.previewTab(true);
                }, "k5-area-colar"),
                new Step(() -> {
                    configuratorScreen = configuratorScreen(configuratorSample(false, false, false));
                    configuratorScreen.previewTab(true);
                    configuratorScreen.previewApplyMode(true, 0, 0);
                }, "k6-area-aplicar"),
                new Step(() -> {
                    configuratorScreen = configuratorScreen(configuratorSample(false, false, true));
                    configuratorScreen.previewTab(true);
                }, "k7-area-grande"));
    }

    private static ConfiguratorScreen configuratorScreen(ConfiguratorView view) {
        ConfiguratorScreen created = new ConfiguratorScreen(new ConfiguratorMenu(0, view), new Inventory(null),
                Component.translatable("gui.wirelessautomate.configurator.title"), true);
        Minecraft minecraft = Minecraft.getInstance();
        created.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        return created;
    }

    /** Duas linhas de cinco máquinas (fornalhas e enriquecedores de mentira: fornalha e alto-forno). */
    private static ConfiguratorView configuratorSample(boolean export, boolean clipboard, boolean tooLarge) {
        List<ConfiguratorView.FaceLine> furnace = List.of(
                new ConfiguratorView.FaceLine(ResourceType.ITEM, RelativeSide.FRONT, PortMode.INSERT, 0),
                new ConfiguratorView.FaceLine(ResourceType.ITEM, RelativeSide.BACK, PortMode.EXTRACT, 3),
                new ConfiguratorView.FaceLine(ResourceType.ITEM, RelativeSide.LEFT, PortMode.INSERT, 12),
                new ConfiguratorView.FaceLine(ResourceType.ENERGY, RelativeSide.BACK, PortMode.INSERT, 0));
        List<ConfiguratorView.LibraryEntry> library = List.of(
                new ConfiguratorView.LibraryEntry("Fornalha simples", 2, 1, furnace.subList(0, 2)),
                new ConfiguratorView.LibraryEntry("Fornalhas da linha 5x", 4, 2, furnace),
                new ConfiguratorView.LibraryEntry("Enriquecedor", 3, 1, furnace.subList(1, 4)),
                new ConfiguratorView.LibraryEntry("Armazém de minérios do lado norte", 1, 0, List.of(
                        new ConfiguratorView.FaceLine(ResourceType.ITEM, RelativeSide.FRONT, PortMode.BOTH, 40))),
                new ConfiguratorView.LibraryEntry("Tanque de lava", 1, 1, List.of(
                        new ConfiguratorView.FaceLine(ResourceType.FLUID, RelativeSide.TOP, PortMode.EXTRACT, 1))),
                new ConfiguratorView.LibraryEntry("Gerador", 1, 1, List.of(
                        new ConfiguratorView.FaceLine(ResourceType.ENERGY, RelativeSide.FRONT, PortMode.EXTRACT, 0))));
        List<ConfiguratorView.Dot> dots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            dots.add(new ConfiguratorView.Dot(i * 2, 1, 0, true, 0));
            dots.add(new ConfiguratorView.Dot(i * 2, 1, 4, i < 2, 1));
        }
        BlockPos min = new BlockPos(100, 64, 200);
        ConfiguratorView.Area area = tooLarge
                ? new ConfiguratorView.Area(Optional.of("too_large"), min, new BlockPos(120, 40, 90), List.of(), List.of())
                : new ConfiguratorView.Area(Optional.empty(), min, new BlockPos(9, 2, 5), List.copyOf(dots),
                        List.of(ResourceLocation.withDefaultNamespace("furnace"),
                                ResourceLocation.withDefaultNamespace("blast_furnace")));
        ConfiguratorView.Wand wand = new ConfiguratorView.Wand(!export, 4, 2, Optional.of(min),
                Optional.of(min.offset(tooLarge ? 119 : 8, 1, tooLarge ? 89 : 4)),
                clipboard ? Optional.of(min.offset(0, 0, 8)) : Optional.empty(), clipboard ? 5 : 0, clipboard ? 4 : 0);
        Optional<ConfiguratorView.Export> code = export
                ? Optional.of(new ConfiguratorView.Export(1, "Fornalhas da linha 5x",
                        "WA1:eNrjYmBgZGBgYmBgYGRgZmBkYGJgZgACAJ0AEQeNrjYmBgZGBgYmBgYGRgZmBkYGJgZgACAJ0AEQ"))
                : Optional.empty();
        Optional<Component> notice = export
                ? Optional.of(Component.translatable("gui.wirelessautomate.configurator.exported", "Fornalhas da linha 5x", 84))
                : clipboard
                        ? Optional.of(Component.translatable("item.wirelessautomate.configurator.area.anchor", 4, 5))
                        : Optional.empty();
        return new ConfiguratorView(wand, library, area, code, notice, 1);
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
        AbstractContainerScreen<?> active = tabletScreen != null ? tabletScreen
                : configuratorScreen != null ? configuratorScreen
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

    /** Fornalha com o roteador Elite preso em cima: Itens na "Linha 5x", Fluidos sem rede, Energia na "Base". */
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
        UUID line = UUID.nameUUIDFromBytes("linha".getBytes());
        List<NetworkEntry> networks = List.of(
                new NetworkEntry(base, "Base", 0x3D8BFF, true),
                new NetworkEntry(line, "Linha 5x", 0xBA68C8, true),
                new NetworkEntry(UUID.nameUUIDFromBytes("fluidos".getBytes()), "Fluidos", 0x45D6CC, true),
                new NetworkEntry(UUID.nameUUIDFromBytes("energia".getBytes()), "Energia", 0xFFB020, false),
                new NetworkEntry(UUID.nameUUIDFromBytes("minerio".getBytes()), "Minério", 0xD8875A, true),
                new NetworkEntry(LONG, "Processamento de minérios do lado norte", 0x81C784, true));
        List<Optional<UUID>> typeNetworks = new ArrayList<>(Collections.nCopies(ResourceType.values().length,
                Optional.of(base)));
        typeNetworks.set(ResourceType.ITEM.ordinal(), Optional.of(line));
        typeNetworks.set(ResourceType.FLUID.ordinal(), Optional.empty());
        return new RouterSnapshot(new BlockPos(0, 64, 0), "", RouterTier.ELITE, Direction.UP, List.copyOf(typeNetworks),
                networks, false, new ItemStack(Items.FURNACE), Blocks.FURNACE.defaultBlockState(), List.copyOf(faces));
    }
}
