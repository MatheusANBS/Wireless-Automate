package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.client.tv.render.TvCanvases;
import io.github.matheusanbs.wirelessautomate.packet.TvValuesPayload;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Os números das Telas que chegaram do servidor, por posição, com a revisão do layout que o servidor leu. Cada
 * chegada marca a imagem da Tela como suja. Guarda uma entrada por Tela vista na dimensão atual: o
 * {@link TvClientEvents} limpa tudo ao trocar de dimensão e ao sair do mundo. Só na thread do cliente.
 */
public final class TvClientValues {
    private static final Long2ObjectOpenHashMap<Entry> VALUES = new Long2ObjectOpenHashMap<>();
    /** Sobe a cada chegada e a cada limpeza: o editor compara para saber se a miniatura precisa dos números novos. */
    private static int version;

    private TvClientValues() {
    }

    private static final class Entry {
        int revision;
        double[] values;
        long[] stale;
    }

    public static void receive(TvValuesPayload payload) {
        long pos = payload.pos().asLong();
        Entry entry = VALUES.get(pos);
        if (entry == null) {
            entry = new Entry();
            VALUES.put(pos, entry);
        }
        entry.revision = payload.revision();
        entry.values = payload.values();
        entry.stale = payload.stale();
        version++;
        TvCanvases.markDirty(pos);
    }

    /** O número {@code index} da Tela, ou {@code NaN} se não chegou ou é de outra revisão do layout. */
    public static double value(long pos, int revision, int index) {
        Entry entry = VALUES.get(pos);
        if (entry == null || entry.revision != revision || index < 0 || index >= entry.values.length) {
            return Double.NaN;
        }
        return entry.values[index];
    }

    /** Se o número {@code index} é o último lido de um armazenamento num chunk descarregado (a Tela o pinta em Apagado). */
    public static boolean stale(long pos, int revision, int index) {
        Entry entry = VALUES.get(pos);
        if (entry == null || entry.revision != revision || index < 0 || index >= entry.values.length) {
            return false;
        }
        int word = index >> 6;
        return word < entry.stale.length && (entry.stale[word] >>> (index & 63) & 1L) != 0;
    }

    /** O maior número que chegou para a Tela em {@code pos}, ou {@code NaN} sem nenhum (gancho do e2e). */
    public static double maxForTests(long pos) {
        Entry entry = VALUES.get(pos);
        double max = Double.NaN;
        if (entry != null) {
            for (double value : entry.values) {
                if (Double.isNaN(max) || value > max) {
                    max = value;
                }
            }
        }
        return max;
    }

    public static int version() {
        return version;
    }

    /** Ao trocar de dimensão e ao sair do mundo. */
    public static void clear() {
        version++;
        VALUES.clear();
        VALUES.trim();
    }
}
