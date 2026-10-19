package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.storage.BulkItems;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Move itens de uma origem para os destinos dela, numa visita do {@link NetworkManager}.
 *
 * <p>Para não duplicar nem perder, cada entrega segue: simula a extração, simula a inserção no
 * destino, extrai de verdade só o que o destino aceitaria e insere de verdade exatamente o que
 * saiu. Assim nunca se insere algo que não foi tirado. Se o destino mentiu na simulação e devolve
 * sobra, ela volta para o mesmo slot da origem; se ele não aceitar (slot só de saída, por exemplo),
 * vai para os destinos da passada cujo filtro deixe, e só o resto cai no mundo na posição do
 * roteador, com o caso no log. Se a extração real der menos que o simulado, só o que saiu é entregue e a visita para.
 *
 * <p>Filtros ({@link Port#filter}: o embutido e os dos cartões, montados na montagem das rotas; ver
 * {@link FilterSet}): o da origem decide que slots podem sair, e o do destino o que pode entrar. Um slot recusado pela origem só conta como varrido (a
 * volta inteira sem mover ainda faz a origem dormir). Um destino que recusa pelo filtro, ou que já
 * tem o estoque, é só pulado, <b>sem dormir</b>: recusar o item A não diz nada sobre o B, e o
 * backoff de "cheio" o deixaria de fora também para o B. Só a recusa por falta de espaço (a
 * inserção simulada não aceita nada de um item que o filtro deixa entrar) faz o destino dormir.
 * A máquina que recusa o item pelo tipo também é só pulada ({@link ItemInsertPlan#refusesItem}): ela
 * tem slot vazio, nenhum dos vazios o aceita ({@link IItemHandler#isItemValid}) e não há pilha dele
 * lá dentro. É a Salvaging Table do Apotheosis com o que não se desmonta, ou uma máquina com o que
 * não tem receita: sem isso, o primeiro item sem receita do baú a punha para dormir como cheia, e
 * os que ela aceitaria esperavam o sono acabar.
 * Se nenhum destino aceitar nenhum item, a origem é que dorme, pela volta sem mover; se todos os
 * destinos estão dormindo, ela dorme até o primeiro deles acordar ({@link SourceSleep}).
 *
 * <p>Estoque: na origem, mantém N do item (mesmo item; com {@code matchComponents}, mesmos
 * componentes) contando o inventário inteiro uma vez por visita, só quando a visita encontra um
 * slot com estoque ({@link ItemStockTally}): O(slots) por visita, e a conta é descontada conforme os
 * itens saem. No destino, aceita até N contando os slots do destino na mesma varredura que simula
 * a inserção ({@link ItemInsertPlan}), só para entradas com estoque, parando ao atingir o estoque; a
 * contagem fica lembrada durante a visita, então o destino cheio não é varrido de novo a cada slot.
 * Sem estoque no filtro, nada é contado.
 *
 * <p>Custo de uma entrega: uma varredura do destino ({@link ItemInsertPlan}), em vez das quatro de
 * chamar o {@link ItemHandlerHelper#insertItemStacked} na simulação e de novo na inserção.
 *
 * <p>Baú do mod ({@link BulkItems}): como origem, a visita percorre tipos em vez de slots
 * ({@link ItemBulkTransfer}); como destino, guarda pela chave, sem varrer slots. Entre dois Baús, um tipo
 * inteiro passa numa chamada só, sem o teto de uma pilha por extração do {@link IItemHandler}. As
 * regras de filtro, estoque, ordem e sono são as mesmas.
 *
 * <p>Partes: a visita em massa ({@link ItemBulkTransfer}), o plano da inserção ({@link ItemInsertPlan}), a
 * contagem do estoque da origem ({@link ItemStockTally}) e a devolução com o resgate ({@link ItemGiveBack}).
 * Nenhuma cria objeto por visita: o plano é um só e a contagem do estoque é reaproveitada por porta.
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
    static final ItemInsertPlan PLAN = new ItemInsertPlan();
    /** Respostas do filtro da origem e do destino ({@link FilterSet#evaluateItem}), reaproveitadas. */
    static final FilterSet.ItemRule SOURCE_RULE = new FilterSet.ItemRule();
    static final FilterSet.ItemRule DESTINATION_RULE = new FilterSet.ItemRule();
    /** Quanto a extração simulada do último {@link #moveSlot} ofereceu. */
    private static int lastOffered;
    /** O último {@link #moveSlot} (ou {@link ItemBulkTransfer#deliverBulk}) pôs algum destino para dormir. */
    static boolean destinationSlept;
    /**
     * Entregas da rajada do último {@link #moveSlot} (ou as entregas extras do último
     * {@link ItemBulkTransfer#deliverBulk}); cada uma conta como uma tentativa.
     */
    static int burstSteps;

    /**
     * Uma visita. Devolve {@link NetworkManager#MOVED} se moveu algo, mais {@link NetworkManager#MORE}
     * se parou num teto da visita (tentativas, ou a janela de slots num inventário maior que ela) e o
     * balde ainda tem saldo: o gerenciador pode visitá-la de novo no mesmo tick se sobrar orçamento.
     */
    static int move(Port source, long now) {
        // O balde vazio volta antes de consultar a máquina: a origem limitada pela vazão é a maioria das visitas.
        long tokens = source.limiter.available(now);
        if (tokens <= 0) {
            return 0;
        }
        BulkItems bulk = source.machine.bulkItems(source.face);
        if (bulk != null) {
            return ItemBulkTransfer.moveBulk(source, bulk, now);
        }
        IItemHandler handler = source.machine.items(source.face);
        RoundRobinOrder<Port> order = source.order;
        if (handler == null || order == null) {
            // Máquina sumiu ou chunk descarregado: dorme como vazia. O listener da capability da
            // face (ou a troca do bloco da máquina) acorda a porta, sem remontar as rotas.
            SourceSleep.nothingToMove(source, now);
            return 0;
        }
        if (SourceSleep.allAsleep(source, order, now)) {
            return 0;
        }
        List<Port> pass = order.pass();
        int slots = handler.getSlots();
        if (slots <= 0) {
            SourceSleep.nothingToMove(source, now);
            return 0;
        }
        FilterSet filter = source.filter;
        boolean filtered = !filter.isEmpty();
        ItemStockTally tally = null;
        if (filtered && filter.usesItemStock()) {
            tally = ItemStockTally.prepare(source);
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
        boolean more = moved > 0 && tokens > 0 && (capped || (scanned >= limit && limit < slots));
        return endVisit(source, moved, more, scanned, slots, destinationsAsleep, pass, now);
    }

    /**
     * O fim de uma visita pilha a pilha ({@link #move}): soma o movido e acorda a origem, ou conta os slots
     * varridos e a põe para dormir (destinos todos dormindo, ou uma volta inteira sem mover). Devolve o
     * resultado da visita; {@code more} só vale com algo movido.
     */
    private static int endVisit(Port source, long moved, boolean more, int scanned, int slots,
            boolean destinationsAsleep, List<Port> pass, long now) {
        if (moved > 0) {
            source.node.addMoved(source.type, moved);
            source.gave += moved;
            source.idleSlots = 0;
            source.limiter.consume(moved);
            source.sourceBackoff.wake();
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
            BulkItems bulkTarget = destination.machine.bulkItems(destination.face);
            IItemHandler target = bulkTarget == null ? destination.machine.items(destination.face) : null;
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
                if (target != null && PLAN.refusesItem(target, probe)) {
                    // A máquina não aceita este item (sem receita): pula sem dormir, como o filtro.
                    continue;
                }
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
                ItemGiveBack.giveBack(source, handler, slot, leftover, pass);
            }
            if (delivered > 0) {
                PLAN.delivered(destination, offered, delivered);
                destination.destinationBackoff.wake();
                destination.got += delivered;
                order.delivered(destination);
                if (source.network != null) {
                    source.network.ops++;
                }
                moved += delivered;
                remaining -= delivered;
                if (remaining == 0 && stock == 0 && allowance > 0 && takenCount == offered.getCount()) {
                    // Entregou a extração inteira e o slot pode ter mais (gaveta, upgrade de pilha):
                    // as próximas vão direto para este destino, sem simular.
                    moved += burst(source, handler, slot, bulkTarget, target, destination, offered, max - moved, allowance, pass);
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
            @Nullable IItemHandler target, Port destination, ItemStack offered, int cap, int allowance, List<Port> pass) {
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
                ItemGiveBack.giveBack(source, handler, slot, taken, pass);
                break;
            }
            int takenCount = taken.getCount();
            ItemStack leftover = execute(bulkTarget, target, taken);
            int delivered = takenCount - leftover.getCount();
            if (!leftover.isEmpty()) {
                ItemGiveBack.giveBack(source, handler, slot, leftover, pass);
            }
            if (delivered > 0) {
                destination.got += delivered;
                if (source.network != null) {
                    source.network.ops++;
                }
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
    static int planInsert(Port source, Port destination, IItemHandler target, ItemStack offered,
            ItemStack probe, long stock, boolean components) {
        if (stock <= 0) {
            return PLAN.simulate(target, probe, ItemInsertPlan.COUNT_NONE, 0);
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
            accepts = PLAN.simulate(target, probe, ItemInsertPlan.COUNT_NONE, 0);
        } else {
            accepts = PLAN.simulate(target, probe,
                    components ? ItemInsertPlan.COUNT_COMPONENTS : ItemInsertPlan.COUNT_ITEM, stock);
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
    static long bulkAccepts(BulkItems target, ItemStack key, long amount, long stock, boolean components) {
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
    static boolean sameMachine(Port a, Port b) {
        return a.machinePos.equals(b.machinePos) && a.node.getLevel() == b.node.getLevel();
    }


    private ItemTransfer() {
    }
}
