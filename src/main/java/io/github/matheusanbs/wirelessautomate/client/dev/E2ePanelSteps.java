package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.chunk.RouterChunkLoader;
import io.github.matheusanbs.wirelessautomate.client.router.ModeCycle;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * O painel do nó na tela do roteador B: prioridade, redstone, visor sincronizado com o cubo, a rede explícita, o quadro
 * da presa, o Cartão de Filtro num slot de face e o Upgrade de chunk loading.
 */
final class E2ePanelSteps {
    private E2ePanelSteps() {
    }

    /**
     * O painel do nó na tela do roteador B, na largura de abertura: a prioridade e o redstone sempre à
     * vista (sem "Mais"), o visor e o cubo andando juntos e Extrai mantendo a face sem rede mesmo com
     * uma rede ativa. Termina no mínimo.
     */
    static void panelSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(new Step("painel: largura de abertura", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(420, 240),
                () -> routerScreen().wideLayout() && routerScreen().size()[0] == Math.min(420, routerScreen().width - 8),
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size())));
        int[] priority = new int[1];
        list.add(new Step("painel: prioridade + sem Mais", STEP_TIMEOUT_MS, () -> {
            clickFace(Direction.UP);
            priority[0] = onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).priority());
            clickPanel(panel().priorityCenter(1), "o + da prioridade");
        }, () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).priority()) == priority[0] + 1
                && routerScreen().getMenu().snapshot().face(ResourceType.ITEM, Direction.UP).priority() == priority[0] + 1
                && panel().snapshot().face(ResourceType.ITEM, Direction.UP).priority() == priority[0] + 1,
                () -> "prioridade no servidor " + onServer(server -> router(server, routerB).face(ResourceType.ITEM,
                        Direction.UP).priority()) + ", antes " + priority[0]));
        list.add(new Step("painel: prioridade de volta pelo -", STEP_TIMEOUT_MS,
                () -> clickPanel(panel().priorityCenter(-1), "o - da prioridade"),
                () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).priority()) == priority[0]
                        && routerScreen().getMenu().snapshot().face(ResourceType.ITEM, Direction.UP).priority() == priority[0],
                () -> "prioridade " + routerScreen().getMenu().snapshot().face(ResourceType.ITEM, Direction.UP).priority()));
        // o cubo: clique avança o modo, direito volta, Shift põe em Nenhum; Copiar e Colar levam a face inteira
        PortMode[] cubeBefore = new PortMode[1];
        list.add(new Step("cubo: clique avança o modo", STEP_TIMEOUT_MS, () -> {
            cubeBefore[0] = onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).mode());
            clickPanel(panel().faceCenter(Direction.UP), "a face de cima");
        }, () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).mode())
                == ModeCycle.next(cubeBefore[0])
                && panel().snapshot().face(ResourceType.ITEM, Direction.UP).mode()
                == ModeCycle.next(cubeBefore[0]),
                () -> "modo no servidor " + onServer(server -> router(server, routerB).face(ResourceType.ITEM,
                        Direction.UP).mode()) + ", antes " + cubeBefore[0]));
        list.add(new Step("cubo: clique direito volta o modo", STEP_TIMEOUT_MS, () -> {
            int[] c = panel().faceCenter(Direction.UP);
            routerScreen().mouseClicked(c[0], c[1], GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        }, () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).mode()) == cubeBefore[0]
                && panel().snapshot().face(ResourceType.ITEM, Direction.UP).mode() == cubeBefore[0],
                () -> "modo no servidor " + onServer(server -> router(server, routerB).face(ResourceType.ITEM,
                        Direction.UP).mode()) + ", esperava " + cubeBefore[0]));
        list.add(new Step("cubo: Copiar e Colar levam a face inteira", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                router(server, routerB).setMode(ResourceType.ITEM, Direction.EAST, PortMode.NONE);
                return null;
            });
            clickPanel(panel().copyCenter(), "o Copiar");
            panel().selectFace(Direction.EAST);
        }, () -> routerScreen().panel().face() == Direction.EAST && stepTicks >= 2,
                () -> "face " + routerScreen().panel().face()));
        list.add(new Step("cubo: Colar", STEP_TIMEOUT_MS,
                () -> clickPanel(panel().pasteCenter(), "o Colar"),
                () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.EAST).mode())
                        == cubeBefore[0],
                () -> "modo colado " + onServer(server -> router(server, routerB).face(ResourceType.ITEM,
                        Direction.EAST).mode()) + ", esperava " + cubeBefore[0]));
        list.add(new Step("cubo: Limpar em dois cliques", STEP_TIMEOUT_MS, () -> {
            clickPanel(panel().clearCenter(), "o Limpar");
            clickPanel(panel().clearCenter(), "o Limpar de novo");
        }, () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.EAST).mode())
                == PortMode.NONE && onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP)
                .mode()) == PortMode.NONE,
                () -> "Limpar não zerou as faces do tipo"));
        list.add(new Step("cubo: o estado de antes", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                router(server, routerB).setMode(ResourceType.ITEM, Direction.UP, cubeBefore[0]);
                return null;
            });
            panel().selectFace(Direction.UP);
        }, () -> panel().snapshot().face(ResourceType.ITEM, Direction.UP).mode() == cubeBefore[0],
                () -> "modo de cima " + panel().snapshot().face(ResourceType.ITEM, Direction.UP).mode()));
        list.add(new Step("painel: abrir a lista do redstone", STEP_TIMEOUT_MS,
                () -> clickPanel(panel().redstoneCenter(), "o redstone"),
                () -> panel().redstoneRowCenter(RedstoneMode.HIGH) != null && stepTicks >= 2,
                () -> "lista do redstone fechada"));
        list.add(capture("1h-painel-redstone"));
        for (RedstoneMode mode : new RedstoneMode[] {RedstoneMode.HIGH, RedstoneMode.IGNORE}) {
            // a lista aberta só vale depois de um quadro desenhado com ela: clicar na linha no mesmo tick erra
            list.add(new Step("painel: lista do redstone para " + mode, STEP_TIMEOUT_MS, () -> {
                if (panel().redstoneRowCenter(mode) == null) {
                    clickPanel(panel().redstoneCenter(), "o redstone");
                }
            }, () -> panel().redstoneRowCenter(mode) != null && stepTicks >= 2, () -> "lista do redstone fechada"));
            list.add(new Step("painel: redstone " + mode, STEP_TIMEOUT_MS,
                    () -> clickPanel(panel().redstoneRowCenter(mode), "a linha " + mode + " do redstone"), () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).redstone()) == mode
                    && routerScreen().getMenu().snapshot().face(ResourceType.ITEM, Direction.UP).redstone() == mode
                    && !panel().popupOpen(),
                    () -> "redstone na tela " + routerScreen().getMenu().snapshot().face(ResourceType.ITEM,
                            Direction.UP).redstone()));
        }

        // o visor e o cubo andam juntos: a face sob o mouse no visor, clicada, é a escolhida no painel
        int[] viewPoint = new int[2];
        Direction[] viewFace = new Direction[1];
        list.add(new Step("painel: mouse numa face do visor", STEP_TIMEOUT_MS, () -> {
            RouterScreen screen = routerScreen();
            screen.previewViewDefault();
            int[] point = screen.previewViewFaceCenter(Direction.SOUTH);
            viewPoint[0] = point[0];
            viewPoint[1] = point[1];
            moveMouse(point[0], point[1]);
        }, () -> {
            viewFace[0] = routerScreen().viewHoveredFace();
            return stepTicks >= 3 && viewFace[0] != null && viewFace[0] != panel().face();
        }, () -> "face sob o mouse no visor " + routerScreen().viewHoveredFace() + ", no painel " + panel().face()));
        list.add(new Step("painel: clicar no visor escolhe a face no cubo", STEP_TIMEOUT_MS,
                () -> click(routerScreen(), viewPoint[0], viewPoint[1]),
                () -> panel().face() == viewFace[0] && routerScreen().getMenu().selectedFace() == viewFace[0]
                        && onServer(server -> server.getPlayerList().getPlayer(playerId).containerMenu instanceof RouterMenu m
                                && m.selectedFace() == viewFace[0]),
                () -> "face no painel " + panel().face() + ", esperada " + viewFace[0]));
        list.add(capture("1i-painel-visor-sincronizado"));

        // A face Oeste do baú B continua sem rede ao escolher uma ação, mesmo com rede ativa.
        Direction d1 = Direction.WEST;
        PortMode[] modeBefore = new PortMode[1];
        UUID[] networkBefore = new UUID[1];
        list.add(new Step("painel rede explícita: face sem modo e sem rede", STEP_TIMEOUT_MS, () -> onServer(server -> {
            RouterBlockEntity router = router(server, routerB);
            modeBefore[0] = router.face(ResourceType.ITEM, d1).mode();
            networkBefore[0] = router.faceNetwork(ResourceType.ITEM, d1);
            router.configureFace(ResourceType.ITEM, d1, PortMode.NONE, 0, RedstoneMode.IGNORE);
            router.setFaceNetwork(ResourceType.ITEM, d1, null);
            NetworkSavedData.get(server).setActiveNetwork(playerId, mainNetwork);
            return null;
        }), () -> {
            FaceView v = routerScreen().getMenu().snapshot().face(ResourceType.ITEM, d1);
            return v.mode() == PortMode.NONE && v.network().isEmpty();
        }, () -> "face na tela " + routerScreen().getMenu().snapshot().face(ResourceType.ITEM, d1)));
        list.add(new Step("painel rede explícita: Extrai mantém a face sem rede", STEP_TIMEOUT_MS, () -> {
            clickFace(d1);
            clickMode(PortMode.EXTRACT);
        }, () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, d1).mode() == PortMode.EXTRACT
                    && router(server, routerB).faceNetwork(ResourceType.ITEM, d1) == null
                    && router(server, routerB).faceNetwork(ResourceType.ITEM, Direction.UP) != null)
                    && routerScreen().getMenu().snapshot().network(ResourceType.ITEM, d1).isEmpty()
                    && PanelNotices.current() == null,
                () -> "rede no servidor " + onServer(server -> router(server, routerB).faceNetwork(ResourceType.ITEM, d1))));
        list.add(clipCheck("painel rede explícita: face sem rede"));
        list.add(capture("1k-painel-escolha-a-rede"));
        list.add(new Step("painel rede explícita: a face volta ao que era", STEP_TIMEOUT_MS, () -> onServer(server -> {
            RouterBlockEntity router = router(server, routerB);
            router.configureFace(ResourceType.ITEM, d1, modeBefore[0], 0, RedstoneMode.IGNORE);
            router.setFaceNetwork(ResourceType.ITEM, d1, networkBefore[0]);
            return null;
        }), () -> routerScreen().getMenu().snapshot().face(ResourceType.ITEM, d1).mode() == modeBefore[0]
                && routerScreen().getMenu().snapshot().network(ResourceType.ITEM, d1).equals(Optional.ofNullable(networkBefore[0])),
                () -> "face na tela " + routerScreen().getMenu().snapshot().face(ResourceType.ITEM, d1)));
        list.add(new Step("painel: de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(300, 240),
                () -> routerScreen().size()[0] == 300 && routerScreen().size()[1] == 240,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size())));
    }

    /**
     * Cartão de Filtro num slot de face pela tela: o servidor dá um cartão (lista branca de
     * diamante) ao jogador, a tela muda para a face Norte (o servidor troca o que os slots mostram)
     * e o cartão vai do inventário para o primeiro slot de cartão com dois cliques, como o jogador
     * faria; confere no roteador e no conjunto de filtros que o motor usa.
     */
    static void cardSteps(List<Step> list) {
        RelativeSide north = RelativeSide.fromAbsolute(Direction.UP, 0, Direction.NORTH);
        list.add(new Step("cartão no inventário", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());
                FilterCardItem.setContents(card, new FilterCardItem.Contents(ResourceType.ITEM, new Filter(
                        Filter.ListMode.WHITELIST, false, List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 0)))));
                server.getPlayerList().getPlayer(playerId).getInventory().setItem(0, card);
                return null;
            });
        }, () -> inventoryCardSlot() != null, () -> "cartão no slot " + inventoryCardSlot()));
        list.add(new Step("face Norte", STEP_TIMEOUT_MS,
                () -> clickFace(Direction.NORTH),
                () -> {
                    UUID playerId = Minecraft.getInstance().player.getUUID();
                    return routerScreen().getMenu().selectedFace() == Direction.NORTH && onServer(server ->
                            server.getPlayerList().getPlayer(playerId).containerMenu instanceof RouterMenu menu
                                    && menu.selectedFace() == Direction.NORTH && menu.selectedType() == ResourceType.ITEM);
                }, () -> "seleção na tela " + routerScreen().getMenu().selectedFace()));
        list.add(new Step("cartão no slot da face", STEP_TIMEOUT_MS, () -> {
            RouterScreen screen = routerScreen();
            Slot from = inventoryCardSlot();
            if (from == null) {
                throw new StepFailure("o cartão sumiu do inventário");
            }
            click(screen, screen.getGuiLeft() + from.x + 8, screen.getGuiTop() + from.y + 8);
            Slot to = screen.getMenu().getSlot(0);
            click(screen, screen.getGuiLeft() + to.x + 8, screen.getGuiTop() + to.y + 8);
        }, () -> onServer(server -> {
            RouterBlockEntity router = router(server, routerB);
            return FilterCardItem.isCard(router.card(ResourceType.ITEM, north, 0))
                    && router.filterSet(ResourceType.ITEM, Direction.NORTH).testItem(new ItemStack(Items.DIAMOND))
                    && !router.filterSet(ResourceType.ITEM, Direction.NORTH).testItem(new ItemStack(Items.STONE));
        }) && routerScreen().getMenu().getSlot(0).hasItem() && routerScreen().getMenu().getCarried().isEmpty()
                && inventoryCardSlot() == null,
                () -> "servidor " + onServer(server -> router(server, routerB).card(ResourceType.ITEM, north, 0).toString())
                        + ", slot na tela " + routerScreen().getMenu().getSlot(0).getItem()
                        + ", na mão " + routerScreen().getMenu().getCarried()));
        list.add(capture("6-cartao-no-slot"));
    }

    /**
     * Upgrade de chunk loading pela tela: o servidor dá o upgrade ao jogador, que o pega no inventário
     * e solta no slot de upgrade; o roteador B fica com o upgrade, o jogador como dono e o ticket no
     * chunk dele, e a tela mostra "ativo". Depois Shift + clique (o mesmo pacote do vanilla) tira o
     * upgrade de volta para o inventário e o ticket sai.
     */
    static void chunkUpgradeSteps(List<Step> list) {
        list.add(new Step("upgrade no inventário", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).getInventory()
                        .setItem(1, new ItemStack(ModItems.CHUNK_LOADER_UPGRADE.get()));
                return null;
            });
        }, () -> inventoryUpgradeSlot() != null, () -> "upgrade no slot " + inventoryUpgradeSlot()));
        list.add(new Step("upgrade no slot de upgrade", STEP_TIMEOUT_MS, () -> {
            RouterScreen screen = routerScreen();
            Slot from = inventoryUpgradeSlot();
            if (from == null) {
                throw new StepFailure("o upgrade sumiu do inventário");
            }
            click(screen, screen.getGuiLeft() + from.x + 8, screen.getGuiTop() + from.y + 8);
            int[] to = screen.previewUpgradeSlotCenter();
            click(screen, to[0], to[1]);
        }, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            return onServer(server -> {
                RouterBlockEntity router = router(server, routerB);
                return router.hasChunkUpgrade() && playerId.equals(router.upgradeOwner())
                        && router.chunkLoadState() == ChunkLoadState.ACTIVE
                        && RouterChunkLoader.hasTicket(server.overworld(), routerB, ChunkPos.asLong(routerB));
            }) && routerScreen().getMenu().getSlot(RouterMenu.UPGRADE_SLOT).hasItem()
                    && routerScreen().getMenu().snapshot().chunkLoad() == ChunkLoadState.ACTIVE
                    && routerScreen().getMenu().getCarried().isEmpty();
        }, () -> "servidor " + onServer(server -> router(server, routerB).upgrade() + " "
                + router(server, routerB).chunkLoadState()) + ", tela " + routerScreen().getMenu().snapshot().chunkLoad()
                + ", na mão " + routerScreen().getMenu().getCarried()));
        list.add(new Step("captura 7-upgrade-no-slot", STEP_TIMEOUT_MS, () -> {
            int[] center = routerScreen().previewUpgradeSlotCenter();
            moveMouse(center[0], center[1]);
        }, () -> {
            if (stepTicks < 6) {
                return false;
            }
            capture(Minecraft.getInstance(), "7-upgrade-no-slot");
            return true;
        }, () -> "7-upgrade-no-slot.png"));
        list.add(new Step("tirar o upgrade com Shift + clique", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.handleInventoryMouseClick(routerScreen().getMenu().containerId, RouterMenu.UPGRADE_SLOT,
                    0, ClickType.QUICK_MOVE, minecraft.player);
        }, () -> onServer(server -> {
            RouterBlockEntity router = router(server, routerB);
            return !router.hasChunkUpgrade() && router.chunkLoadState() == ChunkLoadState.NONE
                    && !RouterChunkLoader.hasTicket(server.overworld(), routerB, ChunkPos.asLong(routerB));
        }) && !routerScreen().getMenu().getSlot(RouterMenu.UPGRADE_SLOT).hasItem() && inventoryUpgradeSlot() != null
                && routerScreen().getMenu().snapshot().chunkLoad() == ChunkLoadState.NONE,
                () -> "servidor " + onServer(server -> router(server, routerB).upgrade().toString())
                        + ", no inventário " + inventoryUpgradeSlot()));
    }

    /** O slot do inventário (na tela do roteador aberta) com o Upgrade de chunk loading, ou {@code null}. */
    private static @Nullable Slot inventoryUpgradeSlot() throws StepFailure {
        RouterMenu menu = routerScreen().getMenu();
        for (int i = RouterMenu.CARD_SLOT_COUNT; i < RouterMenu.UPGRADE_SLOT; i++) {
            if (menu.getSlot(i).getItem().is(ModItems.CHUNK_LOADER_UPGRADE.get())) {
                return menu.getSlot(i);
            }
        }
        return null;
    }

    /**
     * Quadro da presa com a frente fora do Norte: o baú B olha para o Sul, então o botão "Trás" (achado
     * pelo texto da chave {@code side.back}, não pelo rótulo que a tela calcula) seleciona a face
     * absoluta Norte no cliente e no servidor, e "Frente" a Sul.
     */
    static void attachedFrameSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(new Step("B: frente do baú para o Sul", STEP_TIMEOUT_MS, () -> { },
                () -> onServer(server -> router(server, routerB).attachedFront() == Direction.SOUTH.ordinal())
                        && routerScreen().getMenu().snapshot().attachedFront() == Direction.SOUTH.ordinal(),
                () -> "frente " + routerScreen().getMenu().snapshot().attachedFront()));
        for (RemoteSide side : new RemoteSide[] {RemoteSide.BACK, RemoteSide.FRONT}) {
            Direction expected = side == RemoteSide.BACK ? Direction.NORTH : Direction.SOUTH;
            list.add(new Step("B: botão " + side.key() + " é a face " + expected.getName(), STEP_TIMEOUT_MS,
                    () -> clickSide(side),
                    () -> routerScreen().getMenu().selectedFace() == expected && onServer(server ->
                            server.getPlayerList().getPlayer(playerId).containerMenu instanceof RouterMenu menu
                                    && menu.selectedFace() == expected),
                    () -> "face na tela " + routerScreen().getMenu().selectedFace()));
        }
    }

    /** O slot do inventário (na tela do roteador aberta) com um Cartão de Filtro, ou {@code null}. */
    private static @Nullable Slot inventoryCardSlot() throws StepFailure {
        RouterMenu menu = routerScreen().getMenu();
        for (int i = RouterMenu.CARD_SLOT_COUNT; i < menu.slots.size(); i++) {
            if (FilterCardItem.isCard(menu.getSlot(i).getItem())) {
                return menu.getSlot(i);
            }
        }
        return null;
    }
}
