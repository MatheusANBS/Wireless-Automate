package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;

/**
 * Escreve a malha do {@link CrystalMesh} no buffer do {@code resonator_crystal}, no formato {@code NEW_ENTITY}:
 * posição e normal pela pose; a cor do tier no RGB e a sintonia (0 a 255) no alfa; os cantos da faceta no UV0; x e z
 * do cristal no UV1, como {@code (v + 1) × 10000}; a altura relativa × 10000 e o parâmetro (o {@link CrystalParam})
 * no UV2. O UV1 e o UV2 são {@code short} no vértice, então tudo fica abaixo de 32768.
 */
final class CrystalGem {
    private static final float SCALE = 10000f;
    /** Rascunhos do desenho, que é só na thread de render: nada alocado por quadro. */
    private static final Vector3f POS = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();

    private CrystalGem() {
    }

    /**
     * Os triângulos do cristal na pose de agora; {@code state} é um dos estados do {@link CrystalParam} e
     * {@code far} diz que o cristal está longe da câmera.
     */
    static void render(PoseStack.Pose pose, MultiBufferSource buffer, int tierColor, int tune, int state, int seed,
            boolean far) {
        VertexConsumer out = buffer.getBuffer(ChamberRenderTypes.crystal());
        int r = (tierColor >> 16) & 0xFF;
        int g = (tierColor >> 8) & 0xFF;
        int b = tierColor & 0xFF;
        int param = CrystalParam.pack(seed, state, far);
        for (int t = 0; t < CrystalMesh.TRIANGLES; t++) {
            pose.normal().transform(CrystalMesh.normalX(t), CrystalMesh.normalY(t), CrystalMesh.normalZ(t), NORMAL)
                    .normalize();
            for (int k = 0; k < 3; k++) {
                int v = 3 * t + k;
                float x = CrystalMesh.x(v);
                float z = CrystalMesh.z(v);
                pose.pose().transformPosition(x, CrystalMesh.y(v), z, POS);
                out.addVertex(POS.x, POS.y, POS.z)
                        .setColor(r, g, b, tune)
                        .setUv(CrystalMesh.encodedU(v), CrystalMesh.v(v))
                        .setUv1(Math.round((x + 1) * SCALE), Math.round((z + 1) * SCALE))
                        .setUv2(Math.round(CrystalMesh.height(v) * SCALE), param)
                        .setNormal(NORMAL.x, NORMAL.y, NORMAL.z);
            }
        }
    }
}
