package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLists.Popup;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.packet.FaceActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.OpenFilterPayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.RevertMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import io.github.matheusanbs.wirelessautomate.packet.UnlinkMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.UpdateMachinePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.lwjgl.glfw.GLFW;

/**
 * As edições do {@link NodePanel}: cada clique num controle, na roda ou numa lista aberta vira uma ação que a
 * anfitriã manda ao servidor ({@link NodePanel.Actions}). Não desenha nem guarda estado de interface: lê e marca o
 * {@link PanelModel} e abre e fecha as {@link PanelLists}. A aba e a face escolhidas ficam com o painel, que as lembra.
 */
final class PanelEdits {
    private final PanelModel model;
    private final NodePanel.Actions actions;
    private final PanelLists lists;
    private final TargetLine targetLine;
    /** O que as listas abertas (rede, redstone e alvo) escolhem. */
    final PanelLists.Choice choice = new PanelLists.Choice() {
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

    PanelEdits(PanelModel model, NodePanel.Actions actions, PanelLists lists, TargetLine targetLine) {
        this.model = model;
        this.actions = actions;
        this.lists = lists;
        this.targetLine = targetLine;
    }

    void sendFace(PortMode mode, int priority, RedstoneMode redstone) {
        if (!model.editable(model.view())) {
            return;
        }
        actions.send(new SetFacePayload(actions.containerId(), model.type(), model.face(), mode, priority, redstone));
    }

    /**
     * O segundo clique num botão do cubo, na face já escolhida: o esquerdo avança o modo, o direito volta, Shift põe em
     * Nenhum; um painel só de leitura, ou uma face sem acesso, não muda.
     */
    void cycleMode(int button) {
        FaceView v = model.view();
        if (!model.editable(v)) {
            return;
        }
        PortMode next = Screen.hasShiftDown() ? PortMode.NONE
                : button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? ModeCycle.previous(v.mode()) : ModeCycle.next(v.mode());
        if (next != v.mode()) {
            sendFace(next, v.priority(), v.redstone());
        }
    }

    /** Limpar: dois cliques; com Shift no segundo, as faces de todos os tipos, senão só as do tipo aberto. */
    private void clearFaces() {
        if (model.readOnly() == null && model.clearArm().click(System.currentTimeMillis())) {
            actions.send(FaceActionPayload.clear(actions.containerId(), model.type(), Screen.hasShiftDown()));
        }
    }

    private void copyFace() {
        if (model.canCopy()) {
            model.setCopied(new PanelModel.Copied(model.type(), model.face()));
        }
    }

    private void pasteFace() {
        PanelModel.Copied copied = model.copied();
        if (copied != null && model.canPaste()) {
            actions.send(FaceActionPayload.paste(actions.containerId(), model.type(), copied.face(), model.face()));
        }
    }

    private void stepRedstone(int direction) {
        FaceView v = model.view();
        if (!model.editable(v)) {
            return;
        }
        RedstoneMode[] modes = RedstoneMode.values();
        RedstoneMode next = modes[(v.redstone().ordinal() + direction + modes.length) % modes.length];
        sendFace(v.mode(), v.priority(), next);
    }

    void changePriority(int direction) {
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

    void undo(PanelNoticePayload notice) {
        PanelNotices.clear();
        switch (notice.undo()) {
            case CLEAR_FACE_NETWORK -> actions.send(new SetNetworkPayload(notice.containerId(), notice.resource(),
                    notice.face(), Optional.empty()));
            case UNDO_PASTE -> actions.undoPaste();
            case RESTORE_IDLE_NETWORKS -> actions.clearIdleNetworks(true);
            case NONE -> {
            }
        }
    }

    /** O clique esquerdo num controle do painel, sem lista aberta. */
    void click(Hot hot) {
        FaceView v = model.view();
        RangeView.MachineView machine = model.targetMachine();
        boolean edit = model.readOnly() == null;
        switch (hot.kind()) {
            case CLEAR -> clearFaces();
            case COPY -> copyFace();
            case PASTE -> pasteFace();
            case REDSTONE_PREV -> stepRedstone(-1);
            case REDSTONE_NEXT -> stepRedstone(1);
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
                if (edit) {
                    lists.open(Popup.TARGET, lists.targetScroll());
                }
            }
            case RENAME -> {
                if (edit) {
                    targetLine.startRename();
                }
            }
            case IDLE_NETWORKS -> {
                if (edit) {
                    actions.clearIdleNetworks(false);
                }
            }
            case HIGHLIGHT -> targetLine.toggleHighlight();
            case REVERT -> {
                if (edit && machine != null) {
                    actions.send(new RevertMachinePayload(actions.containerId(), model.snapshot().target().id(),
                            machine.pos()));
                }
            }
            case UPDATE -> {
                if (edit && machine != null) {
                    actions.send(UpdateMachinePayload.machine(actions.containerId(), model.snapshot().target().id(),
                            machine.pos()));
                }
            }
            case UNLINK -> {
                if (edit && machine != null && model.unlinkArm().click(System.currentTimeMillis())) {
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
            // a aba e a face mudam a escolha do painel: o NodePanel as trata
            case TAB, FACE, SUMMARY, FILTER, CARDS -> {
            }
        }
    }
}
