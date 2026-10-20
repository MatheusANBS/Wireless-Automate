package io.github.matheusanbs.wirelessautomate.client.tablet;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.client.router.RangeList;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineState;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * As máquinas do roteador escolhido no Mapa do Tablet: um quadradinho por máquina, na posição que já vem no
 * {@link RangeView} do painel (nada novo do servidor), ligado ao roteador por uma linha tracejada de 1 px na cor
 * neutra do tema; as acesas pelo Olho em ciano e a máquina-alvo do painel com o contorno, como o nó escolhido.
 * Clicar numa máquina a escolhe como alvo do painel ({@link SelectTargetPayload}). Só aparecem com o roteador na
 * dimensão do mapa (as máquinas são sempre da dimensão do roteador).
 * <p>
 * Os pontos e os pixels das linhas são montados só quando o painel, a vista (centro, escala, retângulo do mapa) ou o
 * ponto do roteador mudam; as acesas, quando a versão delas muda. Por quadro, só desenhar: tudo num buffer só, com
 * as cores do tema lidas na hora.
 */
final class MapMachines {
    /** Meia aresta do quadradinho: 4 × 4 px. */
    private static final int HALF = 2;
    /** Distância máxima do mouse ao centro para pegar uma máquina, ao quadrado. */
    private static final int PICK = 4 * 4;
    /** O limite de um ponto projetado, em px, bem fora de qualquer mapa. */
    private static final double FAR = 1_000_000;

    private final MapLines lines = new MapLines();
    private @Nullable TabletPanelPayload built;
    private long builtCenter;
    private double builtScale;
    private int builtX0;
    private int builtY0;
    private int builtW;
    private int builtH;
    private int litVersion = -1;

    private @Nullable RangeView view;
    /** As máquinas à vista dentro do mapa: o índice delas no {@link RangeView} e o ponto. */
    private int count;
    private int[] index = new int[0];
    private int[] px = new int[0];
    private int[] py = new int[0];
    private boolean[] lit = new boolean[0];
    private int target = -1;
    private @Nullable TabletPanelPayload tipBuilt;
    private int tipIndex = -1;
    private int tipTheme = -1;
    private @Nullable TabletPanelPayload infoBuilt;
    private int infoTheme = -1;
    private Component infoState = Component.empty();
    private int infoStateW;
    private int infoStateColor;
    private Component infoText = Component.empty();
    private @Nullable Component tip;

    /**
     * Põe os pontos em dia com o painel e a vista. {@code router} é o nó do painel, já na dimensão do mapa (ou
     * {@code null}: nada a mostrar).
     */
    void prepare(@Nullable TabletPanelPayload payload, @Nullable NodeView router, BlockPos center, double scale,
            int x0, int y0, int w, int h) {
        if (payload == null || router == null) {
            built = null;
            view = null;
            count = 0;
            target = -1;
            lines.clear();
            litVersion = -1;
            return;
        }
        if (payload == built && center.asLong() == builtCenter && scale == builtScale && x0 == builtX0
                && y0 == builtY0 && w == builtW && h == builtH) {
            refreshLit(router);
            return;
        }
        built = payload;
        builtCenter = center.asLong();
        builtScale = scale;
        builtX0 = x0;
        builtY0 = y0;
        builtW = w;
        builtH = h;
        rebuild(payload, router.key().pos(), center, scale, x0, y0, w, h);
        litVersion = -1;
        refreshLit(router);
    }

    private void rebuild(TabletPanelPayload payload, BlockPos router, BlockPos center, double scale, int x0, int y0,
            int w, int h) {
        count = 0;
        target = -1;
        lines.clear();
        RouterSnapshot snapshot = payload.snapshot().orElse(null);
        view = snapshot == null || !snapshot.range().present() ? null : snapshot.range();
        if (view == null) {
            return;
        }
        int n = view.machines().size();
        if (index.length < n) {
            index = new int[n];
            px = new int[n];
            py = new int[n];
            lit = new boolean[n];
        }
        int cx = x0 + w / 2;
        int cy = y0 + h / 2;
        int minX = x0 + 1;
        int minY = y0 + 1;
        int maxX = x0 + w - 2;
        int maxY = y0 + h - 2;
        EditTarget editing = view.target();
        // a linha parte da posição real do roteador, mesmo fora do mapa (o ponto dele fica preso à borda)
        int routerX = project(cx, router.getX(), center.getX(), scale);
        int routerY = project(cy, router.getZ(), center.getZ(), scale);
        for (int i = 0; i < n; i++) {
            BlockPos pos = view.machines().get(i).pos();
            int x = project(cx, pos.getX(), center.getX(), scale);
            int y = project(cy, pos.getZ(), center.getZ(), scale);
            lines.dashed(routerX, routerY, x, y, minX, minY, maxX, maxY);
            if (x - HALF - 2 < minX || x + HALF + 2 > maxX || y - HALF - 2 < minY || y + HALF + 2 > maxY) {
                continue;
            }
            if (editing.kind() == EditTarget.Kind.MACHINE && editing.id() == i) {
                target = count;
            }
            index[count] = i;
            px[count] = x;
            py[count] = y;
            count++;
        }
    }

    /**
     * O pixel de uma coordenada no mapa, limitado a {@link #FAR} px para cada lado: no zoom máximo, longe da origem,
     * o valor passaria do {@code int} e voltaria para dentro do mapa.
     */
    private static int project(int middle, int coord, int centerCoord, double scale) {
        return (int) Math.round(Math.clamp(middle + (coord - centerCoord) / scale, -FAR, FAR));
    }

    private void refreshLit(NodeView router) {
        int version = HighlightRenderer.version();
        if (version == litVersion) {
            return;
        }
        litVersion = version;
        Arrays.fill(lit, false);
        for (int k = 0; k < count; k++) {
            lit[k] = HighlightRenderer.isLit(router.key().dimension(), view.machines().get(index[k]).pos().asLong());
        }
    }

    /** O ponto (na lista à vista) da máquina sob o mouse, ou -1; num empate, a máquina-alvo ({@link MapPick}). */
    int at(double mouseX, double mouseY) {
        return MapPick.nearest(px, py, count, mouseX, mouseY, PICK, target);
    }

    /** O clique escolhe a máquina sob o mouse como alvo do painel; devolve se havia uma. */
    boolean click(double mouseX, double mouseY, TabletActions actions) {
        int k = at(mouseX, mouseY);
        if (k < 0) {
            return false;
        }
        int i = index[k];
        actions.send(SelectTargetPayload.machine(actions.containerId(), i, view.machines().get(i).pos()));
        return true;
    }

    /** O centro da máquina {@code machine} (índice do {@link RangeView}) no mapa, ou {@code null} (gancho do e2e). */
    int @Nullable [] center(int machine) {
        for (int k = 0; k < count; k++) {
            if (index[k] == machine) {
                return new int[] {px[k], py[k]};
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ desenho

    /** As linhas e os quadradinhos, num buffer só; antes dos pontos dos nós, que ficam por cima. */
    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (count == 0 && lines.size() == 0) {
            return;
        }
        int hovered = at(mouseX, mouseY);
        VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = g.pose().last().pose();
        int lineColor = GuiPaint.MUTED;
        for (int i = 0; i < lines.size(); i++) {
            quad(buffer, pose, lines.x(i), lines.y(i), lines.x(i) + 1, lines.y(i) + 1, lineColor);
        }
        for (int k = 0; k < count; k++) {
            int x = px[k];
            int y = py[k];
            if (k == target || k == hovered) {
                ring(buffer, pose, x - HALF - 2, y - HALF - 2, x + HALF + 2, y + HALF + 2,
                        k == target ? GuiPaint.FG : GuiPaint.MUTED);
            }
            if (lit[k]) {
                ring(buffer, pose, x - HALF - 1, y - HALF - 1, x + HALF + 1, y + HALF + 1, GuiPaint.HIGHLIGHT);
                quad(buffer, pose, x - HALF, y - HALF, x + HALF, y + HALF, GuiPaint.HIGHLIGHT);
            } else {
                quad(buffer, pose, x - HALF, y - HALF, x + HALF, y + HALF,
                        fill(view.machines().get(index[k]).state()));
            }
        }
        g.flush();
    }

    /** A cor do quadradinho: a neutra na que está bem, a do estado nas outras. */
    private static int fill(MachineState state) {
        return state == MachineState.LOADED ? GuiPaint.MUTED : RangeList.stateColor(state);
    }

    private static void ring(VertexConsumer buffer, Matrix4f pose, int x0, int y0, int x1, int y1, int color) {
        quad(buffer, pose, x0, y0, x1, y0 + 1, color);
        quad(buffer, pose, x0, y1 - 1, x1, y1, color);
        quad(buffer, pose, x0, y0 + 1, x0 + 1, y1 - 1, color);
        quad(buffer, pose, x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    private static void quad(VertexConsumer buffer, Matrix4f pose, int x0, int y0, int x1, int y1, int color) {
        buffer.addVertex(pose, x0, y0, 0).setColor(color);
        buffer.addVertex(pose, x0, y1, 0).setColor(color);
        buffer.addVertex(pose, x1, y1, 0).setColor(color);
        buffer.addVertex(pose, x1, y0, 0).setColor(color);
    }

    // ------------------------------------------------------------------ textos

    /**
     * A faixa de info com a máquina-alvo do painel no lugar do roteador: o nome e as coordenadas, e o estado curto à
     * direita (vazio na que está bem). Os textos são montados quando o painel ou o tema mudam. Devolve se desenhou
     * (sem máquina-alvo, a faixa é a do nó).
     */
    boolean renderInfo(GuiGraphics g, Font font, int x0, int infoY, int w) {
        int machine = view == null || view.target().kind() != EditTarget.Kind.MACHINE ? -1 : view.target().id();
        if (machine < 0 || view.machine(machine) == null) {
            return false;
        }
        if (infoBuilt != built || infoTheme != GuiThemes.version()) {
            buildInfo(font, machine);
        }
        GuiPaint.box(g, x0, infoY, w - 50, 16, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
        GuiPaint.text(g, font, infoState, x0 + w - 56 - infoStateW, infoY + 4, infoStateColor);
        GuiText.draw(g, font, infoText, x0 + 5, infoY + 4, w - 66 - infoStateW, GuiPaint.FG);
        return true;
    }

    /** Os textos da faixa de info da máquina {@code machine}, montados quando o painel ou o tema mudam. */
    private void buildInfo(Font font, int machine) {
        infoBuilt = built;
        infoTheme = GuiThemes.version();
        MachineState state = view.machines().get(machine).state();
        infoState = switch (state) {
            case LOADED -> Component.empty();
            case REPLACED -> Component.translatable("gui.wirelessautomate.router.range.machine.replaced");
            default -> Component.translatable("gui.wirelessautomate.router.range.machine.short."
                    + state.name().toLowerCase(Locale.ROOT));
        };
        infoStateW = font.width(infoState);
        infoStateColor = RangeList.stateColor(state);
        infoText = RangeList.machineName(view, machine).copy()
                .append(Component.literal("  ").append(coords(machine)).withColor(GuiPaint.MUTED));
    }

    private Component coords(int machine) {
        BlockPos pos = view.machines().get(machine).pos();
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    /** A dica da máquina sob o mouse: nome, coordenadas, estado e o que o clique faz; ou {@code null}. */
    @Nullable Component tooltip(double mouseX, double mouseY) {
        int k = at(mouseX, mouseY);
        if (k < 0) {
            return null;
        }
        int i = index[k];
        if (tipBuilt == built && tipIndex == i && tipTheme == GuiThemes.version() && tip != null) {
            return tip;
        }
        MachineView machine = view.machines().get(i);
        MutableComponent text = RangeList.machineName(view, i).copy();
        text.append("\n").append(coords(i).copy().withColor(GuiPaint.TOOLTIP_MUTED));
        text.append("\n").append(RangeList.stateText(machine.state()).copy().withColor(RangeList.stateColor(machine.state())));
        text.append("\n").append(Component.translatable("gui.wirelessautomate.router.range.machine.tooltip")
                .copy().withColor(GuiPaint.TOOLTIP_MUTED));
        tipBuilt = built;
        tipIndex = i;
        tipTheme = GuiThemes.version();
        tip = text;
        return text;
    }

    /** Quantas máquinas e pixels de linha o mapa desenha (gancho do e2e). */
    int drawn() {
        return count + lines.size();
    }
}
