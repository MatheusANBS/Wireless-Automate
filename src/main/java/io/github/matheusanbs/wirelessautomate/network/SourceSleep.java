package io.github.matheusanbs.wirelessautomate.network;

import java.util.List;

/**
 * Como uma origem dorme quando não move nada, e o motivo ({@link Port#waitsDestination}). Se foi
 * porque todos os destinos estão dormindo, ela dorme até o primeiro deles acordar
 * ({@link Backoff#sleepUntil}), e não pelo próprio intervalo: acordar antes só gastaria uma visita
 * para dormir de novo, e acordar depois deixaria o destino esperando; e espera destino. Senão dorme
 * pelo próprio intervalo ({@link #nothingToMove}) e só espera destino se ofereceu algo a um destino
 * acordado desde o último sono ou entrega ({@link Port#offered}); se não, está vazia. Um destino
 * acordado na hora (a máquina mudou) acorda as origens que esperam por ele ({@link NodePorts#wake}).
 */
final class SourceSleep {
    /** Origem sem destino acordado: dorme até o primeiro destino acordar. */
    static void untilDestinations(Port source, List<Port> pass, long now) {
        source.waitsDestination = true;
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

    /**
     * A origem não tem o que mover com algum destino acordado (ou nem tem máquina): dorme pelo
     * próprio intervalo. Espera destino só se ofereceu algo que um destino aceitaria pelo filtro
     * (recusa por espaço ou estoque, que uma mudança no destino pode desfazer); senão está vazia, e
     * só a própria máquina a acorda.
     */
    static void nothingToMove(Port source, long now) {
        source.waitsDestination = source.offered;
        source.sourceBackoff.sleep(now);
    }

    /** Fim de uma visita sem mover: pelos destinos se nenhum está acordado, senão pelo intervalo. */
    static void idle(Port source, List<Port> pass, long now) {
        if (NetworkManager.hasAwakeDestination(pass, now)) {
            nothingToMove(source, now);
        } else {
            untilDestinations(source, pass, now);
        }
    }

    private SourceSleep() {
    }
}
