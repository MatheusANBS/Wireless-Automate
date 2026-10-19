package io.github.matheusanbs.wirelessautomate.filter;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

/**
 * Um {@link Filter} compilado para um tipo de recurso ({@code K} = {@code Item} ou {@code Fluid}).
 * A resposta é o índice da primeira entrada que casa (−1 = nenhuma): dele saem o "passa?" e o estoque.
 *
 * <p>Entradas exatas por chave ficam num mapa {@code chave → primeiro índice}, e as tags na expansão
 * compartilhada da lista de tags do filtro ({@link TagExpansions}: {@code chave → ordinal da tag}, que vira o
 * índice da entrada por {@link #tagIndexes}). Uma consulta é no máximo uma busca de hash em cada, com 9 ou com
 * 4.096 entradas, sem alocar. Mods ficam num mapa {@code namespace → índice}, consultado pelo id da chave (sem
 * varrer o registro).
 *
 * <p>Recarga de tags ({@link FilterTags}): a expansão guarda a geração das tags em que foi montada e é trocada na
 * primeira consulta depois de uma recarga. Ela é imutável e trocada por inteiro (campo {@code volatile}), então
 * qualquer thread pode consultar.
 */
abstract class CompiledMatcher<K> {
    static final int NONE = -1;

    final List<FilterEntry> entries;
    /** Alguma entrada que vale para este tipo tem estoque. */
    boolean usesStock;
    private final Reference2IntOpenHashMap<K> byKey = new Reference2IntOpenHashMap<>();
    /** O índice da entrada de cada tag, pelo ordinal dela na lista do filtro. */
    private int[] tagIndexes = NO_TAGS;
    /** As tags do filtro, na ordem; {@code null} sem tags. */
    private TagExpansions.@Nullable TagList tagList;
    private final Object2IntOpenHashMap<String> mods = new Object2IntOpenHashMap<>();
    /** A expansão de {@link #tagList}, da geração {@link TagExpansions.Expansion#generation}; {@code null} sem tags. */
    private volatile TagExpansions.@Nullable Expansion<K> tags;

    private static final int[] NO_TAGS = new int[0];

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
        ResourceKey<? extends Registry<K>> registryKey = registry.key();
        List<TagKey<K>> tagKeys = new ArrayList<>(0);
        IntArrayList indexes = new IntArrayList(0);
        boolean stock = false;
        for (int i = 0, n = entries.size(); i < n; i++) {
            FilterEntry entry = entries.get(i);
            boolean applies = true;
            switch (entry) {
                case FilterEntry.TagEntry tag -> {
                    tagKeys.add(TagKey.create(registryKey, tag.tag()));
                    indexes.add(i);
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
        if (!tagKeys.isEmpty()) {
            tagIndexes = indexes.toIntArray();
            TagExpansions.TagList list = new TagExpansions.TagList(registry, List.copyOf(tagKeys));
            tagList = list;
            tags = TagExpansions.get(list);
        }
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

    /** Para GameTests: a expansão de tags em uso, ou {@code null} sem tags. */
    final @Nullable Object tagExpansionForTests() {
        return tags;
    }

    /** Índice da primeira entrada que casa com a chave, sem as exatas com componentes. */
    final int keyIndex(K key) {
        int best = byKey.isEmpty() ? NONE : byKey.getInt(key);
        TagExpansions.Expansion<K> current = tags;
        if (current != null) {
            if (current.generation() != FilterTags.generation()) {
                // Recarga de tags: pega a expansão nova, montada uma vez para todos os filtros com estas tags.
                current = TagExpansions.get(tagList);
                tags = current;
            }
            int tag = current.map().getInt(key);
            if (tag >= 0) {
                best = first(best, tagIndexes[tag]);
            }
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
