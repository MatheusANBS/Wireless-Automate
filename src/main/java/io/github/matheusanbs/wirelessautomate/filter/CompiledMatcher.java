package io.github.matheusanbs.wirelessautomate.filter;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * Um {@link Filter} compilado para um tipo de recurso ({@code K} = {@code Item} ou {@code Fluid}).
 * A resposta é o índice da primeira entrada que casa (−1 = nenhuma): dele saem o "passa?" e o estoque.
 *
 * <p>Entradas exatas por chave e tags ficam num mapa só {@code chave → primeiro índice}: as tags são
 * expandidas na compilação (cada chave da tag entra no mapa com o índice da entrada), então uma
 * consulta é uma busca de hash, com 9 ou com 4.096 entradas, sem alocar e sem cache que esvazie.
 * Mods ficam num mapa {@code namespace → índice}, consultado pelo id da chave (sem varrer o registro).
 *
 * <p>Recarga de tags ({@link FilterTags}): o mapa expandido guarda a geração das tags em que foi
 * montado e é refeito na primeira consulta depois de uma recarga. Ele é imutável depois de pronto e
 * trocado por inteiro (campo {@code volatile}), então qualquer thread pode consultar.
 */
abstract class CompiledMatcher<K> {
    static final int NONE = -1;

    final List<FilterEntry> entries;
    /** Alguma entrada que vale para este tipo tem estoque. */
    boolean usesStock;
    private final Reference2IntOpenHashMap<K> byKey = new Reference2IntOpenHashMap<>();
    /** Entradas de tag, na ordem: a tag e o índice da entrada. */
    private final List<TagKey<K>> tagKeys = new ArrayList<>(0);
    private final List<Integer> tagIndexes = new ArrayList<>(0);
    private final Object2IntOpenHashMap<String> mods = new Object2IntOpenHashMap<>();
    private Registry<K> registry;
    /** Exatas mais tags expandidas, da geração {@link Expanded#generation}; sem tags, é o {@link #byKey}. */
    private volatile Expanded<K> expanded;

    private record Expanded<K>(int generation, Reference2IntOpenHashMap<K> map) {
    }

    CompiledMatcher(List<FilterEntry> entries) {
        this.entries = entries;
        byKey.defaultReturnValue(NONE);
        mods.defaultReturnValue(NONE);
    }

    /**
     * Monta os mapas; a subclasse chama no fim do construtor, com os campos dela prontos.
     * {@code exactByKey} diz se as entradas exatas entram no mapa por chave (componentes ignorados)
     * ou vão para {@link #addExact} (componentes exigidos).
     */
    final void compile(Registry<K> registry, boolean exactByKey) {
        this.registry = registry;
        ResourceKey<? extends Registry<K>> registryKey = registry.key();
        boolean stock = false;
        for (int i = 0, n = entries.size(); i < n; i++) {
            FilterEntry entry = entries.get(i);
            boolean applies = true;
            switch (entry) {
                case FilterEntry.TagEntry tag -> {
                    tagKeys.add(TagKey.create(registryKey, tag.tag()));
                    tagIndexes.add(i);
                }
                case FilterEntry.ModEntry mod -> mods.putIfAbsent(mod.modId(), i);
                case FilterEntry.RuleEntry rule -> applies = addRule(rule, i);
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
        expanded = new Expanded<>(tagKeys.isEmpty() ? Integer.MIN_VALUE : FilterTags.generation(), expand());
    }

    /** Exatas mais o conteúdo atual das tags, num mapa novo (o {@link #byKey} mesmo, sem tags). */
    private Reference2IntOpenHashMap<K> expand() {
        if (tagKeys.isEmpty()) {
            return byKey;
        }
        Reference2IntOpenHashMap<K> map = new Reference2IntOpenHashMap<>(byKey);
        map.defaultReturnValue(NONE);
        for (int t = 0, n = tagKeys.size(); t < n; t++) {
            int index = tagIndexes.get(t);
            for (Holder<K> holder : registry.getTagOrEmpty(tagKeys.get(t))) {
                K key = holder.value();
                map.put(key, first(map.getInt(key), index));
            }
        }
        return map;
    }

    /** Guarda uma regra por propriedade; devolve se ela vale para este tipo (só itens). */
    boolean addRule(FilterEntry.RuleEntry rule, int index) {
        return false;
    }

    /** Chave de uma entrada exata deste tipo, ou {@code null} se a entrada for de outro tipo. */
    abstract K exactKey(FilterEntry entry);

    /** Guarda uma entrada exata que exige componentes iguais. */
    abstract void addExact(FilterEntry entry, int index);

    abstract String namespaceOf(K key);

    /** Índice da primeira entrada que casa com a chave, sem as exatas com componentes. */
    final int keyIndex(K key) {
        Expanded<K> current = expanded;
        if (!tagKeys.isEmpty() && current.generation != FilterTags.generation()) {
            // Recarga de tags: refaz o mapa (duas threads podem refazer juntas; dá o mesmo mapa).
            int generation = FilterTags.generation();
            current = new Expanded<>(generation, expand());
            expanded = current;
        }
        int best = current.map.getInt(key);
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
