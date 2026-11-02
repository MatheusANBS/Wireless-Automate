package io.github.matheusanbs.wirelessautomate.client.tuner;

import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorProposalPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Só cliente: a proposta do Afinador que o servidor mandou ({@link ConfiguratorProposalPayload}), para a pintura nas
 * faces da máquina. Guarda o mundo em que chegou e o prazo em ticks do jogo desse mundo; sair do mundo, ou o prazo
 * passar, a apaga sem pacote. O handler é chamado só pelo lambda do registro, no cliente.
 */
public final class TunerProposal {
    /**
     * A proposta mostrada.
     *
     * @param until o último tick (do mundo do cliente) em que ela vale
     */
    public record Shown(BlockPos machine, List<ConfiguratorProposalPayload.Face> faces, long until) {
    }

    private static @Nullable ClientLevel level;
    private static @Nullable Shown shown;

    private TunerProposal() {
    }

    public static void receive(ConfiguratorProposalPayload payload) {
        ClientLevel current = Minecraft.getInstance().level;
        if (payload.faces().isEmpty() || current == null) {
            clear();
            return;
        }
        level = current;
        shown = new Shown(payload.machine(), payload.faces(), current.getGameTime() + payload.ticks());
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

    public static void clear() {
        level = null;
        shown = null;
    }
}
