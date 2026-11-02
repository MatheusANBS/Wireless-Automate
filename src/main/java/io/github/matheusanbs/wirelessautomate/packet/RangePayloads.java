package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.MachineCopyPaste;
import io.github.matheusanbs.wirelessautomate.menu.RouterEditHost;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * Os pacotes do roteador de alcance que mexem num grupo inteiro ou no perfil por máquina: Desvincular o grupo e
 * Copiar e Colar (com o Desfazer), e os avisos deles e do Atualizar. Todos agem só com um {@link RouterEditHost}
 * aberto ({@link ModPayloads#host}), no alvo conferido.
 */
public final class RangePayloads {
    private static final String NOTICE = "gui.wirelessautomate.panel.notice.range.";

    private RangePayloads() {
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToServer(UnlinkGroupPayload.TYPE, UnlinkGroupPayload.STREAM_CODEC,
                (payload, context) -> handleUnlinkGroup(serverPlayer(context), payload));
        registrar.playToServer(MachineCopyPayload.TYPE, MachineCopyPayload.STREAM_CODEC,
                (payload, context) -> handleMachineCopy(serverPlayer(context), payload));
        registrar.playToServer(MachinePastePayload.TYPE, MachinePastePayload.STREAM_CODEC,
                (payload, context) -> handleMachinePaste(serverPlayer(context), payload));
    }

    private static @Nullable ServerPlayer serverPlayer(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }

    /**
     * Desvincula o grupo inteiro, se ele ainda existe com o id e o bloco que a tela viu e o Upgrade de Alcance vale
     * ({@link RouterBlockEntity#unlinkGroup}): as remotas saem; o grupo sem a presa sai, o da presa fica zerado; os
     * cartões vão para o jogador. Depois, o alvo vira a presa (se o grupo era o dela) ou o que ainda vale. Devolve o
     * aviso mandado, ou {@code null} se recusou ou nada mudou.
     */
    public static @Nullable PanelNoticePayload handleUnlinkGroup(@Nullable ServerPlayer player,
            UnlinkGroupPayload payload) {
        RouterEditHost host = ModPayloads.host(player, payload.containerId());
        if (host == null || !host.editedRouter().rangeActive()) {
            return null;
        }
        RouterBlockEntity router = host.editedRouter();
        MachineGroup group = router.remoteMachines().group(payload.group());
        if (group == null || !group.block().equals(payload.block())) {
            return null;
        }
        RemoteMachines.GroupUnlink result = router.unlinkGroup(group.id(), player);
        if (result == null || !result.changed()) {
            return null;
        }
        if (result.kept()) {
            host.selectTarget(EditTarget.ATTACHED_TARGET, null, null);
        } else {
            host.validTarget();
        }
        host.editApplied();
        PanelNoticePayload notice = unlinkedGroupNotice(result);
        ModPayloads.sendNotice(player, notice);
        return notice;
    }

    /**
     * O aviso do Desvincular grupo, pelo resultado: "Desvinculadas N máquinas · M cartões devolvidos", só com as
     * partes que houve; sem nenhuma, "Grupo apagado" (ou, no grupo da presa, que fica, "Perfil do grupo zerado").
     */
    public static PanelNoticePayload unlinkedGroupNotice(RemoteMachines.GroupUnlink result) {
        int machines = result.machines();
        MutableComponent message = null;
        if (machines > 0) {
            message = Component.translatable(NOTICE + (machines == 1 ? "unlinked.one" : "unlinked"), machines);
        }
        message = withCards(message, result.cards().size());
        if (message == null) {
            message = Component.translatable(NOTICE + (result.kept() ? "group_cleared" : "group_removed"));
        }
        return PanelNoticePayload.plain(message);
    }

    /** O aviso do Atualizar, pelo resultado: "Atualizadas N máquinas" e, com cartões devolvidos, "· M cartões devolvidos". */
    public static PanelNoticePayload updatedNotice(RemoteMachines.Regroup result) {
        int machines = result.destinations().size();
        return PanelNoticePayload.plain(withCards(
                Component.translatable(NOTICE + (machines == 1 ? "updated.one" : "updated"), machines),
                result.cards().size()));
    }

    /** {@code message} com "· M cartões devolvidos" (ou só essa parte, sem {@code message}); sem cartões, igual. */
    private static @Nullable MutableComponent withCards(@Nullable MutableComponent message, int cards) {
        if (cards <= 0) {
            return message;
        }
        MutableComponent part = Component.translatable(NOTICE + (cards == 1 ? "cards_returned.one" : "cards_returned"),
                cards);
        return message == null ? part : message.append(" · ").append(part);
    }

    /** Copia o perfil do alvo de edição ({@link MachineCopyPaste#copy}) ou, com {@code forget}, esquece a cópia. */
    public static boolean handleMachineCopy(@Nullable ServerPlayer player, MachineCopyPayload payload) {
        RouterEditHost host = ModPayloads.host(player, payload.containerId());
        if (host == null) {
            return false;
        }
        if (payload.forget()) {
            MachineCopyPaste.forget(player, host);
            return true;
        }
        return MachineCopyPaste.copy(player, host);
    }

    /**
     * Cola a cópia do jogador no alvo de edição ({@link MachineCopyPaste#paste}) e manda o aviso, com o Desfazer se
     * algo mudou (sem máquina na frente da presa, a recusa); com {@code undo}, o Desfazer
     * ({@link MachineCopyPaste#undo}). Devolve se mudou algo.
     */
    public static boolean handleMachinePaste(@Nullable ServerPlayer player, MachinePastePayload payload) {
        RouterEditHost host = ModPayloads.host(player, payload.containerId());
        if (host == null) {
            return false;
        }
        if (payload.undo()) {
            PanelNoticePayload notice = MachineCopyPaste.undo(player, host);
            if (notice != null) {
                ModPayloads.sendNotice(player, notice);
            }
            return notice != null;
        }
        MachineCopyPaste.Pasted pasted = MachineCopyPaste.paste(player, host, payload.wholeGroup());
        if (pasted == null) {
            return false;
        }
        ModPayloads.sendNotice(player, pasted.notice(host.containerId()));
        return pasted.changed();
    }
}
