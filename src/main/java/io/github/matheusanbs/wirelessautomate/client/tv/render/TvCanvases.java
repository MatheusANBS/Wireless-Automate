package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.matheusanbs.wirelessautomate.ClientConfig;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.compat.sodium.SodiumSprites;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * As imagens das Telas à vista, por posição, e a fila das sujas. O BER pede o tipo de desenho a cada quadro
 * ({@link #image}: uma consulta no mapa e uma comparação); o redesenho só acontece no começo do quadro, com a Tela
 * suja (layout novo, números novos, resolução, qualidade ou idioma mudados), dentro de {@link #FRAME_PIXELS} pixels
 * de textura por quadro (criar ou redimensionar conta {@link #ALLOC_WEIGHT} vezes) e de {@link #MAX_PER_FRAME}
 * Telas, com pelo menos uma sempre atendida; quem espera mostra a imagem anterior (ou o vidro apagado, sem nenhuma).
 * Sem Tela suja, {@link #onFrame} é a checagem da fila vazia.
 * <p>
 * A resolução de cada Tela segue o tamanho em que ela aparece: a cada {@link #EVAL_TICKS} ticks, as Telas vistas
 * nos últimos {@link #SEEN_TICKS} passam pelo {@link TvBudget} (desejado, histerese e o orçamento
 * {@code tv.vramBudgetMb}), com no máximo {@link TvBudget#MAX_RESIZES} redimensionamentos por avaliação. Uma Tela nova
 * escolhe a resolução no primeiro redesenho, já dentro do orçamento. A cada {@link #SWEEP_TICKS} ticks solta as
 * imagens que o BER não pede há {@link #IDLE_TICKS}. Os nomes das texturas ({@code wirelessautomate:tv/<n>}) são
 * reaproveitados, para o tipo de render memorizado por nome não crescer sem limite. Só na thread do cliente.
 */
public final class TvCanvases {
    private static final int MAX_PER_FRAME = 4;
    private static final long FRAME_PIXELS = 4_000_000L;
    private static final int ALLOC_WEIGHT = 2;
    private static final int EVAL_TICKS = 10;
    private static final int SEEN_TICKS = 20;
    private static final int SWEEP_TICKS = 100;
    private static final int IDLE_TICKS = 200;
    private static final long MB = 1024L * 1024;
    private static final Long2ObjectOpenHashMap<TvCanvas> CANVASES = new Long2ObjectOpenHashMap<>();
    private static final LongArrayFIFOQueue DIRTY = new LongArrayFIFOQueue();
    private static final LongOpenHashSet QUEUED = new LongOpenHashSet();
    /** As Telas cuja última imagem tinha uma regra de piscar valendo, atualizado a cada redesenho. */
    private static final LongOpenHashSet BLINKING = new LongOpenHashSet();
    private static final IntArrayFIFOQueue FREE_SLOTS = new IntArrayFIFOQueue();
    private static final List<ResourceLocation> IDS = new ArrayList<>();
    /** O rascunho da avaliação, reusado (cresce até o número de Telas vistas ao mesmo tempo). */
    private static TvCanvas[] evalCanvases = new TvCanvas[16];
    private static int[] evalWidths = new int[16];
    private static int[] evalHeights = new int[16];
    private static int[] evalCurrent = new int[16];
    private static int[] evalFitted = new int[16];
    private static double[] evalScreen = new double[16];
    private static final int[] CHOSEN = new int[TvBudget.MAX_RESIZES];
    private static int quality = ClientConfig.TV_PIXELS_PER_BLOCK.getDefault();
    /** A memória de vídeo de todas as imagens, mantida a cada redesenho e a cada liberação. */
    private static long allocatedBytes;
    private static int ticks;
    /** O bloco do piscar em que o último tick estava ({@link TvPaintMath#blinkSlot}). */
    private static long blinkSlot = Long.MIN_VALUE;
    private static String language = "";
    private static char decimalSep = '.';
    private static char groupSep = ',';
    /** Quantos redesenhos houve, o tempo somado e o maior, em ns (o e2e de desempenho lê e zera). */
    public static long paintsForTests;
    public static long paintNanosForTests;
    public static long maxPaintNanosForTests;
    /**
     * Com o gancho ligado (só o e2e de desempenho), o maior intervalo entre dois quadros e o maior trabalho do
     * {@link #onFrame} num quadro, em ns; desligado, custa a leitura do campo.
     */
    public static boolean measureFramesForTests;
    public static long maxFrameNanosForTests;
    public static long maxFrameWorkNanosForTests;
    private static long lastFrameNanos;

    private TvCanvases() {
    }

    /** Marca a imagem da Tela em {@code pos} para redesenhar no próximo quadro; sem repetir na fila. */
    public static void markDirty(long pos) {
        if (QUEUED.add(pos)) {
            DIRTY.enqueue(pos);
        }
    }

    /**
     * O tipo de desenho da imagem da Tela para o BER, ou {@code null} enquanto ela não foi desenhada. Cria a imagem
     * (sem GL) na primeira vez e a marca suja quando a revisão do layout mudou; até o redesenho, a imagem velha
     * continua valendo.
     */
    public static @Nullable RenderType image(TvBlockEntity tv, long gameTime) {
        long pos = tv.getBlockPos().asLong();
        TvCanvas canvas = CANVASES.get(pos);
        if (canvas == null) {
            int slot = slot();
            canvas = new TvCanvas(slot, IDS.get(slot), tv);
            CANVASES.put(pos, canvas);
            markDirty(pos);
        } else if (canvas.tv != tv) {
            canvas.tv = tv;
            markDirty(pos);
        } else if (canvas.revision != tv.revision()) {
            markDirty(pos);
        }
        canvas.lastWanted = gameTime;
        return canvas.painted() ? canvas.renderType : null;
    }

    private static int slot() {
        if (!FREE_SLOTS.isEmpty()) {
            return FREE_SLOTS.dequeueInt();
        }
        int slot = IDS.size();
        IDS.add(WirelessAutomate.id("tv/" + slot));
        return slot;
    }

    /**
     * No começo de cada quadro, antes do jogo desenhar: redesenha as Telas sujas da frente da fila enquanto couberem
     * em {@link #FRAME_PIXELS} e {@link #MAX_PER_FRAME}; a primeira sempre passa.
     */
    public static void onFrame() {
        if (measureFramesForTests) {
            long now = System.nanoTime();
            if (lastFrameNanos != 0) {
                maxFrameNanosForTests = Math.max(maxFrameNanosForTests, now - lastFrameNanos);
            }
            lastFrameNanos = now;
        }
        if (SodiumSprites.AVAILABLE && Minecraft.getInstance().level != null) {
            TvTimed.markIconSprites(Minecraft.getInstance().level.getGameTime());
        }
        if (DIRTY.isEmpty()) {
            return;
        }
        long frameStart = System.nanoTime();
        Minecraft minecraft = Minecraft.getInstance();
        separators(minecraft);
        long budget = ClientConfig.TV_VRAM_BUDGET_MB.get() * MB;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Matrix4f projection = null;
        VertexSorting sorting = null;
        long pixels = 0;
        int painted = 0;
        while (painted < MAX_PER_FRAME && !DIRTY.isEmpty()) {
            long pos = DIRTY.firstLong();
            TvCanvas canvas = CANVASES.get(pos);
            if (canvas == null || canvas.tv.isRemoved()) {
                DIRTY.dequeueLong();
                QUEUED.remove(pos);
                if (canvas != null) {
                    release(minecraft, canvas);
                    CANVASES.remove(pos);
                }
                BLINKING.remove(pos);
                TvTimed.forget(pos);
                continue;
            }
            if (canvas.wantedPpb == 0) {
                canvas.wantedPpb = TvBudget.desired(screenPpb(minecraft, canvas.tv, camera), quality,
                        canvas.tv.area().width(), canvas.tv.area().height(), RenderSystem.maxSupportedTextureSize());
            }
            long cost = withinBudget(canvas, budget);
            if (painted > 0 && pixels + cost > FRAME_PIXELS) {
                break;
            }
            DIRTY.dequeueLong();
            QUEUED.remove(pos);
            if (projection == null) {
                projection = RenderSystem.getProjectionMatrix();
                sorting = RenderSystem.getVertexSorting();
            }
            long before = canvas.bytes;
            long start = System.nanoTime();
            canvas.paint(minecraft, decimalSep, groupSep);
            long spent = System.nanoTime() - start;
            allocatedBytes += canvas.bytes - before;
            if (canvas.blinking) {
                BLINKING.add(pos);
            } else {
                BLINKING.remove(pos);
            }
            TvTimed.track(pos, canvas.graphing, canvas.clocking, canvas.iconing);
            paintsForTests++;
            paintNanosForTests += spent;
            maxPaintNanosForTests = Math.max(maxPaintNanosForTests, spent);
            pixels += cost;
            painted++;
        }
        if (projection != null) {
            RenderSystem.setProjectionMatrix(projection, sorting);
            minecraft.getMainRenderTarget().bindWrite(true);
        }
        maxFrameWorkNanosForTests = Math.max(maxFrameWorkNanosForTests, System.nanoTime() - frameStart);
    }

    /**
     * Baixa o {@code wantedPpb} da Tela pela metade até a textura caber no orçamento com as outras (sem descer de
     * {@link TvBudget#FLOOR_PPB}) e devolve o custo do redesenho em pixels de textura, {@link #ALLOC_WEIGHT} vezes
     * quando a textura vai ser criada ou mudar de tamanho.
     */
    private static long withinBudget(TvCanvas canvas, long budget) {
        TvArea area = canvas.tv.area();
        int w = area.width();
        int h = area.height();
        int cap = TvBudget.maxPpb(w, h, RenderSystem.maxSupportedTextureSize());
        long others = allocatedBytes - canvas.bytes;
        int wanted = Math.min(canvas.wantedPpb, cap);
        while (wanted > TvBudget.FLOOR_PPB && others + TvBudget.bytes(w, h, wanted) > budget) {
            wanted /= 2;
        }
        canvas.wantedPpb = wanted;
        long pixels = (long) w * h * wanted * wanted;
        boolean allocates = wanted != canvas.ppb || canvas.bytes != TvBudget.bytes(w, h, wanted);
        return allocates ? pixels * ALLOC_WEIGHT : pixels;
    }

    /** Os pixels da janela por bloco da Tela, pela distância da câmera ao centro da área e pelo ângulo. */
    private static double screenPpb(Minecraft minecraft, TvBlockEntity tv, Vec3 camera) {
        AABB box = tv.renderBox();
        double dx = (box.minX + box.maxX) / 2 - camera.x;
        double dy = (box.minY + box.maxY) / 2 - camera.y;
        double dz = (box.minZ + box.maxZ) / 2 - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        BlockState state = tv.getBlockState();
        double cosine = 1;
        if (distance > 1e-6 && state.hasProperty(TvBlock.FACING)) {
            Direction facing = state.getValue(TvBlock.FACING);
            cosine = (facing.getStepX() * dx + facing.getStepZ() * dz) / distance;
        }
        return TvBudget.screenPixelsPerBlock(minecraft.getWindow().getHeight(), distance,
                minecraft.options.fov().get(), cosine);
    }

    /**
     * A cada tick do cliente, com alguma imagem: confere a qualidade e o idioma (mudados, tudo fica sujo e a
     * qualidade nova escolhe a resolução de novo), a cada {@link #EVAL_TICKS} avalia a resolução das Telas à vista
     * e a cada {@link #SWEEP_TICKS} solta as imagens que o BER não pede há {@link #IDLE_TICKS}.
     */
    public static void onClientTick() {
        TvArtTextures.onClientTick();
        if (CANVASES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int configured = ClientConfig.TV_PIXELS_PER_BLOCK.get();
        boolean qualityChanged = configured != quality;
        quality = configured;
        if (qualityChanged) {
            for (TvCanvas canvas : CANVASES.values()) {
                canvas.wantedPpb = 0;
            }
        }
        if (separators(minecraft) || qualityChanged) {
            LongIterator it = CANVASES.keySet().iterator();
            while (it.hasNext()) {
                markDirty(it.nextLong());
            }
        }
        if (minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (++ticks % EVAL_TICKS == 0) {
            evaluate(minecraft, now);
        }
        blink(now);
        if (ticks < SWEEP_TICKS) {
            return;
        }
        ticks = 0;
        ObjectIterator<Long2ObjectMap.Entry<TvCanvas>> it = CANVASES.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            TvCanvas canvas = it.next().getValue();
            if (now - canvas.lastWanted > IDLE_TICKS || now < canvas.lastWanted) {
                release(minecraft, canvas);
                BLINKING.remove(canvas.tv.getBlockPos().asLong());
                TvTimed.forget(canvas.tv.getBlockPos().asLong());
                it.remove();
            }
        }
    }

    /**
     * Na troca da fase do piscar ({@link TvPaintMath#BLINK_TICKS} ticks, 2 por segundo), marca suja só cada Tela do
     * {@link #BLINKING} vista nos últimos {@link #SEEN_TICKS} ticks: o resto das Telas não é tocado. Sem nenhuma Tela
     * piscando, a conta de um tick é uma divisão e uma comparação.
     */
    private static void blink(long now) {
        long slot = TvPaintMath.blinkSlot(now);
        if (slot == blinkSlot) {
            return;
        }
        blinkSlot = slot;
        if (BLINKING.isEmpty()) {
            return;
        }
        LongIterator it = BLINKING.iterator();
        while (it.hasNext()) {
            long pos = it.nextLong();
            TvCanvas canvas = CANVASES.get(pos);
            if (canvas == null) {
                it.remove();
            } else if (now - canvas.lastWanted <= SEEN_TICKS) {
                markDirty(pos);
            }
        }
    }

    /**
     * A resolução das Telas vistas há pouco, já desenhadas e sem mudança pendente: o alvo com histerese de cada uma,
     * o corte do orçamento (descontado o que as outras imagens ocupam) e até {@link TvBudget#MAX_RESIZES} mudanças,
     * que só marcam a Tela suja (o redesenho é no {@link #onFrame}).
     */
    private static void evaluate(Minecraft minecraft, long now) {
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        int maxTexture = RenderSystem.maxSupportedTextureSize();
        int count = 0;
        long evaluatedBytes = 0;
        for (TvCanvas canvas : CANVASES.values()) {
            if (now - canvas.lastWanted > SEEN_TICKS || canvas.ppb == 0 || canvas.wantedPpb != canvas.ppb) {
                continue;
            }
            if (count == evalCanvases.length) {
                grow(count * 2);
            }
            TvArea area = canvas.tv.area();
            double screen = screenPpb(minecraft, canvas.tv, camera);
            evalCanvases[count] = canvas;
            evalWidths[count] = area.width();
            evalHeights[count] = area.height();
            evalCurrent[count] = canvas.ppb;
            evalScreen[count] = screen;
            evalFitted[count] = TvBudget.target(canvas.ppb, screen, quality, area.width(), area.height(), maxTexture);
            evaluatedBytes += canvas.bytes;
            count++;
        }
        if (count == 0) {
            return;
        }
        long budget = ClientConfig.TV_VRAM_BUDGET_MB.get() * MB;
        long room = Math.max(0, budget - (allocatedBytes - evaluatedBytes));
        TvBudget.fit(count, evalWidths, evalHeights, evalScreen, evalFitted, room);
        int chosen = TvBudget.choose(count, evalWidths, evalHeights, evalCurrent, evalFitted, allocatedBytes, budget,
                CHOSEN);
        for (int k = 0; k < chosen; k++) {
            TvCanvas canvas = evalCanvases[CHOSEN[k]];
            canvas.wantedPpb = evalFitted[CHOSEN[k]];
            markDirty(canvas.tv.getBlockPos().asLong());
        }
        Arrays.fill(evalCanvases, 0, count, null);
    }

    private static void grow(int size) {
        evalCanvases = Arrays.copyOf(evalCanvases, size);
        evalWidths = Arrays.copyOf(evalWidths, size);
        evalHeights = Arrays.copyOf(evalHeights, size);
        evalCurrent = Arrays.copyOf(evalCurrent, size);
        evalFitted = Arrays.copyOf(evalFitted, size);
        evalScreen = Arrays.copyOf(evalScreen, size);
    }

    /** Os separadores do idioma; verdadeiro se o idioma mudou desde a última vez. */
    private static boolean separators(Minecraft minecraft) {
        String code = minecraft.getLanguageManager().getSelected();
        if (code.equals(language)) {
            return false;
        }
        boolean changed = !language.isEmpty();
        language = code;
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag(code.replace('_', '-')));
        decimalSep = symbols.getDecimalSeparator();
        groupSep = symbols.getGroupingSeparator();
        return changed;
    }

    private static void release(Minecraft minecraft, TvCanvas canvas) {
        allocatedBytes -= canvas.bytes;
        canvas.release(minecraft);
        FREE_SLOTS.enqueue(canvas.slot);
    }

    /**
     * Marca suja a Tela em {@code pos} se o BER a pediu nos últimos {@link #SEEN_TICKS} ticks (o redesenho por tempo
     * do {@link TvTimed}); falso se a imagem não existe mais, e o conjunto do chamador a esquece.
     */
    static boolean refreshIfSeen(long pos, long now) {
        TvCanvas canvas = CANVASES.get(pos);
        if (canvas == null) {
            return false;
        }
        if (now - canvas.lastWanted <= SEEN_TICKS) {
            markDirty(pos);
        }
        return true;
    }

    /** Marca no Sodium as texturas animadas da Tela em {@code pos}, se o BER a pediu nos últimos {@link #SEEN_TICKS} ticks. */
    static void markSpritesIfSeen(long pos, long now) {
        TvCanvas canvas = CANVASES.get(pos);
        if (canvas != null && now - canvas.lastWanted <= SEEN_TICKS) {
            for (TextureAtlasSprite sprite : canvas.sprites) {
                SodiumSprites.mark(sprite);
            }
        }
    }

    /** Quantas Telas têm uma regra de piscar valendo (gancho do e2e e dos testes). */
    public static int blinkingForTests() {
        return BLINKING.size();
    }

    /** A memória de vídeo das imagens, em bytes (o e2e de desempenho lê). */
    public static long allocatedBytesForTests() {
        return allocatedBytes;
    }

    /** Ao trocar de dimensão e ao sair do mundo: solta todas as imagens (a chave é só a posição). */
    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (TvCanvas canvas : CANVASES.values()) {
            release(minecraft, canvas);
        }
        CANVASES.clear();
        CANVASES.trim();
        DIRTY.clear();
        QUEUED.clear();
        BLINKING.clear();
        TvTimed.clear();
        blinkSlot = Long.MIN_VALUE;
        allocatedBytes = 0;
        ticks = 0;
        TvArtTextures.sweep();
    }

    /** Na varredura do {@link TvArtTextures}: marca as artes dos layouts das Telas com imagem que ainda existem. */
    static void keepArts() {
        for (TvCanvas canvas : CANVASES.values()) {
            if (!canvas.tv.isRemoved()) {
                TvArtTextures.keep(canvas.tv.layout());
            }
        }
    }

    /** Quantas texturas de pixel art existem no cliente (o e2e confere que saem quando ninguém as usa). */
    public static int artTexturesForTests() {
        return TvArtTextures.countForTests();
    }
}
