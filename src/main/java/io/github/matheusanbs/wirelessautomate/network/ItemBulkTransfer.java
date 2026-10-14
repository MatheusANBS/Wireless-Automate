package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.storage.BulkItems;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A visita do {@link ItemTransfer} quando a origem é um Baú do mod ({@link BulkItems}): percorre tipos em vez
 * de slots e entrega em quantidades {@code long}. Usa o plano, as regras de filtro e os contadores da visita
 * do {@link ItemTransfer}, com as mesmas regras de filtro, estoque, ordem e sono.
 */
final class ItemBulkTransfer {
    /** O último {@link #deliverToBulk} acabou com a passada (a origem esvaziou). O laço roda numa thread só. */
    private static boolean passEnded;

    /**
     * Visita de uma origem que é um Baú do mod ({@link BulkItems}): percorre os <b>tipos</b>, com o
     * mesmo cursor, a mesma janela por visita e os mesmos tetos de {@link ItemTransfer#move}, e entrega cada tipo
     * em quantidades {@code long} ({@link #deliverBulk}). Filtro e estoque da origem valem igual; o
     * estoque conta pelo inventário visto como slots ({@link ItemStockTally}), uma vez por visita.
     */
    static int moveBulk(Port source, BulkItems bulk, long now) {
        RoundRobinOrder<Port> order = source.order;
        if (order == null) {
            SourceSleep.nothingToMove(source, now);
            return 0;
        }
        long tokens = source.limiter.available(now);
        if (tokens <= 0) {
            return 0;
        }
        List<Port> pass = order.pass();
        if (!NetworkManager.hasAwakeDestination(pass, now)) {
            SourceSleep.untilDestinations(source, pass, now);
            return 0;
        }
        int types = bulk.types();
        if (types <= 0) {
            SourceSleep.nothingToMove(source, now);
            return 0;
        }
        FilterSet filter = source.filter;
        boolean filtered = !filter.isEmpty();
        ItemStockTally tally = null;
        IItemHandler view = null;
        if (filtered && filter.usesItemStock()) {
            view = source.machine.items(source.face);
            tally = ItemStockTally.prepare(source);
        }
        ItemTransfer.PLAN.beginVisit();
        FilterSet.ItemRule rule = ItemTransfer.SOURCE_RULE;
        int index = source.slotCursor < types ? source.slotCursor : 0;
        int limit = Math.min(types, ItemTransfer.MAX_SLOTS_PER_VISIT);
        int scanned = 0;
        int attempts = 0;
        long moved = 0;
        boolean destinationsAsleep = false;
        boolean capped = false;
        while (scanned < limit) {
            // A lista encolhe quando um tipo zera: o cursor volta ao começo.
            types = bulk.types();
            if (types == 0) {
                break;
            }
            int current = index < types ? index : 0;
            scanned++;
            index = current + 1 == types ? 0 : current + 1;
            ItemStack key = bulk.key(current);
            long max = Math.min(tokens, bulk.count(current));
            if (max <= 0) {
                continue;
            }
            boolean stocked = false;
            if (filtered) {
                if (!filter.evaluateItem(key, rule)) {
                    continue;
                }
                if (rule.stock > 0 && tally != null && view != null) {
                    stocked = true;
                    max = StockLimit.extractable(tally.lookup(view, filter, rule.matchComponents, key), rule.stock, max);
                    if (max <= 0) {
                        continue;
                    }
                }
            }
            long amount = deliverBulk(source, bulk, key, max, order, pass, now, ItemTransfer.MAX_ATTEMPTS_PER_VISIT - attempts - 1);
            attempts += ItemTransfer.burstSteps;
            if (amount > 0) {
                if (stocked) {
                    tally.took(amount);
                }
                tokens -= amount;
                moved += amount;
                if (tokens <= 0) {
                    index = current;
                    break;
                }
            }
            if (++attempts >= ItemTransfer.MAX_ATTEMPTS_PER_VISIT) {
                capped = true;
                break;
            }
            if (ItemTransfer.destinationSlept && !NetworkManager.hasAwakeDestination(pass, now)) {
                destinationsAsleep = true;
                break;
            }
        }
        source.slotCursor = index;
        if (moved > 0) {
            source.node.addMoved(source.type, moved);
            source.idleSlots = 0;
            source.limiter.consume(moved);
            source.sourceBackoff.wake();
            boolean more = tokens > 0 && (capped || (scanned >= limit && limit < bulk.types()));
            return more ? NetworkManager.MOVED | NetworkManager.MORE : NetworkManager.MOVED;
        }
        source.idleSlots += scanned;
        if (destinationsAsleep) {
            source.idleSlots = 0;
            SourceSleep.untilDestinations(source, pass, now);
        } else if (source.idleSlots >= types) {
            source.idleSlots = 0;
            SourceSleep.nothingToMove(source, now);
        }
        return 0;
    }

    /**
     * Entrega até {@code max} de um tipo do Baú de origem, na ordem da passada. Para outro Baú do mod
     * é uma transferência só, de qualquer tamanho. Para um inventário comum, uma entrega simulada leva
     * tudo o que o destino aceita: cada slot recebe numa chamada até o que ele aceita
     * ({@link ItemInsertPlan#insert}: uma pilha, ou o limite do slot se ele passa de
     * {@link Item#ABSOLUTE_MAX_STACK_SIZE}, como num barril com upgrade de pilha). Só passa de uma
     * entrega se o pedaço chegou ao teto de um {@code int}; cada uma depois da primeira conta como
     * tentativa ({@link ItemTransfer#burstSteps}, até {@code allowance}). Devolve quanto entregou.
     */
    static long deliverBulk(Port source, BulkItems bulk, ItemStack key, long max,
            RoundRobinOrder<Port> order, List<Port> pass, long now, int allowance) {
        ItemTransfer.destinationSlept = false;
        ItemTransfer.burstSteps = 0;
        long remaining = max;
        long moved = 0;
        FilterSet.ItemRule rule = ItemTransfer.DESTINATION_RULE;
        for (int i = 0, n = pass.size(); i < n && remaining > 0; i++) {
            Port destination = pass.get(i);
            if (destination.destinationBackoff.isSleeping(now) || ItemTransfer.sameMachine(source, destination)) {
                continue;
            }
            FilterSet accept = destination.filter;
            long stock = 0;
            boolean components = false;
            if (!accept.isEmpty()) {
                if (!accept.evaluateItem(key, rule)) {
                    continue;
                }
                stock = rule.stock;
                components = rule.matchComponents;
            }
            source.offered = true;
            BulkItems bulkTarget = destination.machine.bulkItems(destination.face);
            if (bulkTarget != null) {
                passEnded = false;
                long in = deliverToBulk(source, bulk, key, remaining, stock, components, bulkTarget, destination,
                        order, now);
                if (in > 0) {
                    moved += in;
                    remaining -= in;
                }
                if (passEnded) {
                    break;
                }
                continue;
            }
            IItemHandler target = destination.machine.items(destination.face);
            if (target == null) {
                destination.sleepWithoutMachine(now);
                ItemTransfer.destinationSlept = true;
                continue;
            }
            boolean first = true;
            boolean sourceEmpty = false;
            while (remaining > 0) {
                // Uma entrega pode levar mais que uma pilha: o plano limita cada slot ao que ele aceita
                // numa chamada (ItemInsertPlan#insert), então um inventário comum recebe uma pilha por slot, e
                // um slot de pilha grande (upgrade de pilha, gaveta) recebe tudo o que cabe de uma vez.
                int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                if (!first) {
                    if (ItemTransfer.burstSteps >= allowance) {
                        break;
                    }
                    ItemTransfer.burstSteps++;
                }
                int accepts = ItemTransfer.planInsert(source, destination, target, key, key.copyWithCount(chunk), stock, components);
                if (accepts < 0) {
                    break;
                }
                if (accepts == 0) {
                    // A máquina que não aceita este item (sem receita) é pulada sem dormir, como o filtro.
                    if (first && !ItemTransfer.PLAN.refusesItem(target, key)) {
                        destination.destinationBackoff.sleep(now);
                        ItemTransfer.destinationSlept = true;
                    }
                    break;
                }
                long taken = bulk.extract(key, accepts, false);
                if (taken <= 0) {
                    sourceEmpty = true;
                    break;
                }
                ItemStack leftover = ItemTransfer.PLAN.execute(target, key.copyWithCount((int) taken));
                int delivered = (int) taken - leftover.getCount();
                if (!leftover.isEmpty()) {
                    ItemGiveBack.giveBackBulk(source, bulk, leftover, leftover.getCount());
                }
                if (delivered > 0) {
                    ItemTransfer.PLAN.delivered(destination, key, delivered);
                    delivered(source, destination, order);
                    moved += delivered;
                    remaining -= delivered;
                } else if (first) {
                    destination.destinationBackoff.sleep(now);
                    ItemTransfer.destinationSlept = true;
                }
                if (taken < accepts) {
                    sourceEmpty = true;
                    break;
                }
                // O destino aceitou menos que o oferecido: está cheio. Só segue se aceitou tudo (o
                // pedaço era o teto de um int e ainda há mais).
                if (delivered < chunk || stock > 0) {
                    break;
                }
                first = false;
            }
            if (sourceEmpty) {
                break;
            }
        }
        return moved;
    }

    /**
     * Entrega de um tipo do Baú de origem para outro Baú do mod: uma transferência só, de qualquer tamanho.
     * Devolve quanto entrou no destino (0 se ele não aceitou nada); liga {@link #passEnded} se a origem
     * acabou (extraiu menos que o pedido), e a passada para.
     */
    private static long deliverToBulk(Port source, BulkItems bulk, ItemStack key, long remaining, long stock,
            boolean components, BulkItems bulkTarget, Port destination, RoundRobinOrder<Port> order, long now) {
        long room = ItemTransfer.bulkAccepts(bulkTarget, key, remaining, stock, components);
        if (room < 0) {
            return 0;
        }
        if (room == 0) {
            destination.destinationBackoff.sleep(now);
            ItemTransfer.destinationSlept = true;
            return 0;
        }
        long taken = bulk.extract(key, room, false);
        if (taken <= 0) {
            passEnded = true;
            return 0;
        }
        long in = bulkTarget.insert(key, taken, false);
        if (in < taken) {
            ItemGiveBack.giveBackBulk(source, bulk, key, taken - in);
        }
        if (in > 0) {
            delivered(source, destination, order);
        } else {
            destination.destinationBackoff.sleep(now);
            ItemTransfer.destinationSlept = true;
        }
        if (taken < room) {
            passEnded = true;
        }
        return in;
    }

    /** Uma entrega que deixou algo no destino: acorda-o, conta na volta da passada e nas operações. */
    private static void delivered(Port source, Port destination, RoundRobinOrder<Port> order) {
        destination.destinationBackoff.wake();
        order.delivered(destination);
        if (source.network != null) {
            source.network.ops++;
        }
    }

    private ItemBulkTransfer() {
    }
}
