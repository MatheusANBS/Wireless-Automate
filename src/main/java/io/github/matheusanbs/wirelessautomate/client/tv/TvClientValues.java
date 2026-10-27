package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.client.tv.render.TvCanvases;
import io.github.matheusanbs.wirelessautomate.packet.TvValuesPayload;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Os números das Telas que chegaram do servidor, por posição, com a revisão do layout que o servidor leu. Cada
 * chegada marca a imagem da Tela como suja. Guarda uma entrada por Tela vista, até sair do mundo. Só na thread do
 * cliente.
 */
public final class TvClientValues {
    private static final Long2ObjectOpenHashMap<Entry> VALUES = new Long2ObjectOpenHashMap<>();

    private TvClientValues() {
    }

    private static final class Entry {
        int revision;
        double[] values;
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

    /** Ao sair do mundo. */
    public static void clear() {
        VALUES.clear();
        VALUES.trim();
    }
}
