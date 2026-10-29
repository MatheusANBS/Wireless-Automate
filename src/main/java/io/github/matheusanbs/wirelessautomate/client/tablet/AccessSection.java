package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.MIN_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.RX;

import io.github.matheusanbs.wirelessautomate.client.access.AccessHost;
import io.github.matheusanbs.wirelessautomate.client.access.AccessPanel;
import io.github.matheusanbs.wirelessautomate.menu.SuggestionsMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import java.util.UUID;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * O acesso a uma rede privada na coluna da direita da aba Redes, logo abaixo do segmento Pública/Privada e da cor: o
 * {@link AccessPanel} com as medidas da aba e as ações da rede escolhida. Cresce com a altura da tela: as linhas a
 * mais vão para as listas.
 */
final class AccessSection {
    private static final int ROW = 13;
    /** Onde a seção começa, em relação ao corpo da aba: abaixo do segmento e da cor. */
    static final int LABEL_Y = BODY_Y + 62;

    private AccessSection() {
    }

    static AccessPanel create(TabletLayout layout, TabletMenu menu, TabletActions actions,
            Supplier<@Nullable NetworkView> network) {
        return new AccessPanel(new Frame(layout), new Host(menu, actions, network));
    }

    /** As medidas da seção pela tela do Tablet, lidas a cada uso. */
    private record Frame(TabletLayout layout) implements AccessPanel.Frame {
        /** As linhas que a altura da tela dá a mais que o mínimo. */
        private int extraRows() {
            return Math.max(0, (layout.h - MIN_H) / ROW);
        }

        @Override
        public int x() {
            return layout.left + RX;
        }

        @Override
        public int labelY() {
            return layout.top + LABEL_Y;
        }

        @Override
        public int width() {
            return layout.rw();
        }

        @Override
        public int guestRows() {
            return AccessPanel.withTeams() ? 2 + (extraRows() + 1) / 2 : 5 + extraRows();
        }

        @Override
        public int teamRows() {
            return 1 + extraRows() / 2;
        }

        @Override
        public int bottom() {
            return layout.top + layout.h - 8;
        }
    }

    /** As ações vão para a rede escolhida; sem rede escolhida, não há lista. */
    private record Host(TabletMenu tablet, TabletActions actions, Supplier<@Nullable NetworkView> network)
            implements AccessHost {
        @Override
        public @Nullable AccessView access() {
            NetworkView view = network.get();
            return view == null ? null : view.access();
        }

        @Override
        public SuggestionsMenu menu() {
            return tablet;
        }

        @Override
        public void member(@Nullable UUID id, String text, boolean add) {
            NetworkView view = network.get();
            if (view != null) {
                actions.member(view.id(), id, text, add);
            }
        }

        @Override
        public void team(@Nullable UUID id, String text, boolean add) {
            NetworkView view = network.get();
            if (view != null) {
                actions.team(view.id(), id, text, add);
            }
        }

        @Override
        public void suggest(NetworkSuggestPayload.Kind kind, String text) {
            NetworkView view = network.get();
            if (view != null) {
                actions.suggest(kind, text, view.id());
            }
        }
    }
}
