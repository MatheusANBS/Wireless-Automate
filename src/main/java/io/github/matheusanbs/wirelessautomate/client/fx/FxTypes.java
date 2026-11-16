package io.github.matheusanbs.wirelessautomate.client.fx;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FogType;

/**
 * Os tipos de desenho dos efeitos, um por folha de {@link FxSheets} e por família, todos criados uma vez no
 * carregamento da classe (nada é criado por quadro): a tinta (alfa, com luz real e névoa limitada), o aditivo sem
 * pré-multiplicar ({@code SRC_ALPHA, ONE}) e a luz pré-multiplicada ({@code ONE, ONE}). Estende {@link RenderType}
 * para alcançar os {@code ...Shard} protegidos.
 */
public final class FxTypes extends RenderType {
    /** O teto da névoa no ar: o resto da névoa do ar fica fora dos efeitos, para continuarem legíveis. */
    private static final float AIR_CAP = 0.35f;

    /** Tinta: o shader é o do vanilla, e a cada uso ele recebe o teto da névoa (0,35 no ar, 1,0 na água). */
    private static final class InkShaderState extends ShaderStateShard {
        InkShaderState() {
            super(FxShaders::ink);
        }

        @Override
        public void setupRenderState() {
            super.setupRenderState();
            ShaderInstance ink = FxShaders.ink();
            Uniform airFog = ink == null ? null : ink.getUniform("AirFog");
            if (airFog != null) {
                airFog.set(airFogCap());
            }
        }
    }

    /** O aditivo pré-multiplicado: {@code ONE, ONE}; o shader já entrega {@code rgb × a} com alfa 1. */
    private static final TransparencyStateShard ADDITIVE_ONE = new TransparencyStateShard(
            WirelessAutomate.MODID + "_fx_one_one", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final ShaderStateShard INK_SHADER = new InkShaderState();
    private static final ShaderStateShard ADD_SHADER = new ShaderStateShard(FxShaders::add);
    private static final ShaderStateShard LIGHT_SHADER = new ShaderStateShard(FxShaders::light);

    private static final Map<ResourceLocation, RenderType> INK = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> ADD = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> LIGHT = new HashMap<>();
    private static final List<RenderType> ALL;
    private static final List<RenderType> BATCH;

    static {
        List<FxSheet> sheets = FxSheets.all();
        RenderType[] all = new RenderType[sheets.size()];
        for (int i = 0; i < all.length; i++) {
            FxSheet sheet = sheets.get(i);
            ResourceLocation tex = sheet.texture();
            INK.put(tex, create(WirelessAutomate.MODID + "_fx_ink_" + sheet.name(), DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 1536, false, true,
                    CompositeState.builder()
                            .setShaderState(INK_SHADER)
                            .setTextureState(new TextureStateShard(tex, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setWriteMaskState(COLOR_WRITE)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .createCompositeState(false)));
            ADD.put(tex, create(WirelessAutomate.MODID + "_fx_add_" + sheet.name(), DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 1536, false, false,
                    CompositeState.builder()
                            .setShaderState(ADD_SHADER)
                            .setTextureState(new TextureStateShard(tex, false, false))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));
            LIGHT.put(tex, create(WirelessAutomate.MODID + "_fx_light_" + sheet.name(), DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 1536, false, false,
                    CompositeState.builder()
                            .setShaderState(LIGHT_SHADER)
                            .setTextureState(new TextureStateShard(tex, false, false))
                            .setTransparencyState(ADDITIVE_ONE)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));
            all[i] = (sheet.layer() == FxSheet.Layer.INK ? INK : LIGHT).get(tex);
            sheet.bind(all[i]);
        }
        ALL = List.of(all);
        // a ordem do lote: a tinta, o aditivo de todas as folhas e a luz (o endBatch desenha nessa ordem)
        List<RenderType> batch = new ArrayList<>();
        for (FxSheet sheet : sheets) {
            if (sheet.layer() == FxSheet.Layer.INK) {
                batch.add(INK.get(sheet.texture()));
            }
        }
        for (FxSheet sheet : sheets) {
            batch.add(ADD.get(sheet.texture()));
        }
        for (FxSheet sheet : sheets) {
            if (sheet.layer() != FxSheet.Layer.INK) {
                batch.add(LIGHT.get(sheet.texture()));
            }
        }
        BATCH = List.copyOf(batch);
    }

    private FxTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    /**
     * O tipo da folha pela camada dela: tinta (alfa, luz real) ou luz pré-multiplicada. Lê o campo da folha, sem busca
     * e sem alocar; o campo é posto na carga desta classe, que este método garante.
     */
    public static RenderType of(FxSheet sheet) {
        return sheet.type();
    }

    /** O aditivo sem pré-multiplicar ({@code SRC_ALPHA, ONE}), para o Reparador e o que vier. */
    public static RenderType add(FxSheet sheet) {
        return ADD.get(sheet.texture());
    }

    /** Os tipos de todas as folhas, na ordem de {@link FxSheets#all()}: a tinta antes da luz. */
    public static List<RenderType> all() {
        return ALL;
    }

    /**
     * Todos os tipos que o {@link FxBatch} segura, na ordem em que saem: a tinta, o aditivo ({@link #add}) e a luz.
     * Um tipo fora desta lista cairia no buffer de reserva e seria desenhado cedo, no meio de um job.
     */
    public static List<RenderType> batchTypes() {
        return BATCH;
    }

    /**
     * O teto da névoa da tinta: 1,0 (névoa inteira) na água, na lava, na neve em pó, na cegueira e na escuridão, 0,35
     * no resto.
     */
    static float airFogCap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) {
            return 1.0f;
        }
        Player player = mc.player;
        if (player != null && (player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(MobEffects.DARKNESS))) {
            return 1.0f;
        }
        return AIR_CAP;
    }
}
