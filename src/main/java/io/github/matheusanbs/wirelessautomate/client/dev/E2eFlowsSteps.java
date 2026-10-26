package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eDarkSteps.themeClick;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.listClipTooltip;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.tabletScreen;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowsTab;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Fluxos do Tablet no e2e, com máquinas que movem de verdade: um Baú do mod que dá pedregulho a outro Baú do
 * mod e a um baú cheio (um destino cheio) na rede {@link #FLOWS_NETWORK}, e uma Bateria que dá energia a outra,
 * Armazém, na rede da energia (o saldo "enchendo"). Os roteadores e a rede {@link #FLOWS_NETWORK} só existem
 * durante os passos dos Fluxos; as máquinas ficam.
 */
final class E2eFlowsSteps {
    private E2eFlowsSteps() {
    }

    static final String FLOWS_NETWORK = "E2E Fluxos";
    private static @Nullable UUID flowsNetwork;
    /** Os roteadores dos Fluxos, em cima das máquinas. */
    private static List<BlockPos> flowRouters = List.of();

    /**
     * A aba Fluxos: Por tipo com a Energia aberta e as redes dela, Por rede, no escuro, e o clique na linha da
     * Energia na rede da energia, que abre a Lista filtrada pelo tipo e pela rede.
     */
    static void flowsSteps(List<Step> list) {
        list.add(machines("Fluxos: máquinas e roteadores", true));
        list.add(new Step("aba Fluxos", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.tablet.tab.flows"), "aba Fluxos")),
                () -> tabletScreen().currentTab() == TabletScreen.Tab.FLOWS,
                () -> "aba " + tabletScreen().currentTab()));
        list.add(new Step("Tablet maior", STEP_TIMEOUT_MS, () -> tabletScreen().previewResize(470, 280),
                () -> tabletScreen().size()[0] == 470, () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(measured("Fluxos: os 5 s medidos"));
        list.add(new Step("Fluxos: abrir a Energia", STEP_TIMEOUT_MS, () -> {
            int[] c = flows().chevronCenter(ResourceType.ENERGY);
            if (c == null) {
                throw new StepFailure("a Energia não tem a seta");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> flows().isOpen(ResourceType.ENERGY) && flows().rowCenter(ResourceType.ENERGY, energyNetwork) != null,
                () -> "aberta " + flows().isOpen(ResourceType.ENERGY)));
        list.add(capture("7c-tablet-fluxos"));
        list.add(clipCheck("Tablet: Fluxos Por tipo"));
        list.add(new Step("Fluxos: Por rede", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.tablet.flows.by_network"), "Por rede")),
                () -> flows().byNetwork() && flows().rowCenter(null, flowsNetwork) != null
                        && flows().rowCenter(ResourceType.ITEM, flowsNetwork) != null,
                () -> "Por rede " + flows().byNetwork()));
        list.add(capture("7c-tablet-fluxos-rede"));
        list.add(clipCheck("Tablet: Fluxos Por rede"));
        E2eVariants.escuro(list, () -> {
            list.add(themeClick("Fluxos: escuro", GuiTheme.DARK));
            list.add(new Step("Fluxos: Por tipo no escuro", STEP_TIMEOUT_MS,
                    () -> click(widget(byMessageKey("gui.wirelessautomate.tablet.flows.by_type"), "Por tipo")),
                    () -> !flows().byNetwork() && flows().rowCenter(ResourceType.ENERGY, energyNetwork) != null,
                    () -> "Por rede " + flows().byNetwork()));
            list.add(capture("7c-tablet-fluxos-escuro"));
            list.add(clipCheck("Tablet: Fluxos no escuro"));
            list.add(themeClick("Fluxos: claro de novo", GuiTheme.LIGHT));
        });
        list.add(new Step("linha da Energia na rede filtra a Lista", STEP_TIMEOUT_MS, () -> {
            int[] c = flows().rowCenter(ResourceType.ENERGY, energyNetwork);
            click(tabletScreen(), c[0], c[1]);
        }, () -> {
            TabletSnapshot.Query q = tabletScreen().getMenu().snapshot().query();
            return tabletScreen().currentTab() == TabletScreen.Tab.LIST && q.type().equals(Optional.of(ResourceType.ENERGY))
                    && q.networks().equals(List.of(energyNetwork)) && !tabletScreen().getMenu().snapshot().nodes().isEmpty();
        }, () -> "aba " + tabletScreen().currentTab() + ", consulta " + tabletScreen().getMenu().snapshot().query()));
        list.add(capture("7e-tablet-lista-energia"));
        list.add(new Step("Tablet de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(300, 240),
                () -> tabletScreen().size()[0] == 300,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(clipCheck("Tablet: Lista no mínimo"));
        list.add(listClipTooltip("Tablet: Lista no mínimo"));
        // os passos seguintes procuram B na Lista sem filtro
        list.add(new Step("tirar os filtros", STEP_TIMEOUT_MS, () -> tabletScreen().list().clearAll(), () -> {
            TabletSnapshot.Query q = tabletScreen().getMenu().snapshot().query();
            return q.type().isEmpty() && q.networks().isEmpty();
        }, () -> "consulta " + tabletScreen().getMenu().snapshot().query()));
        list.add(machines("Fluxos: tirar os roteadores", false));
    }

    static FlowsTab flows() throws StepFailure {
        return tabletScreen().flows();
    }

    /** Espera os Fluxos com a janela cheia e algo movido nos Itens e na Energia (Por tipo). */
    static Step measured(String name) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
        }, () -> !flows().byNetwork() && flows().measured(ResourceType.ITEM, null)
                && flows().measured(ResourceType.ENERGY, null),
                () -> "itens " + flows().rowText(ResourceType.ITEM, null) + ", energia "
                        + flows().rowText(ResourceType.ENERGY, null));
    }

    /**
     * Põe os roteadores dos Fluxos (e, da primeira vez, as máquinas e a rede {@link #FLOWS_NETWORK}) ou os tira.
     * As máquinas ficam em x − 8 a x − 12 do baú A, longe dos outros passos.
     */
    static Step machines(String name, boolean place) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        return new Step(name, STEP_TIMEOUT_MS, () -> onServer(server -> {
            if (place) {
                place(server, playerId);
            } else {
                ServerLevel level = server.overworld();
                for (BlockPos pos : flowRouters) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
                // a rede sai também: a lista de redes do Vinculador, mais adiante, não a espera
                if (flowsNetwork != null) {
                    NetworkSavedData.get(server).remove(flowsNetwork);
                    flowsNetwork = null;
                }
            }
            return null;
        }), () -> onServer(server -> {
            for (BlockPos pos : flowRouters) {
                boolean there = server.overworld().getBlockEntity(pos) instanceof RouterBlockEntity router
                        && NetworkManager.get().contains(router);
                boolean indexed = NodeIndex.get(server).entry(new NodeIndex.NodeKey(Level.OVERWORLD, pos)) != null;
                if (there != place || indexed != place) {
                    return false;
                }
            }
            return !flowRouters.isEmpty();
        }), () -> "roteadores dos Fluxos " + flowRouters);
    }

    private static void place(MinecraftServer server, UUID playerId) {
        ServerLevel level = server.overworld();
        NetworkSavedData data = NetworkSavedData.get(server);
        if (flowsNetwork == null || data.network(flowsNetwork) == null) {
            flowsNetwork = data.create(playerId, FLOWS_NETWORK).id();
        }
        BlockPos base = chestA.offset(-8, 0, 0);
        BlockPos source = base;
        BlockPos chest = base.offset(-2, 0, 0);
        BlockPos full = base.offset(-4, 0, 0);
        BlockPos battery = base.offset(0, 0, -2);
        BlockPos store = base.offset(-2, 0, -2);
        BlockState modChest = ModBlocks.STORAGE.get(StorageKind.CHEST).get().defaultBlockState();
        BlockState modBattery = ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ADVANCED);
        if (!level.getBlockState(source).is(modChest.getBlock())) {
            level.setBlockAndUpdate(source, modChest);
            level.setBlockAndUpdate(chest, modChest);
            level.setBlockAndUpdate(full, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(battery, modBattery);
            level.setBlockAndUpdate(store, modBattery);
            ((StorageChestBlockEntity) level.getBlockEntity(source)).storage()
                    .insert(new ItemStack(Items.COBBLESTONE), 20_000, false);
            Container container = (Container) level.getBlockEntity(full);
            for (int i = 0; i < container.getContainerSize(); i++) {
                container.setItem(i, new ItemStack(Items.DIRT, 64));
            }
            StorageBatteryBlockEntity energy = (StorageBatteryBlockEntity) level.getBlockEntity(battery);
            energy.store().insert(8_000_000L, false);
        }
        router(level, source, playerId, flowsNetwork, ResourceType.ITEM, PortMode.EXTRACT);
        router(level, chest, playerId, flowsNetwork, ResourceType.ITEM, PortMode.INSERT);
        router(level, full, playerId, flowsNetwork, ResourceType.ITEM, PortMode.INSERT);
        router(level, battery, playerId, energyNetwork, ResourceType.ENERGY, PortMode.EXTRACT);
        router(level, store, playerId, energyNetwork, ResourceType.ENERGY, PortMode.BOTH);
        flowRouters = List.of(source.above(), chest.above(), full.above(), battery.above(), store.above());
    }

    private static void router(ServerLevel level, BlockPos machine, UUID playerId, UUID network, ResourceType type,
            PortMode mode) {
        BlockPos pos = machine.above();
        level.setBlockAndUpdate(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = (RouterBlockEntity) level.getBlockEntity(pos);
        router.setName(type == ResourceType.ITEM ? "E2E Pedregulho" : "E2E Bateria");
        router.setNetworkId(type, network);
        router.setMode(type, Direction.UP, mode);
        NodeIndex.placedBy(router, playerId);
    }
}
