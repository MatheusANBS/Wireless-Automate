package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

/**
 * Os quadriláteros da Tela, escritos direto no {@link VertexConsumer} sem alocar nem mexer no {@code PoseStack}.
 * Quem chama dá os pontos na vista de frente: {@code vx} para a direita de quem olha e {@code vy} para cima, em
 * unidades do modelo (1/16 de bloco), a partir do canto de baixo à esquerda da área; e {@code z} como no modelo, que
 * olha para o norte (z 16 é a parede). Aqui eles viram coordenadas do bloco colocado pelo {@code facing}, como o giro
 * {@code y} do blockstate. A ordem dos vértices sai anti-horária vista pelo lado da normal, para o culling. Só na
 * thread de render (o rascunho é estático).
 */
final class TvQuads {
    private static final int CORNERS = 4;
    private static final float[] X = new float[CORNERS];
    private static final float[] Y = new float[CORNERS];
    private static final float[] Z = new float[CORNERS];
    private static final float[] U = new float[CORNERS];
    private static final float[] V = new float[CORNERS];
    /** A posição e a normal transformadas pela {@code Pose}, reusadas: os {@code addVertex(Pose, ...)} e
     * {@code setNormal(Pose, ...)} padrão do {@code VertexConsumer} criam um {@code Vector3f} por chamada. */
    private static final Vector3f POS = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();
    private static final int[] FORWARD = {0, 1, 2, 3};
    private static final int[] BACKWARD = {0, 3, 2, 1};
    private static Direction facing = Direction.NORTH;
    private static float left;
    private static float bottom;

    private TvQuads() {
    }

    /**
     * Começa uma Tela: o {@code facing} e o canto de baixo à esquerda da área no modelo ({@code left} é o x do
     * modelo do lado esquerdo de quem olha, que fica no x maior, porque a direita de quem olha é o x menor).
     */
    static void begin(Direction tvFacing, float leftX, float bottomY) {
        facing = tvFacing;
        left = leftX;
        bottom = bottomY;
    }

    /** O canto {@code i} (0 a 3, em volta do quadrilátero) na vista de frente. */
    static void corner(int i, float vx, float vy, float z, float u, float v) {
        X[i] = left - vx;
        Y[i] = bottom + vy;
        Z[i] = z;
        U[i] = u;
        V[i] = v;
    }

    /** Um retângulo de frente (normal para fora da tela): {@code u0}/{@code vTop} no canto de cima à esquerda. */
    static void front(float vx0, float vy0, float vx1, float vy1, float z, float u0, float vTop, float u1,
            float vBottom) {
        corner(0, vx0, vy0, z, u0, vBottom);
        corner(1, vx1, vy0, z, u1, vBottom);
        corner(2, vx1, vy1, z, u1, vTop);
        corner(3, vx0, vy1, z, u0, vTop);
    }

    /** Emite os quatro cantos no formato das entidades, com a normal dada no modelo. */
    static void solid(VertexConsumer out, PoseStack.Pose pose, float nx, float ny, float nz, int overlay, int light) {
        int[] order = order(nx, ny, nz);
        pose.normal().transform(worldX(nx, nz), ny, worldZ(nx, nz), NORMAL).normalize();
        for (int k : order) {
            position(pose, k);
            out.addVertex(POS.x, POS.y, POS.z)
                    .setColor(-1)
                    .setUv(U[k], V[k])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(NORMAL.x, NORMAL.y, NORMAL.z);
        }
    }

    /** Emite os quatro cantos no formato do texto (posição, cor, uv e luz), virados para fora da tela. */
    static void text(VertexConsumer out, PoseStack.Pose pose, int light) {
        for (int k : order(0, 0, -1)) {
            position(pose, k);
            out.addVertex(POS.x, POS.y, POS.z)
                    .setColor(-1)
                    .setUv(U[k], V[k])
                    .setLight(light);
        }
    }

    /** O canto {@code k} no bloco, transformado pela {@code Pose} em {@link #POS}. */
    private static void position(PoseStack.Pose pose, int k) {
        pose.pose().transformPosition(blockX(X[k], Z[k]), Y[k] / TvFrame.UNITS_PER_BLOCK, blockZ(X[k], Z[k]), POS);
    }

    /** Anti-horário visto pelo lado da normal: inverte se o produto vetorial dos lados aponta para o outro lado. */
    private static int[] order(float nx, float ny, float nz) {
        float ax = X[1] - X[0];
        float ay = Y[1] - Y[0];
        float az = Z[1] - Z[0];
        float bx = X[3] - X[0];
        float by = Y[3] - Y[0];
        float bz = Z[3] - Z[0];
        float cx = ay * bz - az * by;
        float cy = az * bx - ax * bz;
        float cz = ax * by - ay * bx;
        return cx * nx + cy * ny + cz * nz >= 0 ? FORWARD : BACKWARD;
    }

    /** O x no bloco (0 a 1) do ponto do modelo, pelo giro do {@code facing} em torno do centro. */
    private static float blockX(float x, float z) {
        float bx = switch (facing) {
            case SOUTH -> TvFrame.UNITS_PER_BLOCK - x;
            case WEST -> z;
            case EAST -> TvFrame.UNITS_PER_BLOCK - z;
            default -> x;
        };
        return bx / TvFrame.UNITS_PER_BLOCK;
    }

    private static float blockZ(float x, float z) {
        float bz = switch (facing) {
            case SOUTH -> TvFrame.UNITS_PER_BLOCK - z;
            case WEST -> TvFrame.UNITS_PER_BLOCK - x;
            case EAST -> x;
            default -> z;
        };
        return bz / TvFrame.UNITS_PER_BLOCK;
    }

    private static float worldX(float nx, float nz) {
        return switch (facing) {
            case SOUTH -> -nx;
            case WEST -> nz;
            case EAST -> -nz;
            default -> nx;
        };
    }

    private static float worldZ(float nx, float nz) {
        return switch (facing) {
            case SOUTH -> -nz;
            case WEST -> -nx;
            case EAST -> nx;
            default -> nz;
        };
    }
}
