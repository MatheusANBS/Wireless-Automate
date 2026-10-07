package io.github.matheusanbs.wirelessautomate.network;

import java.util.UUID;

/**
 * Retrato de uma rede para o {@code /wa profile}, montado na hora do comando.
 *
 * @param nodes nós carregados com algum tipo (aba) nesta rede; um roteador com tipos em duas redes
 *     conta nas duas, então a soma das redes pode passar do total de nós
 * @param averageNanos tempo médio por tick gasto movendo recursos da rede (média móvel)
 * @param lastNanos tempo do último tick
 * @param opsLastSecond entregas que moveram algo no último segundo completo
 * @param destinationsFull destinos dormindo depois de recusas seguidas (cheios, ver {@link NodeProbe}),
 *     contados entre os que dormem
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
        int destinationsSleeping,
        int destinationsFull) {
}
