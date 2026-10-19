package io.github.matheusanbs.wirelessautomate.client.storage;

import io.github.matheusanbs.wirelessautomate.filter.FilterTags;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.Hash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Os tipos mostrados na grade, filtrados pela busca e ordenados, com a quantidade de cada um. A lista
 * vem da {@link StorageListView} e só é refeita quando ela, a busca ou a ordem mudam.
 */
final class ShownList {
    private final StorageListMenu<?> menu;
    /** Nome em minúsculas por tipo (as chaves da visão são os mesmos objetos até saírem). */
    private final Map<Object, String> names = new IdentityHashMap<>();
    private List<Object> shown = new ArrayList<>();
    /** Quantidade de cada tipo mostrado, tirada junto com a lista (por identidade, como os nomes). */
    private final Map<Object, Long> shownCounts = new IdentityHashMap<>();
    /**
     * Texto procurado pelo {@code #} em cada tipo (tags e tooltip), montado na primeira busca por {@code #} que
     * passa pelo tipo e guardado até ele sair: digitar só filtra o que já está aqui. Some inteiro, junto dos
     * nomes, quando o idioma ou as tags mudam ({@link #checkTexts}). O tooltip é pedido sempre sem as dicas
     * avançadas, então o F3+H não muda o texto.
     */
    private final Map<Object, String> searchTexts = new IdentityHashMap<>();
    /** Mod de cada tipo, para a ordem por mod e a busca {@code @mod}, sem ir ao registro a cada sincronização. */
    private final Map<Object, String> mods = new IdentityHashMap<>();
    /** Os tipos da última lista refeita por inteiro: se a visão nova tem os mesmos, a lista não se refaz. */
    private final Set<Object> known = Collections.newSetFromMap(new IdentityHashMap<>());
    private String textsLanguage = "";
    private int textsTags = -1;
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

    /**
     * Refaz a lista mostrada se a visão, a busca ou a ordem mudaram; diz se refez. Com a busca, a ordem e os
     * tipos iguais (só as quantidades mudaram), a lista fica como está e só a ordem por quantidade se refaz.
     */
    boolean rebuild(String rawSearch, ListSort sort) {
        String search = rawSearch.trim().toLowerCase(Locale.ROOT);
        boolean sameQuery = search.equals(shownSearch) && sort == shownSort;
        // Shift apertado trava a ordem; digitar maiúsculas na busca muda a busca e refaz tudo.
        boolean frozen = (Screen.hasShiftDown() || StorageListScreen.e2eShift) && sameQuery && shownVersion >= 0;
        if (view().version() == shownVersion && sameQuery && frozen == shownFrozen) {
            return false;
        }
        boolean textsChanged = checkTexts();
        boolean wasFrozen = shownFrozen;
        shownVersion = view().version();
        shownSearch = search;
        shownSort = sort;
        shownFrozen = frozen;
        // Foto da quantidade de cada tipo antes de ordenar: o comparador não pode ver um valor mudar no
        // meio da ordenação ("Comparison method violates its general contract!"). O nome de alguns itens
        // de outros mods mexe nos componentes da pilha, o que muda o hash dela: por isso tudo aqui é por
        // identidade, e a busca da quantidade pelo hash devolveria 0 no meio do caminho.
        List<StorageListView.Entry<Object>> entries = view().entries();
        Map<Object, Long> counts = new IdentityHashMap<>();
        for (StorageListView.Entry<Object> entry : entries) {
            counts.put(entry.key(), entry.count());
        }
        StorageSearch query = StorageSearch.parse(search);
        if (frozen) {
            refreshFrozen(entries, counts, query);
            return true;
        }
        shownCounts.clear();
        shownCounts.putAll(counts);
        Comparator<Object> byName = Comparator.comparing(this::name);
        Comparator<Object> order = switch (sort) {
            case COUNT -> Comparator.<Object>comparingLong(this::count).reversed().thenComparing(byName);
            case NAME -> byName;
            case MOD -> Comparator.comparing(this::mod).thenComparing(byName);
        };
        if (sameQuery && !textsChanged && !wasFrozen && sameKeys(counts)) {
            if (sort == ListSort.COUNT) {
                shown.sort(order);
            }
            return true;
        }
        known.clear();
        known.addAll(counts.keySet());
        names.keySet().retainAll(known);
        mods.keySet().retainAll(known);
        searchTexts.keySet().retainAll(known);
        List<Object> kept = new ArrayList<>(entries.size());
        for (StorageListView.Entry<Object> entry : entries) {
            if (matches(entry.key(), query)) {
                kept.add(entry.key());
            }
        }
        kept.sort(order);
        shown = kept;
        return true;
    }

    /** Os tipos da visão são os mesmos (por identidade) da última lista refeita por inteiro. */
    private boolean sameKeys(Map<Object, Long> counts) {
        if (counts.size() != known.size()) {
            return false;
        }
        for (Object key : counts.keySet()) {
            if (!known.contains(key)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Esvazia os nomes e os textos da busca guardados se o idioma ou as tags mudaram desde que foram montados;
     * diz se esvaziou (a lista então se refaz por inteiro).
     */
    private boolean checkTexts() {
        Minecraft minecraft = Minecraft.getInstance();
        String language = minecraft == null ? "" : minecraft.getLanguageManager().getSelected();
        int tags = FilterTags.generation();
        if (language.equals(textsLanguage) && tags == textsTags) {
            return false;
        }
        boolean first = textsTags < 0;
        textsLanguage = language;
        textsTags = tags;
        names.clear();
        searchTexts.clear();
        return !first;
    }

    /**
     * Com a ordem travada: cada tipo fica onde estava, com a quantidade nova (0 se acabou); um tipo
     * que voltou toma o lugar da célula apagada dele, e um tipo novo entra no fim.
     */
    @SuppressWarnings("unchecked")
    private void refreshFrozen(List<StorageListView.Entry<Object>> entries, Map<Object, Long> counts,
            StorageSearch query) {
        Hash.Strategy<Object> same = (Hash.Strategy<Object>) menu.kind().strategy;
        List<Object> list = new ArrayList<>(shown);
        Set<Object> placed = Collections.newSetFromMap(new IdentityHashMap<>());
        placed.addAll(list);
        for (StorageListView.Entry<Object> entry : entries) {
            Object key = entry.key();
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
            if (!mod(key).contains(mod)) {
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

    private String mod(Object key) {
        return mods.computeIfAbsent(key, StorageKeys::namespace);
    }

    /** Nome em minúsculas, guardado por tipo (o nome de um item com componentes pode ser caro). */
    private String name(Object key) {
        return names.computeIfAbsent(key, k -> StorageKeys.displayName(k).getString().toLowerCase(Locale.ROOT));
    }
}
