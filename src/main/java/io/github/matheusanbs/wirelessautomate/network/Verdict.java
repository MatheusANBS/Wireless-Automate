package io.github.matheusanbs.wirelessautomate.network;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Por que uma máquina está como está: o motivo principal, a face e a rede a que ele se refere, o número que o detalhe
 * cita, a ação que o resolve e os outros problemas. Sem texto: o cliente monta as frases pelas chaves de
 * {@code gui.wirelessautomate.tablet.machine.verdict.*} (o motor não conhece a língua do jogador). Lógica pura; o
 * codec de rede fica no {@link VerdictCodec}.
 *
 * @param count     o número do detalhe (destinos cheios, faces sem rede)
 * @param positions as máquinas cheias de {@link Action#SEE_DESTINATIONS}, até {@link #MAX_POSITIONS}
 */
public record Verdict(Reason reason, ResourceType type, FaceSide face, @Nullable UUID network, int count, Action action,
        List<Problem> problems, List<Long> positions) {
    public static final int MAX_POSITIONS = 8;
    public static final int MAX_PROBLEMS = 4;

    /** A cor do estado na lista e no cartão. */
    public enum Tone { BAD, WARN, MUTED, OK }

    public enum Reason {
        UNLOADED(Tone.MUTED), NO_UPGRADE(Tone.BAD), OUT_OF_RANGE(Tone.BAD), OVER_LIMIT(Tone.BAD), REPLACED(Tone.BAD),
        MISSING(Tone.BAD), NOT_IN_USE(Tone.MUTED), NO_NETWORK(Tone.BAD), PAUSED(Tone.MUTED), REDSTONE(Tone.WARN),
        MOVING(Tone.OK), DESTINATIONS_FULL(Tone.BAD), SELF_FULL(Tone.WARN), NOTHING_TO_SEND(Tone.MUTED), IDLE(Tone.MUTED);

        private final Tone tone;

        Reason(Tone tone) {
            this.tone = tone;
        }

        public Tone tone() {
            return tone;
        }
    }

    /** O botão do cartão (ou o link do problema). */
    public enum Action { NONE, CHOOSE_NETWORK, SEE_DESTINATIONS, RESUME, OPEN_FILTER }

    /** O que é um problema além do motivo. */
    public enum ProblemKind { NO_NETWORK, REDSTONE, FILTER }

    /** Um problema além do motivo: o que é, onde (tipo e face) e o link que o resolve. */
    public record Problem(ProblemKind kind, ResourceType type, FaceSide face, Action action) {
    }
}
