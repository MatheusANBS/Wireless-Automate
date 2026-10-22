package io.github.matheusanbs.wirelessautomate.client.linker;

import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.linker.LinkerBox;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot.Dot;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * O corpo da tela do Vinculador, na coluna da direita: a prévia de cima da caixa marcada com as máquinas
 * vinculadas dentro dela, os cantos e a contagem (ou o que impede, ou o resultado do último Vincular).
 */
final class AreaMap {
    private final LinkerScreen screen;

    AreaMap(LinkerScreen screen) {
        this.screen = screen;
    }

    /** Região do mundo (x, z) mostrada na prévia e a escala, montadas a cada quadro. */
    private record MapView(int left, int top, int width, int height, double centerX, double centerZ, double scale) {
        int sx(double worldX) {
            return left + width / 2 + (int) Math.floor((worldX - centerX) * scale);
        }

        int sz(double worldZ) {
            return top + height / 2 + (int) Math.floor((worldZ - centerZ) * scale);
        }
    }

    private static MapView mapView(LinkerSnapshot s, int left, int top, int width, int height) {
        LinkerBox box = s.box();
        double minX;
        double maxX;
        double minZ;
        double maxZ;
        if (box != null) {
            minX = box.minX();
            maxX = box.maxX() + 1;
            minZ = box.minZ();
            maxZ = box.maxZ() + 1;
        } else if (s.first().isPresent()) {
            BlockPos c = s.first().get();
            minX = c.getX() - 15;
            maxX = c.getX() + 16;
            minZ = c.getZ() - 15;
            maxZ = c.getZ() + 16;
        } else {
            minX = minZ = -16;
            maxX = maxZ = 16;
        }
        double margin = Math.max(1.5, Math.max(maxX - minX, maxZ - minZ) * 0.12);
        double spanX = maxX - minX + 2 * margin;
        double spanZ = maxZ - minZ + 2 * margin;
        double scale = Math.min((width - 4) / spanX, (height - 4) / spanZ);
        return new MapView(left, top, width, height, (minX + maxX) / 2, (minZ + maxZ) / 2, scale);
    }

    void render(GuiGraphics g, LinkerSnapshot s, int x, int y) {
        int mapW = screen.rw();
        int mh = screen.mapH();
        renderMap(g, s, x, y, mapW, mh);
        renderStatus(g, s, x, y, mh);
    }

    private void renderMap(GuiGraphics g, LinkerSnapshot s, int x, int y, int mapW, int mh) {
        Font font = Minecraft.getInstance().font;
        g.fill(x + 1, y + 1, x + mapW - 1, y + mh - 1, GuiPaint.VIEW_PAPER);
        GuiPaint.outline(g, x, y, mapW, mh, GuiPaint.LINE);
        MapView view = mapView(s, x, y, mapW, mh);
        g.enableScissor(x + 1, y + 1, x + mapW - 1, y + mh - 1);
        if (s.first().isPresent()) {
            // grade dos chunks: um quadrado = 16 blocos (agrupa se ficar apertada)
            int step = 16;
            while (step * view.scale() < 6) {
                step *= 2;
            }
            double halfW = mapW / 2.0 / view.scale();
            double halfH = mh / 2.0 / view.scale();
            int fromX = Math.floorDiv((int) Math.floor(view.centerX() - halfW), step) * step;
            int fromZ = Math.floorDiv((int) Math.floor(view.centerZ() - halfH), step) * step;
            for (int wx = fromX; wx <= view.centerX() + halfW; wx += step) {
                int sx = view.sx(wx);
                g.fill(sx, y + 1, sx + 1, y + mh - 1, GuiPaint.VIEW_GRID);
            }
            for (int wz = fromZ; wz <= view.centerZ() + halfH; wz += step) {
                int sz = view.sz(wz);
                g.fill(x + 1, sz, x + mapW - 1, sz + 1, GuiPaint.VIEW_GRID);
            }
            LinkerBox box = s.box();
            int color = s.unlink() ? GuiPaint.DANGER : GuiPaint.ACCENT;
            if (box != null) {
                int bx0 = view.sx(box.minX());
                int bz0 = view.sz(box.minZ());
                int bx1 = Math.max(bx0 + 1, view.sx(box.maxX() + 1));
                int bz1 = Math.max(bz0 + 1, view.sz(box.maxZ() + 1));
                g.fill(bx0, bz0, bx1, bz1, (color & 0x00FFFFFF) | 0x26000000);
                dashedRect(g, bx0, bz0, bx1, bz1, color);
            }
            // os cantos embaixo dos pontos: um roteador no canto continua visível
            corner(g, font, view, s.first().get(), "1");
            s.second().ifPresent(second -> corner(g, font, view, second, "2"));
            renderDots(g, s, view);
        } else {
            Component hint = s.otherDimension() ? tr("problem.other_dimension") : tr("map.empty");
            List<FormattedCharSequence> lines = font.split(hint, mapW - 16);
            int ty = y + mh / 2 - lines.size() * 5;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + (mapW - font.width(line)) / 2, ty, GuiPaint.MUTED, false);
                ty += 10;
            }
        }
        g.disableScissor();
        if (s.first().isPresent()) {
            int step = 16;
            while (step * view.scale() < 6) {
                step *= 2;
            }
            Component scaleText = tr("map.scale", step);
            GuiPaint.textRight(g, font, scaleText, x + mapW - 3, y + mh - 10, GuiPaint.DISABLED);
        }
    }

    private static void renderDots(GuiGraphics g, LinkerSnapshot s, MapView view) {
        int size = view.scale() >= 3 ? 5 : 3;
        for (Dot dot : s.machines()) {
            int dx = view.sx(dot.x() + 0.5) - size / 2;
            int dz = view.sz(dot.z() + 0.5) - size / 2;
            g.fill(dx - 1, dz - 1, dx + size + 1, dz + size + 1, GuiPaint.RING);
            g.fill(dx, dz, dx + size, dz + size, GuiPaint.ACCENT);
        }
    }

    /** Os cantos e, embaixo, o estado (contagem ou o que impede) e o detalhe ou o resultado do último Vincular. */
    private void renderStatus(GuiGraphics g, LinkerSnapshot s, int x, int y, int mh) {
        Font font = Minecraft.getInstance().font;
        int rw = screen.rw();
        // cantos, logo abaixo do mapa
        int cy = y + mh + 4;
        cornerLine(g, font, x, cy, tr("corner1"), s.first());
        cornerLine(g, font, x, cy + 10, tr("corner2"), s.second());

        int sy = y + mh + 25;
        if (s.problem() != LinkerProblem.NONE) {
            GuiText.wrap(g, font, LinkerText.status(s), x, sy, rw, 2, LinkerText.statusColor(s));
            return;
        }
        // depois do Vincular, o resultado toma o lugar da contagem
        Component head = s.outcome().map(LinkerText::outcomeText).orElse(LinkerText.status(s));
        Component detail = s.outcome().isPresent() ? LinkerText.outcomeDetail(s.outcome().get()) : LinkerText.detail(s);
        // sem detalhe, o resultado pode ocupar as duas linhas (até o botão)
        GuiText.wrap(g, font, head, x, sy, rw, detail == null ? 2 : 1, s.outcome().isPresent() ? GuiPaint.OK : GuiPaint.FG);
        if (detail != null) {
            boolean unloaded = s.outcome().map(o -> o.unloadedChunks() > 0).orElse(s.unloadedChunks() > 0);
            GuiText.draw(g, font, detail, x, sy + 11, rw, unloaded ? GuiPaint.WARN : GuiPaint.MUTED);
        }
    }

    private static void cornerLine(GuiGraphics g, Font font, int x, int y, Component label, Optional<BlockPos> pos) {
        GuiPaint.text(g, font, label, x, y, GuiPaint.MUTED);
        Component value = pos.<Component>map(p -> Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ()))
                .orElse(Component.literal("—"));
        GuiPaint.text(g, font, value, x + Math.max(font.width(tr("corner1")), font.width(tr("corner2"))) + 6, y,
                pos.isPresent() ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    private static void corner(GuiGraphics g, Font font, MapView view, BlockPos pos, String label) {
        int cx = view.sx(pos.getX() + 0.5);
        int cz = view.sz(pos.getZ() + 0.5);
        g.fill(cx - 3, cz - 3, cx + 4, cz + 4, GuiPaint.RING);
        g.fill(cx - 2, cz - 2, cx + 3, cz + 3, GuiPaint.ACCENT);
        g.drawString(font, label, cx + 5, cz - 9, GuiPaint.FG, false);
    }

    /** Retângulo tracejado (traços de 4 px, folgas de 2 px). */
    private static void dashedRect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        for (int i = x0; i < x1; i += 6) {
            g.fill(i, y0, Math.min(i + 4, x1), y0 + 1, color);
            g.fill(i, y1 - 1, Math.min(i + 4, x1), y1, color);
        }
        for (int i = y0; i < y1; i += 6) {
            g.fill(x0, i, x0 + 1, Math.min(i + 4, y1), color);
            g.fill(x1 - 1, i, x1, Math.min(i + 4, y1), color);
        }
    }
}
