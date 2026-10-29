package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;

/**
 * As texturas das pixel arts no cliente: uma por arte distinta, do tamanho dela, com a paleta com o alfa
 * pré-multiplicado ({@link TvPremultiply}, o que a mistura do {@link TvRenderTypes#art} espera) e o índice 0
 * transparente, {@code NEAREST} ao ampliar (o pixel duro, nada borra), mipmaps com
 * {@code LINEAR_MIPMAP_LINEAR} ao reduzir (a miniatura e a arte esticada menor que ela não serrilham; o GL escolhe por
 * pixel no redesenho, como nas imagens enviadas, {@link TvPictureTextures#mipmaps}) e {@code GL_REPEAT} (o mosaico é um
 * quadrilátero só). A chave é a arte inteira, id e conteúdo: a mesma arte em várias Telas divide a
 * textura, e uma arte mudada ("Atualizar da biblioteca") ganha outra. Antes do mapa pelo conteúdo vem um pela
 * instância ({@link #BY_INSTANCE}): as artes da biblioteca e dos layouts não mudam de instância entre quadros, e as
 * miniaturas pedem a textura a cada quadro sem recalcular o {@code hashCode} da arte (até 16 KB de pixels). Só a
 * imagem na GPU fica; a cópia na memória sai depois de subir.
 * <p>
 * A arte animada tem uma textura por quadro, cada uma criada na primeira vez que aquele quadro é pedido: trocar de
 * quadro é escolher outra textura, sem subir nada nem refazer mipmaps, e o mosaico continua repetindo pelo
 * {@code GL_REPEAT} sem vazar para o quadro vizinho (o que uma tira com todos os quadros faria). Uma arte de 128 × 128
 * com 8 quadros ocupa 8 × 85 KB (com os mipmaps), uns 680 KB; uma de 32 × 32 com 64, 64 × 5,3 KB, uns 340 KB.
 * <p>
 * Criada no redesenho que a pede. A varredura ({@link #sweep}) solta as que nenhuma Tela com imagem
 * ({@link TvCanvases#keepArts}), nem o rascunho do editor ({@link TvEditorImage#keepArts}), nem as miniaturas da
 * biblioteca ({@link TvArtThumbs#keepArts}) usam mais: a cada
 * {@link #SWEEP_TICKS} ticks enquanto há textura, logo que passam de {@link #SWEEP_SIZE}, ao soltar todas as Telas e
 * ao fechar o editor. Sem textura, o tick é a checagem do mapa vazio. Só na thread de render.
 */
final class TvArtTextures {
    private static final int SWEEP_TICKS = 100;
    /**
     * Acima de tantas texturas, a varredura vem no tick seguinte: muitas artes distintas à vista (uma biblioteca
     * grande, várias Telas com arte por perto) não esperam os {@link #SWEEP_TICKS} para soltar as que saíram.
     */
    private static final int SWEEP_SIZE = 64;
    private static final Map<TvPixelArt, Art> TEXTURES = new HashMap<>();
    /** A textura pela instância da arte; esvaziado a cada varredura (as instâncias velhas não ficam presas). */
    private static final Map<TvPixelArt, Art> BY_INSTANCE = new IdentityHashMap<>();
    /** A volta da varredura; a textura marcada com outra volta não foi usada e sai. */
    private static int generation;
    private static int ticks;

    private TvArtTextures() {
    }

    /** As texturas de uma arte na GPU, uma por quadro (0 enquanto o quadro não foi pedido), e os tipos de desenho. */
    private static final class Art {
        final int[] textures;
        final RenderType[] renderTypes;
        int generation;

        Art(int frames) {
            this.textures = new int[frames];
            this.renderTypes = new RenderType[frames];
        }
    }

    /** O tipo de desenho da textura do primeiro quadro da arte. */
    static RenderType renderType(TvPixelArt art) {
        return renderType(art, 0);
    }

    /** O tipo de desenho da textura do quadro {@code frame} da arte, criada agora se ainda não existe. */
    static RenderType renderType(TvPixelArt art, int frame) {
        Art entry = BY_INSTANCE.get(art);
        if (entry == null) {
            entry = TEXTURES.get(art);
            if (entry == null) {
                entry = new Art(art.frames());
                entry.generation = generation;
                TEXTURES.put(art, entry);
                if (TEXTURES.size() > SWEEP_SIZE) {
                    ticks = SWEEP_TICKS;
                }
            }
            BY_INSTANCE.put(art, entry);
        }
        RenderType type = entry.renderTypes[frame];
        if (type == null) {
            entry.textures[frame] = upload(art, frame);
            type = TvRenderTypes.art(entry.textures[frame]);
            entry.renderTypes[frame] = type;
        }
        return type;
    }

    /**
     * Sobe o quadro {@code frame} da arte numa textura nova: {@code NEAREST} ao ampliar, mipmaps ao reduzir, repetida
     * nos dois eixos.
     */
    private static int upload(TvPixelArt art, int frame) {
        int w = art.width();
        int h = art.height();
        int[] palette = art.palette();
        byte[] pixels = art.pixels();
        int offset = art.frameOffset(frame);
        int texture = TextureUtil.generateTextureId();
        TextureUtil.prepareImage(texture, w, h);
        int[] premultiplied = new int[palette.length];
        for (int i = 0; i < palette.length; i++) {
            premultiplied[i] = FastColor.ABGR32.fromArgb32(TvPremultiply.color(palette[i]));
        }
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setPixelRGBA(x, y, premultiplied[pixels[offset + y * w + x]]);
            }
        }
        image.upload(0, 0, 0, 0, 0, w, h, false, false, false, true);
        GlStateManager._bindTexture(texture);
        TvPictureTextures.mipmaps(w, h);
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
        BY_INSTANCE.clear();
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
                for (int texture : entry.textures) {
                    if (texture != 0) {
                        TextureUtil.releaseTextureId(texture);
                    }
                }
                it.remove();
            }
        }
    }

    /** Quantas artes têm textura (o e2e confere que elas saem). */
    static int countForTests() {
        return TEXTURES.size();
    }

    /** Quantas texturas de quadro a arte tem na GPU agora (o e2e mede a animação). */
    static int framesForTests(TvPixelArt art) {
        Art entry = TEXTURES.get(art);
        int count = 0;
        if (entry != null) {
            for (int texture : entry.textures) {
                count += texture != 0 ? 1 : 0;
            }
        }
        return count;
    }
}
