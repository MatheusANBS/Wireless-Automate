package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O que o painel do nó ({@link NodePanel}) mostra e o que ele lê do snapshot: a aba, a face escolhida, o
 * alvo de edição e o modo só leitura, com as leituras derivadas (lado relativo, rede da face, acesso,
 * resumo do filtro) e os textos. Sem desenho e sem geometria.
 */
final class PanelModel {
    private RouterSnapshot snapshot;
    private ResourceType type = ResourceType.ITEM;
    private Direction face;
    private EditTarget shownTarget;
    private @Nullable Component readOnly;
    private boolean targetFromList;
    private final ArmedClick unlinkArm = new ArmedClick();
    /** O Limpar do quadro das faces, que pede dois cliques. */
    private final ArmedClick clearArm = new ArmedClick();
    /** A face copiada pelo Copiar (tipo e face, no quadro da tela), ou {@code null}. */
    private @Nullable Copied copied;

    /** O que o Copiar guardou: a face e o tipo; o Colar copia dela para a face escolhida depois, no mesmo alvo. */
    record Copied(ResourceType type, Direction face) {
    }

    PanelModel(RouterSnapshot snapshot, Direction face) {
        this.snapshot = snapshot;
        this.face = face;
        this.shownTarget = snapshot.target();
        if (!LoadedTypes.LIST.isEmpty()) {
            this.type = LoadedTypes.LIST.getFirst();
        }
    }

    // ------------------------------------------------------------------ estado

    /**
     * Troca o snapshot. Se o alvo de edição mudou, a face passa à que o servidor escolheu para ele e a
     * resposta é {@code true} (o painel fecha a lista aberta e o aviso).
     */
    boolean setSnapshot(RouterSnapshot snapshot) {
        this.snapshot = snapshot;
        if (!snapshot.target().equals(shownTarget)) {
            shownTarget = snapshot.target();
            unlinkArm.disarm();
            clearArm.disarm();
            copied = null;
            face = snapshot.range().present() ? snapshot.range().face() : snapshot.facing();
            return true;
        }
        return false;
    }

    RouterSnapshot snapshot() {
        return snapshot;
    }

    ResourceType type() {
        return type;
    }

    void setType(ResourceType type) {
        this.type = type;
    }

    /** A face escolhida, no quadro da tela (absoluta na presa, canônica num alvo do alcance). */
    Direction face() {
        return face;
    }

    void setFace(Direction face) {
        this.face = face;
    }

    /** O motivo do só leitura, ou {@code null} quando dá para editar. */
    @Nullable Component readOnly() {
        return readOnly;
    }

    void setReadOnly(@Nullable Component reason) {
        this.readOnly = reason;
    }

    /** O alvo se escolhe na lista das máquinas, à vista ao lado: a linha Editando não aparece. */
    boolean targetFromList() {
        return targetFromList;
    }

    void setTargetFromList(boolean fromList) {
        this.targetFromList = fromList;
    }

    /** O Desvincular da linha Editando, que pede dois cliques; desarma ao trocar de alvo. */
    ArmedClick unlinkArm() {
        return unlinkArm;
    }

    boolean unlinkArmed() {
        return unlinkArm.armed(System.currentTimeMillis());
    }

    ArmedClick clearArm() {
        return clearArm;
    }

    boolean clearArmed() {
        return clearArm.armed(System.currentTimeMillis());
    }

    @Nullable Copied copied() {
        return copied;
    }

    void setCopied(@Nullable Copied copied) {
        this.copied = copied;
    }

    /** O Copiar vale: painel editável e a face escolhida configurável. */
    boolean canCopy() {
        return editable(view());
    }

    /** O Colar vale: algo copiado neste tipo, para outra face, e a face escolhida configurável. */
    boolean canPaste() {
        return copied != null && copied.type() == type && copied.face() != face && editable(view());
    }

    // ------------------------------------------------------------------ leituras

    FaceView view() {
        return view(face);
    }

    FaceView view(Direction direction) {
        return snapshot.face(type, direction);
    }

    RangeView range() {
        return snapshot.range();
    }

    boolean rangeShown() {
        return range().present();
    }

    /** A máquina escolhida tem esta face diferente da do perfil do grupo (a exceção dela). */
    boolean differsFromGroup(Direction direction) {
        return remote() && targetMachine() != null && range().differs(type, sideOf(direction));
    }

    /** O alvo é um grupo ou uma máquina do alcance (faces no quadro canônico). */
    boolean remote() {
        return rangeShown() && !snapshot.target().attached();
    }

    RangeView.@Nullable MachineView targetMachine() {
        EditTarget target = snapshot.target();
        return rangeShown() && target.kind() == EditTarget.Kind.MACHINE ? range().machine(target.id()) : null;
    }

    RangeView.@Nullable GroupView targetGroup() {
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
    boolean remoteLoaded() {
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
    boolean editable(FaceView view) {
        return readOnly == null && (view.available() || remote());
    }

    /** O lado relativo à frente da máquina da face da tela {@code direction}. */
    RemoteSide sideOf(Direction direction) {
        return remote() ? RemoteSide.fromCanonical(direction.ordinal())
                : RemoteSide.fromAbsolute(snapshot.attachedFront(), direction.ordinal());
    }

    /** A face da tela do lado {@code side}: absoluta na presa, canônica num alvo do alcance. */
    Direction faceOf(RemoteSide side) {
        return Direction.values()[remote() ? side.canonical() : side.toAbsolute(snapshot.attachedFront())];
    }

    /** A face onde o roteador está preso (só na máquina presa). */
    boolean attached(Direction direction) {
        return !remote() && direction == snapshot.facing();
    }

    @Nullable NetworkEntry network(Direction direction) {
        Optional<UUID> id = snapshot.network(type, direction);
        return id.isEmpty() ? null : snapshot.entry(id.get());
    }

    Component networkLabel(Direction direction) {
        NetworkEntry entry = network(direction);
        if (entry != null) {
            return Component.literal(entry.name());
        }
        return snapshot.network(type, direction).isPresent() ? tr("network.unknown") : tr("network.none");
    }

    /** A face tem modo e está sem rede: a linha Rede pede a escolha explícita. */
    boolean needsNetwork(FaceView view) {
        return view.mode() != PortMode.NONE && view.network().isEmpty() && (view.available() || remote());
    }

    Component networkSummary() {
        int count = snapshot.networks(type).size();
        if (count == 0) {
            return tr("network.none");
        }
        return count == 1 ? tr("networks.count.one") : tr("networks.count", count);
    }

    /** Dica do resumo: cada face com rede, "Cima: Mina · Baixo: Armazém", na ordem dos quadradinhos. */
    Component networkSummaryTooltip() {
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

    // ------------------------------------------------------------------ textos

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    static Component panelTr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.panel." + key, args);
    }

    Component sideName(Direction direction) {
        return tr("side." + sideOf(direction).key());
    }

    static Component sideName(RemoteSide side) {
        return tr("side." + side.key());
    }

    static Component modeName(PortMode mode) {
        return tr("mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    static Component redstoneName(RedstoneMode mode) {
        return tr("redstone." + mode.name().toLowerCase(Locale.ROOT));
    }

    /** O que a face acessa: "27 slots", "1 tanque", "energia" ou "sem acesso". */
    Component access(FaceView view) {
        if (!view.available() && remote() && !remoteLoaded()) {
            // num grupo sem máquina carregada, a trocada diz mais que o chunk
            RangeView.MachineView machine = targetMachine();
            RangeView.GroupView group = targetGroup();
            RangeView.MachineState state = machine != null ? machine.state()
                    : group != null && RangeRowButtons.replaced(range(), group.id()) > 0
                            ? RangeView.MachineState.REPLACED : null;
            return state == RangeView.MachineState.MISSING ? tr("range.access.missing")
                    : state == RangeView.MachineState.REPLACED ? tr("range.access.replaced") : tr("range.access.unloaded");
        }
        if (!view.available()) {
            return tr("access.none");
        }
        return ResourceStyle.access(type, view.slots());
    }

    boolean hasFilter() {
        return RouterFaceFilterTarget.hasFilter(type);
    }

    /**
     * "Passa tudo", "12 entradas", "2 cartões" ou "12 entradas · 2 cartões" (cartões só com os slots:
     * {@code cardCount} negativo é a tela sem eles).
     */
    Component filterSummary(FaceView view, int cardCount) {
        int cards = Math.max(0, cardCount);
        Component entries = view.filterSize() == 1 ? tr("filter.count.one") : tr("filter.count", view.filterSize());
        if (cards == 0) {
            return view.filterSize() == 0 ? panelTr("filter.none") : entries;
        }
        Component cardText = cards == 1 ? tr("filter.cards.one") : tr("filter.cards", cards);
        return view.filterSize() == 0 ? cardText : tr("filter.summary", entries, cardText);
    }

    /** O nome do alvo de edição: a máquina presa, o grupo ("Fornalha (todas)") ou a máquina ("Fornalha #3"). */
    Component targetName(EditTarget target) {
        return switch (target.kind()) {
            case ATTACHED -> snapshot.attachedLabel().isEmpty() ? panelTr("target.attached")
                    : Component.literal(snapshot.attachedLabel());
            case GROUP -> {
                RangeView.GroupView group = range().group(target.id());
                yield panelTr("target.group", group == null ? Component.literal("?") : MachineTexts.groupName(group));
            }
            case MACHINE -> MachineTexts.machineName(range(), target.id());
        };
    }

    /** A frase do modo da face escolhida, nesta máquina (ou por que não dá para escolher). */
    Component phrase() {
        FaceView v = view();
        if (readOnly == null && !editable(v)) {
            return tr("mode.unavailable");
        }
        return tr("mode." + v.mode().name().toLowerCase(Locale.ROOT) + ".tooltip");
    }
}
