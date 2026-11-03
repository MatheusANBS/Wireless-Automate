package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A conta de "Nesta rede" do Tablet: por rede, quantas máquinas há e, por tipo, quantas dão (Extrair ou Armazém) e
 * quantas recebem (Inserir ou Armazém). Uma máquina conta numa rede quando a máscara dela ({@code NetworkOrder}) tem o
 * bit da rede; dá ou recebe um tipo quando tem o papel dele e a rede está entre as do tipo no nó. A máscara é a união
 * dos tipos, então uma máquina com papéis cruzados em duas redes do mesmo tipo pode contar a mais. Lógica pura, com
 * JUnit; os bits dos papéis são os do {@code NodeIndex.role} (um GameTest confere).
 */
public final class NetworkTally {
    public static final int EXTRACT = 0;
    public static final int INSERT = 1;
    public static final int STORAGE = 2;
    private static final int ROLES_PER_TYPE = 3;
    private static final ResourceType[] TYPES = ResourceType.values();

    private NetworkTally() {
    }

    /** O bit do papel {@code role} do tipo, igual ao {@code NodeIndex.role} (o GameTest do cabeçalho confere). */
    public static int bit(ResourceType type, int role) {
        return 1 << (type.ordinal() * ROLES_PER_TYPE + role);
    }

    /** As somas de uma rede. */
    static final class Counts {
        private int machines;
        private final int[] givers = new int[TYPES.length];
        private final int[] receivers = new int[TYPES.length];

        int machines() {
            return machines;
        }

        int givers(ResourceType type) {
            return givers[type.ordinal()];
        }

        int receivers(ResourceType type) {
            return receivers[type.ordinal()];
        }
    }

    /**
     * Soma uma máquina de papéis {@code roles} e máscara {@code nets} sobre {@code order} (as redes distintas do nó)
     * em {@code totals}; redes fora do mapa ficam de fora. Sem alocar.
     */
    static void machine(int roles, long nets, List<UUID> order, Function<ResourceType, List<UUID>> networksOf,
            Map<UUID, Counts> totals) {
        for (int k = 0, n = Math.min(order.size(), 63); k < n; k++) {
            if ((nets & 1L << k) == 0) {
                continue;
            }
            UUID network = order.get(k);
            Counts counts = totals.get(network);
            if (counts == null) {
                continue;
            }
            counts.machines++;
            for (ResourceType type : TYPES) {
                if (roles == 0) {
                    break;
                }
                List<UUID> ofType = networksOf.apply(type);
                if (ofType == null || !ofType.contains(network)) {
                    continue;
                }
                boolean storage = (roles & bit(type, STORAGE)) != 0;
                if (storage || (roles & bit(type, EXTRACT)) != 0) {
                    counts.givers[type.ordinal()]++;
                }
                if (storage || (roles & bit(type, INSERT)) != 0) {
                    counts.receivers[type.ordinal()]++;
                }
            }
        }
    }
}
