package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do roteador (especificação, "Painel do nó › Tela do roteador"): cabeçalho com nome, vazão da aba,
 * tier, os slots dos upgrades e o botão do tema; o visor 3D da máquina com o roteador
 * ({@link MachineView3D}); os slots de Cartão de Filtro da face escolhida embaixo do visor; o inventário
 * do jogador; e o painel do nó ({@link NodePanel}): abas por tipo, as faces desdobradas como um cubo e a
 * face escolhida com modo, rede, filtro, prioridade e redstone.
 *
 * <p>Duas disposições, pela largura. Larga (a de abertura): à esquerda o visor, os cartões e o inventário;
 * à direita o painel inteiro, com as abas em cima. Estreita (até o mínimo de 300 × 240, a menor escala
 * automática): as abas na largura toda, o visor com o cubo e os cartões embaixo à esquerda e, à direita, o
 * resto do painel em cima do inventário.
 *
 * <p>O visor e o cubo andam juntos: a face clicada num escolhe a mesma no outro, e a face sob o mouse num
 * realça a do outro. Os slots de cartão mostram os da face e do tipo escolhidos: ao mudar de aba ou de
 * face a tela avisa o servidor ({@link SelectFacePayload}), que troca o que os slots mostram.
 *
 * <p>Com o Upgrade de Alcance valendo ({@link RangeView#present()}), um botão "Máquinas · 3/8" acima do
 * visor alterna o visor com a lista das máquinas ({@link RangeList}), e o painel ganha a linha Editando.
 * Escolher um grupo ou uma máquina troca o alvo de edição: as faces passam ao quadro canônico (Norte =
 * Frente) e o visor mostra o bloco do alvo sem o roteador.
 *
 * <p>Tudo vem de {@link RouterMenu#snapshot()}; o painel lê o snapshot a cada quadro, então um snapshot
 * novo aparece sem recriar widgets e sem perder aba, face ou o que está aberto.
 */
public class RouterScreen extends AbstractContainerScreen<RouterMenu> {

    // O mínimo é largo e baixo o bastante para caber na menor escala automática (320×240); a tela
    // cresce pela borda direita, pela de baixo e pelo canto, e o tamanho fica lembrado na sessão.
    private static final int MIN_W = 300;
    private static final int MIN_H = 240;
    /** Largura de abertura: a disposição larga, com o painel inteiro ao lado do visor. */
    private static final int DEFAULT_W = 420;
    /** Alça do canto (lado, em px) e espessura das bordas que redimensionam. */
    private static final int GRIP = 7;
    private static final int EDGE = 3;
    private static int savedW = DEFAULT_W;
    private static int savedH = MIN_H;
    private static final int X0 = 9;
    private static final int HEAD_Y = 8;
    /** Quanto os slots de upgrade recuam para dar lugar ao botão do tema no canto direito do cabeçalho. */
    private static final int HEAD_SHIFT = ThemeToggle.SIZE + 4;
    private static final int PILL_H = 13;
    /** Topo das abas (estreita) ou das duas colunas (larga). */
    private static final int TOP_Y = 26;
    /** Topo das colunas na disposição estreita, abaixo das abas e da linha. */
    private static final int NARROW_BODY_Y = 47;
    /** Inventário (e a coluna da direita na estreita): 9 slots. */
    private static final int INV_W = 162;
    private static final int COLUMN_GAP = 8;
    /** Largura do painel na disposição larga: do mínimo (as abas com o nome da ativa) ao máximo. */
    private static final int PANEL_MIN = 210;
    private static final int PANEL_MAX = 240;
    /** A partir desta largura, a disposição larga. */
    private static final int WIDE_MIN = 2 * X0 + COLUMN_GAP + INV_W + PANEL_MIN;
    /** Altura do cubo embaixo do visor, na estreita: 3 linhas de 13 px. */
    private static final int NARROW_NET_H = 3 * 13 + 2 * 2;
    /** Slot do upgrade (o fundo), no canto direito do cabeçalho; o tier e a vazão ficam à esquerda dele. */
    private static final int UPGRADE_BG_Y = RouterMenu.UPGRADE_Y - 1;
    /** Faixa do botão "Máquinas" acima do visor (tirada da altura dele), com o upgrade de alcance. */
    private static final int RANGE_STRIP = 14;
    /** Largura da lista quando ela fica ao lado do visor ({@link #sideBySide}). */
    private static final int SIDE_LIST_W = 150;
    /** Espaço entre a lista e o visor lado a lado. */
    private static final int SIDE_GAP = 4;
    /** Largura mínima do visor ao lado da lista: abaixo disso, os dois alternam no mesmo lugar. */
    private static final int SIDE_MIN_VIEW = 140;
    /**
     * Mostra a lista das máquinas no lugar do visor. Estático de propósito, como {@link #savedW}: a
     * escolha vale para a sessão inteira, então a próxima tela de um roteador com o upgrade já abre na
     * lista (ou no visor) como o jogador deixou.
     */
    private static boolean showList;

    /** Cartão desenhado apagado num slot de cartão vazio. */
    private static final ItemStack GHOST_CARD = new ItemStack(ModItems.FILTER_CARD.get());
    /** Upgrade desenhado apagado no slot de upgrade vazio. */
    private static final ItemStack GHOST_UPGRADE = new ItemStack(ModItems.CHUNK_LOADER_UPGRADE.get());
    /** Upgrade de Alcance desenhado apagado no slot de alcance vazio. */
    private static final ItemStack GHOST_RANGE = new ItemStack(ModItems.RANGE_UPGRADE.get());

    private final boolean preview;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final NodePanel panel;
    private final MachineView3D machineView;
    private final Function<Direction, FaceView> faceViews = this::visorView;
    private final RangeList rangeList = new RangeList();

    private boolean renaming;
    private String renameDraft = "";

    private FlatButton titleButton;
    private FlatButton machinesButton;
    private EditBox renameBox;

    private int w = MIN_W;
    private int h = MIN_H;
    private final ResizeHandle resizeHandle = new ResizeHandle(GRIP, EDGE);

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
        this.panel = new NodePanel(new PanelActions(), menu.snapshot(), menu.selectedFace());
        this.machineView = new MachineView3D(panel::selectFace);
        // o aviso de outra tela agiria no alvo errado
        PanelNotices.clear();
    }

    /** O que o painel manda: pelo menu desta tela, ou só no snapshot local sem servidor. */
    private final class PanelActions implements NodePanel.Actions {
        @Override
        public int containerId() {
            return menu.containerId;
        }

        @Override
        public void send(CustomPacketPayload payload) {
            if (preview) {
                applyLocally(payload);
            } else {
                PacketDistributor.sendToServer(payload);
            }
        }

        @Override
        public int cardCount() {
            int count = 0;
            for (int i = 0; i < RouterMenu.CARD_SLOT_COUNT; i++) {
                if (menu.getSlot(i).hasItem()) {
                    count++;
                }
            }
            return count;
        }
    }

    /** Captura sem servidor: modo, prioridade, redstone e rede mudam só no snapshot local. */
    private void applyLocally(CustomPacketPayload payload) {
        List<FaceView> faces = new ArrayList<>(snapshot().faces());
        if (payload instanceof SetFacePayload p) {
            int index = RouterSnapshot.index(p.resource(), p.face());
            FaceView current = faces.get(index);
            faces.set(index, new FaceView(p.mode(), p.priority(), p.redstone(), current.slots(), current.filterSize(),
                    current.blacklist(), current.network()));
        } else if (payload instanceof SetNetworkPayload p) {
            int index = RouterSnapshot.index(p.resource(), p.face());
            faces.set(index, faces.get(index).withNetwork(p.network()));
        } else {
            return;
        }
        menu.applySnapshot(snapshot().withFaces(List.copyOf(faces)));
    }

    // ------------------------------------------------------------------ estado

    private RouterSnapshot snapshot() {
        return menu.snapshot();
    }

    private ResourceType type() {
        return panel.type();
    }

    private Direction face() {
        return panel.face();
    }

    private FaceView view(Direction direction) {
        return snapshot().face(type(), direction);
    }

    /** A face no visor: num alvo remoto sem máquina carregada, as faces não aparecem apagadas. */
    private FaceView visorView(Direction direction) {
        FaceView v = view(direction);
        if (remote() && !v.available() && !remoteLoaded()) {
            return new FaceView(v.mode(), v.priority(), v.redstone(), 0, v.filterSize(), v.blacklist(), v.network());
        }
        return v;
    }

    private RangeView range() {
        return snapshot().range();
    }

    /** O upgrade de alcance vale: a tela mostra o botão Máquinas e a lista. */
    private boolean rangeShown() {
        return range().present();
    }

    /** O alvo é um grupo ou uma máquina remota (lados relativos, sem o roteador no visor). */
    private boolean remote() {
        return rangeShown() && !snapshot().target().attached();
    }

    /**
     * A coluna da esquerda é larga o bastante para a lista e o visor lado a lado; senão, o botão
     * Máquinas alterna os dois no mesmo lugar.
     */
    private boolean sideBySide() {
        return rangeShown() && lw() >= SIDE_LIST_W + SIDE_GAP + SIDE_MIN_VIEW;
    }

    /** A lista está na tela: ao lado do visor, ou no lugar dele. */
    private boolean listShown() {
        return rangeShown() && (showList || sideBySide());
    }

    /** O visor está na tela: sem o upgrade, ao lado da lista, ou com a lista fechada. */
    private boolean viewShown() {
        return !rangeShown() || sideBySide() || !showList;
    }

    /** Começo do visor na coluna da esquerda (depois da lista, lado a lado). */
    private int viewX() {
        return X0 + (sideBySide() ? SIDE_LIST_W + SIDE_GAP : 0);
    }

    /** Largura do visor (sem a lista, lado a lado). */
    private int viewBoxW() {
        return sideBySide() ? lw() - SIDE_LIST_W - SIDE_GAP : lw();
    }

    /** Largura da lista. */
    private int listW() {
        return sideBySide() ? SIDE_LIST_W : lw();
    }

    /** Há uma máquina carregada no alvo remoto (a escolhida, ou alguma do grupo). */
    private boolean remoteLoaded() {
        EditTarget target = snapshot().target();
        if (target.kind() == EditTarget.Kind.MACHINE) {
            RangeView.MachineView machine = range().machine(target.id());
            return machine != null && machine.state() == RangeView.MachineState.LOADED;
        }
        if (target.kind() == EditTarget.Kind.GROUP) {
            for (RangeView.MachineView m : range().machines()) {
                if (m.group() == target.id() && m.state() == RangeView.MachineState.LOADED) {
                    return true;
                }
            }
            return false;
        }
        return true;
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

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    private Component rate() {
        long[] perType = menu.throughput();
        ResourceType type = type();
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
        int x = leftPos;
        int y = topPos;

        titleButton = add(new FlatButton(x + X0, y + HEAD_Y, 80, PILL_H, Component.empty(), this::paintTitle,
                this::startRename).tooltip(() -> tr("rename.tooltip")));

        renameBox = new EditBox(font, x + X0 + 4, y + HEAD_Y + 3, 80, 9, tr("rename.narration"));
        renameBox.setBordered(false);
        renameBox.setMaxLength(RenameRouterPayload.MAX_LENGTH);
        renameBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
        renameBox.setValue(renameDraft);
        renameBox.setResponder(value -> renameDraft = value);
        renameBox.visible = renaming;
        addRenderableWidget(renameBox);
        if (renaming) {
            setFocused(renameBox);
        }

        machinesButton = add(new FlatButton(x + X0, y + columnsY(), lw(), RANGE_STRIP - 2, tr("range.machines", 0, 0),
                this::paintMachines, () -> {
                    // lado a lado não há o que alternar: o botão é só o título
                    if (!sideBySide()) {
                        showList = !showList;
                    }
                }).tooltip(this::machinesTooltip));

        // os slots do menu acompanham o tamanho da tela
        menu.placeSlots(X0 + 1, cardsBgY() + 1, inventoryX(), inventoryY(), w - 26 - HEAD_SHIFT,
                w - 26 - 18 - HEAD_SHIFT);
        layoutPanel();
        refresh();
    }

    /** O painel no lugar da disposição atual. */
    private void layoutPanel() {
        int x = leftPos;
        int y = topPos;
        if (wide()) {
            panel.layout(x + rx(), y + TOP_Y, rw(), h - 8 - TOP_Y);
        } else {
            int netY = cardsBgY() - 4 - NARROW_NET_H;
            int bodyBottom = inventoryY() - 1 - 4;
            panel.layoutSplit(x + X0, y + TOP_Y, x1() - X0, x + X0, y + netY, lw(), NARROW_NET_H, x + rx(),
                    y + NARROW_BODY_Y, rw(), bodyBottom - NARROW_BODY_Y);
        }
    }

    /** Modo atual das abas (gancho do e2e). */
    public TabLayout.Mode tabMode() {
        return panel.tabMode();
    }

    /** O painel do nó (gancho do e2e). */
    public NodePanel panel() {
        return panel;
    }

    // ------------------------------------------------------------------ geometria (depende do tamanho)

    int themeX() {
        return leftPos + w - 4 - ThemeToggle.SIZE;
    }

    int themeY() {
        return topPos + HEAD_Y + 1;
    }

    private int x1() {
        return w - 9;
    }

    /** Disposição larga: o painel inteiro ao lado do visor. */
    private boolean wide() {
        return w >= WIDE_MIN;
    }

    /** Largura da coluna da direita: o painel (larga) ou o resto do painel com o inventário (estreita). */
    private int rw() {
        return wide() ? Math.max(PANEL_MIN, Math.min(PANEL_MAX, w - 2 * X0 - COLUMN_GAP - INV_W)) : INV_W;
    }

    /** Começo da coluna da direita, presa à borda direita. */
    private int rx() {
        return x1() - rw();
    }

    /** Largura da coluna da esquerda (visor, cubo na estreita, cartões e, na larga, o inventário). */
    private int lw() {
        return rx() - COLUMN_GAP - X0;
    }

    /** Topo das colunas. */
    private int columnsY() {
        return wide() ? TOP_Y : NARROW_BODY_Y;
    }

    /** Topo do inventário do jogador (o canto do item), colado na borda de baixo. */
    private int inventoryY() {
        return RouterMenu.INVENTORY_Y + (h - MIN_H);
    }

    /** O inventário fica na coluna da esquerda (larga) ou embaixo do painel (estreita). */
    private int inventoryX() {
        return (wide() ? X0 : rx()) + 1;
    }

    /** Fundo dos slots de cartão: embaixo do visor (larga) ou do cubo (estreita). */
    private int cardsBgY() {
        return wide() ? inventoryY() - 1 - 4 - 18 : h - 8 - 18;
    }

    /** Topo do visor (ou da lista): abaixo do botão Máquinas, com o upgrade de alcance. */
    private int boxY() {
        return columnsY() + (rangeShown() ? RANGE_STRIP : 0);
    }

    /** Altura do visor (ou da lista): até o cubo (estreita) ou os cartões (larga). */
    private int boxH() {
        int bottom = wide() ? cardsBgY() - 4 : cardsBgY() - 4 - NARROW_NET_H - 4;
        return bottom - boxY();
    }

    /** Fundo do slot do upgrade, no canto direito do cabeçalho. */
    private int upgradeBgX() {
        return w - 26 - 1 - HEAD_SHIFT;
    }

    /** Fundo do slot do Upgrade de Alcance, à esquerda do slot de upgrade. */
    private int rangeBgX() {
        return upgradeBgX() - 18;
    }

    /** Onde termina a pílula do tier: antes dos dois slots de upgrade, com folga. */
    private int headEnd() {
        return rangeBgX() - 4;
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
        panel.setSnapshot(snapshot());
        Component tierText = Component.translatable(snapshot().tier().translationKey());
        int tierW = pillWidth(tierText);
        int rateX = leftPos + headEnd() - tierW - 6 - font.width(rate());
        int titleW = rateX - (leftPos + X0) - 8;
        titleButton.setWidth(Math.min(titleW, font.width(displayName()) + 4));
        titleButton.setMessage(displayName());
        titleButton.visible = !renaming;
        renameBox.setWidth(titleW - 8);
        renameBox.visible = renaming;
        GuiPaint.ink(renameBox, displayName()); // a cor do EditBox é do widget: segue o tema a cada quadro

        machinesButton.visible = rangeShown();
        machinesButton.setMessage(tr("range.machines", range().used(), range().max()));
        machineView.setBounds(leftPos + viewX() + 1, topPos + boxY() + 1, viewBoxW() - 2, boxH() - 2);
        rangeList.setBounds(leftPos + X0, topPos + boxY(), listW(), boxH());
        if (rangeShown()) {
            rangeList.sync(range(), snapshot().target());
        }
        syncSelection();
    }

    /**
     * Aba ou face mudou (pelo painel, pelo visor ou pelo alvo novo): os slots de cartão passam a mostrar
     * os da face e do tipo novos. O menu local muda já, para a previsão dos cliques; o servidor, pelo pacote.
     */
    private void syncSelection() {
        if (type() == menu.selectedType() && face() == menu.selectedFace()) {
            return;
        }
        menu.select(type(), face());
        if (!preview) {
            PacketDistributor.sendToServer(new SelectFacePayload(menu.containerId, type(), face()));
        }
    }

    private int pillWidth(Component text) {
        return 6 + 5 + 4 + font.width(text) + 6;
    }

    // ------------------------------------------------------------------ ações

    /** Linha da lista clicada: o servidor troca o alvo e o snapshot seguinte traz o alvo e a face novos. */
    private void selectTarget(RangeList.Row row) {
        if (preview) {
            return;
        }
        switch (row.kind()) {
            case ATTACHED -> PacketDistributor.sendToServer(SelectTargetPayload.attached(menu.containerId));
            case GROUP -> {
                RangeView.GroupView group = range().group(row.group());
                if (group != null) {
                    PacketDistributor.sendToServer(SelectTargetPayload.group(menu.containerId, group.id(), group.block()));
                }
            }
            case MACHINE -> {
                RangeView.MachineView machine = range().machine(row.machine());
                if (machine != null) {
                    PacketDistributor.sendToServer(SelectTargetPayload.machine(menu.containerId, row.machine(),
                            machine.pos()));
                }
            }
        }
    }

    /** Dica do botão Máquinas: o que ele alterna, e o raio e o limite do tier. */
    private Component machinesTooltip() {
        Component detail = tr("range.machines.detail", range().radius(), range().max());
        if (sideBySide()) {
            return detail;
        }
        return tr(showList ? "range.machines.tooltip.view" : "range.machines.tooltip.list").copy()
                .append("\n").append(detail.copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** O item da máquina presa (para a linha da lista), pelo mundo do cliente: o snapshot traz o do alvo. */
    private ItemStack attachedIcon() {
        RouterSnapshot s = snapshot();
        if (!remote()) {
            return s.machine();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return ItemStack.EMPTY;
        }
        BlockState state = minecraft.level.getBlockState(s.pos().relative(s.facing().getOpposite()));
        return new ItemStack(state.getBlock().asItem());
    }

    private static boolean isCardSlot(@Nullable Slot slot) {
        return slot != null && slot.index < RouterMenu.CARD_SLOT_COUNT;
    }

    private static boolean isUpgradeSlot(@Nullable Slot slot) {
        return slot != null && slot.index == RouterMenu.UPGRADE_SLOT;
    }

    private static boolean isRangeSlot(@Nullable Slot slot) {
        return slot != null && slot.index == RouterMenu.RANGE_SLOT;
    }

    /**
     * Dica do slot de alcance: vazio, o que ele faz neste tier (ou que o tier não aceita) e como pôr;
     * com o upgrade, o nome, se vale e como tirar. Raio e limite pela config sincronizada do servidor.
     */
    private Component rangeSlotTooltip() {
        RouterTier tier = snapshot().tier();
        int radius = Config.machineRadius(tier);
        int max = Config.maxMachines(tier);
        if (!menu.getSlot(RouterMenu.RANGE_SLOT).hasItem()) {
            Component tip = tr("range.tooltip");
            if (radius <= 0) {
                return tip.copy().append("\n").append(tr("range.tooltip.unavailable").copy().withColor(GuiPaint.WARN));
            }
            return tip.copy()
                    .append("\n").append(tr("range.tooltip.effect", radius, max).copy().withColor(GuiPaint.TOOLTIP_MUTED))
                    .append("\n").append(tr("upgrade.tooltip.add").copy().withColor(GuiPaint.TOOLTIP_MUTED));
        }
        Component state = radius > 0
                ? tr("range.state.active", radius, max).copy().withColor(GuiPaint.OK)
                : tr("range.state.inactive").copy().withColor(GuiPaint.WARN);
        return GHOST_RANGE.getHoverName().copy()
                .append("\n").append(state)
                .append("\n").append(tr("upgrade.tooltip.remove").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** Estado do upgrade: o do snapshot, ou "sem upgrade" se o slot está vazio (a sincronização do slot chega antes). */
    private ChunkLoadState chunkLoadState() {
        return menu.getSlot(RouterMenu.UPGRADE_SLOT).hasItem() ? snapshot().chunkLoad() : ChunkLoadState.NONE;
    }

    private static int chunkLoadColor(ChunkLoadState state) {
        return switch (state) {
            case ACTIVE -> GuiPaint.OK;
            case LIMIT -> GuiPaint.WARN;
            case DISABLED -> GuiPaint.DANGER;
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
                    .append("\n").append(tr("upgrade.tooltip.effect").copy().withColor(GuiPaint.TOOLTIP_MUTED))
                    .append("\n").append(tr("upgrade.tooltip.add").copy().withColor(GuiPaint.TOOLTIP_MUTED));
        }
        String key = "upgrade.state." + state.name().toLowerCase(Locale.ROOT);
        return GHOST_UPGRADE.getHoverName().copy()
                .append("\n").append(tr(key).copy().withColor(chunkLoadColor(state)))
                .append("\n").append(tr(key + ".detail").copy().withColor(GuiPaint.TOOLTIP_MUTED))
                .append("\n").append(tr("upgrade.tooltip.remove").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** Dica de um slot de cartão vazio: o tipo, a regra do conjunto e como pôr um cartão. */
    private Component cardSlotTooltip() {
        return tr("cards.tooltip", ResourceStyle.name(type())).copy()
                .append("\n").append(tr("cards.tooltip.rule").copy().withColor(GuiPaint.TOOLTIP_MUTED))
                .append("\n").append(tr("cards.tooltip.add").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    private void startRename() {
        renaming = true;
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
                menu.applySnapshot(new RouterSnapshot(s.pos(), name, s.tier(), s.facing(), s.attachedFront(), s.networks(),
                        s.powered(), s.machine(), s.machineState(), s.faces(), s.chunkLoad()));
            } else {
                PacketDistributor.sendToServer(new RenameRouterPayload(menu.containerId, name));
            }
        }
        refresh();
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean popup = panel.popupOpen();
        if (!popup && ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && !popup && resizeHandle.begin(mouseX, mouseY, leftPos, topPos, w, h)) {
            return true;
        }
        if (popup) {
            return panel.mouseClicked(mouseX, mouseY, button);
        }
        if (renaming && !renameBox.isMouseOver(mouseX, mouseY)) {
            finishRename(true);
        }
        if (panel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (listShown() && button == 0
                && rangeList.click(range(), snapshot().target(), mouseX, mouseY, this::selectTarget)) {
            return true;
        }
        if (viewShown() && machineView.mouseClicked(mouseX, mouseY, button)) {
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
        if (resizeHandle.dragging()) {
            dragResize(mouseX, mouseY);
            return true;
        }
        // a tela de contêiner não repassa o arrasto aos filhos; o visor recebe direto
        if (viewShown() && machineView.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizeHandle.dragging()) {
            resizeHandle.end();
            return true;
        }
        // soltar fora do visor também encerra o giro
        if (machineView.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Redimensiona em torno do centro, como o filtro: a borda arrastada segue o mouse e a oposta se
     * move igual, então o painel continua centralizado.
     */
    private void dragResize(double mouseX, double mouseY) {
        int newW = w;
        int newH = h;
        if (resizeHandle.changesWidth()) {
            double wanted = resizeHandle.wantedWidth(mouseX);
            newW = Math.max(MIN_W, Math.min(maxW(), (int) Math.round(wanted / 2) * 2));
        }
        if (resizeHandle.changesHeight()) {
            double wanted = resizeHandle.wantedHeight(mouseY);
            newH = Math.max(MIN_H, Math.min(maxH(), (int) Math.round(wanted / 2) * 2));
        }
        if (newW != w || newH != h) {
            savedW = newW;
            savedH = newH;
            rebuild();
        }
    }

    /**
     * Refaz os widgets no tamanho lembrado: aba, face e o que está aberto ficam no painel e sobrevivem; o
     * nome em edição é fechado sem gravar.
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
        resizeHandle.end();
        super.resize(minecraft, width, height);
    }

    @Override
    public void removed() {
        PanelNotices.clear();
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (panel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (listShown() && rangeList.scroll(range(), mouseX, mouseY, scrollY)) {
            return true;
        }
        if (viewShown() && machineView.mouseScrolled(mouseX, mouseY, scrollY)) {
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
        if (panel.keyPressed(keyCode)) {
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
        boolean popup = panel.popupOpen();
        // com uma lista do painel aberta, o que está embaixo dela não fica realçado
        super.render(g, popup ? -1 : mouseX, popup ? -1 : mouseY, partialTick);
        panel.renderOverlay(g, mouseX, mouseY);
        if (popup) {
            Component clipped = GuiText.clipAt(mouseX, mouseY);
            if (clipped != null) {
                setTooltipForNextRenderPass(clipped);
            }
            return;
        }
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        if (resizeHandle.hover(mouseX, mouseY, leftPos, topPos, w, h) != null) {
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
        Component panelTip = panel.tooltip(mouseX, mouseY);
        if (panelTip != null) {
            setTooltipForNextRenderPass(panelTip);
            return;
        }
        if (isCardSlot(hoveredSlot) && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            setTooltipForNextRenderPass(cardSlotTooltip());
            return;
        }
        if (isUpgradeSlot(hoveredSlot) && menu.getCarried().isEmpty()) {
            setTooltipForNextRenderPass(upgradeSlotTooltip());
            return;
        }
        if (isRangeSlot(hoveredSlot) && menu.getCarried().isEmpty()) {
            setTooltipForNextRenderPass(rangeSlotTooltip());
            return;
        }
        renderTooltip(g, mouseX, mouseY);
        // sobre uma face o visor mostra o nome dela embaixo; a dica cobriria o modelo
        boolean machineTip = viewShown() && !machineView.isDragging() && machineView.contains(mouseX, mouseY)
                && machineView.hoveredFace() == null;
        if (machineTip) {
            setTooltipForNextRenderPass(machineTooltip());
        }
        RangeList.Row row = listShown() ? rangeList.rowAt(range(), mouseX, mouseY) : null;
        if (row != null) {
            machineTip = true;
            setTooltipForNextRenderPass(rangeList.tooltip(range(), row, attachedName()));
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

    /** O nome da máquina presa, para a dica da linha dela na lista. */
    private Component attachedName() {
        ItemStack icon = attachedIcon();
        return icon.isEmpty() ? tr("machine.none") : icon.getHoverName();
    }

    /** Dica do visor fora das faces: a máquina e como usar o visor. */
    private Component machineTooltip() {
        ItemStack machine = snapshot().machine();
        Component name = machine.isEmpty() ? tr("machine.none") : machine.getHoverName();
        return name.copy().append("\n").append(tr("view.hint").copy().withColor(GuiPaint.TOOLTIP_MUTED));
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
            GuiPaint.box(g, x + X0, y + HEAD_Y, renameBox.getWidth() + 8, PILL_H, GuiPaint.INSET, GuiPaint.ACCENT);
        }
        Component tierText = Component.translatable(s.tier().translationKey());
        int tierW = pillWidth(tierText);
        int tierX = x + headEnd() - tierW;
        GuiPaint.pill(g, tierX, y + HEAD_Y, tierW, PILL_H, GuiPaint.PANEL, trim);
        GuiPaint.eye(g, tierX + 6, y + HEAD_Y + 4, trim);
        GuiPaint.text(g, font, tierText, tierX + 15, y + HEAD_Y + 3, GuiPaint.FG);

        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);

        // vazão da aba, ao lado do tier, no espaço entre o título e a pílula
        Component rate = rate();
        int rateSpace = tierX - 6 - (x + X0 + titleButton.getWidth() + 8);
        int rateW = Math.min(font.width(rate), Math.max(0, rateSpace));
        GuiText.draw(g, font, rate, tierX - 6 - rateW, y + HEAD_Y + 3, rateW, GuiPaint.MUTED);

        renderUpgradeSlots(g, x, y);

        // na larga, uma linha entre a coluna do visor e a do painel
        if (wide()) {
            int lineX = x + rx() - COLUMN_GAP / 2;
            g.fill(lineX, y + TOP_Y, lineX + 1, y + h - 8, GuiPaint.LINE);
        }

        // visor 3D: máquina e roteador; com uma lista do painel aberta, nada de realce sob o mouse
        boolean popup = panel.popupOpen();
        int pointerX = popup ? -1 : mouseX;
        int pointerY = popup ? -1 : mouseY;
        if (listShown()) {
            rangeList.render(g, font, range(), s.target(), attachedIcon(), pointerX, pointerY);
        }
        if (viewShown()) {
            renderView(g, s, pointerX, pointerY);
        }

        renderCards(g, x, y);

        // inventário do jogador, colado na borda de baixo
        int invX = x + inventoryX();
        int invY = y + inventoryY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                GuiPaint.slot(g, invX - 1 + col * 18, invY - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            GuiPaint.slot(g, invX - 1 + col * 18, invY + 57);
        }

        panel.render(g, pointerX, pointerY, viewShown() && !popup ? machineView.hoveredFace() : null);

        // alça de redimensionar: três riscos na diagonal, acesos com o mouse em cima ou arrastando
        ResizeHandle.Edge hover = resizeHandle.dragging() || !popup
                ? resizeHandle.hover(mouseX, mouseY, x, y, w, h) : null;
        ResizeGrip.renderDotted(g, x, y, w, h, hover, trim);
    }

    /** Os slots de chunk loading (com a luz do estado) e de alcance, no canto do cabeçalho. */
    private void renderUpgradeSlots(GuiGraphics g, int x, int y) {
        int ux = x + upgradeBgX();
        int uy = y + UPGRADE_BG_Y;
        GuiPaint.slot(g, ux, uy);
        ChunkLoadState chunkLoad = chunkLoadState();
        if (chunkLoad == ChunkLoadState.NONE) {
            ghostItem(g, GHOST_UPGRADE, ux + 1, uy + 1);
        } else {
            int color = chunkLoadColor(chunkLoad);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(ux + 11, uy + 1, ux + 17, uy + 7, GuiPaint.RING);
            g.fill(ux + 12, uy + 2, ux + 16, uy + 6, color);
            g.fill(ux + 12, uy + 2, ux + 14, uy + 4, GuiPaint.mix(color, GuiPaint.BEVEL_LIGHT, 0.5f));
            g.pose().popPose();
        }
        int rangeX = x + rangeBgX();
        GuiPaint.slot(g, rangeX, uy);
        if (!menu.getSlot(RouterMenu.RANGE_SLOT).hasItem()) {
            ghostItem(g, GHOST_RANGE, rangeX + 1, uy + 1);
        }
    }

    /** O visor: num alvo remoto só o bloco dele, com o anel da face escolhida na cor da rede dela. */
    private void renderView(GuiGraphics g, RouterSnapshot s, int pointerX, int pointerY) {
        int vx = leftPos + viewX();
        int vy = topPos + boxY();
        int viewW = viewBoxW();
        int viewH = boxH();
        GuiPaint.paper(g, vx + 1, vy + 1, viewW - 2, viewH - 2);
        GuiPaint.outline(g, vx, vy, viewW, viewH, GuiPaint.LINE);
        Optional<UUID> id = s.network(type(), face());
        NetworkEntry selected = id.isEmpty() ? null : s.entry(id.get());
        machineView.render(g, s.machineState(), remote() ? null : s.facing(), s.tier(), faceViews, face(),
                panel.hoveredFace(pointerX, pointerY), selected == null ? -1 : selected.color() & 0xFFFFFF,
                pointerX, pointerY);
        Direction pointed = machineView.hoveredFace();
        if (s.machineState().isAir()) {
            GuiPaint.textCentered(g, font, tr("machine.none"), vx + viewW / 2, vy + viewH - 12, GuiPaint.MUTED);
        } else if (pointed != null) {
            Component label = tr("side." + sideKey(pointed));
            g.fill(vx + 1, vy + viewH - 13, vx + 7 + Math.min(font.width(label), viewW - 10), vy + viewH - 1,
                    GuiPaint.alpha(GuiPaint.INSET, 0xD8));
            GuiText.draw(g, font, label, vx + 4, vy + viewH - 11, viewW - 10, GuiPaint.FG);
        } else if (remote() && !remoteLoaded()) {
            RangeView.MachineView machine = snapshot().target().kind() == EditTarget.Kind.MACHINE
                    ? range().machine(snapshot().target().id()) : null;
            Component label = machine != null && machine.state() == RangeView.MachineState.MISSING
                    ? tr("range.access.missing") : tr("range.access.unloaded");
            g.fill(vx + 1, vy + viewH - 13, vx + 7 + Math.min(font.width(label), viewW - 10), vy + viewH - 1,
                    GuiPaint.alpha(GuiPaint.INSET, 0xD8));
            GuiText.draw(g, font, label, vx + 4, vy + viewH - 11, viewW - 10, GuiPaint.WARN);
        }
    }

    /** O lado relativo da face {@code direction} da tela, para o rótulo do visor. */
    private String sideKey(Direction direction) {
        return (remote() ? RemoteSide.fromCanonical(direction.ordinal())
                : RemoteSide.fromAbsolute(snapshot().attachedFront(), direction.ordinal())).key();
    }

    /**
     * Os slots de cartão da face escolhida e, ao lado, de qual face são; num tipo sem filtro, no lugar
     * deles, a vazão do tier. O texto só aparece se couber inteiro em duas linhas.
     */
    private void renderCards(GuiGraphics g, int x, int y) {
        int cy = y + cardsBgY();
        int textX = x + X0 + RouterMenu.CARD_SLOT_COUNT * 18 + 5;
        int room = x + X0 + lw() - textX;
        Component text;
        if (RouterFaceFilterTarget.hasFilter(type())) {
            for (int i = 0; i < RouterMenu.CARD_SLOT_COUNT; i++) {
                int sx = x + X0 + i * 18;
                GuiPaint.slot(g, sx, cy);
                if (!menu.getSlot(i).hasItem()) {
                    ghostItem(g, GHOST_CARD, sx + 1, cy + 1);
                }
            }
            text = tr("cards.label", tr("side." + sideKey(face())));
        } else {
            textX = x + X0;
            room = lw();
            long tierRate = Config.TIERS.get(snapshot().tier()).rate(type());
            text = tr("rate.tier", tierRate == 0 ? tr("rate.unlimited") : ResourceStyle.rate(type(), tierRate));
        }
        int lines = font.split(text, Math.max(1, room)).size();
        if (room > 0 && lines <= 2) {
            GuiText.wrap(g, font, text, textX, cy + (lines == 1 ? 5 : 0), room, 2, GuiPaint.MUTED);
        }
    }

    /** Botão Máquinas: seta (lista aberta ou fechada), o nome e as vagas usadas. */
    private void paintMachines(GuiGraphics g, FlatButton b, boolean hovered) {
        if (sideBySide()) {
            // lado a lado: só o título da lista, sem alternar
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
            GuiText.draw(g, font, b.getMessage(), b.getX() + 4, b.getY() + 2, b.getWidth() - 7, GuiPaint.FG);
            return;
        }
        int border = showList ? GuiPaint.SELECTED : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON, border);
        int color = hovered || showList ? GuiPaint.FG : GuiPaint.MUTED;
        if (showList) {
            GuiPaint.arrowDown(g, b.getX() + 4, b.getY() + 5, color);
        } else {
            GuiPaint.arrowRight(g, b.getX() + 5, b.getY() + 4, color);
        }
        GuiText.draw(g, font, b.getMessage(), b.getX() + 12, b.getY() + 2, b.getWidth() - 15, GuiPaint.FG);
    }

    /** Item apagado no fundo de um slot vazio (cartão ou upgrade). */
    private void ghostItem(GuiGraphics g, ItemStack ghost, int x, int y) {
        g.renderFakeItem(ghost, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.fill(x, y, x + 16, y + 16, GuiPaint.alpha(GuiPaint.INSET, 0xD8));
        g.pose().popPose();
    }

    private void paintTitle(GuiGraphics g, FlatButton b, boolean hovered) {
        int color = hovered ? GuiPaint.ACCENT : GuiPaint.FG;
        GuiText.draw(g, font, displayName(), b.getX(), b.getY() + 3, b.getWidth() - 2, color);
        if (hovered) {
            int width = Math.min(font.width(displayName()), b.getWidth() - 2);
            for (int i = 0; i < width; i += 2) {
                g.fill(b.getX() + i, b.getY() + 12, b.getX() + i + 1, b.getY() + 13, color);
            }
        }
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    void previewType(ResourceType type) {
        panel.select(type);
        refresh();
    }

    void previewFace(Direction face) {
        panel.selectFace(face);
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

    /** A disposição larga (o painel inteiro ao lado do visor) está valendo (gancho do e2e). */
    public boolean wideLayout() {
        return wide();
    }

    /** A face da máquina sob o mouse no visor (gancho do e2e). */
    @Nullable Direction viewHoveredFace() {
        return machineView.hoveredFace();
    }

    /** Largura da coluna do visor (gancho do e2e). */
    public int viewColumnWidth() {
        return lw();
    }

    void previewNetworkList(boolean open) {
        panel.previewNetworkList(open);
    }

    /** A lista de redes da linha Rede está aberta (gancho do e2e). */
    public boolean networkListShown() {
        return panel.networkListShown();
    }

    /** Centro da linha do alvo {@code target} na lista (aberta), ou {@code null} se fora da vista (gancho do e2e). */
    public int @Nullable [] rangeRowCenter(EditTarget target) {
        return listShown() ? rangeList.rowCenter(range(), target) : null;
    }

    /** Ponto da seta de um grupo na lista, que abre e fecha sem escolher (gancho do e2e). */
    public int @Nullable [] rangeArrowPoint(int group) {
        return listShown() ? rangeList.arrowPoint(range(), group) : null;
    }

    /** A lista das máquinas está na tela (gancho do e2e). */
    public boolean rangeListShown() {
        return listShown();
    }

    /** O visor está na tela (gancho do e2e); lado a lado, os dois estão. */
    public boolean rangeViewShown() {
        return rangeShown() && viewShown();
    }

    /** Centro da face no cubo do painel, para simular o mouse em cima na captura. */
    int[] previewFaceCenter(Direction direction) {
        return orNowhere(panel.faceCenter(direction));
    }

    /** Visor no ângulo inicial do snapshot atual. */
    void previewViewDefault() {
        machineView.previewReset(remote() ? null : snapshot().facing(), snapshot().machineState());
    }

    /** Visor num ângulo fixo (graus) e zoom. */
    void previewView(float yaw, float pitch, float zoom) {
        previewViewDefault();
        machineView.previewAngles(yaw, pitch, zoom);
    }

    /** Ponto da tela no centro da face da máquina no visor, para simular o mouse em cima. */
    int[] previewViewFaceCenter(Direction direction) {
        return machineView.previewProject(direction, remote() ? null : snapshot().facing(), snapshot().machineState());
    }

    int[] previewCardSlotCenter(int slot) {
        Slot s = menu.getSlot(slot);
        return new int[] {leftPos + s.x + 8, topPos + s.y + 8};
    }

    int[] previewUpgradeSlotCenter() {
        Slot s = menu.getSlot(RouterMenu.UPGRADE_SLOT);
        return new int[] {leftPos + s.x + 8, topPos + s.y + 8};
    }

    int[] previewRangeSlotCenter() {
        Slot s = menu.getSlot(RouterMenu.RANGE_SLOT);
        return new int[] {leftPos + s.x + 8, topPos + s.y + 8};
    }

    int[] previewEditFilterCenter() {
        return orNowhere(panel.editCenter());
    }

    int[] previewModeCenter(PortMode mode) {
        return orNowhere(panel.modeCenter(mode));
    }

    int[] previewTabCenter(ResourceType t) {
        return orNowhere(panel.tabCenter(t));
    }

    int[] previewNetworkCenter() {
        return orNowhere(panel.networkCenter());
    }

    private static int[] orNowhere(int @Nullable [] point) {
        return point == null ? new int[] {-1, -1} : point;
    }
}
