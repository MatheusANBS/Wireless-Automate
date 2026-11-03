package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.Verdict;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.Reach;
import io.github.matheusanbs.wirelessautomate.network.VerdictProbe;
import io.github.matheusanbs.wirelessautomate.network.VerdictRules;
import io.github.matheusanbs.wirelessautomate.packet.TabletVerdictPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * O veredito da aba Máquina do Tablet no servidor: com a aba aberta e uma máquina escolhida (a presa ou uma remota do
 * nó escolhido), lê as portas dela a cada {@link TabletMenu#SAMPLE_TICKS} ticks ({@link VerdictProbe}) e só devolve o
 * {@link TabletVerdictPayload} se o veredito mudou. A escolha é lida do {@link TabletChosenNode} a cada chamada: trocar
 * de nó ou de máquina força a próxima leitura. Sem a aba aberta, sem nó ou com um grupo como alvo (que não é uma máquina)
 * não roda nada.
 */
final class TabletVerdict {
    private final ServerPlayer viewer;
    private final int containerId;
    private final TabletChosenNode chosen;
    private boolean watching;
    private @Nullable NodeKey readNode;
    private @Nullable EditTargetRef readTarget;
    private long lastRead;
    private @Nullable TabletVerdictPayload sent;

    TabletVerdict(ServerPlayer viewer, int containerId, TabletChosenNode chosen) {
        this.viewer = viewer;
        this.containerId = containerId;
        this.chosen = chosen;
    }

    /** A tela mostra a aba {@code tab}: só a aba Máquina recebe o veredito. */
    void tab(int tab) {
        if (tab == TabletMenu.LIST_TAB) {
            watching = true;
        } else {
            stop();
        }
    }

    /** Saiu da aba ou fechou o Tablet: a próxima vez manda de novo. */
    void stop() {
        watching = false;
        sent = null;
        readNode = null;
        readTarget = null;
    }

    /** O veredito de uma leitura nova, se mudou desde o último enviado; senão {@code null}. */
    @Nullable TabletVerdictPayload poll() {
        if (!watching) {
            return null;
        }
        NodeKey key = chosen.chosen();
        EditTargetRef target = chosen.targetRef();
        if (key == null || target.target().kind() == EditTarget.Kind.GROUP) {
            readNode = null;
            sent = null;
            return null;
        }
        long now = viewer.server.getTickCount();
        boolean same = Objects.equals(key, readNode) && target.equals(readTarget);
        if (same && now >= lastRead && now - lastRead < TabletMenu.SAMPLE_TICKS) {
            return null;
        }
        readNode = key;
        readTarget = target;
        lastRead = now;
        BlockPos machine = target.target().attached() ? null : target.pos();
        RouterBlockEntity router = chosen.editedRouter();
        Verdict verdict = router == null ? VerdictRules.decide(new VerdictFacts(Reach.UNLOADED, List.of()))
                : VerdictRules.decide(VerdictProbe.read(router, machine, reach(router, machine), now));
        TabletVerdictPayload built = new TabletVerdictPayload(containerId, Optional.of(key),
                Optional.ofNullable(machine), Optional.of(verdict));
        if (built.equals(sent)) {
            return null;
        }
        sent = built;
        return built;
    }

    /** O alcance da máquina; nenhuma consulta a ela sem o chunk carregado ({@code level.isLoaded}). */
    private static Reach reach(RouterBlockEntity router, @Nullable BlockPos machine) {
        if (machine == null) {
            return router.getLevel() != null && router.getLevel().isLoaded(router.machinePos()) ? Reach.OK : Reach.UNLOADED;
        }
        if (!router.rangeActive()) {
            return Reach.NO_UPGRADE;
        }
        RemoteMachines remotes = router.remoteMachines();
        int index = remotes.index(machine);
        RemoteMachine remote = remotes.machine(index);
        if (remote == null) {
            return Reach.MISSING;
        }
        return switch (RouterSnapshot.remoteState(router, remote, router.machineReach()[index])) {
            case UNLOADED -> Reach.UNLOADED;
            case OUT_OF_RANGE -> Reach.OUT_OF_RANGE;
            case OVER_LIMIT -> Reach.OVER_LIMIT;
            case REPLACED -> Reach.REPLACED;
            case MISSING -> Reach.MISSING;
            case LOADED -> Reach.OK;
        };
    }
}
