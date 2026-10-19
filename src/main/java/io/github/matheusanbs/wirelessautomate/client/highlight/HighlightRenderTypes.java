package io.github.matheusanbs.wirelessautomate.client.highlight;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderType;

/**
 * Os dois tipos de desenho do destaque das máquinas ({@link HighlightRenderer}): as linhas (arestas e a linha
 * tracejada) e as faces do véu, os dois sem teste e sem gravação de profundidade, para aparecerem através dos
 * blocos e não esconderem nada que venha depois.
 */
final class HighlightRenderTypes extends RenderType {
    /**
     * Desliga o teste de profundidade durante o desenho e o religa depois. O {@code NO_DEPTH_TEST} do jogo só não
     * liga o teste: no estágio depois dos translúcidos ele continua ligado pelo tipo dos blocos translúcidos, e a
     * parede esconderia o contorno.
     */
    private static final LayeringStateShard THROUGH_BLOCKS = new LayeringStateShard(
            WirelessAutomate.MODID + "_highlight_through_blocks", RenderSystem::disableDepthTest,
            RenderSystem::enableDepthTest);
    private static final RenderType LINES = create(WirelessAutomate.MODID + "_highlight_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setLayeringState(THROUGH_BLOCKS)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    private static final RenderType VEIL = create(WirelessAutomate.MODID + "_highlight_veil",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setLayeringState(THROUGH_BLOCKS)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private HighlightRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    static RenderType highlightLines() {
        return LINES;
    }

    static RenderType highlightVeil() {
        return VEIL;
    }
}
