package io.github.matheusanbs.wirelessautomate.client.dock.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * As caixas da Doca, como os cubos do Blockbench: em pixels do modelo (0 a 16), cada face com o recorte dela no atlas
 * de 64 × 64 do tier (o mesmo {@code pattern_dock/<tier>} do modelo do bloco). As transformações de cada peça vão
 * numa matriz só ({@link #matrix}, refeita a partir da do bloco por {@link #reset}) e os vértices saem já
 * transformados, pela forma longa do {@code addVertex}: nenhum objeto por vértice nem por quadro. Só na thread de
 * render.
 */
final class DockBoxes {
    private static final float PIXEL = 1 / 16f;
    private static final float ATLAS = 64;
    private static final int WHITE = 0xFFFFFFFF;
    /** As faces na ordem do {@code Direction}: baixo, cima, norte, sul, oeste, leste. */
    static final int FACES = 6;

    private static final Matrix4f BASE = new Matrix4f();
    private static final Matrix3f BASE_NORMAL = new Matrix3f();
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Matrix3f NORMAL = new Matrix3f();
    private static final Vector3f POINT = new Vector3f();
    private static final Vector3f FACING = new Vector3f();
    private static final float[] CORNERS = new float[12];

    private static VertexConsumer out;
    private static TextureAtlasSprite sprite;
    private static int worldLight;
    private static int light;

    private DockBoxes() {
    }

    /** O começo de um bloco: a matriz dele em pixels, o buffer, o atlas do tier e a luz do mundo. */
    static void begin(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite atlas, int packedLight) {
        BASE.set(pose.pose()).scale(PIXEL);
        BASE_NORMAL.set(pose.normal());
        out = buffer;
        sprite = atlas;
        worldLight = packedLight;
        light = packedLight;
    }

    /** As próximas caixas com a luz cheia (acesas, brilham no escuro) ou com a luz do mundo. */
    static void glow(boolean on) {
        light = on ? LightTexture.FULL_BRIGHT : worldLight;
    }

    /** A matriz da peça volta à do bloco. */
    static Matrix4f reset() {
        NORMAL.set(BASE_NORMAL);
        return MATRIX.set(BASE);
    }

    /** Gira a peça em torno de {@code (cx, cy, cz)}: x, depois y, depois z (a ordem ZYX do Blockbench), em graus. */
    static void rotate(float cx, float cy, float cz, double x, double y, double z) {
        float rx = (float) Math.toRadians(x);
        float ry = (float) Math.toRadians(y);
        float rz = (float) Math.toRadians(z);
        MATRIX.translate(cx, cy, cz).rotateZ(rz).rotateY(ry).rotateX(rx).translate(-cx, -cy, -cz);
        NORMAL.rotateZ(rz).rotateY(ry).rotateX(rx);
    }

    /**
     * Gira a peça como um keyframe de rotação do Blockbench em torno do pivô do grupo, com os sinais como estão: o
     * GIF de referência bate com eles (a soma das diferenças pixel a pixel, nos 20 quadros conferidos, fica menor que
     * com o x e o y trocados de sinal, a convenção do formato Bedrock).
     */
    static void keyframe(float cx, float cy, float cz, double x, double y, double z) {
        rotate(cx, cy, cz, x, y, z);
    }

    /** Escala a peça em torno de {@code (cx, cy, cz)}. */
    static void scale(float cx, float cy, float cz, float s) {
        MATRIX.translate(cx, cy, cz).scale(s).translate(-cx, -cy, -cz);
    }

    static Matrix4f matrix() {
        return MATRIX;
    }

    /**
     * Uma caixa de {@code box = {x0, y0, z0, x1, y1, z1}} com o recorte {@code uv} ({@code {u0, v0, u1, v1}} por face,
     * na ordem de {@link #FACES}, ou um só para todas). Devolve 1.
     */
    static int box(float[] box, float[] uv) {
        float x0 = box[0];
        float y0 = box[1];
        float z0 = box[2];
        float x1 = box[3];
        float y1 = box[4];
        float z1 = box[5];
        boolean perFace = uv.length > 4;
        face(0, uv, perFace, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        face(1, uv, perFace, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(2, uv, perFace, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
        face(3, uv, perFace, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
        face(4, uv, perFace, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
        face(5, uv, perFace, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
        return 1;
    }

    /** Uma face vista de fora: o canto de cima à esquerda, o de baixo à esquerda, o de baixo à direita, o de cima. */
    private static void face(int index, float[] uv, boolean perFace, float nx, float ny, float nz, float ax, float ay,
            float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        int at = perFace ? index * 4 : 0;
        float u0 = sprite.getU(uv[at] / ATLAS);
        float v0 = sprite.getV(uv[at + 1] / ATLAS);
        float u1 = sprite.getU(uv[at + 2] / ATLAS);
        float v1 = sprite.getV(uv[at + 3] / ATLAS);
        NORMAL.transform(nx, ny, nz, FACING).normalize();
        CORNERS[0] = ax;
        CORNERS[1] = ay;
        CORNERS[2] = az;
        CORNERS[3] = bx;
        CORNERS[4] = by;
        CORNERS[5] = bz;
        CORNERS[6] = cx;
        CORNERS[7] = cy;
        CORNERS[8] = cz;
        CORNERS[9] = dx;
        CORNERS[10] = dy;
        CORNERS[11] = dz;
        vertex(0, u0, v0);
        vertex(3, u0, v1);
        vertex(6, u1, v1);
        vertex(9, u1, v0);
    }

    private static void vertex(int at, float u, float v) {
        MATRIX.transformPosition(CORNERS[at], CORNERS[at + 1], CORNERS[at + 2], POINT);
        out.addVertex(POINT.x, POINT.y, POINT.z, WHITE, u, v, OverlayTexture.NO_OVERLAY, light, FACING.x, FACING.y,
                FACING.z);
    }
}
