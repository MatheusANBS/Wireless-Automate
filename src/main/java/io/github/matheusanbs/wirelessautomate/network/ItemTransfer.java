package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.storage.BulkItems;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Move itens de uma origem para os destinos dela, numa visita do {@link NetworkManager}.
 *
 * <p>Para não duplicar nem perder, cada entrega segue: simula a extração, simula a inserção no
 * destino, extrai de verdade só o que o destino aceitaria e insere de verdade exatamente o que
 * saiu. Assim nunca se insere algo que não foi tirado. Se o destino mentiu na simulação e devolve
 * sobra, ela volta para a origem (mesmo slot, depois qualquer slot); se nem a origem aceitar
 * (slot só de saída, por exemplo), a sobra cai no mundo na posição do roteador e o caso vai para o
 * log. Se a extração real der menos que o simulado, só o que saiu é entregue e a visita para.
 *
 * <p>Filtros ({@link Port#filter}: o embutido e os dos cartões, montados na montagem das rotas; ver
 * {@link FilterSet}): o da origem decide que slots podem sair, e o do destino o que pode entrar. Um slot recusado pela origem só conta como varrido (a
 * volta inteira sem mover ainda faz a origem dormir). Um destino que recusa pelo filtro, ou que já
 * tem o estoque, é só pulado, <b>sem dormir</b>: recusar o item A não diz nada sobre o B, e o
 * backoff de "cheio" o deixaria de fora também para o B. Só a recusa por falta de espaço (a
 * inserção simulada não aceita nada de um item que o filtro deixa entrar) faz o destino dormir.
 * Se nenhum destino aceitar nenhum item, a origem é que dorme, pela volta sem mover; se todos os
 * destinos estão dormindo, ela dorme até o primeiro deles acordar ({@link SourceSleep}).
 *
 * <p>Estoque: na origem, mantém N do item (mesmo item; com {@code matchComponents}, mesmos
 * componentes) contando o inventário inteiro uma vez por visita, só quando a visita encontra um
 * slot com estoque ({@link StockTally}): O(slots) por visita, e a conta é descontada conforme os
 * itens saem. No destino, aceita até N contando os slots do destino na mesma varredura que simula
 * a inserção ({@link InsertPlan}), só para entradas com estoque, parando ao atingir o estoque; a
 * contagem fica lembrada durante a visita, então o destino cheio não é varrido de novo a cada slot.
 * Sem estoque no filtro, nada é contado.
 *
 * <p>Custo de uma entrega: uma varredura do destino ({@link InsertPlan}), em vez das quatro de
 * chamar o {@link ItemHandlerHelper#insertItemStacked} na simulação e de novo na inserção.
 *
 * <p>Baú do mod ({@link BulkItems}): como origem, a visita percorre tipos em vez de slots
 * ({@link #moveBulk}); como destino, guarda pela chave, sem varrer slots. Entre dois Baús, um tipo
 * inteiro passa numa chamada só, sem o teto de uma pilha por extração do {@link IItemHandler}. As
 * regras de filtro, estoque, ordem e sono são as mesmas.
 */
final class ItemTransfer {
    /** Slots examinados por visita: inventário grande continua do cursor no tick seguinte. */
    static final int MAX_SLOTS_PER_VISIT = 128;
    /**
     * Tentativas de entrega por visita, para uma origem não comer o orçamento sozinha. Um slot que
     * entregou tudo o que ofereceu e ainda tem itens (gaveta, barril com upgrade de pilha) é tentado
     * de novo, e cada repetição conta como uma tentativa.
     */
    static final int MAX_ATTEMPTS_PER_VISIT = 32;
    /** Plano da última simulação de inserção; o laço roda numa thread só (a do servidor). */
    private static final InsertPlan PLAN = new InsertPlan();
    /** Respostas do filtro da origem e do destino ({@link FilterSet#evaluateItem}), reaproveitadas. */
    private static final FilterSet.ItemRule SOURCE_RULE = new FilterSet.ItemRule();
    private static final FilterSet.ItemRule DESTINATION_RULE = new FilterSet.ItemRule();
    /** Quanto a extração simulada do último {@link #moveSlot} ofereceu. */
    private static int lastOffered;
    /** O último {@link #moveSlot} pôs algum destino para dormir. */
    private static boolean destinationSlept;
    /** Entregas da rajada do último {@link #moveSlot}; cada uma conta como uma tentativa. */
    private static int burstSteps;

    /**
     * Uma visita. Devolve {@link NetworkManager#MOVED} se moveu algo, mais {@link NetworkManager#MORE}
     * se parou num teto da visita (tentativas, ou a janela de slots num inventário maior que ela) e o
     * balde ainda tem saldo: o gerenciador pode visitá-la de novo no mesmo tick se sobrar orçamento.
     */
    static int move(Port source, long now) {
        BulkItems bulk = source.node.bulkItems(source.face);
        if (bulk != null) {
            return moveBulk(source, bulk, now);
        }
        IItemHandler handler = source.node.items(source.face);
        RoundRobinOrder<Port> order = source.order;
        if (handler == null || order == null) {
            // Máquina sumiu ou chunk descarregado: dorme como vazia. O listener da capability da
            // face (ou a troca do bloco da máquina) acorda a porta, sem remontar as rotas.
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
        int slots = handler.getSlots();
        if (slots <= 0) {
            SourceSleep.nothingToMove(source, now);
            return 0;
        }
        FilterSet filter = source.filter;
        boolean filtered = !filter.isEmpty();
        StockTally tally = null;
        if (filtered && filter.usesItemStock()) {
            tally = source.tally;
            if (tally == null) {
                tally = new StockTally();
                source.tally = tally;
            }
            tally.reset();
        }
        PLAN.beginVisit();
        FilterSet.ItemRule rule = SOURCE_RULE;
        int slot = source.slotCursor < slots ? source.slotCursor : 0;
        int limit = Math.min(slots, MAX_SLOTS_PER_VISIT);
        int scanned = 0;
        int attempts = 0;
        long moved = 0;
        boolean destinationsAsleep = false;
        boolean capped = false;
        while (scanned < limit) {
            int current = slot;
            scanned++;
            slot = slot + 1 == slots ? 0 : slot + 1;
            ItemStack inSlot = handler.getStackInSlot(current);
            if (inSlot.isEmpty()) {
                continue;
            }
            int max = (int) Math.min(tokens, Integer.MAX_VALUE);
            boolean stocked = false;
            if (filtered) {
                // Passa? e estoque numa consulta só.
                if (!filter.evaluateItem(inSlot, rule)) {
                    continue;
                }
                if (rule.stock > 0) {
                    stocked = true;
                    max = (int) StockLimit.extractable(tally.lookup(handler, filter, rule.matchComponents, inSlot),
                            rule.stock, max);
                    if (max <= 0) {
                        continue;
                    }
                }
            }
            int amount = moveSlot(source, handler, current, max, order, pass, now,
                    MAX_ATTEMPTS_PER_VISIT - attempts - 1);
            attempts += burstSteps;
            boolean again = false;
            if (amount > 0) {
                if (stocked) {
                    tally.took(amount);
                }
                tokens -= amount;
                moved += amount;
                if (tokens <= 0) {
                    // Sem saldo: o slot pode ter mais, continua dele no próximo tick.
                    slot = current;
                    break;
                }
                // Pilha maior que uma extração (gaveta, upgrade de pilha): entregou tudo o que
                // ofereceu e ainda tem itens, então tenta o mesmo slot de novo.
                if (amount >= lastOffered && !handler.getStackInSlot(current).isEmpty()) {
                    again = true;
                    slot = current;
                }
            }
            if (++attempts >= MAX_ATTEMPTS_PER_VISIT) {
                capped = true;
                break;
            }
            // Destino só dorme por uma recusa nossa: sem recusa nesta tentativa, ainda há acordado.
            if (destinationSlept && !NetworkManager.hasAwakeDestination(pass, now)) {
                destinationsAsleep = true;
                break;
            }
            if (again) {
                // A repetição não conta como slot varrido (o teto dela são as tentativas).
                scanned--;
            }
        }
        source.slotCursor = slot;
        if (moved > 0) {
            source.node.addMoved(source.type, moved);
            source.idleSlots = 0;
            source.limiter.consume(moved);
            source.sourceBackoff.wake();
            boolean more = tokens > 0 && (capped || (scanned >= limit && limit < slots));
            return more ? NetworkManager.MOVED | NetworkManager.MORE : NetworkManager.MOVED;
        }
        source.idleSlots += scanned;
        if (destinationsAsleep) {
            // Todos os destinos recusaram: dorme até o primeiro acordar.
            source.idleSlots = 0;
            SourceSleep.untilDestinations(source, pass, now);
        } else if (source.idleSlots >= slots) {
            // Uma volta inteira sem mover (mesmo dividida em visitas): dorme até alguém mudar.
            source.idleSlots = 0;
            SourceSleep.nothingToMove(source, now);
        }
        return 0;
    }

    /**
     * Entrega o que der do slot, na ordem da passada. Devolve quanto foi entregue. Com
     * {@code allowance} &gt; 0, pode seguir numa rajada ({@link #burst}) e anota as entregas dela em
     * {@link #burstSteps}.
     */
    private static int moveSlot(Port source, IItemHandler handler, int slot, int max,
            RoundRobinOrder<Port> order, List<Port> pass, long now, int allowance) {
        destinationSlept = false;
        burstSteps = 0;
        ItemStack offered = handler.extractItem(slot, max, true);
        lastOffered = offered.getCount();
        if (offered.isEmpty()) {
            return 0;
        }
        int remaining = offered.getCount();
        int moved = 0;
        FilterSet.ItemRule rule = DESTINATION_RULE;
        for (int i = 0, n = pass.size(); i < n && remaining > 0; i++) {
            Port destination = pass.get(i);
            if (destination.destinationBackoff.isSleeping(now)) {
                continue;
            }
            FilterSet accept = destination.filter;
            long stock = 0;
            boolean components = false;
            if (!accept.isEmpty()) {
                // Recusa do filtro ou estoque já atingido: pula sem dormir (ver o javadoc da classe).
                // O filtro vem antes da capability: recusar não precisa da máquina.
                if (!accept.evaluateItem(offered, rule)) {
                    continue;
                }
                stock = rule.stock;
                components = rule.matchComponents;
            }
            // Passou no filtro do destino: a origem tem o que oferecer (motivo do sono).
            source.offered = true;
            // Baú do mod: guarda por tipo, sem varrer slots. Senão, o inventário da máquina.
            BulkItems bulkTarget = destination.node.bulkItems(destination.face);
            IItemHandler target = bulkTarget == null ? destination.node.items(destination.face) : null;
            if (bulkTarget == null && target == null) {
                // Destino sem máquina (ou com o chunk dela descarregado): dorme até a capability
                // voltar (o listener dela o acorda) ou o teto do sono, sem contar como cheio.
                destination.sleepWithoutMachine(now);
                destinationSlept = true;
                continue;
            }
            ItemStack probe = remaining == offered.getCount() ? offered : offered.copyWithCount(remaining);
            int accepts = bulkTarget != null
                    ? (int) bulkAccepts(bulkTarget, probe, remaining, stock, components)
                    : planInsert(source, destination, target, offered, probe, stock, components);
            if (accepts < 0) {
                // Estoque do destino atingido: pula sem dormir.
                continue;
            }
            if (accepts == 0) {
                destination.destinationBackoff.sleep(now);
                destinationSlept = true;
                continue;
            }
            ItemStack taken = handler.extractItem(slot, accepts, false);
            if (taken.isEmpty()) {
                break;
            }
            int takenCount = taken.getCount();
            ItemStack leftover = execute(bulkTarget, target, taken);
            int delivered = takenCount - leftover.getCount();
            if (!leftover.isEmpty()) {
                giveBack(source, handler, slot, leftover);
            }
            if (delivered > 0) {
                PLAN.delivered(destination, offered, delivered);
                destination.destinationBackoff.wake();
                order.delivered(destination);
                if (source.network != null) {
                    source.network.ops++;
                }
                moved += delivered;
                remaining -= delivered;
                if (remaining == 0 && stock == 0 && allowance > 0 && takenCount == offered.getCount()) {
                    // Entregou a extração inteira e o slot pode ter mais (gaveta, upgrade de pilha):
                    // as próximas vão direto para este destino, sem simular.
                    moved += burst(source, handler, slot, bulkTarget, target, destination, offered, max - moved, allowance);
                }
            } else {
                destination.destinationBackoff.sleep(now);
                destinationSlept = true;
            }
            if (takenCount < accepts) {
                break;
            }
        }
        return moved;
    }

    /**
     * Rajada: o destino acabou de receber a extração inteira do slot, então as próximas extrações
     * do mesmo item vão direto para os slots do plano dele, sem as duas simulações (extração e
     * inserção), que numa pilha enorme eram metade das chamadas. A ordem não muda: na mesma visita a
     * passada é a mesma, e os destinos antes dele seguem recusando o mesmo item (filtro, estoque já
     * atingido, dormindo). A conservação também não: só o que saiu é inserido, e a sobra volta para
     * a origem como numa entrega comum. Para no primeiro sinal de mudança (outro item no slot,
     * extração menor, destino que não aceitou tudo); a tentativa seguinte, completa, decide o resto.
     * Devolve quanto entregou; cada extração conta em {@link #burstSteps}.
     */
    private static int burst(Port source, IItemHandler handler, int slot, @Nullable BulkItems bulkTarget,
            @Nullable IItemHandler target, Port destination, ItemStack offered, int cap, int allowance) {
        int size = offered.getCount();
        int moved = 0;
        while (burstSteps < allowance && moved < cap) {
            if (!ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), offered)) {
                break;
            }
            int want = Math.min(size, cap - moved);
            ItemStack taken = handler.extractItem(slot, want, false);
            if (taken.isEmpty()) {
                break;
            }
            burstSteps++;
            if (!ItemStack.isSameItemSameComponents(taken, offered)) {
                // O filtro do destino aprovou outro item: devolve.
                giveBack(source, handler, slot, taken);
                break;
            }
            int takenCount = taken.getCount();
            ItemStack leftover = execute(bulkTarget, target, taken);
            int delivered = takenCount - leftover.getCount();
            if (!leftover.isEmpty()) {
                giveBack(source, handler, slot, leftover);
            }
            if (delivered > 0 && source.network != null) {
                source.network.ops++;
            }
            moved += delivered;
            if (delivered < want) {
                break;
            }
        }
        if (moved > 0) {
            PLAN.delivered(destination, offered, moved);
        }
        return moved;
    }

    /**
     * Quanto o inventário {@code target} aceita de {@code probe}, já com o estoque do destino, e deixa
     * o plano da inserção em {@link #PLAN}. Devolve −1 se o estoque já foi atingido (pular sem dormir).
     *
     * <p>Contagem do estoque: da memória da visita se o destino já foi contado, senão na varredura
     * que simula a inserção (que para ao atingir o estoque). Destino na própria máquina da origem não
     * é lembrado: a extração muda a contagem dele.
     */
    private static int planInsert(Port source, Port destination, IItemHandler target, ItemStack offered,
            ItemStack probe, long stock, boolean components) {
        if (stock <= 0) {
            return PLAN.simulate(target, probe, InsertPlan.COUNT_NONE, 0);
        }
        boolean remember = !sameMachine(source, destination);
        int memo = remember ? PLAN.recall(destination, offered, components) : -1;
        long counted;
        int accepts;
        if (memo >= 0) {
            counted = PLAN.recalled(memo);
            if (counted >= stock) {
                return -1;
            }
            accepts = PLAN.simulate(target, probe, InsertPlan.COUNT_NONE, 0);
        } else {
            accepts = PLAN.simulate(target, probe,
                    components ? InsertPlan.COUNT_COMPONENTS : InsertPlan.COUNT_ITEM, stock);
            counted = PLAN.counted();
            if (remember) {
                PLAN.remember(destination, offered, components, counted, stock);
            }
        }
        int want = (int) StockLimit.acceptable(counted, stock, probe.getCount());
        return want <= 0 ? -1 : Math.min(accepts, want);
    }

    /**
     * Quanto um Baú do mod aceita de {@code amount} da chave, já com o estoque do destino; −1 se o
     * estoque já foi atingido. A contagem sai do próprio Baú (O(1) com componentes, O(tipos) sem).
     */
    private static long bulkAccepts(BulkItems target, ItemStack key, long amount, long stock, boolean components) {
        long room = target.insert(key, amount, true);
        if (stock > 0) {
            long counted = components ? target.count(key) : target.countItem(key);
            long want = StockLimit.acceptable(counted, stock, amount);
            if (want <= 0) {
                return -1;
            }
            room = Math.min(room, want);
        }
        return room;
    }

    /** Inserção real: no Baú do mod direto pela chave, senão pelos slots do plano. Devolve a sobra. */
    private static ItemStack execute(@Nullable BulkItems bulkTarget, @Nullable IItemHandler target, ItemStack stack) {
        if (bulkTarget == null) {
            return PLAN.execute(target, stack);
        }
        int count = stack.getCount();
        long in = bulkTarget.insert(stack, count, false);
        return in >= count ? ItemStack.EMPTY : stack.copyWithCount(count - (int) in);
    }

    /** As duas portas dão na mesma máquina (faces diferentes de um mesmo bloco). */
    private static boolean sameMachine(Port a, Port b) {
        return a.machinePos.equals(b.machinePos) && a.node.getLevel() == b.node.getLevel();
    }

    /**
     * Visita de uma origem que é um Baú do mod ({@link BulkItems}): percorre os <b>tipos</b>, com o
     * mesmo cursor, a mesma janela por visita e os mesmos tetos de {@link #move}, e entrega cada tipo
     * em quantidades {@code long} ({@link #deliverBulk}). Filtro e estoque da origem valem igual; o
     * estoque conta pelo inventário visto como slots ({@link StockTally}), uma vez por visita.
     */
    private static int moveBulk(Port source, BulkItems bulk, long now) {
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
        StockTally tally = null;
        IItemHandler view = null;
        if (filtered && filter.usesItemStock()) {
            view = source.node.items(source.face);
            tally = source.tally;
            if (tally == null) {
                tally = new StockTally();
                source.tally = tally;
            }
            tally.reset();
        }
        PLAN.beginVisit();
        FilterSet.ItemRule rule = SOURCE_RULE;
        int index = source.slotCursor < types ? source.slotCursor : 0;
        int limit = Math.min(types, MAX_SLOTS_PER_VISIT);
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
            long amount = deliverBulk(source, bulk, key, max, order, pass, now, MAX_ATTEMPTS_PER_VISIT - attempts - 1);
            attempts += burstSteps;
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
            if (++attempts >= MAX_ATTEMPTS_PER_VISIT) {
                capped = true;
                break;
            }
            if (destinationSlept && !NetworkManager.hasAwakeDestination(pass, now)) {
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
     * ({@link InsertPlan#insert}: uma pilha, ou o limite do slot se ele passa de
     * {@link Item#ABSOLUTE_MAX_STACK_SIZE}, como num barril com upgrade de pilha). Só passa de uma
     * entrega se o pedaço chegou ao teto de um {@code int}; cada uma depois da primeira conta como
     * tentativa ({@link #burstSteps}, até {@code allowance}). Devolve quanto entregou.
     */
    private static long deliverBulk(Port source, BulkItems bulk, ItemStack key, long max,
            RoundRobinOrder<Port> order, List<Port> pass, long now, int allowance) {
        destinationSlept = false;
        burstSteps = 0;
        long remaining = max;
        long moved = 0;
        FilterSet.ItemRule rule = DESTINATION_RULE;
        for (int i = 0, n = pass.size(); i < n && remaining > 0; i++) {
            Port destination = pass.get(i);
            if (destination.destinationBackoff.isSleeping(now) || sameMachine(source, destination)) {
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
            BulkItems bulkTarget = destination.node.bulkItems(destination.face);
            if (bulkTarget != null) {
                long room = bulkAccepts(bulkTarget, key, remaining, stock, components);
                if (room < 0) {
                    continue;
                }
                if (room == 0) {
                    destination.destinationBackoff.sleep(now);
                    destinationSlept = true;
                    continue;
                }
                long taken = bulk.extract(key, room, false);
                if (taken <= 0) {
                    break;
                }
                long in = bulkTarget.insert(key, taken, false);
                if (in < taken) {
                    giveBackBulk(source, bulk, key, taken - in);
                }
                if (in > 0) {
                    delivered(source, destination, order);
                    moved += in;
                    remaining -= in;
                } else {
                    destination.destinationBackoff.sleep(now);
                    destinationSlept = true;
                }
                if (taken < room) {
                    break;
                }
                continue;
            }
            IItemHandler target = destination.node.items(destination.face);
            if (target == null) {
                destination.sleepWithoutMachine(now);
                destinationSlept = true;
                continue;
            }
            boolean first = true;
            boolean sourceEmpty = false;
            while (remaining > 0) {
                // Uma entrega pode levar mais que uma pilha: o plano limita cada slot ao que ele aceita
                // numa chamada (InsertPlan#insert), então um inventário comum recebe o mesmo de antes, e
                // um slot de pilha grande (upgrade de pilha, gaveta) recebe tudo o que cabe de uma vez.
                int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                if (!first) {
                    if (burstSteps >= allowance) {
                        break;
                    }
                    burstSteps++;
                }
                int accepts = planInsert(source, destination, target, key, key.copyWithCount(chunk), stock, components);
                if (accepts < 0) {
                    break;
                }
                if (accepts == 0) {
                    if (first) {
                        destination.destinationBackoff.sleep(now);
                        destinationSlept = true;
                    }
                    break;
                }
                long taken = bulk.extract(key, accepts, false);
                if (taken <= 0) {
                    sourceEmpty = true;
                    break;
                }
                ItemStack leftover = PLAN.execute(target, key.copyWithCount((int) taken));
                int delivered = (int) taken - leftover.getCount();
                if (!leftover.isEmpty()) {
                    giveBackBulk(source, bulk, leftover, leftover.getCount());
                }
                if (delivered > 0) {
                    PLAN.delivered(destination, key, delivered);
                    delivered(source, destination, order);
                    moved += delivered;
                    remaining -= delivered;
                } else if (first) {
                    destination.destinationBackoff.sleep(now);
                    destinationSlept = true;
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

    /** Uma entrega que deixou algo no destino: acorda-o, conta na volta da passada e nas operações. */
    private static void delivered(Port source, Port destination, RoundRobinOrder<Port> order) {
        destination.destinationBackoff.wake();
        order.delivered(destination);
        if (source.network != null) {
            source.network.ops++;
        }
    }

    /**
     * Devolve ao Baú de origem o que o destino recusou depois de aceitar na simulação. Cabe sempre
     * (acabou de sair de lá); se a capacidade da config caiu nesse meio-tempo, o resto cai no mundo.
     */
    private static void giveBackBulk(Port source, BulkItems bulk, ItemStack key, long amount) {
        long back = bulk.insert(key, amount, false);
        long rest = amount - back;
        if (rest <= 0) {
            return;
        }
        Level level = source.node.getLevel();
        WirelessAutomate.LOGGER.warn("Destino recusou {} x {} e o Baú de origem {} não aceitou de volta; soltando no mundo",
                rest, key, source);
        if (level == null) {
            return;
        }
        int stackSize = Math.max(1, key.getMaxStackSize());
        while (rest > 0) {
            int drop = (int) Math.min(rest, stackSize);
            Block.popResource(level, source.node.getBlockPos(), key.copyWithCount(drop));
            rest -= drop;
        }
    }

    private static void giveBack(Port source, IItemHandler handler, int slot, ItemStack leftover) {
        ItemStack rest = handler.insertItem(slot, leftover, false);
        if (!rest.isEmpty()) {
            rest = ItemHandlerHelper.insertItemStacked(handler, rest, false);
        }
        if (!rest.isEmpty()) {
            Level level = source.node.getLevel();
            WirelessAutomate.LOGGER.warn("Destino recusou {} depois de aceitar na simulação e a origem {} não aceitou de volta; soltando no mundo",
                    rest, source);
            if (level != null) {
                Block.popResource(level, source.node.getBlockPos(), rest);
            }
        }
    }

    /**
     * Inserção num destino com uma varredura só por entrega. A simulação dá o mesmo resultado do
     * {@link ItemHandlerHelper#insertItemStacked} (primeiro as pilhas do mesmo item, depois os slots
     * vazios, na ordem dos slots), mas numa passada: anota os vazios enquanto procura as pilhas
     * iguais e lembra os slots que aceitaram. A inserção real vai direto neles; o que sobrar (destino
     * que mudou ou mentiu na simulação) cai no {@code insertItemStacked}, e o que nem ele aceitar
     * volta para quem chamou, como antes. Se o destino tem estoque, a mesma passada conta o item
     * (e então vai até o fim do inventário, ou até a contagem atingir o estoque).
     *
     * <p>Pilha igual já no limite do slot ({@link IItemHandler#getSlotLimit}): a primeira é simulada
     * na ordem, como antes. Se ela recusou, as outras cheias não são simuladas na hora: vão para o
     * fim, depois dos vazios, e só são tentadas se ainda sobrar. Um destino que aceita numa pilha
     * cheia (um upgrade que anula o excesso) aceita já na primeira, e a ordem não muda. Em
     * inventários de pilhas grandes, as cheias são a maior parte das chamadas, e as mais caras.
     * Cheia também é a pilha no tamanho máximo do item abaixo do limite do slot, se a primeira assim
     * recusou: é o baú vanilla (limite 99, pedregulho 64), em que o {@code InvWrapper} limita pelo
     * item. Aí as outras são reconhecidas só pela contagem, sem chamada nenhuma ao destino.
     *
     * <p>Memória da visita: a contagem de cada destino com estoque fica guardada até o fim da visita
     * da origem ({@link #recall}) e soma o que entregamos, então um destino que já atingiu o estoque
     * não é varrido de novo a cada slot da origem. Numa visita as contagens só sobem (só entregamos);
     * a memória de outro destino abaixo do estoque é esquecida a cada entrega, porque dois destinos
     * podem ver o mesmo inventário (metades de um baú duplo, interfaces da mesma rede de armazenamento).
     */
    static final class InsertPlan {
        static final int COUNT_NONE = 0;
        /** Conta pelo item, como {@link ItemStack#isSameItem}. */
        static final int COUNT_ITEM = 1;
        /** Conta pelo item e componentes, como {@link ItemStack#isSameItemSameComponents}. */
        static final int COUNT_COMPONENTS = 2;
        /** Destinos lembrados por visita; além disso, conta de novo como antes. */
        private static final int MEMORY = 32;

        private int[] slots = new int[8];
        private int size;
        private int[] empties = new int[64];
        private int[] fulls = new int[16];
        private long counted;
        /** Tamanho máximo do item da simulação em curso ({@link #insert}). */
        private int itemMax;

        private final Port[] memoPorts = new Port[MEMORY];
        private final Item[] memoItems = new Item[MEMORY];
        /** Com componentes: a pilha (cópia de 1); sem: {@code null}. */
        private final ItemStack[] memoStacks = new ItemStack[MEMORY];
        private final long[] memoCounts = new long[MEMORY];
        private final long[] memoStocks = new long[MEMORY];
        private int memoSize;

        /** Começo da visita de uma origem: esquece as contagens da anterior. */
        void beginVisit() {
            if (memoSize > 0) {
                Arrays.fill(memoPorts, 0, memoSize, null);
                Arrays.fill(memoItems, 0, memoSize, null);
                Arrays.fill(memoStacks, 0, memoSize, null);
                memoSize = 0;
            }
        }

        /** Posição da contagem lembrada do item no destino, ou −1. */
        int recall(Port destination, ItemStack stack, boolean components) {
            for (int i = 0; i < memoSize; i++) {
                if (memoPorts[i] == destination && matches(i, stack, components)) {
                    return i;
                }
            }
            return -1;
        }

        long recalled(int index) {
            return memoCounts[index];
        }

        /** Lembra a contagem do item no destino; devolve a posição, ou −1 sem espaço. */
        int remember(Port destination, ItemStack stack, boolean components, long count, long stock) {
            if (memoSize == MEMORY) {
                return -1;
            }
            int i = memoSize++;
            memoPorts[i] = destination;
            memoItems[i] = stack.getItem();
            memoStacks[i] = components ? stack.copyWithCount(1) : null;
            memoCounts[i] = count;
            memoStocks[i] = stock;
            return i;
        }

        /** Entregamos {@code amount} de {@code stack} no destino: soma no que foi lembrado dele. */
        void delivered(Port destination, ItemStack stack, int amount) {
            for (int i = 0; i < memoSize; i++) {
                Port port = memoPorts[i];
                if (port == destination) {
                    if (matches(i, stack, memoStacks[i] != null)) {
                        memoCounts[i] += amount;
                    }
                } else if (port != null && memoCounts[i] < memoStocks[i]) {
                    // Pode ser o mesmo inventário por outro caminho: abaixo do estoque, conta de novo.
                    memoPorts[i] = null;
                }
            }
        }

        private boolean matches(int i, ItemStack stack, boolean components) {
            ItemStack exact = memoStacks[i];
            if (components) {
                return exact != null && ItemStack.isSameItemSameComponents(exact, stack);
            }
            return exact == null && memoItems[i] == stack.getItem();
        }

        /**
         * Simula a inserção de {@code stack} e guarda o plano; com {@code count}, conta também o
         * item no destino ({@link #counted}) e para quando a contagem chega a {@code stock} (aí o
         * destino não aceita nada pelo estoque, e a resposta é 0). Devolve quanto entraria.
         */
        int simulate(IItemHandler target, ItemStack stack, int count, long stock) {
            size = 0;
            itemMax = stack.getMaxStackSize();
            counted = 0;
            int emptyCount = 0;
            int fullCount = 0;
            boolean fullRefused = false;
            // Tamanho máximo do item, se o destino limita pelo item (ver abaixo); 0 = não limita.
            int itemFull = 0;
            ItemStack rest = stack;
            for (int slot = 0, n = target.getSlots(); slot < n; slot++) {
                boolean placing = !rest.isEmpty();
                if (!placing && count == COUNT_NONE) {
                    break;
                }
                ItemStack inSlot = target.getStackInSlot(slot);
                if (inSlot.isEmpty()) {
                    if (placing) {
                        if (emptyCount == empties.length) {
                            empties = Arrays.copyOf(empties, emptyCount * 2);
                        }
                        empties[emptyCount++] = slot;
                    }
                    continue;
                }
                boolean same = ItemStack.isSameItemSameComponents(inSlot, stack);
                if (count == COUNT_COMPONENTS ? same : count == COUNT_ITEM && ItemStack.isSameItem(inSlot, stack)) {
                    counted += inSlot.getCount();
                    if (counted >= stock) {
                        // Estoque atingido: o resto do inventário não muda a resposta.
                        size = 0;
                        return 0;
                    }
                }
                if (same && placing) {
                    int inCount = inSlot.getCount();
                    if (fullRefused && (itemFull > 0 && inCount >= itemFull
                            || inCount >= target.getSlotLimit(slot))) {
                        if (fullCount == fulls.length) {
                            fulls = Arrays.copyOf(fulls, fullCount * 2);
                        }
                        fulls[fullCount++] = slot;
                    } else {
                        int before = rest.getCount();
                        rest = offer(target, slot, rest);
                        if (rest.getCount() == before && !fullRefused) {
                            if (inCount >= target.getSlotLimit(slot)) {
                                fullRefused = true;
                            } else {
                                // Recusou no tamanho máximo do item, abaixo do limite do slot (baú
                                // vanilla: limite 99, pedregulho 64): o destino limita pelo item. As
                                // pilhas iguais têm o mesmo item e componentes, logo o mesmo máximo,
                                // lido uma vez só (o getMaxStackSize consulta os componentes e é a
                                // parte cara de cada recusa do InvWrapper).
                                int max = inSlot.getMaxStackSize();
                                if (inCount >= max) {
                                    fullRefused = true;
                                    itemFull = max;
                                }
                            }
                        }
                    }
                }
            }
            for (int i = 0; i < emptyCount && !rest.isEmpty(); i++) {
                rest = offer(target, empties[i], rest);
            }
            for (int i = 0; i < fullCount && !rest.isEmpty(); i++) {
                rest = offer(target, fulls[i], rest);
            }
            return stack.getCount() - rest.getCount();
        }

        /** Quanto do item a última {@link #simulate} contou no destino. */
        long counted() {
            return counted;
        }

        private ItemStack offer(IItemHandler target, int slot, ItemStack stack) {
            ItemStack rest = insert(target, slot, stack, itemMax, true);
            if (rest.getCount() < stack.getCount()) {
                if (size == slots.length) {
                    slots = Arrays.copyOf(slots, size * 2);
                }
                slots[size++] = slot;
            }
            return rest;
        }

        /**
         * Insere de verdade nos slots do plano; o que sobrar vai pelo empilhado. Devolve a sobra. Vem
         * sempre depois da {@link #simulate} do mesmo item, de quem reaproveita o tamanho máximo.
         */
        ItemStack execute(IItemHandler target, ItemStack stack) {
            ItemStack rest = stack;
            for (int i = 0; i < size && !rest.isEmpty(); i++) {
                rest = insert(target, slots[i], rest, itemMax, false);
            }
            return rest.isEmpty() ? rest : insertStacked(target, rest, itemMax);
        }

        /**
         * {@link IItemHandler#insertItem} sem passar, numa chamada, do que o slot aceita: até o tamanho
         * máximo do item ({@code itemMax}), ou até o limite do slot se ele passa de
         * {@link Item#ABSOLUTE_MAX_STACK_SIZE} (99, o limite comum do vanilla e do {@code ItemStackHandler}).
         * Um slot que declara mais que isso (upgrade de pilha, gaveta) recebe a pilha grande de uma vez;
         * os outros nunca veem uma pilha acima do tamanho do item, mesmo que não a limitem. Devolve a sobra.
         */
        static ItemStack insert(IItemHandler target, int slot, ItemStack stack, int itemMax, boolean simulate) {
            int count = stack.getCount();
            if (count > itemMax) {
                int limit = target.getSlotLimit(slot);
                int cap = limit > Item.ABSOLUTE_MAX_STACK_SIZE ? Math.max(itemMax, limit) : itemMax;
                if (count > cap) {
                    ItemStack rest = target.insertItem(slot, stack.copyWithCount(cap), simulate);
                    return stack.copyWithCount(count - cap + rest.getCount());
                }
            }
            return target.insertItem(slot, stack, simulate);
        }

        /**
         * O {@link ItemHandlerHelper#insertItemStacked} (primeiro as pilhas do mesmo item, depois os
         * vazios, na ordem dos slots), com cada chamada limitada como em {@link #insert}. Devolve a sobra.
         */
        static ItemStack insertStacked(IItemHandler target, ItemStack stack, int itemMax) {
            ItemStack rest = stack;
            int slots = target.getSlots();
            if (stack.isStackable()) {
                for (int slot = 0; slot < slots && !rest.isEmpty(); slot++) {
                    if (ItemStack.isSameItemSameComponents(target.getStackInSlot(slot), rest)) {
                        rest = insert(target, slot, rest, itemMax, false);
                    }
                }
                for (int slot = 0; slot < slots && !rest.isEmpty(); slot++) {
                    if (target.getStackInSlot(slot).isEmpty()) {
                        rest = insert(target, slot, rest, itemMax, false);
                    }
                }
            } else {
                for (int slot = 0; slot < slots && !rest.isEmpty(); slot++) {
                    rest = insert(target, slot, rest, itemMax, false);
                }
            }
            return rest;
        }
    }

    /**
     * Quanto de cada item com estoque há na origem, contado uma vez por visita (na primeira
     * consulta) e descontado conforme os itens saem. Os mapas são reaproveitados entre visitas.
     * Conta por item e, se algum filtro com estoque exige componentes, também por item +
     * componentes; a regra do estoque ({@link FilterSet#evaluateItem}) escolhe qual contagem vale.
     *
     * <p>Sem filtro que exija componentes ({@link FilterSet#itemsByItemOnly}), o estoque de uma pilha
     * depende só do item, então conta todos os slots por item sem consultar o filtro: a consulta
     * seria a mesma para todas as pilhas do item, e o item sem estoque nunca é procurado.
     */
    static final class StockTally {
        private final Reference2ObjectOpenHashMap<Item, long[]> byItem = new Reference2ObjectOpenHashMap<>();
        private final Object2ObjectOpenCustomHashMap<ItemStack, long[]> byStack =
                new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
        private final FilterSet.ItemRule rule = new FilterSet.ItemRule();
        private boolean counted;

        void reset() {
            itemCell = null;
            stackCell = null;
            if (counted) {
                byItem.clear();
                byStack.clear();
                counted = false;
            }
        }

        /** A contagem do item, num {@code long[1]} que quem move desconta; {@code null} se não houver. */
        private long @Nullable [] itemCell;
        private long @Nullable [] stackCell;

        /**
         * Quanto há do item na origem, pela chave da regra do estoque ({@code components}: a regra
         * exige componentes iguais). Guarda as contagens dele para {@link #took}, que desconta das
         * duas (a pilha do slot pode mudar ao sair).
         */
        long lookup(IItemHandler handler, FilterSet filters, boolean components, ItemStack stack) {
            if (!counted) {
                count(handler, filters);
                counted = true;
            }
            itemCell = byItem.get(stack.getItem());
            stackCell = byStack.isEmpty() ? null : byStack.get(stack);
            long[] cell = components ? stackCell : itemCell;
            return cell == null ? 0 : cell[0];
        }

        /** Saíram {@code amount} do item da última {@link #lookup}. */
        void took(long amount) {
            if (itemCell != null) {
                itemCell[0] -= amount;
            }
            if (stackCell != null) {
                stackCell[0] -= amount;
            }
        }

        private void count(IItemHandler handler, FilterSet filters) {
            boolean byItemOnly = filters.itemsByItemOnly();
            boolean components = filters.anyStockMatchesComponents();
            Item lastItem = null;
            long[] lastCell = null;
            for (int slot = 0, n = handler.getSlots(); slot < n; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!byItemOnly && (!filters.evaluateItem(stack, rule) || rule.stock <= 0)) {
                    continue;
                }
                Item item = stack.getItem();
                long[] cell = item == lastItem ? lastCell : byItem.get(item);
                if (cell == null) {
                    cell = new long[1];
                    byItem.put(item, cell);
                }
                lastItem = item;
                lastCell = cell;
                cell[0] += stack.getCount();
                if (components) {
                    long[] exact = byStack.get(stack);
                    if (exact == null) {
                        exact = new long[1];
                        // Cópia: a pilha do slot muda (e vira vazia) quando os itens saem.
                        byStack.put(stack.copyWithCount(1), exact);
                    }
                    exact[0] += stack.getCount();
                }
            }
        }
    }

    private ItemTransfer() {
    }
}
