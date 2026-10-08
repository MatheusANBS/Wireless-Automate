package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.packet.OpenFilterPayload;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do roteador (especificação, "Telas da interface › Roteador"): cabeçalho com nome, vazão da
 * aba e tier; abas por tipo, cada uma com um ponto na cor da sua rede, e à direita delas o seletor
 * da rede da aba selecionada (cada tipo do roteador entra numa rede própria); o visor 3D da
 * máquina com o roteador ({@link MachineView3D}) e os botões das 6 faces logo abaixo; a face selecionada com modo, filtro e os slots de Cartão de
 * Filtro; prioridade e redstone recolhidos em "Mais", embaixo das faces; e o inventário do jogador,
 * embaixo da face selecionada, para pôr e tirar cartões. No cabeçalho, à direita do tier, fica o
 * slot do Upgrade de chunk loading (é do roteador, não da face), com uma luz do estado (ativo, no
 * limite, desligado) e a dica.
 *
 * <p>Os slots de cartão mostram os da face e do tipo selecionados: ao mudar de aba ou de face a
 * tela avisa o servidor ({@link SelectFacePayload}), que troca o que os slots mostram, e a
 * sincronização vanilla traz os cartões.
 *
 * <p>Tudo vem de {@link RouterMenu#snapshot()}. Os botões leem o estado a cada quadro e
 * {@link #refresh()} reposiciona o que depende de texto, então um snapshot novo
 * ({@link RouterMenu#version()} mudou) aparece sem recriar widgets e sem perder aba, face ou o que
 * está aberto.
 */
public class RouterScreen extends AbstractContainerScreen<RouterMenu> {

    // O mínimo é largo e baixo o bastante para caber na menor escala automática (320×240); a tela
    // cresce pela borda direita, pela de baixo e pelo canto, e o tamanho fica lembrado na sessão.
    private static final int MIN_W = 300;
    private static final int MIN_H = 240;
    /** Alça do canto (lado, em px) e espessura das bordas que redimensionam. */
    private static final int GRIP = 7;
    private static final int EDGE = 3;
    private static int savedW = MIN_W;
    private static int savedH = MIN_H;
    private static final int X0 = 9;
    private static final int HEAD_Y = 8;
    private static final int PILL_H = 13;
    private static final int TAB_Y = 26;
    private static final int TAB_H = 15;
    /** Espaço do ponto da rede na aba (ponto de 5 px e folga). */
    private static final int TAB_DOT = 8;
    /** Pílula da rede sem o nome: borda, ponto, folgas e a seta. */
    private static final int NET_PILL_PAD = 6 + 5 + 4 + 4 + 5 + 5;
    /** Mínimo do seletor de rede à direita das abas (bolinha, um pedaço do nome e a seta). */
    private static final int NET_MIN = 92;
    private static final int SEP_Y = 45;
    private static final int BODY_Y = 50;
    // visor 3D (altura no tamanho mínimo; cresce com a tela) e, logo abaixo, os botões das faces em
    // duas fileiras (cada coluna é um eixo)
    private static final int VIEW_H = 84;
    private static final int FACE_H = 18;
    private static final int FACE_GAP = 2;
    // coluna da face selecionada: largura fixa, presa à borda direita
    private static final int RW = 162;
    private static final int MODE_Y = 74;
    private static final int MODE_W = (RW - 2) / 2;
    private static final int MODE_H = 18;
    private static final int FILTER_Y = 114;
    /** Slots de cartão (o fundo; o item fica 1 px para dentro, em {@link RouterMenu#CARD_Y}). */
    private static final int CARD_BG_Y = RouterMenu.CARD_Y - 1;
    /** Slot do upgrade (o fundo), no canto direito do cabeçalho; o tier e a vazão ficam à esquerda dele. */
    private static final int UPGRADE_BG_Y = RouterMenu.UPGRADE_Y - 1;
    // embaixo das faces, na coluna do visor: "Mais" com prioridade e redstone (alturas no tamanho mínimo)
    private static final int ADV_SEP_Y = 180;
    private static final int MORE_Y = 184;
    private static final int PRIO_Y = 199;
    private static final int REDSTONE_Y = 217;
    private static final int ROW_H = 14;
    private static final int DROPDOWN_ROW = 12;
    private static final int DROPDOWN_MAX_ROWS = 8;

    private static final PortMode[] MODES = {PortMode.EXTRACT, PortMode.INSERT, PortMode.BOTH, PortMode.NONE};
    /** Ordem dos botões das faces: em cima Cima, Norte e Leste; embaixo as opostas. */
    private static final Direction[] FACE_ORDER = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.DOWN,
            Direction.SOUTH, Direction.WEST};
    /** Cartão desenhado apagado num slot de cartão vazio. */
    private static final ItemStack GHOST_CARD = new ItemStack(ModItems.FILTER_CARD.get());
    /** Upgrade desenhado apagado no slot de upgrade vazio. */
    private static final ItemStack GHOST_UPGRADE = new ItemStack(ModItems.CHUNK_LOADER_UPGRADE.get());

    private final boolean preview;
    private final List<ResourceType> types = new ArrayList<>();
    private final List<FlatButton> buttons = new ArrayList<>();
    private final Map<ResourceType, FlatButton> tabButtons = new EnumMap<>(ResourceType.class);
    private final MachineView3D machineView = new MachineView3D(direction -> {
        face = direction;
        refresh();
    });
    private final Map<Direction, FlatButton> faceButtons = new EnumMap<>(Direction.class);
    private final Function<Direction, FaceView> faceViews = this::view;

    private ResourceType type = ResourceType.ITEM;
    private Direction face;
    private boolean expanded;
    private boolean networkListOpen;
    private int networkScroll;
    /** Largura disponível para o nome da rede na pílula (o que sobra à direita das abas). */
    private int netTextMax = NET_MIN - NET_PILL_PAD;
    private TabLayout.Mode tabMode = TabLayout.Mode.FULL;
    private boolean renaming;
    private String renameDraft = "";

    private FlatButton titleButton;
    private FlatButton networkButton;
    private FlatButton moreButton;
    private FlatButton prioMinus;
    private FlatButton prioPlus;
    private FlatButton redstoneButton;
    private FlatButton editFilterButton;
    private final List<FlatButton> modeButtons = new ArrayList<>();
    private EditBox renameBox;

    private int w = MIN_W;
    private int h = MIN_H;
    private @Nullable Resize resizing;
    private double resizeCenterX;
    private double resizeCenterY;
    private double resizeGrabX;
    private double resizeGrabY;

    private enum Resize { WIDTH, HEIGHT, BOTH }

    public RouterScreen(RouterMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /**
     * @param preview sem servidor (captura de desenvolvimento): as mudanças valem só no snapshot local
     */
    public RouterScreen(RouterMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.preview = preview;
        this.imageWidth = MIN_W;
        this.imageHeight = MIN_H;
        this.face = menu.snapshot().facing();
        types.addAll(LoadedTypes.LIST);
    }

    // ------------------------------------------------------------------ estado

    private RouterSnapshot snapshot() {
        return menu.snapshot();
    }

    private FaceView view() {
        return view(face);
    }

    private FaceView view(Direction direction) {
        return snapshot().face(type, direction);
    }

    private int tierColor() {
        return GuiPaint.tierColor(snapshot().tier());
    }

    private Component displayName() {
        RouterSnapshot s = snapshot();
        if (!s.name().isEmpty()) {
            return Component.literal(s.name());
        }
        return s.machine().isEmpty() ? Component.translatable("block.wirelessautomate.router") : s.machine().getHoverName();
    }

    private @Nullable NetworkEntry currentNetwork() {
        return network(type);
    }

    /** A rede da aba {@code t}, se ela estiver numa rede que o seletor conhece. */
    private @Nullable NetworkEntry network(ResourceType t) {
        Optional<UUID> id = snapshot().network(t);
        if (id.isEmpty()) {
            return null;
        }
        for (NetworkEntry entry : snapshot().networks()) {
            if (entry.id().equals(id.get())) {
                return entry;
            }
        }
        return null;
    }

    private Component networkLabel() {
        return networkLabel(type);
    }

    private Component networkLabel(ResourceType t) {
        NetworkEntry entry = network(t);
        if (entry != null) {
            return Component.literal(entry.name());
        }
        return snapshot().network(t).isPresent() ? tr("network.unknown") : tr("network.none");
    }

    private int networkColor() {
        return networkColor(type);
    }

    private int networkColor(ResourceType t) {
        NetworkEntry entry = network(t);
        return entry != null ? 0xFF000000 | entry.color() : GuiPaint.MUTED;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    private static Component typeName(ResourceType type) {
        return ResourceStyle.name(type);
    }

    private static Component faceName(Direction direction) {
        return tr("face." + direction.getName());
    }

    private static Component modeName(PortMode mode) {
        return tr("mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private static Component redstoneName(RedstoneMode mode) {
        return tr("redstone." + mode.name().toLowerCase(Locale.ROOT));
    }

    /** O que a face acessa: "27 slots", "1 tanque", "energia" ou "sem acesso". */
    private Component access(FaceView view) {
        if (!view.available()) {
            return tr("access.none");
        }
        return ResourceStyle.access(type, view.slots());
    }

    private Component rate() {
        long[] perType = menu.throughput();
        long value = type.ordinal() < perType.length ? perType[type.ordinal()] : 0L;
        return ResourceStyle.rate(type, value);
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        w = Math.max(MIN_W, Math.min(savedW, maxW()));
        h = Math.max(MIN_H, Math.min(savedH, maxH()));
        imageWidth = w;
        imageHeight = h;
        super.init();
        buttons.clear();
        tabButtons.clear();
        modeButtons.clear();
        int x = leftPos;
        int y = topPos;

        titleButton = add(new FlatButton(x + X0, y + HEAD_Y, 80, PILL_H, Component.empty(), this::paintTitle,
                this::startRename).tooltip(() -> tr("rename.tooltip")));

        renameBox = new EditBox(font, x + X0 + 4, y + HEAD_Y + 3, 80, 9, tr("rename.narration"));
        renameBox.setBordered(false);
        renameBox.setMaxLength(RenameRouterPayload.MAX_LENGTH);
        renameBox.setTextColor(GuiPaint.FG);
        renameBox.setValue(renameDraft);
        renameBox.setResponder(value -> renameDraft = value);
        renameBox.visible = renaming;
        addRenderableWidget(renameBox);
        if (renaming) {
            setFocused(renameBox);
        }

        for (ResourceType t : types) {
            FlatButton tab = add(new FlatButton(0, y + TAB_Y, TabLayout.ICON_TAB, TAB_H, typeName(t),
                    (g, b, hovered) -> paintTab(g, b, hovered, t), () -> selectType(t))
                    .tooltip(() -> tr("tab.tooltip", typeName(t), networkLabel(t))));
            tabButtons.put(t, tab);
        }
        layoutTabs();

        // a rede é da aba: o seletor fica na linha das abas, à direita delas
        networkButton = add(new FlatButton(x, y + TAB_Y + 1, 40, PILL_H, Component.empty(), this::paintNetwork,
                () -> {
                    networkListOpen = !networkListOpen;
                    networkScroll = 0;
                }).tooltip(this::networkTooltip));

        machineView.setBounds(x + X0 + 1, y + BODY_Y + 1, viewW() - 2, viewH() - 2);
        for (int i = 0; i < FACE_ORDER.length; i++) {
            Direction direction = FACE_ORDER[i];
            faceButtons.put(direction, add(new FlatButton(x + X0 + (i % 3) * (faceW() + FACE_GAP),
                    y + facesY() + (i / 3) * (FACE_H + FACE_GAP), faceW(), FACE_H, faceName(direction),
                    (g, b, hovered) -> paintFace(g, b, hovered, direction), () -> face = direction)
                    .tooltip(() -> faceTooltip(direction))));
        }

        for (int i = 0; i < MODES.length; i++) {
            PortMode mode = MODES[i];
            FlatButton button = add(new FlatButton(x + rx() + (i % 2) * (MODE_W + 2), y + MODE_Y + (i / 2) * (MODE_H + 2),
                    MODE_W, MODE_H, modeName(mode), (g, b, hovered) -> paintMode(g, b, hovered, mode),
                    () -> sendFace(mode, view().priority(), view().redstone()))
                    .tooltip(() -> view().available() ? tr("mode." + mode.name().toLowerCase(Locale.ROOT) + ".tooltip")
                            : tr("mode.unavailable")));
            modeButtons.add(button);
        }

        editFilterButton = add(new FlatButton(x + x1() - 40, y + FILTER_Y, 40, ROW_H, tr("filter.edit"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("filter.edit")), this::openFilter)
                .tooltip(() -> tr("filter.edit.tooltip")));

        moreButton = add(new FlatButton(x + X0, y + moreY(), 60, 12, tr("more"), this::paintMore,
                () -> expanded = !expanded).tooltip(() -> tr("more.tooltip")));

        prioMinus = add(new FlatButton(x + lx1() - 58, y + prioY(), 14, ROW_H, tr("priority.decrease"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("-")),
                () -> changePriority(-1)).tooltip(() -> tr("priority.tooltip")));
        prioPlus = add(new FlatButton(x + lx1() - 14, y + prioY(), 14, ROW_H, tr("priority.increase"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("+")),
                () -> changePriority(1)).tooltip(() -> tr("priority.tooltip")));
        redstoneButton = add(new FlatButton(x + X0, y + redstoneY(), viewW(), ROW_H, tr("redstone"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("redstone.narration", redstoneName(view().redstone()))),
                this::cycleRedstone).tooltip(() -> tr("redstone.tooltip")));

        // os slots do menu e o visor acompanham o tamanho da tela
        menu.placeSlots(rx() + 1, w - 26, inventoryY());
        machineView.setBounds(x + X0 + 1, y + BODY_Y + 1, viewW() - 2, viewH() - 2);
        refresh();
    }

    private void selectType(ResourceType t) {
        type = t;
        layoutTabs();
    }

    /** Larguras e posições das abas pelo espaço que sobra depois do seletor de rede. */
    private void layoutTabs() {
        int[] full = new int[types.size()];
        int active = 0;
        for (int i = 0; i < types.size(); i++) {
            full[i] = 4 + TAB_DOT + ResourceStyle.ICON + 3 + font.width(typeName(types.get(i))) + 6;
            if (types.get(i) == type) {
                active = i;
            }
        }
        int available = (x1() - X0) - NET_MIN - 6;
        TabLayout.Result layout = TabLayout.choose(full, active, available);
        tabMode = layout.mode();
        int tabX = leftPos + X0;
        for (int i = 0; i < types.size(); i++) {
            FlatButton tab = tabButtons.get(types.get(i));
            tab.setX(tabX);
            tab.setWidth(layout.widths()[i]);
            tabX += layout.widths()[i] + TabLayout.GAP;
        }
    }

    /** Modo atual das abas (gancho do e2e). */
    public TabLayout.Mode tabMode() {
        return tabMode;
    }

    // ------------------------------------------------------------------ geometria (depende do tamanho)

    private int x1() {
        return w - 9;
    }

    /** Coluna da direita: largura fixa ({@link #RW}), presa à borda direita. */
    private int rx() {
        return x1() - RW;
    }

    /** O visor fica com toda a largura e a altura extras. */
    private int viewW() {
        return rx() - 8 - X0;
    }

    private int viewH() {
        return VIEW_H + (h - MIN_H);
    }

    /** Fim da coluna do visor. */
    private int lx1() {
        return X0 + viewW();
    }

    private int facesY() {
        return BODY_Y + viewH() + 3;
    }

    private int faceW() {
        return (viewW() - 2 * FACE_GAP) / 3;
    }

    private int advSepY() {
        return ADV_SEP_Y + (h - MIN_H);
    }

    private int moreY() {
        return MORE_Y + (h - MIN_H);
    }

    private int prioY() {
        return PRIO_Y + (h - MIN_H);
    }

    private int redstoneY() {
        return REDSTONE_Y + (h - MIN_H);
    }

    /** Topo do inventário do jogador (o canto do item), colado na borda de baixo. */
    private int inventoryY() {
        return RouterMenu.INVENTORY_Y + (h - MIN_H);
    }

    /** Fundo do primeiro slot de cartão (o item fica 1 px para dentro). */
    private int cardBgX() {
        return rx() + 1 - 1;
    }

    /** Fundo do slot do upgrade, no canto direito do cabeçalho. */
    private int upgradeBgX() {
        return w - 26 - 1;
    }

    /** Onde termina a pílula do tier: antes do slot do upgrade, com folga. */
    private int headEnd() {
        return upgradeBgX() - 4;
    }

    private int maxW() {
        return Math.max(MIN_W, width - 8);
    }

    private int maxH() {
        return Math.max(MIN_H, height - 8);
    }

    private FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    /** Posições, visibilidade e narração que dependem do snapshot ou do estado da tela. */
    private void refresh() {
        RouterSnapshot s = snapshot();
        Component tierText = Component.translatable(s.tier().translationKey());
        int tierW = pillWidth(tierText);
        int rateX = leftPos + headEnd() - tierW - 6 - font.width(rate());

        // seletor da rede com todo o espaço que sobra à direita das abas
        int tabsEnd = leftPos + X0;
        for (FlatButton tab : tabButtons.values()) {
            tabsEnd = Math.max(tabsEnd, tab.getX() + tab.getWidth());
        }
        int netX = tabsEnd + 6;
        int netW = leftPos + x1() - netX;
        netTextMax = Math.max(24, netW - NET_PILL_PAD);
        Component netText = networkLabel();
        networkButton.setX(netX);
        networkButton.setWidth(netW);
        networkButton.setMessage(tr("network.tab.narration", typeName(type), netText));

        int titleW = rateX - (leftPos + X0) - 8;
        titleButton.setWidth(Math.min(titleW, font.width(displayName()) + 4));
        titleButton.setMessage(displayName());
        titleButton.visible = !renaming;
        renameBox.setWidth(titleW - 8);
        renameBox.visible = renaming;
        renameBox.setHint(displayName().copy().withColor(GuiPaint.DISABLED));

        FaceView v = view();
        for (FlatButton button : modeButtons) {
            button.active = v.available();
        }
        moreButton.setWidth(11 + font.width(expanded ? tr("less") : tr("more")) + 2);
        moreButton.setMessage(expanded ? tr("less") : tr("more"));
        prioMinus.visible = prioPlus.visible = redstoneButton.visible = expanded;
        prioMinus.active = prioPlus.active = redstoneButton.active = v.available();
        redstoneButton.setMessage(tr("redstone.narration", redstoneName(v.redstone())));
        editFilterButton.visible = editFilterButton.active = hasFilter();

        List<NetworkEntry> networks = s.networks();
        networkScroll = Math.max(0, Math.min(networkScroll, networks.size() + 1 - DROPDOWN_MAX_ROWS));
        syncSelection();
    }

    /**
     * Aba ou face mudou (por botão, visor ou teclado): os slots de cartão passam a mostrar os da
     * face e do tipo novos. O menu local muda já, para a previsão dos cliques; o servidor, pelo pacote.
     */
    private void syncSelection() {
        if (type == menu.selectedType() && face == menu.selectedFace()) {
            return;
        }
        menu.select(type, face);
        if (!preview) {
            send(new SelectFacePayload(menu.containerId, type, face));
        }
    }

    private int pillWidth(Component text) {
        return 6 + 5 + 4 + font.width(text) + 6;
    }

    // ------------------------------------------------------------------ ações

    private void send(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private void sendFace(PortMode mode, int priority, RedstoneMode redstone) {
        FaceView current = view();
        if (!current.available()) {
            return;
        }
        if (preview) {
            List<FaceView> faces = new ArrayList<>(snapshot().faces());
            faces.set(RouterSnapshot.index(type, face), new FaceView(mode, priority, redstone, current.slots(),
                    current.filterSize(), current.blacklist()));
            RouterSnapshot s = snapshot();
            menu.applySnapshot(new RouterSnapshot(s.pos(), s.name(), s.tier(), s.facing(), s.typeNetworks(), s.networks(),
                    s.powered(), s.machine(), s.machineState(), List.copyOf(faces), s.chunkLoad()));
            return;
        }
        send(new SetFacePayload(menu.containerId, type, face, mode, priority, redstone));
    }

    /** Itens, fluidos e químicos têm filtro; energia não. */
    private boolean hasFilter() {
        return RouterFaceFilterTarget.hasFilter(type);
    }

    /** Editar: o servidor troca esta tela pela de filtro da face. */
    private void openFilter() {
        if (hasFilter() && !preview) {
            send(new OpenFilterPayload(menu.containerId, type, face));
        }
    }

    /** Rótulo da linha do filtro: "Filtro" ou, com entradas, "Filtro · lista branca". */
    private Component filterLabel(FaceView view) {
        if (!hasFilter() || view.filterSize() == 0) {
            return tr("filter.label");
        }
        return tr(view.blacklist() ? "filter.black" : "filter.white");
    }

    /** "Passa tudo", "12 entradas", "2 cartões" ou "12 entradas · 2 cartões". */
    private Component filterSummary(FaceView view) {
        int cards = cardCount();
        Component entries = view.filterSize() == 1 ? tr("filter.count.one") : tr("filter.count", view.filterSize());
        if (cards == 0) {
            return view.filterSize() == 0 ? tr("filter.none") : entries;
        }
        Component cardText = cards == 1 ? tr("filter.cards.one") : tr("filter.cards", cards);
        return view.filterSize() == 0 ? cardText : tr("filter.summary", entries, cardText);
    }

    /** Cartões nos slots da face selecionada (os slots já mostram a seleção da tela). */
    private int cardCount() {
        int count = 0;
        for (int i = 0; i < RouterMenu.CARD_SLOT_COUNT; i++) {
            if (menu.getSlot(i).hasItem()) {
                count++;
            }
        }
        return count;
    }

    private static boolean isCardSlot(@Nullable Slot slot) {
        return slot != null && slot.index < RouterMenu.CARD_SLOT_COUNT;
    }

    private static boolean isUpgradeSlot(@Nullable Slot slot) {
        return slot != null && slot.index == RouterMenu.UPGRADE_SLOT;
    }

    /** Estado do upgrade: o do snapshot, ou "sem upgrade" se o slot está vazio (a sincronização do slot chega antes). */
    private ChunkLoadState chunkLoadState() {
        return menu.getSlot(RouterMenu.UPGRADE_SLOT).hasItem() ? snapshot().chunkLoad() : ChunkLoadState.NONE;
    }

    private static int chunkLoadColor(ChunkLoadState state) {
        return switch (state) {
            case ACTIVE -> 0xFF41C96B;
            case LIMIT -> 0xFFFFB020;
            case DISABLED -> 0xFFFF6B5E;
            case NONE -> GuiPaint.MUTED;
        };
    }

    /**
     * Dica do slot de upgrade: vazio, o que ele faz e como pôr; com o upgrade, o nome, o estado e
     * como tirar.
     */
    private Component upgradeSlotTooltip() {
        ChunkLoadState state = chunkLoadState();
        if (state == ChunkLoadState.NONE) {
            return tr("upgrade.tooltip").copy()
                    .append("\n").append(tr("upgrade.tooltip.effect").copy().withColor(GuiPaint.MUTED))
                    .append("\n").append(tr("upgrade.tooltip.add").copy().withColor(GuiPaint.MUTED));
        }
        String key = "upgrade.state." + state.name().toLowerCase(Locale.ROOT);
        return GHOST_UPGRADE.getHoverName().copy()
                .append("\n").append(tr(key).copy().withColor(chunkLoadColor(state)))
                .append("\n").append(tr(key + ".detail").copy().withColor(GuiPaint.MUTED))
                .append("\n").append(tr("upgrade.tooltip.remove").copy().withColor(GuiPaint.MUTED));
    }

    /** Dica de um slot de cartão vazio: o tipo, a regra do conjunto e como pôr um cartão. */
    private Component cardSlotTooltip() {
        return tr("cards.tooltip", typeName(type)).copy()
                .append("\n").append(tr("cards.tooltip.rule").copy().withColor(GuiPaint.MUTED))
                .append("\n").append(tr("cards.tooltip.add").copy().withColor(GuiPaint.MUTED));
    }

    private void changePriority(int direction) {
        int step = hasShiftDown() ? 10 : 1;
        int value = Math.max(RouterMenu.MIN_PRIORITY, Math.min(RouterMenu.MAX_PRIORITY, view().priority() + direction * step));
        if (value != view().priority()) {
            sendFace(view().mode(), value, view().redstone());
        }
    }

    private void cycleRedstone() {
        RedstoneMode[] all = RedstoneMode.values();
        sendFace(view().mode(), view().priority(), all[(view().redstone().ordinal() + 1) % all.length]);
    }

    private void chooseNetwork(Optional<UUID> network) {
        networkListOpen = false;
        if (network.equals(snapshot().network(type))) {
            return;
        }
        if (preview) {
            RouterSnapshot s = snapshot();
            List<Optional<UUID>> typeNetworks = new ArrayList<>(s.typeNetworks());
            typeNetworks.set(type.ordinal(), network);
            menu.applySnapshot(new RouterSnapshot(s.pos(), s.name(), s.tier(), s.facing(), List.copyOf(typeNetworks), s.networks(),
                    s.powered(), s.machine(), s.machineState(), s.faces(), s.chunkLoad()));
            return;
        }
        send(new SetNetworkPayload(menu.containerId, type, network));
    }

    private void startRename() {
        renaming = true;
        networkListOpen = false;
        renameDraft = snapshot().name();
        renameBox.setValue(renameDraft);
        renameBox.moveCursorToEnd(false);
        setFocused(renameBox);
        refresh();
    }

    private void finishRename(boolean commit) {
        if (!renaming) {
            return;
        }
        renaming = false;
        setFocused(null);
        String name = renameBox.getValue().strip();
        if (commit && !name.equals(snapshot().name())) {
            if (preview) {
                RouterSnapshot s = snapshot();
                menu.applySnapshot(new RouterSnapshot(s.pos(), name, s.tier(), s.facing(), s.typeNetworks(), s.networks(),
                        s.powered(), s.machine(), s.machineState(), s.faces(), s.chunkLoad()));
            } else {
                send(new RenameRouterPayload(menu.containerId, name));
            }
        }
        refresh();
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Resize edge = button == 0 && !networkListOpen ? resizeAt(mouseX, mouseY) : null;
        if (edge != null) {
            resizing = edge;
            resizeCenterX = leftPos + w / 2.0;
            resizeCenterY = topPos + h / 2.0;
            resizeGrabX = leftPos + w - mouseX;
            resizeGrabY = topPos + h - mouseY;
            return true;
        }
        if (networkListOpen) {
            int row = dropdownRowAt(mouseX, mouseY);
            if (row >= 0) {
                List<NetworkEntry> networks = snapshot().networks();
                chooseNetwork(row < networks.size() ? Optional.of(networks.get(row).id()) : Optional.empty());
            } else if (!dropdownContains(mouseX, mouseY)) {
                networkListOpen = false;
            }
            return true;
        }
        if (renaming && !renameBox.isMouseOver(mouseX, mouseY)) {
            finishRename(true);
        }
        if (machineView.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        boolean wasRenaming = renaming;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (renaming && !wasRenaming) {
            // a tela dá o foco ao botão clicado depois do onPress, tirando-o do campo do nome
            setFocused(renameBox);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resizing != null) {
            dragResize(mouseX, mouseY);
            return true;
        }
        // a tela de contêiner não repassa o arrasto aos filhos; o visor recebe direto
        if (machineView.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizing != null) {
            resizing = null;
            return true;
        }
        // soltar fora do visor também encerra o giro
        if (machineView.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** O que o mouse redimensiona ali, ou {@code null}: a alça do canto, a borda direita ou a de baixo. */
    private @Nullable Resize resizeAt(double mouseX, double mouseY) {
        int right = leftPos + w;
        int bottom = topPos + h;
        if (mouseX < leftPos || mouseY < topPos || mouseX >= right || mouseY >= bottom) {
            return null;
        }
        if (mouseX >= right - GRIP && mouseY >= bottom - GRIP) {
            return Resize.BOTH;
        }
        if (mouseX >= right - EDGE) {
            return Resize.WIDTH;
        }
        return mouseY >= bottom - EDGE ? Resize.HEIGHT : null;
    }

    /**
     * Redimensiona em torno do centro, como o filtro: a borda arrastada segue o mouse e a oposta se
     * move igual, então o painel continua centralizado.
     */
    private void dragResize(double mouseX, double mouseY) {
        int newW = w;
        int newH = h;
        if (resizing != Resize.HEIGHT) {
            double wanted = 2 * (mouseX + resizeGrabX - resizeCenterX);
            newW = Math.max(MIN_W, Math.min(maxW(), (int) Math.round(wanted / 2) * 2));
        }
        if (resizing != Resize.WIDTH) {
            double wanted = 2 * (mouseY + resizeGrabY - resizeCenterY);
            newH = Math.max(MIN_H, Math.min(maxH(), (int) Math.round(wanted / 2) * 2));
        }
        if (newW != w || newH != h) {
            savedW = newW;
            savedH = newH;
            rebuild();
        }
    }

    /**
     * Refaz os widgets no tamanho lembrado: aba, face, lista de redes e "Mais" ficam em campos e
     * sobrevivem; o nome em edição é fechado sem gravar.
     */
    private void rebuild() {
        if (renaming) {
            renaming = false;
            setFocused(null);
        }
        rebuildWidgets();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        resizing = null;
        super.resize(minecraft, width, height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (networkListOpen && dropdownContains(mouseX, mouseY)) {
            networkScroll -= (int) Math.signum(scrollY);
            refresh();
            return true;
        }
        if (!networkListOpen && machineView.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renaming) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishRename(false);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishRename(true);
            } else {
                renameBox.keyPressed(keyCode, scanCode, modifiers);
            }
            // nenhuma tecla fecha a tela enquanto se digita (nem a do inventário)
            return true;
        }
        if (networkListOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            networkListOpen = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renaming) {
            return renameBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // barato (meia dúzia de larguras de texto): cobre snapshot novo (menu.version() mudou) e mudanças
        // de estado feitas pelo teclado, sem recriar widgets
        refresh();
        GuiText.beginFrame();
        boolean overList = networkListOpen && dropdownContains(mouseX, mouseY);
        // com a lista de redes aberta, os botões embaixo dela não ficam realçados
        super.render(g, overList ? -1 : mouseX, overList ? -1 : mouseY, partialTick);
        if (networkListOpen) {
            renderNetworkList(g, mouseX, mouseY);
            Component clippedName = GuiText.clipAt(mouseX, mouseY);
            if (clippedName != null) {
                setTooltipForNextRenderPass(clippedName);
            }
            return;
        }
        if (resizing != null || resizeAt(mouseX, mouseY) != null) {
            setTooltipForNextRenderPass(Component.translatable("gui.wirelessautomate.resize.tooltip"));
            return;
        }
        for (FlatButton button : buttons) {
            if (button.visible && button.isHovered()) {
                Component tooltip = button.currentTooltip();
                if (tooltip != null) {
                    setTooltipForNextRenderPass(tooltip);
                }
                return;
            }
        }
        if (isCardSlot(hoveredSlot) && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            setTooltipForNextRenderPass(cardSlotTooltip());
            return;
        }
        if (isUpgradeSlot(hoveredSlot) && menu.getCarried().isEmpty()) {
            setTooltipForNextRenderPass(upgradeSlotTooltip());
            return;
        }
        renderTooltip(g, mouseX, mouseY);
        // sobre uma face o visor mostra o nome dela embaixo; a dica cobriria o modelo
        boolean machineTip = !machineView.isDragging() && machineView.contains(mouseX, mouseY)
                && machineView.hoveredFace() == null;
        if (machineTip) {
            setTooltipForNextRenderPass(machineTooltip());
        }
        // texto abreviado: a dica traz o texto inteiro, se nenhuma outra dica já está no ar
        boolean hasTooltip = machineTip || hoveredSlot != null && hoveredSlot.hasItem();
        if (!hasTooltip) {
            Component clipped = GuiText.clipAt(mouseX, mouseY);
            if (clipped != null) {
                setTooltipForNextRenderPass(clipped);
            }
        }
    }

    /** Dica do visor fora das faces: a máquina e como usar o visor. */
    private Component machineTooltip() {
        ItemStack machine = snapshot().machine();
        Component name = machine.isEmpty() ? tr("machine.none") : machine.getHoverName();
        return name.copy().append("\n").append(tr("view.hint").copy().withColor(GuiPaint.MUTED));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RouterSnapshot s = snapshot();
        int x = leftPos;
        int y = topPos;
        int trim = tierColor();
        GuiPaint.panel(g, x, y, w, h, trim);

        // cabeçalho
        if (renaming) {
            GuiPaint.box(g, x + X0, y + HEAD_Y, renameBox.getWidth() + 8, PILL_H, GuiPaint.INSET, trim);
        }
        Component tierText = Component.translatable(s.tier().translationKey());
        int tierW = pillWidth(tierText);
        int tierX = x + headEnd() - tierW;
        GuiPaint.pill(g, tierX, y + HEAD_Y, tierW, PILL_H, GuiPaint.PANEL, trim);
        GuiPaint.dot(g, tierX + 6, y + HEAD_Y + 4, trim);
        GuiPaint.text(g, font, tierText, tierX + 15, y + HEAD_Y + 3, GuiPaint.FG);

        // vazão da aba, ao lado do tier, no espaço entre o título e a pílula
        Component rate = rate();
        int rateSpace = tierX - 6 - (x + X0 + titleButton.getWidth() + 8);
        int rateW = Math.min(font.width(rate), Math.max(0, rateSpace));
        GuiText.draw(g, font, rate, tierX - 6 - rateW, y + HEAD_Y + 3, rateW, GuiPaint.MUTED);

        // slot do upgrade de chunk loading, no canto do cabeçalho, com a luz do estado (sobre o item)
        int ux = x + upgradeBgX();
        int uy = y + UPGRADE_BG_Y;
        GuiPaint.slot(g, ux, uy);
        ChunkLoadState chunkLoad = chunkLoadState();
        if (chunkLoad == ChunkLoadState.NONE) {
            ghostUpgrade(g, ux + 1, uy + 1);
        } else {
            int color = chunkLoadColor(chunkLoad);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(ux + 11, uy + 1, ux + 17, uy + 7, GuiPaint.BEVEL_DARK);
            g.fill(ux + 12, uy + 2, ux + 16, uy + 6, color);
            g.fill(ux + 12, uy + 2, ux + 14, uy + 4, GuiPaint.mix(color, 0xFFFFFFFF, 0.5f));
            g.pose().popPose();
        }

        // separador sob as abas
        g.fill(x + X0, y + SEP_Y, x + x1(), y + SEP_Y + 1, GuiPaint.LINE);

        // visor 3D: máquina e roteador; com a lista de redes aberta, nada de realce sob o mouse
        int vx = x + X0;
        int vy = y + BODY_Y;
        int viewW = viewW();
        int viewH = viewH();
        g.fillGradient(vx + 1, vy + 1, vx + viewW - 1, vy + viewH - 1, GuiPaint.VIEW_TOP, GuiPaint.VIEW_BOTTOM);
        GuiPaint.outline(g, vx, vy, viewW, viewH, GuiPaint.LINE);
        Direction buttonHovered = null;
        for (Map.Entry<Direction, FlatButton> entry : faceButtons.entrySet()) {
            if (!networkListOpen && entry.getValue().isMouseOver(mouseX, mouseY)) {
                buttonHovered = entry.getKey();
            }
        }
        machineView.render(g, s.machineState(), s.facing(), s.tier(), faceViews, face, buttonHovered,
                networkListOpen ? -1 : mouseX, networkListOpen ? -1 : mouseY);
        Direction pointed = machineView.hoveredFace();
        if (s.machineState().isAir()) {
            GuiPaint.textCentered(g, font, tr("machine.none"), vx + viewW / 2, vy + viewH - 12, GuiPaint.MUTED);
        } else if (pointed != null) {
            Component label = faceName(pointed);
            g.fill(vx + 1, vy + viewH - 13, vx + 7 + font.width(label), vy + viewH - 1, 0xB011151B);
            GuiPaint.text(g, font, label, vx + 4, vy + viewH - 11, GuiPaint.FG);
        }

        // face selecionada
        FaceView v = view();
        GuiPaint.text(g, font, faceName(face), x + rx(), y + BODY_Y + 1, GuiPaint.FG);
        Component detail = access(v);
        if (face == s.facing()) {
            detail = detail.copy().append(" · ").append(tr("face.attached"));
        }
        GuiText.draw(g, font, detail, x + rx(), y + BODY_Y + 12, RW, GuiPaint.MUTED);

        // filtro: rótulo com Editar e, embaixo, os slots de cartão com o resumo do conjunto
        if (hasFilter()) {
            GuiText.draw(g, font, filterLabel(v), x + rx(), y + FILTER_Y + 3, RW - 44, GuiPaint.MUTED);
            for (int i = 0; i < RouterMenu.CARD_SLOT_COUNT; i++) {
                int sx = x + cardBgX() + i * 18;
                GuiPaint.slot(g, sx, y + CARD_BG_Y);
                if (!menu.getSlot(i).hasItem()) {
                    ghostCard(g, sx + 1, y + CARD_BG_Y + 1);
                }
            }
            int textX = x + cardBgX() + RouterMenu.CARD_SLOT_COUNT * 18 + 5;
            GuiText.draw(g, font, filterSummary(v), textX, y + CARD_BG_Y + 5, x + x1() - textX, GuiPaint.FG);
        } else {
            int used = GuiText.wrap(g, font, tr("filter.untyped", typeName(type)), x + rx(), y + FILTER_Y + 3, RW, 2,
                    GuiPaint.DISABLED);
            long tierRate = Config.TIERS.get(s.tier()).rate(type);
            Component rateText = tierRate == 0 ? tr("rate.unlimited") : ResourceStyle.rate(type, tierRate);
            GuiText.draw(g, font, tr("rate.tier", rateText), x + rx(), y + FILTER_Y + 3 + used + 4, RW, GuiPaint.MUTED);
        }

        // recolhido, embaixo das faces: prioridade e redstone
        g.fill(x + X0, y + advSepY(), x + lx1(), y + advSepY() + 1, GuiPaint.LINE);
        if (expanded) {
            GuiText.draw(g, font, tr("priority"), x + X0, y + prioY() + 3, viewW - 60, GuiPaint.MUTED);
            int boxX = x + lx1() - 43;
            GuiPaint.box(g, boxX, y + prioY(), 28, ROW_H, GuiPaint.INSET, GuiPaint.LINE);
            GuiPaint.textCentered(g, font, Component.literal(Integer.toString(v.priority())), boxX + 14,
                    y + prioY() + 3, v.available() ? GuiPaint.FG : GuiPaint.DISABLED);
        } else {
            // em duas linhas: a coluna é estreita para "Prioridade 0 · Com sinal"
            Component priority = tr("priority").copy().append(" " + v.priority());
            Component redstone = tr("redstone.narration", redstoneName(v.redstone()));
            GuiText.draw(g, font, priority, x + X0, y + prioY() + 1, viewW, GuiPaint.MUTED);
            GuiText.draw(g, font, redstone, x + X0, y + prioY() + 12, viewW, GuiPaint.MUTED);
        }

        // inventário do jogador, na coluna da direita e colado na borda de baixo
        int invX = x + rx() + 1;
        int invY = y + inventoryY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                GuiPaint.slot(g, invX - 1 + col * 18, invY - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            GuiPaint.slot(g, invX - 1 + col * 18, invY + 57);
        }

        // alça de redimensionar: três riscos na diagonal, acesos com o mouse em cima ou arrastando
        Resize hover = resizing != null ? resizing : networkListOpen ? null : resizeAt(mouseX, mouseY);
        int grip = hover != null ? trim : GuiPaint.BUTTON_HOVER_BORDER;
        int gx = x + w - 4;
        int gy = y + h - 4;
        for (int i = 0; i < 3; i++) {
            int d = 2 + i * 2;
            for (int k = 0; k <= d; k += 2) {
                g.fill(gx - d + k, gy - k, gx - d + k + 1, gy - k + 1, grip);
            }
        }
        if (hover == Resize.WIDTH || hover == Resize.BOTH) {
            g.fill(x + w - 3, y + 3, x + w - 2, y + h - 3, grip);
        }
        if (hover == Resize.HEIGHT || hover == Resize.BOTH) {
            g.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, grip);
        }
    }

    /** Upgrade apagado no fundo do slot de upgrade vazio. */
    private void ghostUpgrade(GuiGraphics g, int x, int y) {
        g.renderFakeItem(GHOST_UPGRADE, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.fill(x, y, x + 16, y + 16, 0xD811151B);
        g.pose().popPose();
    }

    /** Cartão apagado no fundo de um slot de cartão vazio. */
    private void ghostCard(GuiGraphics g, int x, int y) {
        g.renderFakeItem(GHOST_CARD, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.fill(x, y, x + 16, y + 16, 0xD811151B);
        g.pose().popPose();
    }

    private void paintTitle(GuiGraphics g, FlatButton b, boolean hovered) {
        int color = hovered ? tierColor() : GuiPaint.FG;
        GuiText.draw(g, font, displayName(), b.getX(), b.getY() + 3, b.getWidth() - 2, color);
        if (hovered) {
            int width = Math.min(font.width(displayName()), b.getWidth() - 2);
            for (int i = 0; i < width; i += 2) {
                g.fill(b.getX() + i, b.getY() + 12, b.getX() + i + 1, b.getY() + 13, color);
            }
        }
    }

    /** Dica da pílula: de qual aba é a rede e, se o nome não coube, o nome inteiro antes. */
    private Component networkTooltip() {
        Component hint = tr("network.tab.tooltip", typeName(type));
        Component name = networkLabel();
        Component head = tr("network.label").copy().withColor(GuiPaint.MUTED);
        if (font.width(name) <= netTextMax) {
            return head.copy().append("\n").append(hint);
        }
        return head.copy().append("\n").append(name).append("\n").append(hint.copy().withColor(GuiPaint.MUTED));
    }

    private void paintNetwork(GuiGraphics g, FlatButton b, boolean hovered) {
        int color = networkColor();
        int fill = hovered || networkListOpen ? GuiPaint.BUTTON : GuiPaint.PANEL;
        GuiPaint.pill(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, color);
        if (currentNetwork() != null) {
            GuiPaint.dot(g, b.getX() + 6, b.getY() + 4, color);
        } else {
            GuiPaint.outline(g, b.getX() + 6, b.getY() + 4, 5, 5, color);
        }
        Component label = networkLabel();
        int textColor = currentNetwork() != null ? GuiPaint.FG : GuiPaint.MUTED;
        GuiText.draw(g, font, label, b.getX() + 15, b.getY() + 3, netTextMax, textColor);
        GuiPaint.arrowDown(g, b.getX() + b.getWidth() - 10, b.getY() + 5, hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    private void paintTab(GuiGraphics g, FlatButton b, boolean hovered, ResourceType t) {
        boolean selected = t == type;
        int trim = tierColor();
        if (selected) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), trim, trim);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        networkDot(g, b.getX() + 5, b.getY() + 5, t);
        ResourceStyle.drawIcon(g, t, b.getX() + 4 + TAB_DOT, b.getY() + 3);
        if (b.getWidth() > TabLayout.ICON_TAB) {
            int textX = b.getX() + 4 + TAB_DOT + ResourceStyle.ICON + 3;
            GuiText.draw(g, font, typeName(t), textX, b.getY() + 4, b.getX() + b.getWidth() - 4 - textX,
                    selected ? GuiPaint.DARK_TEXT : GuiPaint.FG);
        }
        if (selected) {
            g.fill(b.getX() + 1, b.getY() + b.getHeight() - 2, b.getX() + b.getWidth() - 1,
                    b.getY() + b.getHeight() - 1, ResourceStyle.color(t));
        }
    }

    /**
     * Ponto na cor da rede da aba {@code t} (vazado sem rede), com contorno escuro para aparecer
     * também na aba selecionada, que é preenchida na cor do tier.
     */
    private void networkDot(GuiGraphics g, int x, int y, ResourceType t) {
        g.fill(x, y - 1, x + 5, y + 6, GuiPaint.BEVEL_DARK);
        g.fill(x - 1, y, x + 6, y + 5, GuiPaint.BEVEL_DARK);
        if (network(t) != null) {
            GuiPaint.dot(g, x, y, networkColor(t));
        } else {
            g.fill(x, y, x + 5, y + 5, GuiPaint.BEVEL_DARK);
            GuiPaint.outline(g, x, y, 5, 5, GuiPaint.MUTED);
        }
    }

    private void paintFace(GuiGraphics g, FlatButton b, boolean hovered, Direction direction) {
        FaceView v = view(direction);
        boolean selected = direction == face;
        // a face sob o mouse no visor também realça o botão dela
        boolean pointed = hovered || direction == machineView.hoveredFace();
        int x = b.getX();
        int y = b.getY();
        int border = selected ? tierColor() : pointed ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, x, y, b.getWidth(), b.getHeight(), v.available() ? GuiPaint.BUTTON : GuiPaint.INSET, border);
        if (selected) {
            GuiPaint.outline(g, x + 1, y + 1, b.getWidth() - 2, b.getHeight() - 2, tierColor());
        }
        GuiPaint.port(g, v.available() ? v.mode() : PortMode.NONE, x + 1, y + 1, v.available() ? 1f : 0.3f);
        GuiPaint.text(g, font, tr("face.short." + direction.getName()), x + 19, y + 5,
                v.available() ? (selected ? GuiPaint.FG : GuiPaint.MUTED) : GuiPaint.DISABLED);
        if (direction == snapshot().facing()) {
            // marca do roteador preso nesta face: um "LED" na cor do tier ao lado da letra
            int bx = x + b.getWidth() - 9;
            int by = y + 6;
            g.fill(bx, by, bx + 6, by + 6, GuiPaint.BEVEL_DARK);
            g.fill(bx + 1, by + 1, bx + 5, by + 5, tierColor());
            g.fill(bx + 1, by + 1, bx + 3, by + 3, GuiPaint.mix(tierColor(), 0xFFFFFFFF, 0.5f));
        }
    }

    private Component faceTooltip(Direction direction) {
        FaceView v = view(direction);
        Component line = v.available()
                ? modeName(v.mode()).copy().append(" · ").append(access(v))
                : access(v);
        Component text = faceName(direction).copy().append("\n").append(line);
        if (direction == snapshot().facing()) {
            text = text.copy().append("\n").append(tr("face.attached.tooltip"));
        }
        return text;
    }

    private void paintMode(GuiGraphics g, FlatButton b, boolean hovered, PortMode mode) {
        FaceView v = view();
        boolean selected = v.available() && v.mode() == mode;
        int color = GuiPaint.modeColor(mode);
        int fill = selected ? GuiPaint.mix(GuiPaint.BUTTON, color, 0.28f) : GuiPaint.BUTTON;
        int border = selected ? color : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        GuiPaint.port(g, mode, b.getX() + 2, b.getY() + 1, b.active ? 1f : 0.3f);
        GuiText.draw(g, font, modeName(mode), b.getX() + 21, b.getY() + 5, b.getWidth() - 22,
                b.active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    private void paintTextButton(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        int border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                b.active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    private void paintMore(GuiGraphics g, FlatButton b, boolean hovered) {
        int color = hovered || expanded ? GuiPaint.FG : GuiPaint.MUTED;
        if (expanded) {
            GuiPaint.arrowDown(g, b.getX() + 1, b.getY() + 3, color);
        } else {
            GuiPaint.arrowRight(g, b.getX() + 2, b.getY() + 2, color);
        }
        GuiPaint.text(g, font, expanded ? tr("less") : tr("more"), b.getX() + 10, b.getY() + 1, color);
    }

    // ------------------------------------------------------------------ lista de redes

    private int dropdownWidth() {
        int width = networkButton.getWidth();
        for (NetworkEntry entry : snapshot().networks()) {
            width = Math.max(width, font.width(entry.name()) + 24);
        }
        width = Math.max(width, font.width(tr("network.none")) + 24);
        width = Math.max(width, font.width(listTitle()) + 12);
        return Math.min(width, 140);
    }

    private int dropdownX() {
        return Math.min(networkButton.getX(), leftPos + x1() - dropdownWidth());
    }

    private int dropdownY() {
        return networkButton.getY() + PILL_H + 2;
    }

    private int dropdownRows() {
        return Math.min(DROPDOWN_MAX_ROWS, snapshot().networks().size() + 1);
    }

    /** Título da lista: de qual aba é a rede que se escolhe. */
    private Component listTitle() {
        return tr("network.list.title", typeName(type));
    }

    /** Altura da lista: o título e as linhas visíveis. */
    private int dropdownHeight() {
        return (dropdownRows() + 1) * DROPDOWN_ROW + 4;
    }

    private boolean dropdownContains(double mouseX, double mouseY) {
        return mouseX >= dropdownX() && mouseX < dropdownX() + dropdownWidth()
                && mouseY >= dropdownY() && mouseY < dropdownY() + dropdownHeight();
    }

    /** Índice na lista (redes e depois "Sem rede") sob o mouse, ou -1 (fora dela ou no título). */
    private int dropdownRowAt(double mouseX, double mouseY) {
        if (!dropdownContains(mouseX, mouseY)) {
            return -1;
        }
        int row = (int) Math.floor((mouseY - dropdownY() - 2) / DROPDOWN_ROW) - 1;
        if (row < 0 || row >= dropdownRows()) {
            return -1;
        }
        return row + networkScroll;
    }

    private void renderNetworkList(GuiGraphics g, int mouseX, int mouseY) {
        List<NetworkEntry> networks = snapshot().networks();
        int x = dropdownX();
        int y = dropdownY();
        int w = dropdownWidth();
        int rows = dropdownRows();
        int hoveredRow = dropdownRowAt(mouseX, mouseY);
        Optional<UUID> current = snapshot().network(type);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        GuiPaint.box(g, x, y, w, dropdownHeight(), GuiPaint.INSET, GuiPaint.BUTTON_HOVER_BORDER);
        GuiText.draw(g, font, listTitle(), x + 5, y + 4, w - 10, GuiPaint.MUTED);
        g.fill(x + 3, y + 2 + DROPDOWN_ROW, x + w - 3, y + 3 + DROPDOWN_ROW, GuiPaint.LINE);
        for (int i = 0; i < rows; i++) {
            int index = i + networkScroll;
            int ry = y + 2 + (i + 1) * DROPDOWN_ROW;
            boolean none = index >= networks.size();
            NetworkEntry entry = none ? null : networks.get(index);
            boolean selected = none ? current.isEmpty() : current.isPresent() && current.get().equals(entry.id());
            if (index == hoveredRow) {
                g.fill(x + 1, ry, x + w - 1, ry + DROPDOWN_ROW, GuiPaint.BUTTON);
            }
            if (selected) {
                g.fill(x + 1, ry, x + 2, ry + DROPDOWN_ROW, tierColor());
            }
            int color = none ? GuiPaint.MUTED : 0xFF000000 | entry.color();
            if (none) {
                GuiPaint.outline(g, x + 6, ry + 3, 5, 5, color);
            } else {
                GuiPaint.dot(g, x + 6, ry + 3, color);
            }
            Component label = none ? tr("network.none") : Component.literal(entry.name());
            GuiText.draw(g, font, label, x + 15, ry + 2, w - 20, none ? GuiPaint.MUTED : GuiPaint.FG);
        }
        if (networkScroll > 0) {
            GuiPaint.text(g, font, Component.literal("▲"), x + w - 9, y + 2 + DROPDOWN_ROW, GuiPaint.MUTED);
        }
        if (networkScroll + rows < networks.size() + 1) {
            GuiPaint.text(g, font, Component.literal("▼"), x + w - 9, y + (rows + 1) * DROPDOWN_ROW - 8, GuiPaint.MUTED);
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    void previewType(ResourceType type) {
        selectType(type);
        refresh();
    }

    void previewFace(Direction face) {
        this.face = face;
        refresh();
    }

    void previewExpanded(boolean expanded) {
        this.expanded = expanded;
        refresh();
    }

    void previewRename(String draft) {
        startRename();
        renameBox.setValue(draft);
    }

    /** e2e: muda o tamanho como se a borda tivesse sido arrastada. */
    public void previewResize(int width, int height) {
        savedW = width;
        savedH = height;
        rebuild();
    }

    /** Tamanho atual do painel, {@code {largura, altura}} (gancho do e2e). */
    public int[] size() {
        return new int[] {w, h};
    }

    void previewNetworkList(boolean open) {
        this.networkListOpen = open;
    }

    /** Centro do botão da face, para simular o mouse em cima na captura. */
    int[] previewFaceCenter(Direction direction) {
        for (FlatButton button : buttons) {
            if (button.getMessage().equals(faceName(direction))) {
                return new int[] {button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2};
            }
        }
        return new int[] {-1, -1};
    }

    /** Visor no ângulo inicial do snapshot atual. */
    void previewViewDefault() {
        machineView.previewReset(snapshot().facing(), snapshot().machineState());
    }

    /** Visor num ângulo fixo (graus) e zoom. */
    void previewView(float yaw, float pitch, float zoom) {
        previewViewDefault();
        machineView.previewAngles(yaw, pitch, zoom);
    }

    /** Ponto da tela no centro da face da máquina no visor, para simular o mouse em cima. */
    int[] previewViewFaceCenter(Direction direction) {
        return machineView.previewProject(direction, snapshot().facing(), snapshot().machineState());
    }

    int[] previewCardSlotCenter(int slot) {
        Slot s = menu.getSlot(slot);
        return new int[] {leftPos + s.x + 8, topPos + s.y + 8};
    }

    int[] previewUpgradeSlotCenter() {
        Slot s = menu.getSlot(RouterMenu.UPGRADE_SLOT);
        return new int[] {leftPos + s.x + 8, topPos + s.y + 8};
    }

    int[] previewEditFilterCenter() {
        return new int[] {editFilterButton.getX() + 20, editFilterButton.getY() + 7};
    }

    int[] previewModeCenter(PortMode mode) {
        return center(modeButtons.get(List.of(MODES).indexOf(mode)));
    }

    int[] previewTabCenter(ResourceType t) {
        return center(tabButtons.get(t));
    }

    int[] previewNetworkCenter() {
        return center(networkButton);
    }

    private static int[] center(FlatButton button) {
        return new int[] {button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2};
    }
}
