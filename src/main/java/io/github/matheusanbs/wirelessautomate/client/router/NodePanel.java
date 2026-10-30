package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.FrameCache;
import io.github.matheusanbs.wirelessautomate.client.FrameClock;
import io.github.matheusanbs.wirelessautomate.client.TabLayout;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Kind;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLists.Popup;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
 * {@link PanelLists}, a linha Editando no {@link TargetLine}, as dicas no {@link PanelTooltips}, as ações dos
 * controles no {@link PanelEdits} e a aba e a face lembradas no {@link PanelMemory}. Fica no
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

        /** "Tirar a rede das faces sem modo" no alvo, ou o Desfazer do aviso dele ({@code undo}). */
        void clearIdleNetworks(boolean undo);
    }

    private final Actions actions;
    private final PanelModel model;
    private final PanelLayout layout = new PanelLayout();
    /** O layout do quadro: feito uma vez e reusado no realce, no desenho, por cima e na dica. */
    private final FrameCache arranged = new FrameCache();
    private final TargetLine targetLine;
    private final PanelLists lists;
    private final PanelPainter painter;
    private final FaceCube cube;
    private final PanelTooltips tooltips;
    /** A caixa do nome do alvo, da linha Editando. */
    private final LabelEditor label;
    private final PanelEdits edits;

    public NodePanel(Actions actions, RouterSnapshot snapshot, Direction face) {
        this.actions = actions;
        this.model = new PanelModel(snapshot, face);
        PanelMemory.recall(model, snapshot);
        this.targetLine = new TargetLine(model, layout, actions);
        this.label = targetLine.label();
        this.lists = new PanelLists(model, layout, targetLine);
        this.painter = new PanelPainter(model, layout, targetLine);
        this.cube = new FaceCube(model, layout);
        this.tooltips = new PanelTooltips(model, targetLine);
        this.edits = new PanelEdits(model, actions, lists, targetLine);
    }

    // ------------------------------------------------------------------ estado

    /**
     * O snapshot atual (a tela chama a cada quadro). Se o alvo de edição mudou, a face passa à que o
     * servidor escolheu para ele, a lista aberta fecha e o aviso some (o Desfazer agiria no alvo novo).
     */
    public void setSnapshot(RouterSnapshot snapshot) {
        if (snapshot != model.snapshot()) {
            arranged.invalidate();
        }
        if (model.setSnapshot(snapshot)) {
            label.finish(false);
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
        arranged.invalidate();
        model.setType(type);
        lists.close();
        PanelMemory.remember(model);
    }

    public void selectFace(Direction face) {
        arranged.invalidate();
        model.setFace(face);
        lists.close();
        PanelMemory.remember(model);
    }

    /** Põe o painel na aba, sem lembrar: quem abre o painel sozinho (o nó escolhido do Tablet) não é uma escolha do jogador. */
    public void showType(ResourceType type) {
        arranged.invalidate();
        model.setType(type);
        lists.close();
    }

    /** Só leitura, com o motivo em cima (o Tablet longe do nó); {@code null} volta a editar. */
    public void readOnly(@Nullable Component reason) {
        if (!Objects.equals(reason, model.readOnly())) {
            arranged.invalidate();
        }
        model.setReadOnly(reason);
        if (reason != null) {
            label.finish(false);
            lists.close();
        }
    }

    public TabLayout.Mode tabMode() {
        return layout.tabMode;
    }

    /**
     * A tela do roteador mostra a lista das máquinas: a barra Editando fica só com a pílula do alvo e o Igualar,
     * porque a lista marca o alvo e tem os botões da máquina. Sem ela (o Tablet), a linha Editando os traz todos.
     */
    public void targetFromList(boolean fromList) {
        if (fromList != model.targetFromList()) {
            arranged.invalidate();
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
        arranged.invalidate();
        layout.layout(x, y, w, h);
    }

    /** Cada parte no seu retângulo: a linha das abas, o cubo e o resto. */
    public void layoutSplit(int tabsX, int tabsY, int tabsW, int netX, int netY, int netW, int netH, int bodyX,
            int bodyY, int bodyW, int bodyH) {
        arranged.invalidate();
        layout.layoutSplit(tabsX, tabsY, tabsW, netX, netY, netW, netH, bodyX, bodyY, bodyW, bodyH);
    }

    /** Altura do cubo empilhado ({@link #layout(int, int, int, int)}), para a tela calcular o resto. */
    public static int stackedNetHeight() {
        return PanelLayout.stackedNetHeight();
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /**
     * Posições e áreas de clique, e o lugar da lista aberta, refeitos agora: para um evento ou um gancho, que
     * podem mudar o estado em seguida (o próximo {@link #arrangeForFrame} refaz).
     */
    private void arrange() {
        computeArrangement();
        arranged.invalidate();
    }

    /**
     * O layout do quadro, para o desenho, o realce e a dica: feito na primeira chamada do quadro e reusado nas
     * outras, enquanto o aviso é o mesmo e nada mudou o painel (snapshot, aba, face, medidas).
     */
    private void arrangeForFrame() {
        if (!arranged.reuse(FrameClock.frame(), PanelNotices.current())) {
            computeArrangement();
        }
    }

    private void computeArrangement() {
        Font font = font();
        layout.cardSlots = actions.cardCount() >= 0;
        layout.arrange(font, model);
        lists.place(font);
    }

    /** A face do cubo sob o mouse (para o visor realçar a mesma), ou {@code null}. */
    public @Nullable Direction hoveredFace(double mx, double my) {
        if (lists.open()) {
            return null;
        }
        arrangeForFrame();
        Hot hot = layout.hotAt(mx, my);
        return hot != null && hot.kind() == Kind.FACE ? model.faceOf(FaceNet.SIDES[hot.arg()]) : null;
    }

    // ------------------------------------------------------------------ ações

    /**
     * O clique num botão do cubo: escolhe a face e, se ela já era a escolhida, troca o modo ({@link
     * PanelEdits#cycleMode}).
     */
    private void faceClick(Hot hot, int button) {
        Direction clicked = model.faceOf(FaceNet.SIDES[hot.arg()]);
        boolean alreadySelected = clicked == model.face();
        selectFace(clicked);
        if (alreadySelected) {
            edits.cycleMode(button);
        }
    }

    // ------------------------------------------------------------------ entrada

    /** Com o nome em edição: o clique na caixa fica nela ({@code true}); fora, salva e segue. */
    public boolean labelClick(double mx, double my, int button) {
        return label.mouseClicked(mx, my, button);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (labelClick(mx, my, button)) {
            return true;
        }
        arrange();
        if (lists.open()) {
            lists.click(mx, my, edits.choice);
            return true;
        }
        if (layout.inNotice(mx, my)) {
            // a faixa inteira do aviso engole o clique; só o Desfazer age
            Hot undo = layout.hot(Kind.UNDO);
            PanelNoticePayload notice = PanelNotices.current();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && undo != null && undo.contains(mx, my) && notice != null) {
                edits.undo(notice);
            }
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }
        Hot hot = layout.hotAt(mx, my);
        // os slots de cartão são do menu: o clique segue até eles
        if (hot == null || hot.kind() == Kind.CARDS) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (hot.kind() != Kind.FACE) {
                return false;
            }
            faceClick(hot, button);
            return true;
        }
        click(hot);
        return true;
    }

    /** O clique esquerdo numa área do painel, sem lista aberta: a aba e a face aqui, os controles no {@link PanelEdits}. */
    private void click(Hot hot) {
        switch (hot.kind()) {
            case TAB -> select(ResourceType.values()[hot.arg()]);
            case FACE -> faceClick(hot, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            default -> edits.click(hot);
        }
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!lists.open()) {
            // a roda sobre a prioridade a muda, como os botões ‹ ›
            if (scrollY == 0 || layout.bodyW <= 0) {
                return false;
            }
            arrangeForFrame();
            Hot hot = layout.hotAt(mx, my);
            Hot minus = layout.hot(Kind.PRIO_MINUS);
            Hot plus = layout.hot(Kind.PRIO_PLUS);
            boolean overPriority = minus != null && plus != null && mx >= minus.x() && mx < plus.x() + plus.w()
                    && my >= minus.y() && my < minus.y() + minus.h() && (hot == null || hot.kind() != Kind.UNDO);
            if (!overPriority) {
                return false;
            }
            edits.changePriority(scrollY > 0 ? 1 : -1);
            return true;
        }
        arrange();
        lists.scroll(mx, my, scrollY);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (label.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (lists.keyPressed(keyCode, edits.choice)) {
            arranged.invalidate();
            return true;
        }
        if (lists.open() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            arranged.invalidate();
            lists.close();
            return true;
        }
        return false;
    }

    /** Fecha a caixa do nome sem salvar (a tela saiu do painel). */
    public void cancelLabel() {
        label.finish(false);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return label.charTyped(codePoint, modifiers) || lists.charTyped(codePoint);
    }

    /** A dica do ponto (controles e faces), ou {@code null}; o texto abreviado fica com a tela. */
    public @Nullable Component tooltip(double mx, double my) {
        Component hint = label.tooltip(mx, my);
        if (hint != null) {
            return hint;
        }
        if (lists.open()) {
            return null;
        }
        arrangeForFrame();
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
        boolean covered = lists.open();
        int mx = covered ? -1 : mouseX;
        int my = covered ? -1 : mouseY;
        // o Desvincular armado desarma quando o mouse sai dele (pelas áreas do quadro anterior), antes de
        // arrumar este quadro, para o botão não sair na largura do armado sem estar armado
        Hot unlink = layout.hot(Kind.UNLINK);
        if (unlink == null || !unlink.contains(mx, my)) {
            if (model.unlinkArmed()) {
                arranged.invalidate();
            }
            model.unlinkArm().disarm();
        }
        Hot clear = layout.hot(Kind.CLEAR);
        if (clear == null || !clear.contains(mx, my)) {
            model.clearArm().disarm();
        }
        arrangeForFrame();
        Font font = font();
        painter.renderTabs(g, font, mx, my);
        cube.render(g, font, mx, my, pointed, model.clearArmed());
        painter.renderBody(g, font, mx, my, lists.popup(), actions.cardCount());
        targetLine.renderLabel(g, mx, my);
    }

    /** A lista aberta e o aviso, por cima de tudo (a tela chama depois de desenhar os slots). */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        arrangeForFrame();
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

    /** Escolhe o lado {@code side} (relativo à frente da máquina) como se o visor 3D tivesse sido clicado (gancho do e2e). */
    public void selectSide(RemoteSide side) {
        selectFace(model.faceOf(side));
    }

    /** Põe a face escolhida no modo {@code mode}, pelo mesmo pacote do clique no cubo (gancho do e2e). */
    public void chooseMode(PortMode mode) {
        FaceView v = model.view();
        edits.sendFace(mode, v.priority(), v.redstone());
    }

    /** O Limpar do quadro das faces. */
    public int @Nullable [] clearCenter() {
        return center(Kind.CLEAR);
    }

    public int @Nullable [] copyCenter() {
        return center(Kind.COPY);
    }

    public int @Nullable [] pasteCenter() {
        return center(Kind.PASTE);
    }

    /** O Limpar está armado, esperando o segundo clique (gancho do e2e). */
    public boolean clearArmed() {
        return model.clearArmed();
    }

    /**
     * O canto (na tela) do primeiro slot de cartão, na linha do Filtro, ou {@code null} sem a linha à vista (outro
     * tipo, ou sem espaço); a tela do roteador põe os slots do menu aí.
     */
    public int @Nullable [] cardSlotsAt() {
        arrangeForFrame();
        return layout.cardX < 0 ? null : new int[] {layout.cardX, layout.cardY};
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
     * Centro da linha {@code row} da lista da linha Rede aberta (as redes do seletor, depois da busca; o "Sem rede"
     * fixo no topo é o {@link #networkNoneCenter}), ou {@code null} se ela está fechada ou a linha fora da vista.
     */
    public int @Nullable [] networkRowCenter(int row) {
        return lists.popup() == Popup.NETWORK ? rowCenter(Popup.NETWORK, row) : null;
    }

    /** Centro da linha fixa "Sem rede" da lista da linha Rede aberta, ou {@code null} se ela está fechada. */
    public int @Nullable [] networkNoneCenter() {
        arrange();
        return lists.noneCenter();
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

    /** A seta ‹ ({@code direction < 0}) ou › do redstone. */
    public int @Nullable [] redstoneStepCenter(int direction) {
        return center(direction < 0 ? Kind.REDSTONE_PREV : Kind.REDSTONE_NEXT);
    }

    /** A lista da linha Editando (com o Upgrade de Alcance). */
    public int @Nullable [] targetCenter() {
        return center(Kind.TARGET);
    }

    public int @Nullable [] revertCenter() {
        return center(Kind.REVERT);
    }

    public int @Nullable [] updateCenter() {
        return center(Kind.UPDATE);
    }

    /** O Desvincular da linha Editando está armado, esperando o segundo clique (gancho do e2e). */
    public boolean unlinkArmed() {
        return model.unlinkArmed();
    }

    public int @Nullable [] unlinkCenter() {
        return center(Kind.UNLINK);
    }

    /** O "Tirar redes" da linha Editando, ou {@code null} se ele não aparece. */
    public int @Nullable [] idleNetworksCenter() {
        return center(Kind.IDLE_NETWORKS);
    }

    /** O "No mundo" da linha da máquina, ou {@code null} se ele não aparece. */
    public int @Nullable [] highlightCenter() {
        return center(Kind.HIGHLIGHT);
    }

    /** O ✎ da linha Editando, ou {@code null} se ele não aparece. */
    public int @Nullable [] renameCenter() {
        return center(Kind.RENAME);
    }

    /** O nome do alvo está em edição na linha Editando (gancho do e2e). */
    public boolean labelEditing() {
        return label.editing();
    }

    public int @Nullable [] undoCenter() {
        return center(Kind.UNDO);
    }

    private int @Nullable [] center(Kind kind) {
        arrange();
        Hot hot = layout.hot(kind);
        return hot == null ? null : hot.center();
    }

    /** Põe o texto na busca da lista de redes aberta (captura sem teclado). */
    public void previewNetworkQuery(String text) {
        arranged.invalidate();
        lists.previewQuery(text);
    }

    /** Abre ou fecha a lista da linha Rede (captura sem mouse). */
    public void previewNetworkList(boolean open) {
        arranged.invalidate();
        lists.open(open ? Popup.NETWORK : Popup.NONE, 0);
    }
}
