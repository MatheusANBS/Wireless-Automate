package io.github.matheusanbs.wirelessautomate.client.tablet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * As seções da lista da aba Redes (lógica pura): cada grupo de redes é uma seção, com o cabeçalho e as redes dele
 * na ordem das redes, e as redes sem grupo vêm por último, em "Sem grupo". Uma rede em vários grupos aparece em todas
 * as seções, com os outros grupos dela (para a dica "também em …"). Uma seção recolhida fica só com o cabeçalho. Sem
 * nenhum grupo, a lista é só das redes, sem cabeçalho.
 */
public final class NetworkSections {
    /** A chave da seção "Sem grupo" no conjunto das recolhidas. */
    public static final UUID NO_GROUP = new UUID(0, 0);

    /** Um grupo de redes: o id e as redes dele. */
    public record Group(UUID id, List<UUID> networks) {
    }

    /**
     * Uma linha.
     *
     * @param group     a posição do grupo da seção, ou -1 em "Sem grupo"
     * @param network   a posição da rede, ou -1 no cabeçalho
     * @param others    numa rede, as posições dos outros grupos em que ela está
     * @param collapsed num cabeçalho, se a seção está recolhida
     */
    public record Row(int group, int network, List<Integer> others, boolean collapsed) {
        public boolean header() {
            return network < 0;
        }
    }

    private NetworkSections() {
    }

    /**
     * @param networks  as redes que o jogador vê, na ordem da lista
     * @param groups    os grupos, na ordem da lista; as redes deles que o jogador não vê ficam de fora
     * @param collapsed as seções recolhidas (os ids dos grupos e {@link #NO_GROUP})
     */
    public static List<Row> build(List<UUID> networks, List<Group> groups, Set<UUID> collapsed) {
        List<Row> rows = new ArrayList<>();
        if (groups.isEmpty()) {
            for (int n = 0; n < networks.size(); n++) {
                rows.add(new Row(-1, n, List.of(), false));
            }
            return rows;
        }
        List<Set<UUID>> members = new ArrayList<>(groups.size());
        Set<UUID> grouped = new HashSet<>();
        for (Group group : groups) {
            Set<UUID> set = new HashSet<>(group.networks());
            members.add(set);
            grouped.addAll(set);
        }
        for (int g = 0; g < groups.size(); g++) {
            boolean closed = collapsed.contains(groups.get(g).id());
            rows.add(new Row(g, -1, List.of(), closed));
            if (closed) {
                continue;
            }
            for (int n = 0; n < networks.size(); n++) {
                if (members.get(g).contains(networks.get(n))) {
                    rows.add(new Row(g, n, others(members, g, networks.get(n)), false));
                }
            }
        }
        List<Integer> loose = new ArrayList<>();
        for (int n = 0; n < networks.size(); n++) {
            if (!grouped.contains(networks.get(n))) {
                loose.add(n);
            }
        }
        if (!loose.isEmpty()) {
            boolean closed = collapsed.contains(NO_GROUP);
            rows.add(new Row(-1, -1, List.of(), closed));
            if (!closed) {
                for (int n : loose) {
                    rows.add(new Row(-1, n, List.of(), false));
                }
            }
        }
        return rows;
    }

    private static List<Integer> others(List<Set<UUID>> members, int group, UUID network) {
        List<Integer> others = null;
        for (int g = 0; g < members.size(); g++) {
            if (g != group && members.get(g).contains(network)) {
                if (others == null) {
                    others = new ArrayList<>();
                }
                others.add(g);
            }
        }
        return others == null ? List.of() : Collections.unmodifiableList(others);
    }
}
