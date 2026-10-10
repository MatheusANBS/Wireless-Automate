package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Conteúdo de um armazenamento do mod por tipo: chaves ({@code K}: item, fluido ou químico) com
 * quantidade {@code long}, numa lista na ordem de chegada mais um índice por chave. Guardar, tirar e
 * contar um tipo são O(1); só tirar o último de um tipo é O(tipos), para a lista não ficar com
 * buracos. A capacidade conta o total de todos os tipos ({@link StorageMath}); {@code <= 0} é sem
 * limite, e ela é lida a cada inserção (muda com o tier e com a config, sem copiar nada).
 *
 * <p>Cada mudança real chama {@code onChange} (o block entity marca o chunk e avisa os vizinhos,
 * o que acorda os roteadores presos nele) e sobe a {@link #version()}, que a tela aberta compara.
 * A {@link Admission} decide quanto de cada tipo pode entrar (o filtro de entrada), em qualquer
 * caminho: roteador, funil, outro mod ou a tela.
 *
 * <p>As subclasses dizem como comparar, copiar e salvar a chave.
 */
public abstract class KeyedStorage<K> {
    private final class Entry {
        final K key;
        long count;

        Entry(K key) {
            this.key = key;
        }
    }

    /** Quanto de {@code amount} da chave pode entrar, antes da capacidade (filtro de entrada). */
    @FunctionalInterface
    public interface Admission<K> {
        long admit(K key, long amount);
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Object2ObjectOpenCustomHashMap<K, Entry> index;
    private final Runnable onChange;
    private final LongSupplier capacity;
    private final String what;
    private long total;
    private int version;
    private Admission<K> admission = (key, amount) -> amount;

    /** @param what nome do bloco para o log ("Baú", "Tanque"...) */
    protected KeyedStorage(Hash.Strategy<? super K> strategy, Runnable onChange, LongSupplier capacity, String what) {
        this.index = new Object2ObjectOpenCustomHashMap<>(strategy);
        this.onChange = onChange;
        this.capacity = capacity;
        this.what = what;
    }

    /** Chave vazia (não guardável): item vazio, fluido vazio. */
    protected abstract boolean isEmptyKey(K key);

    /** Cópia normalizada para guardar como chave (pilha de 1, 1 mB); imutáveis podem voltar a mesma. */
    protected abstract K normalize(K key);

    protected abstract Tag saveKey(K key);

    /** A chave lida, ou {@code null} se não existe mais (mod removido). */
    protected abstract @Nullable K loadKey(Tag tag);

    public void setAdmission(Admission<K> admission) {
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

    public int types() {
        return entries.size();
    }

    public K key(int index) {
        return entries.get(index).key;
    }

    public long count(int index) {
        return entries.get(index).count;
    }

    public long count(K key) {
        Entry entry = index.get(key);
        return entry == null ? 0 : entry.count;
    }

    /** Soma dos tipos que casam com {@code test} (O(tipos)), como o estoque sem componentes. */
    public long countWhere(Predicate<K> test) {
        long sum = 0;
        for (Entry entry : entries) {
            if (test.test(entry.key)) {
                sum = StorageMath.add(sum, entry.count);
            }
        }
        return sum;
    }

    /** Guarda até {@code amount} de {@code key}; devolve quanto coube (filtro e capacidade). */
    public long insert(K key, long amount, boolean simulate) {
        if (isEmptyKey(key)) {
            return 0;
        }
        long accepted = StorageMath.accept(total, capacity.getAsLong(), admission.admit(key, amount));
        if (accepted <= 0 || simulate) {
            return accepted;
        }
        Entry entry = index.get(key);
        if (entry == null) {
            entry = new Entry(normalize(key));
            entries.add(entry);
            index.put(entry.key, entry);
        }
        entry.count = StorageMath.add(entry.count, accepted);
        total = StorageMath.add(total, accepted);
        changed();
        return accepted;
    }

    /** Tira até {@code amount} de {@code key}; devolve quanto saiu. */
    public long extract(K key, long amount, boolean simulate) {
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

    /** Lista de {@code {key, count}}. */
    public ListTag save() {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.put("key", saveKey(entry.key));
            tag.putLong("count", entry.count);
            list.add(tag);
        }
        return list;
    }

    /**
     * Troca o conteúdo pelo da lista, sem avisar. Tipos que não existem mais (mod removido) são
     * perdidos, como no baú vanilla, e o caso vai para o log. Lê também o campo antigo
     * {@code item} do Baú.
     */
    public void load(ListTag list) {
        clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            long count = tag.getLong("count");
            Tag keyTag = tag.contains("key") ? tag.get("key") : tag.get("item");
            if (count <= 0 || keyTag == null) {
                continue;
            }
            K key = loadKey(keyTag);
            if (key == null || isEmptyKey(key)) {
                WirelessAutomate.LOGGER.warn("{}: tipo ilegível descartado ({} unidades): {}", what, count, keyTag);
                continue;
            }
            Entry entry = index.get(key);
            if (entry == null) {
                entry = new Entry(normalize(key));
                entries.add(entry);
                index.put(entry.key, entry);
            }
            entry.count = StorageMath.add(entry.count, count);
            total = StorageMath.add(total, count);
        }
    }
}
