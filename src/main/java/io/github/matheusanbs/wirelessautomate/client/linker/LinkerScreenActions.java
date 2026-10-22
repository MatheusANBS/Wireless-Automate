package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload.Op;
import java.util.List;
import java.util.Optional;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tudo o que o Vinculador manda ao servidor. A tela não muda nada sozinha: manda a ação e espera o
 * servidor; na captura de desenvolvimento ({@code preview}) muda só o estado local.
 */
final class LinkerScreenActions {
    private final LinkerMenu menu;
    private final boolean preview;
    /** Visibilidade e mensagens que dependem do estado, refeitas depois de um estado local novo. */
    private final Runnable refresh;

    LinkerScreenActions(LinkerMenu menu, boolean preview, Runnable refresh) {
        this.menu = menu;
        this.preview = preview;
        this.refresh = refresh;
    }

    private LinkerSnapshot snapshot() {
        return menu.snapshot();
    }

    private void send(Op op, int value) {
        PacketDistributor.sendToServer(new LinkerActionPayload(menu.containerId, op, value));
    }

    /** Liga ou desliga o desvincular. */
    void setUnlink(boolean unlink) {
        LinkerSnapshot s = snapshot();
        if (s.unlink() == unlink) {
            return;
        }
        if (preview) {
            apply(new LinkerSnapshot(unlink, s.router(), s.first(), s.second(), s.otherDimension(), s.problem(),
                    s.unloadedChunks(), s.linkedInside(), s.machines(), s.maxVolume(), s.maxDistance(),
                    Optional.empty()));
            return;
        }
        send(Op.SET_UNLINK, unlink ? 1 : 0);
    }

    void link() {
        if (!preview) {
            send(Op.LINK, 0);
        }
    }

    void clear() {
        if (preview) {
            LinkerSnapshot s = snapshot();
            apply(new LinkerSnapshot(s.unlink(), s.router(), Optional.empty(), Optional.empty(), false,
                    LinkerProblem.NO_AREA, 0, 0, List.of(), s.maxVolume(), s.maxDistance(), Optional.empty()));
            return;
        }
        send(Op.CLEAR_AREA, 0);
    }

    private void apply(LinkerSnapshot snapshot) {
        menu.applySnapshot(snapshot);
        refresh.run();
    }
}
