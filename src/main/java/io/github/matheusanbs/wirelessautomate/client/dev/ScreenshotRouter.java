package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevScreenshot.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotFilter.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotTablet.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ItemEntry;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import org.lwjgl.glfw.GLFW;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;

/**
 * As capturas da tela do roteador ({@code WA_SCREENSHOT}): a fornalha de exemplo com as abas, a lista de redes,
 * as dicas e o tema escuro, o visor 3D, os slots de Cartão de Filtro e o slot do Upgrade de chunk loading.
 */
final class ScreenshotRouter {
    private ScreenshotRouter() {
    }

    static RouterScreen screen;

    static final List<Step> STEPS = List.of(
            new Step(() -> screen.previewFace(Direction.DOWN), "1-itens"),
            new Step(() -> screen.previewType(ResourceType.ENERGY), "2-energia"),
            new Step(() -> {
                screen.previewType(ResourceType.ITEM);
                screen.previewFace(Direction.NORTH);
                int[] center = screen.previewFaceCenter(Direction.UP);
                mouseX = center[0];
                mouseY = center[1];
            }, "3-face-dica"),
            new Step(() -> {
                mouseX = mouseY = -1;
                screen.previewNetworkList(true);
            }, "4-redes"),
            new Step(() -> {
                // a lista é da aba: na de energia, marca a "Base"
                screen.previewNetworkList(false);
                screen.previewType(ResourceType.ENERGY);
                screen.previewNetworkList(true);
                int[] center = screen.panel().networkRowCenter(0);
                mouseX = center == null ? -1 : center[0];
                mouseY = center == null ? -1 : center[1];
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
                // nome de rede comprido: a pílula da linha Rede encolhe o nome para caber
                RouterSnapshot s = screen.getMenu().snapshot();
                List<FaceView> faces = new ArrayList<>(s.faces());
                int index = RouterSnapshot.index(ResourceType.ITEM, screen.getMenu().selectedFace());
                faces.set(index, faces.get(index).withNetwork(Optional.of(LONG)));
                screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), "Fornalha da linha de processamento", s.tier(),
                        s.facing(), s.attachedFront(), s.networks(), s.powered(), s.machine(), s.machineState(),
                        List.copyOf(faces)));
                mouseX = mouseY = -1;
            }, "4e-nome-longo"),
            new Step(() -> {
                screen.previewNetworkList(false);
                screen.previewType(ResourceType.FLUID);
                RouterSnapshot s = screen.getMenu().snapshot();
                screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), "Fornalha Norte", s.tier(), s.facing(),
                        s.attachedFront(), s.networks(), s.powered(), s.machine(), s.machineState(), sample().faces()));
                int[] center = screen.previewEditFilterCenter();
                mouseX = center[0];
                mouseY = center[1];
            }, "5-fluidos"),
            new Step(() -> {
                mouseX = mouseY = -1;
                screen.previewType(ResourceType.ITEM);
                screen.previewRename("Fornalha Norte 2");
            }, "6-renomear"),
            new Step(() -> {
                // tema escuro só nas capturas, sem gravar a config
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); // sai da renomeação do passo anterior
                themeBefore = GuiThemes.current();
                GuiThemes.preview(GuiTheme.DARK);
                screen.previewType(ResourceType.ITEM);
                screen.previewFace(Direction.DOWN);
            }, "6b-escuro"),
            new Step(() -> screen.previewNetworkList(true), "6c-escuro-redes"),
            new Step(() -> {
                // a disposição estreita: abas na largura toda, o cubo embaixo do visor
                screen.previewNetworkList(false);
                screen.previewResize(300, 240);
            }, "6d-escuro-estreito"),
            new Step(() -> GuiThemes.preview(themeBefore), "6e-estreito"));

    /**
     * Visor 3D: a fornalha com o roteador em cima em dois ângulos, com uma face selecionada e o
     * mouse sobre outra (o marcador mostra onde ele está, para conferir a escolha da face); um baú
     * virado para o leste (renderizador de entidade) com o roteador no norte; e sem máquina.
     */
    static List<Step> viewSteps() {
        return List.of(
                new Step(() -> {
                    screen.previewResize(420, 240);
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
                            Direction.EAST.ordinal(), s.networks(), s.powered(), new ItemStack(Items.CHEST),
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
                            -1, s.networks(), s.powered(), ItemStack.EMPTY, Blocks.AIR.defaultBlockState(),
                            s.faces()));
                    screen.previewViewDefault();
                    mouseX = mouseY = -1;
                }, "10-visor-sem-maquina"));
    }

    /**
     * Slots de Cartão de Filtro na tela do roteador: a face de baixo com 12 entradas e um cartão
     * de itens no primeiro slot, com o inventário cheio de exemplo; a dica do slot vazio; a aba
     * de energia com "Mais" aberto (sem slots de cartão); e a de fluidos com um cartão no segundo slot.
     */
    static List<Step> cardSteps() {
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
                }, "c3-energia"),
                new Step(() -> {
                    // sem servidor, os slots não trocam sozinhos: põe o que o servidor mandaria
                    screen.previewType(ResourceType.FLUID);
                    screen.previewFace(Direction.UP);
                    screen.getMenu().getSlot(0).set(ItemStack.EMPTY);
                    screen.getMenu().getSlot(1).set(card(ResourceType.FLUID, fluidFilter(), 1));
                }, "c4-fluidos"));
    }

    /**
     * Slot do Upgrade de chunk loading, no fim da linha dos cartões: ativo com a dica, vazio com a
     * dica, inativo pelo limite do dono e, na aba de energia, desligado na config.
     */
    static List<Step> upgradeSteps() {
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
        screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), s.name(), s.tier(), s.facing(), s.attachedFront(),
                s.networks(), s.powered(), s.machine(), s.machineState(), s.faces(), state));
    }

    /** Tela do roteador com um cartão de itens no primeiro slot e o inventário de exemplo. */
    private static RouterScreen cardScreen() {
        RouterSnapshot s = sample();
        List<FaceView> faces = new ArrayList<>(s.faces());
        int down = RouterSnapshot.index(ResourceType.ITEM, Direction.DOWN);
        FaceView d = faces.get(down);
        faces.set(down, new FaceView(d.mode(), d.priority(), d.redstone(), d.slots(), 12, false, d.network()));
        RouterSnapshot snapshot = new RouterSnapshot(s.pos(), "Fornalha Norte", s.tier(), s.facing(), s.attachedFront(),
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

    /**
     * Fornalha (frente para o Norte) com o roteador Elite preso em cima: em Itens, Cima e Norte na
     * "Linha 5x" e Baixo na "Base" (o resumo diz "2 redes"); Fluidos sem rede; Energia na "Base".
     */
    static RouterSnapshot sample() {
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
        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.ITEM || type == ResourceType.FLUID) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                int index = RouterSnapshot.index(type, direction);
                faces.set(index, faces.get(index).withNetwork(Optional.of(base)));
            }
        }
        for (Direction direction : new Direction[] {Direction.UP, Direction.NORTH}) {
            int index = RouterSnapshot.index(ResourceType.ITEM, direction);
            faces.set(index, faces.get(index).withNetwork(Optional.of(line)));
        }
        int downIndex = RouterSnapshot.index(ResourceType.ITEM, Direction.DOWN);
        faces.set(downIndex, faces.get(downIndex).withNetwork(Optional.of(base)));
        return new RouterSnapshot(new BlockPos(0, 64, 0), "", RouterTier.ELITE, Direction.UP, Direction.NORTH.ordinal(),
                networks, false, new ItemStack(Items.FURNACE), Blocks.FURNACE.defaultBlockState(), List.copyOf(faces));
    }
}
