package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    /**
     * Os caches de capability da máquina desta porta: a presa ({@link RouterBlockEntity#attachedCaches},
     * uma instância só que acompanha o giro) ou uma remota. As transferências consultam a máquina por
     * aqui, nunca pelo nó.
     */
    final MachineCaches machine;
    final Direction face;
    final ResourceType type;
    /** Vazão desta porta como origem. */
    final RateLimiter limiter = new RateLimiter();
    /** Origem sem nada para mover. Separado do destino: uma face Ambos pode estar cheia e ter o que dar. */
    final Backoff sourceBackoff = new Backoff(NetworkManager.MAX_SLEEP_TICKS);
    /** Destino que recusou. */
    final Backoff destinationBackoff = new Backoff(NetworkManager.MAX_SLEEP_TICKS);
    /**
     * O que esta porta entregou como origem desde a última leitura, na unidade do tipo (itens, mB, FE,
     * Source ou unidades de químico). Somado pelas transferências no fim da visita que moveu; o
     * amostrador dos Fluxos lê e zera.
     */
    long gave;
    /**
     * O que esta porta recebeu como destino desde a última leitura, na mesma unidade. Somado pelas
     * transferências a cada entrega; o amostrador dos Fluxos lê e zera.
     */
    long got;
    /**
     * O que o amostrador já leu de {@link #gave} e de {@link #got} desde que a porta existe: com ele, o total
     * de uma porta nos GameTests não depende de quando a amostra passou. Só o amostrador soma, uma vez por
     * segundo.
     */
    long gaveRead;
    long gotRead;
    /** Próximo slot a varrer como origem de itens. */
    int slotCursor;
    /** Slots varridos seguidos sem mover nada, somando visitas: uma volta inteira faz a origem dormir. */
    int idleSlots;
    /** Duração da última visita que parou num teto, para as voltas extras do tick caberem no orçamento. */
    long visitNanos;
    /** Contagem da origem para o estoque do filtro; criada na primeira visita que precisar. */
    @Nullable ItemStockTally tally;
    /**
     * Motivo do último sono da origem, gravado onde ela dorme ({@link SourceSleep}): {@code true} se
     * todos os destinos dormiam ou se ela ofereceu algo e foi recusada (espera destino); {@code false}
     * se não tinha o que dar (vazia, filtro ou estoque da própria origem, sem máquina). Uma mudança na
     * máquina de um destino só acorda as que esperam destino; a origem vazia acorda pela própria
     * máquina. Começa {@code true}.
     */
    boolean waitsDestination = true;
    /**
     * A origem ofereceu algo a um destino acordado que passou no filtro dele, desde o último sono ou
     * entrega (a volta sem mover pode durar várias visitas). Marcado pelas transferências, zerado pelo
     * {@link NetworkManager} depois de uma visita que moveu ou dormiu.
     */
    boolean offered;
    /**
     * O tick do gerenciador em que este destino acordou as alimentadoras dele ({@link NodePorts}): uma máquina que
     * avisa mudança várias vezes no mesmo tick não percorre a lista delas de novo.
     */
    long feedersWokenAt = Long.MIN_VALUE;
    /** O controle por redstone da face, lido com a configuração. */
    RedstoneMode redstone = RedstoneMode.IGNORE;
    /**
     * Parada: o sinal do roteador desliga a face ({@link #applyRedstone}), ou o nó saiu do motor e as rotas ainda
     * não foram remontadas ({@link #stop}). A porta fica nas rotas, mas dorme sem prazo como origem e como destino,
     * e nenhum aviso a acorda. Assim um pulso de redstone não remonta as rotas, e a saída de um nó pode esperar a
     * remontagem adiada.
     */
    boolean stopped;

    // Campos de rota, refeitos pela montagem da rede. Os da configuração da face (rede, modo,
    // prioridade, filtro, tier, máquina) só são relidos quando o nó muda (ver NodePorts#collected).
    /**
     * A rede da face, lida com a configuração ({@link FaceConfig#network()}); {@code null} = sem rede.
     * Só a rede com este id adota a porta.
     */
    @Nullable UUID faceNetwork;
    /**
     * A porta foi relida e a rede dela ainda não a consumiu: a leitura é dividida entre as redes do
     * tipo no nó, e quem relê pode ser outra rede ({@link NetworkRoutes}). Vira {@link #fresh} na
     * montagem da rede da porta.
     */
    boolean pendingFresh;
    /** A rede que adotou a porta na última montagem (a rota); {@code null} = sem rota. */
    @Nullable NetworkRoutes network;
    boolean source;
    boolean destination;
    int priority;
    RouterTier tier = RouterTier.BASIC;
    /**
     * Posição da máquina. Numa remota é fixa (a do construtor); na presa é relida com a configuração,
     * porque girar o roteador muda a máquina de lugar sem trocar a porta.
     */
    BlockPos machinePos;
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
    /** Como {@link #sharedFeeders}, para as faces Ambos que entregam em todos os destinos que só inserem. */
    List<Port> sharedBothFeeders = List.of();
    /**
     * Só durante a montagem: a ordem que a origem tinha antes, para continuar com os mesmos cursores
     * se os destinos não mudaram.
     */
    @Nullable RoundRobinOrder<Port> previousOrder;
    /** Só durante a montagem: a configuração da porta foi relida nesta montagem (nó novo ou mudado). */
    boolean fresh;
    /** Só durante a montagem: marca de "estava na ordem antiga" ({@link NetworkRoutes}). */
    int stamp;

    Port(RouterBlockEntity node, MachineCaches machine, Direction face, ResourceType type) {
        this.node = node;
        this.machine = machine;
        this.machinePos = machine.pos();
        this.face = face;
        this.type = type;
    }

    /** Limpa tudo o que a montagem preenche, inclusive o que veio da configuração da face. */
    void clearRoute() {
        faceNetwork = null;
        pendingFresh = false;
        network = null;
        source = false;
        destination = false;
        filter = FilterSet.EMPTY;
        clearOrder();
    }

    /**
     * Limpa só o que depende das outras portas (destinos e alimentadoras), e mantém o que veio da
     * configuração da face: a porta de um nó que não mudou entra na montagem sem ser relida.
     */
    void clearOrder() {
        order = null;
        feeders.clear();
        sharedFeeders = List.of();
        sharedBothFeeders = List.of();
    }

    /**
     * Destino sem máquina (ou com o chunk dela descarregado): dorme até o listener da capability da
     * face acordá-lo, ou no máximo {@link NetworkManager#MAX_SLEEP_TICKS}. Não mexe no intervalo, então
     * não conta como cheio no Tablet ({@link NodeProbe#isFull}).
     */
    void sleepWithoutMachine(long now) {
        destinationBackoff.sleepUntil(now, now + NetworkManager.MAX_SLEEP_TICKS);
    }

    /**
     * Aplica o sinal do roteador ao controle por redstone da face: desligada, dorme sem prazo; ligada de novo,
     * acorda. Devolve se acabou de ligar (quem chama acorda as origens que esperam por ela).
     */
    boolean applyRedstone(boolean powered, long now) {
        boolean off = !redstone.allows(powered);
        if (off == stopped) {
            return false;
        }
        if (off) {
            stop(now);
            return false;
        }
        stopped = false;
        sourceBackoff.thaw();
        destinationBackoff.thaw();
        sourceBackoff.wake();
        destinationBackoff.wake();
        idleSlots = 0;
        return true;
    }

    /**
     * Para a porta sem prazo (ver {@link #stopped}): os dois {@link Backoff} congelam, então nem o {@code wake()}
     * de uma entrega na mesma visita a acorda.
     */
    void stop(long now) {
        stopped = true;
        sourceBackoff.freeze();
        destinationBackoff.freeze();
    }

    /** Mesma máquina e mesma face: entregar aqui seria devolver o que acabou de sair. */
    boolean sameEndpoint(Port other) {
        return face == other.face && node.getLevel() == other.node.getLevel() && machinePos.equals(other.machinePos);
    }

    @Override
    public String toString() {
        return "Port[" + node.getBlockPos().toShortString() + (machine.isRemote() ? " @" + machinePos.toShortString() : "")
                + " " + face + " " + type + "]";
    }
}
