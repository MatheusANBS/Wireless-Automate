package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Conteúdo de um armazenamento do mod por tipo: chaves ({@code K}: item, fluido ou químico) com
 * quantidade {@code long}, numa lista na ordem de chegada mais um índice por chave. Guardar, tirar e
 * contar um tipo são O(1), e contar um item ou fluido com quaisquer componentes também
 * ({@link #countGroup}). O tipo que acaba fica na lista com 0 até o fim do tick, para os slots das
 * visões não andarem no meio de uma chamada ({@link #slots}), e o {@link #compact} fecha os buracos numa
 * passada. A capacidade conta o total de todos os tipos ({@link StorageMath}); {@code <= 0} é sem
 * limite, e ela é lida a cada inserção (muda com o tier e com a config, sem copiar nada).
 *
 * <p>Cada mudança real chama {@code onChange} (o block entity marca o chunk e põe o aviso aos vizinhos
 * na fila do tick, o que acorda os roteadores presos nele) e sobe a {@link #version()}, que a tela
 * aberta compara.
 * A {@link Admission} decide quanto de cada tipo pode entrar (o filtro de entrada), em qualquer
 * caminho: roteador, funil, outro mod ou a tela.
 *
 * <p>As subclasses dizem como comparar, copiar e salvar a chave.
 */
public abstract class KeyedStorage<K> {
    private final class Entry {
        final K key;
        long count;
        /**
         * A chave codificada, feita no primeiro salvamento e reusada nos seguintes (a chave nunca muda).
         * Compartilhada entre os salvamentos: quem lê o NBT salvo não o altera.
         */
        @Nullable Tag tag;
        /** A pilha com a quantidade que {@link #view} devolveu da última vez; trocada (nunca alterada) quando muda. */
        @Nullable K view;

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
    /** Quantas entradas da lista estão com 0, à espera do {@link #compact}. */
    private int holes;
    /** A soma por grupo ({@link #groupOf}), criada no primeiro tipo com grupo. */
    private @Nullable Reference2LongOpenHashMap<Object> groupTotals;
    private int version;
    /** Os contadores de entra e sai, só enquanto uma Tela de Sinal vigia; nulo no resto do tempo. */
    private @Nullable StorageWatch watch;
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

    protected abstract Tag saveKey(K key, HolderLookup.Provider registries);

    /** A chave lida, ou {@code null} se não existe mais (mod removido). */
    protected abstract @Nullable K loadKey(Tag tag, HolderLookup.Provider registries);

    public void setAdmission(Admission<K> admission) {
        this.admission = admission;
    }

    /** Os contadores de entra e sai, ou {@code null} sem vigia. */
    public @Nullable StorageWatch watch() {
        return watch;
    }

    /** Liga a vigia (a mesma, se já ligada). */
    public StorageWatch startWatch() {
        if (watch == null) {
            watch = new StorageWatch();
        }
        return watch;
    }

    public void stopWatch() {
        watch = null;
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
        return entries.size() == holes;
    }

    /** Os tipos guardados, sem os slots esgotados. */
    public int types() {
        return entries.size() - holes;
    }

    /**
     * Os slots das visões de outros mods ({@code IItemHandler}, {@code IFluidHandler}, químicos): os tipos e,
     * até o fim do tick, os que acabaram, com 0 no mesmo lugar. O storage bus do AE2 tira de um slot em laço
     * até ele voltar vazio; se o tipo seguinte andasse para o lugar do esgotado, o laço o levaria contado
     * como o pedido (o pedido duplicado e o outro tipo apagado). O {@link #compact} fecha a lista no fim do
     * tick, quando nenhuma chamada está no meio.
     */
    public int slots() {
        return entries.size();
    }

    /** A chave do slot; a de um esgotado continua valendo. */
    public K key(int slot) {
        return entries.get(slot).key;
    }

    /** A quantidade do slot; 0 num esgotado. */
    public long count(int slot) {
        return entries.get(slot).count;
    }

    /** O primeiro slot com alguma quantidade, ou -1 sem nenhum. */
    public int firstSlot() {
        for (int i = 0, n = entries.size(); i < n; i++) {
            if (entries.get(i).count > 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A chave do tipo na posição com a quantidade dele (até {@link Integer#MAX_VALUE}), para as visões de
     * slot ({@code getStackInSlot}, {@code getFluidInTank}), que quem chama não pode alterar; um slot
     * esgotado dá a quantidade 0, que quem chama confere antes. A mesma pilha
     * volta enquanto a quantidade não muda; quando muda, uma nova toma o lugar e a antiga, que alguém pode
     * estar guardando para comparar, fica como estava. Sem isso, seria uma pilha nova por chamada (um funil
     * olha todos os slots a cada transferência).
     */
    public K view(int index) {
        Entry entry = entries.get(index);
        int amount = (int) Math.min(entry.count, Integer.MAX_VALUE);
        K view = entry.view;
        if (view == null || amountOf(view) != amount) {
            view = withAmount(entry.key, amount);
            entry.view = view;
        }
        return view;
    }

    /** Uma cópia da chave com a quantidade, para a {@link #view}; só nos que têm visão de slot. */
    protected K withAmount(K key, int amount) {
        throw new UnsupportedOperationException(what + " sem visão de slot");
    }

    /** A quantidade de uma pilha da {@link #view}. */
    protected int amountOf(K view) {
        throw new UnsupportedOperationException(what + " sem visão de slot");
    }

    public long count(K key) {
        Entry entry = index.get(key);
        return entry == null ? 0 : entry.count;
    }

    /**
     * O grupo da chave para {@link #countGroup}: o item ou o fluido, sem os componentes. {@code null} (o
     * padrão) é sem grupo: a chave já é o grupo (o químico, que não tem componentes). Comparado por
     * identidade.
     */
    protected @Nullable Object groupOf(K key) {
        return null;
    }

    /**
     * Quanto há do grupo da chave, com quaisquer componentes, como o estoque sem componentes. O(1): a
     * soma por grupo é mantida a cada inserção, extração e carga.
     */
    public long countGroup(K key) {
        Object group = groupOf(key);
        if (group == null) {
            return count(key);
        }
        return groupTotals == null ? 0 : groupTotals.getLong(group);
    }

    /** Quanto há do grupo (o item sem os componentes), nos armazenamentos com grupo. O(1). */
    public long countOfGroup(Object group) {
        return groupTotals == null ? 0 : groupTotals.getLong(group);
    }

    private void addToGroup(K key, long amount) {
        Object group = groupOf(key);
        if (group == null) {
            return;
        }
        if (groupTotals == null) {
            groupTotals = new Reference2LongOpenHashMap<>();
        }
        groupTotals.put(group, StorageMath.add(groupTotals.getLong(group), amount));
    }

    private void takeFromGroup(K key, long amount) {
        Object group = groupOf(key);
        if (group == null || groupTotals == null) {
            return;
        }
        long left = groupTotals.getLong(group) - amount;
        if (left > 0) {
            groupTotals.put(group, left);
        } else {
            groupTotals.removeLong(group);
        }
    }

    /** Guarda até {@code amount} de {@code key}; devolve quanto coube (filtro e capacidade). */
    public long insert(K key, long amount, boolean simulate) {
        return put(key, amount, simulate, true);
    }

    /**
     * Movimento interno (a conversão da Compactação): guarda só até a capacidade, sem o filtro de entrada, sem
     * contar no vigia e sem {@link #inserted}. Contado no vigia, a Tela de Sinal mostraria vazão de itens que nunca
     * entraram nem saíram.
     */
    public long insertInternal(K key, long amount) {
        return put(key, amount, false, false);
    }

    /** O fim de um {@link #insert} de fora que guardou algo; recebe a chave guardada (não alterar). */
    protected void inserted(K key) {
    }

    private long put(K key, long amount, boolean simulate, boolean external) {
        if (isEmptyKey(key)) {
            return 0;
        }
        long accepted = StorageMath.accept(total, capacity.getAsLong(), external ? admission.admit(key, amount) : amount);
        if (accepted <= 0 || simulate) {
            return accepted;
        }
        Entry entry = index.get(key);
        if (entry == null) {
            entry = new Entry(normalize(key));
            entries.add(entry);
            index.put(entry.key, entry);
        } else if (entry.count <= 0) {
            holes--;
        }
        entry.count = StorageMath.add(entry.count, accepted);
        total = StorageMath.add(total, accepted);
        addToGroup(entry.key, accepted);
        if (external && watch != null) {
            watch.inserted(groupOf(entry.key), accepted);
        }
        changed();
        if (external) {
            inserted(entry.key);
        }
        return accepted;
    }

    /** Tira até {@code amount} de {@code key}; devolve quanto saiu. */
    public long extract(K key, long amount, boolean simulate) {
        return take(key, amount, simulate, true);
    }

    /** Movimento interno, como o {@link #insertInternal}: tira sem contar no vigia. */
    public long extractInternal(K key, long amount) {
        return take(key, amount, false, false);
    }

    private long take(K key, long amount, boolean simulate, boolean external) {
        if (amount <= 0) {
            return 0;
        }
        Entry entry = index.get(key);
        if (entry == null) {
            return 0;
        }
        long taken = Math.min(amount, entry.count);
        if (taken <= 0 || simulate) {
            return taken;
        }
        entry.count -= taken;
        total -= taken;
        takeFromGroup(entry.key, taken);
        if (external && watch != null) {
            watch.extracted(groupOf(entry.key), taken);
        }
        if (entry.count <= 0) {
            // Fica no lugar até o compact, para os slots não andarem (ver slots()).
            holes++;
        }
        changed();
        return taken;
    }

    /**
     * Tira da lista os slots esgotados, numa passada; chamado no fim do tick pelo {@link StorageNeighbors}
     * (cada esgotamento passa pelo {@code onChange}, que põe o armazenamento na fila). O conteúdo não muda,
     * só os slots andam, por isso a versão fica igual.
     */
    public void compact() {
        if (holes == 0) {
            return;
        }
        int kept = 0;
        for (int i = 0, n = entries.size(); i < n; i++) {
            Entry entry = entries.get(i);
            if (entry.count > 0) {
                entries.set(kept++, entry);
            } else {
                index.remove(entry.key);
            }
        }
        entries.subList(kept, entries.size()).clear();
        holes = 0;
    }

    private void changed() {
        version++;
        onChange.run();
    }

    /** Esvazia sem avisar o block entity (para carregar ou mover o conteúdo); a tela vê pela versão. */
    public void clear() {
        entries.clear();
        index.clear();
        if (groupTotals != null) {
            groupTotals.clear();
        }
        total = 0;
        holes = 0;
        version++;
    }

    /**
     * Lista de {@code {key, count}}. A chave de cada tipo é codificada uma vez só, no primeiro salvamento
     * dela ({@link Entry#tag}): salvar o chunk de novo não recodifica milhares de chaves.
     */
    public ListTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            if (entry.count <= 0) {
                continue;
            }
            if (entry.tag == null) {
                entry.tag = saveKey(entry.key, registries);
            }
            CompoundTag tag = new CompoundTag();
            tag.put("key", entry.tag);
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
    public void load(ListTag list, HolderLookup.Provider registries) {
        clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            long count = tag.getLong("count");
            Tag keyTag = tag.contains("key") ? tag.get("key") : tag.get("item");
            if (count <= 0 || keyTag == null) {
                continue;
            }
            K key = loadKey(keyTag, registries);
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
            addToGroup(entry.key, count);
        }
    }
}
