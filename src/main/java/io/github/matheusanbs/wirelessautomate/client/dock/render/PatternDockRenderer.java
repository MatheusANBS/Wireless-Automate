package io.github.matheusanbs.wirelessautomate.client.dock.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockWork;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A animação da Doca no mundo, por fórmula, sem estado próprio: o giroscópio sempre, e no laço de trabalho o pacote
 * e os fragmentos, tudo a partir do relógio do mundo e do {@link DockWork} do block entity (o tick e o lado do último
 * evento de trabalho). O modelo do bloco não tem essas peças, e o circuito dos painéis é textura animada, de custo zero
 * aqui. Só a menos de {@value #VIEW_DISTANCE} blocos (o mesmo raio do evento de trabalho); as caixas usam o
 * {@code RenderType} sólido de entidade do vanilla no atlas de blocos, sem shader próprio, então o Iris e o Embeddium
 * as tratam como qualquer block entity.
 */
public final class PatternDockRenderer implements BlockEntityRenderer<PatternDockBlockEntity> {
    private static final int VIEW_DISTANCE = 64;
    /** O atlas de 64 × 64 de cada tier, o mesmo do modelo do bloco. */
    private static final ResourceLocation[] TEXTURES = textures();
    /** Só do e2e: nada desenhado, para medir o quadro sem o renderer. */
    public static boolean disabledForTests;
    /** Só do e2e: quantas caixas o último {@code render} pôs. */
    public static int lastBoxesForTests;

    public PatternDockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PatternDockBlockEntity dock, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
            int light, int overlay) {
        Level level = dock.getLevel();
        if (level == null || disabledForTests) {
            lastBoxesForTests = 0;
            return;
        }
        DockPose pose = DockPose.INSTANCE;
        DockWork work = dock.work();
        int side;
        if (DockClock.frozen()) {
            pose.fillAt(DockClock.freezeForTests, DockClock.workForTests);
            side = work.side();
        } else {
            double t = level.getGameTime() + (double) partialTick;
            pose.fill(t, work.extra(t), work.speed(t), work.workLoop(t));
            side = work.loopSide(t);
        }
        RouterTier tier = dock.getBlockState().getValue(RouterBlock.TIER);
        TextureAtlasSprite atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(TEXTURES[tier.ordinal()]);
        DockBoxes.begin(poseStack.last(), buffer.getBuffer(RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS)), atlas,
                light);
        int drawn = DockGyro.render(pose);
        if (pose.work) {
            drawn += DockPacket.render(pose, side);
        }
        lastBoxesForTests = drawn;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public boolean shouldRenderOffScreen(PatternDockBlockEntity dock) {
        return false;
    }

    /** Tudo cabe no bloco: o pacote nasce na vigia e os fragmentos vão no máximo a 6,6 px do centro. */
    @Override
    public AABB getRenderBoundingBox(PatternDockBlockEntity dock) {
        return dock.renderBox();
    }

    private static ResourceLocation[] textures() {
        RouterTier[] tiers = RouterTier.values();
        ResourceLocation[] out = new ResourceLocation[tiers.length];
        for (RouterTier tier : tiers) {
            out[tier.ordinal()] = ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID,
                    "block/pattern_dock/" + tier.getSerializedName());
        }
        return out;
    }
}
