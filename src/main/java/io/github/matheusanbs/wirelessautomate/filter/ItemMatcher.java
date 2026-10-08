package io.github.matheusanbs.wirelessautomate.filter;

import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import org.jetbrains.annotations.Nullable;

/**
 * {@link Filter} compilado para itens. Sem {@code matchComponents}, as entradas exatas valem pelo
 * {@link Item} e tudo cabe no mapa por item (com as tags expandidas). Com {@code matchComponents}, as
 * exatas ficam num mapa por item + componentes ({@link ItemStackLinkedSet#TYPE_AND_TAG}), consultado
 * a cada pergunta só se houver exatas (hash dos componentes, sem alocar); tags e mods continuam por item.
 *
 * <p>Regras por propriedade ({@link ItemRule}) não cabem num mapa: ficam numa lista na ordem do
 * filtro e só são perguntadas as que vêm antes da melhor resposta dos mapas, até a primeira que casa.
 * O custo cresce com o número de regras (poucas, na prática), não com o de itens ou tags.
 */
final class ItemMatcher extends CompiledMatcher<Item> {
    private final @Nullable Object2IntOpenCustomHashMap<ItemStack> exactWithComponents;
    private final List<Predicate<ItemStack>> rules = new ArrayList<>(0);
    private final IntList ruleIndexes = new IntArrayList(0);

    ItemMatcher(List<FilterEntry> entries, boolean matchComponents) {
        super(entries);
        if (matchComponents) {
            exactWithComponents = new Object2IntOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
            exactWithComponents.defaultReturnValue(NONE);
        } else {
            exactWithComponents = null;
        }
        compile(BuiltInRegistries.ITEM, !matchComponents);
    }

    /** Índice da primeira entrada que casa com a pilha, ou {@link #NONE}. */
    int index(ItemStack stack) {
        int index = keyIndex(stack.getItem());
        if (exactWithComponents != null && !exactWithComponents.isEmpty()) {
            index = first(index, exactWithComponents.getInt(stack));
        }
        for (int r = 0, n = rules.size(); r < n; r++) {
            int ruleIndex = ruleIndexes.getInt(r);
            if (index >= 0 && ruleIndex > index) {
                break;
            }
            if (rules.get(r).test(stack)) {
                return ruleIndex;
            }
        }
        return index;
    }

    @Override
    boolean addRule(FilterEntry.RuleEntry rule, int index) {
        rules.add(rule.rule().compile());
        ruleIndexes.add(index);
        return true;
    }

    @Override
    @Nullable Item exactKey(FilterEntry entry) {
        return entry instanceof FilterEntry.ItemEntry item ? item.stack().getItem() : null;
    }

    @Override
    void addExact(FilterEntry entry, int index) {
        exactWithComponents.putIfAbsent(((FilterEntry.ItemEntry) entry).stack(), index);
    }

    @Override
    @SuppressWarnings("deprecation")
    String namespaceOf(Item item) {
        // O id pelo holder do registro: sem busca no mapa do registro.
        return item.builtInRegistryHolder().key().location().getNamespace();
    }
}
