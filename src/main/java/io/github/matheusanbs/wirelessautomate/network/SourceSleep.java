package io.github.matheusanbs.wirelessautomate.network;

import java.util.List;

/**
 * Como uma origem dorme quando não move nada. Se foi porque todos os destinos estão dormindo, ela
 * dorme até o primeiro deles acordar ({@link Backoff#sleepUntil}), e não pelo próprio intervalo:
 * acordar antes só gastaria uma visita para dormir de novo, e acordar depois deixaria o destino
 * esperando. Um destino acordado na hora (a máquina mudou) acorda as origens dele à parte
 * ({@link NodePorts#wake}).
 */
final class SourceSleep {
    /** Origem sem destino acordado: dorme até o primeiro destino acordar. */
    static void untilDestinations(Port source, List<Port> pass, long now) {
        long wake = Long.MAX_VALUE;
        for (int i = 0, n = pass.size(); i < n; i++) {
            wake = pass.get(i).destinationBackoff.earliestWake(wake);
        }
        if (wake <= now || wake == Long.MAX_VALUE) {
            // Algum acordado (não deveria acontecer aqui) ou nenhum destino: o intervalo da origem.
            source.sourceBackoff.sleep(now);
        } else {
            source.sourceBackoff.sleepUntil(now, wake);
        }
    }

    /** Fim de uma visita sem mover: pelos destinos se nenhum está acordado, senão pelo intervalo. */
    static void idle(Port source, List<Port> pass, long now) {
        if (NetworkManager.hasAwakeDestination(pass, now)) {
            source.sourceBackoff.sleep(now);
        } else {
            untilDestinations(source, pass, now);
        }
    }

    private SourceSleep() {
    }
}
