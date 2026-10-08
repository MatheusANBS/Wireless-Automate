package io.github.matheusanbs.wirelessautomate.storage;

import java.util.function.LongSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import org.jetbrains.annotations.Nullable;

/**
 * Conteúdo do Baú: itens (item e componentes) com quantidade {@code long}. As regras estão no
 * {@link KeyedStorage}; aqui só como comparar (item e componentes), copiar (pilha de 1) e salvar
 * a chave. É também a {@link BulkItems} que o roteador usa.
 */
public final class ItemStorage extends KeyedStorage<ItemStack> implements BulkItems {
    public ItemStorage(Runnable onChange, LongSupplier capacity) {
        super(ItemStackLinkedSet.TYPE_AND_TAG, onChange, capacity, "Baú");
    }

    @Override
    protected boolean isEmptyKey(ItemStack key) {
        return key.isEmpty();
    }

    @Override
    protected ItemStack normalize(ItemStack key) {
        return key.copyWithCount(1);
    }

    @Override
    protected Tag saveKey(ItemStack key, HolderLookup.Provider registries) {
        return key.save(registries);
    }

    @Override
    protected @Nullable ItemStack loadKey(Tag tag, HolderLookup.Provider registries) {
        return ItemStack.parse(registries, tag).orElse(null);
    }

    @Override
    public long countItem(ItemStack key) {
        return countWhere(stored -> stored.is(key.getItem()));
    }
}
