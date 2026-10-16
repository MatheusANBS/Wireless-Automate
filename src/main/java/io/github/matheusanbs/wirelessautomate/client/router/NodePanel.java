package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.TabLayout;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Kind;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLists.Popup;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.OpenFilterPayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.RevertMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import io.github.matheusanbs.wirelessautomate.packet.UnlinkMachinePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
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
 *
 * <p>O painel é a anfitriã das partes: o estado e as leituras no {@link PanelModel}, a geometria no
 * {@link PanelLayout}, o desenho no {@link PanelPainter} e no {@link FaceCube}, as listas abertas no
 * {@link PanelLists}, a linha Editando no {@link TargetLine} e as dicas no {@link PanelTooltips}. Fica no
 * pacote da tela do roteador, público, porque a Lista do Tablet mostra o mesmo painel do mesmo nó.
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

    private final Actions actions;
    private final PanelModel model;
    private final PanelLayout layout = new PanelLayout();
    private final TargetLine targetLine;
    private final PanelLists lists;
    private final PanelPainter painter;
    private final FaceCube cube;
    private final PanelTooltips tooltips;
    private final PanelLists.Choice choice = new PanelLists.Choice() {
        @Override
        public void network(Optional<UUID> network) {
            chooseNetwork(network);
        }

        @Override
        public void redstone(RedstoneMode mode) {
            FaceView v = model.view();
            if (mode != v.redstone()) {
                sendFace(v.mode(), v.priority(), mode);
            }
        }

        @Override
        public void target(EditTarget target) {
            chooseTarget(target);
        }
    };

    public NodePanel(Actions actions, RouterSnapshot snapshot, Direction face) {
        this.actions = actions;
        this.model = new PanelModel(snapshot, face);
        this.targetLine = new TargetLine(model, layout);
        this.lists = new PanelLists(model, layout, targetLine);
        this.painter = new PanelPainter(model, layout, targetLine);
        this.cube = new FaceCube(model, layout);
        this.tooltips = new PanelTooltips(model, targetLine);
    }

    // ------------------------------------------------------------------ estado

    /**
     * O snapshot atual (a tela chama a cada quadro). Se o alvo de edição mudou, a face passa à que o
     * servidor escolheu para ele, a lista aberta fecha e o aviso some (o Desfazer agiria no alvo novo).
     */
    public void setSnapshot(RouterSnapshot snapshot) {
        if (model.setSnapshot(snapshot)) {
            lists.close();
            PanelNotices.clear();
        }
    }

    public RouterSnapshot snapshot() {
        return model.snapshot();
    }

    public ResourceType type() {
        return model.type();
    }

    /** A face escolhida, no quadro da tela (absoluta na presa, canônica num alvo do alcance). */
    public Direction face() {
        return model.face();
    }

    public void select(ResourceType type) {
        model.setType(type);
        lists.close();
    }

    public void selectFace(Direction face) {
        model.setFace(face);
        lists.close();
    }

    /** Só leitura, com o motivo em cima (o Tablet longe do nó); {@code null} volta a editar. */
    public void readOnly(@Nullable Component reason) {
        model.setReadOnly(reason);
        if (reason != null) {
            lists.close();
        }
    }

    public TabLayout.Mode tabMode() {
        return layout.tabMode;
    }

    /**
     * A tela do roteador mostra a lista das máquinas ao lado: a pílula Editando vira só o nome do alvo, porque
     * a lista faz a mesma escolha. Sem ela (visor no lugar, ou o Tablet), a pílula abre a lista do alvo.
     */
    public void targetFromList(boolean fromList) {
        if (fromList && !model.targetFromList() && lists.popup() == Popup.TARGET) {
            lists.close();
        }
        model.setTargetFromList(fromList);
    }

    /** Alguma lista (rede, redstone ou alvo) está aberta. */
    public boolean popupOpen() {
        return lists.open();
    }

    public boolean networkListShown() {
        return lists.popup() == Popup.NETWORK;
    }

    // ------------------------------------------------------------------ layout

    /** Empilha abas, cubo e o resto no retângulo. */
    public void layout(int x, int y, int w, int h) {
        layout.layout(x, y, w, h);
    }

    /** Cada parte no seu retângulo: a linha das abas, o cubo e o resto. */
    public void layoutSplit(int tabsX, int tabsY, int tabsW, int netX, int netY, int netW, int netH, int bodyX,
            int bodyY, int bodyW, int bodyH) {
        layout.layoutSplit(tabsX, tabsY, tabsW, netX, netY, netW, netH, bodyX, bodyY, bodyW, bodyH);
    }

    /** Altura do cubo empilhado ({@link #layout(int, int, int, int)}), para a tela calcular o resto. */
    public static int stackedNetHeight() {
        return PanelLayout.stackedNetHeight();
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Posições e áreas de clique do quadro atual, e o lugar da lista aberta. */
    private void arrange() {
        Font font = font();
        layout.arrange(font, model);
        lists.place(font);
    }

    /** A face do cubo sob o mouse (para o visor realçar a mesma), ou {@code null}. */
    public @Nullable Direction hoveredFace(double mx, double my) {
        if (lists.open()) {
            return null;
        }
        arrange();
        Hot hot = layout.hotAt(mx, my);
        return hot != null && hot.kind() == Kind.FACE ? model.faceOf(RemoteSide.values()[hot.arg()]) : null;
    }

    // ------------------------------------------------------------------ ações

    private void sendFace(PortMode mode, int priority, RedstoneMode redstone) {
        if (!model.editable(model.view())) {
            return;
        }
        actions.send(new SetFacePayload(actions.containerId(), model.type(), model.face(), mode, priority, redstone));
    }

    private void changePriority(int direction) {
        FaceView v = model.view();
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
        lists.close();
        if (network.isPresent() && network.equals(model.snapshot().network(model.type(), model.face()))) {
            return;
        }
        actions.send(new SetNetworkPayload(actions.containerId(), model.type(), model.face(), network));
    }

    private void chooseTarget(EditTarget target) {
        lists.close();
        if (target.equals(model.snapshot().target())) {
            return;
        }
        CustomPacketPayload payload = targetLine.select(actions.containerId(), target);
        if (payload != null) {
            actions.send(payload);
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
        if (lists.open()) {
            lists.click(mx, my, choice);
            return true;
        }
        if (layout.inNotice(mx, my)) {
            // a faixa inteira do aviso engole o clique; só o Desfazer age
            Hot undo = layout.hot(Kind.UNDO);
            PanelNoticePayload notice = PanelNotices.current();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && undo != null && undo.contains(mx, my) && notice != null) {
                undo(notice);
            }
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Hot hot = layout.hotAt(mx, my);
        if (hot == null) {
            return false;
        }
        click(hot);
        return true;
    }

    /** O clique esquerdo numa área do painel, sem lista aberta. */
    private void click(Hot hot) {
        FaceView v = model.view();
        RangeView.MachineView machine = model.targetMachine();
        boolean edit = model.readOnly() == null;
        switch (hot.kind()) {
            case TAB -> select(ResourceType.values()[hot.arg()]);
            case FACE -> selectFace(model.faceOf(RemoteSide.values()[hot.arg()]));
            case MODE -> sendFace(PortMode.values()[hot.arg()], v.priority(), v.redstone());
            case NETWORK -> {
                if (edit) {
                    lists.open(Popup.NETWORK, 0);
                }
            }
            case EDIT -> {
                if (edit && model.hasFilter()) {
                    actions.send(new OpenFilterPayload(actions.containerId(), model.type(), model.face()));
                }
            }
            case PRIO_MINUS -> changePriority(-1);
            case PRIO_PLUS -> changePriority(1);
            case REDSTONE -> {
                if (model.editable(v)) {
                    lists.open(Popup.REDSTONE, 0);
                }
            }
            case TARGET -> {
                if (edit && !model.targetFromList()) {
                    lists.open(Popup.TARGET, lists.targetScroll());
                }
            }
            case REVERT -> {
                if (edit && machine != null) {
                    actions.send(new RevertMachinePayload(actions.containerId(), model.snapshot().target().id(),
                            machine.pos()));
                }
            }
            case UNLINK -> {
                if (edit && machine != null) {
                    actions.send(new UnlinkMachinePayload(actions.containerId(), model.snapshot().target().id(),
                            machine.pos()));
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
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!lists.open()) {
            return false;
        }
        arrange();
        lists.scroll(mx, my, scrollY);
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (lists.open() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            lists.close();
            return true;
        }
        return false;
    }

    /** A dica do ponto (controles e faces), ou {@code null}; o texto abreviado fica com a tela. */
    public @Nullable Component tooltip(double mx, double my) {
        if (lists.open()) {
            return null;
        }
        arrange();
        if (layout.inNotice(mx, my)) {
            return null;
        }
        Hot hot = layout.hotAt(mx, my);
        return hot == null ? null : tooltips.of(hot);
    }

    // ------------------------------------------------------------------ desenho

    /**
     * Abas, cubo e a face escolhida. {@code pointed} é a face sob o mouse no visor da tela, realçada no
     * cubo como a face sob o mouse.
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, @Nullable Direction pointed) {
        arrange();
        Font font = font();
        boolean covered = lists.open();
        int mx = covered ? -1 : mouseX;
        int my = covered ? -1 : mouseY;
        painter.renderTabs(g, font, mx, my);
        cube.render(g, font, mx, my, pointed);
        painter.renderBody(g, font, mx, my, lists.popup(), actions.cardCount());
    }

    /** A lista aberta e o aviso, por cima de tudo (a tela chama depois de desenhar os slots). */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        arrange();
        Font font = font();
        PanelNoticePayload notice = PanelNotices.current();
        if (notice != null && layout.bodyW > 0) {
            painter.renderNotice(g, font, notice, mouseX, mouseY);
        }
        lists.render(g, font, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ ganchos do e2e e das capturas

    /** Centro da face {@code direction} (quadro da tela) no cubo. */
    public int @Nullable [] faceCenter(Direction direction) {
        return sideCenter(model.sideOf(direction));
    }

    /** Centro da face do lado {@code side} no cubo. */
    public int @Nullable [] sideCenter(RemoteSide side) {
        arrange();
        Hot hot = layout.hot(Kind.FACE, side.ordinal());
        return hot == null ? null : hot.center();
    }

    public int @Nullable [] modeCenter(PortMode mode) {
        arrange();
        Hot hot = layout.hot(Kind.MODE, mode.ordinal());
        return hot == null ? null : hot.center();
    }

    public int @Nullable [] tabCenter(ResourceType t) {
        arrange();
        Hot hot = layout.hot(Kind.TAB, t.ordinal());
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
        return lists.popup() == Popup.NETWORK ? rowCenter(Popup.NETWORK, row) : null;
    }

    /** Centro de uma linha da lista do redstone aberta. */
    public int @Nullable [] redstoneRowCenter(RedstoneMode mode) {
        return lists.popup() == Popup.REDSTONE ? rowCenter(Popup.REDSTONE, mode.ordinal()) : null;
    }

    /** Centro da linha do alvo na lista da linha Editando aberta. */
    public int @Nullable [] targetRowCenter(EditTarget target) {
        if (lists.popup() != Popup.TARGET) {
            return null;
        }
        List<TargetLine.TargetRow> rows = targetLine.rows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).target().equals(target)) {
                return rowCenter(Popup.TARGET, i);
            }
        }
        return null;
    }

    private int @Nullable [] rowCenter(Popup which, int row) {
        arrange();
        return lists.rowCenter(which, row);
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
        Hot hot = layout.hot(kind);
        return hot == null ? null : hot.center();
    }

    /** Abre ou fecha a lista da linha Rede (captura sem mouse). */
    public void previewNetworkList(boolean open) {
        lists.open(open ? Popup.NETWORK : Popup.NONE, 0);
    }
}
