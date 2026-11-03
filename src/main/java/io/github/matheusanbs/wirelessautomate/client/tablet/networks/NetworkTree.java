package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * As linhas da coluna esquerda da aba Redes (mockup {@code lista_redes}): cada grupo, na ordem do servidor, com as redes
 * dele; uma rede em vários grupos (mundo antigo) aparece só no primeiro; por último "Sem grupo", só o rótulo, com as
 * redes avulsas, quando há alguma. {@code top} é o topo do realce da linha, em px a partir do topo do painel. Pura, com
 * JUnit.
 */
final class NetworkTree {
    static final int TOP = 28;
    static final int GROUP_H = 20;
    static final int GROUP_STEP = 22;
    static final int NETWORK_H = 14;
    static final int GROUP_GAP = 4;

    enum Kind { GROUP, NETWORK, LOOSE }

    /** {@code group}: o grupo em que a rede aparece, ou {@code null} em "Sem grupo". */
    record Row(Kind kind, @Nullable UUID id, @Nullable UUID group, int top, int height) {
    }

    record GroupIn(UUID id, List<UUID> networks) {
    }

    private NetworkTree() {
    }

    static List<Row> build(List<GroupIn> groups, List<UUID> networks) {
        Set<UUID> known = new HashSet<>(networks);
        Set<UUID> placed = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        int y = TOP;
        for (GroupIn group : groups) {
            rows.add(new Row(Kind.GROUP, group.id(), group.id(), y, GROUP_H));
            y += GROUP_STEP;
            for (UUID id : group.networks()) {
                if (known.contains(id) && placed.add(id)) {
                    rows.add(new Row(Kind.NETWORK, id, group.id(), y - 2, NETWORK_H));
                    y += NETWORK_H;
                }
            }
            y += GROUP_GAP;
        }
        boolean loose = false;
        for (UUID id : networks) {
            if (placed.contains(id)) {
                continue;
            }
            if (!loose) {
                loose = true;
                rows.add(new Row(Kind.LOOSE, null, null, y, GROUP_H));
                y += GROUP_STEP;
            }
            rows.add(new Row(Kind.NETWORK, id, null, y - 2, NETWORK_H));
            y += NETWORK_H;
        }
        return List.copyOf(rows);
    }

    /** Onde a última linha termina (a rolagem usa isso). */
    static int height(List<Row> rows) {
        if (rows.isEmpty()) {
            return TOP;
        }
        Row last = rows.get(rows.size() - 1);
        return last.top() + last.height();
    }
}
