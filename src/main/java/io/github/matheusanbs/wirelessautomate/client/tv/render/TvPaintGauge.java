package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * O Medidor: um arco aberto embaixo, a trilha na abertura inteira e o cheio por cima, desenhados em segmentos (um
 * quadrilátero por 3 graus entre o raio de dentro e o de fora, no mesmo buffer dos retângulos, na ordem do desenho):
 * sem textura nova. O arco começa embaixo à esquerda e gira no sentido do relógio.
 */
final class TvPaintGauge {
    private static final double STEP_DEGREES = 3;

    private TvPaintGauge() {
    }

    static void paint(GuiGraphics g, TvPiece piece, TvPartStyle part, double fraction, int fill, int track) {
        float cx = piece.x() + piece.width() / 2f;
        float cy = piece.y() + piece.height() / 2f;
        float outer = Math.min(piece.width(), piece.height()) / 2f;
        float inner = Math.max(0f, outer - part.thickness());
        double start = 90 + (360 - part.sweep()) / 2.0;
        VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = g.pose().last().pose();
        if (track >>> 24 != 0) {
            arc(buffer, matrix, cx, cy, inner, outer, start, part.sweep(), track);
        }
        if (fraction > 0 && fill >>> 24 != 0) {
            arc(buffer, matrix, cx, cy, inner, outer, start, part.sweep() * fraction, fill);
        }
    }

    /** O arco de {@code sweep} graus a partir de {@code start} (graus, no sentido do relógio na tela, 0 à direita). */
    private static void arc(VertexConsumer buffer, Matrix4f matrix, float cx, float cy, float inner, float outer,
            double start, double sweep, int color) {
        int segments = Math.max(1, (int) Math.ceil(sweep / STEP_DEGREES));
        double step = Math.toRadians(sweep) / segments;
        double angle = Math.toRadians(start);
        float cos0 = (float) Math.cos(angle);
        float sin0 = (float) Math.sin(angle);
        for (int i = 0; i < segments; i++) {
            angle += step;
            float cos1 = (float) Math.cos(angle);
            float sin1 = (float) Math.sin(angle);
            buffer.addVertex(matrix, cx + inner * cos0, cy + inner * sin0, 0f).setColor(color);
            buffer.addVertex(matrix, cx + inner * cos1, cy + inner * sin1, 0f).setColor(color);
            buffer.addVertex(matrix, cx + outer * cos1, cy + outer * sin1, 0f).setColor(color);
            buffer.addVertex(matrix, cx + outer * cos0, cy + outer * sin0, 0f).setColor(color);
            cos0 = cos1;
            sin0 = sin1;
        }
    }
}
