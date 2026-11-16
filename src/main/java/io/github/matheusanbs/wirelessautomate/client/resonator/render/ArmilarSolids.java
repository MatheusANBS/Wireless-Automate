package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * As peças sólidas da Armilar, o {@code desenhar} do filme ({@code partes.js}) em transformações do {@code PoseStack}:
 * o horizonte e os marcos, e por órbita os pinos, o anel com os engastes e os cristais nos nós. Vão pelo tipo de
 * entidade do vanilla, então valem com e sem shaderpack e fazem sombra. As matrizes são giradas direto (sem criar um
 * quaternion por giro) e os vértices passam por um vetor de rascunho: nada é alocado por quadro além do que o
 * {@code pushPose} do vanilla copia.
 */
final class ArmilarSolids {
    static final ResourceLocation RING_TEX = WirelessAutomate.id("textures/armilar/anel.png");
    static final ResourceLocation PIN_TEX = WirelessAutomate.id("textures/armilar/grafite.png");
    static final ResourceLocation HORIZON_TEX = WirelessAutomate.id("textures/block/armilar_latao.png");
    /** O coral do conceito (o grafite seria {@code armilar_grafite.png}). */
    static final ResourceLocation MARKER_TEXTURE = WirelessAutomate.id("textures/block/armilar_coral.png");

    private static final float PX = 1f / 16;
    private static final int ROWS = CrystalPlan.rows();
    /** Os tipos por textura, criados uma vez: o do vanilla memoizado cria uma chave por chamada. */
    private static final RenderType RING_TYPE = RenderType.entityCutoutNoCull(RING_TEX);
    private static final RenderType PIN_TYPE = RenderType.entityCutoutNoCull(PIN_TEX);
    private static final RenderType HORIZON_TYPE = RenderType.entityCutoutNoCull(HORIZON_TEX);
    private static final RenderType MARKER_TYPE = RenderType.entityCutoutNoCull(MARKER_TEXTURE);
    private static final RenderType[] CRYSTAL_TYPES = crystalTypes();
    private static final RouterTier[] TIERS = RouterTier.values();
    private static final float OPACO_TILT = (float) Math.toRadians(ArmilarSpec.OPACO_TILT_DEG);
    private static final Vector3f SCRATCH = new Vector3f();

    private ArmilarSolids() {
    }

    private static RenderType[] crystalTypes() {
        CrystalLook.Tex[] texes = CrystalLook.Tex.values();
        RenderType[] types = new RenderType[texes.length];
        for (int i = 0; i < texes.length; i++) {
            types[i] = RenderType.entityCutoutNoCull(texes[i].texture());
        }
        return types;
    }

    static void draw(ArmilarScene s, PoseStack ps, MultiBufferSource buf, float pt) {
        double t = s.clock(pt);
        double a = s.assembly(pt);
        double u = s.motion().u(pt);
        double since = s.sinceTuned(pt);
        int light = s.sunLight();
        ps.pushPose();
        ps.translate(s.frame().centerX() - s.core().getX(), s.frame().centerY() - s.core().getY(),
                s.frame().centerZ() - s.core().getZ());
        rotY(ps, s.frame().yaw());
        ps.scale(PX, PX, PX);
        mesh(buf.getBuffer(HORIZON_TYPE), ArmilarMeshes.horizon(), ps.last(), light, 0xFFFFFF);
        mesh(buf.getBuffer(MARKER_TYPE), ArmilarMeshes.markers(), ps.last(), light, 0xFFFFFF);
        ps.translate(0, ArmilarSpec.SOL_Y, 0);
        for (int i = 0; i < 3; i++) {
            float ai = (float) ArmilarAssembly.ring(a, i);
            if (ai <= 0.001f) {
                continue;
            }
            ps.pushPose();
            rotY(ps, Ease.wrap(ArmilarPose.prec(i, t)));
            rotX(ps, Ease.wrap(ArmilarPose.tilt(i, t, u, since)));
            ps.scale(ai, ai, ai);
            mesh(buf.getBuffer(PIN_TYPE), ArmilarMeshes.pins(i), ps.last(), light, 0xFFFFFF);
            rotY(ps, Ease.wrap(s.motion().spin(i, pt)));
            mesh(buf.getBuffer(RING_TYPE), ArmilarMeshes.ring(i), ps.last(), light, 0xFFFFFF);
            for (int k = 0; k < ArmilarSpec.NOS[i]; k++) {
                crystal(s, ps, buf, light, a, pt, ArmilarNodes.FIRST[i] + k);
            }
            ps.popPose();
        }
        ps.popPose();
    }

    /**
     * O cristal do nó, no referencial do anel. O vivo fica de pé com o centro em {@code (r, CENTRO, 0)}; o Opaco e o
     * rachado inclinam em torno da ponta de baixo e voltam ao prumo com a recarga ({@link ArmilarRecharge#fall}). A
     * inclinação é para fora do anel (a spec e o filme não dão o sentido).
     */
    private static void crystal(ArmilarScene s, PoseStack ps, MultiBufferSource buf, int light, double a, float pt,
            int node) {
        byte[] chambers = s.chambers();
        if (node >= chambers.length || CrystalLook.empty(chambers[node])) {
            return;
        }
        float ck = (float) ArmilarAssembly.crystal(a, ArmilarNodes.popIndex(node));
        if (ck <= 0.001f) {
            return;
        }
        byte code = chambers[node];
        boolean dull = CrystalLook.dull(code);
        boolean recharging = ArmilarRecharge.isRecharging(node, chambers, s.view());
        double progress = recharging ? ArmilarRecharge.progress(node, chambers, s.gameTime() + pt, s.view()) : 0;
        float r = (float) ArmilarSpec.RAIOS[ArmilarNodes.orbit(node)];
        ps.pushPose();
        rotY(ps, (float) ArmilarNodes.angle(node));
        if (dull) {
            float fall = (float) ArmilarRecharge.fall(progress);
            ps.translate(r, ArmilarSpec.TOPO_BASE + ArmilarSpec.OPACO_LIFT_PX * fall, 0);
            rotZ(ps, -OPACO_TILT * fall);
            ps.translate(0, ArmilarSpec.CENTRO - ArmilarSpec.TOPO_BASE, 0);
        } else {
            ps.translate(r, ArmilarSpec.CENTRO, 0);
        }
        ps.scale(ck, ck, ck);
        int lit = recharging ? CrystalPlan.litRows(progress) : dull ? 0 : ROWS;
        int tier = s.tier(node);
        // a faixa do Afinado só existe nas linhas; a recarga no meio também; senão o cristal sai inteiro numa textura
        boolean rows = lit > 0 && (lit < ROWS || (tier > 0 && CrystalLook.cut(code)));
        if (!rows) {
            CrystalLook.Tex tex = lit == 0 ? CrystalLook.bodyTex(code) : CrystalLook.liveTex(code);
            mesh(buf.getBuffer(CRYSTAL_TYPES[tex.ordinal()]), ArmilarMeshes.crystal(), ps.last(), light, 0xFFFFFF);
        } else {
            QuadMesh[] meshes = ArmilarMeshes.crystalRows();
            int tint = tier > 0 ? GuiPaint.tierColor(TIERS[tier - 1]) & 0xFFFFFF : 0xFFFFFF;
            for (int row = 0; row < ROWS; row++) {
                CrystalLook.Tex tex = CrystalPlan.rowTex(row, code, tier, lit);
                int rgb = CrystalPlan.rowTinted(row, code, tier, lit) ? tint : 0xFFFFFF;
                mesh(buf.getBuffer(CRYSTAL_TYPES[tex.ordinal()]), meshes[row], ps.last(), light, rgb);
            }
        }
        ps.popPose();
    }

    /** Os quads da malha com o tom de cada um na cor {@code rgb}, a luz {@code light} e a normal pela pose. */
    private static void mesh(VertexConsumer vc, QuadMesh m, PoseStack.Pose pose, int light, int rgb) {
        Matrix4f matrix = pose.pose();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (int q = 0; q < m.quads(); q++) {
            int tone = Math.round(255 * m.tone(q));
            int cr = r * tone / 255;
            int cg = g * tone / 255;
            int cb = b * tone / 255;
            pose.transformNormal(m.nx(q), m.ny(q), m.nz(q), SCRATCH);
            float nx = SCRATCH.x();
            float ny = SCRATCH.y();
            float nz = SCRATCH.z();
            for (int v = 0; v < 4; v++) {
                matrix.transformPosition(m.x(q, v), m.y(q, v), m.z(q, v), SCRATCH);
                vc.addVertex(SCRATCH.x(), SCRATCH.y(), SCRATCH.z()).setColor(cr, cg, cb, 255)
                        .setUv(m.u(q, v), m.v(q, v)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(nx, ny, nz);
            }
        }
    }

    private static void rotX(PoseStack ps, float angle) {
        PoseStack.Pose pose = ps.last();
        pose.pose().rotateX(angle);
        pose.normal().rotateX(angle);
    }

    private static void rotY(PoseStack ps, float angle) {
        PoseStack.Pose pose = ps.last();
        pose.pose().rotateY(angle);
        pose.normal().rotateY(angle);
    }

    private static void rotZ(PoseStack ps, float angle) {
        PoseStack.Pose pose = ps.last();
        pose.pose().rotateZ(angle);
        pose.normal().rotateZ(angle);
    }
}
