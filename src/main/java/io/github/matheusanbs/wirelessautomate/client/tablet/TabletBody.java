package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen.Tab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O corpo da tela do Tablet: repassa o desenho, o clique nas partes desenhadas, a roda e a dica para a aba
 * aberta (Lista, Fluxos, Mapa ou Redes). Os botões fixos e os campos ficam com a tela.
 */
final class TabletBody {
    private final TabletList list;
    private final MapTab map;
    private final FlowsTab flows;
    private final NetworksTab networks;

    TabletBody(TabletList list, MapTab map, FlowsTab flows, NetworksTab networks) {
        this.list = list;
        this.map = map;
        this.flows = flows;
        this.networks = networks;
    }

    private static Font font() {
        return TabletList.font();
    }

    /** Cliques nas partes desenhadas (pontos, linhas, cores) fora da Lista, que trata os dela antes. */
    boolean mouseClicked(Tab tab, double mouseX, double mouseY) {
        return switch (tab) {
            case MAP -> map.mouseClicked(mouseX, mouseY);
            case NETWORKS -> networks.mouseClicked(font(), mouseX, mouseY);
            case FLOWS -> flows.mouseClicked(mouseX, mouseY);
            case LIST -> false;
        };
    }

    void mouseScrolled(Tab tab, double mouseX, double mouseY, double scrollY) {
        int step = -(int) Math.signum(scrollY);
        switch (tab) {
            case LIST -> list.mouseScrolled(mouseX, mouseY, scrollY);
            case MAP -> map.mouseScrolled(mouseX, mouseY, scrollY);
            case FLOWS -> flows.mouseScrolled(step);
            case NETWORKS -> networks.mouseScrolled(font(), mouseX, mouseY, step);
        }
    }

    @Nullable Component tooltip(Tab tab, int mouseX, int mouseY) {
        return switch (tab) {
            case LIST -> list.tooltip(mouseX, mouseY);
            case MAP -> map.tooltip(mouseX, mouseY);
            case FLOWS -> flows.tooltip(mouseX, mouseY);
            case NETWORKS -> networks.tooltip(font(), mouseX, mouseY);
        };
    }

    void render(Tab tab, GuiGraphics g, Font font, int mouseX, int mouseY) {
        switch (tab) {
            case LIST -> list.render(g, mouseX, mouseY);
            case MAP -> map.render(g, font, mouseX, mouseY);
            case FLOWS -> flows.render(g, font, mouseX, mouseY);
            case NETWORKS -> networks.render(g, font, mouseX, mouseY);
        }
    }
}
