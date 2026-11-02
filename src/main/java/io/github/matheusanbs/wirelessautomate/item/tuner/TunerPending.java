package io.github.matheusanbs.wirelessautomate.item.tuner;

import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * A proposta do Afinador de cada jogador, à espera do segundo clique: a máquina (dimensão, posição e bloco), o prazo
 * e os modos por tipo. Só na memória do servidor, nunca no item nem no NBT: some ao aplicar, ao clicar no ar, quando o
 * jogador sai ou quando o servidor para; vencida, o clique seguinte refaz a análise.
 */
public final class TunerPending {
    /** O prazo do segundo clique: 10 s. */
    public static final int WINDOW = 200;

    /**
     * Uma proposta.
     *
     * @param modes os modos por tipo, pelas faces absolutas ({@link TunerPlan#decide}); só leitura
     * @param until o último tick (do jogo) em que o segundo clique aplica
     */
    public record Proposal(ResourceKey<Level> dimension, BlockPos machine, Block block, long until,
            Map<ResourceType, PortMode[]> modes) {
        /** É a mesma máquina, no prazo. */
        public boolean matches(Level level, BlockPos pos, Block now) {
            return dimension == level.dimension() && machine.equals(pos) && block == now
                    && level.getGameTime() <= until;
        }
    }

    private static final Map<UUID, Proposal> PENDING = new HashMap<>();

    private TunerPending() {
    }

    public static void put(UUID player, Proposal proposal) {
        PENDING.put(player, proposal);
    }

    /** A proposta do jogador, ou {@code null}. */
    public static @Nullable Proposal get(UUID player) {
        return PENDING.get(player);
    }

    /** Esquece a proposta do jogador; devolve se havia uma. */
    public static boolean forget(UUID player) {
        return PENDING.remove(player) != null;
    }

    /** Esquece todas (o servidor parou). */
    public static void reset() {
        PENDING.clear();
    }
}
