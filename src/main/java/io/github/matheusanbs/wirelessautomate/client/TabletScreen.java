package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.RoleFilter;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TabletActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMoveNodesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletOpenNodePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletQueryPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do Tablet de rede (especificação, "Telas da interface › Tablet de rede"), com cinco abas:
 * <ul>
 *   <li><b>Lista:</b> busca, filtro por papel e Selecionar para mover vários nós de rede (por aba
 *       ou todas); cada nó mostra status e etiquetas de papel; clicar abre o nó à distância;
 *   <li><b>Mapa:</b> vista de cima centrada no jogador, pontos coloridos por status; clicar
 *       seleciona e mostra o nó, com Abrir;
 *   <li><b>Estatísticas:</b> tempo do mod por tick, vazão por tipo e, por rede, vazão, ms/tick,
 *       destinos cheios ou dormindo e nós descarregados;
 *   <li><b>Redes:</b> lista à esquerda e a rede escolhida à direita (nome, cor, privacidade, usar
 *       como ativa, remover); nova rede fica recolhida;
 *   <li><b>Grupos:</b> o mesmo para grupos: pausar e retomar o grupo todo e marcar as redes dele.
 * </ul>
 *
 * <p>Tudo vem de {@link TabletMenu#snapshot()}; a tela só pede (consulta, ações) e espera o servidor.
 * Linhas de lista, pontos do mapa e cores são desenhados e clicados por geometria, sem um widget por
 * item; os botões fixos são {@link FlatButton}s que leem o estado a cada quadro.
 */
public class TabletScreen extends AbstractContainerScreen<TabletMenu> {
    /** Cor de destaque do Tablet (a do item). */
    static final int ACCENT = 0xFF45D6CC;
    private static final int DANGER = 0xFFE5534B;
    private static final int W = 300;
    private static final int H = 240;
    private static final int X0 = 9;
    private static final int X1 = W - 9;
    private static final int HEAD_Y = 8;
    private static final int TAB_Y = 26;
    private static final int TAB_H = 15;
    private static final int SEP_Y = 45;
    private static final int BODY_Y = 50;
    private static final int ROW_H = 14;
    // lista
    private static final int LIST_Y = BODY_Y + 18;
    private static final int NODE_ROW = 22;
    private static final int NODE_ROWS = 6;
    private static final int ACTION_Y = LIST_Y + NODE_ROW * NODE_ROWS + 4;
    private static final int PAGER_Y = ACTION_Y + 16;
    // mapa
    private static final int MAP_H = 136;
    private static final int MAP_INFO_Y = BODY_Y + MAP_H + 4;
    private static final int LEGEND_Y = MAP_INFO_Y + 20;
    // estatísticas
    private static final int STATS_LIST_Y = BODY_Y + 62;
    private static final int STAT_CARD = 34;
    // redes e grupos
    private static final int SIDE_W = 120;
    private static final int RX = X0 + SIDE_W + 8;
    private static final int RW = X1 - RX;
    private static final int SIDE_BOTTOM = 200;
    private static final int NEW_Y = 203;
    private static final int NEW_BOX_Y = 217;
    private static final int SWATCH = 10;

    enum Tab { LIST, MAP, STATS, NETWORKS, GROUPS }

    /** O que o campo de nome está renomeando. */
    private enum Rename { NONE, NETWORK, GROUP }

    private static final RoleFilter[] ROLE_FILTERS = RoleFilter.values();
    /** Tipos que o Mover oferece, depois de "Todas as abas". */
    private static final ResourceType[] MOVE_TYPES = {ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY};

    private final boolean preview;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final Map<Tab, List<FlatButton>> tabWidgets = new EnumMap<>(Tab.class);
    private final Map<ResourceLocation, ItemStack> icons = new HashMap<>();

    private Tab tab = Tab.LIST;
    private int lastVersion = -1;
    private int lastNoticeId;
    private long noticeUntil;
    private Component noticeText = Component.empty();
    // lista
    private EditBox searchBox;
    private String search = "";
    private int searchTicks = -1;
    private RoleFilter role = RoleFilter.ALL;
    private int page;
    private int listScroll;
    private boolean multi;
    private final Set<NodeKey> selected = new LinkedHashSet<>();
    /** Índice em {@link #MOVE_TYPES}; -1 = todas as abas. */
    private int moveType = -1;
    private Optional<UUID> moveNetwork = Optional.empty();
    private boolean moveNetworkChosen;
    // mapa
    private @Nullable NodeKey mapSelected;
    private float mapZoom = 1f;
    // estatísticas
    private int statsScroll;
    // redes e grupos
    private @Nullable UUID selectedNetwork;
    private @Nullable UUID selectedGroup;
    private int sideScroll;
    private int groupNetScroll;
    private boolean newOpen;
    private EditBox newBox;
    private EditBox renameBox;
    private Rename renaming = Rename.NONE;
    private long removeArmedUntil;

    public TabletScreen(TabletMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /** @param preview sem servidor (captura de desenvolvimento): nada é enviado */
    public TabletScreen(TabletMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.preview = preview;
        this.imageWidth = W;
        this.imageHeight = H;
        this.search = menu.snapshot().query().search();
        this.role = menu.snapshot().query().role();
        this.page = menu.snapshot().query().page();
        this.lastNoticeId = menu.snapshot().noticeId();
    }

    // ------------------------------------------------------------------ estado

    private TabletSnapshot snapshot() {
        return menu.snapshot();
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.tablet." + key, args);
    }

    /** {@code key} com o número, ou {@code key.one} quando é 1. */
    static Component plural(String key, long count) {
        return tr(count == 1 ? key + ".one" : key, count);
    }

    /** Texto quebrado em até {@code maxLines} linhas de {@code width}; devolve a altura usada. */
    private int wrapped(GuiGraphics g, Component text, int x, int y, int width, int maxLines, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, width);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            GuiPaint.text(g, font, lines.get(i), x, y + i * 10, color);
        }
        return count * 10;
    }

    static int statusColor(NodeStatus status) {
        return switch (status) {
            case ACTIVE -> 0xFF41C96B;
            case IDLE -> 0xFF8D99A8;
            case FULL -> 0xFFFFB020;
            case UNLOADED -> 0xFFFF6B5E;
            case PAUSED -> 0xFF7986CB;
            case NO_NETWORK -> 0xFF5A6270;
        };
    }

    static Component statusName(NodeStatus status) {
        return tr("status." + status.name().toLowerCase(Locale.ROOT));
    }

    private static Component typeWord(ResourceType type) {
        return tr("type." + type.name().toLowerCase(Locale.ROOT));
    }

    private static Component typeName(ResourceType type) {
        return ResourceStyle.name(type);
    }

    private Block machineBlock(NodeView node) {
        return BuiltInRegistries.BLOCK.get(node.machine());
    }

    private ItemStack icon(NodeView node) {
        return icons.computeIfAbsent(node.machine(), id -> new ItemStack(BuiltInRegistries.BLOCK.get(id).asItem()));
    }

    /** Nome do nó; sem nome, o da máquina; sem máquina conhecida, "Roteador". */
    private Component nodeName(NodeView node) {
        if (!node.name().isEmpty()) {
            return Component.literal(node.name());
        }
        Block block = machineBlock(node);
        return block == Blocks.AIR ? Component.translatable("block.wirelessautomate.router") : RouterBlock.machineName(block);
    }

    private boolean sameDimension(NodeView node) {
        return node.key().dimension().location().equals(snapshot().dimension());
    }

    private Component coords(NodeView node) {
        BlockPos pos = node.key().pos();
        Component text = Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        if (!sameDimension(node)) {
            text = text.copy().append(" · " + node.key().dimension().location().getPath());
        }
        return text;
    }

    /** Etiquetas de papel, na cor do modo: "Extrai itens", "Insere energia", "Armazém de fluidos". */
    private List<Component> roleLabels(int roles) {
        List<Component> labels = new ArrayList<>();
        for (ResourceType type : MOVE_TYPES) {
            if ((roles & NodeIndex.role(type, NodeIndex.EXTRACT)) != 0) {
                labels.add(tr("label.extract", typeWord(type)).copy().withColor(GuiPaint.modeColor(PortMode.EXTRACT)));
            }
            if ((roles & NodeIndex.role(type, NodeIndex.INSERT)) != 0) {
                labels.add(tr("label.insert", typeWord(type)).copy().withColor(GuiPaint.modeColor(PortMode.INSERT)));
            }
            if ((roles & NodeIndex.role(type, NodeIndex.STORAGE)) != 0) {
                labels.add(tr("label.storage", typeWord(type)).copy().withColor(GuiPaint.modeColor(PortMode.BOTH)));
            }
        }
        return labels;
    }

    private Component networkName(Optional<UUID> id) {
        if (id.isEmpty()) {
            return tr("network.none");
        }
        return snapshot().network(id.get()).<Component>map(n -> Component.literal(n.name()).withColor(n.color()))
                .orElse(tr("network.hidden"));
    }

    private @Nullable NetworkView selectedNetworkView() {
        return selectedNetwork == null ? null : snapshot().network(selectedNetwork).orElse(null);
    }

    private @Nullable GroupView selectedGroupView() {
        for (GroupView group : snapshot().groups()) {
            if (group.id().equals(selectedGroup)) {
                return group;
            }
        }
        return null;
    }

    /** Redes em que o jogador pode pôr nós: as dele, as públicas e, para operador, todas. */
    private List<NetworkView> usableNetworks() {
        return snapshot().networks();
    }

    private List<NetworkView> manageableNetworks() {
        List<NetworkView> list = new ArrayList<>();
        for (NetworkView network : snapshot().networks()) {
            if (network.manageable()) {
                list.add(network);
            }
        }
        return list;
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replace(".", tr("decimal").getString());
    }

    private Component ms(long nanos) {
        return tr("ms", decimal(nanos / 1_000_000.0));
    }

    private Component rate(ResourceType type, long value) {
        return ResourceStyle.rate(type, value);
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        tabWidgets.clear();
        for (Tab t : Tab.values()) {
            tabWidgets.put(t, new ArrayList<>());
        }
        int x = leftPos;
        int y = topPos;

        int tabX = x + X0;
        for (Tab t : Tab.values()) {
            Component label = tr("tab." + t.name().toLowerCase(Locale.ROOT));
            int width = font.width(label) + 12;
            add(null, new FlatButton(tabX, y + TAB_Y, width, TAB_H, label,
                    (g, b, hovered) -> paintTab(g, b, hovered, t), () -> switchTab(t)));
            tabX += width + 3;
        }

        // Lista
        searchBox = new EditBox(font, x + X0 + 4, y + BODY_Y + 3, 120, 9, tr("search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(TabletSnapshot.MAX_SEARCH);
        searchBox.setTextColor(GuiPaint.FG);
        searchBox.setHint(tr("search").copy().withColor(GuiPaint.DISABLED));
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            if (!value.equals(search)) {
                search = value;
                searchTicks = 6;
            }
        });
        addRenderableWidget(searchBox);
        add(Tab.LIST, new FlatButton(x + X0 + 134, y + BODY_Y, 84, ROW_H, Component.empty(),
                (g, b, hovered) -> paintCycle(g, b, hovered, roleText()), () -> cycleRole(hasShiftDown() ? -1 : 1))
                .tooltip(() -> tr("role.tooltip")));
        add(Tab.LIST, new FlatButton(x + X1 - 60, y + BODY_Y, 60, ROW_H, tr("select"),
                (g, b, hovered) -> paintToggle(g, b, hovered, multi ? tr("select.done") : tr("select"), multi),
                this::toggleMulti).tooltip(() -> tr("select.tooltip")));
        // barra de mover (só selecionando)
        add(Tab.LIST, new FlatButton(x + X0 + 64, y + ACTION_Y, 86, ROW_H, Component.empty(),
                (g, b, hovered) -> paintCycle(g, b, hovered, moveTypeText()),
                () -> moveType = Math.floorMod(moveType + 1 + (hasShiftDown() ? -1 : 1), MOVE_TYPES.length + 1) - 1)
                .tooltip(() -> tr("move.type.tooltip")));
        add(Tab.LIST, new FlatButton(x + X0 + 154, y + ACTION_Y, 82, ROW_H, Component.empty(),
                (g, b, hovered) -> paintCycle(g, b, hovered, networkName(effectiveMoveNetwork())),
                () -> cycleMoveNetwork(hasShiftDown() ? -1 : 1)).tooltip(() -> tr("move.network.tooltip")));
        add(Tab.LIST, new FlatButton(x + X1 - 44, y + ACTION_Y, 44, ROW_H, tr("move"),
                (g, b, hovered) -> paintPrimary(g, b, hovered, tr("move")), this::moveSelected)
                .tooltip(() -> tr("move.tooltip", selected.size())));
        add(Tab.LIST, new FlatButton(x + X0 + 84, y + PAGER_Y, 34, ROW_H, tr("select.all"),
                (g, b, hovered) -> paintText(g, b, hovered, tr("select.all")), this::selectPage)
                .tooltip(() -> tr("select.all.tooltip")));
        add(Tab.LIST, new FlatButton(x + X1 - 120, y + PAGER_Y, 14, ROW_H, tr("page.previous"),
                (g, b, hovered) -> paintText(g, b, hovered, Component.literal("‹")), () -> changePage(-1)));
        add(Tab.LIST, new FlatButton(x + X1 - 14, y + PAGER_Y, 14, ROW_H, tr("page.next"),
                (g, b, hovered) -> paintText(g, b, hovered, Component.literal("›")), () -> changePage(1)));

        // Mapa
        add(Tab.MAP, new FlatButton(x + X1 - 46, y + MAP_INFO_Y, 46, 16, tr("open"),
                (g, b, hovered) -> paintPrimary(g, b, hovered, tr("open")), () -> {
                    if (mapSelected != null) {
                        openNode(mapSelected);
                    }
                }).tooltip(() -> tr("open.tooltip")));

        // Redes
        add(Tab.NETWORKS, new FlatButton(x + RX, y + BODY_Y + 112, RW / 2 - 1, ROW_H, tr("private"),
                (g, b, hovered) -> paintSegment(g, b, hovered, tr("private"), isSelectedPublic() == Boolean.FALSE),
                () -> setPublic(false)).tooltip(() -> tr("private.tooltip")));
        add(Tab.NETWORKS, new FlatButton(x + RX + RW / 2 + 1, y + BODY_Y + 112, RW - RW / 2 - 1, ROW_H, tr("public"),
                (g, b, hovered) -> paintSegment(g, b, hovered, tr("public"), isSelectedPublic() == Boolean.TRUE),
                () -> setPublic(true)).tooltip(() -> tr("public.tooltip")));
        add(Tab.NETWORKS, new FlatButton(x + RX, y + BODY_Y + 132, RW, ROW_H, tr("use"),
                (g, b, hovered) -> paintText(g, b, hovered, isSelectedActive() ? tr("use.active") : tr("use")),
                this::useNetwork).tooltip(() -> tr("use.tooltip")));
        add(Tab.NETWORKS, new FlatButton(x + RX, y + NEW_BOX_Y, RW, ROW_H, tr("network.remove"),
                (g, b, hovered) -> paintDanger(g, b, hovered, removeArmed() ? tr("remove.confirm") : tr("network.remove"),
                        removeArmed()), () -> removeSelected(Action.NETWORK_REMOVE, selectedNetwork))
                .tooltip(() -> tr("network.remove.tooltip")));
        add(Tab.NETWORKS, newToggle(tr("network.new")));
        add(Tab.NETWORKS, new FlatButton(x + X0 + SIDE_W - 40, y + NEW_BOX_Y, 40, ROW_H, tr("create"),
                (g, b, hovered) -> paintPrimary(g, b, hovered, tr("create")), () -> create(Action.NETWORK_CREATE)));

        // Grupos
        add(Tab.GROUPS, new FlatButton(x + RX, y + BODY_Y + 26, RW, 16, tr("group.pause"),
                (g, b, hovered) -> {
                    GroupView group = selectedGroupView();
                    paintPrimary(g, b, hovered, group != null && group.paused() ? tr("group.resume") : tr("group.pause"));
                }, this::togglePause).tooltip(() -> tr("group.pause.tooltip")));
        add(Tab.GROUPS, new FlatButton(x + RX, y + NEW_BOX_Y, RW, ROW_H, tr("group.remove"),
                (g, b, hovered) -> paintDanger(g, b, hovered, removeArmed() ? tr("remove.confirm") : tr("group.remove"),
                        removeArmed()), () -> removeSelected(Action.GROUP_REMOVE, selectedGroup))
                .tooltip(() -> tr("group.remove.tooltip")));
        add(Tab.GROUPS, newToggle(tr("group.new")));
        add(Tab.GROUPS, new FlatButton(x + X0 + SIDE_W - 40, y + NEW_BOX_Y, 40, ROW_H, tr("create"),
                (g, b, hovered) -> paintPrimary(g, b, hovered, tr("create")), () -> create(Action.GROUP_CREATE)));

        newBox = new EditBox(font, x + X0 + 4, y + NEW_BOX_Y + 3, SIDE_W - 52, 9, tr("name"));
        newBox.setBordered(false);
        newBox.setMaxLength(32);
        newBox.setTextColor(GuiPaint.FG);
        addRenderableWidget(newBox);
        renameBox = new EditBox(font, x + RX + 4, y + BODY_Y + 3, RW - 8, 9, tr("rename"));
        renameBox.setBordered(false);
        renameBox.setMaxLength(32);
        renameBox.setTextColor(GuiPaint.FG);
        addRenderableWidget(renameBox);
        refresh();
    }

    private FlatButton newToggle(Component label) {
        return new FlatButton(leftPos + X0, topPos + NEW_Y, SIDE_W, 12, label,
                (g, b, hovered) -> paintMore(g, b, hovered, label), () -> {
                    newOpen = !newOpen;
                    newBox.setValue("");
                    // o campo fica visível já, não só no próximo quadro: digitar logo depois funciona
                    refresh();
                    if (newOpen) {
                        setFocused(newBox);
                    }
                }).tooltip(() -> tr("new.tooltip"));
    }

    /** {@code owner} nulo: visível em todas as abas. */
    private FlatButton add(@Nullable Tab owner, FlatButton button) {
        buttons.add(button);
        if (owner != null) {
            tabWidgets.get(owner).add(button);
        }
        return addRenderableWidget(button);
    }

    /** Visibilidade e estado que dependem da aba e do snapshot. */
    private void refresh() {
        if (menu.version() != lastVersion) {
            lastVersion = menu.version();
            onSnapshot();
        }
        for (Map.Entry<Tab, List<FlatButton>> entry : tabWidgets.entrySet()) {
            for (FlatButton button : entry.getValue()) {
                button.visible = entry.getKey() == tab;
            }
        }
        searchBox.visible = tab == Tab.LIST;
        List<FlatButton> list = tabWidgets.get(Tab.LIST);
        // [papel, Selecionar, tipo, rede, Mover, Todos, ‹, ›]
        for (int i = 2; i <= 5; i++) {
            list.get(i).visible = tab == Tab.LIST && multi;
        }
        list.get(4).active = !selected.isEmpty();
        list.get(6).active = page > 0;
        list.get(7).active = page + 1 < snapshot().pages();

        tabWidgets.get(Tab.MAP).get(0).visible = tab == Tab.MAP && mapNode() != null;

        NetworkView network = selectedNetworkView();
        List<FlatButton> nets = tabWidgets.get(Tab.NETWORKS);
        // [Privada, Pública, Usar, Remover, Nova, Criar]
        boolean manage = network != null && network.manageable();
        nets.get(0).visible = nets.get(1).visible = tab == Tab.NETWORKS && manage;
        nets.get(2).visible = tab == Tab.NETWORKS && network != null;
        nets.get(2).active = network != null && !isSelectedActive();
        nets.get(3).visible = tab == Tab.NETWORKS && manage;
        nets.get(5).visible = tab == Tab.NETWORKS && newOpen;

        GroupView group = selectedGroupView();
        List<FlatButton> groups = tabWidgets.get(Tab.GROUPS);
        // [Pausar, Desfazer, Novo, Criar]
        groups.get(0).visible = tab == Tab.GROUPS && group != null && group.manageable();
        groups.get(0).active = group != null && !group.networks().isEmpty();
        groups.get(1).visible = tab == Tab.GROUPS && group != null && group.manageable();
        groups.get(3).visible = tab == Tab.GROUPS && newOpen;

        newBox.visible = newOpen && (tab == Tab.NETWORKS || tab == Tab.GROUPS);
        renameBox.visible = renaming != Rename.NONE && (tab == Tab.NETWORKS || tab == Tab.GROUPS);
    }

    /** Snapshot novo: a seleção que sumiu sai, a página volta ao que o servidor respondeu, e o aviso aparece. */
    private void onSnapshot() {
        TabletSnapshot s = snapshot();
        if (searchTicks < 0) {
            page = s.query().page();
        }
        if (s.noticeId() != lastNoticeId) {
            lastNoticeId = s.noticeId();
            noticeText = s.notice();
            noticeUntil = System.currentTimeMillis() + 4000;
        }
        if (selectedNetwork != null && s.network(selectedNetwork).isEmpty()) {
            selectedNetwork = null;
            renaming = Rename.NONE;
        }
        if (selectedNetwork == null && !s.networks().isEmpty()) {
            selectedNetwork = s.activeNetwork().filter(id -> s.network(id).isPresent())
                    .orElse(s.networks().get(0).id());
        }
        if (selectedGroup != null && selectedGroupView() == null) {
            selectedGroup = null;
            renaming = Rename.NONE;
        }
        if (selectedGroup == null && !s.groups().isEmpty()) {
            selectedGroup = s.groups().get(0).id();
        }
        if (!moveNetworkChosen) {
            moveNetwork = s.activeNetwork();
        }
        listScroll = Math.min(listScroll, Math.max(0, s.nodes().size() - NODE_ROWS));
    }

    // ------------------------------------------------------------------ ações

    private void send(CustomPacketPayload payload) {
        if (!preview) {
            PacketDistributor.sendToServer(payload);
        }
    }

    private void switchTab(Tab t) {
        if (tab != t) {
            tab = t;
            renaming = Rename.NONE;
            newOpen = false;
            removeArmedUntil = 0;
            setFocused(null);
        }
    }

    private void sendQuery() {
        send(new TabletQueryPayload(menu.containerId, new Query(search, role, page)));
    }

    private Component roleText() {
        return tr("role." + role.name().toLowerCase(Locale.ROOT));
    }

    private void cycleRole(int direction) {
        role = ROLE_FILTERS[Math.floorMod(role.ordinal() + direction, ROLE_FILTERS.length)];
        page = 0;
        listScroll = 0;
        sendQuery();
    }

    private void changePage(int direction) {
        int next = Mth.clamp(page + direction, 0, snapshot().pages() - 1);
        if (next != page) {
            page = next;
            listScroll = 0;
            sendQuery();
        }
    }

    private void toggleMulti() {
        multi = !multi;
        if (!multi) {
            selected.clear();
        }
    }

    /** Todos da página: marca todos os nós da página; se já estavam, desmarca. */
    private void selectPage() {
        List<NodeKey> keys = snapshot().nodes().stream().map(NodeView::key).toList();
        if (selected.containsAll(keys)) {
            keys.forEach(selected::remove);
        } else {
            selected.addAll(keys);
        }
    }

    private Component moveTypeText() {
        return moveType < 0 ? tr("move.type.all") : typeName(MOVE_TYPES[moveType]);
    }

    private Optional<UUID> effectiveMoveNetwork() {
        return moveNetwork.filter(id -> snapshot().network(id).isPresent());
    }

    private void cycleMoveNetwork(int direction) {
        List<NetworkView> networks = usableNetworks();
        // posições: 0..n-1 as redes, n = sem rede
        int current = networks.size();
        Optional<UUID> chosen = effectiveMoveNetwork();
        for (int i = 0; i < networks.size(); i++) {
            if (chosen.isPresent() && networks.get(i).id().equals(chosen.get())) {
                current = i;
            }
        }
        int next = Math.floorMod(current + direction, networks.size() + 1);
        moveNetwork = next == networks.size() ? Optional.empty() : Optional.of(networks.get(next).id());
        moveNetworkChosen = true;
    }

    private void moveSelected() {
        if (selected.isEmpty()) {
            return;
        }
        List<NodeKey> keys = new ArrayList<>(selected);
        if (keys.size() > TabletMenu.MAX_MOVE) {
            keys = keys.subList(0, TabletMenu.MAX_MOVE);
        }
        send(new TabletMoveNodesPayload(menu.containerId, List.copyOf(keys),
                moveType < 0 ? Optional.empty() : Optional.of(MOVE_TYPES[moveType]), effectiveMoveNetwork()));
        selected.clear();
        multi = false;
    }

    private void openNode(NodeKey key) {
        send(new TabletOpenNodePayload(menu.containerId, key));
    }

    private @Nullable Boolean isSelectedPublic() {
        NetworkView network = selectedNetworkView();
        return network == null ? null : network.isPublic();
    }

    private boolean isSelectedActive() {
        return selectedNetwork != null && snapshot().activeNetwork().equals(Optional.of(selectedNetwork));
    }

    private void setPublic(boolean value) {
        NetworkView network = selectedNetworkView();
        if (network != null && network.isPublic() != value) {
            send(new TabletActionPayload(menu.containerId, Action.NETWORK_PUBLIC, Optional.of(network.id()),
                    Optional.empty(), "", value ? 1 : 0));
        }
    }

    private void useNetwork() {
        if (selectedNetwork != null && !isSelectedActive()) {
            send(new TabletActionPayload(menu.containerId, Action.NETWORK_USE, selectedNetwork));
        }
    }

    private void setColor(int color) {
        if (selectedNetwork != null) {
            send(new TabletActionPayload(menu.containerId, Action.NETWORK_COLOR, Optional.of(selectedNetwork),
                    Optional.empty(), "", color));
        }
    }

    private void togglePause() {
        GroupView group = selectedGroupView();
        if (group != null) {
            send(new TabletActionPayload(menu.containerId, Action.GROUP_PAUSE, Optional.of(group.id()),
                    Optional.empty(), "", group.paused() ? 0 : 1));
        }
    }

    private void toggleGroupNetwork(UUID network) {
        GroupView group = selectedGroupView();
        if (group != null && group.manageable()) {
            Action action = group.networks().contains(network) ? Action.GROUP_REMOVE_NETWORK : Action.GROUP_ADD_NETWORK;
            send(new TabletActionPayload(menu.containerId, action, Optional.of(group.id()), Optional.of(network), "", 0));
        }
    }

    private boolean removeArmed() {
        return System.currentTimeMillis() < removeArmedUntil;
    }

    /** Remover pede dois cliques: o primeiro arma por 3 s. */
    private void removeSelected(Action action, @Nullable UUID id) {
        if (id == null) {
            return;
        }
        if (!removeArmed()) {
            removeArmedUntil = System.currentTimeMillis() + 3000;
            return;
        }
        removeArmedUntil = 0;
        send(new TabletActionPayload(menu.containerId, action, id));
    }

    private void create(Action action) {
        String name = newBox.getValue().strip();
        if (name.isEmpty()) {
            return;
        }
        send(new TabletActionPayload(menu.containerId, action, Optional.empty(), Optional.empty(), name, 0));
        newBox.setValue("");
        newOpen = false;
        setFocused(null);
    }

    private void startRename(Rename what, String current) {
        renaming = what;
        renameBox.setValue(current);
        renameBox.moveCursorToEnd(false);
        refresh();
        setFocused(renameBox);
    }

    private void finishRename(boolean commit) {
        if (renaming == Rename.NONE) {
            return;
        }
        String name = renameBox.getValue().strip();
        if (commit && !name.isEmpty()) {
            if (renaming == Rename.NETWORK && selectedNetwork != null) {
                send(new TabletActionPayload(menu.containerId, Action.NETWORK_RENAME, Optional.of(selectedNetwork),
                        Optional.empty(), name, 0));
            } else if (renaming == Rename.GROUP && selectedGroup != null) {
                send(new TabletActionPayload(menu.containerId, Action.GROUP_RENAME, Optional.of(selectedGroup),
                        Optional.empty(), name, 0));
            }
        }
        renaming = Rename.NONE;
        setFocused(null);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (searchTicks > 0 && --searchTicks == 0) {
            searchTicks = -1;
            page = 0;
            listScroll = 0;
            sendQuery();
        }
    }

    // ------------------------------------------------------------------ geometria

    /** Linha da lista de nós sob o mouse (índice no snapshot), ou -1. */
    private int nodeRowAt(double mouseX, double mouseY) {
        int x = leftPos + X0;
        int y = topPos + LIST_Y;
        if (mouseX < x || mouseX >= leftPos + X1 || mouseY < y || mouseY >= y + NODE_ROW * NODE_ROWS) {
            return -1;
        }
        int index = (int) ((mouseY - y) / NODE_ROW) + listScroll;
        return index < snapshot().nodes().size() ? index : -1;
    }

    private int mapX0() {
        return leftPos + X0;
    }

    private int mapY0() {
        return topPos + BODY_Y;
    }

    private int mapW() {
        return X1 - X0;
    }

    /** Blocos por pixel: cabe o nó mais longe da página (mínimo 16 blocos), vezes o zoom. */
    private double mapScale() {
        double far = 16;
        BlockPos center = snapshot().center();
        for (NodeView node : snapshot().nodes()) {
            if (sameDimension(node)) {
                far = Math.max(far, Math.max(Math.abs(node.key().pos().getX() - center.getX()),
                        Math.abs(node.key().pos().getZ() - center.getZ())));
            }
        }
        far = Math.min(far, 4096);
        return far / (MAP_H / 2.0 - 10) / mapZoom;
    }

    /** Ponto do nó no mapa (preso à borda se estiver fora); {@code [x, y, dentro]}. */
    private int[] mapPoint(NodeView node, double scale) {
        BlockPos center = snapshot().center();
        int cx = mapX0() + mapW() / 2;
        int cy = mapY0() + MAP_H / 2;
        double px = cx + (node.key().pos().getX() - center.getX()) / scale;
        double py = cy + (node.key().pos().getZ() - center.getZ()) / scale;
        boolean inside = px >= mapX0() + 4 && px <= mapX0() + mapW() - 4 && py >= mapY0() + 4 && py <= mapY0() + MAP_H - 4;
        px = Mth.clamp(px, mapX0() + 4, mapX0() + mapW() - 4);
        py = Mth.clamp(py, mapY0() + 4, mapY0() + MAP_H - 4);
        return new int[] {(int) Math.round(px), (int) Math.round(py), inside ? 1 : 0};
    }

    private boolean overMap(double mouseX, double mouseY) {
        return mouseX >= mapX0() && mouseX < mapX0() + mapW() && mouseY >= mapY0() && mouseY < mapY0() + MAP_H;
    }

    private @Nullable NodeView mapNodeAt(double mouseX, double mouseY) {
        if (!overMap(mouseX, mouseY)) {
            return null;
        }
        double scale = mapScale();
        NodeView best = null;
        double bestDistance = 7 * 7;
        for (NodeView node : snapshot().nodes()) {
            if (!sameDimension(node)) {
                continue;
            }
            int[] p = mapPoint(node, scale);
            double d = (p[0] - mouseX) * (p[0] - mouseX) + (p[1] - mouseY) * (p[1] - mouseY);
            if (d <= bestDistance) {
                best = node;
                bestDistance = d;
            }
        }
        return best;
    }

    private @Nullable NodeView mapNode() {
        if (mapSelected == null) {
            return null;
        }
        for (NodeView node : snapshot().nodes()) {
            if (node.key().equals(mapSelected)) {
                return node;
            }
        }
        return null;
    }

    private int sideRows() {
        return (SIDE_BOTTOM - BODY_Y) / ROW_H;
    }

    /** Linha da lista lateral (redes ou grupos) sob o mouse, ou -1. */
    private int sideRowAt(double mouseX, double mouseY, int count) {
        int x = leftPos + X0;
        int y = topPos + BODY_Y;
        if (mouseX < x || mouseX >= x + SIDE_W || mouseY < y || mouseY >= y + sideRows() * ROW_H) {
            return -1;
        }
        int index = (int) ((mouseY - y) / ROW_H) + sideScroll;
        return index < count ? index : -1;
    }

    private static final int GROUP_NET_Y = BODY_Y + 62;
    private static final int GROUP_NET_ROW = 13;
    private static final int GROUP_NET_ROWS = 6;

    /** Rede da lista de marcar do grupo sob o mouse, ou -1. */
    private int groupNetAt(double mouseX, double mouseY) {
        int x = leftPos + RX;
        int y = topPos + GROUP_NET_Y;
        if (mouseX < x || mouseX >= x + RW || mouseY < y || mouseY >= y + GROUP_NET_ROWS * GROUP_NET_ROW) {
            return -1;
        }
        int index = (int) ((mouseY - y) / GROUP_NET_ROW) + groupNetScroll;
        return index < manageableNetworks().size() ? index : -1;
    }

    private static final int SWATCH_Y = BODY_Y + 84;

    /** Cor da paleta sob o mouse, ou -1. */
    private int swatchAt(double mouseX, double mouseY) {
        int[] palette = NetworkSavedData.palette();
        for (int i = 0; i < palette.length; i++) {
            int sx = leftPos + RX + i * (SWATCH + 2);
            int sy = topPos + SWATCH_Y;
            if (mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH) {
                return i;
            }
        }
        return -1;
    }

    private boolean overTitle(double mouseX, double mouseY) {
        return mouseX >= leftPos + RX && mouseX < leftPos + X1 && mouseY >= topPos + BODY_Y
                && mouseY < topPos + BODY_Y + ROW_H;
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // um snapshot pode ter chegado depois do último quadro: botões ativos e visíveis em dia
        refresh();
        if (renaming != Rename.NONE && !renameBox.isMouseOver(mouseX, mouseY)) {
            finishRename(true);
        }
        if (removeArmed() && !overRemoveButton(mouseX, mouseY)) {
            removeArmedUntil = 0;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && clickBody(mouseX, mouseY)) {
            return true;
        }
        boolean wasOpen = newOpen;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (newOpen && !wasOpen) {
            // a tela dá o foco ao botão clicado depois do onPress, tirando-o do campo
            setFocused(newBox);
        }
        return handled;
    }

    private boolean overRemoveButton(double mouseX, double mouseY) {
        FlatButton remove = tab == Tab.NETWORKS ? tabWidgets.get(Tab.NETWORKS).get(3)
                : tab == Tab.GROUPS ? tabWidgets.get(Tab.GROUPS).get(1) : null;
        return remove != null && remove.visible && remove.isMouseOver(mouseX, mouseY);
    }

    /** Cliques nas partes desenhadas (linhas, pontos, cores). Devolve se tratou. */
    private boolean clickBody(double mouseX, double mouseY) {
        switch (tab) {
            case LIST -> {
                int row = nodeRowAt(mouseX, mouseY);
                if (row >= 0) {
                    NodeKey key = snapshot().nodes().get(row).key();
                    if (multi) {
                        if (!selected.remove(key)) {
                            selected.add(key);
                        }
                    } else {
                        openNode(key);
                    }
                    return true;
                }
            }
            case MAP -> {
                if (overMap(mouseX, mouseY)) {
                    NodeView node = mapNodeAt(mouseX, mouseY);
                    mapSelected = node == null ? null : node.key();
                    return true;
                }
            }
            case NETWORKS -> {
                List<NetworkView> networks = snapshot().networks();
                int row = sideRowAt(mouseX, mouseY, networks.size());
                if (row >= 0) {
                    selectedNetwork = networks.get(row).id();
                    renaming = Rename.NONE;
                    removeArmedUntil = 0;
                    return true;
                }
                NetworkView network = selectedNetworkView();
                if (network != null && network.manageable()) {
                    int swatch = swatchAt(mouseX, mouseY);
                    if (swatch >= 0) {
                        setColor(NetworkSavedData.palette()[swatch]);
                        return true;
                    }
                    if (renaming == Rename.NONE && overTitle(mouseX, mouseY)) {
                        startRename(Rename.NETWORK, network.name());
                        return true;
                    }
                }
            }
            case GROUPS -> {
                List<GroupView> groups = snapshot().groups();
                int row = sideRowAt(mouseX, mouseY, groups.size());
                if (row >= 0) {
                    selectedGroup = groups.get(row).id();
                    renaming = Rename.NONE;
                    removeArmedUntil = 0;
                    groupNetScroll = 0;
                    return true;
                }
                GroupView group = selectedGroupView();
                if (group != null && group.manageable()) {
                    int net = groupNetAt(mouseX, mouseY);
                    if (net >= 0) {
                        toggleGroupNetwork(manageableNetworks().get(net).id());
                        return true;
                    }
                    if (renaming == Rename.NONE && overTitle(mouseX, mouseY)) {
                        startRename(Rename.GROUP, group.name());
                        return true;
                    }
                }
            }
            default -> {
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = -(int) Math.signum(scrollY);
        switch (tab) {
            case LIST -> listScroll = Mth.clamp(listScroll + step, 0, Math.max(0, snapshot().nodes().size() - NODE_ROWS));
            case MAP -> {
                if (overMap(mouseX, mouseY)) {
                    mapZoom = Mth.clamp(mapZoom * (scrollY > 0 ? 1.25f : 0.8f), 0.25f, 16f);
                }
            }
            case STATS -> statsScroll = Mth.clamp(statsScroll + step, 0, Math.max(0, snapshot().networks().size() - 3));
            case NETWORKS, GROUPS -> {
                if (tab == Tab.GROUPS && mouseX >= leftPos + RX) {
                    groupNetScroll = Mth.clamp(groupNetScroll + step, 0,
                            Math.max(0, manageableNetworks().size() - GROUP_NET_ROWS));
                } else {
                    int count = tab == Tab.NETWORKS ? snapshot().networks().size() : snapshot().groups().size();
                    sideScroll = Mth.clamp(sideScroll + step, 0, Math.max(0, count - sideRows()));
                }
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
            if (box == renameBox && (enter || keyCode == GLFW.GLFW_KEY_ESCAPE)) {
                finishRename(enter);
            } else if (box == newBox && enter) {
                create(tab == Tab.GROUPS ? Action.GROUP_CREATE : Action.NETWORK_CREATE);
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(null);
            } else {
                box.keyPressed(keyCode, scanCode, modifiers);
            }
            // nenhuma tecla fecha a tela enquanto se digita (nem a do inventário)
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            return box.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refresh();
        super.render(g, mouseX, mouseY, partialTick);
        renderNotice(g);
        for (FlatButton button : buttons) {
            if (button.visible && button.isHovered()) {
                Component tooltip = button.currentTooltip();
                if (tooltip != null) {
                    setTooltipForNextRenderPass(tooltip);
                }
                return;
            }
        }
        Component tooltip = bodyTooltip(mouseX, mouseY);
        if (tooltip != null) {
            setTooltipForNextRenderPass(tooltip);
        }
    }

    private @Nullable Component bodyTooltip(int mouseX, int mouseY) {
        if (tab == Tab.LIST) {
            int row = nodeRowAt(mouseX, mouseY);
            if (row >= 0) {
                return nodeTooltip(snapshot().nodes().get(row), !multi);
            }
        } else if (tab == Tab.MAP) {
            NodeView node = mapNodeAt(mouseX, mouseY);
            if (node != null) {
                return nodeTooltip(node, false);
            }
        } else if (tab == Tab.NETWORKS) {
            NetworkView network = selectedNetworkView();
            if (network != null && network.manageable() && swatchAt(mouseX, mouseY) >= 0) {
                return tr("color.tooltip");
            }
            if (network != null && network.manageable() && renaming == Rename.NONE && overTitle(mouseX, mouseY)) {
                return tr("rename.tooltip");
            }
        } else if (tab == Tab.GROUPS) {
            GroupView group = selectedGroupView();
            if (group != null && group.manageable() && groupNetAt(mouseX, mouseY) >= 0) {
                return tr("group.network.tooltip");
            }
            if (group != null && group.manageable() && renaming == Rename.NONE && overTitle(mouseX, mouseY)) {
                return tr("rename.tooltip");
            }
        }
        return null;
    }

    /** Nome, onde fica, tier, máquina, rede de cada aba, papéis e status. */
    private Component nodeTooltip(NodeView node, boolean clickOpens) {
        MutableComponent text = nodeName(node).copy();
        text.append("\n").append(coords(node).copy().withColor(GuiPaint.MUTED));
        text.append("\n").append(Component.translatable(node.tier().translationKey()).copy().withColor(GuiPaint.MUTED))
                .append(Component.literal(" · ").withColor(GuiPaint.MUTED))
                .append(RouterBlock.machineName(machineBlock(node)).copy().withColor(GuiPaint.MUTED));
        for (ResourceType type : MOVE_TYPES) {
            text.append("\n").append(typeName(type).copy().withColor(GuiPaint.MUTED))
                    .append(Component.literal(": ").withColor(GuiPaint.MUTED)).append(networkName(node.network(type)));
        }
        List<Component> roles = roleLabels(node.roles());
        if (!roles.isEmpty()) {
            MutableComponent line = Component.empty();
            for (int i = 0; i < roles.size(); i++) {
                if (i > 0) {
                    line.append(Component.literal(" · ").withColor(GuiPaint.MUTED));
                }
                line.append(roles.get(i));
            }
            text.append("\n").append(line);
        }
        text.append("\n").append(statusName(node.status()).copy().withColor(statusColor(node.status())));
        if (clickOpens) {
            text.append("\n").append(tr("open.click").copy().withColor(GuiPaint.MUTED));
        }
        return text;
    }

    private void renderNotice(GuiGraphics g) {
        if (System.currentTimeMillis() > noticeUntil || noticeText.getString().isEmpty()) {
            return;
        }
        // sobre o cabeçalho: embaixo cobriria botões
        int width = Math.min(W - 20, font.width(noticeText) + 12);
        int x = leftPos + X1 - width;
        int y = topPos + HEAD_Y - 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        GuiPaint.box(g, x, y, width, 16, GuiPaint.INSET, ACCENT);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, noticeText, width - 12), x + 6, y + 4, GuiPaint.FG);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        TabletSnapshot s = snapshot();
        int x = leftPos;
        int y = topPos;
        GuiPaint.panel(g, x, y, W, H, ACCENT);
        GuiPaint.text(g, font, tr("title"), x + X0, y + HEAD_Y + 3, GuiPaint.FG);
        Component meta = plural("meta.nodes", s.totalNodes()).copy().append(" · ")
                .append(plural("meta.networks", s.networks().size()));
        GuiPaint.textRight(g, font, meta, x + X1, y + HEAD_Y + 3, GuiPaint.MUTED);
        g.fill(x + X0, y + SEP_Y, x + X1, y + SEP_Y + 1, GuiPaint.LINE);
        switch (tab) {
            case LIST -> renderList(g, mouseX, mouseY);
            case MAP -> renderMap(g, mouseX, mouseY);
            case STATS -> renderStats(g);
            case NETWORKS -> renderNetworks(g, mouseX, mouseY);
            case GROUPS -> renderGroups(g, mouseX, mouseY);
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        TabletSnapshot s = snapshot();
        int x = leftPos;
        int y = topPos;
        GuiPaint.box(g, x + X0, y + BODY_Y, 128, ROW_H, GuiPaint.INSET, searchBox.isFocused() ? ACCENT : GuiPaint.LINE);
        int hovered = nodeRowAt(mouseX, mouseY);
        List<NodeView> nodes = s.nodes();
        if (nodes.isEmpty()) {
            GuiPaint.textCentered(g, font, s.totalNodes() == 0 ? tr("list.empty") : tr("list.no_match"),
                    x + W / 2, y + LIST_Y + 40, GuiPaint.MUTED);
        }
        for (int i = 0; i < NODE_ROWS && i + listScroll < nodes.size(); i++) {
            int index = i + listScroll;
            NodeView node = nodes.get(index);
            int rx = x + X0;
            int ry = y + LIST_Y + i * NODE_ROW;
            boolean picked = selected.contains(node.key());
            if (index == hovered || picked) {
                GuiPaint.box(g, rx, ry, X1 - X0, NODE_ROW - 1, GuiPaint.BUTTON,
                        picked ? ACCENT : GuiPaint.BUTTON_HOVER_BORDER);
            } else if (i > 0) {
                g.fill(rx + 2, ry - 1, x + X1 - 2, ry, GuiPaint.LINE);
            }
            int cx = rx + 3;
            if (multi) {
                GuiPaint.checkbox(g, cx, ry + 6, picked, ACCENT);
                cx += 13;
            }
            g.renderItem(icon(node), cx, ry + 2);
            int textX = cx + 20;
            Component status = statusName(node.status());
            int statusW = font.width(status) + 9;
            GuiPaint.dot(g, x + X1 - statusW - 2, ry + 4, statusColor(node.status()));
            GuiPaint.text(g, font, status, x + X1 - statusW + 6, ry + 3, statusColor(node.status()));
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, nodeName(node), x + X1 - statusW - 8 - textX), textX, ry + 3,
                    GuiPaint.FG);
            MutableComponent line = coords(node).copy().withColor(GuiPaint.MUTED);
            for (Component label : roleLabels(node.roles())) {
                line.append(Component.literal(" · ").withColor(GuiPaint.MUTED)).append(label);
            }
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, line, x + X1 - 4 - textX), textX, ry + 12, GuiPaint.MUTED);
        }
        if (listScroll > 0) {
            GuiPaint.text(g, font, Component.literal("▲"), x + X1 - 8, y + LIST_Y - 9, GuiPaint.MUTED);
        }
        if (listScroll + NODE_ROWS < nodes.size()) {
            GuiPaint.text(g, font, Component.literal("▼"), x + X1 - 8, y + ACTION_Y - 4, GuiPaint.MUTED);
        }

        // barra de baixo: mover (selecionando) ou a dica; depois a seleção e as páginas
        if (multi) {
            GuiPaint.text(g, font, tr("move.to"), x + X0, y + ACTION_Y + 3, GuiPaint.MUTED);
            GuiPaint.text(g, font, plural("selected", selected.size()), x + X0, y + PAGER_Y + 3, GuiPaint.FG);
        } else {
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, tr("list.hint"), X1 - X0), x + X0, y + ACTION_Y + 3,
                    GuiPaint.DISABLED);
        }
        int from = s.matchingNodes() == 0 ? 0 : s.query().page() * TabletSnapshot.PAGE_SIZE + 1;
        int to = Math.min(s.matchingNodes(), (s.query().page() + 1) * TabletSnapshot.PAGE_SIZE);
        GuiPaint.textCentered(g, font, tr("page", from, to, s.matchingNodes()), x + X1 - 60, y + PAGER_Y + 3,
                GuiPaint.MUTED);
    }

    private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
        TabletSnapshot s = snapshot();
        int x0 = mapX0();
        int y0 = mapY0();
        int w = mapW();
        g.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + MAP_H - 1, GuiPaint.INSET);
        GuiPaint.outline(g, x0, y0, w, MAP_H, GuiPaint.LINE);
        double scale = mapScale();
        // grade: o passo é uma potência de 2 em blocos que dê pelo menos 20 px
        int step = 1;
        while (step / scale < 20) {
            step *= 2;
        }
        BlockPos center = s.center();
        int cx = x0 + w / 2;
        int cy = y0 + MAP_H / 2;
        int gridColor = 0xFF1A2129;
        int startX = Math.floorDiv(center.getX() - (int) (w / 2 * scale), step) * step;
        for (int bx = startX; (bx - center.getX()) / scale < w / 2.0; bx += step) {
            int px = (int) Math.round(cx + (bx - center.getX()) / scale);
            if (px > x0 && px < x0 + w - 1) {
                g.fill(px, y0 + 1, px + 1, y0 + MAP_H - 1, gridColor);
            }
        }
        int startZ = Math.floorDiv(center.getZ() - (int) (MAP_H / 2 * scale), step) * step;
        for (int bz = startZ; (bz - center.getZ()) / scale < MAP_H / 2.0; bz += step) {
            int py = (int) Math.round(cy + (bz - center.getZ()) / scale);
            if (py > y0 && py < y0 + MAP_H - 1) {
                g.fill(x0 + 1, py, x0 + w - 1, py + 1, gridColor);
            }
        }
        // o jogador, no centro
        g.fill(cx - 3, cy, cx + 4, cy + 1, GuiPaint.FG);
        g.fill(cx, cy - 3, cx + 1, cy + 4, GuiPaint.FG);

        NodeView hovered = mapNodeAt(mouseX, mouseY);
        int otherDimension = 0;
        for (NodeView node : s.nodes()) {
            if (!sameDimension(node)) {
                otherDimension++;
                continue;
            }
            int[] p = mapPoint(node, scale);
            int color = statusColor(node.status());
            boolean chosen = node.key().equals(mapSelected);
            if (chosen || node == hovered) {
                GuiPaint.outline(g, p[0] - 4, p[1] - 4, 9, 9, chosen ? GuiPaint.FG : GuiPaint.MUTED);
            }
            if (p[2] == 1) {
                g.fill(p[0] - 3, p[1] - 2, p[0] + 4, p[1] + 3, GuiPaint.BEVEL_DARK);
                g.fill(p[0] - 2, p[1] - 3, p[0] + 3, p[1] + 4, GuiPaint.BEVEL_DARK);
                GuiPaint.dot(g, p[0] - 2, p[1] - 2, color);
            } else {
                // fora do mapa: presa na borda, vazada
                GuiPaint.outline(g, p[0] - 2, p[1] - 2, 5, 5, color);
            }
        }
        GuiPaint.textRight(g, font, tr("map.north"), x0 + w - 4, y0 + 4, GuiPaint.DISABLED);
        GuiPaint.text(g, font, tr("map.scale", step), x0 + 4, y0 + MAP_H - 11, GuiPaint.DISABLED);

        // o nó escolhido ou a dica
        int infoY = topPos + MAP_INFO_Y;
        NodeView chosen = mapNode();
        if (chosen != null) {
            GuiPaint.box(g, x0, infoY, w - 50, 16, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
            Component status = statusName(chosen.status());
            int statusW = font.width(status);
            GuiPaint.text(g, font, status, x0 + w - 56 - statusW, infoY + 4, statusColor(chosen.status()));
            MutableComponent text = nodeName(chosen).copy().append(Component.literal("  ")
                    .append(coords(chosen)).withColor(GuiPaint.MUTED));
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, text, w - 66 - statusW), x0 + 5, infoY + 4, GuiPaint.FG);
        } else {
            Component hint = otherDimension > 0 ? tr("map.hint.other", otherDimension) : tr("map.hint");
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, hint, w), x0, infoY + 4, GuiPaint.DISABLED);
        }

        // legenda
        int lx = x0;
        int ly = topPos + LEGEND_Y;
        for (NodeStatus status : NodeStatus.values()) {
            Component name = tr("legend." + status.name().toLowerCase(Locale.ROOT));
            int width = 8 + font.width(name) + 8;
            if (lx + width > x0 + w) {
                lx = x0;
                ly += 10;
            }
            GuiPaint.dot(g, lx, ly + 1, statusColor(status));
            GuiPaint.text(g, font, name, lx + 8, ly, GuiPaint.MUTED);
            lx += width;
        }
    }

    private void renderStats(GuiGraphics g) {
        TabletSnapshot s = snapshot();
        int x = leftPos;
        int y = topPos;
        // tempo do mod por tick, com a barra do orçamento
        GuiPaint.text(g, font, tr("stats.time"), x + X0, y + BODY_Y, GuiPaint.FG);
        GuiPaint.textRight(g, font, tr("stats.time.value", decimal(s.modNanos() / 1e6), decimal(s.budgetNanos() / 1e6)),
                x + X1, y + BODY_Y, GuiPaint.MUTED);
        float used = s.budgetNanos() <= 0 ? 0 : Math.min(1f, s.modNanos() / (float) s.budgetNanos());
        int barColor = used < 0.6f ? 0xFF41C96B : used < 0.9f ? 0xFFFFB020 : 0xFFFF6B5E;
        g.fill(x + X0, y + BODY_Y + 11, x + X1, y + BODY_Y + 15, GuiPaint.INSET);
        g.fill(x + X0, y + BODY_Y + 11, x + X0 + Math.max(1, Math.round((X1 - X0) * used)), y + BODY_Y + 15, barColor);

        // vazão por tipo, somando as redes visíveis
        long items = 0;
        long fluids = 0;
        long energy = 0;
        long chemicals = 0;
        int full = 0;
        int unloaded = 0;
        int paused = 0;
        for (NetworkView n : s.networks()) {
            items += n.itemRate();
            fluids += n.fluidRate();
            energy += n.energyRate();
            chemicals += n.chemicalRate();
            full += n.full();
            unloaded += n.unloaded();
            paused += n.paused() ? 1 : 0;
        }
        // químicos só com o Mekanism: a quarta coluna aparece só com ele
        int colW = (X1 - X0) / (Chemicals.LOADED ? 4 : 3);
        statColumn(g, x + X0, y + BODY_Y + 20, colW, typeName(ResourceType.ITEM), rate(ResourceType.ITEM, items),
                ResourceStyle.color(ResourceType.ITEM));
        statColumn(g, x + X0 + colW, y + BODY_Y + 20, colW, typeName(ResourceType.FLUID), rate(ResourceType.FLUID, fluids),
                ResourceStyle.color(ResourceType.FLUID));
        statColumn(g, x + X0 + 2 * colW, y + BODY_Y + 20, colW, typeName(ResourceType.ENERGY),
                rate(ResourceType.ENERGY, energy), ResourceStyle.color(ResourceType.ENERGY));
        if (Chemicals.LOADED) {
            statColumn(g, x + X0 + 3 * colW, y + BODY_Y + 20, colW, typeName(ResourceType.CHEMICAL),
                    rate(ResourceType.CHEMICAL, chemicals), ResourceStyle.color(ResourceType.CHEMICAL));
        }
        MutableComponent warnings = Component.empty();
        appendWarning(warnings, full, "stats.full", statusColor(NodeStatus.FULL));
        appendWarning(warnings, unloaded, "stats.unloaded", statusColor(NodeStatus.UNLOADED));
        appendWarning(warnings, paused, "stats.paused", statusColor(NodeStatus.PAUSED));
        wrapped(g, warnings.getString().isEmpty() ? tr("stats.ok") : warnings, x + X0, y + BODY_Y + 40, X1 - X0, 2,
                warnings.getString().isEmpty() ? statusColor(NodeStatus.ACTIVE) : GuiPaint.FG);
        g.fill(x + X0, y + STATS_LIST_Y - 3, x + X1, y + STATS_LIST_Y - 2, GuiPaint.LINE);

        List<NetworkView> networks = s.networks();
        if (networks.isEmpty()) {
            GuiPaint.textCentered(g, font, tr("networks.empty"), x + W / 2, y + STATS_LIST_Y + 30, GuiPaint.MUTED);
        }
        int rows = (H - 8 - STATS_LIST_Y) / STAT_CARD;
        for (int i = 0; i < rows && i + statsScroll < networks.size(); i++) {
            NetworkView n = networks.get(i + statsScroll);
            int cy = y + STATS_LIST_Y + i * STAT_CARD;
            GuiPaint.box(g, x + X0, cy, X1 - X0, STAT_CARD - 3, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
            GuiPaint.dot(g, x + X0 + 5, cy + 5, 0xFF000000 | n.color());
            Component right = tr("stats.network", plural("meta.nodes", n.nodes()), ms(n.averageNanos()), n.opsPerSecond());
            GuiPaint.textRight(g, font, right, x + X1 - 5, cy + 4, GuiPaint.MUTED);
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, Component.literal(n.name()), X1 - X0 - 20 - font.width(right)),
                    x + X0 + 14, cy + 4, GuiPaint.FG);
            MutableComponent rates = rate(ResourceType.ITEM, n.itemRate()).copy()
                    .append(" · ").append(rate(ResourceType.FLUID, n.fluidRate()))
                    .append(" · ").append(rate(ResourceType.ENERGY, n.energyRate()));
            if (Chemicals.LOADED) {
                rates.append(" · ").append(rate(ResourceType.CHEMICAL, n.chemicalRate()));
            }
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, rates, X1 - X0 - 10), x + X0 + 5, cy + 14, GuiPaint.MUTED);
            MutableComponent line = Component.empty();
            if (n.paused()) {
                appendPart(line, tr("status.paused").copy().withColor(statusColor(NodeStatus.PAUSED)));
            }
            if (n.full() > 0) {
                appendPart(line, plural("stats.full", n.full()).copy().withColor(statusColor(NodeStatus.FULL)));
            }
            if (n.sleeping() > 0) {
                appendPart(line, tr("stats.sleeping", n.sleeping()).copy().withColor(GuiPaint.MUTED));
            }
            if (n.unloaded() > 0) {
                appendPart(line, plural("stats.unloaded", n.unloaded()).copy()
                        .withColor(statusColor(NodeStatus.UNLOADED)));
            }
            if (line.getSiblings().isEmpty()) {
                line.append(tr("stats.network.ok").copy().withColor(GuiPaint.DISABLED));
            }
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, line, X1 - X0 - 10), x + X0 + 5, cy + 23, GuiPaint.FG);
        }
        if (statsScroll + rows < networks.size()) {
            GuiPaint.text(g, font, Component.literal("▼"), x + X1 - 8, y + H - 12, GuiPaint.MUTED);
        }
    }

    private void statColumn(GuiGraphics g, int x, int y, int width, Component label, Component value, int color) {
        GuiPaint.dot(g, x, y + 1, color);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, label, width - 10), x + 8, y, GuiPaint.MUTED);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, value, width - 10), x + 8, y + 10, GuiPaint.FG);
    }

    private void appendWarning(MutableComponent line, int count, String key, int color) {
        if (count > 0) {
            appendPart(line, plural(key, count).copy().withColor(color));
        }
    }

    private static void appendPart(MutableComponent line, Component part) {
        if (!line.getSiblings().isEmpty()) {
            line.append(Component.literal(" · ").withColor(GuiPaint.MUTED));
        }
        line.append(part);
    }

    private void renderSideList(GuiGraphics g, int mouseX, int mouseY, int count, java.util.function.IntFunction<Row> rows,
            Component empty) {
        int x = leftPos + X0;
        int y = topPos + BODY_Y;
        g.fill(x, y, x + SIDE_W, topPos + SIDE_BOTTOM, GuiPaint.INSET);
        GuiPaint.outline(g, x, y, SIDE_W, SIDE_BOTTOM - BODY_Y, GuiPaint.LINE);
        if (count == 0) {
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, empty, SIDE_W - 8), x + 4, y + 5, GuiPaint.MUTED);
        }
        int hovered = sideRowAt(mouseX, mouseY, count);
        for (int i = 0; i < sideRows() && i + sideScroll < count; i++) {
            int index = i + sideScroll;
            Row row = rows.apply(index);
            int ry = y + i * ROW_H;
            if (row.selected) {
                g.fill(x + 1, ry + 1, x + SIDE_W - 1, ry + ROW_H, GuiPaint.BUTTON);
                g.fill(x + 1, ry + 1, x + 2, ry + ROW_H, ACCENT);
            } else if (index == hovered) {
                g.fill(x + 1, ry + 1, x + SIDE_W - 1, ry + ROW_H, GuiPaint.BUTTON);
            }
            GuiPaint.dot(g, x + 5, ry + 5, row.color);
            int right = x + SIDE_W - 4;
            if (row.mark != null) {
                GuiPaint.textRight(g, font, row.mark, right, ry + 4, GuiPaint.MUTED);
                right -= font.width(row.mark) + 3;
            }
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, row.name, right - (x + 14)), x + 14, ry + 4, GuiPaint.FG);
        }
        if (sideScroll + sideRows() < count) {
            GuiPaint.text(g, font, Component.literal("▼"), x + SIDE_W - 9, topPos + SIDE_BOTTOM - 10, GuiPaint.MUTED);
        }
        // nova rede ou grupo, recolhido
        if (newOpen) {
            GuiPaint.box(g, x, topPos + NEW_BOX_Y, SIDE_W - 44, ROW_H, GuiPaint.INSET,
                    newBox.isFocused() ? ACCENT : GuiPaint.LINE);
        }
    }

    private record Row(Component name, int color, boolean selected, @Nullable Component mark) {
    }

    private void renderNetworks(GuiGraphics g, int mouseX, int mouseY) {
        TabletSnapshot s = snapshot();
        List<NetworkView> networks = s.networks();
        renderSideList(g, mouseX, mouseY, networks.size(), i -> {
            NetworkView n = networks.get(i);
            boolean active = s.activeNetwork().equals(Optional.of(n.id()));
            Component mark = active ? tr("mark.active") : n.isPublic() ? tr("mark.public") : null;
            return new Row(Component.literal(n.name()), 0xFF000000 | n.color(), n.id().equals(selectedNetwork), mark);
        }, tr("networks.empty"));

        int x = leftPos + RX;
        int y = topPos;
        NetworkView n = selectedNetworkView();
        if (n == null) {
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, tr("network.pick"), RW), x, y + BODY_Y + 3, GuiPaint.MUTED);
            return;
        }
        titleRow(g, mouseX, mouseY, Component.literal(n.name()), 0xFF000000 | n.color(), n.manageable());
        Component owner = n.owned() ? tr("owner.you") : tr("owner", n.owner());
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, owner, RW), x, y + BODY_Y + 18, GuiPaint.MUTED);
        MutableComponent nodes = plural("meta.nodes", n.nodes()).copy();
        if (n.unloaded() > 0) {
            nodes.append(", ").append(plural("network.unloaded", n.unloaded()));
        }
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, nodes, RW), x, y + BODY_Y + 29, GuiPaint.MUTED);
        Component members = n.isPublic() ? tr("members.public") : tr("members.private", n.owner());
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, members, RW), x, y + BODY_Y + 40, GuiPaint.MUTED);
        if (n.manageable()) {
            GuiPaint.text(g, font, tr("color"), x, y + SWATCH_Y - 11, GuiPaint.FG);
            int[] palette = NetworkSavedData.palette();
            for (int i = 0; i < palette.length; i++) {
                int sx = x + i * (SWATCH + 2);
                int sy = y + SWATCH_Y;
                boolean current = (palette[i] & 0xFFFFFF) == n.color();
                GuiPaint.box(g, sx, sy, SWATCH, SWATCH, 0xFF000000 | palette[i], current ? GuiPaint.FG : GuiPaint.BEVEL_DARK);
            }
            GuiPaint.text(g, font, tr("privacy"), x, y + BODY_Y + 101, GuiPaint.FG);
        } else {
            wrapped(g, tr("network.foreign"), x, y + SWATCH_Y, RW, 3, GuiPaint.DISABLED);
        }
    }

    /** Nome da rede ou do grupo no alto da coluna da direita; clicável para renomear quando pode. */
    private void titleRow(GuiGraphics g, int mouseX, int mouseY, Component name, int color, boolean canRename) {
        int x = leftPos + RX;
        int y = topPos + BODY_Y;
        if (renaming != Rename.NONE) {
            GuiPaint.box(g, x, y, RW, ROW_H, GuiPaint.INSET, ACCENT);
            return;
        }
        boolean hovered = canRename && overTitle(mouseX, mouseY);
        GuiPaint.dot(g, x, y + 4, color);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, name, RW - 10), x + 9, y + 3, hovered ? ACCENT : GuiPaint.FG);
        if (hovered) {
            int width = Math.min(font.width(name), RW - 10);
            for (int i = 0; i < width; i += 2) {
                g.fill(x + 9 + i, y + 12, x + 10 + i, y + 13, ACCENT);
            }
        }
    }

    private void renderGroups(GuiGraphics g, int mouseX, int mouseY) {
        TabletSnapshot s = snapshot();
        List<GroupView> groups = s.groups();
        renderSideList(g, mouseX, mouseY, groups.size(), i -> {
            GroupView group = groups.get(i);
            int color = group.paused() ? statusColor(NodeStatus.PAUSED) : statusColor(NodeStatus.ACTIVE);
            return new Row(Component.literal(group.name()), color, group.id().equals(selectedGroup),
                    Component.literal(Integer.toString(group.networks().size())));
        }, tr("groups.empty"));

        int x = leftPos + RX;
        int y = topPos;
        GroupView group = selectedGroupView();
        if (group == null) {
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, tr("group.pick"), RW), x, y + BODY_Y + 3, GuiPaint.MUTED);
            return;
        }
        int color = group.paused() ? statusColor(NodeStatus.PAUSED) : statusColor(NodeStatus.ACTIVE);
        titleRow(g, mouseX, mouseY, Component.literal(group.name()), color, group.manageable());
        int nodes = 0;
        for (UUID id : group.networks()) {
            Optional<NetworkView> n = s.network(id);
            if (n.isPresent()) {
                nodes += n.get().nodes();
            }
        }
        Component summary = (group.paused() ? tr("status.paused") : tr("group.running")).copy().withColor(color)
                .append(Component.literal(" · ").withColor(GuiPaint.MUTED))
                .append(plural("meta.networks", group.networks().size()).copy().withColor(GuiPaint.MUTED))
                .append(Component.literal(" · ").withColor(GuiPaint.MUTED))
                .append(plural("meta.nodes", nodes).copy().withColor(GuiPaint.MUTED));
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, summary, RW), x, y + BODY_Y + 15, GuiPaint.FG);

        GuiPaint.text(g, font, tr("group.networks"), x, y + GROUP_NET_Y - 11, GuiPaint.FG);
        List<NetworkView> networks = manageableNetworks();
        int hovered = group.manageable() ? groupNetAt(mouseX, mouseY) : -1;
        if (networks.isEmpty()) {
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, tr("networks.empty"), RW), x, y + GROUP_NET_Y + 2,
                    GuiPaint.MUTED);
        }
        for (int i = 0; i < GROUP_NET_ROWS && i + groupNetScroll < networks.size(); i++) {
            int index = i + groupNetScroll;
            NetworkView n = networks.get(index);
            int ry = y + GROUP_NET_Y + i * GROUP_NET_ROW;
            if (index == hovered) {
                g.fill(x, ry, x + RW, ry + GROUP_NET_ROW, GuiPaint.BUTTON);
            }
            GuiPaint.checkbox(g, x + 2, ry + 2, group.networks().contains(n.id()), ACCENT);
            GuiPaint.dot(g, x + 15, ry + 4, 0xFF000000 | n.color());
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, Component.literal(n.name()), RW - 26), x + 24, ry + 3,
                    group.manageable() ? GuiPaint.FG : GuiPaint.MUTED);
        }
        if (groupNetScroll + GROUP_NET_ROWS < networks.size()) {
            GuiPaint.text(g, font, Component.literal("▼"), x + RW - 8, y + GROUP_NET_Y + GROUP_NET_ROWS * GROUP_NET_ROW - 8,
                    GuiPaint.MUTED);
        }
        wrapped(g, tr("group.hint"), x, y + NEW_Y - 10, RW, 2, GuiPaint.DISABLED);
    }

    // ------------------------------------------------------------------ pintura dos botões

    private void paintTab(GuiGraphics g, FlatButton b, boolean hovered, Tab t) {
        boolean chosen = t == tab;
        if (chosen) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), ACCENT, ACCENT);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        GuiPaint.textCentered(g, font, b.getMessage(), b.getX() + b.getWidth() / 2 + 1, b.getY() + 4,
                chosen ? GuiPaint.DARK_TEXT : GuiPaint.FG);
    }

    private void paintText(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        int border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                b.active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** Botão que troca entre opções: o valor atual e a seta. */
    private void paintCycle(GuiGraphics g, FlatButton b, boolean hovered, Component value) {
        int border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON, border);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, value, b.getWidth() - 14), b.getX() + 4, b.getY() + 3, GuiPaint.FG);
        GuiPaint.arrowDown(g, b.getX() + b.getWidth() - 9, b.getY() + 6, hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    private void paintToggle(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean on) {
        int fill = on ? GuiPaint.mix(GuiPaint.BUTTON, ACCENT, 0.3f) : GuiPaint.BUTTON;
        int border = on ? ACCENT : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + 3, GuiPaint.FG);
    }

    private void paintSegment(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean on) {
        paintToggle(g, b, hovered, text, on);
    }

    /** Botão principal, na cor do Tablet. */
    private void paintPrimary(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        if (!b.active) {
            paintText(g, b, hovered, text);
            return;
        }
        int fill = hovered ? GuiPaint.mix(ACCENT, 0xFFFFFFFF, 0.2f) : ACCENT;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, fill);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                GuiPaint.DARK_TEXT);
    }

    private void paintDanger(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean armed) {
        int fill = armed ? GuiPaint.mix(GuiPaint.BUTTON, DANGER, 0.3f) : GuiPaint.BUTTON;
        int border = armed || hovered ? DANGER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + 3,
                armed || hovered ? GuiPaint.mix(DANGER, 0xFFFFFFFF, 0.45f) : GuiPaint.FG);
    }

    private void paintMore(GuiGraphics g, FlatButton b, boolean hovered, Component label) {
        int color = hovered || newOpen ? GuiPaint.FG : GuiPaint.MUTED;
        if (newOpen) {
            GuiPaint.arrowDown(g, b.getX() + 1, b.getY() + 3, color);
        } else {
            GuiPaint.arrowRight(g, b.getX() + 2, b.getY() + 2, color);
        }
        GuiPaint.text(g, font, label, b.getX() + 10, b.getY() + 1, color);
    }

    // ------------------------------------------------------------------ captura e teste de ponta a ponta

    void previewTab(Tab t) {
        switchTab(t);
    }

    void previewMulti(boolean on, int count) {
        multi = on;
        selected.clear();
        if (on) {
            snapshot().nodes().stream().limit(count).forEach(node -> selected.add(node.key()));
        }
    }

    void previewMapSelect(int index) {
        mapSelected = snapshot().nodes().get(index).key();
    }

    void previewNetwork(int index) {
        selectedNetwork = snapshot().networks().get(index).id();
    }

    void previewNewOpen(String text) {
        newOpen = true;
        newBox.setValue(text);
        setFocused(newBox);
    }

    Tab tab() {
        return tab;
    }

    /** Centro da linha do nó na lista (página atual, sem rolagem), ou {@code null} se ele não aparece. */
    int @Nullable [] nodeRowCenter(NodeKey key) {
        List<NodeView> nodes = snapshot().nodes();
        for (int i = listScroll; i < Math.min(nodes.size(), listScroll + NODE_ROWS); i++) {
            if (nodes.get(i).key().equals(key)) {
                return new int[] {leftPos + W / 2, topPos + LIST_Y + (i - listScroll) * NODE_ROW + NODE_ROW / 2};
            }
        }
        return null;
    }

    /** Centro da linha do grupo na lista lateral, ou {@code null}. */
    int @Nullable [] groupRowCenter(UUID id) {
        List<GroupView> groups = snapshot().groups();
        for (int i = sideScroll; i < Math.min(groups.size(), sideScroll + sideRows()); i++) {
            if (groups.get(i).id().equals(id)) {
                return new int[] {leftPos + X0 + SIDE_W / 2, topPos + BODY_Y + (i - sideScroll) * ROW_H + ROW_H / 2};
            }
        }
        return null;
    }

    /** Centro da caixa de marcar da rede na coluna do grupo, ou {@code null}. */
    int @Nullable [] groupNetworkCenter(UUID id) {
        List<NetworkView> networks = manageableNetworks();
        for (int i = groupNetScroll; i < Math.min(networks.size(), groupNetScroll + GROUP_NET_ROWS); i++) {
            if (networks.get(i).id().equals(id)) {
                return new int[] {leftPos + RX + 6, topPos + GROUP_NET_Y + (i - groupNetScroll) * GROUP_NET_ROW + 6};
            }
        }
        return null;
    }

    EditBox newBox() {
        return newBox;
    }

    EditBox searchBox() {
        return searchBox;
    }
}
