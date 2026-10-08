package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import java.util.Arrays;
import java.util.List;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Move energia de uma origem para os destinos dela num único passe: simula a extração, pergunta
 * (simulando) quanto cada destino aceita, divide com {@link EnergySplit}, extrai de verdade o total
 * dividido e entrega. Extrair antes de entregar garante que nunca se cria energia: se a extração
 * real der menos, os primeiros da ordem recebem até acabar; o que um destino recusar depois de
 * aceitar na simulação volta para a origem, e o que nem ela aceitar se perde.
 *
 * <p>Bateria do mod ({@link BulkEnergy}): o lado dela (origem ou destino) é em {@code long}, sem o
 * teto de {@link Integer#MAX_VALUE} FE por chamada do {@link IEnergyStorage}. Entre duas Baterias,
 * bilhões de FE passam por tick; com máquinas e cabos de outros mods, o lado deles continua em
 * {@code int}.
 *
 * <p>Os vetores de trabalho são reaproveitados entre chamadas (só a thread do servidor usa).
 */
final class EnergyTransfer {
    private static long[] wants = new long[16];
    private static long[] shares = new long[16];
    private static int[] priorities = new int[16];
    private static IEnergyStorage[] targets = new IEnergyStorage[16];
    private static BulkEnergy[] bulkTargets = new BulkEnergy[16];

    /** Uma visita. Devolve {@code true} se moveu algo. */
    static boolean move(Port source, long now) {
        BulkEnergy bulk = source.node.bulkEnergy(source.face);
        IEnergyStorage handler = bulk == null ? source.node.energy(source.face) : null;
        RoundRobinOrder<Port> order = source.order;
        if (bulk == null && (handler == null || !handler.canExtract()) || order == null) {
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
        long offered = bulk != null ? bulk.extract(tokens, true)
                : handler.extractEnergy((int) Math.min(tokens, Integer.MAX_VALUE), true);
        if (offered <= 0) {
            SourceSleep.nothingToMove(source, now);
            return false;
        }
        // Tem energia e há destino acordado: se dormir, foi esperando destino (motivo do sono).
        source.offered = true;
        int count = pass.size();
        ensureCapacity(count);
        for (int i = 0; i < count; i++) {
            Port destination = pass.get(i);
            priorities[i] = destination.priority;
            wants[i] = 0;
            targets[i] = null;
            bulkTargets[i] = null;
            if (destination.destinationBackoff.isSleeping(now)) {
                continue;
            }
            BulkEnergy bulkTarget = destination.node.bulkEnergy(destination.face);
            IEnergyStorage target = bulkTarget == null ? destination.node.energy(destination.face) : null;
            if (bulkTarget == null && target == null) {
                // Destino sem máquina (ou com o chunk dela descarregado): dorme até a capability
                // voltar (o listener dela o acorda) ou o teto do sono, sem contar como cheio.
                destination.sleepWithoutMachine(now);
                continue;
            }
            long accepts = bulkTarget != null ? bulkTarget.insert(offered, true)
                    : target.canReceive() ? target.receiveEnergy((int) Math.min(offered, Integer.MAX_VALUE), true) : 0;
            if (accepts <= 0) {
                destination.destinationBackoff.sleep(now);
                continue;
            }
            wants[i] = accepts;
            targets[i] = target;
            bulkTargets[i] = bulkTarget;
        }
        long total = EnergySplit.split(offered, wants, priorities, count, shares);
        long delivered = 0;
        if (total > 0) {
            long taken = extract(bulk, handler, total);
            long left = taken;
            for (int i = 0; i < count && left > 0; i++) {
                if (shares[i] <= 0) {
                    continue;
                }
                long received = receive(bulkTargets[i], targets[i], Math.min(shares[i], left));
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
                receive(bulk, handler, left);
            }
        }
        Arrays.fill(targets, 0, count, null);
        Arrays.fill(bulkTargets, 0, count, null);
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

    /** Extração real pelo lado que existir; o {@code int} do outro mod corta em {@link Integer#MAX_VALUE}. */
    private static long extract(@Nullable BulkEnergy bulk, @Nullable IEnergyStorage handler, long amount) {
        return bulk != null ? bulk.extract(amount, false)
                : handler.extractEnergy((int) Math.min(amount, Integer.MAX_VALUE), false);
    }

    /** Entrega real pelo lado que existir; o {@code int} do outro mod corta em {@link Integer#MAX_VALUE}. */
    private static long receive(@Nullable BulkEnergy bulk, @Nullable IEnergyStorage handler, long amount) {
        return bulk != null ? bulk.insert(amount, false)
                : handler.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), false);
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
        bulkTargets = new BulkEnergy[size];
    }

    private EnergyTransfer() {
    }
}
