package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
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

    // Campos de rota, refeitos pela montagem da rede.
    @Nullable NetworkRoutes network;
    boolean source;
    boolean destination;
    int priority;
    RouterTier tier = RouterTier.BASIC;
    BlockPos machinePos = BlockPos.ZERO;
    /** Destinos desta origem; {@code null} quando ela não tem para onde mandar. */
    @Nullable RoundRobinOrder<Port> order;
    /** Origens que entregam neste destino, para acordá-las quando ele acordar. */
    final List<Port> feeders = new ArrayList<>(0);

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
        order = null;
        feeders.clear();
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
