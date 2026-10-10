package io.github.matheusanbs.wirelessautomate.filter;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * O conjunto de filtros de uma face para um tipo: o filtro embutido e os dos cartões nos slots
 * (especificação, "Filtros": "o recurso passa se for aceito pelo conjunto"). Imutável; o motor o
 * monta na montagem das rotas e o laço só consulta.
 *
 * <p>Regras do conjunto:
 * <ul>
 *   <li>filtro sem entradas (embutido ou cartão) não conta, nem como lista branca nem como negra;
 *   <li>sem nenhum filtro com entradas, passa tudo (igual a um {@link Filter} vazio);
 *   <li>senão, passa se <b>algum</b> filtro com entradas aceitar (lista branca que casa ou lista
 *       negra que não casa);
 *   <li>estoque: o da primeira regra que casar, numa lista branca, olhando o embutido primeiro e
 *       depois os cartões na ordem dos slots. Se essa regra não tiver estoque, não há limite,
 *       mesmo que uma regra de outro filtro tenha.
 * </ul>
 *
 * <p>Custo: a correspondência compilada vive em cada {@link Filter}, então o conjunto não recompila
 * nada; conferir um recurso custa uma consulta por filtro com entradas. O laço de itens usa
 * {@link #evaluateItem}, que responde "passa?" e o estoque na mesma passada.
 * Uma face sem cartões usa {@link Filter#asSet()}, guardado no próprio filtro, e não aloca.
 */
public final class FilterSet {
    public static final FilterSet EMPTY = new FilterSet(new Filter[0]);

    /** Só os filtros com entradas, o embutido primeiro. */
    private final Filter[] filters;
    private final boolean usesItemStock;
    private final boolean usesFluidStock;
    private final boolean usesChemicalStock;
    private final boolean itemsByItemOnly;

    /**
     * Resposta de {@link #evaluateItem} além do "passa?": o estoque da regra (0 = sem limite) e se a
     * contagem dele exige componentes iguais. Mutável e reaproveitada por quem chama (o laço roda
     * numa thread só).
     */
    public static final class ItemRule {
        public long stock;
        public boolean matchComponents;
    }

    private FilterSet(Filter[] filters) {
        this.filters = filters;
        boolean items = false;
        boolean fluids = false;
        boolean chemicals = false;
        boolean byItem = true;
        for (Filter filter : filters) {
            byItem &= !filter.matchComponents();
            items |= filter.usesItemStock();
            fluids |= filter.usesFluidStock();
            chemicals |= filter.usesChemicalStock();
        }
        this.usesItemStock = items;
        this.usesFluidStock = fluids;
        this.usesChemicalStock = chemicals;
        this.itemsByItemOnly = byItem;
    }

    /** Conjunto de um filtro só (sem cartões). Prefira {@link Filter#asSet()}, que fica em cache. */
    static FilterSet single(Filter filter) {
        return filter.isEmpty() ? EMPTY : new FilterSet(new Filter[] {filter});
    }

    /** O filtro embutido e os dos cartões, nessa ordem. Filtros sem entradas são deixados de fora. */
    public static FilterSet of(Filter embedded, List<Filter> cards) {
        if (cards.isEmpty()) {
            return embedded.asSet();
        }
        List<Filter> kept = new ArrayList<>(cards.size() + 1);
        if (!embedded.isEmpty()) {
            kept.add(embedded);
        }
        for (Filter card : cards) {
            if (!card.isEmpty()) {
                kept.add(card);
            }
        }
        if (kept.isEmpty()) {
            return EMPTY;
        }
        if (kept.size() == 1) {
            return kept.get(0).asSet();
        }
        return new FilterSet(kept.toArray(new Filter[0]));
    }

    /** Nenhum filtro com entradas: passa tudo e não há estoque. */
    public boolean isEmpty() {
        return filters.length == 0;
    }

    /** Quantos filtros com entradas o conjunto tem. */
    public int size() {
        return filters.length;
    }

    /** O item passa pelo conjunto? */
    public boolean testItem(ItemStack stack) {
        if (filters.length == 0) {
            return true;
        }
        for (Filter filter : filters) {
            if (filter.testItem(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * O item passa pelo conjunto? E, se passa, o estoque dele (o mesmo de {@link #itemStockFilter} e
     * {@link #itemStock}) em {@code rule}, tudo numa passada: uma consulta por filtro, no máximo.
     * Se não passa, {@code rule} fica zerado.
     */
    public boolean evaluateItem(ItemStack stack, ItemRule rule) {
        rule.stock = 0;
        rule.matchComponents = false;
        if (filters.length == 0) {
            return true;
        }
        boolean passes = false;
        // O estoque é o da primeira lista branca que casa; sem estoque no conjunto, nem procura.
        boolean decided = !usesItemStock;
        for (Filter filter : filters) {
            int index = filter.itemIndex(stack);
            boolean whitelist = filter.listMode() == Filter.ListMode.WHITELIST;
            if ((index >= 0) == whitelist) {
                passes = true;
            }
            if (!decided && whitelist && index >= 0) {
                decided = true;
                long stock = filter.entries().get(index).stock();
                if (stock > 0) {
                    rule.stock = stock;
                    rule.matchComponents = filter.matchComponents();
                }
            }
            if (passes && decided) {
                break;
            }
        }
        if (!passes) {
            rule.stock = 0;
            rule.matchComponents = false;
        }
        return passes;
    }

    /**
     * Nenhum filtro do conjunto exige componentes: o "passa?" e o estoque de um item dependem só do
     * {@code Item}, não da pilha (a contagem do estoque da origem pode ser por item, sem o filtro).
     */
    public boolean itemsByItemOnly() {
        return itemsByItemOnly;
    }

    /** O fluido passa pelo conjunto? */
    public boolean testFluid(FluidStack stack) {
        if (filters.length == 0) {
            return true;
        }
        for (Filter filter : filters) {
            if (filter.testFluid(stack)) {
                return true;
            }
        }
        return false;
    }

    /** O químico (pelo id) passa pelo conjunto? */
    public boolean testChemical(ResourceLocation chemical) {
        if (filters.length == 0) {
            return true;
        }
        for (Filter filter : filters) {
            if (filter.testChemical(chemical)) {
                return true;
            }
        }
        return false;
    }

    /** Algum filtro do conjunto tem estoque para itens. Barato: calculado na criação. */
    public boolean usesItemStock() {
        return usesItemStock;
    }

    public boolean usesFluidStock() {
        return usesFluidStock;
    }

    /**
     * O filtro cuja regra dá o estoque do item: o primeiro de lista branca com uma entrada que casa,
     * se essa entrada tiver estoque; senão {@code null} (sem limite). O estoque é
     * {@code filtro.itemStock(stack)} e a contagem segue o {@code matchComponents} dele.
     */
    public @Nullable Filter itemStockFilter(ItemStack stack) {
        if (!usesItemStock) {
            return null;
        }
        for (Filter filter : filters) {
            if (filter.listMode() == Filter.ListMode.WHITELIST && filter.matchesItem(stack)) {
                return filter.itemStock(stack) > 0 ? filter : null;
            }
        }
        return null;
    }

    /** Como {@link #itemStockFilter}, para fluidos. */
    public @Nullable Filter fluidStockFilter(FluidStack stack) {
        if (!usesFluidStock) {
            return null;
        }
        for (Filter filter : filters) {
            if (filter.listMode() == Filter.ListMode.WHITELIST && filter.matchesFluid(stack)) {
                return filter.fluidStock(stack) > 0 ? filter : null;
            }
        }
        return null;
    }

    /** Como {@link #itemStockFilter}, para químicos (pelo id). */
    public @Nullable Filter chemicalStockFilter(ResourceLocation chemical) {
        if (!usesChemicalStock) {
            return null;
        }
        for (Filter filter : filters) {
            if (filter.listMode() == Filter.ListMode.WHITELIST && filter.matchesChemical(chemical)) {
                return filter.chemicalStock(chemical) > 0 ? filter : null;
            }
        }
        return null;
    }

    /** Estoque do item pela regra de {@link #itemStockFilter}, ou 0 sem limite. */
    public long itemStock(ItemStack stack) {
        Filter filter = itemStockFilter(stack);
        return filter == null ? 0 : filter.itemStock(stack);
    }

    /** Estoque do fluido pela regra de {@link #fluidStockFilter}, ou 0 sem limite. */
    public long fluidStock(FluidStack stack) {
        Filter filter = fluidStockFilter(stack);
        return filter == null ? 0 : filter.fluidStock(stack);
    }

    /** Algum filtro com estoque exige componentes iguais (a contagem da origem precisa das duas chaves). */
    public boolean anyStockMatchesComponents() {
        for (Filter filter : filters) {
            if (filter.matchComponents() && filter.usesItemStock()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "FilterSet" + List.of(filters);
    }
}
