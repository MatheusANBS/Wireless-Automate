package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;

/**
 * Escreve a malha do cristal ({@link CrystalMesh#CLUSTER} ou {@link CrystalMesh#DIAMOND}) no buffer do
 * {@code resonator_crystal}, no formato {@code NEW_ENTITY}, duas vezes: as facetas e o casco do contorno. Posição e
 * normal pela pose; a cor da faixa no RGB (o dourado do Lapidado ou a cor do tier do Afinado) e o clarão do fim da
 * recarga (0 a 255) no alfa; x e z do cristal no UV1, como {@code (v + 1) × 10000}; a altura relativa × 10000 e o parâmetro (o
 * {@link CrystalParam}) no UV2. O UV1 e o UV2 são {@code short} no vértice, então tudo fica abaixo de 32768. O casco
 * leva 1 no UV0.x, a direção do contorno do {@link CrystalMesh} no lugar da normal e os vértices em ordem invertida:
 * o corte das faces de trás do vanilla deixa à vista só a parte de trás do casco, que o shader infla e pinta de
 * escuro.
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
     * {@code diamond} escolhe o losango em vez do aglomerado.
     */
    static void render(PoseStack.Pose pose, MultiBufferSource buffer, int bandColor, int flash, int state, int seed,
            boolean diamond) {
        CrystalMesh mesh = diamond ? CrystalMesh.DIAMOND : CrystalMesh.CLUSTER;
        VertexConsumer out = buffer.getBuffer(ChamberRenderTypes.crystal());
        int r = (bandColor >> 16) & 0xFF;
        int g = (bandColor >> 8) & 0xFF;
        int b = bandColor & 0xFF;
        int param = CrystalParam.pack(seed, state, diamond);
        for (int t = 0; t < mesh.triangles(); t++) {
            pose.normal().transform(mesh.normalX(t), mesh.normalY(t), mesh.normalZ(t), NORMAL).normalize();
            for (int k = 0; k < 3; k++) {
                vertex(pose, out, mesh, 3 * t + k, r, g, b, flash, param, 0);
            }
        }
        for (int t = 0; t < mesh.triangles(); t++) {
            for (int k = 2; k >= 0; k--) {
                int v = 3 * t + k;
                pose.normal().transform(mesh.outlineX(v), mesh.outlineY(v), mesh.outlineZ(v), NORMAL).normalize();
                vertex(pose, out, mesh, v, r, g, b, flash, param, 1);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, CrystalMesh mesh, int v, int r, int g, int b,
            int flash, int param, float hull) {
        float x = mesh.x(v);
        float z = mesh.z(v);
        pose.pose().transformPosition(x, mesh.y(v), z, POS);
        out.addVertex(POS.x, POS.y, POS.z)
                .setColor(r, g, b, flash)
                .setUv(hull, 0)
                .setUv1(Math.round((x + 1) * SCALE), Math.round((z + 1) * SCALE))
                .setUv2(Math.round(mesh.height(v) * SCALE), param)
                .setNormal(NORMAL.x, NORMAL.y, NORMAL.z);
    }
}
