package io.github.matheusanbs.wirelessautomate.client.storage;

import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import it.unimi.dsi.fastutil.Hash;
import net.minecraft.client.gui.screens.Screen;

/**
 * Os tipos mostrados na grade, filtrados pela busca e ordenados, com a quantidade de cada um. A lista
 * vem da {@link StorageListView} e só é refeita quando ela, a busca ou a ordem mudam.
 */
final class ShownList {
    private final StorageListMenu<?> menu;
    /** Nome em minúsculas por tipo (as chaves da visão são os mesmos objetos até saírem). */
    private final Map<Object, String> names = new IdentityHashMap<>();
    private List<Object> shown = List.of();
    /** Quantidade de cada tipo mostrado, tirada junto com a lista (por identidade, como os nomes). */
    private final Map<Object, Long> shownCounts = new IdentityHashMap<>();
    /** Texto procurado pelo {@code #} em cada tipo (tags e tooltip), refeito quando a busca muda. */
    private final Map<Object, String> searchTexts = new IdentityHashMap<>();
    private int shownVersion = -1;
    private String shownSearch = "";
    private ListSort shownSort;
    /**
     * Ordem travada enquanto o Shift está apertado (como no AE2): as quantidades mudam, mas os tipos
     * não trocam de lugar no meio da coleta; um tipo que acaba fica como célula apagada até soltar.
     */
    private boolean shownFrozen;

    ShownList(StorageListMenu<?> menu, ListSort sort) {
        this.menu = menu;
        this.shownSort = sort;
    }

    @SuppressWarnings("unchecked")
    private StorageListView<Object> view() {
        return (StorageListView<Object>) menu.view();
    }

    List<Object> shown() {
        return shown;
    }

    int size() {
        return shown.size();
    }

    Object get(int index) {
        return shown.get(index);
    }

    boolean isEmpty() {
        return shown.isEmpty();
    }

    /** Refaz a lista mostrada se a visão, a busca ou a ordem mudaram; diz se refez. */
    boolean rebuild(String rawSearch, ListSort sort) {
        String search = rawSearch.trim().toLowerCase(Locale.ROOT);
        boolean sameQuery = search.equals(shownSearch) && sort == shownSort;
        // Shift apertado trava a ordem; digitar maiúsculas na busca muda a busca e refaz tudo.
        boolean frozen = (Screen.hasShiftDown() || StorageListScreen.e2eShift) && sameQuery && shownVersion >= 0;
        if (view().version() == shownVersion && sameQuery && frozen == shownFrozen) {
            return false;
        }
        shownVersion = view().version();
        if (!search.equals(shownSearch)) {
            searchTexts.clear();
        }
        shownSearch = search;
        shownSort = sort;
        shownFrozen = frozen;
        // Foto da quantidade e do nome de cada tipo antes de ordenar: o comparador não pode ver um
        // valor mudar no meio da ordenação ("Comparison method violates its general contract!"),
        // e o nome de alguns itens de outros mods mexe nos componentes da pilha, o que muda o hash
        // dela e faria a busca da quantidade devolver 0 no meio do caminho.
        List<StorageListView.Entry<Object>> entries = view().entries();
        Map<Object, Long> counts = new IdentityHashMap<>();
        List<Object> keys = new ArrayList<>(entries.size());
        for (StorageListView.Entry<Object> entry : entries) {
            keys.add(entry.key());
            counts.put(entry.key(), entry.count());
        }
        StorageSearch query = StorageSearch.parse(search);
        if (frozen) {
            refreshFrozen(keys, counts, query);
            return true;
        }
        shownCounts.clear();
        shownCounts.putAll(counts);
        names.keySet().retainAll(counts.keySet());
        searchTexts.keySet().retainAll(counts.keySet());
        List<Object> kept = new ArrayList<>(keys.size());
        for (Object key : keys) {
            if (matches(key, query)) {
                kept.add(key);
            }
        }
        Map<Object, String> mods = new IdentityHashMap<>();
        for (Object key : kept) {
            mods.put(key, StorageKeys.namespace(key));
        }
        Comparator<Object> byName = Comparator.comparing(this::name);
        Comparator<Object> order = switch (sort) {
            case COUNT -> Comparator.<Object>comparingLong(this::count).reversed().thenComparing(byName);
            case NAME -> byName;
            case MOD -> Comparator.<Object, String>comparing(mods::get).thenComparing(byName);
        };
        kept.sort(order);
        shown = kept;
        return true;
    }

    /**
     * Com a ordem travada: cada tipo fica onde estava, com a quantidade nova (0 se acabou); um tipo
     * que voltou toma o lugar da célula apagada dele, e um tipo novo entra no fim.
     */
    @SuppressWarnings("unchecked")
    private void refreshFrozen(List<Object> keys, Map<Object, Long> counts, StorageSearch query) {
        Hash.Strategy<Object> same = (Hash.Strategy<Object>) menu.kind().strategy;
        List<Object> list = new ArrayList<>(shown);
        java.util.Set<Object> placed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        placed.addAll(list);
        for (Object key : keys) {
            if (placed.contains(key)) {
                continue;
            }
            int ghost = -1;
            for (int i = 0; i < list.size(); i++) {
                if (!counts.containsKey(list.get(i)) && same.equals(list.get(i), key)) {
                    ghost = i;
                    break;
                }
            }
            if (ghost >= 0) {
                list.set(ghost, key);
            } else if (matches(key, query)) {
                list.add(key);
            }
            placed.add(key);
        }
        shownCounts.clear();
        for (Object key : list) {
            shownCounts.put(key, counts.getOrDefault(key, 0L));
        }
        shown = list;
    }

    /** Quantidade do tipo na foto da última {@link #rebuild}. */
    long count(Object key) {
        Long count = shownCounts.get(key);
        return count == null ? 0 : count;
    }

    /**
     * Busca: cada palavra precisa aparecer no nome; {@code @texto} procura no id do mod e
     * {@code #texto} nas tags e no tooltip (o {@code #} vale até o próximo {@code @} ou {@code #},
     * então {@code #not yet eaten} procura a frase inteira).
     */
    private boolean matches(Object key, StorageSearch query) {
        if (query.isEmpty()) {
            return true;
        }
        String name = name(key);
        for (String word : query.words()) {
            if (!name.contains(word)) {
                return false;
            }
        }
        for (String mod : query.mods()) {
            if (!StorageKeys.namespace(key).contains(mod)) {
                return false;
            }
        }
        if (!query.tags().isEmpty()) {
            String text = searchTexts.computeIfAbsent(key, StorageKeys::searchText);
            for (String tag : query.tags()) {
                if (!text.contains(tag)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Nome em minúsculas, guardado por tipo (o nome de um item com componentes pode ser caro). */
    private String name(Object key) {
        return names.computeIfAbsent(key, k -> StorageKeys.displayName(k).getString().toLowerCase(Locale.ROOT));
    }
}
