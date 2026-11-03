package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevScreenshot.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotRouter.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.PanelAccess;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.network.FlowWindow;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TabletFlowsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;

/**
 * As capturas do Tablet de rede, com um snapshot de exemplo: a Lista com o painel do nó, a busca, o perfil, a
 * seleção, o escuro, o mínimo e as outras abas.
 */
final class ScreenshotTablet {
    private ScreenshotTablet() {
    }

    /** Desenhada no lugar das outras a partir do primeiro passo do Tablet. */
    static TabletScreen tabletScreen;

    /**
     * Tablet de rede com um snapshot de exemplo (os nós do rascunho visual, em volta do jogador): a Lista com o
     * painel do nó, a dica de uma linha, as sugestões da busca, o perfil copiado, a seleção, o painel só para ler,
     * no escuro e no tamanho mínimo, e as outras abas.
     */
    static List<Step> tabletSteps() {
        return List.of(
                new Step(() -> {
                    mouseX = mouseY = -1;
                    TabletMenu menu = new TabletMenu(0, new Inventory(null), sampleTablet(false));
                    tabletScreen = new TabletScreen(menu, new Inventory(null),
                            Component.translatable("item.wirelessautomate.network_tablet"), true);
                    Minecraft minecraft = Minecraft.getInstance();
                    tabletScreen.init(minecraft, minecraft.getWindow().getGuiScaledWidth(),
                            minecraft.getWindow().getGuiScaledHeight());
                    tabletScreen.previewResize(420, 280);
                    choosePanel(PanelAccess.EDIT);
                }, "t1-tablet-lista"),
                new Step(() -> tabletScreen.previewGrid(true), "t1b-tablet-grade"),
                new Step(() -> {
                    tabletScreen.previewGrid(false);
                    int[] center = tabletScreen.nodeRowCenter(tabletNodeKey("Fornalha 2"));
                    mouseX = center[0];
                    mouseY = center[1];
                }, "t2-tablet-lista-dica"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    tabletScreen.list().previewText("forn");
                }, "t2b-tablet-busca"),
                new Step(() -> {
                    tabletScreen.list().previewText("");
                    tabletScreen.getMenu().applySnapshot(sampleTablet(true));
                    mouseX = mouseY = -1;
                }, "t2c-tablet-perfil"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    tabletScreen.previewMulti(true, 3);
                }, "t3-tablet-selecionar"),
                new Step(() -> {
                    tabletScreen.previewMulti(false, 0);
                    choosePanel(PanelAccess.READ_OUT_OF_REACH);
                    int[] center = tabletScreen.nodePanel().faceCenter(tabletScreen.nodePanel().face());
                    mouseX = center == null ? -1 : center[0];
                    mouseY = center == null ? -1 : center[1];
                }, "t3b-tablet-so-leitura"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    choosePanel(PanelAccess.EDIT);
                    themeBefore = GuiThemes.current();
                    GuiThemes.preview(GuiTheme.DARK);
                }, "t3c-tablet-escuro"),
                new Step(() -> tabletScreen.previewResize(300, 240), "t3d-tablet-escuro-minimo"),
                new Step(() -> GuiThemes.preview(themeBefore), "t3e-tablet-minimo"),
                new Step(() -> {
                    tabletScreen.previewResize(420, 280);
                    tabletScreen.previewTab(TabletScreen.Tab.MAP);
                    tabletScreen.previewMapSelect(3);
                }, "t4-tablet-mapa"),
                new Step(() -> {
                    tabletScreen.previewTab(TabletScreen.Tab.FLOWS);
                    tabletScreen.getMenu().applyFlows(sampleFlows());
                    tabletScreen.flows().previewOpen(ResourceType.ENERGY, true);
                }, "t5-tablet-fluxos"),
                new Step(() -> tabletScreen.flows().previewMode(true), "t5b-tablet-fluxos-rede"),
                new Step(() -> {
                    tabletScreen.flows().previewMode(false);
                    tabletScreen.previewTab(TabletScreen.Tab.NETWORKS);
                    tabletScreen.previewNetwork(0);
                }, "t6-tablet-redes"),
                new Step(() -> {
                    tabletScreen.previewNetwork(2);
                    tabletScreen.previewNewOpen("Utilidades");
                }, "t7-tablet-redes-alheia-nova"),
                new Step(() -> tabletScreen.previewGroup(0), "t8-tablet-grupos"));
    }

    /** Escolhe a "Fornalha 3" na Lista e põe o painel dela, com o acesso dado (a 18 blocos, alcance de 128). */
    private static void choosePanel(PanelAccess access) {
        NodeIndex.NodeKey key = tabletNodeKey("Fornalha 3");
        tabletScreen.list().previewChoose(key);
        RouterSnapshot s = sample();
        RouterSnapshot named = new RouterSnapshot(key.pos(), "Fornalha 3", s.tier(), s.facing(), s.attachedFront(),
                s.networks(), s.powered(), s.machine(), s.machineState(), s.faces());
        tabletScreen.getMenu().applyPanel(new TabletPanelPayload(0, Optional.of(key), Optional.of(named), access,
                access == PanelAccess.READ_OUT_OF_REACH ? 300 : 18, 128));
    }

    private static NodeIndex.NodeKey tabletNodeKey(String name) {
        return sampleTablet(false).nodes().stream().filter(n -> n.name().equals(name)).findFirst().orElseThrow().key();
    }

    /**
     * Fluxos de exemplo nas redes do {@link #sampleTablet}: a Base com itens e energia enchendo, os Fluidos, a
     * Energia de outro dono e a Linha de fundição pausada; totais da janela cheia de 5 s.
     */
    private static TabletFlowsPayload sampleFlows() {
        UUID base = UUID.nameUUIDFromBytes("base".getBytes());
        UUID fluids = UUID.nameUUIDFromBytes("fluidos".getBytes());
        UUID energy = UUID.nameUUIDFromBytes("energia".getBytes());
        UUID line = UUID.nameUUIDFromBytes("linha".getBytes());
        FlowStats.States waiting = new FlowStats.States(3, 1, 3, 4, 1, 0);
        FlowStats.States moving = new FlowStats.States(2, 0, 0, 2, 0, 0);
        List<TabletFlowsPayload.NetworkFlows> networks = List.of(
                new TabletFlowsPayload.NetworkFlows(base, false, 42_000, List.of(
                        flow(base, ResourceType.ITEM, 1_850, 1_870, 0, 20, waiting),
                        flow(base, ResourceType.ENERGY, 4_800_000, 3_150_000, 1_650_000, 0, moving))),
                new TabletFlowsPayload.NetworkFlows(fluids, false, 18_000, List.of(
                        flow(fluids, ResourceType.FLUID, 10_000, 10_000, 0, 0, moving))),
                new TabletFlowsPayload.NetworkFlows(energy, false, 9_000, List.of(
                        flow(energy, ResourceType.ENERGY, 4_000_000, 4_000_000, 0, 0, moving))),
                new TabletFlowsPayload.NetworkFlows(line, true, 0, List.of()));
        return new TabletFlowsPayload(0, 80_000, 500_000, networks, List.of(0, 0, 0, 0, 0));
    }

    private static FlowStats flow(UUID network, ResourceType type, long produced, long consumed, long stored,
            long released, FlowStats.States states) {
        return new FlowStats(network, type, produced, consumed, stored, released, FlowWindow.SECONDS, states);
    }

    /** {@code bits} com o modo da face do lado {@code side} da máquina, do tipo {@code type}. */
    static long mode(long bits, ResourceType type, RemoteSide side, PortMode mode) {
        return bits | (long) mode.ordinal() << (type.ordinal() * 6 + side.ordinal()) * 2;
    }

    /**
     * Nós do rascunho visual em volta do jogador em (0, 64, 0), já na ordem do servidor (máquina e nome), com
     * redes, um grupo e status variados. Com {@code profile}, o perfil copiado da "Fornalha Elite" e a comparação
     * de cada nó carregado.
     */
    private static TabletSnapshot sampleTablet(boolean profile) {
        UUID base = UUID.nameUUIDFromBytes("base".getBytes());
        UUID fluids = UUID.nameUUIDFromBytes("fluidos".getBytes());
        UUID energy = UUID.nameUUIDFromBytes("energia".getBytes());
        UUID ore = UUID.nameUUIDFromBytes("minerio".getBytes());
        UUID line = UUID.nameUUIDFromBytes("linha".getBytes());
        List<TabletSnapshot.NetworkView> networks = List.of(
                new TabletSnapshot.NetworkView(base, "Base", 0x45D6CC, "Dev", true, true, false, false, 5, 1),
                new TabletSnapshot.NetworkView(fluids, "Fluidos", 0x3D8BFF, "Dev", true, true, false, false, 2, 0),
                new TabletSnapshot.NetworkView(energy, "Energia", 0xFFB020, "Convidado", false, false, true, false, 3, 0),
                new TabletSnapshot.NetworkView(ore, "Minério", 0xD8875A, "Dev", true, true, true, true, 4, 0),
                new TabletSnapshot.NetworkView(line, "Linha de fundição", 0xA46CFF, "Dev", true, true, false, true, 8, 0));
        int extractItems = NodeIndex.role(ResourceType.ITEM, NodeIndex.EXTRACT);
        int insertItems = NodeIndex.role(ResourceType.ITEM, NodeIndex.INSERT);
        int insertFluids = NodeIndex.role(ResourceType.FLUID, NodeIndex.INSERT);
        int extractEnergy = NodeIndex.role(ResourceType.ENERGY, NodeIndex.EXTRACT);
        int insertEnergy = NodeIndex.role(ResourceType.ENERGY, NodeIndex.INSERT);
        int storageItems = NodeIndex.role(ResourceType.ITEM, NodeIndex.STORAGE);
        long furnace = mode(mode(mode(0, ResourceType.ITEM, RemoteSide.TOP, PortMode.INSERT), ResourceType.ITEM,
                RemoteSide.BOTTOM, PortMode.EXTRACT), ResourceType.ENERGY, RemoteSide.BACK, PortMode.INSERT);
        long chest = mode(0, ResourceType.ITEM, RemoteSide.TOP, PortMode.INSERT);
        long barrel = mode(0, ResourceType.ITEM, RemoteSide.TOP, PortMode.BOTH);
        long tank = mode(0, ResourceType.FLUID, RemoteSide.TOP, PortMode.INSERT);
        long reactor = mode(0, ResourceType.ENERGY, RemoteSide.BOTTOM, PortMode.EXTRACT);
        ProfileDiff.ProfileMatch equal = ProfileDiff.ProfileMatch.EQUAL;
        List<TabletSnapshot.NodeView> nodes = List.of(
                tabletNode(12, 64, -20, "Barril de pedra", "barrel", RouterTier.ADVANCED, line, null, null, storageItems,
                        TabletSnapshot.NodeStatus.ACTIVE, barrel, 64, profile ? ProfileDiff.ProfileMatch.differs(
                                ResourceType.ITEM, RemoteSide.TOP, ProfileDiff.What.MODE) : null),
                tabletNode(14, 64, -20, "Barril de sobras", "barrel", RouterTier.ADVANCED, base, null, null, storageItems,
                        TabletSnapshot.NodeStatus.ACTIVE, barrel, 12, profile ? ProfileDiff.ProfileMatch.differs(
                                ResourceType.ITEM, RemoteSide.TOP, ProfileDiff.What.MODE) : null),
                tabletNode(-60, 40, -75, "Reator de fissão", "beacon", RouterTier.ULTIMATE, null, null, energy,
                        extractEnergy, TabletSnapshot.NodeStatus.ACTIVE, reactor, 0, null),
                tabletNode(44, 64, 46, "Separador eletrolítico", "blast_furnace", RouterTier.ELITE, ore, fluids, energy,
                        insertFluids | insertEnergy, TabletSnapshot.NodeStatus.PAUSED, tank, 0, null),
                tabletNode(38, 64, 40, "Tanque de água", "cauldron", RouterTier.ADVANCED, null, fluids, null, insertFluids,
                        TabletSnapshot.NodeStatus.IDLE, tank, 0, null),
                tabletNode(5, 66, -2, "", "chest", RouterTier.ELITE, base, null, null, insertItems,
                        TabletSnapshot.NodeStatus.IDLE, chest, 0, profile ? ProfileDiff.ProfileMatch.differs(
                                ResourceType.ITEM, RemoteSide.BOTTOM, ProfileDiff.What.MODE) : null),
                tabletNode(5, 66, 2, "Baú de lingotes 2", "chest", RouterTier.ELITE, base, null, null, insertItems,
                        TabletSnapshot.NodeStatus.FULL, chest, 0, null),
                tabletNode(7, 66, 2, "Baú de minério", "chest", RouterTier.BASIC, null, null, null, storageItems,
                        TabletSnapshot.NodeStatus.NO_NETWORK, barrel, 0, null),
                tabletNode(0, 64, 9, "", "dropper", RouterTier.BASIC, null, null, null, 0,
                        TabletSnapshot.NodeStatus.NO_NETWORK, 0L, 0, null),
                tabletNode(-5, 66, -2, "Fornalha 2", "furnace", RouterTier.ELITE, line, null, energy,
                        extractItems | insertItems | insertEnergy, TabletSnapshot.NodeStatus.IDLE, furnace, 0,
                        profile ? equal : null),
                tabletNode(-5, 66, 2, "Fornalha 3", "furnace", RouterTier.ELITE, line, null, energy,
                        extractItems | insertItems | insertEnergy, TabletSnapshot.NodeStatus.ACTIVE, furnace, 8,
                        profile ? equal : null),
                tabletNode(-7, 66, 2, "Fornalha 4", "furnace", RouterTier.ELITE, line, null, energy,
                        extractItems | insertItems, TabletSnapshot.NodeStatus.FULL, furnace, 0,
                        profile ? ProfileDiff.ProfileMatch.differs(ResourceType.ITEM, RemoteSide.BOTTOM,
                                ProfileDiff.What.FILTER) : null),
                tabletNode(-9, 66, 2, "Fornalha Elite", "furnace", RouterTier.ELITE, line, null, energy,
                        extractItems | insertItems | insertEnergy, TabletSnapshot.NodeStatus.ACTIVE, furnace, 12,
                        profile ? equal : null),
                tabletNode(-70, 70, 47, "Fazenda de cana", "hopper", RouterTier.BASIC, base, null, null, extractItems,
                        TabletSnapshot.NodeStatus.UNLOADED, mode(0, ResourceType.ITEM, RemoteSide.BOTTOM, PortMode.EXTRACT),
                        0, null));
        List<TabletSnapshot.GroupView> groups = List.of(
                new TabletSnapshot.GroupView(UUID.nameUUIDFromBytes("g1".getBytes()), "Linha 5x", "Dev", true, true,
                        List.of(ore, line, fluids)),
                new TabletSnapshot.GroupView(UUID.nameUUIDFromBytes("g2".getBytes()), "Base principal", "Dev", true, false,
                        List.of(base)));
        java.util.Map<ResourceLocation, Integer> machines = new java.util.LinkedHashMap<>();
        java.util.Map<UUID, Integer> nets = new java.util.LinkedHashMap<>();
        for (TabletSnapshot.NodeView node : nodes) {
            machines.merge(node.machine(), 1, Integer::sum);
            java.util.Set<UUID> distinct = new java.util.HashSet<>();
            for (ResourceType type : ResourceType.values()) {
                distinct.addAll(node.networks(type));
            }
            distinct.forEach(id -> nets.merge(id, 1, Integer::sum));
        }
        List<TabletSnapshot.Facet<ResourceLocation>> machineFacets = new ArrayList<>();
        machines.forEach((id, count) -> machineFacets.add(new TabletSnapshot.Facet<>(id, count)));
        List<TabletSnapshot.Facet<UUID>> networkFacets = new ArrayList<>();
        nets.forEach((id, count) -> networkFacets.add(new TabletSnapshot.Facet<>(id, count)));
        TabletSnapshot.NodeView elite = nodes.get(12);
        Optional<TabletSnapshot.ProfileSummary> summary = profile
                ? Optional.of(new TabletSnapshot.ProfileSummary("Fornalha Elite", elite.machine(), elite.key(),
                        List.of(ResourceType.ITEM, ResourceType.ENERGY), 3))
                : Optional.empty();
        return new TabletSnapshot(false, ResourceLocation.withDefaultNamespace("overworld"),
                Optional.of(base), TabletSnapshot.Query.DEFAULT, nodes.size(), nodes.size(), networks,
                groups, machineFacets, networkFacets, new TabletSnapshot.QuickCounts(5, 2, 1), 63, summary, nodes,
                TabletRows.EMPTY, TabletMachineRows.EMPTY, Component.empty(), 0);
    }

    private static TabletSnapshot.NodeView tabletNode(int x, int y, int z, String name, String machine, RouterTier tier,
            @Nullable UUID items, @Nullable UUID fluids, @Nullable UUID energy, int roles, TabletSnapshot.NodeStatus status,
            long faceModes, long itemRate, ProfileDiff.@Nullable ProfileMatch match) {
        // uma rede por tipo do registro (Itens, Fluidos e Energia; os outros sem rede)
        List<List<UUID>> networks = new java.util.ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            UUID id = switch (type) {
                case ITEM -> items;
                case FLUID -> fluids;
                case ENERGY -> energy;
                default -> null;
            };
            networks.add(id == null ? List.of() : List.of(id));
        }
        List<Long> rates = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            rates.add(type == ResourceType.ITEM ? itemRate : 0L);
        }
        return new TabletSnapshot.NodeView(new NodeIndex.NodeKey(net.minecraft.world.level.Level.OVERWORLD,
                new BlockPos(x, y, z)), name, "",
                ResourceLocation.withDefaultNamespace(machine), tier, networks, roles, status,
                false, faceModes, List.copyOf(rates), Optional.ofNullable(match), 0);
    }
}
