package io.github.matheusanbs.wirelessautomate.network;

import java.util.UUID;

/**
 * Retrato de uma rede para o {@code /wa profile}, montado na hora do comando.
 *
 * @param averageNanos tempo médio por tick gasto movendo recursos da rede (média móvel)
 * @param lastNanos tempo do último tick
 * @param opsLastSecond entregas que moveram algo no último segundo completo
 */
public record NetworkStats(
        UUID id,
        int nodes,
        double averageNanos,
        long lastNanos,
        int opsLastSecond,
        int sourcesAwake,
        int sourcesSleeping,
        int destinationsAwake,
        int destinationsSleeping) {
}
