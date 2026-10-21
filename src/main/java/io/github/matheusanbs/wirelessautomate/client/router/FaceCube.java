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
 * O quadro das faces do painel do nó: o cubo desdobrado pelo {@link FaceNet} num quadro afundado, um botão por lado
 * da máquina (o botão é o modo, como na configuração de lados do Mekanism), com a bolinha da rede e o Olho do
 * roteador, mais Limpar, Copiar e Colar. As áreas e o clique ficam no {@link PanelLayout}.
 */
final class FaceCube {
    private final PanelModel model;
    private final PanelLayout layout;

    FaceCube(PanelModel model, PanelLayout layout) {
        this.model = model;
        this.layout = layout;
    }

    /**
     * O quadro, os seis botões e os três botões de ação; {@code pointed} é a face sob o mouse no visor da tela,
     * realçada como a sob o mouse; {@code clearArmed} é o Limpar esperando o segundo clique.
     */
    void render(GuiGraphics g, Font font, int mx, int my, @Nullable Direction pointed, boolean clearArmed) {
        if (layout.netW <= 0) {
            return;
        }
        GuiPaint.inset(g, layout.netX, layout.netY, layout.netW, layout.netH);
        for (Hot hot : layout.hots) {
            if (hot.kind() == Kind.FACE) {
                RemoteSide side = FaceNet.SIDES[hot.arg()];
                Direction direction = model.faceOf(side);
                renderCell(g, font, hot, side, direction, hot.contains(mx, my) || direction == pointed);
            }
        }
        boolean editable = model.readOnly() == null;
        action(g, font, layout.hot(Kind.CLEAR), clearArmed ? PanelModel.panelTr("clear.armed")
                : PanelModel.panelTr("clear"), mx, my, editable, clearArmed);
        action(g, font, layout.hot(Kind.COPY), PanelModel.panelTr("copy"), mx, my, model.canCopy(), false);
        action(g, font, layout.hot(Kind.PASTE), PanelModel.panelTr("paste"), mx, my, model.canPaste(), false);
    }

    private static void action(GuiGraphics g, Font font, @Nullable Hot hot, Component label, int mx, int my,
            boolean enabled, boolean armed) {
        if (hot != null) {
            PanelPainter.textButton(g, font, hot, label, hot.contains(mx, my), enabled, armed);
        }
    }

    /**
     * Uma face do cubo. Com modo: fundo na cor do modo, borda de grafite, o sprite da porta e a letra do lado. Sem
     * modo: afundada, só a letra. A bolinha da rede fica embaixo à direita e, na face onde o roteador está preso, o
     * Olho na cor do tier em cima. Com modo e sem rede, a borda é tracejada em coral; a escolhida tem a borda dupla.
     */
    private void renderCell(GuiGraphics g, Font font, Hot hot, RemoteSide side, Direction direction, boolean hovered) {
        FaceView v = model.view(direction);
        boolean lit = v.available() || model.remote();
        boolean selected = direction == model.face();
        boolean routerHere = model.attached(direction);
        PortMode mode = lit ? v.mode() : PortMode.NONE;
        int x = hot.x();
        int y = hot.y();
        int w = hot.w();
        int h = hot.h();
        boolean withMode = mode != PortMode.NONE;
        int fill = withMode ? GuiPaint.mix(GuiPaint.BUTTON, GuiPaint.modeColor(mode), 0.28f) : GuiPaint.INSET;
        int border = withMode ? GuiPaint.FG : hovered && lit ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BEVEL_DARK;
        if (hovered && withMode) {
            border = GuiPaint.BUTTON_HOVER_BORDER;
        }
        GuiPaint.box(g, x, y, w, h, fill, border);
        boolean ask = model.needsNetwork(v);
        if (ask) {
            dashed(g, x, y, w, h, GuiPaint.ACCENT);
        }
        if (selected) {
            GuiPaint.box(g, x, y, w, h, fill, GuiPaint.ACCENT);
            GuiPaint.outline(g, x + 1, y + 1, w - 2, h - 2, GuiPaint.ACCENT);
        }
        Component letter = PanelModel.tr("side.letter." + side.key());
        int textY = y + (h - 8) / 2 + 1;
        if (withMode) {
            // o sprite de 16 px a 3/4 (12 px): ao lado da letra, ou sozinho no botão estreito (o lado vai na dica)
            boolean withLetter = w >= 28;
            g.pose().pushPose();
            g.pose().translate(withLetter ? x + 2 : x + (w - 12) / 2, y + (h - 12) / 2f, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            GuiPaint.port(g, mode, 0, 0, 1f);
            g.pose().popPose();
            if (withLetter) {
                int letterX = x + 2 + 12 + 2;
                GuiText.draw(g, font, letter, letterX, textY, x + w - 1 - letterX, GuiPaint.FG);
            }
        } else {
            GuiPaint.textCentered(g, font, letter, x + w / 2 + 1, textY, lit ? GuiPaint.MUTED : GuiPaint.DISABLED);
        }
        NetworkEntry entry = model.network(direction);
        if (entry != null) {
            GuiPaint.dot(g, x + w - 7, y + h - 7, 0xFF000000 | entry.color());
        }
        if (routerHere) {
            GuiPaint.eye(g, x + w - 7, y + 2, GuiPaint.tierColor(model.snapshot().tier()));
        }
    }

    /** Contorno tracejado de 1 px. */
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
