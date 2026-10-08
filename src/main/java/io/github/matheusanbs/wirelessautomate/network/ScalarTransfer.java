package io.github.matheusanbs.wirelessautomate.network;

import java.util.Arrays;
import java.util.List;

/**
 * Move um recurso de "um valor só" (energia, Source) de uma origem para os destinos dela num único
 * passe: simula a extração, pergunta (simulando) quanto cada destino aceita, divide com
 * {@link EnergySplit}, extrai de verdade o total dividido e entrega. Extrair antes de entregar
 * garante que nunca se cria nada: se a extração real der menos, os primeiros da ordem recebem até
 * acabar; o que um destino recusar depois de aceitar na simulação volta para a origem, e o que nem
 * ela aceitar se perde.
 *
 * <p>O recurso chega pelo {@link ScalarAccess}: {@link EnergyAccess} (a Bateria do mod em
 * {@code long}, as outras máquinas em {@code int}) e o da Source ({@code compat/arsnouveau}).
 *
 * <p>Os vetores de trabalho são reaproveitados entre chamadas (só a thread do servidor usa).
 */
final class ScalarTransfer {
    private static long[] wants = new long[16];
    private static long[] shares = new long[16];
    private static int[] priorities = new int[16];
    private static Object[] targets = new Object[16];

    /** Uma visita. Devolve {@code true} se moveu algo. */
    static boolean move(Port source, long now, ScalarAccess access) {
        Object handler = access.handler(source.node, source.face);
        RoundRobinOrder<Port> order = source.order;
        if (handler == null || !access.canExtract(handler) || order == null) {
            SourceSleep.nothingToMove(source, now);
            return false;
        }
        long tokens = source.limiter.available(now);
        if (tokens <= 0) {
            return false;
        }
        List<Port> pass = order.pass();
        if (!NetworkManager.hasAwakeDestination(pass, now)) {
            SourceSleep.untilDestinations(source, pass, now);
            return false;
        }
        long offered = access.extract(handler, tokens, true);
        if (offered <= 0) {
            SourceSleep.nothingToMove(source, now);
            return false;
        }
        // Tem o que dar e há destino acordado: se dormir, foi esperando destino (motivo do sono).
        source.offered = true;
        int count = pass.size();
        ensureCapacity(count);
        for (int i = 0; i < count; i++) {
            Port destination = pass.get(i);
            priorities[i] = destination.priority;
            wants[i] = 0;
            targets[i] = null;
            if (destination.destinationBackoff.isSleeping(now)) {
                continue;
            }
            Object target = access.handler(destination.node, destination.face);
            if (target == null) {
                // Destino sem máquina (ou com o chunk dela descarregado): dorme até a capability
                // voltar (o listener dela o acorda) ou o teto do sono, sem contar como cheio.
                destination.sleepWithoutMachine(now);
                continue;
            }
            long accepts = access.canReceive(target) ? access.insert(target, offered, true) : 0;
            if (accepts <= 0) {
                destination.destinationBackoff.sleep(now);
                continue;
            }
            wants[i] = accepts;
            targets[i] = target;
        }
        long total = EnergySplit.split(offered, wants, priorities, count, shares);
        long delivered = 0;
        if (total > 0) {
            long taken = access.extract(handler, total, false);
            long left = taken;
            for (int i = 0; i < count && left > 0; i++) {
                if (shares[i] <= 0) {
                    continue;
                }
                long received = access.insert(targets[i], Math.min(shares[i], left), false);
                if (received > 0) {
                    Port destination = pass.get(i);
                    destination.destinationBackoff.wake();
                    order.delivered(destination);
                    if (source.network != null) {
                        source.network.ops++;
                    }
                    left -= received;
                    delivered += received;
                }
            }
            if (left > 0) {
                access.insert(handler, left, false);
            }
        }
        Arrays.fill(targets, 0, count, null);
        if (delivered > 0) {
            source.node.addMoved(source.type, delivered);
            source.limiter.consume(delivered);
            source.sourceBackoff.wake();
            return true;
        }
        // Destinos que recusaram dormiram: se foram todos, a origem dorme até o primeiro acordar.
        SourceSleep.idle(source, pass, now);
        return false;
    }

    private static void ensureCapacity(int count) {
        if (wants.length >= count) {
            return;
        }
        int size = Math.max(count, wants.length * 2);
        wants = new long[size];
        shares = new long[size];
        priorities = new int[size];
        targets = new Object[size];
    }

    private ScalarTransfer() {
    }
}
