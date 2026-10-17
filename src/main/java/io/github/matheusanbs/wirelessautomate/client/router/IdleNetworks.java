package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * O botão "Tirar a rede das faces sem modo" na tela: quando ele aparece (pelo snapshot que a tela já tem),
 * os textos e o ícone. Ele fica na linha Editando do painel ({@link TargetLine}) e, com a lista das máquinas
 * à vista, entre os botões da linha do alvo na lista ({@link RangeRowButtons}).
 */
final class IdleNetworks {
    /** O ícone em 6 × 6 px, linha a linha ({@code #} aceso): o círculo cortado, "sem rede". */
    private static final String[] ICON = {".####.", "#...##", "#..#.#", "#.#..#", "##...#", ".####."};
    static final int ICON_SIZE = 6;

    private IdleNetworks() {
    }

    /**
     * O alvo do snapshot é um grupo ou uma máquina com exceção e tem ao menos uma face, de qualquer tipo
     * carregado, com modo Nenhum e com rede.
     */
    static boolean shown(RouterSnapshot snapshot) {
        RangeView range = snapshot.range();
        EditTarget target = snapshot.target();
        if (!range.present()) {
            return false;
        }
        boolean ownFaces = switch (target.kind()) {
            case ATTACHED -> false;
            case GROUP -> range.group(target.id()) != null;
            case MACHINE -> {
                RangeView.MachineView machine = range.machine(target.id());
                yield machine != null && machine.exception();
            }
        };
        if (!ownFaces) {
            return false;
        }
        for (ResourceType type : LoadedTypes.LIST) {
            for (Direction side : Direction.values()) {
                FaceView face = snapshot.face(type, side);
                if (face.mode() == PortMode.NONE && face.network().isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** O rótulo curto do botão. */
    static Component label() {
        return PanelModel.panelTr("idle.clear.short");
    }

    /** A dica: o nome inteiro e o que o botão faz. */
    static Component tooltip() {
        return PanelModel.panelTr("idle.clear").copy().append("\n")
                .append(PanelModel.panelTr("idle.clear.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    static void icon(GuiGraphics g, int x, int y, int color) {
        for (int row = 0; row < ICON.length; row++) {
            for (int col = 0; col < ICON[row].length(); col++) {
                if (ICON[row].charAt(col) == '#') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                }
            }
        }
    }
}
