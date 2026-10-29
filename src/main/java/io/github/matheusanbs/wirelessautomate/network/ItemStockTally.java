package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Arrays;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Quanto de cada item com estoque há na origem, contado uma vez por visita (na primeira
 * consulta) e descontado conforme os itens saem. Os mapas são reaproveitados entre visitas.
 * Conta por item e, se algum filtro com estoque exige componentes, também por item +
 * componentes; a regra do estoque ({@link FilterSet#evaluateItem}) escolhe qual contagem vale.
 *
 * <p>Sem filtro que exija componentes ({@link FilterSet#itemsByItemOnly}), o estoque de uma pilha
 * depende só do item, então conta todos os slots por item sem consultar o filtro: a consulta
 * seria a mesma para todas as pilhas do item, e o item sem estoque nunca é procurado.
 *
 * <p>As células da contagem ({@code long[1]}) vêm de um estoque reaproveitado entre visitas, e os mapas encolhem
 * quando uma visita usa menos de um quarto do que já usaram: uma origem que esvaziou não guarda a memória de quando
 * estava cheia.
 */
final class ItemStockTally {
    private final Reference2ObjectOpenHashMap<Item, long[]> byItem = new Reference2ObjectOpenHashMap<>();
    private final Object2ObjectOpenCustomHashMap<ItemStack, long[]> byStack =
            new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
    private final FilterSet.ItemRule rule = new FilterSet.ItemRule();
    private boolean counted;
    /** As células já criadas; as {@link #used} primeiras estão nos mapas nesta visita. */
    private long[][] cells = new long[8][];
    private int used;
    /** O maior número de itens contados desde o último encolhimento dos mapas. */
    private int peak;

    /** A contagem da origem, reaproveitada entre visitas (criada na primeira) e zerada para esta visita. */
    static ItemStockTally prepare(Port source) {
        ItemStockTally tally = source.tally;
        if (tally == null) {
            tally = new ItemStockTally();
            source.tally = tally;
        }
        tally.reset();
        return tally;
    }

    void reset() {
        itemCell = null;
        stackCell = null;
        if (counted) {
            int size = byItem.size() + byStack.size();
            peak = Math.max(peak, size);
            byItem.clear();
            byStack.clear();
            if (peak > 64 && size * 4 < peak) {
                byItem.trim();
                byStack.trim();
                peak = size;
                if (cells.length > 2 * size + 8) {
                    cells = Arrays.copyOf(cells, 2 * size + 8);
                }
            }
            used = 0;
            counted = false;
        }
    }

    /** Uma célula zerada para esta visita. */
    private long[] cell() {
        if (used == cells.length) {
            cells = Arrays.copyOf(cells, used * 2);
        }
        long[] cell = cells[used];
        if (cell == null) {
            cell = new long[1];
            cells[used] = cell;
        } else {
            cell[0] = 0;
        }
        used++;
        return cell;
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
            if (!byItemOnly && (!filters.evaluateItem(stack, rule) || rule.keep <= 0)) {
                continue;
            }
            Item item = stack.getItem();
            long[] cell = item == lastItem ? lastCell : byItem.get(item);
            if (cell == null) {
                cell = cell();
                byItem.put(item, cell);
            }
            lastItem = item;
            lastCell = cell;
            cell[0] += stack.getCount();
            if (components) {
                long[] exact = byStack.get(stack);
                if (exact == null) {
                    exact = cell();
                    // Cópia: a pilha do slot muda (e vira vazia) quando os itens saem.
                    byStack.put(stack.copyWithCount(1), exact);
                }
                exact[0] += stack.getCount();
            }
        }
    }
}
