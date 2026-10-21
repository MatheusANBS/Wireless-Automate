package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import io.github.matheusanbs.wirelessautomate.client.TabLayout;

/**
 * A tela do roteador no e2e: os primeiros passos (Extrai em A, Insere em B, a vazão), o nome e a rede por face, os
 * tamanhos da tela e o transporte por aba, e os acessos à tela e ao painel do nó que as outras áreas usam.
 */
final class E2eRouterSteps {
    private E2eRouterSteps() {
    }

    private static int versionBefore;

    /** Os primeiros passos: Extrai em A, Insere em B, as pedras chegam e a vazão aparece na tela de A. */
    static void openingSteps(List<Step> list) {
        list.add(new Step("preparar", STEP_TIMEOUT_MS, E2eWorld::setUpWorld,
                () -> onServer(server -> NetworkManager.get().contains(router(server, routerA))
                        && NetworkManager.get().contains(router(server, routerB)))
                        && clientSees(routerA) && clientSees(routerB),
                () -> "roteadores em " + routerA.toShortString() + " e " + routerB.toShortString() + " na rede "
                        + mainNetwork));

        list.add(open("abrir A", () -> routerA));
        list.add(new Step("A: Cima e Extrai", STEP_TIMEOUT_MS, () -> {
            versionBefore = routerScreen().getMenu().version();
            clickFace(Direction.UP);
            clickMode(PortMode.EXTRACT);
        }, () -> onServer(server -> router(server, routerA).face(ResourceType.ITEM, Direction.UP).mode())
                == PortMode.EXTRACT && clientMode() == PortMode.EXTRACT && routerScreen().getMenu().version() > versionBefore,
                () -> "servidor e tela em " + clientModeText()));
        list.add(capture("1-roteador-a-extrai"));
        list.add(close("fechar A"));

        list.add(open("abrir B", () -> routerB));
        list.add(new Step("B: Cima e Insere", STEP_TIMEOUT_MS, () -> {
            versionBefore = routerScreen().getMenu().version();
            clickFace(Direction.UP);
            clickMode(PortMode.INSERT);
        }, () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).mode())
                == PortMode.INSERT && clientMode() == PortMode.INSERT && routerScreen().getMenu().version() > versionBefore,
                () -> "servidor e tela em " + clientModeText()));
        list.add(new Step("pedras chegam ao baú B", 30_000, () -> {
        }, () -> onServer(server -> count(server, chestA, Items.STONE) == 0 && count(server, chestB, Items.STONE) == 64),
                () -> onServer(server -> "A=" + count(server, chestA, Items.STONE) + " B="
                        + count(server, chestB, Items.STONE)) + ", vazão na tela "
                        + routerScreen().getMenu().throughput()[ResourceType.ITEM.ordinal()] + "/s"));
        list.add(capture("2-roteador-b-insere"));
        list.add(close("fechar B"));

        // a vazão é contada na origem: com a tela de A aberta, mais 64 pedras no baú A
        list.add(open("abrir A de novo", () -> routerA));
        list.add(new Step("vazão chega à tela de A", STEP_TIMEOUT_MS, () -> {
            maxItemRate = 0;
            onServer(server -> {
                ((Container) server.overworld().getBlockEntity(chestA)).setItem(0, new ItemStack(Items.STONE, 64));
                return null;
            });
        }, () -> maxItemRate > 0 && onServer(server -> count(server, chestB, Items.STONE) == 128),
                () -> "pico de " + maxItemRate + " itens/s, agora " + safeThroughput() + "/s; B="
                        + onServer(server -> count(server, chestB, Items.STONE))));
        list.add(capture("3-roteador-a-vazao"));
        list.add(close("fechar A de novo"));
        list.add(open("abrir B de novo", () -> routerB));
    }

    /** O nome do nó e a rede por face: a linha Rede troca só a face Cima, em Itens e em Energia. */
    static void networkSteps(List<Step> list) {
        list.add(new Step("renomear", STEP_TIMEOUT_MS, () -> {
            click(widget(byTooltip(Component.translatable("gui.wirelessautomate.router.rename.tooltip")), "nome do nó"));
            type(NODE_NAME);
            routerScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> NODE_NAME.equals(onServer(server -> router(server, routerB).name()))
                && NODE_NAME.equals(routerScreen().getMenu().snapshot().name()),
                () -> "servidor \"" + onServer(server -> router(server, routerB).name()) + "\", tela \""
                        + routerScreen().getMenu().snapshot().name() + "\""));
        // A rede é da face: a linha Rede troca só a face selecionada (Cima); as outras ficam na principal.
        list.add(new Step("abrir a lista da linha Rede", STEP_TIMEOUT_MS,
                () -> clickPanel(panel().networkCenter(), "pílula da linha Rede"),
                () -> routerScreen().networkListShown() && stepTicks >= 2,
                () -> "lista " + routerScreen().networkListShown()));
        list.add(capture("5a-roteador-b-lista-rede"));
        list.add(new Step("trocar a rede da face Cima em Itens", STEP_TIMEOUT_MS,
                () -> chooseNetwork(otherNetwork, OTHER_NETWORK),
                () -> onServer(server -> {
                    RouterBlockEntity router = router(server, routerB);
                    return otherNetwork.equals(router.faceNetwork(ResourceType.ITEM, Direction.UP))
                            && mainNetwork.equals(router.faceNetwork(ResourceType.ITEM, Direction.DOWN));
                }) && routerScreen().getMenu().snapshot().network(ResourceType.ITEM, Direction.UP)
                        .equals(Optional.of(otherNetwork))
                        && routerScreen().getMenu().snapshot().networks(ResourceType.ITEM).size() == 2,
                () -> "servidor " + serverNetworks(routerB) + ", tela " + clientNetworks()));
        list.add(capture("5-roteador-b-rede-itens"));
        list.add(allFacesShortcut("B: as outras faces de Itens na mesma rede", ResourceType.ITEM, () -> otherNetwork));
        list.add(new Step("aba Energia", STEP_TIMEOUT_MS,
                () -> clickTab(ResourceType.ENERGY),
                () -> {
                    UUID playerId = Minecraft.getInstance().player.getUUID();
                    return routerScreen().getMenu().selectedType() == ResourceType.ENERGY && onServer(server ->
                            server.getPlayerList().getPlayer(playerId).containerMenu instanceof RouterMenu menu
                                    && menu.selectedType() == ResourceType.ENERGY);
                }, () -> "aba na tela " + routerScreen().getMenu().selectedType()));
        list.add(new Step("trocar só a rede da face Cima em Energia", STEP_TIMEOUT_MS,
                () -> chooseNetwork(energyNetwork, ENERGY_NETWORK),
                () -> onServer(server -> {
                    RouterBlockEntity router = router(server, routerB);
                    return energyNetwork.equals(router.faceNetwork(ResourceType.ENERGY, Direction.UP))
                            && mainNetwork.equals(router.faceNetwork(ResourceType.ENERGY, Direction.DOWN))
                            && otherNetwork.equals(router.networkId(ResourceType.ITEM))
                            && mainNetwork.equals(router.networkId(ResourceType.FLUID));
                }) && routerScreen().getMenu().snapshot().network(ResourceType.ENERGY, Direction.UP)
                        .equals(Optional.of(energyNetwork))
                        && routerScreen().getMenu().snapshot().networks(ResourceType.ITEM).equals(List.of(otherNetwork)),
                () -> "servidor " + serverNetworks(routerB) + ", tela " + clientNetworks()));
        list.add(capture("5b-roteador-b-rede-energia"));
        list.add(allFacesShortcut("B: as outras faces de Energia na mesma rede", ResourceType.ENERGY, () -> energyNetwork));
        list.add(new Step("de volta à aba Itens", STEP_TIMEOUT_MS,
                () -> clickTab(ResourceType.ITEM),
                () -> routerScreen().getMenu().selectedType() == ResourceType.ITEM,
                () -> "aba na tela " + routerScreen().getMenu().selectedType()));
        if (LoadedTypes.contains(ResourceType.SOURCE)) {
            list.add(new Step("aba Source", STEP_TIMEOUT_MS,
                    () -> clickTab(ResourceType.SOURCE),
                    () -> routerScreen().getMenu().selectedType() == ResourceType.SOURCE,
                    () -> "aba na tela " + routerScreen().getMenu().selectedType()));
            list.add(clipCheck("aba Source"));
            list.add(capture("1d-roteador-aba-source"));
            list.add(new Step("de volta à aba Itens depois da Source", STEP_TIMEOUT_MS,
                    () -> clickTab(ResourceType.ITEM),
                    () -> routerScreen().getMenu().selectedType() == ResourceType.ITEM,
                    () -> "aba na tela " + routerScreen().getMenu().selectedType()));
        }
    }

    /** Os tamanhos da tela do roteador: abertura, maior, largo e o mínimo, com o modo das abas. */
    static void layoutSteps(List<Step> list) {
        // A tela abre na disposição larga (o painel inteiro ao lado do visor). Com 5 tipos (Mekanism e Ars no
        // runClient) só a aba ativa tem nome, no painel e no mínimo (300), onde as abas vão na largura toda.
        TabLayout.Mode tabsWithName = TabLayout.Mode.ACTIVE_NAME;
        list.add(new Step("abas na largura de abertura", STEP_TIMEOUT_MS,
                () -> { },
                () -> routerScreen().wideLayout() && routerScreen().tabMode() == tabsWithName,
                () -> "larga " + routerScreen().wideLayout() + ", modo das abas " + routerScreen().tabMode()));
        list.add(capture("1b-roteador-abas"));
        // Maior (420 x 300): a disposição larga, com o inventário embaixo do visor, à esquerda.
        list.add(new Step("roteador maior", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(420, 300),
                () -> routerScreen().size()[0] == Math.min(420, Math.max(300, routerScreen().width - 8))
                        && routerScreen().wideLayout() && routerScreen().tabMode() == tabsWithName
                        && routerScreen().getMenu().slots.get(RouterMenu.INVENTORY_START).x == 9 + 1,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", abas " + routerScreen().tabMode()));
        list.add(capture("1c-roteador-grande"));
        // Bem largo: o painel para de crescer e a largura extra vai para o visor.
        list.add(new Step("roteador largo, o visor fica com a largura extra", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(Math.min(600, routerScreen().width - 8), 300),
                () -> routerScreen().size()[0] > 500 && routerScreen().viewColumnWidth() > 250,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", visor "
                        + routerScreen().viewColumnWidth()));
        list.add(capture("1f-roteador-largo"));
        list.add(new Step("roteador no mínimo, disposição estreita", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(300, 240),
                () -> routerScreen().size()[0] == 300 && routerScreen().size()[1] == 240 && !routerScreen().wideLayout()
                        && routerScreen().tabMode() == tabsWithName
                        && routerScreen().getMenu().slots.get(RouterMenu.INVENTORY_START).x == 300 - 9 - 162 + 1,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", abas " + routerScreen().tabMode()));
        list.add(clipCheck("roteador no mínimo"));
        list.add(capture("1g-roteador-minimo"));
    }

    /** Abre a lista da linha Rede do painel e clica na linha da rede (ou em "Sem rede", a última). */
    static void chooseNetwork(@Nullable UUID network, String name) throws StepFailure {
        RouterScreen screen = routerScreen();
        if (!screen.networkListShown()) {
            clickPanel(panel().networkCenter(), "pílula da linha Rede");
        }
        List<NetworkEntry> networks = screen.getMenu().snapshot().networks();
        // "Sem rede" é a última linha, depois das redes
        int row = network == null ? networks.size() : -1;
        for (int i = 0; i < networks.size(); i++) {
            if (networks.get(i).id().equals(network)) {
                row = i;
            }
        }
        if (row < 0) {
            throw new StepFailure("a rede " + name + " não está na lista da tela: " + networks);
        }
        clickPanel(panel().networkRowCenter(row), "a linha da rede " + name);
    }

    /**
     * Atalho no servidor: todas as faces do tipo {@code type} do roteador B na rede {@code network}, como a
     * rede da aba fazia, para os passos de transporte por aba; espera a tela mostrar só essa rede. A rede
     * vem por {@code Supplier}: os campos das redes só existem quando os passos rodam.
     */
    private static Step allFacesShortcut(String label, ResourceType type, Supplier<UUID> network) {
        return new Step(label, STEP_TIMEOUT_MS, () -> onServer(server -> {
            router(server, routerB).setNetworkId(type, network.get());
            return null;
        }), () -> routerScreen().getMenu().snapshot().networks(type).equals(List.of(network.get())),
                () -> "servidor " + serverNetworks(routerB) + ", tela " + clientNetworks());
    }

    /** Redes das faces de cada aba no snapshot da tela, para o log. */
    private static String clientNetworks() throws StepFailure {
        RouterSnapshot s = routerScreen().getMenu().snapshot();
        return "itens " + networkNames(s.networks(ResourceType.ITEM)) + ", fluidos "
                + networkNames(s.networks(ResourceType.FLUID)) + ", energia "
                + networkNames(s.networks(ResourceType.ENERGY));
    }

    /**
     * Transporte por aba: com os itens de B na "E2E Outra" e os de A ainda na principal, pedras
     * postas em A ficam paradas; com os itens de A também na "E2E Outra" elas chegam a B, mesmo com a
     * energia de A na principal e a de B na "E2E Energia".
     */
    static void typeNetworkTransferSteps(List<Step> list) {
        list.add(new Step("itens de A e B em redes diferentes ficam parados", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ((Container) server.overworld().getBlockEntity(chestA)).setItem(0, new ItemStack(Items.STONE, 64));
            return null;
        }), () -> {
            if (onServer(server -> count(server, chestB, Items.STONE)) != 128) {
                throw new StepFailure("pedras chegaram a B com os itens em redes diferentes");
            }
            // 2 s parados bastam: na mesma rede as 64 pedras passaram em menos de 1 s
            return stepTicks >= 40 && onServer(server -> count(server, chestA, Items.STONE) == 64);
        }, () -> onServer(server -> "A=" + count(server, chestA, Items.STONE) + " B=" + count(server, chestB, Items.STONE)
                + "; A: " + serverNetworksText(server, routerA) + "; B: " + serverNetworksText(server, routerB))));
        list.add(new Step("itens de A na rede dos de B chegam, energia em outras redes", 30_000, () -> onServer(server -> {
            router(server, routerA).setNetworkId(ResourceType.ITEM, otherNetwork);
            return null;
        }), () -> onServer(server -> count(server, chestA, Items.STONE) == 0 && count(server, chestB, Items.STONE) == 192
                && mainNetwork.equals(router(server, routerA).networkId(ResourceType.ENERGY))
                && energyNetwork.equals(router(server, routerB).networkId(ResourceType.ENERGY))),
                () -> onServer(server -> "A=" + count(server, chestA, Items.STONE) + " B=" + count(server, chestB, Items.STONE)
                        + "; A: " + serverNetworksText(server, routerA) + "; B: " + serverNetworksText(server, routerB))));
    }

    static RouterScreen routerScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof RouterScreen screen) {
            return screen;
        }
        throw new StepFailure("a tela do roteador não está aberta (tela: " + describe(Minecraft.getInstance().screen) + ")");
    }

    private static PortMode clientMode() throws StepFailure {
        return routerScreen().getMenu().snapshot().face(ResourceType.ITEM, Direction.UP).mode();
    }

    private static String clientModeText() {
        try {
            return clientMode().name();
        } catch (StepFailure e) {
            return e.getMessage();
        }
    }

    private static String safeThroughput() {
        return Minecraft.getInstance().screen instanceof RouterScreen screen
                ? Long.toString(screen.getMenu().throughput()[ResourceType.ITEM.ordinal()]) : "?";
    }

    static boolean clientSees(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getBlockState(pos).getBlock() instanceof RouterBlock;
    }

    /** O painel do nó da tela do roteador aberta. */
    static NodePanel panel() throws StepFailure {
        return routerScreen().panel();
    }

    /** Clique no ponto de um controle do painel (um gancho dele); {@code null} = o controle não está lá. */
    static void clickPanel(int @Nullable [] point, String what) throws StepFailure {
        if (point == null) {
            throw new StepFailure("não achei " + what + " no painel da tela " + describe(Minecraft.getInstance().screen));
        }
        click(routerScreen(), point[0], point[1]);
    }

    /** Escolhe a face {@code direction} (absoluta na presa, canônica num alvo do alcance), sem mudar o modo. */
    static void clickFace(Direction direction) throws StepFailure {
        routerScreen().syncNow();
        panel().selectFace(direction);
    }

    /** Escolhe a face do lado {@code side} (relativo à frente da máquina), sem mudar o modo. */
    static void clickSide(RemoteSide side) throws StepFailure {
        routerScreen().syncNow();
        panel().selectSide(side);
    }

    static void clickMode(PortMode mode) throws StepFailure {
        routerScreen().syncNow();
        panel().chooseMode(mode);
    }

    static void clickTab(ResourceType type) throws StepFailure {
        clickPanel(panel().tabCenter(type), "a aba " + type);
    }
}
