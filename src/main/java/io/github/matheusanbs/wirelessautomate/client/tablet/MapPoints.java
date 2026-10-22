package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.packet.TabletMapPayload;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/**
 * Os pontos que o Mapa desenha, em arrays primitivos: a posição ({@link BlockPos#asLong()}) e o estado de cada
 * roteador da dimensão, e quantos há em outras dimensões. Vêm do {@link TabletMapPayload} (todos os que o jogador
 * vê) ou, enquanto ele não chega e na prévia sem servidor, da página da Lista. Montado quando a fonte muda, não por
 * quadro.
 */
final class MapPoints {
    static final MapPoints EMPTY = new MapPoints(new long[0], new byte[0], 0, 0);

    private static final NodeStatus[] STATUSES = NodeStatus.values();

    final long[] positions;
    final byte[] statuses;
    /** Quantos roteadores que o jogador vê (nesta dimensão) o servidor mandou a menos, por causa do teto. */
    final int omitted;
    final int otherDimension;

    private MapPoints(long[] positions, byte[] statuses, int omitted, int otherDimension) {
        this.positions = positions;
        this.statuses = statuses;
        this.omitted = omitted;
        this.otherDimension = otherDimension;
    }

    static MapPoints of(TabletMapPayload payload) {
        return new MapPoints(payload.positions(), payload.statuses(), Math.max(0, payload.total() - payload.count()),
                payload.otherDimension());
    }

    /** Os nós da página da Lista; {@code sameDimension} separa os da dimensão do jogador. */
    static MapPoints ofPage(List<NodeView> nodes, Predicate<NodeView> sameDimension) {
        int same = 0;
        for (NodeView node : nodes) {
            if (sameDimension.test(node)) {
                same++;
            }
        }
        long[] positions = new long[same];
        byte[] statuses = new byte[same];
        int i = 0;
        for (NodeView node : nodes) {
            if (sameDimension.test(node)) {
                positions[i] = node.key().pos().asLong();
                statuses[i++] = (byte) node.status().ordinal();
            }
        }
        return new MapPoints(positions, statuses, 0, nodes.size() - same);
    }

    int count() {
        return positions.length;
    }

    NodeStatus status(int index) {
        return STATUSES[Math.min(Math.max(statuses[index], 0), STATUSES.length - 1)];
    }

    /** O índice do ponto em {@code pos}, ou -1. */
    int indexOf(long pos) {
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] == pos) {
                return i;
            }
        }
        return -1;
    }
}
