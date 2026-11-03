package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.packet.TabletFlowsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMapPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletVerdictPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Cliente: o último pacote de cada vigia do servidor ({@link TabletWatches}) que o {@link TabletMenu} recebeu, ou
 * {@code null} até chegar o primeiro. O veredito só vale se o nó e a máquina dele batem com a escolha da tela.
 */
public final class TabletClientFeed {
    private @Nullable TabletFlowsPayload flows;
    private @Nullable TabletMapPayload map;
    private @Nullable TabletVerdictPayload verdict;

    public void applyFlows(TabletFlowsPayload payload) {
        flows = payload;
    }

    public void applyMap(TabletMapPayload payload) {
        map = payload;
    }

    public void applyVerdict(TabletVerdictPayload payload) {
        verdict = payload;
    }

    public @Nullable TabletFlowsPayload flows() {
        return flows;
    }

    /** Os pontos do Mapa; {@code null} (a tela usa a página da Lista até chegarem). */
    public @Nullable TabletMapPayload mapPoints() {
        return map;
    }

    public @Nullable TabletVerdictPayload verdict() {
        return verdict;
    }
}
