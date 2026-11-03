package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import io.github.matheusanbs.wirelessautomate.client.access.AccessPanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.ModalLayer;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.TitleEditor;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;

/**
 * O que abre por cima do painel da aba Redes, a pedido das peças: o nome em edição ({@link TitleEditor}) e as janelas
 * sobre o véu ({@link ModalLayer}): o seletor Grupo, o "+ pôr uma rede neste grupo" e a {@link AccessWindow}. Lê a
 * rede ou o grupo escolhido da anfitriã ({@link NetworksPanel}).
 */
final class NetworksWindows {
    private final TabletLayout layout;
    private final ModalLayer modals;
    private final NetworksModel model;
    private final NetworkActions send;
    private final TitleEditor title;
    private final AccessPanel access;
    private final Supplier<@Nullable UUID> network;
    private final Supplier<@Nullable UUID> group;
    private @Nullable NetworkDetail detail;
    private @Nullable GroupPanel groupPanel;
    private List<EditBox> accessBoxes = List.of();

    NetworksWindows(TabletLayout layout, ModalLayer modals, NetworksModel model, NetworkActions send,
            TitleEditor title, AccessPanel access, Supplier<@Nullable UUID> network, Supplier<@Nullable UUID> group) {
        this.layout = layout;
        this.modals = modals;
        this.model = model;
        this.send = send;
        this.title = title;
        this.access = access;
        this.network = network;
        this.group = group;
    }

    /** As peças que dão a largura do título e redesenham a âncora das listas (criadas depois desta). */
    void attach(NetworkDetail networkDetail, GroupPanel chosenGroup) {
        this.detail = networkDetail;
        this.groupPanel = chosenGroup;
    }

    /** Os campos do acesso, refeitos a cada {@code init}. */
    void accessBoxes(List<EditBox> boxes) {
        accessBoxes = boxes;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private @Nullable NetworkView networkView() {
        UUID id = network.get();
        return id == null ? null : model.network(id);
    }

    private @Nullable GroupView groupView() {
        UUID id = group.get();
        return id == null || network.get() != null ? null : model.group(id);
    }

    boolean titleEditing() {
        return title.editing();
    }

    /** Abre o nome da rede ou do grupo escolhido para edição, com o texto todo selecionado. */
    void startTitle() {
        int x = layout.left + NetworkDetail.X0 + 21;
        int y = layout.top + 30;
        NetworkView view = networkView();
        GroupView chosen = groupView();
        if (view != null && view.manageable() && detail != null) {
            title.start(view.name(), x, y, detail.titleWidth());
        } else if (chosen != null && chosen.manageable() && groupPanel != null) {
            title.start(chosen.name(), x, y, groupPanel.titleWidth());
        }
    }

    /** O seletor de cor da rede escolhida, abaixo do slot da cor; a cor vai ao servidor ao fechar. */
    void openColor() {
        NetworkView view = networkView();
        if (view != null) {
            UUID id = view.id();
            modals.open(new ColorModal(font(), layout.left, layout.top, view.color(), rgb -> send.networkColor(id, rgb)));
        }
    }

    /** A cor que o seletor aberto está mostrando (ARGB), ou 0 sem seletor aberto. */
    int liveColor() {
        return modals.current() instanceof ColorModal open ? open.current() : 0;
    }

    void openAccess() {
        NetworkView view = networkView();
        if (view != null) {
            modals.open(new AccessWindow(layout, access, accessBoxes, font(), view.name()));
            access.refresh(true);
        }
    }

    void openGroupPicker() {
        NetworkView view = networkView();
        if (view == null || detail == null) {
            return;
        }
        GroupView in = model.groupOf(view.id());
        UUID id = view.id();
        NetworkDetail anchor = detail;
        modals.open(GroupPicker.groups(layout.left + NetworkDetail.VX, layout.top + 60, model.snapshot(),
                model.icons(), in == null ? null : in.id(), to -> send.moveNetwork(id, to),
                g -> anchor.renderPicker(g, font(), false)));
    }

    /** A lista das redes fora do grupo escolhido, aberta logo abaixo do link em ({@code x}, {@code y}). */
    void openAddToGroup(int x, int y) {
        GroupView chosen = groupView();
        if (chosen != null && groupPanel != null) {
            UUID id = chosen.id();
            GroupPanel anchor = groupPanel;
            modals.open(GroupPicker.networks(x, y, model.snapshot(), chosen, to -> send.moveNetwork(to, id),
                    g -> anchor.renderLink(g, font())));
        }
    }
}
