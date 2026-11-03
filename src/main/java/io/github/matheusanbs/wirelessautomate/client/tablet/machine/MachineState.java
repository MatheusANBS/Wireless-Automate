package io.github.matheusanbs.wirelessautomate.client.tablet.machine;

import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Tone;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * O que a lista da aba Máquina diz de cada máquina (lógica pura, com JUnit): a palavra do estado e a cor dele, ou, quando
 * a máquina move, o tipo e o número da vazão; e o nome da linha. O texto sai das chaves
 * {@code gui.wirelessautomate.tablet.machine.state.*}, montado pelo {@link MachineList}.
 */
public final class MachineState {
    private MachineState() {
    }

    /**
     * @param key      o fim da chave {@code machine.state.*}; {@code null} quando a linha mostra a vazão
     * @param rateType o tipo da vazão mostrada; {@code null} sem vazão
     * @param rate     a vazão do tipo (o que recebe e o que dá somados)
     */
    public record State(Tone tone, @Nullable String key, @Nullable ResourceType rateType, long rate) {
    }

    /**
     * O estado a mostrar: movendo, o tipo de maior vazão ({@code flow} é o que cada tipo recebeu mais o que deu, na
     * ordem de {@link ResourceType#ordinal()}; vazio = sem vazão, e então "movendo"); nos outros, a palavra do estado.
     */
    public static State of(MachineStatus status, long[] flow) {
        if (status == MachineStatus.MOVING) {
            ResourceType best = null;
            long most = 0;
            for (ResourceType type : ResourceType.values()) {
                long value = type.ordinal() < flow.length ? flow[type.ordinal()] : 0;
                if (value > most) {
                    most = value;
                    best = type;
                }
            }
            if (best != null) {
                return new State(Tone.MUTED, null, best, most);
            }
        }
        return new State(tone(status), status.name().toLowerCase(Locale.ROOT), null, 0);
    }

    /** A cor do estado: problema em {@code BAD}, cheia em {@code WARN}, o resto apagado. */
    public static Tone tone(MachineStatus status) {
        return switch (status) {
            case FULL -> Tone.WARN;
            case NO_UPGRADE, OUT_OF_RANGE, OVER_LIMIT, REPLACED, MISSING, NO_NETWORK -> Tone.BAD;
            case UNLOADED, PAUSED, MOVING, SLEEPING -> Tone.MUTED;
        };
    }

    /** O nome da linha: o rótulo; sem ele, o nome do bloco na presa e "bloco #N" na vinculada. */
    public static String name(String label, boolean attached, int number, String blockName) {
        if (!label.isEmpty()) {
            return label;
        }
        return attached ? blockName : blockName + " #" + number;
    }
}
