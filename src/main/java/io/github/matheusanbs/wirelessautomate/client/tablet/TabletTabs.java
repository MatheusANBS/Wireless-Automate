package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen.Tab;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;

/**
 * Os botões fixos do Tablet e o que muda com a aba aberta: de quem é cada botão, a visibilidade deles e dos
 * campos a cada quadro, e o Remover da aba (para o clique fora dele desarmar o de dois cliques). A
 * {@link TabletScreen} cria os botões e os põe aqui; esta parte só os guarda e liga e desliga.
 */
final class TabletTabs {
    private final List<FlatButton> buttons = new ArrayList<>();
    private final Map<Tab, List<FlatButton>> owned = new EnumMap<>(Tab.class);
    private final SelectBar selectBar;
    private final MapTab map;
    private final NetworksTab networks;
    private final NameFields names;

    TabletTabs(SelectBar selectBar, MapTab map, NetworksTab networks, NameFields names) {
        this.selectBar = selectBar;
        this.map = map;
        this.networks = networks;
        this.names = names;
    }

    /** Todos os botões fixos, para a dica do quadro. */
    List<FlatButton> buttons() {
        return buttons;
    }

    /** Esquece os botões, antes de a tela montar os widgets de novo. */
    void clear() {
        buttons.clear();
        owned.clear();
        for (Tab t : Tab.values()) {
            owned.put(t, new ArrayList<>());
        }
    }

    /** Guarda o botão; {@code owner} nulo: visível em todas as abas. */
    FlatButton register(@Nullable Tab owner, FlatButton button) {
        buttons.add(button);
        if (owner != null) {
            owned.get(owner).add(button);
        }
        return button;
    }

    /** Visibilidade dos botões e campos que dependem da aba aberta. */
    void refresh(Tab tab, EditBox searchBox) {
        for (Map.Entry<Tab, List<FlatButton>> entry : owned.entrySet()) {
            for (FlatButton button : entry.getValue()) {
                button.visible = entry.getKey() == tab;
            }
        }
        searchBox.visible = tab == Tab.LIST;
        selectBar.refresh(tab == Tab.LIST);
        map.refresh(tab == Tab.MAP);
        networks.refresh(tab == Tab.NETWORKS);
        names.refresh(tab == Tab.NETWORKS);
    }

    /** O mouse está no Remover da aba aberta. */
    boolean overRemoveButton(Tab tab, double mouseX, double mouseY) {
        FlatButton remove = tab == Tab.NETWORKS ? networks.removeButton() : null;
        return remove != null && remove.visible && remove.isMouseOver(mouseX, mouseY);
    }
}
