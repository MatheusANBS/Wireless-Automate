package io.github.matheusanbs.wirelessautomate.filter;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * Um {@link Filter} compilado para um tipo de recurso ({@code K} = {@code Item} ou {@code Fluid}).
 * A resposta é o índice da primeira entrada que casa (−1 = nenhuma): dele saem o "passa?" e o estoque.
 *
 * <p>Entradas exatas por chave, tags e mods ficam em mapas de hash ({@code alvo → primeiro índice}),
 * então uma consulta custa o mesmo com 9 ou com 4.096 entradas. Tags: em vez de perguntar a cada
 * tag do filtro se a chave está nela, percorre as tags da chave (poucas, em geral menos de 20)
 * contra o mapa; isso aloca um {@code Stream}, mas só na primeira consulta da chave.
 *
 * <p>Cache: tudo o que depende só da chave (exato sem componentes, tags, mods) fica num mapa
 * pequeno {@code chave → índice}, descartado quando as tags recarregam ({@link FilterTags}) ou
 * quando enche. O caso comum (chave já vista) não aloca. O cache não é seguro entre threads: só a
 * thread que compilou o usa (a do servidor, no motor); outra thread calcula sem cache.
 */
abstract class CompiledMatcher<K> {
    static final int NONE = -1;
    private static final int MISSING = Integer.MIN_VALUE;
    private static final int CACHE_LIMIT = 512;

    final List<FilterEntry> entries;
    /** Alguma entrada que vale para este tipo tem estoque. */
    boolean usesStock;
    private final Reference2IntOpenHashMap<K> byKey = new Reference2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<TagKey<K>> tags = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<String> mods = new Object2IntOpenHashMap<>();
    private final Thread owner = Thread.currentThread();
    private final Reference2IntOpenHashMap<K> cache = new Reference2IntOpenHashMap<>();
    private int generation = FilterTags.generation();

    CompiledMatcher(List<FilterEntry> entries) {
        this.entries = entries;
        byKey.defaultReturnValue(NONE);
        tags.defaultReturnValue(NONE);
        mods.defaultReturnValue(NONE);
        cache.defaultReturnValue(MISSING);
    }

    /**
     * Monta os mapas; a subclasse chama no fim do construtor, com os campos dela prontos.
     * {@code exactByKey} diz se as entradas exatas entram no mapa por chave (componentes ignorados)
     * ou vão para {@link #addExact} (componentes exigidos).
     */
    final void compile(ResourceKey<? extends Registry<K>> registry, boolean exactByKey) {
        boolean stock = false;
        for (int i = 0, n = entries.size(); i < n; i++) {
            FilterEntry entry = entries.get(i);
            boolean applies = true;
            switch (entry) {
                case FilterEntry.TagEntry tag -> tags.putIfAbsent(TagKey.create(registry, tag.tag()), i);
                case FilterEntry.ModEntry mod -> mods.putIfAbsent(mod.modId(), i);
                default -> {
                    K key = exactKey(entry);
                    if (key == null) {
                        applies = false;
                    } else if (exactByKey) {
                        byKey.putIfAbsent(key, i);
                    } else {
                        addExact(entry, i);
                    }
                }
            }
            stock |= applies && entry.stock() > 0;
        }
        usesStock = stock;
    }

    /** Chave de uma entrada exata deste tipo, ou {@code null} se a entrada for de outro tipo. */
    abstract K exactKey(FilterEntry entry);

    /** Guarda uma entrada exata que exige componentes iguais. */
    abstract void addExact(FilterEntry entry, int index);

    abstract Stream<TagKey<K>> tagsOf(K key);

    abstract String namespaceOf(K key);

    /** Índice da primeira entrada que casa com a chave, sem as exatas com componentes. Usa o cache. */
    final int keyIndex(K key) {
        if (Thread.currentThread() != owner) {
            return resolve(key);
        }
        int current = FilterTags.generation();
        if (current != generation) {
            cache.clear();
            generation = current;
        }
        int index = cache.getInt(key);
        if (index == MISSING) {
            index = resolve(key);
            if (cache.size() >= CACHE_LIMIT) {
                cache.clear();
            }
            cache.put(key, index);
        }
        return index;
    }

    private int resolve(K key) {
        int best = byKey.getInt(key);
        if (!tags.isEmpty()) {
            int[] tagBest = {NONE};
            tagsOf(key).forEach(tag -> tagBest[0] = first(tagBest[0], tags.getInt(tag)));
            best = first(best, tagBest[0]);
        }
        if (!mods.isEmpty()) {
            best = first(best, mods.getInt(namespaceOf(key)));
        }
        return best;
    }

    /** O menor índice válido dos dois (−1 = nenhum). */
    static int first(int a, int b) {
        if (a < 0) {
            return b;
        }
        return b < 0 ? a : Math.min(a, b);
    }
}
