package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevScreenshot.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotFilter.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotLinker.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotTablet.*;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlock;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * As galerias desenhadas sobre a tela de título: o giro do roteador, os armazenamentos e os renders do roteador
 * para a capa do CurseForge.
 */
final class ScreenshotGalleries {
    private ScreenshotGalleries() {
    }

    /** Com {@code true}, a tela de título mostra a galeria do giro em vez das telas. */
    static boolean spinGallery = ONLY != null && ONLY.startsWith("giro");

    /** Galeria do giro: roteadores com spin 0 a 3 numa parede e no chão, com o contorno da colisão. */
    static List<Step> spinSteps() {
        return List.of(new Step(() -> {
            linkerScreen = null;
            tabletScreen = null;
            filterScreen = null;
            mouseX = mouseY = -1;
            spinGallery = true;
        }, "giro-parede"));
    }

    /**
     * Linha de cima: {@code facing=north} (preso numa parede ao sul), vista do norte; o giro deve andar no
     * sentido horário de quem olha (spin 1 com os LEDs à direita, o oeste). Linha de baixo: {@code facing=up}
     * no chão, vista do sul e de cima; spin 1 com os LEDs à esquerda (oeste). Em preto, o contorno da colisão.
     */
    static void renderSpinGallery(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, 0xFF2A2F38);
        var font = Minecraft.getInstance().font;
        g.drawString(font, "Parede: facing=north, visto do norte (oeste à direita)", 12, 6, 0xFFFFFF);
        g.drawString(font, "Chão: facing=up, visto do sul (oeste à esquerda)", 12, height / 2 + 6, 0xFFFFFF);
        float size = Math.min(width / 7f, height / 5.5f);
        for (int row = 0; row < 2; row++) {
            Direction facing = row == 0 ? Direction.NORTH : Direction.UP;
            float yaw = row == 0 ? 180 : 0;
            float pitch = row == 0 ? 15 : 35;
            float cy = height * (row == 0 ? 0.24f : 0.74f);
            for (int spin = 0; spin < RelativeSide.SPINS; spin++) {
                float cx = width * (spin + 1) / 5f;
                int labelY = (int) (cy + size * 0.95f);
                g.drawCenteredString(font, "spin " + spin, (int) cx, labelY, 0xFFE0E0E0);
                g.drawCenteredString(font, "TOP " + RelativeSide.TOP.toAbsolute(facing, spin).getSerializedName(),
                        (int) cx, labelY + 10, 0xFFA0A0A0);
                BlockState state = ModBlocks.ROUTER.get().defaultBlockState()
                        .setValue(RouterBlock.FACING, facing)
                        .setValue(RouterBlock.SPIN, spin)
                        .setValue(RouterBlock.TIER, RouterTier.ELITE);
                g.flush();
                PoseStack pose = g.pose();
                pose.pushPose();
                pose.translate(cx, cy, 150);
                pose.scale(size, -size, size);
                pose.mulPose(Axis.XP.rotationDegrees(pitch));
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.translate(-0.5f, -0.5f, -0.5f);
                MultiBufferSource.BufferSource buffers = g.bufferSource();
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                buffers.endBatch();
                VertexConsumer lines = buffers.getBuffer(RenderType.lines());
                for (AABB box : state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
                    LevelRenderer.renderLineBox(pose, lines, box, 0f, 0f, 0f, 1f);
                }
                buffers.endBatch();
                pose.popPose();
            }
        }
        Lighting.setupFor3DItems();
    }

    /** Com {@code true}, a tela de título mostra os armazenamentos em vez das telas. */
    static boolean blockGallery = ONLY != null && ONLY.startsWith("blocos");

    /** Galeria dos armazenamentos: os cinco blocos (o Tanque de Source em três níveis) nos tiers Básico e Ultimate. */
    static List<Step> blockSteps() {
        return List.of(new Step(() -> {
            linkerScreen = null;
            tabletScreen = null;
            filterScreen = null;
            mouseX = mouseY = -1;
            spinGallery = false;
            blockGallery = true;
        }, "blocos-galeria"));
    }

    /** Os estados mostrados na galeria, na ordem das colunas. */
    private static List<BlockState> galleryStates(RouterTier tier) {
        List<BlockState> states = new ArrayList<>();
        for (StorageKind kind : List.of(StorageKind.CHEST, StorageKind.TANK, StorageKind.BATTERY, StorageKind.CHEMICAL_TANK)) {
            states.add(ModBlocks.STORAGE.get(kind).get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        }
        for (int fill : new int[] {0, 5, 10}) {
            states.add(ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, tier).setValue(StorageSourceTankBlock.FILL, fill));
        }
        return states;
    }

    /**
     * Quatro linhas de blocos: Básico e Ultimate vistos de frente e do alto (como o jogador vê um bloco no
     * chão), e os mesmos vistos quase de cima (o Olho e as tampas), com o contorno da colisão em preto.
     * Sete colunas: Baú, Tanque, Bateria, Tanque Químico e o Tanque de Source nos níveis 0, 5 e 10. Pelo
     * renderizador de blocos do jogo, com os modelos reais. A quinta linha são os itens como aparecem no
     * inventário ({@code GuiGraphics.renderItem}, a vista {@code gui} do modelo): o roteador em cada tier
     * (de frente: a fenda do Olho e o interior da parabólica) e os cinco armazenamentos.
     */
    static void renderBlockGallery(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, 0xFF2A2F38);
        var font = Minecraft.getInstance().font;
        String[] titles = {"Basico, de frente e do alto", "Ultimate, de frente e do alto", "Basico, de cima", "Ultimate, de cima"};
        int rows = 5;
        float size = Math.min(width / 11f, height / (2f * rows));
        for (int row = 0; row < 4; row++) {
            RouterTier tier = row % 2 == 0 ? RouterTier.BASIC : RouterTier.ULTIMATE;
            float pitch = row < 2 ? 30 : 70;
            float yaw = 20;
            float cy = height * (row + 0.5f) / rows;
            g.drawString(font, titles[row], 8, (int) (cy - height / (2f * rows)) + 3, 0xFFFFFF);
            List<BlockState> states = galleryStates(tier);
            for (int col = 0; col < states.size(); col++) {
                float cx = width * (col + 1) / (states.size() + 1f);
                g.flush();
                PoseStack pose = g.pose();
                pose.pushPose();
                pose.translate(cx, cy + size * 0.1f, 150);
                pose.scale(size, -size, size);
                pose.mulPose(Axis.XP.rotationDegrees(pitch));
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.translate(-0.5f, -0.5f, -0.5f);
                MultiBufferSource.BufferSource buffers = g.bufferSource();
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(states.get(col), pose, buffers,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                buffers.endBatch();
                VertexConsumer lines = buffers.getBuffer(RenderType.lines());
                for (AABB box : states.get(col).getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
                    LevelRenderer.renderLineBox(pose, lines, box, 0f, 0f, 0f, 1f);
                }
                buffers.endBatch();
                pose.popPose();
            }
        }
        Lighting.setupFor3DItems();
        // Itens no inventário: o roteador por tier e os armazenamentos.
        float cy = height * (rows - 0.5f) / rows;
        g.drawString(font, "Itens no inventario (vista gui): roteador por tier e armazenamentos", 8,
                (int) (cy - height / (2f * rows)) + 3, 0xFFFFFF);
        List<ItemStack> items = new ArrayList<>();
        for (RouterTier tier : RouterTier.values()) {
            items.add(RouterBlockItem.withTier(ModItems.ROUTER.get(), tier));
        }
        for (StorageKind kind : StorageKind.values()) {
            items.add(StorageBlockItem.withTier(ModItems.STORAGE.get(kind).get(), RouterTier.ELITE));
        }
        float scale = Math.min(width / (items.size() + 1f), height / (float) rows) / 20f;
        for (int col = 0; col < items.size(); col++) {
            float cx = width * (col + 1) / (items.size() + 1f);
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(cx - 8 * scale, cy - 8 * scale, 0);
            pose.scale(scale, scale, 1);
            g.renderItem(items.get(col), 0, 0);
            pose.popPose();
        }
    }

    /** Com {@code true}, a tela de título mostra os renders do roteador sobre magenta. */
    static boolean coverGallery = ONLY != null && ONLY.startsWith("capa");

    static List<Step> coverSteps() {
        return List.of(new Step(() -> {
            linkerScreen = null;
            tabletScreen = null;
            filterScreen = null;
            mouseX = mouseY = -1;
            spinGallery = false;
            blockGallery = false;
            coverGallery = true;
        }, "capa-renders"));
    }

    /**
     * Renders do roteador pelo renderizador de blocos do jogo, com os modelos e as texturas reais, sobre magenta
     * puro ({@code #FF00FF}) para o {@code gerar_capa.py} recortar: na linha de cima o Elite em quatro tamanhos
     * (a mesma vista "de frente e do alto" da galeria dos armazenamentos), na de baixo os oito tiers, e na
     * terceira o Elite de novo, mais de lado. Luz cheia, sem contorno.
     */
    static void renderCoverGallery(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, 0xFFFF00FF);
        BlockState elite = ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ELITE);
        float[] sizes = {height * 0.06f, height * 0.10f, height * 0.16f, height * 0.26f};
        float x = width * 0.05f;
        for (float size : sizes) {
            x += size * 1.1f;
            renderBlock(g, elite, x, height * 0.20f, size, 30, 20);
            x += size * 1.1f;
        }
        RouterTier[] tiers = RouterTier.values();
        for (int i = 0; i < tiers.length; i++) {
            BlockState state = ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.TIER, tiers[i]);
            renderBlock(g, state, width * (i + 1) / (tiers.length + 1f), height * 0.56f, height * 0.10f, 30, 20);
        }
        renderBlock(g, elite, width * 0.25f, height * 0.85f, height * 0.16f, 25, 40);
        renderBlock(g, elite, width * 0.55f, height * 0.85f, height * 0.16f, 35, -25);
        renderBlock(g, elite, width * 0.85f, height * 0.85f, height * 0.16f, 20, 0);
    }

    private static void renderBlock(GuiGraphics g, BlockState state, float cx, float cy, float size, float pitch, float yaw) {
        g.flush();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy + size * 0.1f, 150);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5f, -0.5f, -0.5f);
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        buffers.endBatch();
        pose.popPose();
    }
}
