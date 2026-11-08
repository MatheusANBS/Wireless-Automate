package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideInk.Scene;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.Block;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.SceneBlock;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.SheetOp;
import io.github.matheusanbs.wirelessautomate.client.router.MachineView3D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * A cena 3D na moldura do livro. Quando a tinta da folha é preparada, os estados de bloco saem do id e das propriedades
 * {@code p:}, e a vista ortográfica é enquadrada para a cena caber na moldura com margem pela {@code IsometricCamera}
 * da página. O jogador a gira e dá zoom ({@link SceneView}, pelos gestos do {@link SceneInput}); por quadro, só a
 * matriz e a luz são refeitas, e só quando a vista mudou, e os blocos são desenhados com o
 * {@link BlockRenderDispatcher}, como no visor 3D do roteador ({@link MachineView3D}), recortados à moldura.
 */
final class GuideScenes {
    /** A borda da moldura (fio e latão) e a margem entre ela e a cena, em unidades de folha. */
    private static final int BORDER = 4;
    private static final int MARGIN = 6;
    /** Folga de profundidade, para a cena inteira ficar na frente do papel. */
    private static final float DEPTH = 50f;
    /** As direções de luz do mundo, como no visor do roteador: topo claro, laterais mais escuras, base escura. */
    private static final Vector3f WORLD_LIGHT_0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
    private static final Vector3f WORLD_LIGHT_1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
    /** Rascunho do recorte; o desenho é só na linha de render. */
    private static final Vector3f CORNER = new Vector3f();

    private GuideScenes() {
    }

    /** Monta os estados de bloco e enquadra a vista, uma vez por cena. */
    static Scene prepare(SheetOp.SceneFrame f) {
        Block.Scene scene = f.scene();
        List<BlockState> states = new ArrayList<>(scene.blocks().size());
        List<int[]> where = new ArrayList<>(scene.blocks().size());
        for (SceneBlock b : scene.blocks()) {
            BlockState state = state(b);
            if (state != null) {
                states.add(state);
                where.add(new int[] {b.x(), b.y(), b.z()});
            }
        }
        int[] pos = new int[where.size() * 3];
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < where.size(); i++) {
            int[] p = where.get(i);
            System.arraycopy(p, 0, pos, i * 3, 3);
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0] + 1);
            maxY = Math.max(maxY, p[1] + 1);
            maxZ = Math.max(maxZ, p[2] + 1);
        }
        if (where.isEmpty()) {
            minX = minY = minZ = 0;
            maxX = maxY = maxZ = 1;
        }
        float yaw = (float) scene.camera().yaw() * Mth.DEG_TO_RAD;
        float pitch = (float) scene.camera().pitch() * Mth.DEG_TO_RAD;
        float cx = (minX + maxX) / 2;
        float cy = (minY + maxY) / 2;
        float cz = (minZ + maxZ) / 2;
        // os oito cantos da caixa da cena, girados pela câmera da página, dão a largura e a altura na tela
        Matrix4f rot = new Matrix4f().rotateX(pitch).rotateY(yaw);
        Vector3f c = new Vector3f();
        float halfW = 0;
        float halfH = 0;
        for (int i = 0; i < 8; i++) {
            rot.transformPosition((i & 1) == 0 ? minX - cx : maxX - cx, (i & 2) == 0 ? minY - cy : maxY - cy,
                    (i & 4) == 0 ? minZ - cz : maxZ - cz, c);
            halfW = Math.max(halfW, Math.abs(c.x));
            halfH = Math.max(halfH, Math.abs(c.y));
        }
        float availW = f.w() - 2 * (BORDER + MARGIN);
        float availH = f.h() - 2 * (BORDER + MARGIN);
        float fit = Math.min(availW / Math.max(0.01f, 2 * halfW), availH / Math.max(0.01f, 2 * halfH));
        float radius = (float) Math.sqrt((maxX - minX) * (maxX - minX) + (maxY - minY) * (maxY - minY)
                + (maxZ - minZ) * (maxZ - minZ)) / 2;
        return new Scene(f.x(), f.y(), f.w(), f.h(), states.toArray(new BlockState[0]), pos,
                new SceneView(scene.camera(), scene.zoom()), new Camera(fit, cx, cy, cz, radius),
                SceneNotes.of(scene.notes()));
    }

    /**
     * O enquadramento de uma cena (o tamanho de um bloco que faz a cena caber na moldura pela câmera da página, o
     * centro e o raio da caixa) e a matriz e as luzes do quadro, refeitas no lugar só quando a {@link SceneView} muda.
     */
    static final class Camera {
        private final float fit;
        private final float cx;
        private final float cy;
        private final float cz;
        private final float radius;
        private final Matrix4f view = new Matrix4f();
        private final Vector3f light0 = new Vector3f();
        private final Vector3f light1 = new Vector3f();
        /** A versão da vista com que a matriz foi feita; -1 = nunca. */
        private int seen = -1;

        Camera(float fit, float cx, float cy, float cz, float radius) {
            this.fit = fit;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.radius = radius;
        }
    }

    /**
     * A matriz da cena pela vista: o centro da caixa no meio da moldura, girado pelo yaw e pelo pitch, na escala do
     * enquadramento vezes o zoom, e à frente do papel pelo raio inteiro (a cena girada não atravessa a folha). A luz
     * fica presa ao mundo e gira com a vista, como a sombra das faces de um bloco colocado. As anotações são
     * projetadas com a matriz nova no mesmo passo.
     */
    private static void aim(Scene s) {
        Camera c = s.camera();
        SceneView v = s.view();
        if (c.seen == v.version()) {
            return;
        }
        c.seen = v.version();
        float yaw = (float) v.yaw() * Mth.DEG_TO_RAD;
        float pitch = (float) v.pitch() * Mth.DEG_TO_RAD;
        float k = c.fit * (float) v.scale();
        c.view.translation(s.x() + s.w() / 2f, s.y() + s.h() / 2f, k * c.radius + DEPTH)
                .scale(k, -k, k)
                .rotateX(pitch)
                .rotateY(yaw)
                .translate(-c.cx, -c.cy, -c.cz);
        c.light0.set(WORLD_LIGHT_0).rotateY(yaw).rotateX(pitch).mul(1f, -1f, 1f);
        c.light1.set(WORLD_LIGHT_1).rotateY(yaw).rotateX(pitch).mul(1f, -1f, 1f);
        s.notes().project(c.view, c.seen, k);
    }

    /** O mouse em ({@code ux}, {@code uy}), unidades da folha, sobre a cena: a anotação sob ele (só se algo mudou). */
    static void hover(Scene s, double ux, double uy) {
        aim(s);
        s.notes().hover(ux, uy);
    }

    /** O estado do bloco pelo id e pelas propriedades; {@code null} (com aviso) se o bloco não existe. */
    private static @Nullable BlockState state(SceneBlock b) {
        ResourceLocation rl = ResourceLocation.tryParse(b.id());
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            WirelessAutomate.LOGGER.warn("Livro: bloco desconhecido na cena: {}", b.id());
            return null;
        }
        BlockState state = BuiltInRegistries.BLOCK.get(rl).defaultBlockState();
        for (Map.Entry<String, String> e : b.props().entrySet()) {
            Property<?> prop = state.getBlock().getStateDefinition().getProperty(e.getKey());
            BlockState next = prop == null ? null : with(state, prop, e.getValue());
            if (next == null) {
                WirelessAutomate.LOGGER.warn("Livro: propriedade inválida na cena: {}[{}={}]", b.id(), e.getKey(),
                        e.getValue());
            } else {
                state = next;
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> @Nullable BlockState with(BlockState state, Property<T> prop, String value) {
        Optional<T> v = prop.getValue(value);
        return v.map(t -> state.setValue(prop, t)).orElse(null);
    }

    // ---- desenho

    static void draw(GuiGraphics g, Scene s) {
        int x0 = s.x();
        int y0 = s.y();
        int x1 = x0 + s.w();
        int y1 = y0 + s.h();
        GuidePainter.box(g, x0, y0, s.w(), s.h());
        if (s.states().length == 0) {
            return;
        }
        g.flush();
        PoseStack pose = g.pose();
        scissor(pose.last().pose(), x0 + BORDER, y0 + BORDER, x1 - BORDER, y1 - BORDER);
        aim(s);
        pose.pushPose();
        pose.mulPose(s.camera().view);
        RenderSystem.setShaderLights(s.camera().light0, s.camera().light1);
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        BlockState[] states = s.states();
        int[] pos = s.pos();
        for (int i = 0; i < states.length; i++) {
            float bx = pos[i * 3];
            float by = pos[i * 3 + 1];
            float bz = pos[i * 3 + 2];
            pose.translate(bx, by, bz);
            MachineView3D.renderBlock(blocks, pose, buffers, states[i]);
            pose.translate(-bx, -by, -bz);
        }
        buffers.endBatch();
        pose.popPose();
        Lighting.setupFor3DItems();
        // o que vier depois não fica atrás dos blocos; o recorte limita a limpeza à moldura
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        if (!s.notes().isEmpty()) {
            // a folha não gira: a escala dela em x vezes a da interface dá os pixels de tela por unidade
            s.notes().draw(g, 1f / (float) (pose.last().pose().m00() * Minecraft.getInstance().getWindow()
                    .getGuiScale()));
        }
        RenderSystem.disableScissor();
    }

    /**
     * Recorta ao retângulo em unidades de folha: a matriz da folha leva os cantos a coordenadas de interface, e a
     * escala de interface, a pixels de tela (arredondado para dentro, para a cena não passar do latão).
     */
    private static void scissor(Matrix4f m, int x0, int y0, int x1, int y1) {
        Window w = Minecraft.getInstance().getWindow();
        double gs = w.getGuiScale();
        m.transformPosition(x0, y0, 0, CORNER);
        int px0 = (int) Math.ceil(CORNER.x * gs);
        int py0 = (int) Math.ceil(CORNER.y * gs);
        m.transformPosition(x1, y1, 0, CORNER);
        int px1 = (int) Math.floor(CORNER.x * gs);
        int py1 = (int) Math.floor(CORNER.y * gs);
        RenderSystem.enableScissor(px0, w.getHeight() - py1, Math.max(0, px1 - px0), Math.max(0, py1 - py0));
    }
}
