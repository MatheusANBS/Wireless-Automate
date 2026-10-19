package io.github.matheusanbs.wirelessautomate.filter;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;

/**
 * As tags dos filtros expandidas uma vez só, compartilhadas: mil faces com o mesmo {@code #c:ingots} usam o mesmo
 * mapa, e depois de uma recarga de tags ele é refeito uma vez, por quem consultar primeiro, e não uma vez por
 * filtro.
 *
 * <p>A chave é a lista de tags do filtro, na ordem ({@link TagList}); o mapa leva cada chave do registro ao ordinal
 * da primeira tag da lista que a contém, e o {@link CompiledMatcher} traduz o ordinal no índice da entrada. Assim
 * dois filtros com as mesmas tags compartilham o mapa mesmo com as entradas em posições diferentes.
 *
 * <p>Cada expansão guarda a geração das tags ({@link FilterTags#generation}) lida antes de ler o conteúdo delas: uma
 * expansão de uma geração velha nunca é devolvida. A recarga também esvazia o cache ({@link #clear}), então ele só
 * guarda as listas usadas desde a última recarga. O mapa é imutável depois de pronto; qualquer thread pode consultar.
 */
final class TagExpansions {
    /** As tags de um filtro, na ordem, num registro. */
    record TagList(Registry<?> registry, List<? extends TagKey<?>> tags) {
    }

    /** Chave do registro → ordinal da primeira tag que a contém ({@link CompiledMatcher#NONE} = nenhuma). */
    record Expansion<K>(int generation, Reference2IntOpenHashMap<K> map) {
    }

    private static final ConcurrentHashMap<TagList, Expansion<?>> CACHE = new ConcurrentHashMap<>();

    private TagExpansions() {
    }

    /** A expansão de {@code list} na geração atual das tags, montada se ainda não existe nela. */
    @SuppressWarnings("unchecked")
    static <K> Expansion<K> get(TagList list) {
        int generation = FilterTags.generation();
        Expansion<?> found = CACHE.get(list);
        if (found == null || found.generation() != generation) {
            // Duas threads podem montar juntas: dá o mesmo mapa, e fica o último.
            found = new Expansion<>(generation, expand((Registry<K>) list.registry(), (List<TagKey<K>>) list.tags()));
            CACHE.put(list, found);
        }
        return (Expansion<K>) found;
    }

    private static <K> Reference2IntOpenHashMap<K> expand(Registry<K> registry, List<TagKey<K>> tags) {
        Reference2IntOpenHashMap<K> map = new Reference2IntOpenHashMap<>();
        map.defaultReturnValue(CompiledMatcher.NONE);
        for (int t = 0, n = tags.size(); t < n; t++) {
            for (Holder<K> holder : registry.getTagOrEmpty(tags.get(t))) {
                // A primeira tag que contém a chave ganha: as tags vêm em ordem.
                map.putIfAbsent(holder.value(), t);
            }
        }
        map.trim();
        return map;
    }

    /** Recarga de tags: esquece todas as expansões. */
    static void clear() {
        CACHE.clear();
    }

    /** Para GameTests: quantas listas de tags estão no cache. */
    static int sizeForTests() {
        return CACHE.size();
    }
}
