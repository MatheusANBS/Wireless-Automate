package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvFrame.Slice;

/**
 * A moldura da Tela maior que 1 × 1, em 9 fatias do atlas pelo {@link TvFrame}: os cantos, as faixas de cima e dos
 * lados esticadas (são iguais ao longo), a de baixo repetida com o queixo enfeitado uma vez no canto de baixo à
 * direita, os quatro lados de fora repetidos por bloco, as quatro paredes de dentro (da frente da moldura até o
 * miolo, com a última linha de texels da faixa vizinha) e o miolo apagado esticado. Sem vidro. O fundo, contra a
 * parede, não é desenhado. Tudo num {@code VertexConsumer} só, sem alocar.
 */
final class TvFrameMesh {
    private static final float NORMAL_OUT = -1f;

    private TvFrameMesh() {
    }

    /** A área tem {@code w} × {@code h} unidades; {@link TvQuads#begin} já foi chamado. */
    static void render(VertexConsumer out, PoseStack.Pose pose, float w, float h, int overlay, int light) {
        float band = TvFrame.BAND;
        float low = TvFrame.BAND_BOTTOM;
        float z = TvFrame.FRONT_Z;
        whole(out, pose, TvFrame.CORNER_TL, 0, h - band, band, h, overlay, light);
        whole(out, pose, TvFrame.CORNER_TR, w - band, h - band, w, h, overlay, light);
        whole(out, pose, TvFrame.CORNER_BL, 0, 0, band, low, overlay, light);
        whole(out, pose, TvFrame.CORNER_BR, w - band, 0, w, low, overlay, light);
        whole(out, pose, TvFrame.EDGE_TOP, band, h - band, w - band, h, overlay, light);
        whole(out, pose, TvFrame.EDGE_LEFT, 0, low, band, h - band, overlay, light);
        whole(out, pose, TvFrame.EDGE_RIGHT, w - band, low, w, h - band, overlay, light);
        float chinStart = w - band - TvFrame.CHIN.unitsWide();
        whole(out, pose, TvFrame.CHIN, chinStart, 0, w - band, low, overlay, light);
        Slice bottom = TvFrame.EDGE_BOTTOM;
        for (float vx = band; vx < chinStart; vx += bottom.unitsWide()) {
            float end = Math.min(vx + bottom.unitsWide(), chinStart);
            TvQuads.front(vx, 0, end, low, z, bottom.u0(), bottom.v0(),
                    bottom.u((end - vx) * TvFrame.PIXELS_PER_UNIT), bottom.v1());
            TvQuads.solid(out, pose, 0, 0, NORMAL_OUT, overlay, light);
        }
        sides(out, pose, w, h, overlay, light);
        inner(out, pose, w, h, overlay, light);
        whole(out, pose, TvFrame.MIDDLE, band, low, w - band, h - band, TvFrame.SCREEN_Z, overlay, light);
    }

    private static void whole(VertexConsumer out, PoseStack.Pose pose, Slice slice, float vx0, float vy0, float vx1,
            float vy1, int overlay, int light) {
        whole(out, pose, slice, vx0, vy0, vx1, vy1, TvFrame.FRONT_Z, overlay, light);
    }

    private static void whole(VertexConsumer out, PoseStack.Pose pose, Slice slice, float vx0, float vy0, float vx1,
            float vy1, float z, int overlay, int light) {
        TvQuads.front(vx0, vy0, vx1, vy1, z, slice.u0(), slice.v0(), slice.u1(), slice.v1());
        TvQuads.solid(out, pose, 0, 0, NORMAL_OUT, overlay, light);
    }

    /** Os lados de fora, da frente da moldura até a parede, com a fatia {@code side} repetida a cada bloco. */
    private static void sides(VertexConsumer out, PoseStack.Pose pose, float w, float h, int overlay, int light) {
        Slice side = TvFrame.SIDE;
        float front = TvFrame.FRONT_Z;
        float wall = TvFrame.WALL_Z;
        float block = TvFrame.UNITS_PER_BLOCK;
        for (float vy = 0; vy < h; vy += block) {
            float top = vy + block;
            for (int k = 0; k < 2; k++) {
                float vx = k == 0 ? 0 : w;
                TvQuads.corner(0, vx, vy, front, side.u0(), side.v1());
                TvQuads.corner(1, vx, vy, wall, side.u1(), side.v1());
                TvQuads.corner(2, vx, top, wall, side.u1(), side.v0());
                TvQuads.corner(3, vx, top, front, side.u0(), side.v0());
                TvQuads.solid(out, pose, k == 0 ? 1 : -1, 0, 0, overlay, light);
            }
        }
        for (float vx = 0; vx < w; vx += block) {
            float end = vx + block;
            for (int k = 0; k < 2; k++) {
                float vy = k == 0 ? 0 : h;
                TvQuads.corner(0, vx, vy, front, side.u0(), side.v0());
                TvQuads.corner(1, end, vy, front, side.u0(), side.v1());
                TvQuads.corner(2, end, vy, wall, side.u1(), side.v1());
                TvQuads.corner(3, vx, vy, wall, side.u1(), side.v0());
                TvQuads.solid(out, pose, 0, k == 0 ? -1 : 1, 0, overlay, light);
            }
        }
    }

    /** As paredes de dentro, da frente da moldura até o miolo, com a linha de texels da faixa que fica junto. */
    private static void inner(VertexConsumer out, PoseStack.Pose pose, float w, float h, int overlay, int light) {
        float band = TvFrame.BAND;
        float low = TvFrame.BAND_BOTTOM;
        Slice left = TvFrame.EDGE_LEFT;
        wall(left.u(left.width() - 1), left.u1(), left.v0(), left.v1(), band, low, band, h - band, true);
        TvQuads.solid(out, pose, -1, 0, 0, overlay, light);
        Slice right = TvFrame.EDGE_RIGHT;
        wall(right.u0(), right.u(1), right.v0(), right.v1(), w - band, low, w - band, h - band, true);
        TvQuads.solid(out, pose, 1, 0, 0, overlay, light);
        Slice top = TvFrame.EDGE_TOP;
        wall(top.u0(), top.u1(), top.v(top.height() - 1), top.v1(), band, h - band, w - band, h - band, false);
        TvQuads.solid(out, pose, 0, -1, 0, overlay, light);
        Slice bottom = TvFrame.EDGE_BOTTOM;
        wall(bottom.u0(), bottom.u1(), bottom.v0(), bottom.v(1), band, low, w - band, low, false);
        TvQuads.solid(out, pose, 0, 1, 0, overlay, light);
    }

    /**
     * Uma parede de dentro, de z {@link TvFrame#FRONT_Z} a {@link TvFrame#SCREEN_Z}, entre os pontos de frente
     * ({@code vx0}, {@code vy0}) e ({@code vx1}, {@code vy1}). Vertical: a fatia corre em {@code v} ao longo; senão
     * em {@code u}.
     */
    private static void wall(float u0, float u1, float v0, float v1, float vx0, float vy0, float vx1, float vy1,
            boolean vertical) {
        float front = TvFrame.FRONT_Z;
        float screen = TvFrame.SCREEN_Z;
        if (vertical) {
            TvQuads.corner(0, vx0, vy0, front, u0, v1);
            TvQuads.corner(1, vx0, vy0, screen, u1, v1);
            TvQuads.corner(2, vx1, vy1, screen, u1, v0);
            TvQuads.corner(3, vx1, vy1, front, u0, v0);
        } else {
            TvQuads.corner(0, vx0, vy0, front, u0, v0);
            TvQuads.corner(1, vx1, vy1, front, u1, v0);
            TvQuads.corner(2, vx1, vy1, screen, u1, v1);
            TvQuads.corner(3, vx0, vy0, screen, u0, v1);
        }
    }
}
