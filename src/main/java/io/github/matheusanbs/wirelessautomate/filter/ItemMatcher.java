package io.github.matheusanbs.wirelessautomate.filter;

import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import org.jetbrains.annotations.Nullable;

/**
 * {@link Filter} compilado para itens. Sem {@code matchComponents}, as entradas exatas valem pelo
 * {@link Item} e tudo cabe no cache por item. Com {@code matchComponents}, as exatas ficam num mapa
 * por item + componentes ({@link ItemStackLinkedSet#TYPE_AND_TAG}), consultado a cada pergunta
 * (hash dos componentes, sem alocar); tags e mods continuam no cache por item.
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
        compile(Registries.ITEM, !matchComponents);
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
    Stream<TagKey<Item>> tagsOf(Item item) {
        return item.builtInRegistryHolder().tags();
    }

    @Override
    String namespaceOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getNamespace();
    }
}
