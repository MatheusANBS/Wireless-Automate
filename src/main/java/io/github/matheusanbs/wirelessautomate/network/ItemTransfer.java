package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
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
 * Se nenhum destino aceitar nenhum item, a origem é que dorme, pela volta sem mover.
 *
 * <p>Estoque: na origem, mantém N do item (mesmo item; com {@code matchComponents}, mesmos
 * componentes) contando o inventário inteiro uma vez por visita, só quando a visita encontra um
 * slot com estoque ({@link StockTally}): O(slots) por visita, e a conta é descontada conforme os
 * itens saem. No destino, aceita até N contando os slots do destino na mesma varredura que simula
 * a inserção ({@link InsertPlan}), só para entradas com estoque. Sem estoque no filtro, nada é contado.
 *
 * <p>Custo de uma entrega: uma varredura do destino ({@link InsertPlan}), em vez das quatro de
 * chamar o {@link ItemHandlerHelper#insertItemStacked} na simulação e de novo na inserção.
 */
final class ItemTransfer {
    /** Slots examinados por visita: inventário grande continua do cursor no tick seguinte. */
    static final int MAX_SLOTS_PER_VISIT = 128;
    /** Slots com item que tentam entregar por visita, para uma origem não comer o orçamento sozinha. */
    static final int MAX_ATTEMPTS_PER_VISIT = 32;
    /** Plano da última simulação de inserção; o laço roda numa thread só (a do servidor). */
    private static final InsertPlan PLAN = new InsertPlan();

    static void move(Port source, long now) {
        IItemHandler handler = source.node.items(source.face);
        RoundRobinOrder<Port> order = source.order;
        if (handler == null || order == null) {
            // Máquina sumiu ou chunk descarregado: a invalidação do cache refaz as rotas e acorda.
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
        int slots = handler.getSlots();
        if (slots <= 0) {
            source.sourceBackoff.sleep(now);
            return;
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
        int slot = source.slotCursor < slots ? source.slotCursor : 0;
        int limit = Math.min(slots, MAX_SLOTS_PER_VISIT);
        int scanned = 0;
        int attempts = 0;
        long moved = 0;
        boolean destinationsAsleep = false;
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
                if (!filter.testItem(inSlot)) {
                    continue;
                }
                Filter rule = tally == null ? null : filter.itemStockFilter(inSlot);
                long stock = rule == null ? 0 : rule.itemStock(inSlot);
                if (stock > 0) {
                    stocked = true;
                    max = (int) StockLimit.extractable(tally.lookup(handler, filter, rule, inSlot), stock, max);
                    if (max <= 0) {
                        continue;
                    }
                }
            }
            int amount = moveSlot(source, handler, current, max, order, pass, now);
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
            }
            if (++attempts >= MAX_ATTEMPTS_PER_VISIT) {
                break;
            }
            if (!NetworkManager.hasAwakeDestination(pass, now)) {
                destinationsAsleep = true;
                break;
            }
        }
        source.slotCursor = slot;
        if (moved > 0) {
            source.node.addMoved(source.type, moved);
            source.idleSlots = 0;
            source.limiter.consume(moved);
            source.sourceBackoff.wake();
            return;
        }
        source.idleSlots += scanned;
        if (destinationsAsleep || source.idleSlots >= slots) {
            // Uma volta inteira sem mover (mesmo dividida em visitas), ou todos os destinos
            // recusaram: dorme até alguém mudar.
            source.idleSlots = 0;
            source.sourceBackoff.sleep(now);
        }
    }

    /** Entrega o que der do slot, na ordem da passada. Devolve quanto foi entregue. */
    private static int moveSlot(Port source, IItemHandler handler, int slot, int max,
            RoundRobinOrder<Port> order, List<Port> pass, long now) {
        ItemStack offered = handler.extractItem(slot, max, true);
        if (offered.isEmpty()) {
            return 0;
        }
        int remaining = offered.getCount();
        int moved = 0;
        for (int i = 0, n = pass.size(); i < n && remaining > 0; i++) {
            Port destination = pass.get(i);
            if (destination.destinationBackoff.isSleeping(now)) {
                continue;
            }
            IItemHandler target = destination.node.items(destination.face);
            if (target == null) {
                continue;
            }
            FilterSet accept = destination.filter;
            long stock = 0;
            int count = InsertPlan.COUNT_NONE;
            if (!accept.isEmpty()) {
                // Recusa do filtro ou estoque já atingido: pula sem dormir (ver o javadoc da classe).
                if (!accept.testItem(offered)) {
                    continue;
                }
                Filter rule = accept.itemStockFilter(offered);
                stock = rule == null ? 0 : rule.itemStock(offered);
                if (stock > 0) {
                    count = rule.matchComponents() ? InsertPlan.COUNT_COMPONENTS : InsertPlan.COUNT_ITEM;
                }
            }
            ItemStack probe = remaining == offered.getCount() ? offered : offered.copyWithCount(remaining);
            int accepts = PLAN.simulate(target, probe, count);
            if (stock > 0) {
                int want = (int) StockLimit.acceptable(PLAN.counted(), stock, remaining);
                if (want <= 0) {
                    continue;
                }
                accepts = Math.min(accepts, want);
            }
            if (accepts <= 0) {
                destination.destinationBackoff.sleep(now);
                continue;
            }
            ItemStack taken = handler.extractItem(slot, accepts, false);
            if (taken.isEmpty()) {
                break;
            }
            int takenCount = taken.getCount();
            ItemStack leftover = PLAN.execute(target, taken);
            int delivered = takenCount - leftover.getCount();
            if (!leftover.isEmpty()) {
                giveBack(source, handler, slot, leftover);
            }
            if (delivered > 0) {
                destination.destinationBackoff.wake();
                order.delivered(destination);
                if (source.network != null) {
                    source.network.ops++;
                }
                moved += delivered;
                remaining -= delivered;
            } else {
                destination.destinationBackoff.sleep(now);
            }
            if (takenCount < accepts) {
                break;
            }
        }
        return moved;
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
     * (e então vai até o fim do inventário).
     */
    static final class InsertPlan {
        static final int COUNT_NONE = 0;
        /** Conta pelo item, como {@link ItemStack#isSameItem}. */
        static final int COUNT_ITEM = 1;
        /** Conta pelo item e componentes, como {@link ItemStack#isSameItemSameComponents}. */
        static final int COUNT_COMPONENTS = 2;

        private int[] slots = new int[8];
        private int size;
        private int[] empties = new int[64];
        private long counted;

        /**
         * Simula a inserção de {@code stack} e guarda o plano; com {@code count}, conta também o
         * item no destino ({@link #counted}). Devolve quanto entraria.
         */
        int simulate(IItemHandler target, ItemStack stack, int count) {
            size = 0;
            counted = 0;
            int emptyCount = 0;
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
                }
                if (same && placing) {
                    rest = offer(target, slot, rest);
                }
            }
            for (int i = 0; i < emptyCount && !rest.isEmpty(); i++) {
                rest = offer(target, empties[i], rest);
            }
            return stack.getCount() - rest.getCount();
        }

        /** Quanto do item a última {@link #simulate} contou no destino. */
        long counted() {
            return counted;
        }

        private ItemStack offer(IItemHandler target, int slot, ItemStack stack) {
            ItemStack rest = target.insertItem(slot, stack, true);
            if (rest.getCount() < stack.getCount()) {
                if (size == slots.length) {
                    slots = Arrays.copyOf(slots, size * 2);
                }
                slots[size++] = slot;
            }
            return rest;
        }

        /** Insere de verdade nos slots do plano; o que sobrar vai pelo empilhado. Devolve a sobra. */
        ItemStack execute(IItemHandler target, ItemStack stack) {
            ItemStack rest = stack;
            for (int i = 0; i < size && !rest.isEmpty(); i++) {
                rest = target.insertItem(slots[i], rest, false);
            }
            return rest.isEmpty() ? rest : ItemHandlerHelper.insertItemStacked(target, rest, false);
        }
    }

    /**
     * Quanto de cada item com estoque há na origem, contado uma vez por visita (na primeira
     * consulta) e descontado conforme os itens saem. Os mapas são reaproveitados entre visitas.
     * Conta por item e, se algum filtro com estoque exige componentes, também por item +
     * componentes; a regra do estoque ({@link FilterSet#itemStockFilter}) escolhe qual contagem vale.
     */
    static final class StockTally {
        private final Reference2ObjectOpenHashMap<Item, long[]> byItem = new Reference2ObjectOpenHashMap<>();
        private final Object2ObjectOpenCustomHashMap<ItemStack, long[]> byStack =
                new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
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
         * Quanto há do item na origem, pela chave da regra do estoque. Guarda as contagens dele para
         * {@link #took}, que desconta das duas (a pilha do slot pode mudar ao sair).
         */
        long lookup(IItemHandler handler, FilterSet filters, Filter rule, ItemStack stack) {
            if (!counted) {
                count(handler, filters);
                counted = true;
            }
            itemCell = byItem.get(stack.getItem());
            stackCell = byStack.isEmpty() ? null : byStack.get(stack);
            long[] cell = rule.matchComponents() ? stackCell : itemCell;
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
            boolean components = filters.anyStockMatchesComponents();
            for (int slot = 0, n = handler.getSlots(); slot < n; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || filters.itemStock(stack) <= 0) {
                    continue;
                }
                long[] cell = byItem.get(stack.getItem());
                if (cell == null) {
                    cell = new long[1];
                    byItem.put(stack.getItem(), cell);
                }
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
