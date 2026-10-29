package io.github.matheusanbs.wirelessautomate.client.filter;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * A entrada escolhida na lista, guardada pelo alvo (sobrevive a uma visão nova, mesmo que mude de lugar),
 * e a entrada pendente: uma vinda do JEI ou da tela, que fica escolhida e à vista quando aparecer na visão.
 */
final class EntrySelection {
    /** A linha fixa "Todos os outros tipos" do armazenamento, o limite por tipo, no lugar de um índice. */
    static final int GLOBAL = -3;

    private final FilterScreen screen;
    private EntryList list;
    private EntryTab entry;

    private @Nullable FilterEntry selected;
    private int selectedIndex = -1;
    private boolean global;
    private @Nullable FilterEntry pendingReveal;

    EntrySelection(FilterScreen screen) {
        this.screen = screen;
    }

    /** A lista (para rolar até a entrada) e a aba Entrada (o estoque digitado) são montadas depois. */
    void bind(EntryList list, EntryTab entry) {
        this.list = list;
        this.entry = entry;
    }

    @Nullable FilterEntry selected() {
        return selected;
    }

    int selectedIndex() {
        return selectedIndex;
    }

    /** A linha fixa do limite por tipo está escolhida. */
    boolean global() {
        return global;
    }

    void clear() {
        selected = null;
        selectedIndex = -1;
        global = false;
    }

    /** A visão mudou: recupera a seleção pelo alvo. */
    void recover(Filter f) {
        selectedIndex = selected == null ? -1 : f.indexOf(selected);
        selected = selectedIndex < 0 ? null : f.entries().get(selectedIndex);
    }

    /** Escolhe a entrada {@code index}, a linha fixa ({@link #GLOBAL}) ou nada (−1). */
    void select(int index) {
        entry.commitStock();
        List<FilterEntry> entries = screen.filter().entries();
        global = index == GLOBAL;
        selected = index >= 0 && index < entries.size() ? entries.get(index) : null;
        selectedIndex = selected == null ? -1 : index;
        if (entry.isLimitBox(screen.getFocused())) {
            screen.unfocus();
        }
        entry.resetLimitBoxes();
        if (selected != null || global) {
            screen.setTab(FilterScreen.Tab.ENTRY);
        }
    }

    void pendingReveal(FilterEntry pending) {
        pendingReveal = pending;
    }

    /** Seleciona e rola até a entrada pendente, se ela já está no filtro. */
    void revealPending() {
        if (pendingReveal == null) {
            return;
        }
        int index = screen.filter().indexOf(pendingReveal);
        if (index < 0) {
            return;
        }
        pendingReveal = null;
        select(index);
        list.scrollTo(index);
    }
}
