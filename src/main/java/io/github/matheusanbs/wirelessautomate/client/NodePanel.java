package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.OpenFilterPayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.RevertMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import io.github.matheusanbs.wirelessautomate.packet.UnlinkMachinePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * O painel do nó (especificação, "Painel do nó"): as abas por tipo com o resumo "N redes", a linha
 * Editando (com o Upgrade de Alcance), as faces desdobradas como um cubo ({@link FaceNet}) e, da face
 * escolhida, o que ela acessa, os quatro modos, a frase do modo, a Rede, o Filtro com Editar, a
 * prioridade e o redstone. O mesmo componente na tela do roteador e na Lista do Tablet.
 *
 * <p>Tudo é desenhado e respondido por geometria, sem widget por controle: a tela que hospeda chama
 * {@link #render}, {@link #renderOverlay} (as listas abertas e o aviso), {@link #mouseClicked},
 * {@link #mouseScrolled}, {@link #keyPressed} e {@link #tooltip}. As ações vão pelos payloads de face de
 * sempre, com o {@code containerId} da tela ({@link Actions}); o servidor resolve o roteador e o alvo
 * pelo menu aberto.
 *
 * <p>As faces são as da tela: absolutas na máquina presa (o lado relativo vem da frente dela,
 * {@link RouterSnapshot#attachedFront()}) e no quadro canônico (Frente = Norte) num grupo ou numa
 * máquina do alcance, como nos payloads.
 *
 * <p>Duas formas de pôr na tela: {@link #layout(int, int, int, int)} empilha abas, cubo e o resto num
 * retângulo; {@link #layoutSplit} dá um retângulo a cada parte (a tela do roteador estreita põe o cubo
 * embaixo do visor e as abas na largura toda).
 */
public final class NodePanel {
    /** O que a tela que hospeda o painel oferece a ele. */
    public interface Actions {
        int containerId();

        /** Manda a ação ao servidor (ou, numa captura sem servidor, aplica no snapshot local). */
        void send(CustomPacketPayload payload);

        /** Cartões nos slots da face escolhida, ou {@code -1} se a tela não tem os slots. */
        default int cardCount() {
            return -1;
        }

        /** O Desfazer de um aviso de colar. */
        default void undoPaste() {
        }
    }

    private static final int TAB_H = 15;
    /** Da linha das abas ao que vem embaixo dela: a linha separadora fica no meio. */
    private static final int TAB_BELOW = 5;
    private static final int NET_GAP = 2;
    /** Altura da célula do cubo empilhado. */
    private static final int CELL_H = 16;
    private static final int ROW_H = 12;
    private static final int BUTTON_H = 14;
    private static final int GAP = 2;
    private static final int MODE_GAP = 2;
    /** O ícone do modo (a 3/4) e a folga até o nome. */
    private static final int MODE_TEXT_X = 16;
    private static final int PRIO_BUTTON_W = 12;
    private static final int PRIO_VALUE_W = 22;
    private static final int EDIT_PAD = 5;
    private static final int TARGET_PAD = 3;
    private static final int DROPDOWN_ROW = 12;
    private static final int DROPDOWN_MAX_ROWS = 8;
    private static final int NOTICE_H = 13;
    private static final PortMode[] MODES = {PortMode.EXTRACT, PortMode.INSERT, PortMode.BOTH, PortMode.NONE};

    private enum Kind { TAB, SUMMARY, FACE, TARGET, REVERT, UNLINK, MODE, NETWORK, FILTER, EDIT, PRIO_MINUS, PRIO_PLUS,
        REDSTONE, UNDO }

    private enum Popup { NONE, NETWORK, REDSTONE, TARGET }

    /** Uma área que responde ao mouse. */
    private record Hot(Kind kind, int x, int y, int w, int h, int arg) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int[] center() {
            return new int[] {x + w / 2, y + h / 2};
        }
    }

    /** Uma linha da lista da linha Editando. */
    private record TargetRow(EditTarget target, Component label, @Nullable Component right, int rightColor,
            boolean indent) {
    }

    private final Actions actions;
    private RouterSnapshot snapshot;
    private ResourceType type = ResourceType.ITEM;
    private Direction face;
    private EditTarget shownTarget;
    private @Nullable Component readOnly;
    private Popup popup = Popup.NONE;
    private int popupScroll;
    private TabLayout.Mode tabMode = TabLayout.Mode.FULL;

    // retângulos: largura 0 = a parte não aparece
    private int tabsX;
    private int tabsY;
    private int tabsW;
    private int netX;
    private int netY;
    private int netW;
    private int netH;
    private int bodyX;
    private int bodyY;
    private int bodyW;
    private int bodyH;

    // o que arrange() calcula a cada quadro
    private final List<Hot> hots = new ArrayList<>();
    private int cellW;
    private int cellH;
    private int reasonY;
    private int reasonLines;
    private int targetY = -1;
    private int faceY;
    private boolean modeNames;
    private int phraseY = -1;
    private int phraseLines;
    private int networkY;
    private int filterY;
    private int prioY;
    private boolean prioLabel;
    private Component revertLabel = Component.empty();
    private Component redstoneLabel = Component.empty();

    public NodePanel(Actions actions, RouterSnapshot snapshot, Direction face) {
        this.actions = actions;
        this.snapshot = snapshot;
        this.face = face;
        this.shownTarget = snapshot.target();
        if (!LoadedTypes.LIST.isEmpty()) {
            this.type = LoadedTypes.LIST.getFirst();
        }
    }

    // ------------------------------------------------------------------ estado

    /**
     * O snapshot atual (a tela chama a cada quadro). Se o alvo de edição mudou, a face passa à que o
     * servidor escolheu para ele, a lista aberta fecha e o aviso some (o Desfazer agiria no alvo novo).
     */
    public void setSnapshot(RouterSnapshot snapshot) {
        this.snapshot = snapshot;
        if (!snapshot.target().equals(shownTarget)) {
            shownTarget = snapshot.target();
            face = snapshot.range().present() ? snapshot.range().face() : snapshot.facing();
            popup = Popup.NONE;
            PanelNotices.clear();
        }
    }

    public RouterSnapshot snapshot() {
        return snapshot;
    }

    public ResourceType type() {
        return type;
    }

    /** A face escolhida, no quadro da tela (absoluta na presa, canônica num alvo do alcance). */
    public Direction face() {
        return face;
    }

    public void select(ResourceType type) {
        this.type = type;
        popup = Popup.NONE;
    }

    public void selectFace(Direction face) {
        this.face = face;
        popup = Popup.NONE;
    }

    /** Só leitura, com o motivo em cima (o Tablet longe do nó); {@code null} volta a editar. */
    public void readOnly(@Nullable Component reason) {
        this.readOnly = reason;
        if (reason != null) {
            popup = Popup.NONE;
        }
    }

    public TabLayout.Mode tabMode() {
        return tabMode;
    }

    /** Alguma lista (rede, redstone ou alvo) está aberta. */
    public boolean popupOpen() {
        return popup != Popup.NONE;
    }

    public boolean networkListShown() {
        return popup == Popup.NETWORK;
    }

    private FaceView view() {
        return view(face);
    }

    private FaceView view(Direction direction) {
        return snapshot.face(type, direction);
    }

    private RangeView range() {
        return snapshot.range();
    }

    private boolean rangeShown() {
        return range().present();
    }

    /** O alvo é um grupo ou uma máquina do alcance (faces no quadro canônico). */
    private boolean remote() {
        return rangeShown() && !snapshot.target().attached();
    }

    private RangeView.@Nullable MachineView targetMachine() {
        EditTarget target = snapshot.target();
        return rangeShown() && target.kind() == EditTarget.Kind.MACHINE ? range().machine(target.id()) : null;
    }

    private RangeView.@Nullable GroupView targetGroup() {
        EditTarget target = snapshot.target();
        if (!rangeShown()) {
            return null;
        }
        return switch (target.kind()) {
            case ATTACHED -> null;
            case GROUP -> range().group(target.id());
            case MACHINE -> {
                RangeView.MachineView machine = range().machine(target.id());
                yield machine == null ? null : range().group(machine.group());
            }
        };
    }

    /** Há uma máquina carregada no alvo do alcance (a escolhida ou alguma do grupo). */
    private boolean remoteLoaded() {
        RangeView.MachineView machine = targetMachine();
        if (machine != null) {
            return machine.state() == RangeView.MachineState.LOADED;
        }
        RangeView.GroupView group = targetGroup();
        if (group == null) {
            return true;
        }
        for (RangeView.MachineView m : range().machines()) {
            if (m.group() == group.id() && m.state() == RangeView.MachineState.LOADED) {
                return true;
            }
        }
        return false;
    }

    /** A face pode ser configurada: tem acesso, ou é de um alvo do alcance (o perfil vale para várias). */
    private boolean editable(FaceView view) {
        return readOnly == null && (view.available() || remote());
    }

    /** O lado relativo à frente da máquina da face da tela {@code direction}. */
    private RemoteSide sideOf(Direction direction) {
        return remote() ? RemoteSide.fromCanonical(direction.ordinal())
                : RemoteSide.fromAbsolute(snapshot.attachedFront(), direction.ordinal());
    }

    /** A face da tela do lado {@code side}: absoluta na presa, canônica num alvo do alcance. */
    private Direction faceOf(RemoteSide side) {
        return Direction.values()[remote() ? side.canonical() : side.toAbsolute(snapshot.attachedFront())];
    }

    /** A face onde o roteador está preso (só na máquina presa). */
    private boolean attached(Direction direction) {
        return !remote() && direction == snapshot.facing();
    }

    private @Nullable NetworkEntry network(Direction direction) {
        Optional<UUID> id = snapshot.network(type, direction);
        return id.isEmpty() ? null : snapshot.entry(id.get());
    }

    private Component networkLabel(Direction direction) {
        NetworkEntry entry = network(direction);
        if (entry != null) {
            return Component.literal(entry.name());
        }
        return snapshot.network(type, direction).isPresent() ? tr("network.unknown") : tr("network.none");
    }

    /** A face tem modo e está sem rede: a linha Rede pede a escolha (a regra D1 não achou rede ativa). */
    private boolean needsNetwork(FaceView view) {
        return view.mode() != PortMode.NONE && view.network().isEmpty() && (view.available() || remote());
    }

    private Component networkSummary() {
        int count = snapshot.networks(type).size();
        if (count == 0) {
            return tr("network.none");
        }
        return count == 1 ? tr("networks.count.one") : tr("networks.count", count);
    }

    /** Dica do resumo: cada face com rede, "Cima: Mina · Baixo: Armazém", na ordem dos quadradinhos. */
    private Component networkSummaryTooltip() {
        Component faces = Component.empty();
        boolean first = true;
        for (RemoteSide side : FaceNet.CHIP_ORDER) {
            Direction f = faceOf(side);
            if (snapshot.network(type, f).isEmpty()) {
                continue;
            }
            if (!first) {
                faces = faces.copy().append(" · ");
            }
            faces = faces.copy().append(sideName(f)).append(": ").append(networkLabel(f));
            first = false;
        }
        Component head = tr("networks.tooltip", ResourceStyle.name(type));
        Component body = first ? tr("network.none") : faces;
        return head.copy().append("\n").append(body.copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    private static Component panelTr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.panel." + key, args);
    }

    private Component sideName(Direction direction) {
        return tr("side." + sideOf(direction).key());
    }

    private static Component sideName(RemoteSide side) {
        return tr("side." + side.key());
    }

    private static Component modeName(PortMode mode) {
        return tr("mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private static Component redstoneName(RedstoneMode mode) {
        return tr("redstone." + mode.name().toLowerCase(Locale.ROOT));
    }

    /** O que a face acessa: "27 slots", "1 tanque", "energia" ou "sem acesso". */
    private Component access(FaceView view) {
        if (!view.available() && remote() && !remoteLoaded()) {
            RangeView.MachineView machine = targetMachine();
            return machine != null && machine.state() == RangeView.MachineState.MISSING
                    ? tr("range.access.missing") : tr("range.access.unloaded");
        }
        if (!view.available()) {
            return tr("access.none");
        }
        return ResourceStyle.access(type, view.slots());
    }

    private boolean hasFilter() {
        return RouterFaceFilterTarget.hasFilter(type);
    }

    /** "Passa tudo", "12 entradas", "2 cartões" ou "12 entradas · 2 cartões" (cartões só com os slots). */
    private Component filterSummary(FaceView view) {
        int cards = Math.max(0, actions.cardCount());
        Component entries = view.filterSize() == 1 ? tr("filter.count.one") : tr("filter.count", view.filterSize());
        if (cards == 0) {
            return view.filterSize() == 0 ? tr("filter.none") : entries;
        }
        Component cardText = cards == 1 ? tr("filter.cards.one") : tr("filter.cards", cards);
        return view.filterSize() == 0 ? cardText : tr("filter.summary", entries, cardText);
    }

    /** O nome do alvo de edição: a máquina presa, o grupo ("Fornalha (todas)") ou a máquina ("Fornalha #3"). */
    private Component targetName(EditTarget target) {
        return switch (target.kind()) {
            case ATTACHED -> panelTr("target.attached");
            case GROUP -> {
                RangeView.GroupView group = range().group(target.id());
                yield panelTr("target.group", group == null ? Component.literal("?") : RangeList.groupName(group));
            }
            case MACHINE -> RangeList.machineName(range(), target.id());
        };
    }

    // ------------------------------------------------------------------ layout

    /** Empilha abas, cubo e o resto no retângulo. */
    public void layout(int x, int y, int w, int h) {
        tabsX = x;
        tabsY = y;
        tabsW = w;
        netX = x;
        netY = y + TAB_H + TAB_BELOW;
        netW = w;
        netH = 3 * CELL_H + 2 * NET_GAP;
        bodyX = x;
        bodyY = netY + netH + 5;
        bodyW = w;
        bodyH = y + h - bodyY;
    }

    /** Cada parte no seu retângulo: a linha das abas, o cubo e o resto. */
    public void layoutSplit(int tabsX, int tabsY, int tabsW, int netX, int netY, int netW, int netH, int bodyX,
            int bodyY, int bodyW, int bodyH) {
        this.tabsX = tabsX;
        this.tabsY = tabsY;
        this.tabsW = tabsW;
        this.netX = netX;
        this.netY = netY;
        this.netW = netW;
        this.netH = netH;
        this.bodyX = bodyX;
        this.bodyY = bodyY;
        this.bodyW = bodyW;
        this.bodyH = bodyH;
    }

    /** Altura do cubo empilhado ({@link #layout(int, int, int, int)}), para a tela calcular o resto. */
    public static int stackedNetHeight() {
        return 3 * CELL_H + 2 * NET_GAP;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Posições e áreas de clique do quadro atual, pelo snapshot, pela aba e pelo espaço. */
    private void arrange() {
        Font font = font();
        hots.clear();
        arrangeTabs(font);
        arrangeNet();
        arrangeBody(font);
    }

    private void arrangeTabs(Font font) {
        if (tabsW <= 0) {
            return;
        }
        List<ResourceType> types = LoadedTypes.LIST;
        Component summary = networkSummary();
        int summaryW = font.width(summary) + 2;
        // o resumo "N redes" sai quando nem as abas só com ícone cabem ao lado dele
        int icons = types.size() * TabLayout.ICON_TAB + Math.max(0, types.size() - 1) * TabLayout.GAP;
        boolean showSummary = tabsW - summaryW - 6 >= icons;
        if (showSummary) {
            hots.add(new Hot(Kind.SUMMARY, tabsX + tabsW - summaryW, tabsY + 1, summaryW, 13, 0));
        }
        int[] full = new int[types.size()];
        int active = 0;
        for (int i = 0; i < types.size(); i++) {
            full[i] = 4 + ResourceStyle.ICON + 3 + font.width(ResourceStyle.name(types.get(i))) + 6;
            if (types.get(i) == type) {
                active = i;
            }
        }
        TabLayout.Result layout = TabLayout.choose(full, active, showSummary ? tabsW - summaryW - 6 : tabsW);
        tabMode = layout.mode();
        int x = tabsX;
        for (int i = 0; i < types.size(); i++) {
            hots.add(new Hot(Kind.TAB, x, tabsY, layout.widths()[i], TAB_H, types.get(i).ordinal()));
            x += layout.widths()[i] + TabLayout.GAP;
        }
    }

    private void arrangeNet() {
        if (netW <= 0) {
            return;
        }
        cellW = (netW - (FaceNet.COLS - 1) * NET_GAP) / FaceNet.COLS;
        cellH = (netH - (FaceNet.ROWS - 1) * NET_GAP) / FaceNet.ROWS;
        for (RemoteSide side : RemoteSide.values()) {
            int[] cell = FaceNet.cell(side);
            hots.add(new Hot(Kind.FACE, netX + cell[0] * (cellW + NET_GAP), netY + cell[1] * (cellH + NET_GAP), cellW,
                    cellH, side.ordinal()));
        }
    }

    private void arrangeBody(Font font) {
        if (bodyW <= 0) {
            return;
        }
        int x = bodyX;
        int w = bodyW;
        int y = bodyY;
        int bottom = bodyY + bodyH;
        reasonLines = readOnly == null ? 0 : GuiText.lineCount(font, readOnly, w, 2);
        reasonY = y;
        if (reasonLines > 0) {
            y += reasonLines * 10 + 3;
        }

        targetY = -1;
        if (rangeShown()) {
            targetY = y;
            int labelW = font.width(panelTr("target")) + 5;
            hots.add(new Hot(Kind.TARGET, x + labelW, y, w - labelW, ROW_H, 0));
            y += ROW_H + GAP;
            RangeView.MachineView machine = targetMachine();
            if (machine != null) {
                arrangeTargetButtons(font, x, y, w, machine.exception());
                y += ROW_H + GAP;
            }
        }
        faceY = y;
        y += 10 + GAP;

        // o que fica sempre: Rede, Filtro e a linha da prioridade com o redstone
        int fixed = ROW_H + GAP + BUTTON_H + GAP + BUTTON_H;
        int free = bottom - y - fixed;
        int maxName = 0;
        for (PortMode mode : MODES) {
            maxName = Math.max(maxName, font.width(modeName(mode)));
        }
        int oneRow = (w - 3 * MODE_GAP) / 4;
        int twoRows = (w - MODE_GAP) / 2;
        int modeRows;
        if (oneRow >= MODE_TEXT_X + maxName + 2) {
            modeRows = 1;
            modeNames = true;
        } else if (twoRows >= MODE_TEXT_X + maxName + 2 && free >= 2 * BUTTON_H + MODE_GAP + GAP) {
            modeRows = 2;
            modeNames = true;
        } else {
            modeRows = 1;
            modeNames = false;
        }
        int cell = modeRows == 1 ? oneRow : twoRows;
        int perRow = modeRows == 1 ? 4 : 2;
        for (int i = 0; i < MODES.length; i++) {
            hots.add(new Hot(Kind.MODE, x + (i % perRow) * (cell + MODE_GAP), y + (i / perRow) * (BUTTON_H + MODE_GAP),
                    cell, BUTTON_H, MODES[i].ordinal()));
        }
        int modesH = modeRows * BUTTON_H + (modeRows - 1) * MODE_GAP;
        y += modesH + GAP;
        free -= modesH + GAP;

        // a frase do modo, inteira, só se couber
        List<?> lines = font.split(phrase(), w);
        phraseY = -1;
        phraseLines = 0;
        if (lines.size() <= 2 && free >= lines.size() * 10 + 1) {
            phraseY = y;
            phraseLines = lines.size();
            y += phraseLines * 10 + 1;
        }

        networkY = y;
        int netLabelW = font.width(tr("network.face")) + 5;
        hots.add(new Hot(Kind.NETWORK, x + netLabelW, y, w - netLabelW, ROW_H, 0));
        y += ROW_H + GAP;

        filterY = y;
        if (hasFilter()) {
            Component edit = tr("filter.edit");
            int editW = font.width(edit) + 2 * EDIT_PAD;
            hots.add(new Hot(Kind.EDIT, x + w - editW, y, editW, BUTTON_H, 0));
            int labelW = font.width(tr("filter.label")) + 5;
            hots.add(new Hot(Kind.FILTER, x + labelW, y, w - labelW - editW - 3, BUTTON_H, 0));
        }
        y += BUTTON_H + GAP;

        prioY = y;
        int labelW = font.width(panelTr("priority")) + 4;
        int controlsW = 2 * PRIO_BUTTON_W + PRIO_VALUE_W + 2;
        int minRedstone = font.width(redstoneName(RedstoneMode.HIGH)) + 16;
        prioLabel = w - labelW - controlsW - 4 >= minRedstone;
        int px = x + (prioLabel ? labelW : 0);
        hots.add(new Hot(Kind.PRIO_MINUS, px, y, PRIO_BUTTON_W, BUTTON_H, 0));
        hots.add(new Hot(Kind.PRIO_PLUS, px + PRIO_BUTTON_W + PRIO_VALUE_W + 2, y, PRIO_BUTTON_W, BUTTON_H, 0));
        int rx = px + controlsW + 4;
        int rw = x + w - rx;
        hots.add(new Hot(Kind.REDSTONE, rx, y, rw, BUTTON_H, 0));
        Component full = tr("redstone.narration", redstoneName(view().redstone()));
        redstoneLabel = font.width(full) <= rw - 16 ? full : redstoneName(view().redstone());

        PanelNoticePayload notice = PanelNotices.current();
        if (notice != null && notice.undo() != PanelNoticePayload.Undo.NONE) {
            Component undo = panelTr("notice.undo");
            int undoW = font.width(undo) + 8;
            hots.add(new Hot(Kind.UNDO, x + w - undoW, noticeY(), undoW, NOTICE_H, 0));
        }
    }

    /**
     * Voltar a igual às outras (só na exceção) e Desvincular, lado a lado: o texto da especificação, ou o
     * curto se os dois não couberem; se nem assim, o Desvincular fica com até metade.
     */
    private void arrangeTargetButtons(Font font, int x, int y, int w, boolean exception) {
        Component unlink = tr("range.unlink");
        int unlinkW = font.width(unlink) + 2 * TARGET_PAD;
        int bx = x;
        if (exception) {
            revertLabel = panelTr("target.revert");
            int revertW = font.width(revertLabel) + 2 * TARGET_PAD;
            if (revertW + GAP + unlinkW > w) {
                revertLabel = tr("range.revert");
                revertW = font.width(revertLabel) + 2 * TARGET_PAD;
            }
            if (revertW + GAP + unlinkW > w) {
                unlinkW = Math.min(unlinkW, (w - GAP) / 2);
                revertW = w - GAP - unlinkW;
            }
            hots.add(new Hot(Kind.REVERT, bx, y, revertW, ROW_H, 0));
            bx += revertW + GAP;
        }
        hots.add(new Hot(Kind.UNLINK, bx, y, Math.min(unlinkW, w), ROW_H, 0));
    }

    private int noticeY() {
        return bodyY + bodyH - NOTICE_H;
    }

    /** A frase do modo da face escolhida, nesta máquina (ou por que não dá para escolher). */
    private Component phrase() {
        FaceView v = view();
        if (readOnly == null && !editable(v)) {
            return tr("mode.unavailable");
        }
        return tr("mode." + v.mode().name().toLowerCase(Locale.ROOT) + ".tooltip");
    }

    private @Nullable Hot hot(Kind kind) {
        for (Hot hot : hots) {
            if (hot.kind() == kind) {
                return hot;
            }
        }
        return null;
    }

    private @Nullable Hot hot(Kind kind, int arg) {
        for (Hot hot : hots) {
            if (hot.kind() == kind && hot.arg() == arg) {
                return hot;
            }
        }
        return null;
    }

    private @Nullable Hot hotAt(double mx, double my) {
        // o aviso fica por cima do que estiver embaixo dele
        Hot undo = hot(Kind.UNDO);
        if (undo != null && undo.contains(mx, my)) {
            return undo;
        }
        if (netW > 0) {
            RemoteSide side = FaceNet.sideAt((int) Math.floor(mx) - netX, (int) Math.floor(my) - netY, cellW, cellH,
                    NET_GAP);
            if (side != null) {
                return hot(Kind.FACE, side.ordinal());
            }
        }
        for (Hot hot : hots) {
            if (hot.kind() != Kind.FACE && hot.contains(mx, my)) {
                return hot;
            }
        }
        return null;
    }

    /** O ponto está na faixa do aviso do painel (que fica por cima do que estiver embaixo dela). */
    private boolean inNotice(double mx, double my) {
        return bodyW > 0 && PanelNotices.current() != null && mx >= bodyX && mx < bodyX + bodyW
                && my >= noticeY() && my < noticeY() + NOTICE_H;
    }

    /** A face do cubo sob o mouse (para o visor realçar a mesma), ou {@code null}. */
    public @Nullable Direction hoveredFace(double mx, double my) {
        if (popup != Popup.NONE) {
            return null;
        }
        arrange();
        Hot hot = hotAt(mx, my);
        return hot != null && hot.kind() == Kind.FACE ? faceOf(RemoteSide.values()[hot.arg()]) : null;
    }

    // ------------------------------------------------------------------ ações

    private void sendFace(PortMode mode, int priority, RedstoneMode redstone) {
        if (!editable(view())) {
            return;
        }
        actions.send(new SetFacePayload(actions.containerId(), type, face, mode, priority, redstone));
    }

    private void changePriority(int direction) {
        FaceView v = view();
        int step = Screen.hasShiftDown() ? 10 : 1;
        int value = Math.max(RouterMenu.MIN_PRIORITY, Math.min(RouterMenu.MAX_PRIORITY, v.priority() + direction * step));
        if (value != v.priority()) {
            sendFace(v.mode(), value, v.redstone());
        }
    }

    /**
     * Põe a face na rede {@code network} (vazio = sem rede). "Sem rede" vai sempre ao servidor: uma face
     * numa rede apagada chega como vazia no snapshot, mas guarda o UUID, e só assim sai dela.
     */
    private void chooseNetwork(Optional<UUID> network) {
        popup = Popup.NONE;
        if (network.isPresent() && network.equals(snapshot.network(type, face))) {
            return;
        }
        actions.send(new SetNetworkPayload(actions.containerId(), type, face, network));
    }

    private void chooseTarget(EditTarget target) {
        popup = Popup.NONE;
        if (target.equals(snapshot.target())) {
            return;
        }
        int id = actions.containerId();
        switch (target.kind()) {
            case ATTACHED -> actions.send(SelectTargetPayload.attached(id));
            case GROUP -> {
                RangeView.GroupView group = range().group(target.id());
                if (group != null) {
                    actions.send(SelectTargetPayload.group(id, group.id(), group.block()));
                }
            }
            case MACHINE -> {
                RangeView.MachineView machine = range().machine(target.id());
                if (machine != null) {
                    actions.send(SelectTargetPayload.machine(id, target.id(), machine.pos()));
                }
            }
        }
    }

    private void undo(PanelNoticePayload notice) {
        PanelNotices.clear();
        switch (notice.undo()) {
            case CLEAR_FACE_NETWORK -> actions.send(new SetNetworkPayload(notice.containerId(), notice.resource(),
                    notice.face(), Optional.empty()));
            case UNDO_PASTE -> actions.undoPaste();
            case NONE -> {
            }
        }
    }

    // ------------------------------------------------------------------ entrada

    public boolean mouseClicked(double mx, double my, int button) {
        arrange();
        if (popup != Popup.NONE) {
            clickPopup(mx, my);
            return true;
        }
        if (inNotice(mx, my)) {
            // a faixa inteira do aviso engole o clique; só o Desfazer age
            Hot undo = hot(Kind.UNDO);
            PanelNoticePayload notice = PanelNotices.current();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && undo != null && undo.contains(mx, my) && notice != null) {
                undo(notice);
            }
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Hot hot = hotAt(mx, my);
        if (hot == null) {
            return false;
        }
        FaceView v = view();
        RangeView.MachineView machine = targetMachine();
        boolean edit = readOnly == null;
        switch (hot.kind()) {
            case TAB -> select(ResourceType.values()[hot.arg()]);
            case FACE -> selectFace(faceOf(RemoteSide.values()[hot.arg()]));
            case MODE -> sendFace(PortMode.values()[hot.arg()], v.priority(), v.redstone());
            case NETWORK -> {
                if (edit) {
                    popup = Popup.NETWORK;
                    popupScroll = 0;
                }
            }
            case EDIT -> {
                if (edit && hasFilter()) {
                    actions.send(new OpenFilterPayload(actions.containerId(), type, face));
                }
            }
            case PRIO_MINUS -> changePriority(-1);
            case PRIO_PLUS -> changePriority(1);
            case REDSTONE -> {
                if (editable(v)) {
                    popup = Popup.REDSTONE;
                    popupScroll = 0;
                }
            }
            case TARGET -> {
                if (edit) {
                    popup = Popup.TARGET;
                    popupScroll = Math.max(0, Math.min(targetRows().indexOf(currentTargetRow()),
                            targetRows().size() - DROPDOWN_MAX_ROWS));
                }
            }
            case REVERT -> {
                if (edit && machine != null) {
                    actions.send(new RevertMachinePayload(actions.containerId(), snapshot.target().id(), machine.pos()));
                }
            }
            case UNLINK -> {
                if (edit && machine != null) {
                    actions.send(new UnlinkMachinePayload(actions.containerId(), snapshot.target().id(), machine.pos()));
                }
            }
            case UNDO -> {
                PanelNoticePayload notice = PanelNotices.current();
                if (notice != null) {
                    undo(notice);
                }
            }
            case SUMMARY, FILTER -> {
            }
        }
        return true;
    }

    private void clickPopup(double mx, double my) {
        int row = popupRowAt(mx, my);
        if (row < 0) {
            if (!popupContains(mx, my)) {
                popup = Popup.NONE;
            }
            return;
        }
        switch (popup) {
            case NETWORK -> {
                List<NetworkEntry> networks = snapshot.networks();
                // uma rede que o jogador não pode usar fica na lista só para mostrar o nome: sem clique
                if (row >= networks.size() || networks.get(row).usable()) {
                    chooseNetwork(row < networks.size() ? Optional.of(networks.get(row).id()) : Optional.empty());
                }
            }
            case REDSTONE -> {
                popup = Popup.NONE;
                FaceView v = view();
                RedstoneMode mode = RedstoneMode.values()[row];
                if (mode != v.redstone()) {
                    sendFace(v.mode(), v.priority(), mode);
                }
            }
            case TARGET -> chooseTarget(targetRows().get(row).target());
            case NONE -> {
            }
        }
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (popup == Popup.NONE) {
            return false;
        }
        arrange();
        if (popupContains(mx, my)) {
            popupScroll = Math.max(0, Math.min(popupScroll - (int) Math.signum(scrollY),
                    popupCount() - popupRows()));
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (popup != Popup.NONE && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            popup = Popup.NONE;
            return true;
        }
        return false;
    }

    /** A dica do ponto (controles e faces), ou {@code null}; o texto abreviado fica com a tela. */
    public @Nullable Component tooltip(double mx, double my) {
        if (popup != Popup.NONE) {
            return null;
        }
        arrange();
        if (inNotice(mx, my)) {
            return null;
        }
        Hot hot = hotAt(mx, my);
        if (hot == null) {
            return null;
        }
        FaceView v = view();
        Component disabled = readOnly != null ? readOnly : tr("mode.unavailable");
        return switch (hot.kind()) {
            case TAB -> tr("tab.tooltip", ResourceStyle.name(ResourceType.values()[hot.arg()]));
            case SUMMARY -> networkSummaryTooltip();
            case FACE -> faceTooltip(faceOf(RemoteSide.values()[hot.arg()]));
            case MODE -> editable(v)
                    ? tr("mode." + PortMode.values()[hot.arg()].name().toLowerCase(Locale.ROOT) + ".tooltip")
                    : disabled;
            case NETWORK -> readOnly != null ? readOnly : networkTooltip(v);
            case FILTER -> v.filterSize() == 0 ? null : tr(v.blacklist() ? "filter.black" : "filter.white");
            case EDIT -> readOnly != null ? readOnly : tr("filter.edit.tooltip");
            case PRIO_MINUS, PRIO_PLUS -> editable(v) ? tr("priority.tooltip") : disabled;
            case REDSTONE -> editable(v)
                    ? tr("redstone.narration", redstoneName(v.redstone())).copy().append("\n")
                            .append(panelTr("redstone.hint").copy().withColor(GuiPaint.TOOLTIP_MUTED))
                    : disabled;
            case TARGET -> readOnly != null ? readOnly : targetTooltip();
            case REVERT -> readOnly != null ? readOnly : tr("range.revert.tooltip");
            case UNLINK -> readOnly != null ? readOnly : tr("range.unlink.tooltip");
            case UNDO -> null;
        };
    }

    /** Dica de uma face: o lado (e, na presa, o absoluto), o modo e o acesso, a rede e o roteador preso. */
    private Component faceTooltip(Direction direction) {
        FaceView v = view(direction);
        Component head = remote() ? sideName(direction)
                : sideName(direction).copy().append(" · ").append(tr("face." + direction.getName()));
        Component line = v.available() || remote()
                ? modeName(v.mode()).copy().append(" · ").append(access(v))
                : access(v);
        NetworkEntry entry = network(direction);
        Component net = entry != null ? tr("network.face.line", entry.name())
                : v.network().isPresent() ? tr("network.unknown") : tr("network.none");
        Component text = head.copy().append("\n").append(line).append("\n")
                .append(net.copy().withColor(GuiPaint.TOOLTIP_MUTED));
        if (attached(direction)) {
            text = text.copy().append("\n").append(tr("face.attached.tooltip"));
        }
        return text;
    }

    private Component networkTooltip(FaceView v) {
        Component text = tr("network.face.tooltip", sideName(face), ResourceStyle.name(type)).copy()
                .append("\n").append(networkLabel(face));
        if (needsNetwork(v)) {
            text = text.copy().append("\n").append(panelTr("network.no_active").copy().withColor(GuiPaint.WARN));
        }
        return text.copy().append("\n").append(tr("network.face.hint").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    private Component targetTooltip() {
        RangeView.MachineView machine = targetMachine();
        Component scope;
        if (machine != null) {
            Component state = machine.exception() ? tr("range.machine.exception") : tr("range.machine.follows");
            scope = tr("range.scope.machine", RangeList.machineName(range(), snapshot.target().id()), state);
        } else {
            RangeView.GroupView group = targetGroup();
            scope = group == null ? panelTr("target.attached")
                    : tr("range.scope.group", RangeList.groupName(group), group.count() - group.exceptions(), group.count());
        }
        return scope.copy().append("\n").append(panelTr("target.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    // ------------------------------------------------------------------ listas abertas

    private List<TargetRow> targetRows() {
        List<TargetRow> rows = new ArrayList<>();
        rows.add(new TargetRow(EditTarget.ATTACHED_TARGET, panelTr("target.attached"), null, 0, false));
        RangeView view = range();
        for (RangeView.GroupView group : view.groups()) {
            rows.add(new TargetRow(EditTarget.group(group.id()), panelTr("target.group", RangeList.groupName(group)),
                    Component.literal(Integer.toString(group.count())), GuiPaint.MUTED, false));
            for (int i = 0; i < view.machines().size(); i++) {
                RangeView.MachineView machine = view.machines().get(i);
                if (machine.group() == group.id()) {
                    rows.add(new TargetRow(EditTarget.machine(i), RangeList.machineName(view, i),
                            machine.exception() ? panelTr("target.exception") : null, GuiPaint.WARN, true));
                }
            }
        }
        return rows;
    }

    private @Nullable TargetRow currentTargetRow() {
        for (TargetRow row : targetRows()) {
            if (row.target().equals(snapshot.target())) {
                return row;
            }
        }
        return null;
    }

    /** Linhas da lista aberta (sem o título). */
    private int popupCount() {
        return switch (popup) {
            case NETWORK -> snapshot.networks().size() + 1;
            case REDSTONE -> RedstoneMode.values().length;
            case TARGET -> targetRows().size();
            case NONE -> 0;
        };
    }

    private int popupRows() {
        return Math.min(DROPDOWN_MAX_ROWS, popupCount());
    }

    /** A lista da linha Rede tem um título; as outras não. */
    private boolean popupTitled() {
        return popup == Popup.NETWORK;
    }

    private @Nullable Hot popupAnchor() {
        return switch (popup) {
            case NETWORK -> hot(Kind.NETWORK);
            case REDSTONE -> hot(Kind.REDSTONE);
            case TARGET -> hot(Kind.TARGET);
            case NONE -> null;
        };
    }

    private Component networkListTitle() {
        return tr("network.list.title", sideName(face), ResourceStyle.name(type));
    }

    private int popupWidth() {
        Hot anchor = popupAnchor();
        if (anchor == null) {
            return 0;
        }
        Font font = font();
        int width = anchor.w();
        switch (popup) {
            case NETWORK -> {
                for (NetworkEntry entry : snapshot.networks()) {
                    width = Math.max(width, font.width(entry.name()) + 24);
                }
                width = Math.max(width, font.width(tr("network.none")) + 24);
                width = Math.max(width, font.width(networkListTitle()) + 12);
            }
            case TARGET -> {
                for (TargetRow row : targetRows()) {
                    int right = row.right() == null ? 0 : font.width(row.right()) + 6;
                    width = Math.max(width, font.width(row.label()) + right + (row.indent() ? 18 : 10));
                }
            }
            case REDSTONE, NONE -> {
            }
        }
        return Math.min(width, bodyW);
    }

    private int popupX() {
        Hot anchor = popupAnchor();
        return anchor == null ? 0 : Math.min(anchor.x(), bodyX + bodyW - popupWidth());
    }

    private int popupHeight() {
        return (popupRows() + (popupTitled() ? 1 : 0)) * DROPDOWN_ROW + 4;
    }

    /** Abaixo do controle; se não couber na janela, acima dele. */
    private int popupY() {
        Hot anchor = popupAnchor();
        if (anchor == null) {
            return 0;
        }
        int below = anchor.y() + anchor.h() + 1;
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return below + popupHeight() <= screenH - 2 ? below : Math.max(2, anchor.y() - 1 - popupHeight());
    }

    private int firstRowY() {
        return popupY() + 2 + (popupTitled() ? DROPDOWN_ROW : 0);
    }

    private boolean popupContains(double mx, double my) {
        int x = popupX();
        int y = popupY();
        return popup != Popup.NONE && mx >= x && mx < x + popupWidth() && my >= y && my < y + popupHeight();
    }

    /** Índice na lista aberta sob o mouse (com a rolagem), ou -1. */
    private int popupRowAt(double mx, double my) {
        if (!popupContains(mx, my)) {
            return -1;
        }
        int row = (int) Math.floor((my - firstRowY()) / DROPDOWN_ROW);
        if (row < 0 || row >= popupRows()) {
            return -1;
        }
        return row + popupScroll;
    }

    // ------------------------------------------------------------------ desenho

    /**
     * Abas, cubo e a face escolhida. {@code pointed} é a face sob o mouse no visor da tela, realçada no
     * cubo como a face sob o mouse.
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, @Nullable Direction pointed) {
        arrange();
        Font font = font();
        boolean covered = popup != Popup.NONE;
        int mx = covered ? -1 : mouseX;
        int my = covered ? -1 : mouseY;
        renderTabs(g, font, mx, my);
        renderNet(g, font, mx, my, pointed);
        renderBody(g, font, mx, my);
    }

    private void renderTabs(GuiGraphics g, Font font, int mx, int my) {
        if (tabsW <= 0) {
            return;
        }
        for (Hot hot : hots) {
            if (hot.kind() != Kind.TAB) {
                continue;
            }
            ResourceType t = ResourceType.values()[hot.arg()];
            boolean selected = t == type;
            boolean hovered = hot.contains(mx, my);
            GuiPaint.tab(g, hot.x(), hot.y(), hot.w(), hot.h(), selected, hovered);
            if (hot.w() > TabLayout.ICON_TAB) {
                ResourceStyle.drawIcon(g, t, hot.x() + 4, hot.y() + 3);
                int textX = hot.x() + 4 + ResourceStyle.ICON + 3;
                GuiText.draw(g, font, ResourceStyle.name(t), textX, hot.y() + 4, hot.x() + hot.w() - 4 - textX,
                        GuiPaint.tabText(selected, hovered));
            } else {
                ResourceStyle.drawIcon(g, t, hot.x() + (hot.w() - ResourceStyle.ICON) / 2, hot.y() + 3);
            }
        }
        Hot summary = hot(Kind.SUMMARY);
        if (summary != null) {
            GuiText.draw(g, font, networkSummary(), summary.x(), summary.y() + 3, summary.w(), GuiPaint.MUTED);
        }
        int lineY = tabsY + TAB_H + TAB_BELOW / 2;
        g.fill(tabsX, lineY, tabsX + tabsW, lineY + 1, GuiPaint.LINE);
    }

    private void renderNet(GuiGraphics g, Font font, int mx, int my, @Nullable Direction pointed) {
        if (netW <= 0) {
            return;
        }
        for (Hot hot : hots) {
            if (hot.kind() == Kind.FACE) {
                RemoteSide side = RemoteSide.values()[hot.arg()];
                Direction direction = faceOf(side);
                renderCell(g, font, hot, side, direction, hot.contains(mx, my) || direction == pointed);
            }
        }
    }

    /**
     * Uma face do cubo: tarja no topo na cor do modo, o nome do lado (curto quando aperta), a bolinha na cor
     * da rede e, na face onde o roteador está preso, o Olho na cor do tier, com o fundo apagado. Sem modo, a
     * borda é tracejada; a escolhida tem a borda de seleção.
     */
    private void renderCell(GuiGraphics g, Font font, Hot hot, RemoteSide side, Direction direction, boolean hovered) {
        FaceView v = view(direction);
        boolean lit = v.available() || remote();
        boolean selected = direction == face;
        boolean routerHere = attached(direction);
        int x = hot.x();
        int y = hot.y();
        int w = hot.w();
        int h = hot.h();
        int fill = !lit ? GuiPaint.INSET : routerHere ? GuiPaint.mix(GuiPaint.BUTTON, GuiPaint.INSET, 0.6f) : GuiPaint.BUTTON;
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        PortMode mode = lit ? v.mode() : PortMode.NONE;
        if (selected) {
            GuiPaint.box(g, x, y, w, h, fill, GuiPaint.ACCENT);
            GuiPaint.outline(g, x + 1, y + 1, w - 2, h - 2, GuiPaint.ACCENT);
        } else if (mode == PortMode.NONE) {
            dashed(g, x, y, w, h, hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        } else {
            GuiPaint.box(g, x, y, w, h, fill, hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        if (mode != PortMode.NONE) {
            int inset = selected ? 2 : 1;
            g.fill(x + inset, y + inset, x + w - inset, y + inset + 2, GuiPaint.modeColor(mode));
        }
        NetworkEntry entry = network(direction);
        int marks = (entry != null ? 6 : 0) + (routerHere ? 7 : 0);
        int room = w - 6 - marks;
        // na face onde o roteador está preso, "roteador" no lugar do lado (o lado vai na dica)
        Component full = routerHere ? panelTr("face.router") : sideName(side);
        Component shortName = routerHere ? panelTr("face.router.short") : tr("side.short." + side.key());
        Component label = switch (FaceNet.label(font.width(full), font.width(shortName), room)) {
            case FULL -> full;
            case SHORT -> shortName;
            case LETTER -> routerHere ? panelTr("face.router.letter") : tr("side.letter." + side.key());
        };
        int textY = y + Math.max(3, (h - 8) / 2 + 1);
        GuiText.draw(g, font, label, x + 3, textY, room, !lit ? GuiPaint.DISABLED : selected ? GuiPaint.FG : GuiPaint.MUTED);
        int markX = x + w - 3;
        int markY = y + Math.max(3, (h - 5) / 2 + 1);
        if (routerHere) {
            markX -= 6;
            GuiPaint.eye(g, markX, markY, GuiPaint.tierColor(snapshot.tier()));
        }
        if (entry != null) {
            markX -= 6;
            GuiPaint.dot(g, markX, markY, 0xFF000000 | entry.color());
        }
    }

    /** Contorno tracejado de 1 px (face sem modo). */
    private static void dashed(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int i = 1; i < w - 1; i += 3) {
            int end = Math.min(i + 2, w - 1);
            g.fill(x + i, y, x + end, y + 1, color);
            g.fill(x + i, y + h - 1, x + end, y + h, color);
        }
        for (int i = 1; i < h - 1; i += 3) {
            int end = Math.min(i + 2, h - 1);
            g.fill(x, y + i, x + 1, y + end, color);
            g.fill(x + w - 1, y + i, x + w, y + end, color);
        }
    }

    private void renderBody(GuiGraphics g, Font font, int mx, int my) {
        if (bodyW <= 0) {
            return;
        }
        int x = bodyX;
        int w = bodyW;
        FaceView v = view();
        if (readOnly != null && reasonLines > 0) {
            GuiText.wrap(g, font, readOnly, x, reasonY, w, 2, GuiPaint.WARN);
        }
        if (targetY >= 0) {
            renderTarget(g, font, mx, my);
        }

        Component detail = sideName(face).copy().append(" · ").append(access(v));
        if (attached(face)) {
            detail = detail.copy().append(" · ").append(tr("face.attached"));
        }
        GuiText.draw(g, font, detail, x, faceY, w, GuiPaint.FG);

        for (Hot hot : hots) {
            if (hot.kind() == Kind.MODE) {
                renderMode(g, font, hot, PortMode.values()[hot.arg()], hot.contains(mx, my), v);
            }
        }
        if (phraseY >= 0) {
            GuiText.wrap(g, font, phrase(), x, phraseY, w, 2, GuiPaint.MUTED);
        }

        GuiText.draw(g, font, tr("network.face"), x, networkY + 2, w, GuiPaint.MUTED);
        Hot net = hot(Kind.NETWORK);
        if (net != null) {
            renderNetworkPill(g, font, net, net.contains(mx, my) || popup == Popup.NETWORK, v);
        }

        GuiText.draw(g, font, tr("filter.label"), x, filterY + 3, w, GuiPaint.MUTED);
        Hot filter = hot(Kind.FILTER);
        Hot edit = hot(Kind.EDIT);
        if (filter != null && edit != null) {
            GuiText.draw(g, font, filterSummary(v), filter.x(), filterY + 3, filter.w(), GuiPaint.FG);
            textButton(g, font, edit, tr("filter.edit"), edit.contains(mx, my), readOnly == null);
        } else {
            int labelW = font.width(tr("filter.label")) + 5;
            GuiText.draw(g, font, panelTr("filter.none"), x + labelW, filterY + 3, w - labelW, GuiPaint.MUTED);
        }

        if (prioLabel) {
            GuiText.draw(g, font, panelTr("priority"), x, prioY + 3, w, GuiPaint.MUTED);
        }
        boolean editable = editable(v);
        Hot minus = hot(Kind.PRIO_MINUS);
        Hot plus = hot(Kind.PRIO_PLUS);
        if (minus != null && plus != null) {
            textButton(g, font, minus, Component.literal("-"), minus.contains(mx, my), editable);
            textButton(g, font, plus, Component.literal("+"), plus.contains(mx, my), editable);
            int boxX = minus.x() + minus.w() + 1;
            GuiPaint.box(g, boxX, prioY, PRIO_VALUE_W, BUTTON_H, GuiPaint.INSET, GuiPaint.LINE);
            GuiPaint.textCentered(g, font, Component.literal(Integer.toString(v.priority())), boxX + PRIO_VALUE_W / 2 + 1,
                    prioY + 3, editable ? GuiPaint.FG : GuiPaint.DISABLED);
        }
        Hot redstone = hot(Kind.REDSTONE);
        if (redstone != null) {
            dropButton(g, font, redstone, redstoneLabel, redstone.contains(mx, my) || popup == Popup.REDSTONE,
                    editable, GuiPaint.BUTTON_BORDER);
        }
    }

    private void renderTarget(GuiGraphics g, Font font, int mx, int my) {
        GuiText.draw(g, font, panelTr("target"), bodyX, targetY + 2, bodyW, GuiPaint.MUTED);
        Hot pill = hot(Kind.TARGET);
        if (pill != null) {
            RangeView.MachineView machine = targetMachine();
            int border = machine != null && machine.exception() ? GuiPaint.ACCENT : GuiPaint.BUTTON_BORDER;
            dropButton(g, font, pill, targetName(snapshot.target()), pill.contains(mx, my) || popup == Popup.TARGET,
                    readOnly == null, border);
        }
        Hot revert = hot(Kind.REVERT);
        if (revert != null) {
            clippedButton(g, font, revert, revertLabel, revert.contains(mx, my));
        }
        Hot unlink = hot(Kind.UNLINK);
        if (unlink != null) {
            clippedButton(g, font, unlink, tr("range.unlink"), unlink.contains(mx, my));
        }
    }

    private void renderMode(GuiGraphics g, Font font, Hot hot, PortMode mode, boolean hovered, FaceView v) {
        boolean active = editable(v);
        boolean selected = (v.available() || remote()) && v.mode() == mode;
        int color = GuiPaint.modeColor(mode);
        int fill = selected ? GuiPaint.mix(GuiPaint.BUTTON, color, 0.28f) : GuiPaint.BUTTON;
        int border = selected ? color : hovered && active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, hot.x(), hot.y(), hot.w(), hot.h(), fill, border);
        // o botão tem 14 px: o ícone do modo vai a 3/4 (12 px); sem o nome, centrado
        int iconX = modeNames ? hot.x() + 2 : hot.x() + (hot.w() - 12) / 2;
        g.pose().pushPose();
        g.pose().translate(iconX, hot.y() + 1, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        GuiPaint.port(g, mode, 0, 0, active ? 1f : 0.3f);
        g.pose().popPose();
        if (modeNames) {
            GuiText.draw(g, font, modeName(mode), hot.x() + MODE_TEXT_X + 1, hot.y() + 3, hot.w() - MODE_TEXT_X - 2,
                    active ? GuiPaint.FG : GuiPaint.DISABLED);
        }
    }

    /** A pílula da linha Rede: borda e ponto na cor da rede; com modo e sem rede, contorno coral e o pedido. */
    private void renderNetworkPill(GuiGraphics g, Font font, Hot b, boolean hovered, FaceView v) {
        NetworkEntry entry = network(face);
        boolean ask = needsNetwork(v);
        int color = entry != null ? 0xFF000000 | entry.color() : GuiPaint.MUTED;
        int fill = hovered && readOnly == null ? GuiPaint.BUTTON : GuiPaint.PANEL;
        int border = ask ? GuiPaint.ACCENT : entry != null ? color : GuiPaint.LINE;
        GuiPaint.pill(g, b.x(), b.y(), b.w(), b.h(), fill, border);
        if (entry != null) {
            GuiPaint.eye(g, b.x() + 4, b.y() + 3, color);
        } else {
            GuiPaint.outline(g, b.x() + 4, b.y() + 3, 5, 5, ask ? GuiPaint.ACCENT : GuiPaint.MUTED);
        }
        int textX = b.x() + 13;
        int room = b.x() + b.w() - 12 - textX;
        Component label = networkLabel(face);
        if (ask && v.network().isEmpty()) {
            // o pedido inteiro, ou o curto se a pílula for estreita
            label = font.width(panelTr("network.choose")) <= room ? panelTr("network.choose")
                    : panelTr("network.choose.short");
        }
        GuiText.draw(g, font, label, textX, b.y() + 2, room,
                readOnly != null ? GuiPaint.DISABLED : entry != null || ask ? GuiPaint.FG : GuiPaint.MUTED);
        GuiPaint.arrowDown(g, b.x() + b.w() - 9, b.y() + 4, hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    /** Botão com uma lista: o texto e a seta. */
    private static void dropButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered, boolean active,
            int border) {
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET,
                !active ? GuiPaint.LINE : hovered ? GuiPaint.BUTTON_HOVER_BORDER : border);
        GuiText.draw(g, font, text, b.x() + 4, b.y() + (b.h() - 8) / 2, b.w() - 15, active ? GuiPaint.FG : GuiPaint.DISABLED);
        GuiPaint.arrowDown(g, b.x() + b.w() - 9, b.y() + b.h() / 2 - 1, hovered && active ? GuiPaint.FG : GuiPaint.MUTED);
    }

    private static void textButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered, boolean active) {
        int border = hovered && active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET, active ? border : GuiPaint.LINE);
        GuiPaint.textCentered(g, font, text, b.x() + b.w() / 2 + 1, b.y() + (b.h() - 8) / 2,
                active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** Botão de texto que abrevia pelo {@link GuiText} quando o texto não cabe (o inteiro vai na dica). */
    private void clippedButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered) {
        boolean active = readOnly == null;
        int border = hovered && active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET, border);
        int room = b.w() - 2 * TARGET_PAD;
        int textW = Math.min(font.width(text), room);
        GuiText.draw(g, font, text, b.x() + TARGET_PAD + (room - textW + 1) / 2, b.y() + (b.h() - 8) / 2, room,
                active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** A lista aberta e o aviso, por cima de tudo (a tela chama depois de desenhar os slots). */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        arrange();
        Font font = font();
        PanelNoticePayload notice = PanelNotices.current();
        if (notice != null && bodyW > 0) {
            renderNotice(g, font, notice, mouseX, mouseY);
        }
        if (popup == Popup.NONE || popupAnchor() == null) {
            return;
        }
        int x = popupX();
        int y = popupY();
        int w = popupWidth();
        int rows = popupRows();
        int hovered = popupRowAt(mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        GuiPaint.box(g, x, y, w, popupHeight(), GuiPaint.PANEL, GuiPaint.BUTTON_BORDER);
        if (popupTitled()) {
            GuiText.draw(g, font, networkListTitle(), x + 5, y + 4, w - 10, GuiPaint.MUTED);
            g.fill(x + 3, y + 2 + DROPDOWN_ROW, x + w - 3, y + 3 + DROPDOWN_ROW, GuiPaint.LINE);
        }
        int top = firstRowY();
        for (int i = 0; i < rows; i++) {
            int index = i + popupScroll;
            int ry = top + i * DROPDOWN_ROW;
            switch (popup) {
                case NETWORK -> renderNetworkRow(g, font, x, ry, w, index, index == hovered);
                case REDSTONE -> renderRedstoneRow(g, font, x, ry, w, index, index == hovered);
                case TARGET -> renderTargetRow(g, font, x, ry, w, index, index == hovered);
                case NONE -> {
                }
            }
        }
        if (popupScroll > 0) {
            GuiPaint.text(g, font, Component.literal("▲"), x + w - 9, top, GuiPaint.MUTED);
        }
        if (popupScroll + rows < popupCount()) {
            GuiPaint.text(g, font, Component.literal("▼"), x + w - 9, top + (rows - 1) * DROPDOWN_ROW + 2, GuiPaint.MUTED);
        }
        g.pose().popPose();
    }

    private void renderNetworkRow(GuiGraphics g, Font font, int x, int ry, int w, int index, boolean hovered) {
        List<NetworkEntry> networks = snapshot.networks();
        Optional<UUID> current = snapshot.network(type, face);
        boolean none = index >= networks.size();
        NetworkEntry entry = none ? null : networks.get(index);
        boolean selected = none ? current.isEmpty() : current.isPresent() && current.get().equals(entry.id());
        boolean usable = none || entry.usable();
        if (hovered && usable) {
            g.fill(x + 1, ry, x + w - 1, ry + DROPDOWN_ROW, GuiPaint.ROW_SELECTED);
        }
        if (selected) {
            g.fill(x + 1, ry, x + 2, ry + DROPDOWN_ROW, GuiPaint.ACCENT);
        }
        int color = none ? GuiPaint.MUTED
                : usable ? 0xFF000000 | entry.color() : GuiPaint.mix(0xFF000000 | entry.color(), GuiPaint.PANEL, 0.6f);
        if (none) {
            GuiPaint.outline(g, x + 6, ry + 3, 5, 5, color);
        } else {
            GuiPaint.eye(g, x + 6, ry + 3, color);
        }
        Component label = none ? tr("network.none") : Component.literal(entry.name());
        GuiText.draw(g, font, label, x + 15, ry + 2, w - 20, none ? GuiPaint.MUTED : usable ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    private void renderRedstoneRow(GuiGraphics g, Font font, int x, int ry, int w, int index, boolean hovered) {
        RedstoneMode mode = RedstoneMode.values()[index];
        if (hovered) {
            g.fill(x + 1, ry, x + w - 1, ry + DROPDOWN_ROW, GuiPaint.ROW_SELECTED);
        }
        if (mode == view().redstone()) {
            g.fill(x + 1, ry, x + 2, ry + DROPDOWN_ROW, GuiPaint.ACCENT);
        }
        GuiText.draw(g, font, redstoneName(mode), x + 6, ry + 2, w - 10, GuiPaint.FG);
    }

    private void renderTargetRow(GuiGraphics g, Font font, int x, int ry, int w, int index, boolean hovered) {
        TargetRow row = targetRows().get(index);
        if (hovered) {
            g.fill(x + 1, ry, x + w - 1, ry + DROPDOWN_ROW, GuiPaint.ROW_SELECTED);
        }
        if (row.target().equals(snapshot.target())) {
            g.fill(x + 1, ry, x + 2, ry + DROPDOWN_ROW, GuiPaint.ACCENT);
        }
        int textX = x + (row.indent() ? 14 : 6);
        int rightW = 0;
        if (row.right() != null) {
            rightW = Math.min(font.width(row.right()), w / 2);
            GuiText.draw(g, font, row.right(), x + w - 5 - rightW, ry + 2, rightW, row.rightColor());
        }
        GuiText.draw(g, font, row.label(), textX, ry + 2, x + w - 5 - rightW - (rightW > 0 ? 4 : 0) - textX, GuiPaint.FG);
    }

    /** O aviso do painel, embaixo dele: o texto e o Desfazer sublinhado de coral. */
    private void renderNotice(GuiGraphics g, Font font, PanelNoticePayload notice, int mouseX, int mouseY) {
        int y = noticeY();
        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        g.fill(bodyX, y, bodyX + bodyW, y + NOTICE_H, GuiPaint.FG);
        Hot undo = hot(Kind.UNDO);
        int textRoom = bodyW - 8 - (undo != null ? undo.w() + 2 : 0);
        GuiText.draw(g, font, notice.message(), bodyX + 4, y + 3, textRoom, GuiPaint.PANEL);
        if (undo != null) {
            Component label = panelTr("notice.undo");
            int lx = undo.x() + 4;
            boolean hovered = undo.contains(mouseX, mouseY);
            GuiPaint.text(g, font, label, lx, y + 3, hovered ? GuiPaint.ACCENT : GuiPaint.PANEL);
            g.fill(lx, y + 11, lx + font.width(label) - 1, y + 12, GuiPaint.ACCENT);
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ ganchos do e2e e das capturas

    /** Centro da face {@code direction} (quadro da tela) no cubo. */
    public int @Nullable [] faceCenter(Direction direction) {
        return sideCenter(sideOf(direction));
    }

    /** Centro da face do lado {@code side} no cubo. */
    public int @Nullable [] sideCenter(RemoteSide side) {
        arrange();
        Hot hot = hot(Kind.FACE, side.ordinal());
        return hot == null ? null : hot.center();
    }

    public int @Nullable [] modeCenter(PortMode mode) {
        arrange();
        Hot hot = hot(Kind.MODE, mode.ordinal());
        return hot == null ? null : hot.center();
    }

    public int @Nullable [] tabCenter(ResourceType t) {
        arrange();
        Hot hot = hot(Kind.TAB, t.ordinal());
        return hot == null ? null : hot.center();
    }

    /** A pílula da linha Rede. */
    public int @Nullable [] networkCenter() {
        return center(Kind.NETWORK);
    }

    /**
     * Centro da linha {@code row} da lista da linha Rede aberta (as redes do seletor e, por último, "Sem
     * rede"), ou {@code null} se ela está fechada ou a linha fora da vista.
     */
    public int @Nullable [] networkRowCenter(int row) {
        return popup == Popup.NETWORK ? popupRowCenter(row) : null;
    }

    /** Centro de uma linha da lista do redstone aberta. */
    public int @Nullable [] redstoneRowCenter(RedstoneMode mode) {
        return popup == Popup.REDSTONE ? popupRowCenter(mode.ordinal()) : null;
    }

    /** Centro da linha do alvo na lista da linha Editando aberta. */
    public int @Nullable [] targetRowCenter(EditTarget target) {
        if (popup != Popup.TARGET) {
            return null;
        }
        List<TargetRow> rows = targetRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).target().equals(target)) {
                return popupRowCenter(i);
            }
        }
        return null;
    }

    private int @Nullable [] popupRowCenter(int row) {
        arrange();
        int visible = row - popupScroll;
        if (visible < 0 || visible >= popupRows()) {
            return null;
        }
        return new int[] {popupX() + popupWidth() / 2, firstRowY() + visible * DROPDOWN_ROW + DROPDOWN_ROW / 2};
    }

    public int @Nullable [] editCenter() {
        return center(Kind.EDIT);
    }

    /** O − ({@code direction < 0}) ou o + da prioridade. */
    public int @Nullable [] priorityCenter(int direction) {
        return center(direction < 0 ? Kind.PRIO_MINUS : Kind.PRIO_PLUS);
    }

    public int @Nullable [] redstoneCenter() {
        return center(Kind.REDSTONE);
    }

    /** A lista da linha Editando (com o Upgrade de Alcance). */
    public int @Nullable [] targetCenter() {
        return center(Kind.TARGET);
    }

    public int @Nullable [] revertCenter() {
        return center(Kind.REVERT);
    }

    public int @Nullable [] unlinkCenter() {
        return center(Kind.UNLINK);
    }

    public int @Nullable [] undoCenter() {
        return center(Kind.UNDO);
    }

    private int @Nullable [] center(Kind kind) {
        arrange();
        Hot hot = hot(kind);
        return hot == null ? null : hot.center();
    }

    /** Abre ou fecha a lista da linha Rede (captura sem mouse). */
    public void previewNetworkList(boolean open) {
        popup = open ? Popup.NETWORK : Popup.NONE;
        popupScroll = 0;
    }
}
