package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.tv.TvClock;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * O redesenho por tempo, no mesmo esquema do piscar: as Telas cuja última imagem tinha um Gráfico (redesenha a cada
 * segundo), um número do relógio (redesenha quando o minuto do jogo muda), um Ícone animado (a cada 2 ticks) ou um GIF
 * animado (quando o quadro dele muda, {@link #gifChanged}) ficam em quatro conjuntos, atualizados a cada desenho pela
 * {@link TvCanvases}; na virada do segundo, do minuto ou do quadro só essas, e só se estão à vista, são marcadas
 * sujas. As Telas com GIF à vista também mantêm os GIFs delas tocando, a cada tick ({@link TvPictureTextures#seen}).
 * Também entrega o segundo aos {@link TvHistories} e avisa a miniatura do editor ({@link #editorVersion}). Sem Gráfico
 * nem relógio, o tick é duas divisões e duas comparações.
 * <p>
 * As Telas com pixel art animada ficam no {@link #ARTING} com o instante em que o quadro de alguma arte delas muda; a
 * cada quadro do jogo ({@link #animateArts}), só as que passaram desse instante e estão à vista redesenham, e o
 * redesenho guarda o instante seguinte. Sem arte animada, é a checagem do mapa vazio.
 */
public final class TvTimed {
    private static final LongOpenHashSet GRAPHING = new LongOpenHashSet();
    private static final LongOpenHashSet CLOCKING = new LongOpenHashSet();
    private static final LongOpenHashSet ICONING = new LongOpenHashSet();
    private static final LongOpenHashSet GIFING = new LongOpenHashSet();
    private static final Long2LongOpenHashMap ARTING = new Long2LongOpenHashMap();
    /** Os ticks entre dois redesenhos de um Ícone animado: 2, ou 10 vezes por segundo. */
    private static final int ICON_TICKS = 2;
    private static long second = Long.MIN_VALUE;
    private static long iconStep = Long.MIN_VALUE;
    private static int minute = -1;
    private static boolean editorGraph;
    private static boolean editorClock;
    private static boolean editorIcon;
    private static boolean editorGif;
    /** Quando a pixel art animada da miniatura do editor troca de quadro, ou {@link Long#MAX_VALUE} sem nenhuma. */
    private static long editorArtNext = Long.MAX_VALUE;
    private static int editorVersion;

    private TvTimed() {
    }

    /**
     * O que a última imagem da Tela em {@code pos} tinha: Gráfico, número do relógio, Ícone animado, GIF e quando a
     * pixel art animada dela troca de quadro ({@link Long#MAX_VALUE} sem nenhuma).
     */
    static void track(long pos, boolean graph, boolean clock, boolean icon, boolean gif, long artNext) {
        if (artNext != Long.MAX_VALUE) {
            ARTING.put(pos, artNext);
        } else {
            ARTING.remove(pos);
        }
        if (gif) {
            GIFING.add(pos);
        } else {
            GIFING.remove(pos);
        }
        if (icon) {
            ICONING.add(pos);
        } else {
            ICONING.remove(pos);
        }
        if (graph) {
            GRAPHING.add(pos);
        } else {
            GRAPHING.remove(pos);
        }
        if (clock) {
            CLOCKING.add(pos);
        } else {
            CLOCKING.remove(pos);
        }
    }

    static void forget(long pos) {
        GRAPHING.remove(pos);
        CLOCKING.remove(pos);
        ICONING.remove(pos);
        GIFING.remove(pos);
        ARTING.remove(pos);
    }

    /** A miniatura do editor tem um Gráfico, um número do relógio, um Ícone animado ou um GIF no que desenhou. */
    static void editor(boolean graph, boolean clock, boolean icon, boolean gif, long artNext) {
        editorArtNext = artNext;
        editorGraph = graph;
        editorClock = clock;
        editorIcon = icon;
        editorGif = gif;
    }

    /** O quadro do GIF {@code hash} mudou na textura: as Telas à vista que o mostram e a miniatura redesenham. */
    static void gifChanged(String hash) {
        if (editorGif) {
            editorVersion++;
        }
        if (GIFING.isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }
        long now = Minecraft.getInstance().level.getGameTime();
        LongIterator it = GIFING.iterator();
        while (it.hasNext()) {
            TvCanvases.refreshIfShows(it.nextLong(), hash, now);
        }
    }

    /** Sobe quando a miniatura precisa de um redesenho por tempo (o segundo ou o minuto virou). */
    public static int editorVersion() {
        return editorVersion;
    }

    /**
     * A cada quadro, com o Sodium e algum Ícone animado à vista: marca as texturas dele como ativas, porque o Sodium só
     * anima a que foi usada no quadro e o desenho da Tela acontece só a cada {@link #ICON_TICKS} ticks.
     */
    static void markIconSprites(long now) {
        if (ICONING.isEmpty()) {
            return;
        }
        LongIterator it = ICONING.iterator();
        while (it.hasNext()) {
            TvCanvases.markSpritesIfSeen(it.nextLong(), now);
        }
    }

    /**
     * A cada quadro do jogo, antes dos redesenhos: as Telas cuja pixel art animada já trocou de quadro redesenham se
     * estão à vista, e a miniatura do editor também.
     */
    static void animateArts() {
        if (ARTING.isEmpty() && editorArtNext == Long.MAX_VALUE) {
            return;
        }
        long now = Util.getMillis();
        if (editorArtNext <= now) {
            editorArtNext = Long.MAX_VALUE;
            editorVersion++;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (ARTING.isEmpty() || level == null) {
            return;
        }
        long tick = level.getGameTime();
        ObjectIterator<Long2LongMap.Entry> it = ARTING.long2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2LongMap.Entry entry = it.next();
            if (entry.getLongValue() <= now && !TvCanvases.refreshIfSeen(entry.getLongKey(), tick)) {
                it.remove();
            }
        }
    }

    /** A cada tick do cliente. */
    public static void onClientTick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (!GIFING.isEmpty()) {
            LongIterator it = GIFING.iterator();
            while (it.hasNext()) {
                if (!TvCanvases.seenGif(it.nextLong(), now)) {
                    it.remove();
                }
            }
        }
        if (editorGif) {
            TvEditorImage.seenPictures();
        }
        long nowSecond = Math.floorDiv(now, 20L);
        if (nowSecond != second) {
            second = nowSecond;
            TvHistories.sample(now);
            refresh(GRAPHING, now);
            if (editorGraph) {
                editorVersion++;
            }
        }
        long nowStep = Math.floorDiv(now, (long) ICON_TICKS);
        if (nowStep != iconStep) {
            iconStep = nowStep;
            refresh(ICONING, now);
            if (editorIcon) {
                editorVersion++;
            }
        }
        int nowMinute = TvClock.minuteOfDay(level.getDayTime());
        if (nowMinute != minute) {
            minute = nowMinute;
            refresh(CLOCKING, now);
            if (editorClock) {
                editorVersion++;
            }
        }
    }

    private static void refresh(LongOpenHashSet set, long now) {
        if (set.isEmpty()) {
            return;
        }
        LongIterator it = set.iterator();
        while (it.hasNext()) {
            if (!TvCanvases.refreshIfSeen(it.nextLong(), now)) {
                it.remove();
            }
        }
    }

    /** O quadro da última pixel art animada desenhada numa Tela ou na miniatura (gancho do e2e). */
    public static int artFrameForTests() {
        return TvPaintImage.lastArtFrame;
    }

    /**
     * Cria as texturas de todos os quadros da arte e devolve o tempo gasto, em ns, e quantas texturas ela tem (o e2e
     * mede a animação); a varredura solta as texturas depois, como as de qualquer arte que nada usa.
     */
    public static long[] uploadFramesForTests(TvPixelArt art) {
        long start = System.nanoTime();
        for (int frame = 0; frame < art.frames(); frame++) {
            TvArtTextures.renderType(art, frame);
        }
        long spent = System.nanoTime() - start;
        long switchStart = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            TvArtTextures.renderType(art, i % art.frames());
        }
        return new long[] {spent, TvArtTextures.framesForTests(art), (System.nanoTime() - switchStart) / 1000};
    }

    /** O editor fechou. */
    static void editorClosed() {
        editorGraph = false;
        editorClock = false;
        editorIcon = false;
        editorGif = false;
        editorArtNext = Long.MAX_VALUE;
        TvHistories.dropPreview();
    }

    /** Ao trocar de dimensão e ao sair do mundo. */
    public static void clear() {
        GRAPHING.clear();
        CLOCKING.clear();
        ICONING.clear();
        GIFING.clear();
        ARTING.clear();
        iconStep = Long.MIN_VALUE;
        TvHistories.clear();
        second = Long.MIN_VALUE;
        minute = -1;
    }
}
