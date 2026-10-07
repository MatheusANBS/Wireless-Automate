package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Uma porta: um nó, uma face absoluta da máquina e um tipo de recurso. Vive enquanto o nó estiver
 * no {@link NetworkManager}, então balde, cursores e sonos sobrevivem às reconstruções de rota;
 * só os campos de rota são refeitos na montagem.
 *
 * <p>Comparada por identidade (o {@link RoundRobinOrder} usa {@code equals}).
 */
final class Port {
    final RouterBlockEntity node;
    final Direction face;
    final ResourceType type;
    /** Vazão desta porta como origem. */
    final RateLimiter limiter = new RateLimiter();
    /** Origem sem nada para mover. Separado do destino: uma face Ambos pode estar cheia e ter o que dar. */
    final Backoff sourceBackoff = new Backoff(NetworkManager.MAX_SLEEP_TICKS);
    /** Destino que recusou. */
    final Backoff destinationBackoff = new Backoff(NetworkManager.MAX_SLEEP_TICKS);
    /** Próximo slot a varrer como origem de itens. */
    int slotCursor;
    /** Slots varridos seguidos sem mover nada, somando visitas: uma volta inteira faz a origem dormir. */
    int idleSlots;
    /** Contagem da origem para o estoque do filtro; criada na primeira visita que precisar. */
    @Nullable ItemTransfer.StockTally tally;

    // Campos de rota, refeitos pela montagem da rede.
    @Nullable NetworkRoutes network;
    boolean source;
    boolean destination;
    int priority;
    RouterTier tier = RouterTier.BASIC;
    BlockPos machinePos = BlockPos.ZERO;
    /**
     * Filtros da face (o embutido e os dos cartões), montados na montagem das rotas; energia ignora.
     * O laço só consulta: a correspondência compilada vive em cada {@code Filter}.
     */
    FilterSet filter = FilterSet.EMPTY;
    /** Destinos desta origem; {@code null} quando ela não tem para onde mandar. */
    @Nullable RoundRobinOrder<Port> order;
    /**
     * Origens que entregam neste destino, para acordá-las quando ele acordar: as que conferiram
     * destino a destino aqui, e as que entregam em todos os destinos do tipo na rede numa lista
     * só, dividida entre esses destinos (ver {@link NetworkRoutes}).
     */
    final List<Port> feeders = new ArrayList<>(0);
    List<Port> sharedFeeders = List.of();

    Port(RouterBlockEntity node, Direction face, ResourceType type) {
        this.node = node;
        this.face = face;
        this.type = type;
    }

    /** Limpa os campos de rota antes da montagem. */
    void clearRoute() {
        network = null;
        source = false;
        destination = false;
        filter = FilterSet.EMPTY;
        order = null;
        feeders.clear();
        sharedFeeders = List.of();
    }

    /** Mesma máquina e mesma face: entregar aqui seria devolver o que acabou de sair. */
    boolean sameEndpoint(Port other) {
        return face == other.face && node.getLevel() == other.node.getLevel() && machinePos.equals(other.machinePos);
    }

    @Override
    public String toString() {
        return "Port[" + node.getBlockPos().toShortString() + " " + face + " " + type + "]";
    }
}
