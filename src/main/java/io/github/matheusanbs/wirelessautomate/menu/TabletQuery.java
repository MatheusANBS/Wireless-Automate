package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickFilter;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A consulta da Lista do Tablet no servidor: a {@link Query} que a tela mandou, já preparada para filtrar
 * (palavras da busca, etiquetas em conjuntos e os nomes das redes em minúsculas), e a regra de quais nós
 * passam nela.
 */
final class TabletQuery {
    private static final Pattern SEARCH_SPLIT = Pattern.compile("[\\s,]+");

    private Query query = Query.DEFAULT;
    private String[] searchWords = new String[0];
    private Set<ResourceLocation> textMachines = Set.of();
    private Set<ResourceLocation> machineLabels = Set.of();
    private Set<UUID> networkLabels = Set.of();
    private @Nullable Map<UUID, String> searchNames;
    private int searchNamesVersion;

    Query query() {
        return query;
    }

    void set(Query query) {
        if (!query.text().equals(this.query.text())) {
            searchWords = searchWords(query.text());
        }
        textMachines = Set.copyOf(query.textMachines());
        machineLabels = Set.copyOf(query.machines());
        networkLabels = Set.copyOf(query.networks());
        this.query = query;
    }

    /** Os nomes das redes para a busca por texto; vazio sem busca. */
    Map<UUID, String> networkNames(NetworkSavedData data) {
        return searchWords.length == 0 ? Map.of() : searchNames(data);
    }

    /** A consulta vale para o nó: filtros rápidos, etiquetas, tipo e texto, nessa ordem. */
    boolean matches(NodeIndex.Entry entry, boolean problem, boolean noNetwork, boolean unloaded,
            Map<UUID, String> networkNames) {
        return matchesQuick(problem, noNetwork, unloaded) && matchesLabels(entry) && matchesType(entry, query.type())
                && matchesText(entry, networkNames);
    }

    /** Os filtros rápidos ligados valem todos (E). */
    private boolean matchesQuick(boolean problem, boolean noNetwork, boolean unloaded) {
        for (QuickFilter filter : query.quick()) {
            boolean on = switch (filter) {
                case PROBLEM -> problem;
                case NO_NETWORK -> noNetwork;
                case UNLOADED -> unloaded;
            };
            if (!on) {
                return false;
            }
        }
        return true;
    }

    /** Etiquetas: a máquina do nó está entre as escolhidas e alguma face dele está numa das redes escolhidas. */
    private boolean matchesLabels(NodeIndex.Entry entry) {
        if (!machineLabels.isEmpty() && !machineLabels.contains(entry.machine())) {
            return false;
        }
        if (networkLabels.isEmpty()) {
            return true;
        }
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                if (networkLabels.contains(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Sem tipo escolhido passa tudo; com tipo, só nós com algum papel (extrai, insere, armazém) nele. */
    private static boolean matchesType(NodeIndex.Entry entry, Optional<ResourceType> type) {
        if (type.isEmpty()) {
            return true;
        }
        int mask = NodeIndex.role(type.get(), NodeIndex.EXTRACT) | NodeIndex.role(type.get(), NodeIndex.INSERT)
                | NodeIndex.role(type.get(), NodeIndex.STORAGE);
        return (entry.roles() & mask) != 0;
    }

    /**
     * Cada palavra do texto precisa aparecer no nome, na máquina, nas coordenadas
     * ({@link NodeIndex.Entry#searchText()}) ou no nome de uma rede do nó; ou o nó é de uma máquina cujo
     * nome o cliente achou no texto ({@code textMachines}).
     */
    private boolean matchesText(NodeIndex.Entry entry, Map<UUID, String> networkNames) {
        if (searchWords.length == 0 || textMachines.contains(entry.machine())) {
            return true;
        }
        String text = entry.searchText();
        for (String word : searchWords) {
            if (!text.contains(word) && !inNetworkName(entry, word, networkNames)) {
                return false;
            }
        }
        return true;
    }

    private static boolean inNetworkName(NodeIndex.Entry entry, String word, Map<UUID, String> networkNames) {
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                String name = networkNames.get(id);
                if (name != null && name.contains(word)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Nomes das redes em minúsculas, refeitos só quando as redes mudam. */
    private Map<UUID, String> searchNames(NetworkSavedData data) {
        if (searchNames == null || searchNamesVersion != data.version()) {
            Map<UUID, String> names = new HashMap<>();
            for (WaNetwork network : data.networks()) {
                names.put(network.id(), network.name().toLowerCase(Locale.ROOT));
            }
            searchNames = names;
            searchNamesVersion = data.version();
        }
        return searchNames;
    }

    /** Palavras da busca: em minúsculas, separadas por espaços ou vírgulas, sem vazias. */
    static String[] searchWords(String search) {
        return Arrays.stream(SEARCH_SPLIT.split(search.strip().toLowerCase(Locale.ROOT)))
                .filter(word -> !word.isEmpty())
                .toArray(String[]::new);
    }
}
