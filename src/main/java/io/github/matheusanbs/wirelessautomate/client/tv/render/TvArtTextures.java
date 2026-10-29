package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import org.lwjgl.opengl.GL11;

/**
 * As texturas das pixel arts no cliente: uma por arte distinta, do tamanho dela, com a paleta em ARGB sem
 * pré-multiplicar e o índice 0 transparente, filtro {@code NEAREST} sem mipmap (nada borra) e {@code GL_REPEAT} (o
 * mosaico é um quadrilátero só). A chave é a arte inteira, id e conteúdo: a mesma arte em várias Telas divide a
 * textura, e uma arte mudada ("Atualizar da biblioteca") ganha outra. Só a imagem na GPU fica; a cópia na memória sai
 * depois de subir.
 * <p>
 * Criada no redesenho que a pede. A varredura ({@link #sweep}) solta as que nenhuma Tela com imagem
 * ({@link TvCanvases#keepArts}), nem o rascunho do editor ({@link TvEditorImage#keepArts}), nem as miniaturas da
 * biblioteca ({@link TvArtThumbs#keepArts}) usam mais: a cada
 * {@link #SWEEP_TICKS} ticks enquanto há textura, logo que passam de {@link #SWEEP_SIZE}, ao soltar todas as Telas e
 * ao fechar o editor. Sem textura, o tick é a checagem do mapa vazio. Só na thread de render.
 */
final class TvArtTextures {
    private static final int SWEEP_TICKS = 100;
    /** Acima de tantas texturas, a varredura vem no tick seguinte (o editor de pixel art cria uma por traço). */
    private static final int SWEEP_SIZE = 64;
    private static final Map<TvPixelArt, Art> TEXTURES = new HashMap<>();
    /** A volta da varredura; a textura marcada com outra volta não foi usada e sai. */
    private static int generation;
    private static int ticks;

    private TvArtTextures() {
    }

    /** A textura de uma arte na GPU e o tipo de desenho que a liga. */
    private static final class Art {
        final int texture;
        final RenderType renderType;
        int generation;

        Art(int texture) {
            this.texture = texture;
            this.renderType = TvRenderTypes.art(texture);
        }
    }

    /** O tipo de desenho da textura da arte, criada agora se ainda não existe. */
    static RenderType renderType(TvPixelArt art) {
        Art entry = TEXTURES.get(art);
        if (entry == null) {
            entry = new Art(upload(art));
            entry.generation = generation;
            TEXTURES.put(art, entry);
            if (TEXTURES.size() > SWEEP_SIZE) {
                ticks = SWEEP_TICKS;
            }
        }
        return entry.renderType;
    }

    /** Sobe a arte numa textura nova: {@code NEAREST}, sem mipmap e repetida nos dois eixos. */
    private static int upload(TvPixelArt art) {
        int w = art.width();
        int h = art.height();
        int[] palette = art.palette();
        byte[] pixels = art.pixels();
        int texture = TextureUtil.generateTextureId();
        TextureUtil.prepareImage(texture, w, h);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(palette[pixels[y * w + x]]));
            }
        }
        image.upload(0, 0, 0, 0, 0, w, h, false, false, false, true);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        return texture;
    }

    /** A cada tick do cliente: a varredura a cada {@link #SWEEP_TICKS}, só com alguma textura. */
    static void onClientTick() {
        if (TEXTURES.isEmpty()) {
            return;
        }
        if (++ticks >= SWEEP_TICKS) {
            sweep();
        }
    }

    /** Marca como em uso as texturas das artes do layout (chamado durante a {@link #sweep}). */
    static void keep(TvLayout layout) {
        List<TvPixelArt> images = layout.images();
        for (int i = 0; i < images.size(); i++) {
            keep(images.get(i));
        }
    }

    /** Marca como em uso a textura de uma arte (chamado durante a {@link #sweep}). */
    static void keep(TvPixelArt art) {
        Art entry = TEXTURES.get(art);
        if (entry != null) {
            entry.generation = generation;
        }
    }

    /** Solta as texturas que nenhuma Tela com imagem nem o rascunho do editor usa. */
    static void sweep() {
        ticks = 0;
        if (TEXTURES.isEmpty()) {
            return;
        }
        generation++;
        TvCanvases.keepArts();
        TvEditorImage.keepArts();
        TvArtThumbs.keepArts();
        Iterator<Art> it = TEXTURES.values().iterator();
        while (it.hasNext()) {
            Art entry = it.next();
            if (entry.generation != generation) {
                TextureUtil.releaseTextureId(entry.texture);
                it.remove();
            }
        }
    }

    /** Quantas texturas de arte existem (o e2e confere que elas saem). */
    static int countForTests() {
        return TEXTURES.size();
    }
}
