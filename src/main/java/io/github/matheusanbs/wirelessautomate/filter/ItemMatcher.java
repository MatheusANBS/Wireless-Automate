package io.github.matheusanbs.wirelessautomate.filter;

import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import java.util.List;
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
 */
final class ItemMatcher extends CompiledMatcher<Item> {
    private final @Nullable Object2IntOpenCustomHashMap<ItemStack> exactWithComponents;

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
        return index;
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
