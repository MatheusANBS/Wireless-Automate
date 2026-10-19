package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.packet.ClearIdleNetworksPayload;
import io.github.matheusanbs.wirelessautomate.packet.RevertMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.UnlinkMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.UpdateMachinePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A coluna do visor da tela do roteador: o visor 3D da máquina ({@link MachineView3D}) e, com o Upgrade de
 * Alcance, o botão "Máquinas · 3/8" e a lista das máquinas ({@link RangeList}), ao lado do visor ou no
 * lugar dele. Escolher um grupo ou uma máquina na lista pede ao servidor a troca do alvo de edição, e os
 * botões da linha escolhida ({@link RangeRowButtons}) pedem Atualizar, Voltar ao perfil e Desvincular. O ✎ e os dois
 * cliques no nome abrem a caixa do nome ({@link LabelEditor}) no lugar dele.
 */
final class MachineColumn {
    /**
     * Mostra a lista das máquinas no lugar do visor. Estático de propósito, como o tamanho da tela: a
     * escolha vale para a sessão inteira, então a próxima tela de um roteador com o upgrade já abre na
     * lista (ou no visor) como o jogador deixou.
     */
    private static boolean showList;

    private final Supplier<RouterSnapshot> snapshot;
    private final NodePanel panel;
    private final RouterLayout layout;
    private final Consumer<CustomPacketPayload> sender;
    private final IntSupplier containerId;
    private final MachineView3D machineView;
    private final Function<Direction, FaceView> faceViews = this::visorView;
    private final RangeList rangeList = new RangeList();
    private final LabelEditor labelEditor = new LabelEditor(this::sendRename);
    private FlatButton machinesButton;

    /**
     * @param sender manda o pedido ao servidor, ou, numa captura sem servidor, só muda o snapshot local (o
     *               {@link RouterPanelActions}, o mesmo do painel)
     */
    MachineColumn(Supplier<RouterSnapshot> snapshot, NodePanel panel, RouterLayout layout,
            Consumer<CustomPacketPayload> sender, IntSupplier containerId) {
        this.snapshot = snapshot;
        this.panel = panel;
        this.layout = layout;
        this.sender = sender;
        this.containerId = containerId;
        this.machineView = new MachineView3D(panel::selectFace);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    // ------------------------------------------------------------------ estado

    private FaceView view(Direction direction) {
        return snapshot.get().face(panel.type(), direction);
    }

    /** A face no visor: num alvo remoto sem máquina carregada, as faces não aparecem apagadas. */
    private FaceView visorView(Direction direction) {
        FaceView v = view(direction);
        if (remote() && !v.available() && !remoteLoaded()) {
            return new FaceView(v.mode(), v.priority(), v.redstone(), 0, v.filterSize(), v.blacklist(), v.network());
        }
        return v;
    }

    RangeView range() {
        return snapshot.get().range();
    }

    /** O upgrade de alcance vale: a tela mostra o botão Máquinas e a lista. */
    boolean rangeShown() {
        return range().present();
    }

    /** O alvo é um grupo ou uma máquina remota (lados relativos, sem o roteador no visor). */
    boolean remote() {
        return rangeShown() && !snapshot.get().target().attached();
    }

    /** A lista está na tela: ao lado do visor, ou no lugar dele. */
    boolean listShown() {
        return rangeShown() && (showList || layout.sideBySide());
    }

    /** O visor está na tela: sem o upgrade, ao lado da lista, ou com a lista fechada. */
    boolean viewShown() {
        return !rangeShown() || layout.sideBySide() || !showList;
    }

    /** Há uma máquina carregada no alvo remoto (a escolhida, ou alguma do grupo). */
    private boolean remoteLoaded() {
        EditTarget target = snapshot.get().target();
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

    /** O lado relativo da face {@code direction} da tela, para o rótulo do visor e o dos cartões. */
    String sideKey(Direction direction) {
        return (remote() ? RemoteSide.fromCanonical(direction.ordinal())
                : RemoteSide.fromAbsolute(snapshot.get().attachedFront(), direction.ordinal())).key();
    }

    // ------------------------------------------------------------------ montagem

    /** O botão Máquinas no topo da coluna (a tela o põe entre os widgets). */
    FlatButton createMachinesButton(int left, int top) {
        machinesButton = new FlatButton(left + RouterLayout.X0, top + layout.columnsY(), layout.lw(),
                RouterLayout.RANGE_STRIP - 2, tr("range.machines", 0, 0), this::paintMachines, () -> {
                    // lado a lado não há o que alternar: o botão é só o título
                    if (!layout.sideBySide()) {
                        showList = !showList;
                    }
                }).tooltip(this::machinesTooltip);
        return machinesButton;
    }

    /** Visibilidade, texto e posições pelo snapshot e pelo tamanho atuais. */
    void refresh(int left, int top) {
        machinesButton.visible = rangeShown();
        machinesButton.setMessage(tr("range.machines", range().used(), range().max()));
        machineView.setBounds(left + layout.viewX() + 1, top + layout.boxY() + 1, layout.viewBoxW() - 2,
                layout.boxH() - 2);
        rangeList.setBounds(left + RouterLayout.X0, top + layout.boxY(), layout.listW(), layout.boxH());
        if (rangeShown()) {
            rangeList.sync(range(), snapshot.get().target(), IdleNetworks.shown(snapshot.get()));
        }
    }

    // ------------------------------------------------------------------ ações

    /** O pedido ao servidor (numa captura sem servidor, o {@code sender} decide); {@code null} não manda nada. */
    private void send(@Nullable CustomPacketPayload payload) {
        if (payload != null) {
            sender.accept(payload);
        }
    }

    private void sendRename(EditTarget target, @Nullable BlockPos machinePos, String name) {
        send(LabelEditor.payload(containerId.getAsInt(), target, machinePos, name));
    }

    /** Abre a caixa do nome no lugar do nome da linha {@code row}, com o nome dado agora. */
    private void startRename(RangeList.Row row) {
        if (row.kind() == RangeList.Kind.GROUP) {
            RangeView.GroupView group = range().group(row.group());
            if (group != null) {
                labelEditor.start(row.target(), null, group.name(), RangeList.groupName(group));
            }
        } else if (row.kind() == RangeList.Kind.MACHINE) {
            RangeView.MachineView machine = range().machine(row.machine());
            if (machine != null) {
                labelEditor.start(row.target(), machine.pos(), machine.label(),
                        RangeList.blockName(range(), row.machine()));
            }
        }
    }

    /** Linha da lista clicada: o servidor troca o alvo e o snapshot seguinte traz o alvo e a face novos. */
    private void selectTarget(RangeList.Row row) {
        int id = containerId.getAsInt();
        RangeView.GroupView group = range().group(row.group());
        RangeView.MachineView machine = range().machine(row.machine());
        send(switch (row.kind()) {
            case ATTACHED -> SelectTargetPayload.attached(id);
            case GROUP -> group == null ? null : SelectTargetPayload.group(id, group.id(), group.block());
            case MACHINE -> machine == null ? null : SelectTargetPayload.machine(id, row.machine(), machine.pos());
        });
    }

    /** Um botão da linha escolhida: o servidor confere o que a tela viu e age; o snapshot seguinte mostra. */
    private void rowAction(RangeList.Row row, RangeRowButtons.Action action) {
        if (action == RangeRowButtons.Action.RENAME) {
            startRename(row);
            return;
        }
        int id = containerId.getAsInt();
        if (row.kind() == RangeList.Kind.GROUP) {
            RangeView.GroupView group = range().group(row.group());
            if (group != null && action == RangeRowButtons.Action.UPDATE) {
                send(UpdateMachinePayload.group(id, group.id(), group.block()));
            } else if (action == RangeRowButtons.Action.CLEAR_NETWORKS) {
                send(new ClearIdleNetworksPayload(id, false));
            }
            return;
        }
        RangeView.MachineView machine = range().machine(row.machine());
        if (row.kind() != RangeList.Kind.MACHINE || machine == null) {
            return;
        }
        send(switch (action) {
            case UPDATE -> UpdateMachinePayload.machine(id, row.machine(), machine.pos());
            case REVERT -> new RevertMachinePayload(id, row.machine(), machine.pos());
            case UNLINK -> new UnlinkMachinePayload(id, row.machine(), machine.pos());
            case CLEAR_NETWORKS -> new ClearIdleNetworksPayload(id, false);
            case RENAME -> null;
        });
    }

    // ------------------------------------------------------------------ entrada

    /**
     * Um clique com o nome de uma linha em edição: dentro da caixa, fica nela ({@code true}); fora, salva o nome e
     * o clique segue. A tela chama antes de repassar o clique a qualquer parte.
     */
    boolean labelClick(double mouseX, double mouseY, int button) {
        return labelEditor.mouseClicked(mouseX, mouseY, button);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return labelEditor.keyPressed(keyCode, scanCode, modifiers);
    }

    boolean charTyped(char codePoint, int modifiers) {
        return labelEditor.charTyped(codePoint, modifiers);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listShown() && button == 0
                && rangeList.click(Minecraft.getInstance().font, range(), snapshot.get().target(), mouseX, mouseY,
                        this::selectTarget, this::rowAction)) {
            return true;
        }
        return viewShown() && machineView.mouseClicked(mouseX, mouseY, button);
    }

    boolean mouseDragged(double mouseX, double mouseY, int button) {
        return viewShown() && machineView.mouseDragged(mouseX, mouseY, button);
    }

    /** Soltar fora do visor também encerra o giro. */
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return machineView.mouseReleased(mouseX, mouseY, button);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (listShown() && rangeList.scroll(range(), mouseX, mouseY, scrollY)) {
            return true;
        }
        return viewShown() && machineView.mouseScrolled(mouseX, mouseY, scrollY);
    }

    // ------------------------------------------------------------------ dicas

    /** Dica do botão Máquinas: o que ele alterna, e o raio e o limite do tier. */
    private Component machinesTooltip() {
        Component detail = tr("range.machines.detail", range().radius(), range().max());
        if (layout.sideBySide()) {
            return detail;
        }
        return tr(showList ? "range.machines.tooltip.view" : "range.machines.tooltip.list").copy()
                .append("\n").append(detail.copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /**
     * As dicas do visor fora das faces e da linha da lista sob o mouse, nessa ordem, por {@code set};
     * devolve se alguma foi posta.
     */
    boolean tooltips(int mouseX, int mouseY, Consumer<Component> set) {
        // sobre uma face o visor mostra o nome dela embaixo; a dica cobriria o modelo
        boolean machineTip = viewShown() && !machineView.isDragging() && machineView.contains(mouseX, mouseY)
                && machineView.hoveredFace() == null;
        if (machineTip) {
            set.accept(machineTooltip());
        }
        Component hint = labelEditor.tooltip(mouseX, mouseY);
        if (hint != null) {
            set.accept(hint);
            return true;
        }
        RangeList.Row row = listShown() ? rangeList.rowAt(range(), mouseX, mouseY) : null;
        if (row != null) {
            machineTip = true;
            Component button = rangeList.buttonTooltip(Minecraft.getInstance().font, range(), snapshot.get().target(),
                    mouseX, mouseY);
            set.accept(button != null ? button : rangeList.tooltip(range(), row, attachedName()));
        }
        return machineTip;
    }

    /** O item da máquina presa (para a linha da lista), pelo mundo do cliente: o snapshot traz o do alvo. */
    private ItemStack attachedIcon() {
        RouterSnapshot s = snapshot.get();
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

    /** O nome da máquina presa, para a dica da linha dela na lista. */
    private Component attachedName() {
        ItemStack icon = attachedIcon();
        return icon.isEmpty() ? tr("machine.none") : icon.getHoverName();
    }

    /** Dica do visor fora das faces: a máquina e como usar o visor. */
    private Component machineTooltip() {
        ItemStack machine = snapshot.get().machine();
        Component name = machine.isEmpty() ? tr("machine.none") : machine.getHoverName();
        return name.copy().append("\n").append(tr("view.hint").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    // ------------------------------------------------------------------ desenho

    /** A lista e o visor que estão na tela; {@code pointerX} -1 é sem realce sob o mouse. */
    void render(GuiGraphics g, Font font, int left, int top, int pointerX, int pointerY) {
        RouterSnapshot s = snapshot.get();
        if (listShown()) {
            placeLabelEditor();
            rangeList.render(g, font, range(), s.target(), attachedIcon(), pointerX, pointerY);
            labelEditor.render(g, pointerX, pointerY);
        } else {
            labelEditor.finish(true);
        }
        if (viewShown()) {
            renderView(g, font, s, left, top, pointerX, pointerY);
        }
    }

    /** O visor: num alvo remoto só o bloco dele, com o anel da face escolhida na cor da rede dela. */
    private void renderView(GuiGraphics g, Font font, RouterSnapshot s, int left, int top, int pointerX,
            int pointerY) {
        int vx = left + layout.viewX();
        int vy = top + layout.boxY();
        int viewW = layout.viewBoxW();
        int viewH = layout.boxH();
        GuiPaint.paper(g, vx + 1, vy + 1, viewW - 2, viewH - 2);
        GuiPaint.outline(g, vx, vy, viewW, viewH, GuiPaint.LINE);
        Optional<UUID> id = s.network(panel.type(), panel.face());
        NetworkEntry selected = id.isEmpty() ? null : s.entry(id.get());
        machineView.render(g, s.machineState(), remote() ? null : s.facing(), s.tier(), faceViews, panel.face(),
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
            RangeView.MachineView machine = s.target().kind() == EditTarget.Kind.MACHINE
                    ? range().machine(s.target().id()) : null;
            boolean groupReplaced = s.target().kind() == EditTarget.Kind.GROUP
                    && RangeRowButtons.replaced(range(), s.target().id()) > 0;
            RangeView.MachineState state = machine != null ? machine.state()
                    : groupReplaced ? RangeView.MachineState.REPLACED : RangeView.MachineState.UNLOADED;
            Component label = switch (state) {
                case MISSING -> tr("range.access.missing");
                case REPLACED -> tr("range.access.replaced");
                default -> tr("range.access.unloaded");
            };
            g.fill(vx + 1, vy + viewH - 13, vx + 7 + Math.min(font.width(label), viewW - 10), vy + viewH - 1,
                    GuiPaint.alpha(GuiPaint.INSET, 0xD8));
            GuiText.draw(g, font, label, vx + 4, vy + viewH - 11, viewW - 10,
                    state == RangeView.MachineState.REPLACED ? GuiPaint.BRASS : GuiPaint.WARN);
        }
    }

    /**
     * A caixa do nome no lugar do nome da linha em edição, onde ela está agora; se a linha saiu da vista (rolagem,
     * grupo fechado) ou sumiu, salva e fecha.
     */
    private void placeLabelEditor() {
        EditTarget editing = labelEditor.target();
        int[] box = editing == null ? null : rangeList.nameBox(range(), editing);
        if (editing != null && box == null) {
            labelEditor.finish(true);
            editing = null;
        } else if (box != null) {
            labelEditor.place(box[0], box[1], box[2]);
        }
        rangeList.editing(editing);
    }

    /** Botão Máquinas: seta (lista aberta ou fechada), o nome e as vagas usadas. */
    private void paintMachines(GuiGraphics g, FlatButton b, boolean hovered) {
        Font font = Minecraft.getInstance().font;
        if (layout.sideBySide()) {
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

    // ------------------------------------------------------------------ ganchos do e2e e das capturas

    /** A face da máquina sob o mouse no visor. */
    @Nullable Direction hoveredFace() {
        return machineView.hoveredFace();
    }

    int @Nullable [] rangeRowCenter(EditTarget target) {
        return listShown() ? rangeList.rowCenter(range(), target) : null;
    }

    int @Nullable [] rangeButtonCenter(RangeRowButtons.Action action) {
        return listShown() ? rangeList.buttonCenter(Minecraft.getInstance().font, range(), snapshot.get().target(), action)
                : null;
    }

    /** O nome de uma linha da lista está em edição. */
    boolean labelEditing() {
        return labelEditor.editing();
    }

    /** O ✕ do alvo na lista está armado. */
    boolean rangeUnlinkArmed() {
        return listShown() && rangeList.unlinkArmed(snapshot.get().target());
    }

    int @Nullable [] rangeArrowPoint(int group) {
        return listShown() ? rangeList.arrowPoint(range(), group) : null;
    }

    /** Visor no ângulo inicial do snapshot atual. */
    void previewViewDefault() {
        machineView.previewReset(remote() ? null : snapshot.get().facing(), snapshot.get().machineState());
    }

    /** Visor num ângulo fixo (graus) e zoom. */
    void previewView(float yaw, float pitch, float zoom) {
        previewViewDefault();
        machineView.previewAngles(yaw, pitch, zoom);
    }

    /** Ponto da tela no centro da face da máquina no visor. */
    int[] previewViewFaceCenter(Direction direction) {
        return machineView.previewProject(direction, remote() ? null : snapshot.get().facing(),
                snapshot.get().machineState());
    }
}
