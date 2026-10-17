package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.tablet.TabletSearch.Candidate;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Facet;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickFilter;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A busca da Lista do Tablet: o texto (a consulta sai um pouco depois da última letra), as etiquetas de máquina e
 * de rede, os filtros rápidos e o de tipo, e as sugestões abertas embaixo da caixa. A busca é feita no servidor;
 * as sugestões saem no cliente, das facetas do cabeçalho e dos nomes dos nós recebidos ({@link TabletSearch}).
 */
final class ListSearch {
    /** Tiques entre a última letra e a consulta. */
    private static final int DEBOUNCE = 6;

    /** Uma etiqueta da busca: uma máquina ou uma rede. */
    record Label(@Nullable ResourceLocation machine, @Nullable UUID network) {
    }

    private final TabletList list;
    private EditBox searchBox;

    String text = "";
    private int textTicks = -1;
    final List<Label> labels = new ArrayList<>();
    final Set<QuickFilter> quick = EnumSet.noneOf(QuickFilter.class);
    Optional<ResourceType> typeFilter = Optional.empty();

    final SearchSuggestions suggestions = new SearchSuggestions();
    final SearchSuggestions.Picker picker = new SearchSuggestions.Picker() {
        @Override
        public void machine(ResourceLocation id) {
            addLabel(new Label(id, null));
        }

        @Override
        public void network(UUID id) {
            addLabel(new Label(null, id));
        }

        @Override
        public void name(String name) {
            pickName(name);
        }
    };

    /** A consulta restaurada ao abrir (a do servidor). */
    ListSearch(TabletList list, Query query) {
        this.list = list;
        this.text = query.text();
        query.machines().forEach(id -> labels.add(new Label(id, null)));
        query.networks().forEach(id -> labels.add(new Label(null, id)));
        this.quick.addAll(query.quick());
        this.typeFilter = query.type();
    }

    private TabletSnapshot snapshot() {
        return list.snapshot();
    }

    /** A caixa de busca da tela (um widget dela), refeita a cada {@code init}. */
    void attach(EditBox box) {
        this.searchBox = box;
        box.setValue(text);
        box.setResponder(value -> {
            if (!value.equals(text)) {
                text = value;
                textTicks = DEBOUNCE;
                refreshSuggestions();
                suggestions.reopen();
            }
        });
    }

    @Nullable EditBox box() {
        return searchBox;
    }

    /** Há busca, etiqueta, filtro rápido ou filtro de tipo. */
    boolean filtering() {
        return !text.isBlank() || !labels.isEmpty() || !quick.isEmpty() || typeFilter.isPresent();
    }

    /** A consulta espera a última letra. */
    boolean pending() {
        return textTicks > 0;
    }

    // ------------------------------------------------------------------ consulta

    private Query query() {
        List<ResourceLocation> machines = new ArrayList<>();
        List<UUID> networks = new ArrayList<>();
        for (Label label : labels) {
            if (label.machine() != null) {
                machines.add(label.machine());
            } else {
                networks.add(label.network());
            }
        }
        return Query.of(text.strip()).withTextMachines(textMachines()).withMachines(machines).withNetworks(networks)
                .withQuick(quick).withType(typeFilter);
    }

    private List<Candidate<ResourceLocation>> machineCandidates() {
        List<Candidate<ResourceLocation>> candidates = new ArrayList<>();
        for (Facet<ResourceLocation> facet : snapshot().machineFacets()) {
            candidates.add(new Candidate<>(facet.id(), list.texts.machineName(facet.id()), facet.count()));
        }
        return candidates;
    }

    /** As máquinas cujo nome, no idioma do jogador, contém o texto: o servidor só conhece os ids. */
    private List<ResourceLocation> textMachines() {
        List<ResourceLocation> ids = TabletSearch.matchingIds(text, machineCandidates());
        return ids.size() > TabletSnapshot.MAX_TEXT_MACHINES ? ids.subList(0, TabletSnapshot.MAX_TEXT_MACHINES) : ids;
    }

    void sendQuery() {
        textTicks = -1;
        list.scroll = 0;
        list.actions.query(query());
    }

    /** A cada tique da tela: a consulta sai um pouco depois da última letra. */
    void tick() {
        if (textTicks > 0 && --textTicks == 0) {
            sendQuery();
        }
    }

    void refreshSuggestions() {
        List<Candidate<UUID>> networks = new ArrayList<>();
        for (Facet<UUID> facet : snapshot().networkFacets()) {
            snapshot().network(facet.id()).ifPresent(n -> networks.add(new Candidate<>(n.id(), n.name(), facet.count())));
        }
        Map<String, Integer> names = new LinkedHashMap<>();
        for (NodeView node : snapshot().nodes()) {
            if (!node.name().isEmpty()) {
                names.merge(node.name(), 1, Integer::sum);
            }
        }
        List<Candidate<String>> nameList = new ArrayList<>();
        names.forEach((name, count) -> nameList.add(new Candidate<>(name, name, count)));
        suggestions.update(text, machineCandidates(), networks, nameList);
    }

    /** A sugestão de um nome vira o texto da busca e escolhe o nó, se é um só com esse nome. */
    private void pickName(String name) {
        setText(name);
        NodeView only = null;
        int count = 0;
        for (NodeView node : snapshot().nodes()) {
            if (node.name().equals(name)) {
                only = node;
                count++;
            }
        }
        if (count == 1) {
            list.choose(only.key());
        }
        sendQuery();
    }

    private void addLabel(Label label) {
        long same = labels.stream().filter(l -> (l.machine() != null) == (label.machine() != null)).count();
        if (!labels.contains(label) && same < TabletSnapshot.MAX_LABELS) {
            labels.add(label);
        }
        setText("");
        sendQuery();
    }

    void removeLabel(int index) {
        if (index >= 0 && index < labels.size()) {
            labels.remove(index);
            sendQuery();
        }
    }

    private void setText(String value) {
        text = value;
        if (searchBox != null) {
            searchBox.setValue(value);
        }
        suggestions.close();
    }

    /** Tira a busca, as etiquetas, os filtros rápidos e o de tipo. */
    void clearAll() {
        setText("");
        labels.clear();
        quick.clear();
        typeFilter = Optional.empty();
        sendQuery();
    }

    /** A etiqueta de rede passa a ser só {@code id} (as de máquina ficam); a consulta sai depois, por quem chamou. */
    void onlyNetwork(UUID id) {
        labels.removeIf(label -> label.network() != null);
        labels.add(new Label(null, id));
    }

    void toggleQuick(QuickFilter filter) {
        if (!quick.remove(filter)) {
            quick.add(filter);
        }
        sendQuery();
    }

    void clearType() {
        typeFilter = Optional.empty();
        sendQuery();
    }

    /** Snapshot novo: as sugestões abertas acompanham. */
    void onSnapshot() {
        if (suggestions.shown()) {
            refreshSuggestions();
        }
    }

    void previewText(String value) {
        setText(value);
        refreshSuggestions();
        suggestions.reopen();
    }

    List<Object> labelIds() {
        List<Object> ids = new ArrayList<>();
        labels.forEach(l -> ids.add(l.machine() != null ? l.machine() : l.network()));
        return ids;
    }
}
