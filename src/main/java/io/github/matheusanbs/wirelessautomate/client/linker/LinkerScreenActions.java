package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.linker.LinkerFaces;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload.Op;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

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

    private void send(Op op, Optional<UUID> network, String text, int value) {
        PacketDistributor.sendToServer(new LinkerActionPayload(menu.containerId, op, network, text, value));
    }

    /** Cópia do estado com rede, desvincular, abas e modo trocados (só na captura de desenvolvimento). */
    private static LinkerSnapshot with(LinkerSnapshot s, Optional<UUID> active, boolean unlink, LinkerTabs tabs,
            LinkerMode mode) {
        return with(s, active, unlink, tabs, mode, s.faces());
    }

    private static LinkerSnapshot with(LinkerSnapshot s, Optional<UUID> active, boolean unlink, LinkerTabs tabs,
            LinkerMode mode, LinkerFaces faces) {
        return new LinkerSnapshot(s.networks(), active, unlink, tabs, s.available(), mode, s.first(), s.second(),
                s.otherDimension(), s.inside(), s.already(), s.unloadedChunks(), s.routers(), s.problem(),
                s.maxVolume(), s.maxDistance(), Optional.empty(), faces);
    }

    /** Grava as faces (o servidor aceita vazio: o aviso aparece e o Vincular desliga). */
    private void setFaces(LinkerFaces next) {
        if (preview) {
            LinkerSnapshot s = snapshot();
            apply(with(s, s.active(), s.unlink(), s.tabs(), s.mode(), next));
            return;
        }
        send(Op.SET_FACES, Optional.empty(), "", next.mask());
    }

    void toggleFace(RemoteSide side) {
        setFaces(snapshot().faces().toggle(side));
    }

    /** "Todas" só marca as seis (para desmarcar, as letras uma a uma). */
    void setAllFaces() {
        if (!snapshot().faces().isAll()) {
            setFaces(LinkerFaces.ALL);
        }
    }

    void setMode(LinkerMode mode) {
        if (preview) {
            LinkerSnapshot s = snapshot();
            apply(with(s, s.active(), s.unlink(), s.tabs(), mode));
            return;
        }
        send(Op.SET_MODE, Optional.empty(), "", mode.ordinal());
    }

    /** Marca ou desmarca a aba; o servidor recusa desmarcar a última. */
    void toggleTab(ResourceType t) {
        if (preview) {
            LinkerSnapshot s = snapshot();
            LinkerTabs next = s.tabs().toggle(t);
            if (!next.isEmpty(s.available().types())) {
                apply(with(s, s.active(), s.unlink(), next, s.mode()));
            }
            return;
        }
        send(Op.TOGGLE_TAB, Optional.empty(), "", t.ordinal());
    }

    /**
     * "Todos": com todas as abas marcadas deixa só a primeira disponível (não dá para ficar sem
     * nenhuma); senão marca todas as disponíveis.
     */
    void setAllTabs() {
        LinkerSnapshot s = snapshot();
        List<ResourceType> available = s.available().types();
        if (available.isEmpty()) {
            return;
        }
        LinkerTabs next = LinkerText.allTabs(s) ? LinkerTabs.of(available.get(0)) : LinkerTabs.available(available);
        if (preview) {
            apply(with(s, s.active(), s.unlink(), next, s.mode()));
            return;
        }
        send(Op.SET_TABS, Optional.empty(), "", next.mask());
    }

    /** A linha escolhida na lista: a rede {@code entry}, ou "Nenhuma (desvincular)" com {@code null}. */
    void choose(@Nullable NetworkEntry entry) {
        LinkerSnapshot s = snapshot();
        if (entry == null) {
            // "Nenhuma (desvincular)"
            if (s.unlink()) {
                return;
            }
            if (preview) {
                apply(with(s, s.active(), true, s.tabs(), s.mode()));
                return;
            }
            send(Op.SET_UNLINK, Optional.empty(), "", 1);
            return;
        }
        if (!entry.owned() || (!s.unlink() && Optional.of(entry.id()).equals(s.active()))) {
            return;
        }
        if (preview) {
            apply(with(s, Optional.of(entry.id()), false, s.tabs(), s.mode()));
            return;
        }
        send(Op.SET_ACTIVE, Optional.of(entry.id()), "", 0);
    }

    void link() {
        if (!preview) {
            send(Op.LINK, Optional.empty(), "", 0);
        }
    }

    void clear() {
        if (preview) {
            LinkerSnapshot s = snapshot();
            apply(new LinkerSnapshot(s.networks(), s.active(), s.unlink(), s.tabs(), s.available(), s.mode(),
                    Optional.empty(), Optional.empty(), false, 0, 0, 0, List.of(), LinkerProblem.NO_AREA, s.maxVolume(),
                    s.maxDistance(), Optional.empty(), s.faces()));
            return;
        }
        send(Op.CLEAR_AREA, Optional.empty(), "", 0);
    }

    /** Cria a rede {@code name}; na captura, nada. Diz se mandou. */
    boolean createNetwork(String name) {
        if (preview) {
            return false;
        }
        send(Op.CREATE_NETWORK, Optional.empty(), name, 0);
        return true;
    }

    private void apply(LinkerSnapshot snapshot) {
        menu.applySnapshot(snapshot);
        refresh.run();
    }
}
