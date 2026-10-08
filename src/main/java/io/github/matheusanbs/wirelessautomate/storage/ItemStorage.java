package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

/**
 * Conteúdo do Baú: tipos (item e componentes) com quantidade {@code long}, numa lista na ordem de
 * chegada mais um índice por chave. Guardar, tirar e contar um tipo são O(1); só tirar o último de
 * um tipo é O(tipos), para a lista não ficar com buracos. A capacidade conta o total de itens de
 * todos os tipos ({@link StorageMath}); {@code <= 0} é sem limite.
 *
 * <p>Cada mudança real chama {@code onChange} (o block entity marca o chunk e avisa os vizinhos,
 * o que acorda os roteadores presos nele) e sobe a {@link #version()}, que a tela aberta compara.
 *
 * <p>A {@link Admission} decide quanto de cada tipo pode entrar (o filtro de entrada do Baú), em
 * qualquer caminho: roteador, funil, outro mod ou a tela.
 */
public final class ItemStorage implements BulkItems {
    private static final class Entry {
        final ItemStack key;
        long count;

        Entry(ItemStack key) {
            this.key = key;
        }
    }

    /** Quanto de {@code amount} da chave pode entrar, antes da capacidade (filtro de entrada). */
    @FunctionalInterface
    public interface Admission {
        Admission ALL = (key, amount) -> amount;

        long admit(ItemStack key, long amount);
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Object2ObjectOpenCustomHashMap<ItemStack, Entry> index =
            new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
    private final Runnable onChange;
    private final LongSupplier capacity;
    private long total;
    private int version;
    private Admission admission = Admission.ALL;

    /** {@code capacity} é lida a cada inserção: muda com o tier e com a config, sem copiar nada. */
    public ItemStorage(Runnable onChange, LongSupplier capacity) {
        this.onChange = onChange;
        this.capacity = capacity;
    }

    public void setAdmission(Admission admission) {
        this.admission = admission;
    }

    /** Sobe a cada mudança do conteúdo. */
    public int version() {
        return version;
    }

    public long total() {
        return total;
    }

    public long capacity() {
        return capacity.getAsLong();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public int types() {
        return entries.size();
    }

    @Override
    public ItemStack key(int index) {
        return entries.get(index).key;
    }

    @Override
    public long count(int index) {
        return entries.get(index).count;
    }

    @Override
    public long count(ItemStack key) {
        Entry entry = index.get(key);
        return entry == null ? 0 : entry.count;
    }

    @Override
    public long countItem(ItemStack key) {
        long sum = 0;
        for (Entry entry : entries) {
            if (entry.key.is(key.getItem())) {
                sum = StorageMath.add(sum, entry.count);
            }
        }
        return sum;
    }

    @Override
    public long insert(ItemStack key, long amount, boolean simulate) {
        if (key.isEmpty()) {
            return 0;
        }
        long accepted = StorageMath.accept(total, capacity.getAsLong(), admission.admit(key, amount));
        if (accepted <= 0 || simulate) {
            return accepted;
        }
        Entry entry = index.get(key);
        if (entry == null) {
            entry = new Entry(key.copyWithCount(1));
            entries.add(entry);
            index.put(entry.key, entry);
        }
        entry.count = StorageMath.add(entry.count, accepted);
        total = StorageMath.add(total, accepted);
        changed();
        return accepted;
    }

    @Override
    public long extract(ItemStack key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        Entry entry = index.get(key);
        if (entry == null) {
            return 0;
        }
        long taken = Math.min(amount, entry.count);
        if (simulate) {
            return taken;
        }
        entry.count -= taken;
        total -= taken;
        if (entry.count <= 0) {
            index.remove(entry.key);
            entries.remove(entry);
        }
        changed();
        return taken;
    }

    private void changed() {
        version++;
        onChange.run();
    }

    /** Esvazia sem avisar o block entity (para carregar ou mover o conteúdo); a tela vê pela versão. */
    public void clear() {
        entries.clear();
        index.clear();
        total = 0;
        version++;
    }

    /** Lista de {@code {item, count}}, com o item salvo como pilha de 1. */
    public ListTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.put("item", entry.key.save(registries));
            tag.putLong("count", entry.count);
            list.add(tag);
        }
        return list;
    }

    /**
     * Troca o conteúdo pelo da lista, sem avisar. Itens que não existem mais (mod removido) são
     * perdidos, como no baú vanilla, e o caso vai para o log.
     */
    public void load(ListTag list, HolderLookup.Provider registries) {
        clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            long count = tag.getLong("count");
            if (count <= 0 || !tag.contains("item")) {
                continue;
            }
            ItemStack key = ItemStack.parse(registries, tag.get("item")).orElse(ItemStack.EMPTY);
            if (key.isEmpty()) {
                WirelessAutomate.LOGGER.warn("Baú: item ilegível descartado ({} unidades): {}", count, tag.get("item"));
                continue;
            }
            Entry entry = index.get(key);
            if (entry == null) {
                entry = new Entry(key.copyWithCount(1));
                entries.add(entry);
                index.put(entry.key, entry);
            }
            entry.count = StorageMath.add(entry.count, count);
            total = StorageMath.add(total, count);
        }
    }
}
