package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O desenho do cubo das faces do painel do nó, desdobrado pelo {@link FaceNet}: uma célula por lado da
 * máquina, com o modo, o nome do lado, a rede e o roteador preso. As células e o clique ficam no
 * {@link PanelLayout}.
 */
final class FaceCube {
    private final PanelModel model;
    private final PanelLayout layout;

    FaceCube(PanelModel model, PanelLayout layout) {
        this.model = model;
        this.layout = layout;
    }

    /** As seis células; {@code pointed} é a face sob o mouse no visor da tela, realçada como a sob o mouse. */
    void render(GuiGraphics g, Font font, int mx, int my, @Nullable Direction pointed) {
        if (layout.netW <= 0) {
            return;
        }
        for (Hot hot : layout.hots) {
            if (hot.kind() == Kind.FACE) {
                RemoteSide side = RemoteSide.values()[hot.arg()];
                Direction direction = model.faceOf(side);
                renderCell(g, font, hot, side, direction, hot.contains(mx, my) || direction == pointed);
            }
        }
    }

    /**
     * Uma face do cubo: tarja no topo na cor do modo, o nome do lado (curto quando aperta), a bolinha na cor
     * da rede e, na face onde o roteador está preso, o Olho na cor do tier, com o fundo apagado. Sem modo, a
     * borda é tracejada; a escolhida tem a borda de seleção.
     */
    private void renderCell(GuiGraphics g, Font font, Hot hot, RemoteSide side, Direction direction, boolean hovered) {
        FaceView v = model.view(direction);
        boolean lit = v.available() || model.remote();
        boolean selected = direction == model.face();
        boolean routerHere = model.attached(direction);
        int x = hot.x();
        int y = hot.y();
        int w = hot.w();
        int h = hot.h();
        int fill = !lit ? GuiPaint.INSET : routerHere ? GuiPaint.mix(GuiPaint.BUTTON, GuiPaint.INSET, 0.6f) : GuiPaint.BUTTON;
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        PortMode mode = lit ? v.mode() : PortMode.NONE;
        if (selected) {
            GuiPaint.box(g, x, y, w, h, fill, GuiPaint.ACCENT);
            GuiPaint.outline(g, x + 1, y + 1, w - 2, h - 2, GuiPaint.ACCENT);
        } else if (mode == PortMode.NONE) {
            dashed(g, x, y, w, h, hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        } else {
            GuiPaint.box(g, x, y, w, h, fill, hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        if (mode != PortMode.NONE) {
            int inset = selected ? 2 : 1;
            g.fill(x + inset, y + inset, x + w - inset, y + inset + 2, GuiPaint.modeColor(mode));
        }
        NetworkEntry entry = model.network(direction);
        int marks = (entry != null ? 6 : 0) + (routerHere ? 7 : 0);
        int room = w - 6 - marks;
        // na face onde o roteador está preso, "roteador" no lugar do lado (o lado vai na dica)
        Component full = routerHere ? PanelModel.panelTr("face.router") : PanelModel.sideName(side);
        Component shortName = routerHere ? PanelModel.panelTr("face.router.short")
                : PanelModel.tr("side.short." + side.key());
        Component label = switch (FaceNet.label(font.width(full), font.width(shortName), room)) {
            case FULL -> full;
            case SHORT -> shortName;
            case LETTER -> routerHere ? PanelModel.panelTr("face.router.letter") : PanelModel.tr("side.letter." + side.key());
        };
        int textY = y + Math.max(3, (h - 8) / 2 + 1);
        GuiText.draw(g, font, label, x + 3, textY, room, !lit ? GuiPaint.DISABLED : selected ? GuiPaint.FG : GuiPaint.MUTED);
        int markX = x + w - 3;
        int markY = y + Math.max(3, (h - 5) / 2 + 1);
        if (routerHere) {
            markX -= 6;
            GuiPaint.eye(g, markX, markY, GuiPaint.tierColor(model.snapshot().tier()));
        }
        if (entry != null) {
            markX -= 6;
            GuiPaint.dot(g, markX, markY, 0xFF000000 | entry.color());
        }
    }

    /** Contorno tracejado de 1 px (face sem modo). */
    private static void dashed(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int i = 1; i < w - 1; i += 3) {
            int end = Math.min(i + 2, w - 1);
            g.fill(x + i, y, x + end, y + 1, color);
            g.fill(x + i, y + h - 1, x + end, y + h, color);
        }
        for (int i = 1; i < h - 1; i += 3) {
            int end = Math.min(i + 2, h - 1);
            g.fill(x, y + i, x + 1, y + end, color);
            g.fill(x + w - 1, y + i, x + w, y + end, color);
        }
    }
}
