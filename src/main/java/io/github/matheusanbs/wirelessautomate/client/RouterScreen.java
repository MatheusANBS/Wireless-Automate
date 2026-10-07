package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.OpenFilterPayload;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do roteador (especificação, "Telas da interface › Roteador"): cabeçalho com nome, rede e
 * tier; abas por tipo com a vazão; o visor 3D da máquina com o roteador ({@link MachineView3D}) e
 * os botões das 6 faces logo abaixo; a face selecionada com modo e filtro; prioridade e redstone
 * recolhidos em "Mais".
 *
 * <p>Tudo vem de {@link RouterMenu#snapshot()}. Os botões leem o estado a cada quadro e
 * {@link #refresh()} reposiciona o que depende de texto, então um snapshot novo
 * ({@link RouterMenu#version()} mudou) aparece sem recriar widgets e sem perder aba, face ou o que
 * está aberto.
 */
public class RouterScreen extends AbstractContainerScreen<RouterMenu> {
    /** Químicos ficam ocultos até o motor movê-los (o servidor ainda recusa CHEMICAL). */
    private static final boolean CHEMICALS_READY = false;

    private static final int W = 256;
    /** Altura com "Mais" aberto; a posição da tela usa esta, e recolhido o painel só fica mais curto embaixo. */
    private static final int H = 205;
    private static final int H_COLLAPSED = 183;
    private static final int X0 = 9;
    private static final int X1 = W - 9;
    private static final int HEAD_Y = 8;
    private static final int PILL_H = 13;
    private static final int TAB_Y = 26;
    private static final int TAB_H = 15;
    private static final int SEP_Y = 45;
    private static final int BODY_Y = 50;
    // visor 3D e, logo abaixo, os botões das faces em duas fileiras (cada coluna é um eixo)
    private static final int VIEW_W = 100;
    private static final int VIEW_H = 84;
    private static final int FACE_W = 32;
    private static final int FACE_H = 18;
    private static final int FACE_GAP = 2;
    private static final int FACES_Y = BODY_Y + VIEW_H + 3;
    // coluna da face selecionada
    private static final int RX = X0 + VIEW_W + 8;
    private static final int RW = X1 - RX;
    private static final int MODE_Y = 74;
    private static final int MODE_W = (RW - 2) / 2;
    private static final int MODE_H = 18;
    private static final int FILTER_Y = 118;
    private static final int ADV_SEP_Y = 146;
    private static final int MORE_Y = 150;
    private static final int PRIO_Y = 166;
    private static final int REDSTONE_Y = 184;
    private static final int ROW_H = 14;
    private static final int DROPDOWN_ROW = 12;
    private static final int DROPDOWN_MAX_ROWS = 8;

    private static final PortMode[] MODES = {PortMode.EXTRACT, PortMode.INSERT, PortMode.BOTH, PortMode.NONE};
    /** Ordem dos botões das faces: em cima Cima, Norte e Leste; embaixo as opostas. */
    private static final Direction[] FACE_ORDER = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.DOWN,
            Direction.SOUTH, Direction.WEST};

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

    public RouterScreen(RouterMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /**
     * @param preview sem servidor (captura de desenvolvimento): as mudanças valem só no snapshot local
     */
    public RouterScreen(RouterMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.preview = preview;
        this.imageWidth = W;
        this.imageHeight = H;
        this.face = menu.snapshot().facing();
        types.add(ResourceType.ITEM);
        types.add(ResourceType.FLUID);
        types.add(ResourceType.ENERGY);
        if (CHEMICALS_READY && ModList.get().isLoaded("mekanism")) {
            types.add(ResourceType.CHEMICAL);
        }
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
        Optional<UUID> id = snapshot().network();
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
        NetworkEntry entry = currentNetwork();
        if (entry != null) {
            return Component.literal(entry.name());
        }
        return snapshot().network().isPresent() ? tr("network.unknown") : tr("network.none");
    }

    private int networkColor() {
        NetworkEntry entry = currentNetwork();
        return entry != null ? 0xFF000000 | entry.color() : GuiPaint.MUTED;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    private static Component typeName(ResourceType type) {
        return tr("type." + type.name().toLowerCase(Locale.ROOT));
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
        return switch (type) {
            case ITEM -> view.slots() == 1 ? tr("access.slot") : tr("access.slots", view.slots());
            case FLUID, CHEMICAL -> view.slots() == 1 ? tr("access.tank") : tr("access.tanks", view.slots());
            case ENERGY -> tr("access.energy");
        };
    }

    private Component rate() {
        long[] perType = menu.throughput();
        long value = type.ordinal() < perType.length ? perType[type.ordinal()] : 0L;
        return switch (type) {
            case ITEM -> tr("rate.items", RateFormat.abbreviate(value));
            case FLUID, CHEMICAL -> value >= 1000
                    ? tr("rate.buckets", RateFormat.abbreviate(value / 1000))
                    : tr("rate.millibuckets", RateFormat.abbreviate(value));
            case ENERGY -> tr("rate.energy", RateFormat.abbreviate(value));
        };
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
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

        networkButton = add(new FlatButton(x, y + HEAD_Y, 40, PILL_H, Component.empty(), this::paintNetwork,
                () -> {
                    networkListOpen = !networkListOpen;
                    networkScroll = 0;
                }).tooltip(() -> tr("network.tooltip")));

        int tabX = x + X0;
        for (ResourceType t : types) {
            int width = font.width(typeName(t)) + 12;
            FlatButton tab = add(new FlatButton(tabX, y + TAB_Y, width, TAB_H, typeName(t),
                    (g, b, hovered) -> paintTab(g, b, hovered, t), () -> type = t));
            tabButtons.put(t, tab);
            tabX += width + 3;
        }

        machineView.setBounds(x + X0 + 1, y + BODY_Y + 1, VIEW_W - 2, VIEW_H - 2);
        for (int i = 0; i < FACE_ORDER.length; i++) {
            Direction direction = FACE_ORDER[i];
            faceButtons.put(direction, add(new FlatButton(x + X0 + (i % 3) * (FACE_W + FACE_GAP),
                    y + FACES_Y + (i / 3) * (FACE_H + FACE_GAP), FACE_W, FACE_H, faceName(direction),
                    (g, b, hovered) -> paintFace(g, b, hovered, direction), () -> face = direction)
                    .tooltip(() -> faceTooltip(direction))));
        }

        for (int i = 0; i < MODES.length; i++) {
            PortMode mode = MODES[i];
            FlatButton button = add(new FlatButton(x + RX + (i % 2) * (MODE_W + 2), y + MODE_Y + (i / 2) * (MODE_H + 2),
                    MODE_W, MODE_H, modeName(mode), (g, b, hovered) -> paintMode(g, b, hovered, mode),
                    () -> sendFace(mode, view().priority(), view().redstone()))
                    .tooltip(() -> view().available() ? tr("mode." + mode.name().toLowerCase(Locale.ROOT) + ".tooltip")
                            : tr("mode.unavailable")));
            modeButtons.add(button);
        }

        editFilterButton = add(new FlatButton(x + X1 - 40, y + FILTER_Y + 8, 40, ROW_H, tr("filter.edit"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("filter.edit")), this::openFilter)
                .tooltip(() -> hasFilter() ? tr("filter.edit.tooltip") : tr("filter.energy")));

        moreButton = add(new FlatButton(x + RX, y + MORE_Y, 60, 12, tr("more"), this::paintMore,
                () -> expanded = !expanded).tooltip(() -> tr("more.tooltip")));

        prioMinus = add(new FlatButton(x + X1 - 58, y + PRIO_Y, 14, ROW_H, tr("priority.decrease"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("-")),
                () -> changePriority(-1)).tooltip(() -> tr("priority.tooltip")));
        prioPlus = add(new FlatButton(x + X1 - 14, y + PRIO_Y, 14, ROW_H, tr("priority.increase"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("+")),
                () -> changePriority(1)).tooltip(() -> tr("priority.tooltip")));
        redstoneButton = add(new FlatButton(x + X1 - 66, y + REDSTONE_Y, 66, ROW_H, tr("redstone"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, redstoneName(view().redstone())),
                this::cycleRedstone).tooltip(() -> tr("redstone.tooltip")));

        refresh();
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
        Component netText = networkLabel();
        int netW = 6 + 5 + 4 + Math.min(font.width(netText), 84) + 4 + 5 + 5;
        int netX = leftPos + X1 - tierW - 4 - netW;
        networkButton.setX(netX);
        networkButton.setWidth(netW);
        networkButton.setMessage(tr("network.narration", netText));

        int titleW = netX - (leftPos + X0) - 6;
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
        editFilterButton.active = hasFilter();

        List<NetworkEntry> networks = s.networks();
        networkScroll = Math.max(0, Math.min(networkScroll, networks.size() + 1 - DROPDOWN_MAX_ROWS));
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
            menu.applySnapshot(new RouterSnapshot(s.pos(), s.name(), s.tier(), s.facing(), s.network(), s.networks(),
                    s.powered(), s.machine(), s.machineState(), List.copyOf(faces)));
            return;
        }
        send(new SetFacePayload(menu.containerId, type, face, mode, priority, redstone));
    }

    /** Só itens e fluidos têm filtro. */
    private boolean hasFilter() {
        return type == ResourceType.ITEM || type == ResourceType.FLUID;
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

    /** "Passa tudo", "1 entrada" ou "12 entradas". */
    private Component filterSummary(FaceView view) {
        if (view.filterSize() == 0) {
            return tr("filter.none");
        }
        return view.filterSize() == 1 ? tr("filter.count.one") : tr("filter.count", view.filterSize());
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
        if (network.equals(snapshot().network())) {
            return;
        }
        if (preview) {
            RouterSnapshot s = snapshot();
            menu.applySnapshot(new RouterSnapshot(s.pos(), s.name(), s.tier(), s.facing(), network, s.networks(),
                    s.powered(), s.machine(), s.machineState(), s.faces()));
            return;
        }
        send(new SetNetworkPayload(menu.containerId, network));
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
                menu.applySnapshot(new RouterSnapshot(s.pos(), name, s.tier(), s.facing(), s.network(), s.networks(),
                        s.powered(), s.machine(), s.machineState(), s.faces()));
            } else {
                send(new RenameRouterPayload(menu.containerId, name));
            }
        }
        refresh();
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (networkListOpen) {
            int row = dropdownRowAt(mouseX, mouseY);
            if (row >= 0) {
                List<NetworkEntry> networks = snapshot().networks();
                chooseNetwork(row < networks.size() ? Optional.of(networks.get(row).id()) : Optional.empty());
            } else {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // a tela de contêiner não repassa o arrasto aos filhos; o visor recebe direto
        if (machineView.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // soltar fora do visor também encerra o giro
        if (machineView.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
        boolean overList = networkListOpen && dropdownContains(mouseX, mouseY);
        // com a lista de redes aberta, os botões embaixo dela não ficam realçados
        super.render(g, overList ? -1 : mouseX, overList ? -1 : mouseY, partialTick);
        if (networkListOpen) {
            renderNetworkList(g, mouseX, mouseY);
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
        // sobre uma face o visor mostra o nome dela embaixo; a dica cobriria o modelo
        if (!machineView.isDragging() && machineView.contains(mouseX, mouseY) && machineView.hoveredFace() == null) {
            setTooltipForNextRenderPass(machineTooltip());
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
        GuiPaint.panel(g, x, y, W, expanded ? H : H_COLLAPSED, trim);

        // cabeçalho
        if (renaming) {
            GuiPaint.box(g, x + X0, y + HEAD_Y, renameBox.getWidth() + 8, PILL_H, GuiPaint.INSET, trim);
        }
        Component tierText = Component.translatable(s.tier().translationKey());
        int tierW = pillWidth(tierText);
        int tierX = x + X1 - tierW;
        GuiPaint.pill(g, tierX, y + HEAD_Y, tierW, PILL_H, GuiPaint.PANEL, trim);
        GuiPaint.dot(g, tierX + 6, y + HEAD_Y + 4, trim);
        GuiPaint.text(g, font, tierText, tierX + 15, y + HEAD_Y + 3, GuiPaint.FG);

        // abas e vazão
        g.fill(x + X0, y + SEP_Y, x + X1, y + SEP_Y + 1, GuiPaint.LINE);
        GuiPaint.textRight(g, font, rate(), x + X1, y + TAB_Y + 4, GuiPaint.MUTED);

        // visor 3D: máquina e roteador; com a lista de redes aberta, nada de realce sob o mouse
        int vx = x + X0;
        int vy = y + BODY_Y;
        g.fillGradient(vx + 1, vy + 1, vx + VIEW_W - 1, vy + VIEW_H - 1, GuiPaint.VIEW_TOP, GuiPaint.VIEW_BOTTOM);
        GuiPaint.outline(g, vx, vy, VIEW_W, VIEW_H, GuiPaint.LINE);
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
            GuiPaint.textCentered(g, font, tr("machine.none"), vx + VIEW_W / 2, vy + VIEW_H - 12, GuiPaint.MUTED);
        } else if (pointed != null) {
            Component label = faceName(pointed);
            g.fill(vx + 1, vy + VIEW_H - 13, vx + 7 + font.width(label), vy + VIEW_H - 1, 0xB011151B);
            GuiPaint.text(g, font, label, vx + 4, vy + VIEW_H - 11, GuiPaint.FG);
        }

        // face selecionada
        FaceView v = view();
        GuiPaint.text(g, font, faceName(face), x + RX, y + BODY_Y + 1, GuiPaint.FG);
        Component detail = access(v);
        if (face == s.facing()) {
            detail = detail.copy().append(" · ").append(tr("face.attached"));
        }
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, detail, RW), x + RX, y + BODY_Y + 12, GuiPaint.MUTED);

        // filtro resumido em uma linha, com Editar
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, filterLabel(v), RW), x + RX, y + FILTER_Y, GuiPaint.MUTED);
        Component filterLine = hasFilter() ? filterSummary(v) : tr("filter.energy");
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, filterLine, RW - 44), x + RX, y + FILTER_Y + 11,
                hasFilter() ? GuiPaint.FG : GuiPaint.MUTED);

        // recolhido: prioridade e redstone
        g.fill(x + RX, y + ADV_SEP_Y, x + X1, y + ADV_SEP_Y + 1, GuiPaint.LINE);
        if (expanded) {
            GuiPaint.text(g, font, tr("priority"), x + RX, y + PRIO_Y + 3, GuiPaint.MUTED);
            int boxX = x + X1 - 43;
            GuiPaint.box(g, boxX, y + PRIO_Y, 28, ROW_H, GuiPaint.INSET, GuiPaint.LINE);
            GuiPaint.textCentered(g, font, Component.literal(Integer.toString(v.priority())), boxX + 14,
                    y + PRIO_Y + 3, v.available() ? GuiPaint.FG : GuiPaint.DISABLED);
            GuiPaint.text(g, font, tr("redstone"), x + RX, y + REDSTONE_Y + 3, GuiPaint.MUTED);
        } else {
            Component summary = tr("summary", v.priority(), redstoneName(v.redstone()));
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, summary, RW), x + RX, y + PRIO_Y + 1, GuiPaint.MUTED);
        }
    }

    private void paintTitle(GuiGraphics g, FlatButton b, boolean hovered) {
        int color = hovered ? tierColor() : GuiPaint.FG;
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, displayName(), b.getWidth() - 2), b.getX(), b.getY() + 3, color);
        if (hovered) {
            int width = Math.min(font.width(displayName()), b.getWidth() - 2);
            for (int i = 0; i < width; i += 2) {
                g.fill(b.getX() + i, b.getY() + 12, b.getX() + i + 1, b.getY() + 13, color);
            }
        }
    }

    private void paintNetwork(GuiGraphics g, FlatButton b, boolean hovered) {
        int color = networkColor();
        int fill = hovered || networkListOpen ? GuiPaint.BUTTON : GuiPaint.PANEL;
        GuiPaint.pill(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, color);
        GuiPaint.dot(g, b.getX() + 6, b.getY() + 4, color);
        Component label = networkLabel();
        int textColor = currentNetwork() != null ? GuiPaint.FG : GuiPaint.MUTED;
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, label, 84), b.getX() + 15, b.getY() + 3, textColor);
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
        GuiPaint.textCentered(g, font, typeName(t), b.getX() + b.getWidth() / 2, b.getY() + 4,
                selected ? GuiPaint.DARK_TEXT : GuiPaint.FG);
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
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, modeName(mode), b.getWidth() - 22), b.getX() + 21,
                b.getY() + 5, b.active ? GuiPaint.FG : GuiPaint.DISABLED);
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
        return Math.min(width, 140);
    }

    private int dropdownX() {
        return Math.min(networkButton.getX(), leftPos + X1 - dropdownWidth());
    }

    private int dropdownY() {
        return networkButton.getY() + PILL_H + 2;
    }

    private int dropdownRows() {
        return Math.min(DROPDOWN_MAX_ROWS, snapshot().networks().size() + 1);
    }

    private boolean dropdownContains(double mouseX, double mouseY) {
        return mouseX >= dropdownX() && mouseX < dropdownX() + dropdownWidth()
                && mouseY >= dropdownY() && mouseY < dropdownY() + dropdownRows() * DROPDOWN_ROW + 4;
    }

    /** Índice na lista (redes e depois "Sem rede") sob o mouse, ou -1. */
    private int dropdownRowAt(double mouseX, double mouseY) {
        if (!dropdownContains(mouseX, mouseY)) {
            return -1;
        }
        int row = (int) ((mouseY - dropdownY() - 2) / DROPDOWN_ROW);
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
        Optional<UUID> current = snapshot().network();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        GuiPaint.box(g, x, y, w, rows * DROPDOWN_ROW + 4, GuiPaint.INSET, GuiPaint.BUTTON_HOVER_BORDER);
        for (int i = 0; i < rows; i++) {
            int index = i + networkScroll;
            int ry = y + 2 + i * DROPDOWN_ROW;
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
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, label, w - 20), x + 15, ry + 2,
                    none ? GuiPaint.MUTED : GuiPaint.FG);
        }
        if (networkScroll > 0) {
            GuiPaint.text(g, font, Component.literal("▲"), x + w - 9, y + 2, GuiPaint.MUTED);
        }
        if (networkScroll + rows < networks.size() + 1) {
            GuiPaint.text(g, font, Component.literal("▼"), x + w - 9, y + rows * DROPDOWN_ROW - 8, GuiPaint.MUTED);
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    void previewType(ResourceType type) {
        this.type = type;
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

    int[] previewEditFilterCenter() {
        return new int[] {editFilterButton.getX() + 20, editFilterButton.getY() + 7};
    }
}
