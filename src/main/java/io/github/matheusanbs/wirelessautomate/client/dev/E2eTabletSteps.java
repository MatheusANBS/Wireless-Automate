package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.menu.PanelAccess;
import io.github.matheusanbs.wirelessautomate.menu.RemoteRouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import io.github.matheusanbs.wirelessautomate.client.GuiText;

/**
 * O Tablet de rede no e2e: busca e etiquetas, filtro rápido, painel do nó, perfil (copiar, colar, desfazer),
 * estatísticas, grupos com pausa e o roteador aberto à distância.
 */
final class E2eTabletSteps {
    private E2eTabletSteps() {
    }

    private static final String GROUP_NAME = "E2E Grupo";
    /** Posição do jogador antes de se afastar para abrir B à distância. */
    private static Vec3 beforeTablet = Vec3.ZERO;
    /** Baú com um roteador sem rede, só durante os passos do Tablet (dá o filtro rápido "Sem rede"). */
    private static BlockPos tabletLoneChest = BlockPos.ZERO;
    /** O perfil de A antes do colar no grupo, para conferir o Desfazer. */
    private static @Nullable RouterPreset aBeforePaste;
    private static final ResourceLocation CHEST_ID = BuiltInRegistries.BLOCK.getKey(Blocks.CHEST);

    /**
     * Tablet de rede: abre pelo item na mão com o cursor na busca, busca o nó B pelo nome, escolhe a sugestão da
     * máquina (vira etiqueta e filtra) e a tira pelo ✕, liga e desliga o filtro rápido "Sem rede", escolhe B na
     * lista (o painel aparece e muda o modo de uma face no servidor), copia o perfil de B (a faixa aparece), cola
     * no grupo dos baús (o aviso aparece e A fica "igual ao perfil"), desfaz, e repete com Ctrl+C, Ctrl+V e Ctrl+Z.
     * Depois cria um grupo de redes, pausa e retoma, edita o filtro de B a 20 blocos pelo painel e volta ao
     * Tablet com B escolhido, e abre B à distância com duplo clique na linha.
     */
    static void tabletSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        tabletSearchSteps(list, playerId);
        tabletPanelSteps(list);
        tabletProfileSteps(list);
        tabletStatsSteps(list);
        tabletGroupSteps(list);
        tabletRemoteSteps(list, playerId);
    }

    /** Tablet: abre pelo item, acha B pela busca, a sugestão da máquina e o filtro rápido "Sem rede". */
    private static void tabletSearchSteps(List<Step> list, UUID playerId) {
        list.add(new Step("Tablet na mão", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.NETWORK_TABLET.get()));
                // um nó sem rede, para o filtro rápido
                tabletLoneChest = chestA.offset(-4, 0, 0);
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(tabletLoneChest,
                        Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
                level.setBlockAndUpdate(tabletLoneChest.above(),
                        ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
                RouterBlockEntity lone = router(server, tabletLoneChest.above());
                lone.setName("E2E Sem rede");
                NodeIndex.placedBy(lone, playerId);
                return null;
            });
        }, () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.NETWORK_TABLET.get())
                && onServer(server -> NodeIndex.get(server).entry(new NodeIndex.NodeKey(
                        net.minecraft.world.level.Level.OVERWORLD, tabletLoneChest.above())) != null),
                () -> "na mão: " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(new Step("abrir o Tablet", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof TabletScreen screen && screen.searchBox().isFocused(),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("achar B pela busca (o cursor já está nela)", STEP_TIMEOUT_MS, () -> type(NODE_NAME), () -> {
            TabletScreen screen = tabletScreen();
            TabletSnapshot snapshot = screen.getMenu().snapshot();
            return snapshot.query().text().equals(NODE_NAME) && snapshot.nodes().size() == 1
                    && screen.nodeRowCenter(nodeB()) != null;
        }, () -> "busca \"" + tabletScreen().getMenu().snapshot().query().text() + "\", nós "
                + tabletScreen().getMenu().snapshot().nodes().stream().map(n -> n.key().pos().toShortString()).toList()));
        list.add(capture("7-tablet-lista"));
        list.add(new Step("busca: sugestão da máquina", STEP_TIMEOUT_MS, () -> {
            tabletScreen().list().clearAll();
            type(RouterBlock.machineName(Blocks.CHEST).getString().substring(0, 3));
        }, () -> tabletScreen().list().suggestionCenter(CHEST_ID) != null,
                () -> "sugestões abertas " + tabletScreen().list().suggestionsOpen()));
        list.add(capture("7a-tablet-sugestoes"));
        list.add(new Step("busca: a sugestão vira etiqueta e filtra", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().suggestionCenter(CHEST_ID);
            click(tabletScreen(), c[0], c[1]);
        }, () -> {
            TabletSnapshot s = tabletScreen().getMenu().snapshot();
            return tabletScreen().list().labelIds().equals(List.of(CHEST_ID)) && s.query().machines().equals(List.of(CHEST_ID))
                    && s.query().text().isEmpty() && !s.nodes().isEmpty()
                    && s.nodes().stream().allMatch(n -> n.machine().equals(CHEST_ID));
        }, () -> "etiquetas " + tabletScreen().list().labelIds() + ", consulta " + tabletScreen().getMenu().snapshot().query()));
        list.add(new Step("busca: o ✕ tira a etiqueta", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().labelCenter(0);
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().list().labelIds().isEmpty()
                && tabletScreen().getMenu().snapshot().query().machines().isEmpty(),
                () -> "etiquetas " + tabletScreen().list().labelIds()));
        list.add(new Step("filtro rápido Sem rede filtra", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().quickCenter(TabletSnapshot.QuickFilter.NO_NETWORK);
            if (c == null) {
                throw new StepFailure("o filtro rápido Sem rede não aparece");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> {
            TabletSnapshot s = tabletScreen().getMenu().snapshot();
            return s.query().quick().equals(java.util.Set.of(TabletSnapshot.QuickFilter.NO_NETWORK)) && !s.nodes().isEmpty()
                    && s.nodes().stream().noneMatch(n -> n.key().pos().equals(routerB));
        }, () -> "consulta " + tabletScreen().getMenu().snapshot().query()));
        list.add(new Step("filtro rápido desliga", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().quickCenter(TabletSnapshot.QuickFilter.NO_NETWORK);
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().getMenu().snapshot().query().quick().isEmpty()
                && tabletScreen().nodeRowCenter(nodeB()) != null,
                () -> "consulta " + tabletScreen().getMenu().snapshot().query()));
    }

    /** Tablet: escolher B mostra o painel, que muda o modo de uma face no servidor. */
    private static void tabletPanelSteps(List<Step> list) {
        list.add(new Step("escolher B mostra o painel", STEP_TIMEOUT_MS, () -> {
            int[] row = tabletScreen().nodeRowCenter(nodeB());
            click(tabletScreen(), row[0], row[1]);
        }, () -> tabletScreen().nodePanel() != null && tabletScreen().panel().node().equals(Optional.of(nodeB()))
                && tabletScreen().panel().access() == PanelAccess.EDIT,
                () -> "painel " + (tabletScreen().panel() == null ? "nenhum" : tabletScreen().panel().node())));
        list.add(new Step("painel: aba Itens", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().nodePanel().tabCenter(ResourceType.ITEM);
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().nodePanel().type() == ResourceType.ITEM, () -> "aba " + tabletScreen().nodePanel().type()));
        list.add(capture("7b-tablet-painel"));
        list.add(new Step("painel: mudar o modo muda o nó", STEP_TIMEOUT_MS, () -> {
            NodePanel panel = tabletScreen().nodePanel();
            PortMode now = panel.snapshot().face(ResourceType.ITEM, panel.face()).mode();
            PortMode next = now == PortMode.BOTH ? PortMode.INSERT : PortMode.BOTH;
            panelFace = panel.face();
            panelModeBefore = now;
            panelNetworkBefore = onServer(server -> router(server, routerB).faceNetwork(ResourceType.ITEM, panelFace));
            panelModeAfter = next;
            int[] c = panel.modeCenter(next);
            click(tabletScreen(), c[0], c[1]);
        }, () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, panelFace).mode() == panelModeAfter)
                && tabletScreen().nodePanel().snapshot().face(ResourceType.ITEM, panelFace).mode() == panelModeAfter,
                () -> onServer(server -> "modo no servidor " + router(server, routerB).face(ResourceType.ITEM, panelFace).mode())));
        list.add(new Step("painel: modo de volta", STEP_TIMEOUT_MS, () -> onServer(server -> {
            RouterBlockEntity b = router(server, routerB);
            b.setMode(ResourceType.ITEM, panelFace, panelModeBefore);
            b.setFaceNetwork(ResourceType.ITEM, panelFace, panelNetworkBefore);
            return null;
        }), () -> tabletScreen().nodePanel().snapshot().face(ResourceType.ITEM, panelFace).mode() == panelModeBefore,
                () -> "modo no painel " + tabletScreen().nodePanel().snapshot().face(ResourceType.ITEM, panelFace).mode()));
    }

    /** Tablet: copia o perfil de B, cola no grupo dos baús e desfaz, pelos botões e pelo teclado. */
    private static void tabletProfileSteps(List<Step> list) {
        list.add(new Step("Copiar perfil mostra a faixa", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().copyCenter();
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().getMenu().snapshot().profile().map(p -> p.origin().equals(nodeB())).orElse(false)
                && tabletScreen().list().forgetCenter() != null && tabletScreen().list().pasteGroupCenter(CHEST_ID) != null,
                () -> "perfil " + tabletScreen().getMenu().snapshot().profile()));
        list.add(capture("7h-tablet-perfil"));
        list.add(clipCheck("Tablet: perfil copiado"));
        list.add(listClipTooltip("Tablet: perfil copiado"));
        list.add(new Step("Colar nas N cola e mostra o aviso", STEP_TIMEOUT_MS, () -> {
            aBeforePaste = onServer(server -> RouterPreset.copyOf(router(server, routerA)));
            int[] c = tabletScreen().list().pasteGroupCenter(CHEST_ID);
            click(tabletScreen(), c[0], c[1]);
        }, () -> {
            PanelNoticePayload notice = PanelNotices.current();
            TabletSnapshot.NodeView a = tabletScreen().getMenu().snapshot().nodes().stream()
                    .filter(n -> n.key().pos().equals(routerA)).findFirst().orElse(null);
            return notice != null && notice.undo() == PanelNoticePayload.Undo.UNDO_PASTE && a != null
                    && a.profile().map(ProfileDiff.ProfileMatch::equal).orElse(false)
                    && tabletScreen().list().undoCenter() != null;
        }, () -> "aviso " + PanelNotices.current() + ", A " + tabletScreen().getMenu().snapshot().nodes().stream()
                .filter(n -> n.key().pos().equals(routerA)).map(n -> n.profile().toString()).toList()));
        list.add(capture("7i-tablet-colado"));
        list.add(new Step("Desfazer devolve A", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().undoCenter();
            click(tabletScreen(), c[0], c[1]);
        }, () -> onServer(server -> RouterPreset.copyOf(router(server, routerA)).equals(aBeforePaste)),
                () -> "A igual ao de antes: " + onServer(server -> RouterPreset.copyOf(router(server, routerA))
                        .equals(aBeforePaste))));
        list.add(new Step("esquecer o perfil para o Ctrl+C", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().forgetCenter();
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().getMenu().snapshot().profile().isEmpty(),
                () -> "perfil " + tabletScreen().getMenu().snapshot().profile()));
        list.add(new Step("Ctrl+C na linha escolhida", STEP_TIMEOUT_MS,
                () -> tabletScreen().keyPressed(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL),
                () -> tabletScreen().getMenu().snapshot().profile().map(p -> p.origin().equals(nodeB())).orElse(false),
                () -> "perfil " + tabletScreen().getMenu().snapshot().profile()));
        list.add(new Step("Ctrl+V cola em A", STEP_TIMEOUT_MS, () -> {
            int[] row = tabletScreen().nodeRowCenter(nodeA());
            click(tabletScreen(), row[0], row[1]);
        }, () -> tabletScreen().panel() != null && tabletScreen().panel().node().equals(Optional.of(nodeA())),
                () -> "painel " + (tabletScreen().panel() == null ? "nenhum" : tabletScreen().panel().node())));
        list.add(new Step("Ctrl+V: A igual ao perfil", STEP_TIMEOUT_MS,
                () -> tabletScreen().keyPressed(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL),
                () -> !onServer(server -> RouterPreset.copyOf(router(server, routerA)).equals(aBeforePaste))
                        && PanelNotices.current() != null,
                () -> "aviso " + PanelNotices.current()));
        list.add(new Step("Ctrl+Z desfaz", STEP_TIMEOUT_MS,
                () -> tabletScreen().keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL),
                () -> onServer(server -> RouterPreset.copyOf(router(server, routerA)).equals(aBeforePaste)),
                () -> "A restaurado: " + onServer(server -> RouterPreset.copyOf(router(server, routerA)).equals(aBeforePaste))));
        list.add(new Step("esquecer o perfil", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().forgetCenter();
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().getMenu().snapshot().profile().isEmpty(), () -> "perfil " + tabletScreen().getMenu().snapshot().profile()));
    }

    /** Tablet: as Estatísticas, o cartão de energia que filtra a Lista e o tamanho mínimo. */
    private static void tabletStatsSteps(List<Step> list) {
        list.add(new Step("aba Estatísticas", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.tablet.tab.stats"), "aba Estatísticas")),
                () -> tabletScreen().cardCenter(ResourceType.ITEM) != null,
                () -> "aba " + tabletScreen().currentTab()));
        list.add(capture("7c-tablet-estatisticas"));
        list.add(new Step("Tablet maior", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(470, 260),
                () -> tabletScreen().size()[0] > 300 && tabletScreen().cardCenter(ResourceType.ITEM) != null,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(capture("7d-tablet-estatisticas-grande"));
        list.add(new Step("cartão de energia filtra a Lista", STEP_TIMEOUT_MS,
                () -> {
                    int[] c = tabletScreen().cardCenter(ResourceType.ENERGY);
                    click(tabletScreen(), c[0], c[1]);
                },
                () -> tabletScreen().currentTab() == TabletScreen.Tab.LIST
                        && tabletScreen().getMenu().snapshot().query().type().equals(Optional.of(ResourceType.ENERGY)),
                () -> "aba " + tabletScreen().currentTab() + ", consulta " + tabletScreen().getMenu().snapshot().query()));
        list.add(capture("7e-tablet-lista-energia"));
        list.add(new Step("Tablet de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(300, 240),
                () -> tabletScreen().size()[0] == 300,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(clipCheck("Tablet: Lista no mínimo"));
        list.add(listClipTooltip("Tablet: Lista no mínimo"));
        // o "Só Energia ✕" tira o filtro: os passos seguintes procuram B na Lista sem filtro de tipo
        list.add(new Step("tirar o filtro de tipo", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().typeChipCenter();
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().getMenu().snapshot().query().type().isEmpty(),
                () -> "consulta " + tabletScreen().getMenu().snapshot().query()));
    }

    /** Tablet: cria um grupo com a rede dos itens, pausa e retoma. */
    private static void tabletGroupSteps(List<Step> list) {
        // os widgets da aba só aparecem no quadro seguinte, como para o jogador
        list.add(new Step("aba Grupos", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.tab.groups")), "aba Grupos")),
                () -> find(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.new"))) != null,
                () -> "Novo grupo visível"));
        list.add(new Step("novo grupo", STEP_TIMEOUT_MS, () -> {
            click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.new")), "Novo grupo"));
            type(GROUP_NAME);
            tabletScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> group() != null && tabletScreen().getMenu().snapshot().groups().stream()
                .anyMatch(g -> g.name().equals(GROUP_NAME)),
                () -> "grupos na tela " + tabletScreen().getMenu().snapshot().groups()));
        list.add(new Step("rede dos itens no grupo", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            int[] box = screen.groupNetworkCenter(otherNetwork);
            if (box == null) {
                throw new StepFailure("a rede " + OTHER_NETWORK + " não aparece na coluna do grupo");
            }
            click(screen, box[0], box[1]);
        }, () -> {
            WaGroup group = group();
            // e o botão Pausar já redesenhado como ativo (a tela o atualiza a cada quadro)
            AbstractWidget pause = find(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.pause")));
            return group != null && group.networks().contains(otherNetwork) && pause != null && pause.active
                    && tabletScreen().getMenu().snapshot().groups().stream()
                            .anyMatch(g -> g.name().equals(GROUP_NAME) && g.networks().contains(otherNetwork));
        }, () -> "grupo no servidor " + group()));
        list.add(new Step("pausar o grupo", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.pause")),
                        "Pausar o grupo")),
                () -> onServer(server -> NetworkSavedData.get(server).isPaused(otherNetwork))
                        && tabletScreen().getMenu().snapshot().groups().stream()
                                .anyMatch(g -> g.name().equals(GROUP_NAME) && g.paused()),
                () -> "pausada no servidor: " + onServer(server -> NetworkSavedData.get(server).isPaused(otherNetwork))));
        list.add(capture("8-tablet-grupo-pausado"));
        list.add(new Step("rede pausada: pedras param", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ((Container) server.overworld().getBlockEntity(chestA)).setItem(0, new ItemStack(Items.STONE, 64));
            return null;
        }), () -> {
            if (onServer(server -> count(server, chestB, Items.STONE)) != 192) {
                throw new StepFailure("pedras chegaram a B com a rede pausada");
            }
            return stepTicks >= 40 && onServer(server -> count(server, chestA, Items.STONE) == 64);
        }, () -> onServer(server -> "A=" + count(server, chestA, Items.STONE) + " B=" + count(server, chestB, Items.STONE))));
        list.add(new Step("retomar: pedras chegam", 30_000,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.pause")),
                        "Retomar o grupo")),
                () -> onServer(server -> !NetworkSavedData.get(server).isPaused(otherNetwork)
                        && count(server, chestA, Items.STONE) == 0 && count(server, chestB, Items.STONE) == 256),
                () -> onServer(server -> "pausada " + NetworkSavedData.get(server).isPaused(otherNetwork) + ", A="
                        + count(server, chestA, Items.STONE) + " B=" + count(server, chestB, Items.STONE))));
    }

    /** Tablet: a 20 blocos, o filtro de B pelo painel e B aberto à distância; depois volta. */
    private static void tabletRemoteSteps(List<Step> list, UUID playerId) {
        list.add(new Step("afastar 20 blocos", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                beforeTablet = player.position();
                player.teleportTo(player.serverLevel(), routerB.getX() + 0.5, routerB.getY(), routerB.getZ() + 20.5,
                        180f, 10f);
                return null;
            });
        }, () -> Minecraft.getInstance().player.position().distanceTo(Vec3.atCenterOf(routerB)) > 15,
                () -> "distância " + Minecraft.getInstance().player.position().distanceTo(Vec3.atCenterOf(routerB))));
        list.add(new Step("filtro de B a 20 blocos pelo painel", STEP_TIMEOUT_MS, () -> {
            click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.tab.list")), "aba Lista"));
            int[] row = tabletScreen().nodeRowCenter(nodeB());
            if (row == null) {
                throw new StepFailure("B não está na lista");
            }
            click(tabletScreen(), row[0], row[1]);
        }, () -> tabletScreen().nodePanel() != null && tabletScreen().panel().node().equals(Optional.of(nodeB()))
                && tabletScreen().panel().access() == PanelAccess.EDIT,
                () -> "painel " + (tabletScreen().panel() == null ? "nenhum" : tabletScreen().panel().access())));
        // o cartão de energia deixou o painel na aba Energia, que não tem filtro
        list.add(new Step("painel de B na aba Itens", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().nodePanel().tabCenter(ResourceType.ITEM);
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().nodePanel().editCenter() != null, () -> "aba " + tabletScreen().nodePanel().type()));
        list.add(new Step("Editar abre o filtro a 20 blocos", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().nodePanel().editCenter();
            click(tabletScreen(), c[0], c[1]);
        }, () -> stepTicks >= 20 && Minecraft.getInstance().screen instanceof FilterScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("Voltar retorna ao Tablet com B escolhido", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.back")), "Voltar")),
                () -> Minecraft.getInstance().screen instanceof TabletScreen screen
                        && screen.currentTab() == TabletScreen.Tab.LIST && nodeB().equals(screen.list().chosen())
                        && screen.panel() != null && screen.panel().node().equals(Optional.of(nodeB())),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("duplo clique abre B à distância", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            int[] row = screen.nodeRowCenter(nodeB());
            if (row == null) {
                throw new StepFailure("B não está na lista");
            }
            click(screen, row[0], row[1]);
            click(screen, row[0], row[1]);
        }, () -> {
            // passa alguns ticks aberta: a tela comum fecharia a mais de 8 blocos
            return stepTicks >= 20 && Minecraft.getInstance().screen instanceof RouterScreen screen
                    && screen.getMenu().snapshot().pos().equals(routerB)
                    && onServer(server -> server.getPlayerList().getPlayer(playerId).containerMenu
                            instanceof RemoteRouterMenu);
        }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("9-roteador-a-distancia"));
        list.add(close("fechar B à distância"));
        // de volta para onde estava: os passos seguintes clicam em blocos ao alcance da mão
        list.add(new Step("voltar para perto dos baús", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.teleportTo(player.serverLevel(), beforeTablet.x, beforeTablet.y, beforeTablet.z, 180f, 30f);
                return null;
            });
        }, () -> Minecraft.getInstance().player.position().distanceTo(beforeTablet) < 0.5,
                () -> "distância " + Minecraft.getInstance().player.position().distanceTo(beforeTablet)));
        list.add(new Step("tirar o nó sem rede", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.overworld().removeBlock(tabletLoneChest.above(), false);
            server.overworld().removeBlock(tabletLoneChest, false);
            return null;
        }), () -> onServer(server -> NodeIndex.get(server).entry(new NodeIndex.NodeKey(
                net.minecraft.world.level.Level.OVERWORLD, tabletLoneChest.above())) == null),
                () -> "nó sem rede ainda no índice"));
    }

    /**
     * Cada texto cortado da tela do Tablet tem, na dica da Lista naquele ponto, o texto inteiro (a dica da linha
     * ou do cabeçalho não esconde a segunda linha nem o "N diferentes" cortados).
     */
    private static Step listClipTooltip(String name) {
        String[] missing = {""};
        return new Step(name + ": dica com o texto cortado inteiro", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            TabletScreen screen = tabletScreen();
            for (int[] c : GuiText.clipCenters()) {
                Component clipped = GuiText.clipAt(c[0], c[1]);
                Component tip = screen.list().tooltip(c[0], c[1]);
                if (clipped != null && (tip == null || !tip.getString().contains(clipped.getString()))) {
                    missing[0] = clipped.getString() + " -> " + (tip == null ? "sem dica" : tip.getString());
                    return false;
                }
            }
            return true;
        }, () -> "dica sem o texto inteiro: " + missing[0]);
    }

    static TabletScreen tabletScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof TabletScreen screen) {
            return screen;
        }
        throw new StepFailure("o Tablet não está aberto (tela: " + describe(Minecraft.getInstance().screen) + ")");
    }

    private static NodeIndex.NodeKey nodeB() {
        return new NodeIndex.NodeKey(net.minecraft.world.level.Level.OVERWORLD, routerB);
    }

    private static NodeIndex.NodeKey nodeA() {
        return new NodeIndex.NodeKey(net.minecraft.world.level.Level.OVERWORLD, routerA);
    }

    /** A face do painel que o passo do modo mudou, e como ela estava (para voltar). */
    private static Direction panelFace = Direction.UP;
    private static PortMode panelModeBefore = PortMode.NONE;
    private static PortMode panelModeAfter = PortMode.NONE;
    private static @Nullable UUID panelNetworkBefore;

    /** O grupo do teste no servidor, ou {@code null}. */
    static @Nullable WaGroup group() throws Exception {
        return onServer(server -> NetworkSavedData.get(server).groups().stream()
                .filter(g -> g.name().equals(GROUP_NAME)).findFirst().orElse(null));
    }
}
