package io.github.matheusanbs.wirelessautomate.network;

import java.util.Arrays;
import java.util.List;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Move energia de uma origem para os destinos dela num único passe: simula a extração, pergunta
 * (simulando) quanto cada destino aceita, divide com {@link EnergySplit}, extrai de verdade o total
 * dividido e entrega. Extrair antes de entregar garante que nunca se cria energia: se a extração
 * real der menos, os primeiros da ordem recebem até acabar; o que um destino recusar depois de
 * aceitar na simulação volta para a origem, e o que nem ela aceitar se perde.
 *
 * <p>Os vetores de trabalho são reaproveitados entre chamadas (só a thread do servidor usa).
 */
final class EnergyTransfer {
    private static long[] wants = new long[16];
    private static long[] shares = new long[16];
    private static int[] priorities = new int[16];
    private static IEnergyStorage[] targets = new IEnergyStorage[16];

    static void move(Port source, long now) {
        IEnergyStorage handler = source.node.energy(source.face);
        RoundRobinOrder<Port> order = source.order;
        if (handler == null || order == null || !handler.canExtract()) {
            source.sourceBackoff.sleep(now);
            return;
        }
        long tokens = source.limiter.available(now);
        if (tokens <= 0) {
            return;
        }
        List<Port> pass = order.pass();
        if (!NetworkManager.hasAwakeDestination(pass, now)) {
            source.sourceBackoff.sleep(now);
            return;
        }
        int offered = handler.extractEnergy((int) Math.min(tokens, Integer.MAX_VALUE), true);
        if (offered <= 0) {
            source.sourceBackoff.sleep(now);
            return;
        }
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
            IEnergyStorage target = destination.node.energy(destination.face);
            if (target == null) {
                continue;
            }
            int accepts = target.canReceive() ? target.receiveEnergy(offered, true) : 0;
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
            int taken = handler.extractEnergy((int) total, false);
            long left = taken;
            for (int i = 0; i < count && left > 0; i++) {
                if (shares[i] <= 0) {
                    continue;
                }
                int received = targets[i].receiveEnergy((int) Math.min(shares[i], left), false);
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
                handler.receiveEnergy((int) left, false);
            }
        }
        Arrays.fill(targets, 0, count, null);
        if (delivered > 0) {
            source.limiter.consume(delivered);
            source.sourceBackoff.wake();
        } else {
            source.sourceBackoff.sleep(now);
        }
    }

    private static void ensureCapacity(int count) {
        if (wants.length >= count) {
            return;
        }
        int size = Math.max(count, wants.length * 2);
        wants = new long[size];
        shares = new long[size];
        priorities = new int[size];
        targets = new IEnergyStorage[size];
    }

    private EnergyTransfer() {
    }
}
