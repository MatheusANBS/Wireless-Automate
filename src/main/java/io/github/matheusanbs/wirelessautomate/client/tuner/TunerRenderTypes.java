package io.github.matheusanbs.wirelessautomate.client.tuner;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.renderer.RenderType;

/**
 * O tipo de desenho da pintura do Afinador ({@link TunerPaint}): quadriláteros translúcidos com teste de profundidade
 * (a parede na frente esconde a pintura, e só as faces à vista aparecem), sem gravar profundidade, para não esconder o
 * que vem depois, e dos dois lados.
 */
final class TunerRenderTypes extends RenderType {
    private static final RenderType PAINT = create(WirelessAutomate.MODID + "_tuner_paint",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private TunerRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    static RenderType paint() {
        return PAINT;
    }
}
