package io.github.matheusanbs.wirelessautomate.client.tuner;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorProposalPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A pintura da proposta do Afinador ({@link TunerProposal}) nas faces da máquina: em cada face com modo, um véu e
 * uma moldura translúcidos, um pouco por fora do bloco, na cor do modo (as do painel: Insere, Extrai, Armazém); com
 * mais de um tipo na face, uma moldura por tipo, uma dentro da outra, e a letra de cada tipo no meio, na cor do modo
 * dele. Os vértices (relativos ao canto da máquina) e as letras são montados quando a proposta chega; o quadro só
 * soma a posição da câmera e desenha. As cores são as do mundo, iguais nos dois temas das telas.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class TunerPaint {
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Largura de uma moldura e o vão até a de dentro, em frações da face. */
    private static final float RING = 0.09f;
    private static final float GAP = 0.035f;
    /** O quanto véu, molduras e letras saem do bloco. */
    private static final float LIFT_VEIL = 0.006f;
    private static final float LIFT_RING = 0.008f;
    private static final float LIFT_BACK = 0.010f;
    private static final float LIFT_TEXT = 0.012f;
    private static final int VEIL_ALPHA = 0x38;
    private static final int RING_ALPHA = 0xD0;
    /** A letra tem 8 px de altura: 0,22 do bloco. */
    private static final float TEXT_SCALE = 0.22f / 8f;
    private static final int TEXT_BACKGROUND = 0xA0000000;
    /**
     * Longe disso a máquina nem é desenhada: os afastamentos acima são fixos, e mais longe a profundidade perde precisão
     * e a pintura piscaria contra a face.
     */
    private static final double REACH_SQR = 32.0 * 32.0;

    /** Para cada face absoluta, do plano dela (x e y em [-0,5; 0,5], z para fora) ao bloco [0, 1]³. */
    private static final Matrix4f[] FACE = new Matrix4f[6];

    static {
        for (Direction d : DIRECTIONS) {
            Matrix4f m = new Matrix4f().translate(0.5f, 0.5f, 0.5f);
            switch (d) {
                case NORTH -> m.rotateY((float) Math.PI);
                case EAST -> m.rotateY((float) (Math.PI / 2));
                case WEST -> m.rotateY((float) (-Math.PI / 2));
                case UP -> m.rotateX((float) (-Math.PI / 2));
                case DOWN -> m.rotateX((float) (Math.PI / 2));
                default -> {
                }
            }
            FACE[d.ordinal()] = m;
        }
    }

    /** A linha de letras de uma face, já com a cor de cada uma, o x de onde começa e a largura, em pixels da fonte. */
    private record Letters(int face, FormattedCharSequence line, float x, int width) {
    }

    /** O que o quadro desenha, montado em {@link #build}; {@code null} sem proposta. */
    private static @Nullable BlockPos machine;
    /** x, y, z de cada vértice (quatro por quadrilátero), relativos ao canto da máquina. */
    private static float[] vertices = new float[0];
    private static int[] colors = new int[0];
    private static Letters[] letters = new Letters[0];
    /** O texto do tick, em matriz própria para não alocar por quadro. */
    private static final Matrix4f TEXT = new Matrix4f();
    /** O buffer das letras ({@link #textBuffers}) e o tamanho inicial dele: dá para algumas faces sem crescer. */
    private static final int TEXT_BUFFER_BYTES = 4096;
    private static MultiBufferSource.@Nullable BufferSource textBuffers;

    private TunerPaint() {
    }

    /** Monta a pintura da proposta (ou a apaga com {@code null}); chamado quando a proposta chega ou some. */
    static void build(TunerProposal.@Nullable Shown shown) {
        if (shown == null) {
            machine = null;
            vertices = new float[0];
            colors = new int[0];
            letters = new Letters[0];
            return;
        }
        List<List<ConfiguratorProposalPayload.Face>> byFace = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            byFace.add(new ArrayList<>());
        }
        for (ConfiguratorProposalPayload.Face face : shown.faces()) {
            if (face.face() >= 0 && face.face() < 6 && face.mode() != PortMode.NONE) {
                byFace.get(face.face()).add(face);
            }
        }
        List<float[]> quads = new ArrayList<>();
        List<Integer> quadColors = new ArrayList<>();
        List<Letters> faceLetters = new ArrayList<>();
        Font font = Minecraft.getInstance().font;
        for (int d = 0; d < 6; d++) {
            List<ConfiguratorProposalPayload.Face> entries = byFace.get(d);
            if (entries.isEmpty()) {
                continue;
            }
            entries.sort((a, b) -> Integer.compare(a.type().ordinal(), b.type().ordinal()));
            int first = GuiPaint.modeColor(entries.getFirst().mode()) & 0xFFFFFF;
            quad(quads, quadColors, d, 0, 0, 1, 1, LIFT_VEIL, VEIL_ALPHA << 24 | first);
            for (int k = 0; k < entries.size(); k++) {
                float a = k * (RING + GAP);
                if (a + RING > 0.5f) {
                    break;
                }
                int color = RING_ALPHA << 24 | GuiPaint.modeColor(entries.get(k).mode()) & 0xFFFFFF;
                ring(quads, quadColors, d, a, a + RING, LIFT_RING + k * 0.0005f, color);
            }
            if (entries.size() > 1) {
                Letters row = letters(font, d, entries);
                faceLetters.add(row);
                // o fundo escuro atrás da linha, 1 px além das letras (o da fonte ficaria na frente delas)
                float half = (row.width() / 2f + 1) * TEXT_SCALE;
                float halfHeight = 5 * TEXT_SCALE;
                quad(quads, quadColors, d, 0.5f - half, 0.5f - halfHeight, 0.5f + half, 0.5f + halfHeight,
                        LIFT_BACK, TEXT_BACKGROUND);
            }
        }
        float[] v = new float[quads.size() * 12];
        int[] c = new int[quads.size()];
        for (int i = 0; i < quads.size(); i++) {
            System.arraycopy(quads.get(i), 0, v, i * 12, 12);
            c[i] = quadColors.get(i);
        }
        vertices = v;
        colors = c;
        letters = faceLetters.toArray(Letters[]::new);
        machine = shown.machine();
    }

    /**
     * As letras dos tipos da face numa linha só, separadas por espaço, cada uma na cor do modo dela, centrada: uma
     * chamada de texto por face no quadro.
     */
    private static Letters letters(Font font, int face, List<ConfiguratorProposalPayload.Face> entries) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < entries.size(); i++) {
            String name = ResourceStyle.name(entries.get(i).type()).getString();
            String letter = name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase();
            if (i > 0) {
                line.append(" ");
            }
            line.append(Component.literal(letter).withColor(GuiPaint.modeColor(entries.get(i).mode()) & 0xFFFFFF));
        }
        int width = font.width(line);
        return new Letters(face, line.getVisualOrderText(), -width / 2f, width);
    }

    /** Moldura de {@code a} a {@code b} da borda da face. */
    private static void ring(List<float[]> quads, List<Integer> colors, int face, float a, float b, float lift,
            int argb) {
        quad(quads, colors, face, a, a, 1 - a, b, lift, argb);
        quad(quads, colors, face, a, 1 - b, 1 - a, 1 - a, lift, argb);
        quad(quads, colors, face, a, b, b, 1 - b, lift, argb);
        quad(quads, colors, face, 1 - b, b, 1 - a, 1 - b, lift, argb);
    }

    /** O retângulo {@code [u0,u1]×[v0,v1]} da face (0 a 1), afastado {@code lift} do bloco. */
    private static void quad(List<float[]> quads, List<Integer> colors, int face, float u0, float v0, float u1,
            float v1, float lift, int argb) {
        float[] out = new float[12];
        Vector3f p = new Vector3f();
        float[][] corners = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int i = 0; i < 4; i++) {
            FACE[face].transformPosition(corners[i][0] - 0.5f, corners[i][1] - 0.5f, 0.5f + lift, p);
            out[i * 3] = p.x;
            out[i * 3 + 1] = p.y;
            out[i * 3 + 2] = p.z;
        }
        quads.add(out);
        colors.add(argb);
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        BlockPos at = machine;
        if (at == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockState(at).isAir()) {
            // a máquina foi quebrada: a proposta não tem mais onde ser pintada
            TunerProposal.clear();
            return;
        }
        HitResult hit = minecraft.hitResult;
        TunerProposal.tickAim(hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos() : null);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        BlockPos at = machine;
        if (at == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || TunerProposal.current() == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float ox = (float) (at.getX() - camera.x);
        float oy = (float) (at.getY() - camera.y);
        float oz = (float) (at.getZ() - camera.z);
        if ((double) ox * ox + (double) oy * oy + (double) oz * oz > REACH_SQR) {
            return;
        }
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(TunerRenderTypes.paint());
        float[] v = vertices;
        int[] c = colors;
        for (int q = 0; q < c.length; q++) {
            for (int i = 0; i < 4; i++) {
                int j = q * 12 + i * 3;
                out.addVertex(pose, ox + v[j], oy + v[j + 1], oz + v[j + 2]).setColor(c[q]);
            }
        }
        buffers.endBatch(TunerRenderTypes.paint());
        if (letters.length == 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        MultiBufferSource.BufferSource text = textBuffers();
        for (Letters face : letters) {
            TEXT.set(pose.pose()).translate(ox, oy, oz).mul(FACE[face.face()])
                    .translate(0, 0, 0.5f + LIFT_TEXT).scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
            font.drawInBatch(face.line(), face.x(), -3.5f, 0xFFFFFFFF, false, TEXT, text,
                    Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        }
        text.endBatch();
    }

    /**
     * Um buffer só das letras, criado no primeiro quadro com letras: fechá-lo desenha só os tipos de texto usados
     * aqui, sem esvaziar o buffer geral do quadro, que guarda o que os outros desenharam.
     */
    private static MultiBufferSource.BufferSource textBuffers() {
        if (textBuffers == null) {
            textBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(TEXT_BUFFER_BYTES));
        }
        return textBuffers;
    }
}
