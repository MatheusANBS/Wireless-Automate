package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.linker.LinkerScreen;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import org.jetbrains.annotations.Nullable;

/**
 * A volta em português com uma rede de nome longo: roteador, Vinculador e Tablet, no
 * mínimo e no máximo, conferindo que todo texto cortado tem o texto inteiro no tooltip.
 */
final class E2ePortugueseSteps {
    private E2ePortugueseSteps() {
    }

    private static final String LONG_NETWORK = "Rede de teste com um nome bem comprido 40";
    private static UUID longNetwork;
    /** A rede de itens de B e a rede ativa do jogador antes da rede de nome longo, devolvidas no fim. */
    private static @Nullable UUID previousItemNetwork;
    private static @Nullable UUID previousActive;

    /**
     * Volta em português com uma rede de nome longo: roteador (mínimo e máximo), Vinculador e Tablet
     * (mínimo e máximo), com as capturas {@code *-pt-*} e, em cada tela, a conferência de que todo
     * texto cortado tem o texto inteiro no tooltip.
     */
    static void portugueseSteps(List<Step> list) {
        ptRouterSteps(list);
        ptLinkerSteps(list);
        ptTabletSteps(list);
    }

    /** Português: a rede de nome longo em B e o roteador no mínimo e no máximo. */
    private static void ptRouterSteps(List<Step> list) {
        list.add(language("pt_br"));
        list.add(new Step("rede de nome longo no roteador B", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                previousItemNetwork = router(server, routerB).networkId(ResourceType.ITEM);
                previousActive = NetworkSavedData.get(server).activeNetwork(playerId);
                longNetwork = NetworkSavedData.get(server).create(playerId, LONG_NETWORK).id();
                NetworkSavedData.get(server).setActiveNetwork(playerId, longNetwork);
                router(server, routerB).setNetworkId(ResourceType.ITEM, longNetwork);
                // mão vazia: com o Vinculador na mão, o clique vincularia o roteador em vez de abrir a tela
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return null;
            });
        }, () -> onServer(server -> longNetwork.equals(router(server, routerB).networkId(ResourceType.ITEM)))
                && Minecraft.getInstance().player.getMainHandItem().isEmpty(),
                () -> "rede " + longNetwork));

        list.add(open("pt: abrir B", () -> routerB));
        list.add(new Step("pt: roteador no mínimo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(300, 240),
                () -> routerScreen().size()[0] == 300 && routerScreen().size()[1] == 240
                        && routerScreen().getMenu().snapshot().networks(ResourceType.ITEM).equals(List.of(longNetwork)),
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", rede "
                        + routerScreen().getMenu().snapshot().networks(ResourceType.ITEM)));
        list.add(new Step("pt: nome longo cortado com tooltip inteiro", STEP_TIMEOUT_MS, () -> {
            int[] center = GuiText.firstClipCenter();
            if (center != null) {
                moveMouse(center[0], center[1]);
            }
        }, () -> {
            for (int[] c : GuiText.clipCenters()) {
                Component full = GuiText.clipAt(c[0], c[1]);
                if (full != null && full.getString().contains(LONG_NETWORK)) {
                    return true;
                }
            }
            return false;
        }, () -> GuiText.clipCount() + " texto(s) cortado(s), nenhum com o nome longo inteiro"));
        list.add(capture("1d-roteador-pt-min"));
        list.add(new Step("pt: roteador no máximo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(10_000, 10_000),
                () -> routerScreen().size()[0] == routerScreen().width - 8 && routerScreen().size()[1] > 240,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size())));
        list.add(clipCheck("pt: roteador máximo"));
        list.add(capture("1e-roteador-pt-max"));
        list.add(new Step("pt: roteador de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(300, 240),
                () -> routerScreen().size()[0] == 300,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size())));
        list.add(close("pt: fechar B"));
    }

    /** Português: o Vinculador. */
    private static void ptLinkerSteps(List<Step> list) {
        // Vinculador sem roteador escolhido: a ajuda dos gestos e o aviso são os textos mais longos
        list.add(new Step("pt: Vinculador na mão", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID())
                    .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
            return null;
        }), () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.LINKER.get()),
                () -> "na mão " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(new Step("pt: abrir o Vinculador", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof LinkerScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(clipCheck("pt: Vinculador"));
        list.add(capture("8c-vinculador-pt"));
        list.add(close("pt: fechar o Vinculador"));
    }

    /** Português: o Tablet no mínimo e no máximo, e a volta ao inglês. */
    private static void ptTabletSteps(List<Step> list) {
        list.add(E2eFlowsSteps.machines("pt: roteadores dos Fluxos", true));
        list.add(new Step("pt: Tablet na mão", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID())
                    .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.NETWORK_TABLET.get()));
            return null;
        }), () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.NETWORK_TABLET.get()),
                () -> "na mão " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(new Step("pt: abrir o Tablet", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof TabletScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("pt: Tablet no mínimo, Lista", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(420, 240),
                () -> tabletScreen().size()[0] == 420 && stepTicks >= 10,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(clipCheck("pt: Tablet Lista"));
        list.add(new Step("pt: aba Fluxos", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.tablet.tab.flows"), "aba Fluxos")),
                () -> tabletScreen().currentTab() == TabletScreen.Tab.FLOWS,
                () -> "aba " + tabletScreen().currentTab()));
        list.add(E2eFlowsSteps.measured("pt: Fluxos medidos"));
        list.add(clipCheck("pt: Tablet mínimo"));
        list.add(capture("7f-tablet-pt-min"));
        list.add(new Step("pt: Tablet médio", STEP_TIMEOUT_MS, () -> tabletScreen().previewResize(470, 280),
                () -> tabletScreen().size()[0] == 470 && E2eFlowsSteps.flows().rowCenter(ResourceType.ENERGY, null) != null,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(clipCheck("pt: Fluxos"));
        list.add(capture("7c-tablet-fluxos-pt"));
        list.add(new Step("pt: Tablet no máximo", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(10_000, 10_000),
                () -> tabletScreen().size()[0] == tabletScreen().width - 8
                        && E2eFlowsSteps.flows().rowCenter(ResourceType.ENERGY, null) != null,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(clipCheck("pt: Tablet máximo"));
        list.add(capture("7g-tablet-pt-max"));
        list.add(new Step("pt: Tablet de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(420, 240),
                () -> tabletScreen().size()[0] == 420,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(close("pt: fechar o Tablet"));
        list.add(E2eFlowsSteps.machines("pt: tirar os roteadores dos Fluxos", false));
        list.add(language("en_us"));
        // a rede de nome longo sai: as suítes seguintes (a Tela de Sinal) listam as redes e a cortariam sem tooltip
        list.add(new Step("pt: tirar a rede de nome longo", STEP_TIMEOUT_MS, () -> onServer(server -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            NetworkSavedData data = NetworkSavedData.get(server);
            router(server, routerB).setNetworkId(ResourceType.ITEM, previousItemNetwork);
            data.setActiveNetwork(playerId, previousActive);
            data.remove(longNetwork);
            return null;
        }), () -> onServer(server -> NetworkSavedData.get(server).network(longNetwork) == null),
                () -> "rede " + longNetwork));
    }
}
