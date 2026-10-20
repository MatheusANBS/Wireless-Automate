package io.github.matheusanbs.wirelessautomate.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * A máscara de redes de uma máquina no {@link NodeIndex}: o bit {@code k} é a {@code k}-ésima rede distinta do nó,
 * na ordem de {@link LoadedTypes#LIST} e, dentro do tipo, na de {@link NodeIndex.Entry#networks}. Com mais de 63
 * redes distintas num nó, o bit 63 ({@link #OTHERS}) quer dizer "alguma das outras", e a máquina com ele passa em
 * qualquer filtro de rede (melhor mostrar a mais do que esconder).
 */
public final class NetworkOrder {
    /** "Alguma das redes acima da 63ª". */
    public static final long OTHERS = 1L << 63;

    private NetworkOrder() {
    }

    /** As redes distintas do nó na ordem dos bits; montada só quando as redes do nó mudam. */
    static List<UUID> of(NodeIndex.Entry entry) {
        List<UUID> order = new ArrayList<>();
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                if (!order.contains(id)) {
                    order.add(id);
                }
            }
        }
        return List.copyOf(order);
    }

    /** O bit da rede {@code network} em {@code order}; 0 sem rede ou com uma rede fora da ordem. Sem alocar. */
    static long bit(List<UUID> order, @Nullable UUID network) {
        if (network == null) {
            return 0;
        }
        int k = order.indexOf(network);
        return k < 0 ? 0 : k >= 63 ? OTHERS : 1L << k;
    }

    /** Os bits do nó das redes de {@code wanted}, para comparar com a máscara de uma máquina. Sem alocar. */
    public static long bits(NodeIndex.Entry entry, Set<UUID> wanted) {
        List<UUID> order = entry.networkOrder();
        long bits = 0;
        for (int k = 0; k < order.size(); k++) {
            if (wanted.contains(order.get(k))) {
                bits |= k >= 63 ? OTHERS : 1L << k;
            }
        }
        return bits;
    }

    /** A máquina de máscara {@code nets} passa no filtro de rede dos bits {@code wanted} ({@link #bits}). */
    public static boolean passes(long nets, long wanted) {
        return (nets & wanted) != 0 || (nets & OTHERS) != 0;
    }
}
