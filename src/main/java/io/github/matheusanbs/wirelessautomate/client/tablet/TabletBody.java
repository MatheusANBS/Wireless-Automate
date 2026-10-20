package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen.Tab;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O corpo da tela do Tablet: repassa o desenho, o clique nas partes desenhadas, a roda e a dica para a aba
 * aberta (Lista, Mapa, Fluxos, Redes ou Grupos). Os botões fixos e os campos ficam com a tela.
 */
final class TabletBody {
    private final TabletList list;
    private final MapTab map;
    private final FlowsTab flows;
    private final SideList side;
    private final NetworksTab networks;
    private final GroupsTab groups;
    /** Quantas redes a lista lateral da aba Redes tem (para a rolagem). */
    private final IntSupplier networkCount;

    TabletBody(TabletList list, MapTab map, FlowsTab flows, SideList side, NetworksTab networks, GroupsTab groups,
            IntSupplier networkCount) {
        this.list = list;
        this.map = map;
        this.flows = flows;
        this.side = side;
        this.networks = networks;
        this.groups = groups;
        this.networkCount = networkCount;
    }

    /** Cliques nas partes desenhadas (pontos, linhas, cores) fora da Lista, que trata os dela antes. */
    boolean mouseClicked(Tab tab, double mouseX, double mouseY) {
        switch (tab) {
            case MAP -> {
                return map.mouseClicked(mouseX, mouseY);
            }
            case NETWORKS -> {
                return networks.mouseClicked(mouseX, mouseY);
            }
            case GROUPS -> {
                return groups.mouseClicked(mouseX, mouseY);
            }
            case FLOWS -> {
                return flows.mouseClicked(mouseX, mouseY);
            }
            case LIST -> {
            }
        }
        return false;
    }

    void mouseScrolled(Tab tab, double mouseX, double mouseY, double scrollY) {
        int step = -(int) Math.signum(scrollY);
        switch (tab) {
            case LIST -> list.mouseScrolled(mouseX, mouseY, scrollY);
            case MAP -> map.mouseScrolled(mouseX, mouseY, scrollY);
            case FLOWS -> flows.mouseScrolled(step);
            case NETWORKS -> side.mouseScrolled(step, networkCount.getAsInt());
            case GROUPS -> groups.mouseScrolled(mouseX, step);
        }
    }

    @Nullable Component tooltip(Tab tab, int mouseX, int mouseY) {
        return switch (tab) {
            case LIST -> list.tooltip(mouseX, mouseY);
            case MAP -> map.tooltip(mouseX, mouseY);
            case FLOWS -> flows.tooltip(mouseX, mouseY);
            case NETWORKS -> networks.tooltip(mouseX, mouseY);
            case GROUPS -> groups.tooltip(mouseX, mouseY);
        };
    }

    void render(Tab tab, GuiGraphics g, Font font, int mouseX, int mouseY) {
        switch (tab) {
            case LIST -> list.render(g, mouseX, mouseY);
            case MAP -> map.render(g, font, mouseX, mouseY);
            case FLOWS -> flows.render(g, font, mouseX, mouseY);
            case NETWORKS -> networks.render(g, font, mouseX, mouseY);
            case GROUPS -> groups.render(g, font, mouseX, mouseY);
        }
    }
}
