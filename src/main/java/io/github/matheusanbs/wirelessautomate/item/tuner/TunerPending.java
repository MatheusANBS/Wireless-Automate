package io.github.matheusanbs.wirelessautomate.item.tuner;

import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
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
 * e os modos por tipo. Só na memória do servidor, nunca no item nem no NBT: some ao aplicar, ao clicar no ar, quando a
 * pintura some no cliente ({@code ConfiguratorCancelPayload}), quando o jogador sai ou quando o servidor para; vencida,
 * o clique seguinte refaz a análise.
 *
 * <p>Guarda também o tick da última tentativa de cada jogador com o Configurador: o botão segurado repete o uso a
 * cada 4 ticks, e a análise e a confirmação só valem depois de uma pausa maior que
 * {@link #HOLD_GAP} ({@link #attempt}). A trava do cliente ({@code TunerInput}) poupa os pacotes; esta vale contra
 * qualquer cliente.
 */
public final class TunerPending {
    /** O prazo do segundo clique: 10 s. */
    public static final int WINDOW = 200;
    /** O segundo clique só vale tanto depois do primeiro (segurar o botão repete o uso a cada 4 ticks). */
    public static final int CONFIRM_DELAY = 6;
    /**
     * Duas tentativas a até tantos ticks uma da outra são o botão segurado (o jogo repete a cada 4), não um clique
     * novo.
     */
    public static final int HOLD_GAP = 5;

    /**
     * Uma proposta.
     *
     * @param modes os modos por tipo, pelas faces absolutas ({@link TunerPlan#decide}); só leitura
     * @param created o tick (do jogo) da análise
     * @param until   o último tick (do jogo) em que o segundo clique aplica
     */
    public record Proposal(ResourceKey<Level> dimension, BlockPos machine, Block block, long created, long until,
            Map<ResourceType, PortMode[]> modes) {
        /** É a mesma máquina, no prazo. */
        public boolean matches(Level level, BlockPos pos, Block now) {
            return dimension == level.dimension() && machine.equals(pos) && block == now
                    && level.getGameTime() <= until;
        }

        /** Já passou o intervalo mínimo do segundo clique ({@link #CONFIRM_DELAY}). */
        public boolean confirmable(Level level) {
            return level.getGameTime() >= created + CONFIRM_DELAY;
        }
    }

    private static final Map<UUID, Proposal> PENDING = new HashMap<>();
    /** O tick (do jogo) da última tentativa de cada jogador; sai no logout e na parada. */
    private static final Object2LongOpenHashMap<UUID> LAST_ATTEMPT = new Object2LongOpenHashMap<>();
    /** O valor de quem ainda não tentou. */
    private static final long NEVER = Long.MIN_VALUE;

    static {
        LAST_ATTEMPT.defaultReturnValue(NEVER);
    }

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

    /**
     * Registra uma tentativa do jogador no tick {@code now} e diz se ela é um clique novo: a anterior foi há mais de
     * {@link #HOLD_GAP} ticks (ou não houve). O relógio voltando (outro mundo, {@code /time}) também conta como novo.
     */
    public static boolean attempt(UUID player, long now) {
        long previous = LAST_ATTEMPT.put(player, now);
        return previous == NEVER || now - previous > HOLD_GAP || now < previous;
    }

    /** O jogador saiu: a proposta e a última tentativa dele somem. */
    public static void leave(UUID player) {
        PENDING.remove(player);
        LAST_ATTEMPT.removeLong(player);
    }

    /** Esquece todas (o servidor parou). */
    public static void reset() {
        PENDING.clear();
        LAST_ATTEMPT.clear();
    }
}
