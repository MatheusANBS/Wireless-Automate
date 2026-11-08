package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.packet.ClearIdleNetworksPayload;
import io.github.matheusanbs.wirelessautomate.packet.MachineCopyPayload;
import io.github.matheusanbs.wirelessautomate.packet.MachinePastePayload;
import io.github.matheusanbs.wirelessautomate.packet.RevertGroupPayload;
import io.github.matheusanbs.wirelessautomate.packet.RevertMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.UnlinkGroupPayload;
import io.github.matheusanbs.wirelessautomate.packet.UnlinkMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.UpdateMachinePayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Os pedidos ao servidor da lista das máquinas da tela do roteador: escolher uma linha, os botões da linha escolhida
 * ({@link RangeRowButtons}) e as teclas de copiar e colar ({@link CopyFooter#key}). Só monta o pacote com o que a
 * tela viu (o id e o bloco do grupo, a posição da máquina), que o servidor confere; quem manda é a coluna
 * ({@link MachineColumn}). O ✎ não passa por aqui: ele abre a caixa do nome na tela.
 */
final class RangeRequests {
    private RangeRequests() {
    }

    /** Linha da lista clicada: o servidor troca o alvo e o snapshot seguinte traz o alvo e a face novos. */
    static @Nullable CustomPacketPayload select(int id, RangeView range, RangeList.Row row) {
        RangeView.GroupView group = range.group(row.group());
        RangeView.MachineView machine = range.machine(row.machine());
        return switch (row.kind()) {
            case ATTACHED -> SelectTargetPayload.attached(id);
            case GROUP -> group == null ? null : SelectTargetPayload.group(id, group.id(), group.block());
            case MACHINE -> machine == null ? null : SelectTargetPayload.machine(id, row.machine(), machine.pos());
        };
    }

    /** O pedido de uma tecla ({@link CopyFooter#key}): copiar ou colar no alvo escolhido, ou no grupo inteiro dele. */
    static CustomPacketPayload key(int id, CopyFooter.Request request) {
        return switch (request) {
            case COPY -> new MachineCopyPayload(id, false);
            case PASTE -> MachinePastePayload.paste(id, false);
            case PASTE_GROUP -> MachinePastePayload.paste(id, true);
        };
    }

    /**
     * Um botão da linha escolhida {@code row} ({@code shift}: Shift seguro, o ⎘ cola no grupo inteiro); {@code null}
     * quando não há o que pedir (a linha sumiu da visão).
     */
    static @Nullable CustomPacketPayload action(int id, RouterSnapshot snapshot, RangeList.Row row,
            RangeRowButtons.Action action, boolean shift) {
        if (action == RangeRowButtons.Action.COPY) {
            return new MachineCopyPayload(id, false);
        }
        if (action == RangeRowButtons.Action.PASTE) {
            return MachinePastePayload.paste(id, shift || RangeRowButtons.e2eShift);
        }
        if (action == RangeRowButtons.Action.CLEAR_NETWORKS) {
            return new ClearIdleNetworksPayload(id, false);
        }
        RangeView range = snapshot.range();
        if (row.kind() == RangeList.Kind.GROUP) {
            RangeView.GroupView group = range.group(row.group());
            if (group == null) {
                return null;
            }
            return switch (action) {
                case UPDATE -> UpdateMachinePayload.group(id, group.id(), group.block());
                case REVERT_GROUP -> new RevertGroupPayload(id, group.id(), group.block());
                case UNLINK -> new UnlinkGroupPayload(id, group.id(), group.block());
                default -> null;
            };
        }
        if (row.kind() == RangeList.Kind.ATTACHED) {
            return action == RangeRowButtons.Action.REVERT
                    ? RevertMachinePayload.attached(id, snapshot.pos().relative(snapshot.facing().getOpposite())) : null;
        }
        RangeView.MachineView machine = range.machine(row.machine());
        if (machine == null) {
            return null;
        }
        return switch (action) {
            case UPDATE -> UpdateMachinePayload.machine(id, row.machine(), machine.pos());
            case REVERT -> new RevertMachinePayload(id, row.machine(), machine.pos());
            case UNLINK -> new UnlinkMachinePayload(id, row.machine(), machine.pos());
            default -> null;
        };
    }
}
