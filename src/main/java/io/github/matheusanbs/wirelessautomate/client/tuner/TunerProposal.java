package io.github.matheusanbs.wirelessautomate.client.tuner;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorCancelPayload;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorProposalPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Só cliente: a proposta do Afinador que o servidor mandou ({@link ConfiguratorProposalPayload}), para a pintura nas
 * faces da máquina ({@link TunerPaint}). Guarda o mundo em que chegou e o prazo em ticks do jogo desse mundo; sair do
 * mundo, o prazo passar ou a máquina virar ar (quebrada) a apaga sem pacote, porque a do servidor também não vale
 * mais. Mirar outro bloco por {@link #AIM_AWAY_TICKS} ticks ou renascer (o {@code Clone}, depois da morte) a apaga e
 * avisa o servidor ({@link ConfiguratorCancelPayload}), que descarta a dele: a proposta some nos dois, e o próximo
 * clique na máquina refaz a análise. O handler é chamado só pelo lambda do registro, no cliente.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class TunerProposal {
    /** O prazo mais longo aceito do servidor (o dele é de 200 ticks). */
    static final int MAX_TICKS = 400;
    /** Mirando outro bloco por 2 s, a proposta some. */
    static final int AIM_AWAY_TICKS = 40;

    /**
     * A proposta mostrada.
     *
     * @param until o último tick (do mundo do cliente) em que ela vale
     */
    public record Shown(BlockPos machine, List<ConfiguratorProposalPayload.Face> faces, long until) {
    }

    private static @Nullable ClientLevel level;
    private static @Nullable Shown shown;
    /** Ticks seguidos mirando outro bloco. */
    private static int aimAway;

    private TunerProposal() {
    }

    public static void receive(ConfiguratorProposalPayload payload) {
        ClientLevel current = Minecraft.getInstance().level;
        if (payload.faces().isEmpty() || current == null) {
            clear();
            return;
        }
        int ticks = Math.clamp(payload.ticks(), 0, MAX_TICKS);
        level = current;
        shown = new Shown(payload.machine(), payload.faces(), current.getGameTime() + ticks);
        aimAway = 0;
        TunerPaint.build(shown);
    }

    /** A proposta que vale agora, ou {@code null} (nenhuma, outro mundo ou vencida). */
    public static @Nullable Shown current() {
        Shown proposal = shown;
        ClientLevel current = Minecraft.getInstance().level;
        if (proposal == null || current == null || current != level || current.getGameTime() > proposal.until()) {
            return null;
        }
        return proposal;
    }

    /**
     * Uma vez por tick do cliente, só com proposta: conta os ticks mirando um bloco que não é a máquina (mirar o ar
     * não conta nem zera) e a apaga no {@link #AIM_AWAY_TICKS}.
     */
    static void tickAim(@Nullable BlockPos aimed) {
        Shown proposal = current();
        if (proposal == null) {
            if (shown != null) {
                clear();
            }
            return;
        }
        if (aimed == null) {
            return;
        }
        if (aimed.equals(proposal.machine())) {
            aimAway = 0;
        } else if (++aimAway >= AIM_AWAY_TICKS) {
            dismiss();
        }
    }

    /** Apaga a proposta e avisa o servidor, que descarta a dele. */
    private static void dismiss() {
        Shown proposal = shown;
        clear();
        if (proposal != null && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new ConfiguratorCancelPayload(proposal.machine()));
        }
    }

    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) {
        if (shown != null) {
            dismiss();
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (shown != null) {
            clear();
        }
    }

    public static void clear() {
        level = null;
        shown = null;
        aimAway = 0;
        TunerPaint.build(null);
    }
}
