package io.github.matheusanbs.wirelessautomate.client.tablet.machine;

import static io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachineText.tr;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.PanelHeader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * O cabeçalho da máquina escolhida (mockup {@code cabecalho}): o ícone do bloco, o nome em negrito e o subtítulo "Roteador
 * Elite · a 14 blocos" (ou "noutra dimensão"), com a linha embaixo. O texto e as larguras são medidos no {@link #set}, que a
 * anfitriã chama quando chega a página, o painel ou muda o tema; o {@link #render} só desenha.
 */
final class MachineHeader {
    private final TabletLayout layout;
    private final PanelHeader header = new PanelHeader();
    private ItemStack icon = ItemStack.EMPTY;
    private boolean present;
    private String title = "";

    MachineHeader(TabletLayout layout) {
        this.layout = layout;
    }

    boolean present() {
        return present;
    }

    String title() {
        return title;
    }

    void clear() {
        present = false;
        title = "";
    }

    /**
     * @param blocks a distância do jogador à máquina em blocos; negativa quando ela está noutra dimensão
     */
    void set(Font font, ItemStack icon, String name, RouterTier tier, int blocks) {
        Component tierName = Component.translatable(tier.translationKey());
        Component subtitle = blocks < 0 ? tr("header.far", tierName)
                : tr(blocks == 1 ? "header.near.one" : "header.near", tierName, blocks);
        header.set(font, Component.literal(name), subtitle, null, 0, false,
                layout.panelX1() - TabletLayout.PANEL_X0 - 20);
        this.icon = icon;
        this.title = name;
        present = true;
    }

    /**
     * Os blocos entre o jogador e a posição, arredondados; {@code -1} noutra dimensão. Sem alocar: calculada quando a
     * página muda, não por quadro.
     */
    static int distance(BlockPos pos, ResourceKey<Level> dimension) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.level().dimension().equals(dimension)) {
            return -1;
        }
        return (int) Math.round(Math.sqrt(pos.distToCenterSqr(player.getX(), player.getY(), player.getZ())));
    }

    void render(GuiGraphics g, Font font) {
        if (!present) {
            return;
        }
        g.renderItem(icon, layout.left + TabletLayout.PANEL_X0, layout.top + 30);
        header.render(g, font, layout.left, layout.top, TabletLayout.PANEL_X0, layout.panelX1());
    }
}
