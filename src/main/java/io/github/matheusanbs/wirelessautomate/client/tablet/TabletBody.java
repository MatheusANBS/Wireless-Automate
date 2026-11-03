package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen.Tab;
import io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachinePanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksPanel;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O corpo da tela do Tablet: repassa o desenho, o clique nas partes desenhadas, a roda e a dica para a aba
 * aberta (Máquina, Fluxos, Mapa ou Redes; a Máquina mostra a Lista antiga só com o gancho {@code legacyList}). Os botões fixos e os campos ficam com a tela; os cliques da aba Redes
 * vão direto a ela (as janelas dela vêm antes de tudo).
 */
final class TabletBody {
    private final TabletList list;
    private final MachinePanel machine;
    private final BooleanSupplier legacy;
    private final MapTab map;
    private final FlowsTab flows;
    private final NetworksPanel networks;

    TabletBody(TabletList list, MachinePanel machine, BooleanSupplier legacy, MapTab map, FlowsTab flows,
            NetworksPanel networks) {
        this.list = list;
        this.machine = machine;
        this.legacy = legacy;
        this.map = map;
        this.flows = flows;
        this.networks = networks;
    }

    /** O clique na aba Máquina (ou na Lista antiga): a lista de máquinas. */
    boolean listClicked(double mouseX, double mouseY, int button) {
        return legacy.getAsBoolean() ? list.mouseClicked(mouseX, mouseY, button)
                : machine.mouseClicked(mouseX, mouseY, button);
    }

    /** Cliques nas partes desenhadas (pontos, linhas) do Mapa e dos Fluxos; a Máquina e as Redes tratam os delas antes. */
    boolean mouseClicked(Tab tab, double mouseX, double mouseY) {
        return switch (tab) {
            case MAP -> map.mouseClicked(mouseX, mouseY);
            case FLOWS -> flows.mouseClicked(mouseX, mouseY);
            case LIST, NETWORKS -> false;
        };
    }

    /** O arrasto do mouse: o Mapa (mover o mapa) e as janelas da aba Redes. */
    boolean mouseDragged(Tab tab, double mouseX, double mouseY, double dragX, double dragY) {
        return tab == Tab.MAP && map.mouseDragged(mouseX, mouseY, dragX, dragY)
                || tab == Tab.NETWORKS && networks.mouseDragged(mouseX, mouseY);
    }

    /** Soltar o botão: o Mapa (o clique no mapa vale ao soltar) e as janelas da aba Redes. */
    boolean mouseReleased(Tab tab, double mouseX, double mouseY) {
        return tab == Tab.MAP && map.mouseReleased(mouseX, mouseY)
                || tab == Tab.NETWORKS && networks.mouseReleased(mouseX, mouseY);
    }

    void mouseScrolled(Tab tab, double mouseX, double mouseY, double scrollY) {
        int step = -(int) Math.signum(scrollY);
        switch (tab) {
            case LIST -> {
                if (legacy.getAsBoolean()) {
                    list.mouseScrolled(mouseX, mouseY, scrollY);
                } else {
                    machine.mouseScrolled(mouseX, mouseY, scrollY);
                }
            }
            case MAP -> map.mouseScrolled(mouseX, mouseY, scrollY);
            case FLOWS -> flows.mouseScrolled(step);
            case NETWORKS -> networks.mouseScrolled(mouseX, mouseY, scrollY);
        }
    }

    @Nullable Component tooltip(Tab tab, int mouseX, int mouseY) {
        return switch (tab) {
            case LIST -> legacy.getAsBoolean() ? list.tooltip(mouseX, mouseY) : machine.tooltip(mouseX, mouseY);
            case MAP -> map.tooltip(mouseX, mouseY);
            case FLOWS -> flows.tooltip(mouseX, mouseY);
            case NETWORKS -> networks.tooltip(mouseX, mouseY);
        };
    }

    void render(Tab tab, GuiGraphics g, Font font, int mouseX, int mouseY) {
        switch (tab) {
            case LIST -> {
                if (legacy.getAsBoolean()) {
                    list.render(g, mouseX, mouseY);
                } else {
                    machine.render(g, font, mouseX, mouseY);
                }
            }
            case MAP -> map.render(g, font, mouseX, mouseY);
            case FLOWS -> flows.render(g, font, mouseX, mouseY);
            case NETWORKS -> networks.render(g, font, mouseX, mouseY);
        }
    }
}
