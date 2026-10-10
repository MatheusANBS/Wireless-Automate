package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Visor 3D da tela do roteador: a máquina e o roteador preso nela, na posição do mundo, girados
 * com o mouse. Arrastar com o botão esquerdo gira; clicar sem arrastar escolhe a face da máquina
 * sob o mouse; a roda dá zoom.
 *
 * <p>O desenho usa uma matriz de vista própria (escala, pitch e yaw em torno de um pivô entre a
 * máquina e o roteador) e a escolha da face inverte essa mesma matriz e cruza o raio do mouse com
 * o cubo unitário da máquina, sem ler o buffer de profundidade. Só roda no {@code render} da tela
 * e reaproveita matrizes e vetores entre quadros.
 */
public final class MachineView3D {
    private static final float DRAG_DEGREES_PER_PIXEL = 1.4f;
    /** Até onde o mouse anda (px) antes de o clique virar arrasto. */
    private static final float DRAG_THRESHOLD = 3f;
    private static final float PITCH_LIMIT = 85f;
    private static final float MIN_ZOOM = 0.6f;
    private static final float MAX_ZOOM = 2.4f;
    /** Profundidade da GUI onde fica o pivô: acima do fundo do visor, abaixo das dicas. */
    private static final float DEPTH = 150f;
    /** Afastamento das sobreposições em relação à face do cubo, para não brigar com a textura. */
    private static final float LIFT = 0.004f;
    private static final float PX = 1f / 16f;
    /** Mesmas direções de luz do mundo: topo claro, laterais mais escuras e base escura. */
    private static final Vector3f WORLD_LIGHT_0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
    private static final Vector3f WORLD_LIGHT_1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
    private static final Direction[] DIRECTIONS = Direction.values();

    private final Consumer<Direction> onPick;

    private int x;
    private int y;
    private int width;
    private int height;

    private float yaw;
    private float pitch;
    private float zoom = 1f;
    private float targetZoom = 1f;
    private long lastFrame;
    private @Nullable Direction viewFacing;
    private @Nullable BlockState viewMachine;

    private boolean pressed;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private double lastX;
    private double lastY;

    private boolean hasMachine;
    private boolean pickReady;
    private @Nullable Direction hovered;

    private final Matrix4f local = new Matrix4f();
    private final Matrix4f drawn = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private final Vector3f origin = new Vector3f();
    private final Vector3f direction = new Vector3f();
    private final Vector3f pivot = new Vector3f();
    private final Vector3f light0 = new Vector3f();
    private final Vector3f light1 = new Vector3f();

    /** @param onPick chamado com a face da máquina clicada no visor */
    public MachineView3D(Consumer<Direction> onPick) {
        this.onPick = onPick;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Face da máquina sob o mouse no último quadro, ou {@code null} (inclusive durante um arrasto). */
    public @Nullable Direction hoveredFace() {
        return hovered;
    }

    public boolean isDragging() {
        return dragging;
    }

    // ------------------------------------------------------------------ entrada

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        pressed = true;
        dragging = false;
        pressX = lastX = mouseX;
        pressY = lastY = mouseY;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!pressed || button != 0) {
            return false;
        }
        if (!dragging && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > DRAG_THRESHOLD) {
            dragging = true;
        }
        if (dragging) {
            yaw = Mth.wrapDegrees(yaw + (float) (mouseX - lastX) * DRAG_DEGREES_PER_PIXEL);
            pitch = Mth.clamp(pitch + (float) (mouseY - lastY) * DRAG_DEGREES_PER_PIXEL, -PITCH_LIMIT, PITCH_LIMIT);
        }
        lastX = mouseX;
        lastY = mouseY;
        return true;
    }

    /** Solta o botão; sem arrasto, é um clique e escolhe a face sob o mouse. */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!pressed || button != 0) {
            return false;
        }
        pressed = false;
        boolean click = !dragging;
        dragging = false;
        if (click) {
            Direction face = pick(mouseX, mouseY);
            if (face != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                onPick.accept(face);
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!contains(mouseX, mouseY) || scrollY == 0) {
            return false;
        }
        targetZoom = Mth.clamp(targetZoom * (float) Math.pow(1.15, scrollY), MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    // ------------------------------------------------------------------ desenho

    /**
     * Desenha o visor dentro do retângulo de {@link #setBounds}.
     *
     * @param faces    a configuração de cada face da máquina no tipo da aba atual
     * @param selected face selecionada na tela
     * @param pointedOutside face apontada fora do visor (o botão dela sob o mouse), realçada como a
     *                       face sob o mouse; {@code null} se nenhuma
     */
    public void render(GuiGraphics g, BlockState machine, Direction facing, RouterTier tier,
            Function<Direction, FaceView> faces, Direction selected, @Nullable Direction pointedOutside,
            int mouseX, int mouseY) {
        hasMachine = machine.getRenderShape() != RenderShape.INVISIBLE;
        if (facing != viewFacing || machine != viewMachine) {
            resetAngles(facing, machine);
        }
        animateZoom();
        buildMatrix(local, facing);

        g.flush();
        g.enableScissor(x, y, x + width, y + height);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.mulPose(local);
        drawn.set(pose.last().pose());
        drawn.invert(inverse);
        pickReady = true;
        hovered = pressed && dragging || !contains(mouseX, mouseY) ? null : pick(mouseX, mouseY);

        // luz fixa no mundo: gira junto com o modelo, como a sombra das faces de um bloco colocado
        light0.set(WORLD_LIGHT_0).rotateY(rad(yaw)).rotateX(rad(pitch)).mul(1f, -1f, 1f);
        light1.set(WORLD_LIGHT_1).rotateY(rad(yaw)).rotateX(rad(pitch)).mul(1f, -1f, 1f);
        RenderSystem.setShaderLights(light0, light1);

        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        if (hasMachine) {
            renderBlock(blocks, pose, buffers, machine);
        }
        pose.pushPose();
        pose.translate(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        BlockState router = ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, facing)
                .setValue(RouterBlock.TIER, tier);
        blocks.renderSingleBlock(router, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        pose.popPose();
        buffers.endBatch();

        if (hasMachine) {
            renderOverlays(buffers.getBuffer(RenderType.debugQuads()), pose.last().pose(), faces, selected,
                    hovered != null ? hovered : pointedOutside, GuiPaint.tierColor(tier));
            buffers.endBatch();
        }
        pose.popPose();
        Lighting.setupFor3DItems();
        // o que vier depois (dicas, textos) não fica atrás dos blocos; o scissor limita a limpeza ao visor
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        g.disableScissor();
    }

    /**
     * Blocos com renderizador de entidade (baú) vêm pelo caminho do item, que sem mundo desenha o
     * baú virado para o sul; gira o modelo para a direção do estado, como no mundo.
     */
    private static void renderBlock(BlockRenderDispatcher blocks, PoseStack pose, MultiBufferSource buffers,
            BlockState state) {
        boolean turn = state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
        if (turn) {
            pose.pushPose();
            pose.rotateAround(Axis.YP.rotationDegrees(
                    -state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot()), 0.5f, 0.5f, 0.5f);
        }
        blocks.renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        if (turn) {
            pose.popPose();
        }
    }

    /** Véu de uma face sem acesso: porcelana da sombra, translúcida, que apaga a face sobre o papel. */
    private static final int UNAVAILABLE_VEIL = 0x90BDB19A;
    /** Véu da face sob o mouse: coral bem leve. */
    private static final int POINTED_VEIL = 0x40F0603E;

    /**
     * Modo de cada face (moldura e véu na cor do modo), face sem acesso apagada, mouse (véu coral) e
     * seleção (anel na cor do tier).
     */
    private static void renderOverlays(VertexConsumer out, Matrix4f m, Function<Direction, FaceView> faces,
            Direction selected, @Nullable Direction pointed, int tierColor) {
        for (Direction face : DIRECTIONS) {
            FaceView view = faces.apply(face);
            if (!view.available()) {
                quad(out, m, face, 0, 0, 1, 1, LIFT, UNAVAILABLE_VEIL);
            } else if (view.mode() != PortMode.NONE) {
                int color = GuiPaint.modeColor(view.mode()) & 0xFFFFFF;
                quad(out, m, face, 0, 0, 1, 1, LIFT, 0x30000000 | color);
                ring(out, m, face, 0, PX, LIFT * 2, 0xF0000000 | color);
            }
            if (face == pointed && face != selected) {
                quad(out, m, face, 0, 0, 1, 1, LIFT * 3, POINTED_VEIL);
            }
            if (face == selected) {
                ring(out, m, face, 0, PX * 1.5f, LIFT * 3, 0xFF000000 | tierColor);
            }
        }
    }

    /** Moldura de largura {@code width} começando a {@code inset} da borda da face. */
    private static void ring(VertexConsumer out, Matrix4f m, Direction face, float inset, float width, float lift,
            int argb) {
        float a = inset;
        float b = inset + width;
        quad(out, m, face, a, a, 1 - a, b, lift, argb);
        quad(out, m, face, a, 1 - b, 1 - a, 1 - a, lift, argb);
        quad(out, m, face, a, b, b, 1 - b, lift, argb);
        quad(out, m, face, 1 - b, b, 1 - a, 1 - b, lift, argb);
    }

    /** Retângulo {@code [u0,u1]×[v0,v1]} no plano da face, afastado {@code lift} para fora do cubo. */
    private static void quad(VertexConsumer out, Matrix4f m, Direction face, float u0, float v0, float u1, float v1,
            float lift, int argb) {
        vertex(out, m, face, u0, v0, lift, argb);
        vertex(out, m, face, u0, v1, lift, argb);
        vertex(out, m, face, u1, v1, lift, argb);
        vertex(out, m, face, u1, v0, lift, argb);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Direction face, float u, float v, float lift,
            int argb) {
        float px;
        float py;
        float pz;
        switch (face) {
            case DOWN -> { px = u; py = -lift; pz = v; }
            case UP -> { px = u; py = 1 + lift; pz = v; }
            case NORTH -> { px = u; py = v; pz = -lift; }
            case SOUTH -> { px = u; py = v; pz = 1 + lift; }
            case WEST -> { px = -lift; py = u; pz = v; }
            default -> { px = 1 + lift; py = u; pz = v; }
        }
        out.addVertex(m, px, py, pz).setColor(argb);
    }

    // ------------------------------------------------------------------ vista

    /** Ângulo inicial: o roteador de frente, um pouco de lado, e de cima (ou de baixo, se ele estiver embaixo). */
    private void resetAngles(Direction facing, BlockState machine) {
        viewFacing = facing;
        viewMachine = machine;
        if (facing.getAxis().isHorizontal()) {
            yaw = faceYaw(facing) + 35f;
            pitch = 22f;
        } else {
            // de cima ou de baixo: mostra a frente da máquina, se ela tiver uma
            Direction front = machine.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    ? machine.getValue(BlockStateProperties.HORIZONTAL_FACING)
                    : Direction.SOUTH;
            yaw = faceYaw(front) + 35f;
            pitch = facing == Direction.UP ? 28f : -28f;
        }
        yaw = Mth.wrapDegrees(yaw);
    }

    /** Yaw que vira a face {@code face} para quem olha a tela. */
    private static float faceYaw(Direction face) {
        return (float) Math.toDegrees(Math.atan2(-face.getStepX(), face.getStepZ()));
    }

    private void animateZoom() {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 1f : Math.min(1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        zoom += (targetZoom - zoom) * Math.min(1f, dt * 14f);
        if (Math.abs(targetZoom - zoom) < 0.001f) {
            zoom = targetZoom;
        }
    }

    /**
     * Vista do bloco da máquina (cubo {@code [0,1]³}) para a GUI: centro do visor, escala com o Y
     * invertido (a GUI cresce para baixo), pitch, yaw e o pivô entre a máquina e o roteador.
     */
    private Matrix4f buildMatrix(Matrix4f out, Direction facing) {
        float reach = hasMachine ? 0.45f : 1f;
        pivot.set(0.5f + facing.getStepX() * reach, 0.5f + facing.getStepY() * reach,
                0.5f + facing.getStepZ() * reach);
        float scale = Math.min(width, height) / 2.5f * zoom;
        out.translation(x + width / 2f, y + height / 2f, DEPTH)
                .scale(scale, -scale, scale)
                .rotateX(rad(pitch))
                .rotateY(rad(yaw))
                .translate(-pivot.x, -pivot.y, -pivot.z);
        return out;
    }

    /** Face da máquina sob o ponto da tela, pelo raio da vista contra o cubo unitário. */
    private @Nullable Direction pick(double mouseX, double mouseY) {
        if (!hasMachine || !pickReady || !contains(mouseX, mouseY)) {
            return null;
        }
        // o raio sai da frente da tela (z alto) e entra nela; na vista é um raio qualquer
        inverse.transformPosition((float) mouseX, (float) mouseY, DEPTH + 1000f, origin);
        inverse.transformDirection(0f, 0f, -1f, direction);
        float near = Float.NEGATIVE_INFINITY;
        float far = Float.POSITIVE_INFINITY;
        Direction entered = null;
        for (int axis = 0; axis < 3; axis++) {
            float o = origin.get(axis);
            float d = direction.get(axis);
            if (Math.abs(d) < 1e-7f) {
                if (o < 0 || o > 1) {
                    return null;
                }
                continue;
            }
            float t0 = (0 - o) / d;
            float t1 = (1 - o) / d;
            // entrando pelo lado 0 (d > 0) ou pelo lado 1 (d < 0)
            float tIn = Math.min(t0, t1);
            if (tIn > near) {
                near = tIn;
                entered = face(axis, d < 0);
            }
            far = Math.min(far, Math.max(t0, t1));
        }
        return near <= far && far >= 0 ? entered : null;
    }

    private static Direction face(int axis, boolean positive) {
        return switch (axis) {
            case 0 -> positive ? Direction.EAST : Direction.WEST;
            case 1 -> positive ? Direction.UP : Direction.DOWN;
            default -> positive ? Direction.SOUTH : Direction.NORTH;
        };
    }

    private static float rad(float degrees) {
        return degrees * Mth.DEG_TO_RAD;
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    /** Volta ao ângulo inicial de {@code facing} e {@code machine} e ao zoom normal, já neste quadro. */
    void previewReset(Direction facing, BlockState machine) {
        resetAngles(facing, machine);
        zoom = targetZoom = 1f;
    }

    /** Fixa o ângulo (e o zoom, sem animação) para a captura. */
    void previewAngles(float yaw, float pitch, float zoom) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.zoom = this.targetZoom = zoom;
    }

    /** Ponto da tela no centro da face {@code face} da máquina, com a vista atual. */
    int[] previewProject(Direction face, Direction facing, BlockState machine) {
        hasMachine = machine.getRenderShape() != RenderShape.INVISIBLE;
        Matrix4f m = buildMatrix(new Matrix4f(), facing);
        Vector3f p = m.transformPosition(0.5f + face.getStepX() * 0.5f, 0.5f + face.getStepY() * 0.5f,
                0.5f + face.getStepZ() * 0.5f, new Vector3f());
        return new int[] {Math.round(p.x), Math.round(p.y)};
    }
}
