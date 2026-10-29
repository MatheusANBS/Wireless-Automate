package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageDecoder;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageDisk;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * As texturas das imagens enviadas no cliente: uma por resumo à vista, do tamanho da imagem, com a cadeia inteira de
 * mipmaps ({@code glGenerateMipmap}), {@code LINEAR_MIPMAP_LINEAR} ao reduzir e {@code NEAREST} ao ampliar. O GL
 * escolhe entre os dois por pixel no redesenho da Tela (que é quando a textura é desenhada; nada por quadro): a foto
 * reduzida fica lisa, sem serrilhado, e a ampliada fica com o pixel duro, como a pixel art. Uma imagem de 1024 × 1024
 * ocupa uns 5,3 MB de memória de vídeo.
 * <p>
 * O caminho de uma imagem: o redesenho pede a textura ({@link #renderType}) e, na primeira vez, a imagem é lida do
 * cache em disco ({@link TvImageDisk}) e decodificada ({@link TvImageDecoder}) num executor, fora da thread de render;
 * a textura sobe em faixas de até {@link #BAND_PIXELS} pixels por quadro ({@link #onFrame}), e só quando inteira, com
 * os mipmaps, a Tela é redesenhada com ela. Até lá, e se a imagem ainda não chegou do servidor ({@link #arrived} a traz
 * depois) ou não abre, a Tela desenha o quadro neutro. Com as imagens desligadas no servidor ({@link #serverOff}),
 * sempre o quadro neutro.
 * <p>
 * O GIF animado ({@link TvGifAnimation}) também é uma textura só, do tamanho dele, com o quadro da vez: a cada
 * quadro do jogo ({@link #animate}), os GIFs vistos no último tick (por uma Tela à vista, {@link TvTimed}, pelo
 * rascunho do editor ou por uma miniatura da biblioteca, {@link #seen}) conferem o quadro pelo relógio do cliente e,
 * quando ele muda, sobem o quadro novo com os mipmaps e marcam sujas as Telas à vista que o mostram. O GIF que ninguém
 * vê para no quadro em que está.
 * <p>
 * A varredura, a cada {@link #SWEEP_TICKS} ticks com alguma imagem, solta as que nenhuma Tela com imagem
 * ({@link TvCanvases#keepPictures}), nem o rascunho do editor ({@link TvEditorImage#keepPictures}), nem as miniaturas
 * da biblioteca ({@link TvPictureThumbs#keepPictures}) usam. Sem imagem, o tick e o quadro são a checagem de um mapa e
 * de duas listas vazias. Só na thread de render.
 */
public final class TvPictureTextures {
    private static final int SWEEP_TICKS = 100;
    /** Pixels subidos por quadro, no máximo (2 MB): uma imagem de 1024 × 1024 sobe em dois quadros. */
    private static final int BAND_PIXELS = 1 << 19;
    private static final Map<String, Picture> PICTURES = new HashMap<>();
    private static final ArrayDeque<Picture> UPLOADS = new ArrayDeque<>();
    /** As imagens prontas que são GIF animado. */
    private static final List<Picture> ANIMATED = new ArrayList<>();
    /** Por quantos ticks um GIF continua tocando depois da última vez que alguém o mostrou. */
    private static final int SEEN_TICKS = 2;
    private static int generation;
    private static int ticks;
    /** Os ticks do cliente desde o começo, para o "visto há pouco" dos GIFs. */
    private static int clientTicks;
    private static int version;
    private static boolean serverOff;
    /** O tempo da última decodificação e da última subida, para o e2e medir. */
    public static long lastDecodeNanosForTests;
    public static long lastUploadNanosForTests;

    private TvPictureTextures() {
    }

    private enum State { LOADING, MISSING, UPLOADING, READY, FAILED }

    /** Uma imagem no cliente, do pedido até a textura pronta. */
    private static final class Picture {
        final String hash;
        State state = State.LOADING;
        int texture = -1;
        @Nullable RenderType renderType;
        TvImageDecoder.@Nullable Decoded decoded;
        @Nullable TvGifAnimation animation;
        int rows;
        long uploadNanos;
        int generation;

        Picture(String hash) {
            this.hash = hash;
        }
    }

    /** O que a leitura e a decodificação deram: a imagem, ou se ela não estava no disco. */
    private record Loaded(TvImageDecoder.@Nullable Decoded decoded, boolean missing) {
    }

    /**
     * O tipo de desenho da textura da imagem, ou {@code null} enquanto ela não está pronta (a Tela desenha o quadro
     * neutro); na primeira vez, começa a carregá-la.
     */
    static @Nullable RenderType renderType(TvPicture picture) {
        if (serverOff) {
            return null;
        }
        Picture entry = PICTURES.get(picture.hash());
        if (entry == null) {
            entry = new Picture(picture.hash());
            entry.generation = generation;
            PICTURES.put(picture.hash(), entry);
            load(entry, TvImageDisk.read(picture.hash()));
        }
        return entry.state == State.READY ? entry.renderType : null;
    }

    /**
     * Lê (do disco ou dos bytes que chegaram) e decodifica num executor; a volta é na thread de render. O GIF animado
     * vem com todos os quadros se ainda cabem no teto dos GIFs ({@link TvGifAnimation#ramRoom}), medido agora.
     */
    private static void load(Picture entry, CompletableFuture<byte @Nullable []> bytes) {
        entry.state = State.LOADING;
        long room = TvGifAnimation.ramRoom();
        bytes.thenApplyAsync(data -> data == null ? new Loaded(null, true)
                        : new Loaded(TvImageDecoder.decode(data, room), false), Util.backgroundExecutor())
                .whenComplete((loaded, error) -> Minecraft.getInstance().execute(() -> loaded(entry,
                        loaded == null ? new Loaded(null, false) : loaded)));
    }

    private static void loaded(Picture entry, Loaded loaded) {
        if (PICTURES.get(entry.hash) != entry) {
            // Solta pela varredura enquanto carregava.
            if (loaded.decoded != null) {
                loaded.decoded.close();
            }
            return;
        }
        if (loaded.decoded == null) {
            entry.state = loaded.missing ? State.MISSING : State.FAILED;
            return;
        }
        TvImageDecoder.Decoded decoded = loaded.decoded;
        lastDecodeNanosForTests = decoded.nanos;
        entry.animation = decoded.animated() ? new TvGifAnimation(decoded) : null;
        entry.texture = TextureUtil.generateTextureId();
        // Só o nível 0: o glGenerateMipmap cria os outros com o tamanho certo (o prepareImage daria 0 de altura ao
        // último nível de uma imagem que não é quadrada).
        TextureUtil.prepareImage(NativeImage.InternalGlFormat.RGBA, entry.texture, 0, decoded.width, decoded.height);
        entry.decoded = decoded;
        entry.rows = 0;
        entry.uploadNanos = 0;
        entry.state = State.UPLOADING;
        UPLOADS.add(entry);
    }

    /**
     * Os bytes de uma imagem chegaram do servidor (já conferidos e guardados no disco): a que esperava por eles é
     * decodificada agora.
     */
    public static void arrived(String hash, byte[] data) {
        Picture entry = PICTURES.get(hash);
        if (entry != null && (entry.state == State.MISSING || entry.state == State.FAILED)) {
            load(entry, CompletableFuture.completedFuture(data));
        }
    }

    /**
     * No começo do quadro: os GIFs à vista trocam de quadro ({@link #animate}) e, só com subida pendente, sobe uma
     * faixa da imagem da frente; inteira, gera os mipmaps, solta os pixels da memória e marca sujas as Telas que a
     * usam.
     */
    static void onFrame() {
        if (!ANIMATED.isEmpty()) {
            animate();
        }
        Picture entry = UPLOADS.peekFirst();
        if (entry == null) {
            return;
        }
        long start = System.nanoTime();
        TvImageDecoder.Decoded decoded = entry.decoded;
        int w = decoded.width;
        int rows = Math.max(1, Math.min(decoded.height - entry.rows, BAND_PIXELS / w));
        GlStateManager._bindTexture(entry.texture);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, entry.rows, w, rows, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, decoded.address() + (long) entry.rows * w * 4);
        entry.rows += rows;
        if (entry.rows >= decoded.height) {
            mipmaps(w, decoded.height);
            if (entry.animation == null || !entry.animation.ownsDecoded()) {
                decoded.close();
            }
            entry.decoded = null;
            entry.renderType = TvRenderTypes.art(entry.texture);
            entry.state = State.READY;
            UPLOADS.pollFirst();
            if (entry.animation != null) {
                ANIMATED.add(entry);
            }
            version++;
            TvCanvases.markPicture(entry.hash);
        }
        entry.uploadNanos += System.nanoTime() - start;
        if (entry.state == State.READY) {
            lastUploadNanosForTests = entry.uploadNanos;
        }
    }

    /**
     * Na textura ligada, com o nível 0 inteiro: a cadeia de mipmaps até 1 × 1, {@code LINEAR_MIPMAP_LINEAR} ao reduzir,
     * {@code NEAREST} ao ampliar e repetida nos dois eixos (o mosaico). A pixel art usa o mesmo ({@link TvArtTextures}).
     */
    static void mipmaps(int width, int height) {
        int levels = 31 - Integer.numberOfLeadingZeros(Math.max(width, height));
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, levels);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    }

    /**
     * Os GIFs vistos há pouco: o quadro da vez pelo relógio do cliente; quando muda e já está aberto, sobe com os
     * mipmaps e as Telas à vista que o mostram redesenham ({@link TvTimed#gifChanged}).
     */
    private static void animate() {
        long now = Util.getMillis();
        for (int i = 0; i < ANIMATED.size(); i++) {
            Picture entry = ANIMATED.get(i);
            TvGifAnimation animation = entry.animation;
            if (clientTicks - animation.seenTick > SEEN_TICKS) {
                continue;
            }
            int index = animation.clock.frameAt(now);
            if (index == animation.shown) {
                continue;
            }
            long address = animation.pixels(index);
            if (address == 0) {
                continue;
            }
            GlStateManager._bindTexture(entry.texture);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, animation.width, animation.height,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, address);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            animation.shown(index);
            TvTimed.gifChanged(entry.hash);
        }
    }

    /** Uma Tela à vista, o rascunho do editor ou uma miniatura mostrou o GIF agora: ele toca por mais um pouco. */
    static void seen(String hash) {
        Picture entry = PICTURES.get(hash);
        if (entry != null && entry.animation != null) {
            entry.animation.seenTick = clientTicks;
        }
    }

    /** O mesmo para as imagens do layout. */
    static void seen(TvLayout layout) {
        List<TvPicture> pictures = layout.pictures();
        for (int i = 0; i < pictures.size(); i++) {
            if (pictures.get(i).animated()) {
                seen(pictures.get(i).hash());
            }
        }
    }

    /** O servidor ligou ou desligou as imagens: as Telas com imagem redesenham na hora, com o quadro neutro ou não. */
    public static void setServerOff(boolean off) {
        if (off != serverOff) {
            serverOff = off;
            version++;
            TvCanvases.markAllPictures();
        }
    }

    /** Algo mudou o que as imagens mostram (a opção dos outros jogadores): o rascunho do editor se redesenha. */
    static void changed() {
        version++;
    }

    /** Se o servidor desligou as imagens: o quadro neutro no lugar de todas. */
    public static boolean serverOff() {
        return serverOff;
    }

    /** Sobe quando uma imagem fica pronta: o rascunho do editor se redesenha. */
    static int version() {
        return version;
    }

    /** A cada tick do cliente: a varredura a cada {@link #SWEEP_TICKS}, só com alguma imagem. */
    static void onClientTick() {
        clientTicks++;
        if (PICTURES.isEmpty()) {
            return;
        }
        if (++ticks >= SWEEP_TICKS) {
            sweep();
        }
    }

    /** Marca como em uso as imagens do layout (chamado durante a {@link #sweep}). */
    static void keep(TvLayout layout) {
        List<TvPicture> pictures = layout.pictures();
        for (int i = 0; i < pictures.size(); i++) {
            keep(pictures.get(i));
        }
    }

    /** Marca como em uso uma imagem (chamado durante a {@link #sweep}). */
    static void keep(TvPicture picture) {
        Picture entry = PICTURES.get(picture.hash());
        if (entry != null) {
            entry.generation = generation;
        }
    }

    /** Solta as imagens que nenhuma Tela com imagem nem o rascunho do editor usa. */
    static void sweep() {
        ticks = 0;
        if (PICTURES.isEmpty()) {
            return;
        }
        generation++;
        TvCanvases.keepPictures();
        TvEditorImage.keepPictures();
        TvPictureThumbs.keepPictures();
        Iterator<Picture> it = PICTURES.values().iterator();
        while (it.hasNext()) {
            Picture entry = it.next();
            if (entry.generation != generation) {
                release(entry);
                it.remove();
            }
        }
    }

    private static void release(Picture entry) {
        if (entry.texture >= 0) {
            TextureUtil.releaseTextureId(entry.texture);
            entry.texture = -1;
        }
        if (entry.animation != null) {
            if (entry.animation.ownsDecoded()) {
                // A animação solta os quadros dela, que são os mesmos da subida que talvez nem terminou.
                entry.decoded = null;
            }
            entry.animation.close();
            entry.animation = null;
            ANIMATED.remove(entry);
        }
        if (entry.decoded != null) {
            entry.decoded.close();
            entry.decoded = null;
        }
        UPLOADS.remove(entry);
    }

    /** Ao sair do mundo: solta tudo. */
    public static void clear() {
        for (Picture entry : PICTURES.values()) {
            release(entry);
        }
        PICTURES.clear();
        UPLOADS.clear();
        ANIMATED.clear();
        serverOff = false;
        ticks = 0;
    }

    /** Quantas texturas de imagem estão prontas (o e2e confere que saem). */
    public static int readyForTests() {
        int ready = 0;
        for (Picture entry : PICTURES.values()) {
            if (entry.state == State.READY) {
                ready++;
            }
        }
        return ready;
    }

    /** Se a imagem está pronta para desenhar. */
    public static boolean readyForTests(String hash) {
        Picture entry = PICTURES.get(hash);
        return entry != null && entry.state == State.READY;
    }

    /** O quadro do GIF que está na textura, ou -1 se a imagem não é um GIF animado pronto (o e2e confere que troca). */
    public static int shownFrameForTests(String hash) {
        Picture entry = PICTURES.get(hash);
        return entry == null || entry.animation == null || entry.state != State.READY ? -1 : entry.animation.shown;
    }

    /** A memória dos GIFs abertos inteiros (o e2e confere que volta a 0 quando eles saem). */
    public static long gifRamForTests() {
        return TvGifAnimation.ramUsedForTests();
    }
}
