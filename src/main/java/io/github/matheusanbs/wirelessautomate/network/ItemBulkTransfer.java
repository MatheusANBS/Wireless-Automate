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
 *
 * <p>Antes da varredura dos tipos, cada visita procura pela chave os itens que os destinos seletivos já
 * receberam ({@link #pull}): a fornalha diante de um Baú com milhares de tipos recebe o ferro na hora, sem
 * esperar a varredura chegar nele.
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
        if (SourceSleep.allAsleep(source, order, now)) {
            return 0;
        }
        List<Port> pass = order.pass();
        int types = bulk.slots();
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
        ItemTransfer.beginVisit();
        FilterSet.ItemRule rule = ItemTransfer.SOURCE_RULE;
        int index = source.slotCursor < types ? source.slotCursor : 0;
        int limit = Math.min(types, ItemTransfer.MAX_SLOTS_PER_VISIT);
        int scanned = 0;
        int attempts = 0;
        long moved = 0;
        boolean destinationsAsleep = false;
        boolean capped = false;
        if (tally == null) {
            // Primeiro o que os destinos seletivos já receberam, direto pela chave (ver pull).
            moved = pull(source, bulk, filter, order, pass, tokens, now);
            tokens -= moved;
            attempts = pullAttempts;
            if (tokens <= 0 || attempts >= ItemTransfer.MAX_ATTEMPTS_PER_VISIT) {
                limit = 0;
                capped = attempts >= ItemTransfer.MAX_ATTEMPTS_PER_VISIT;
            }
        }
        while (scanned < limit) {
            // A lista encolhe no fim do tick em que um tipo zera: o cursor volta ao começo.
            types = bulk.slots();
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
                if (rule.keep > 0 && tally != null && view != null) {
                    stocked = true;
                    max = StockLimit.extractable(tally.lookup(view, filter, rule.matchComponents, key), rule.keep, max);
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
                    // Sem saldo: a próxima visita segue do tipo seguinte, como no ItemTransfer.
                    break;
                }
            }
            if (ItemTransfer.countsAsAttempt(amount) && ++attempts >= ItemTransfer.MAX_ATTEMPTS_PER_VISIT
                    || ItemTransfer.visitRefusals >= ItemTransfer.MAX_REFUSALS_PER_VISIT) {
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
            source.addGave(moved);
            source.idleSlots = 0;
            source.limiter.consume(moved);
            source.sourceBackoff.wake();
            boolean more = tokens > 0 && (capped || (scanned >= limit && limit < bulk.slots()));
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

    /** Tentativas gastas pelo último {@link #pull}. */
    private static int pullAttempts;

    /**
     * Itens procurados pela chave por visita no {@link #pull}, somando os destinos: o custo fixo de uma visita
     * que não acha nada é este número de consultas ao índice do Baú.
     */
    static final int MAX_PULL_LOOKUPS = 16;

    /**
     * Antes de varrer os tipos, procura no Baú, pela chave, os itens que cada destino seletivo acordado já
     * recebeu ({@link ItemHints}, só nas máquinas que recusam parte dos itens) e entrega os que houver. Com
     * milhares de tipos, a varredura levaria dezenas de visitas até chegar no item que a fornalha quer; a
     * consulta pela chave é O(1). Pula o item que o destino recusou desde a última mudança dele, e a origem
     * com estoque no filtro (a contagem do estoque vem da varredura). Devolve quanto entregou; as tentativas
     * gastas ficam em {@link #pullAttempts}.
     */
    private static long pull(Port source, BulkItems bulk, FilterSet filter, RoundRobinOrder<Port> order,
            List<Port> pass, long tokens, long now) {
        pullAttempts = 0;
        long moved = 0;
        int lookups = 0;
        FilterSet.ItemRule rule = ItemTransfer.SOURCE_RULE;
        boolean filtered = !filter.isEmpty();
        for (int d = 0, n = pass.size(); d < n; d++) {
            Port destination = pass.get(d);
            ItemHints hints = destination.hints;
            if (hints == null || destination.destinationBackoff.isSleeping(now)) {
                continue;
            }
            for (int i = 0, learned = hints.learnedSize(); i < learned; i++) {
                if (lookups++ >= MAX_PULL_LOOKUPS) {
                    return moved;
                }
                ItemStack key = hints.learned(i);
                if (hints.refused(key, now, destination.destinationBackoff.maxTicks())) {
                    continue;
                }
                long have = bulk.count(key);
                if (have <= 0 || filtered && !filter.evaluateItem(key, rule)) {
                    continue;
                }
                long amount = deliverBulk(source, bulk, key, Math.min(tokens - moved, have), order, pass, now,
                        ItemTransfer.MAX_ATTEMPTS_PER_VISIT - pullAttempts - 1);
                pullAttempts += ItemTransfer.burstSteps;
                if (ItemTransfer.countsAsAttempt(amount)) {
                    pullAttempts++;
                }
                moved += amount;
                if (moved >= tokens || pullAttempts >= ItemTransfer.MAX_ATTEMPTS_PER_VISIT) {
                    return moved;
                }
            }
        }
        return moved;
    }

    /**
     * Entrega até {@code max} de um tipo do Baú de origem, na ordem da passada. Para outro Baú do mod
     * é uma transferência só, de qualquer tamanho. Para um inventário comum, uma entrega simulada leva
     * tudo o que o destino aceita: cada slot recebe até o que ele aceita ({@link ItemInsertPlan#insert}:
     * uma pilha, ou o limite do slot se ele passa de {@link Item#ABSOLUTE_MAX_STACK_SIZE}, como num
     * barril com upgrade de pilha, depois que ele mostra que guarda o item). Só passa de uma
     * entrega se o pedaço chegou ao teto de um {@code int}; cada uma depois da primeira conta como
     * tentativa ({@link ItemTransfer#burstSteps}, até {@code allowance}). Devolve quanto entregou.
     */
    static long deliverBulk(Port source, BulkItems bulk, ItemStack key, long max,
            RoundRobinOrder<Port> order, List<Port> pass, long now, int allowance) {
        ItemTransfer.destinationSlept = false;
        ItemTransfer.burstSteps = 0;
        ItemTransfer.stepSimulations = 0;
        ItemTransfer.stepRefusals = 0;
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
            ItemHints hints = destination.hints;
            if (hints != null && hints.refused(key, now, destination.destinationBackoff.maxTicks())) {
                // Recusou este item desde a última mudança: pula sem simular.
                continue;
            }
            boolean first = true;
            boolean sourceEmpty = false;
            while (remaining > 0) {
                // Uma entrega pode levar mais que uma pilha: o plano limita cada slot ao que ele aceita
                // (ItemInsertPlan#insert), então um inventário comum recebe uma pilha por slot, e um slot
                // de pilha grande (upgrade de pilha, gaveta) recebe tudo o que cabe em duas chamadas.
                int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                if (!first) {
                    if (ItemTransfer.burstSteps >= allowance) {
                        break;
                    }
                    ItemTransfer.burstSteps++;
                }
                ItemTransfer.stepSimulations++;
                int accepts = ItemTransfer.planInsert(source, destination, target, key, key.copyWithCount(chunk), stock, components);
                if (accepts < 0) {
                    break;
                }
                if (accepts == 0) {
                    // Recusa só deste item (sem receita, ou sem lugar para ele): anota e segue; cheia, dorme.
                    if (first) {
                        ItemTransfer.refusedByTarget(destination, target, key, now);
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
                    ItemTransfer.learned(destination, key);
                    delivered(source, destination, order, delivered);
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
            delivered(source, destination, order, in);
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
    private static void delivered(Port source, Port destination, RoundRobinOrder<Port> order, long amount) {
        destination.destinationBackoff.wake();
        // Recebeu: não está cheio.
        destination.full = false;
        destination.addGot(amount);
        order.delivered(destination);
        if (source.network != null) {
            source.network.ops++;
        }
    }

    private ItemBulkTransfer() {
    }
}
