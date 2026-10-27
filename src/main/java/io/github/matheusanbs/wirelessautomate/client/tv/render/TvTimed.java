package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.tv.TvClock;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * O redesenho por tempo, no mesmo esquema do piscar: as Telas cuja última imagem tinha um Gráfico (redesenha a cada
 * segundo) ou um número do relógio (redesenha quando o minuto do jogo muda) ou um Ícone animado (a cada 2 ticks) ficam em três conjuntos, atualizados a
 * cada desenho pela {@link TvCanvases}; na virada do segundo ou do minuto só essas, e só se estão à vista, são
 * marcadas sujas. Também entrega o segundo aos {@link TvHistories} e avisa a miniatura do editor
 * ({@link #editorVersion}). Sem Gráfico nem relógio, o tick é duas divisões e duas comparações.
 */
public final class TvTimed {
    private static final LongOpenHashSet GRAPHING = new LongOpenHashSet();
    private static final LongOpenHashSet CLOCKING = new LongOpenHashSet();
    private static final LongOpenHashSet ICONING = new LongOpenHashSet();
    /** Os ticks entre dois redesenhos de um Ícone animado: 2, ou 10 vezes por segundo. */
    private static final int ICON_TICKS = 2;
    private static long second = Long.MIN_VALUE;
    private static long iconStep = Long.MIN_VALUE;
    private static int minute = -1;
    private static boolean editorGraph;
    private static boolean editorClock;
    private static boolean editorIcon;
    private static int editorVersion;

    private TvTimed() {
    }

    /** O que a última imagem da Tela em {@code pos} tinha: Gráfico e número do relógio. */
    static void track(long pos, boolean graph, boolean clock, boolean icon) {
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
    }

    /** A miniatura do editor tem um Gráfico ou um número do relógio no que desenhou. */
    static void editor(boolean graph, boolean clock, boolean icon) {
        editorGraph = graph;
        editorClock = clock;
        editorIcon = icon;
    }

    /** Sobe quando a miniatura precisa de um redesenho por tempo (o segundo ou o minuto virou). */
    public static int editorVersion() {
        return editorVersion;
    }

    /** A cada tick do cliente. */
    public static void onClientTick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
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

    /** O editor fechou. */
    static void editorClosed() {
        editorGraph = false;
        editorClock = false;
        editorIcon = false;
        TvHistories.dropPreview();
    }

    /** Ao trocar de dimensão e ao sair do mundo. */
    public static void clear() {
        GRAPHING.clear();
        CLOCKING.clear();
        ICONING.clear();
        iconStep = Long.MIN_VALUE;
        TvHistories.clear();
        second = Long.MIN_VALUE;
        minute = -1;
    }
}
