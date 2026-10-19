package io.github.matheusanbs.wirelessautomate.client.highlight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * O destaque no mundo das máquinas acesas pelo Olho ({@link MachineHighlights}): o contorno ciano, um véu leve
 * nas faces e a linha tracejada até o roteador, desenhados sem teste de profundidade, para aparecerem através dos
 * blocos. Só as da dimensão atual e dentro da distância de visão. As cores são as do mundo, iguais nos dois temas
 * das telas. Sem nada aceso, o quadro volta na primeira linha; aceso, só vértices, sem alocar. As acesas ficam
 * na sessão: saem ao sair do mundo, e nada vai ao servidor nem ao save.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class HighlightRenderer {
    /** A aresta: o ciano do destaque. */
    private static final float EDGE_R = 0x3F / 255f;
    private static final float EDGE_G = 0xC9 / 255f;
    private static final float EDGE_B = 0xBE / 255f;
    /** O véu e a linha tracejada: o ciano claro, o do osciloscópio do Ressonador. */
    private static final float LIGHT_R = 0x8E / 255f;
    private static final float LIGHT_G = 0xE8 / 255f;
    private static final float LIGHT_B = 0xE1 / 255f;
    private static final float VEIL_ALPHA = 0.18f;
    private static final float DASH_ALPHA = 0.85f;
    /** O contorno sai um pouco para fora do bloco. */
    private static final float INFLATE = 0.004f;

    private static final MachineHighlights LIT = new MachineHighlights();
    private static final DashedLine DASHES = new DashedLine();

    private HighlightRenderer() {
    }

    // ------------------------------------------------------------------ as acesas

    /**
     * Acende a máquina em {@code machine} da dimensão {@code dimension}, com a linha até o roteador em
     * {@code router}, ou a apaga se ela já está acesa. Passando do limite, a mais antiga apaga e a barra de ação
     * avisa.
     */
    public static MachineHighlights.Result toggle(ResourceKey<Level> dimension, BlockPos machine, BlockPos router) {
        MachineHighlights.Result result = LIT.toggle(dimension, machine.asLong(), router.asLong());
        Minecraft minecraft = Minecraft.getInstance();
        if (result == MachineHighlights.Result.LIT_OLDEST_OFF && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("gui.wirelessautomate.highlight.limit", MachineHighlights.LIMIT), true);
        }
        return result;
    }

    public static boolean isLit(ResourceKey<Level> dimension, BlockPos machine) {
        return LIT.isLit(dimension, machine.asLong());
    }

    /** O mesmo, pela posição em {@code long}, para quem pergunta a cada linha da lista. */
    public static boolean isLit(ResourceKey<Level> dimension, long machine) {
        return LIT.isLit(dimension, machine);
    }

    public static int count() {
        return LIT.count();
    }

    /** Sobe a cada mudança das acesas ({@link MachineHighlights#version}). */
    public static int version() {
        return LIT.version();
    }

    /** O "Apagar todas": apaga as acesas de qualquer roteador e dimensão. */
    public static void clear() {
        LIT.clear();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LIT.clear();
    }

    // ------------------------------------------------------------------ desenho

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (LIT.count() == 0 || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        Vec3 camera = event.getCamera().getPosition();
        double reach = minecraft.options.getEffectiveRenderDistance() * 16.0;
        double reachSqr = reach * reach;
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        VertexConsumer veil = buffers.getBuffer(HighlightRenderTypes.highlightVeil());
        int shown = 0;
        for (int i = 0; i < LIT.count(); i++) {
            if (visible(i, dimension, camera, reachSqr)) {
                long m = LIT.machine(i);
                veil(veil, pose, (float) (BlockPos.getX(m) - camera.x), (float) (BlockPos.getY(m) - camera.y),
                        (float) (BlockPos.getZ(m) - camera.z));
                shown++;
            }
        }
        buffers.endBatch(HighlightRenderTypes.highlightVeil());
        if (shown == 0) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(HighlightRenderTypes.highlightLines());
        for (int i = 0; i < LIT.count(); i++) {
            if (visible(i, dimension, camera, reachSqr)) {
                drawLines(lines, pose, camera, LIT.machine(i), LIT.router(i));
            }
        }
        buffers.endBatch(HighlightRenderTypes.highlightLines());
    }

    /** A acesa {@code i} é da dimensão atual e está dentro da distância de visão. */
    private static boolean visible(int i, ResourceKey<Level> dimension, Vec3 camera, double reachSqr) {
        if (!dimension.equals(LIT.dimension(i))) {
            return false;
        }
        long m = LIT.machine(i);
        double dx = BlockPos.getX(m) + 0.5 - camera.x;
        double dy = BlockPos.getY(m) + 0.5 - camera.y;
        double dz = BlockPos.getZ(m) + 0.5 - camera.z;
        return dx * dx + dy * dy + dz * dz <= reachSqr;
    }

    /** As 12 arestas da máquina e a linha tracejada do centro do roteador ao centro dela. */
    private static void drawLines(VertexConsumer lines, PoseStack.Pose pose, Vec3 camera, long machine, long router) {
        float x0 = (float) (BlockPos.getX(machine) - camera.x) - INFLATE;
        float y0 = (float) (BlockPos.getY(machine) - camera.y) - INFLATE;
        float z0 = (float) (BlockPos.getZ(machine) - camera.z) - INFLATE;
        float x1 = x0 + 1 + 2 * INFLATE;
        float y1 = y0 + 1 + 2 * INFLATE;
        float z1 = z0 + 1 + 2 * INFLATE;
        for (int k = 0; k < 4; k++) {
            float y = (k & 1) == 0 ? y0 : y1;
            float z = (k & 2) == 0 ? z0 : z1;
            line(lines, pose, x0, y, z, x1, y, z, 1, 0, 0, EDGE_R, EDGE_G, EDGE_B, 1f);
            float x = (k & 1) == 0 ? x0 : x1;
            line(lines, pose, x, y0, z, x, y1, z, 0, 1, 0, EDGE_R, EDGE_G, EDGE_B, 1f);
            float yz = (k & 2) == 0 ? y0 : y1;
            line(lines, pose, x, yz, z0, x, yz, z1, 0, 0, 1, EDGE_R, EDGE_G, EDGE_B, 1f);
        }
        int n = DASHES.compute(BlockPos.getX(router) + 0.5 - camera.x, BlockPos.getY(router) + 0.5 - camera.y,
                BlockPos.getZ(router) + 0.5 - camera.z, BlockPos.getX(machine) + 0.5 - camera.x,
                BlockPos.getY(machine) + 0.5 - camera.y, BlockPos.getZ(machine) + 0.5 - camera.z);
        if (n == 0) {
            return;
        }
        float[] p = DASHES.points();
        float nx = p[3] - p[0];
        float ny = p[4] - p[1];
        float nz = p[5] - p[2];
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        for (int i = 0; i < n; i++) {
            int o = i * 6;
            line(lines, pose, p[o], p[o + 1], p[o + 2], p[o + 3], p[o + 4], p[o + 5], nx, ny, nz, LIGHT_R, LIGHT_G,
                    LIGHT_B, DASH_ALPHA);
        }
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, float ax, float ay, float az, float bx,
            float by, float bz, float nx, float ny, float nz, float r, float g, float b, float a) {
        lines.addVertex(pose, ax, ay, az).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, bx, by, bz).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }

    /** As seis faces do bloco no canto ({@code x}, {@code y}, {@code z}), relativo à câmera. */
    private static void veil(VertexConsumer veil, PoseStack.Pose pose, float x, float y, float z) {
        float x1 = x + 1;
        float y1 = y + 1;
        float z1 = z + 1;
        quad(veil, pose, x, y, z, x1, y, z, x1, y, z1, x, y, z1);
        quad(veil, pose, x, y1, z, x, y1, z1, x1, y1, z1, x1, y1, z);
        quad(veil, pose, x, y, z, x, y1, z, x1, y1, z, x1, y, z);
        quad(veil, pose, x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1);
        quad(veil, pose, x, y, z, x, y, z1, x, y1, z1, x, y1, z);
        quad(veil, pose, x1, y, z, x1, y1, z, x1, y1, z1, x1, y, z1);
    }

    private static void quad(VertexConsumer veil, PoseStack.Pose pose, float ax, float ay, float az, float bx,
            float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        veil.addVertex(pose, ax, ay, az).setColor(LIGHT_R, LIGHT_G, LIGHT_B, VEIL_ALPHA);
        veil.addVertex(pose, bx, by, bz).setColor(LIGHT_R, LIGHT_G, LIGHT_B, VEIL_ALPHA);
        veil.addVertex(pose, cx, cy, cz).setColor(LIGHT_R, LIGHT_G, LIGHT_B, VEIL_ALPHA);
        veil.addVertex(pose, dx, dy, dz).setColor(LIGHT_R, LIGHT_G, LIGHT_B, VEIL_ALPHA);
    }
}
