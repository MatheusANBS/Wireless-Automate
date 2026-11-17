package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import net.minecraft.client.renderer.LightTexture;
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
    private static final CrystalLook.Tex[] TEXES = CrystalLook.Tex.values();
    /** Abaixo disto a órbita (a escala da entrada) e o cristal não valem o desenho. */
    private static final float RING_MIN = 0.001f;
    private static final float CRYSTAL_MIN = 0.001f;
    private static final int NODES = ArmilarNodes.FIRST[2] + ArmilarSpec.NOS[2];
    /** O que cada nó usa neste quadro ({@link #classify}): rascunhos, só a thread de render os usa. */
    private static final int[] NODE_MASK = new int[NODES];
    private static final double[] NODE_PROGRESS = new double[NODES];
    private static final int[] NODE_LIT = new int[NODES];
    private static final boolean[] NODE_ROWS = new boolean[NODES];

    /** O nível de luz de bloco do cristal vivo com a sintonia no máximo: o {@code emit} do filme, {@code 0,35 + 0,35 u}, em 15 níveis. */
    private static final double GLOW_BASE = 0.35;
    private static final double GLOW_UP = 0.35;

    private ArmilarSolids() {
    }

    /**
     * A luz do cristal vivo: o {@code emit} do filme ({@code partes.js}, {@code [0,35 + 0,35 u, 0,25 + 0,2 u, 0,2]})
     * vira uma luz de bloco mínima de {@code 15 × (0,35 + 0,35 u)}, combinada com a do mundo pelo máximo (a do céu não
     * muda). Só o canal vermelho do {@code emit} cabe numa luz empacotada, e ele é o que domina a cor do cristal.
     */
    static int glowLight(int light, double u) {
        int block = (int) Math.round(15 * (GLOW_BASE + GLOW_UP * u));
        return LightTexture.pack(Math.max(LightTexture.block(light), block), LightTexture.sky(light));
    }

    private static RenderType[] crystalTypes() {
        CrystalLook.Tex[] texes = CrystalLook.Tex.values();
        RenderType[] types = new RenderType[texes.length];
        for (int i = 0; i < texes.length; i++) {
            types[i] = RenderType.entityCutoutNoCull(texes[i].texture());
        }
        return types;
    }

    /**
     * Desenha em passadas por tipo de desenho: o horizonte e os marcos, todos os pinos, todos os anéis e, por fim, os
     * cristais por textura. Cada troca de {@link RenderType} fecha um lote do buffer do vanilla; assim são uns 10
     * lotes por Armilar em vez de um por malha. Tudo é opaco (cutout) e com teste de profundidade, então a ordem de
     * desenho não muda a imagem.
     */
    static void draw(ArmilarScene s, PoseStack ps, MultiBufferSource buf, float pt) {
        double t = s.clock(pt);
        double a = s.assembly(pt);
        double u = s.motion().u(pt);
        double since = s.sinceTuned(pt);
        int light = s.sunLight();
        int glow = glowLight(light, u);
        ps.pushPose();
        ps.translate(s.frame().centerX() - s.core().getX(), s.frame().centerY() - s.core().getY(),
                s.frame().centerZ() - s.core().getZ());
        rotY(ps, s.frame().yaw());
        ps.scale(PX, PX, PX);
        mesh(buf.getBuffer(HORIZON_TYPE), ArmilarMeshes.horizon(), ps.last(), light, 0xFFFFFF);
        mesh(buf.getBuffer(MARKER_TYPE), ArmilarMeshes.markers(), ps.last(), light, 0xFFFFFF);
        ps.translate(0, ArmilarSpec.SOL_Y, 0);
        for (int i = 0; i < 3; i++) {
            if ((float) ArmilarAssembly.ring(a, i) > RING_MIN) {
                orbitPose(ps, i, t, a, u, since);
                mesh(buf.getBuffer(PIN_TYPE), ArmilarMeshes.pins(i), ps.last(), light, 0xFFFFFF);
                ps.popPose();
            }
        }
        for (int i = 0; i < 3; i++) {
            if ((float) ArmilarAssembly.ring(a, i) > RING_MIN) {
                orbitPose(ps, i, t, a, u, since);
                rotY(ps, Ease.wrap(s.motion().spin(i, pt)));
                mesh(buf.getBuffer(RING_TYPE), ArmilarMeshes.ring(i), ps.last(), light, 0xFFFFFF);
                ps.popPose();
            }
        }
        crystals(s, ps, buf, light, glow, t, a, u, since, pt);
        ps.popPose();
    }

    /** Empilha a pose da órbita {@code i} (precessão, inclinação e a escala da entrada); quem chama desempilha. */
    private static void orbitPose(PoseStack ps, int i, double t, double a, double u, double since) {
        float ai = (float) ArmilarAssembly.ring(a, i);
        ps.pushPose();
        rotY(ps, Ease.wrap(ArmilarPose.prec(i, t)));
        rotX(ps, Ease.wrap(ArmilarPose.tilt(i, t, u, since)));
        ps.scale(ai, ai, ai);
    }

    /**
     * Os cristais, uma passada por textura (a do corpo inteiro, a das linhas acesas e a da faixa), cada uma só com os
     * nós que a usam. O que cada nó usa vem de {@link #classify}, uma vez por quadro.
     */
    private static void crystals(ArmilarScene s, PoseStack ps, MultiBufferSource buf, int light, int glow, double t,
            double a, double u, double since, float pt) {
        int count = Math.min(s.chambers().length, NODE_MASK.length);
        int all = 0;
        for (int node = 0; node < count; node++) {
            all |= classify(s, a, pt, node);
        }
        for (CrystalLook.Tex tex : TEXES) {
            int bit = 1 << tex.ordinal();
            if ((all & bit) == 0) {
                continue;
            }
            VertexConsumer vc = null;
            for (int i = 0; i < 3; i++) {
                if ((float) ArmilarAssembly.ring(a, i) <= RING_MIN || !orbitUses(i, count, bit)) {
                    continue;
                }
                if (vc == null) {
                    vc = buf.getBuffer(CRYSTAL_TYPES[tex.ordinal()]);
                }
                orbitPose(ps, i, t, a, u, since);
                rotY(ps, Ease.wrap(s.motion().spin(i, pt)));
                for (int k = 0; k < ArmilarSpec.NOS[i]; k++) {
                    int node = ArmilarNodes.FIRST[i] + k;
                    if (node < count && (NODE_MASK[node] & bit) != 0) {
                        crystal(s, ps, vc, light, glow, a, tex, node);
                    }
                }
                ps.popPose();
            }
        }
    }

    private static boolean orbitUses(int orbit, int count, int bit) {
        for (int k = 0; k < ArmilarSpec.NOS[orbit]; k++) {
            int node = ArmilarNodes.FIRST[orbit] + k;
            if (node < count && (NODE_MASK[node] & bit) != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Guarda em {@code NODE_MASK} as texturas que o nó usa neste quadro (um bit por {@link CrystalLook.Tex}; 0 se não
     * há cristal à vista) e o resto do que o desenho precisa dele, e devolve a máscara. O vivo e o parado saem inteiros
     * numa textura; a recarga no meio e a faixa do Afinado, linha a linha.
     */
    private static int classify(ArmilarScene s, double a, float pt, int node) {
        byte[] chambers = s.chambers();
        NODE_MASK[node] = 0;
        if (CrystalLook.empty(chambers[node])
                || (float) ArmilarAssembly.crystal(a, ArmilarNodes.popIndex(node)) <= CRYSTAL_MIN) {
            return 0;
        }
        byte code = chambers[node];
        boolean dull = CrystalLook.dull(code);
        boolean recharging = ArmilarRecharge.isRecharging(node, chambers, s.view());
        double progress = recharging
                ? ArmilarRecharge.progress(node, chambers, s.gameTime() + (double) pt, s.view()) : 0;
        int lit = recharging ? CrystalPlan.litRows(progress) : dull ? 0 : ROWS;
        int tier = s.tier(node);
        // a faixa do Afinado só existe nas linhas; a recarga no meio também; senão o cristal sai inteiro numa textura
        boolean rows = lit > 0 && (lit < ROWS || (tier > 0 && CrystalLook.cut(code)));
        int mask = 0;
        if (!rows) {
            mask = 1 << (lit == 0 ? CrystalLook.bodyTex(code) : CrystalLook.liveTex(code)).ordinal();
        } else {
            for (int row = 0; row < ROWS; row++) {
                mask |= 1 << CrystalPlan.rowTex(row, code, tier, lit).ordinal();
            }
        }
        NODE_MASK[node] = mask;
        NODE_PROGRESS[node] = progress;
        NODE_LIT[node] = lit;
        NODE_ROWS[node] = rows;
        return mask;
    }

    /**
     * O cristal do nó, no referencial do anel, só com as malhas da textura {@code only}. O vivo fica de pé com o centro
     * em {@code (r, CENTRO, 0)}; o Opaco e o rachado inclinam em torno da ponta de baixo e voltam ao prumo com a
     * recarga ({@link ArmilarRecharge#fall}). A inclinação é para fora do anel (a spec e o filme não dão o sentido). O
     * que está aceso (vivo, ou as linhas já acesas na recarga) leva a luz {@code glow}; o Opaco, o Rachado e as linhas
     * apagadas, a do mundo.
     */
    private static void crystal(ArmilarScene s, PoseStack ps, VertexConsumer vc, int light, int glow, double a,
            CrystalLook.Tex only, int node) {
        byte code = s.chambers()[node];
        int lit = NODE_LIT[node];
        float ck = (float) ArmilarAssembly.crystal(a, ArmilarNodes.popIndex(node));
        float r = (float) ArmilarSpec.RAIOS[ArmilarNodes.orbit(node)];
        ps.pushPose();
        rotY(ps, (float) ArmilarNodes.angle(node));
        if (CrystalLook.dull(code)) {
            float fall = (float) ArmilarRecharge.fall(NODE_PROGRESS[node]);
            ps.translate(r, ArmilarSpec.TOPO_BASE + ArmilarSpec.OPACO_LIFT_PX * fall, 0);
            rotZ(ps, -OPACO_TILT * fall);
            ps.translate(0, ArmilarSpec.CENTRO - ArmilarSpec.TOPO_BASE, 0);
        } else {
            ps.translate(r, ArmilarSpec.CENTRO, 0);
        }
        ps.scale(ck, ck, ck);
        if (!NODE_ROWS[node]) {
            mesh(vc, ArmilarMeshes.crystal(), ps.last(), lit == 0 ? light : glow, 0xFFFFFF);
        } else {
            int tier = s.tier(node);
            QuadMesh[] meshes = ArmilarMeshes.crystalRows();
            int tint = tier > 0 ? GuiPaint.tierColor(TIERS[tier - 1]) & 0xFFFFFF : 0xFFFFFF;
            for (int row = 0; row < ROWS; row++) {
                if (CrystalPlan.rowTex(row, code, tier, lit) != only) {
                    continue;
                }
                int rgb = CrystalPlan.rowTinted(row, code, tier, lit) ? tint : 0xFFFFFF;
                mesh(vc, meshes[row], ps.last(), row < lit ? glow : light, rgb);
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
