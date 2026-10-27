package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.matheusanbs.wirelessautomate.ClientConfig;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
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
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * As imagens das Telas à vista, por posição, e a fila das sujas. O BER pede a textura a cada quadro
 * ({@link #texture}: uma consulta no mapa e duas comparações); o redesenho só acontece no começo do quadro, com a
 * Tela suja (layout novo, números novos, pixels por bloco ou idioma mudados), no máximo {@link #MAX_PER_FRAME} por
 * quadro. Sem Tela suja, {@link #onFrame} é a checagem da fila vazia. A cada {@link #SWEEP_TICKS} ticks solta as
 * imagens que o BER não pede há {@link #IDLE_TICKS}. Os nomes das texturas ({@code wirelessautomate:tv/<n>}) são
 * reaproveitados, para o tipo de render memorizado por nome não crescer sem limite. Só na thread do cliente.
 */
public final class TvCanvases {
    private static final int MAX_PER_FRAME = 4;
    private static final int SWEEP_TICKS = 100;
    private static final int IDLE_TICKS = 200;
    private static final Long2ObjectOpenHashMap<TvCanvas> CANVASES = new Long2ObjectOpenHashMap<>();
    private static final LongArrayFIFOQueue DIRTY = new LongArrayFIFOQueue();
    private static final LongOpenHashSet QUEUED = new LongOpenHashSet();
    private static final IntArrayFIFOQueue FREE_SLOTS = new IntArrayFIFOQueue();
    private static final List<ResourceLocation> IDS = new ArrayList<>();
    private static int ppb = TvArea.PIXELS_PER_BLOCK;
    private static int ticks;
    private static String language = "";
    private static char decimalSep = '.';
    private static char groupSep = ',';

    private TvCanvases() {
    }

    /** Marca a imagem da Tela em {@code pos} para redesenhar no próximo quadro; sem repetir na fila. */
    public static void markDirty(long pos) {
        if (QUEUED.add(pos)) {
            DIRTY.enqueue(pos);
        }
    }

    /**
     * A textura da Tela para o BER, ou {@code null} enquanto ela não foi desenhada. Cria a imagem (sem GL) na
     * primeira vez e a marca suja quando a revisão do layout ou os pixels por bloco mudaram; até o redesenho, a
     * imagem velha continua valendo.
     */
    public static @Nullable ResourceLocation texture(TvBlockEntity tv, long gameTime) {
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
        } else if (canvas.revision != tv.revision() || canvas.configuredPpb != ppb) {
            markDirty(pos);
        }
        canvas.lastWanted = gameTime;
        return canvas.painted() ? canvas.id : null;
    }

    private static int slot() {
        if (!FREE_SLOTS.isEmpty()) {
            return FREE_SLOTS.dequeueInt();
        }
        int slot = IDS.size();
        IDS.add(WirelessAutomate.id("tv/" + slot));
        return slot;
    }

    /** No começo de cada quadro, antes do jogo desenhar: redesenha até {@link #MAX_PER_FRAME} Telas sujas. */
    public static void onFrame() {
        if (DIRTY.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ppb = ClientConfig.TV_PIXELS_PER_BLOCK.get();
        separators(minecraft);
        Matrix4f projection = null;
        VertexSorting sorting = null;
        int painted = 0;
        while (painted < MAX_PER_FRAME && !DIRTY.isEmpty()) {
            long pos = DIRTY.dequeueLong();
            QUEUED.remove(pos);
            TvCanvas canvas = CANVASES.get(pos);
            if (canvas == null) {
                continue;
            }
            if (canvas.tv.isRemoved()) {
                release(minecraft, canvas);
                CANVASES.remove(pos);
                continue;
            }
            if (projection == null) {
                projection = RenderSystem.getProjectionMatrix();
                sorting = RenderSystem.getVertexSorting();
            }
            canvas.paint(minecraft, ppb, decimalSep, groupSep);
            painted++;
        }
        if (projection != null) {
            RenderSystem.setProjectionMatrix(projection, sorting);
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    /**
     * A cada tick do cliente, com alguma imagem: confere os pixels por bloco e o idioma (mudados, tudo fica sujo) e,
     * a cada {@link #SWEEP_TICKS}, solta as imagens que o BER não pede há {@link #IDLE_TICKS}.
     */
    public static void onClientTick() {
        if (CANVASES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ppb = ClientConfig.TV_PIXELS_PER_BLOCK.get();
        if (separators(minecraft)) {
            LongIterator it = CANVASES.keySet().iterator();
            while (it.hasNext()) {
                markDirty(it.nextLong());
            }
        }
        if (++ticks < SWEEP_TICKS || minecraft.level == null) {
            return;
        }
        ticks = 0;
        long now = minecraft.level.getGameTime();
        ObjectIterator<Long2ObjectMap.Entry<TvCanvas>> it = CANVASES.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            TvCanvas canvas = it.next().getValue();
            if (now - canvas.lastWanted > IDLE_TICKS || now < canvas.lastWanted) {
                release(minecraft, canvas);
                it.remove();
            }
        }
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
        canvas.release(minecraft);
        FREE_SLOTS.enqueue(canvas.slot);
    }

    /** Ao sair do mundo: solta todas as imagens. */
    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (TvCanvas canvas : CANVASES.values()) {
            release(minecraft, canvas);
        }
        CANVASES.clear();
        CANVASES.trim();
        DIRTY.clear();
        QUEUED.clear();
        ticks = 0;
    }
}
