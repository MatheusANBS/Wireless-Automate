package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvGraphHistory;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Os históricos dos Gráficos (e do "máximo visto" da Barra e do Medidor sem máximo fixo), só no cliente: um
 * {@link TvGraphHistory} por Tela × ligação pedida no desenho ({@link #wanted}), com um número por segundo lido por
 * {@link #sample}, chamado pelo {@link TvTimed} uma vez por segundo. A série nasce no primeiro desenho que a pede e
 * some {@link #IDLE_TICKS} depois do último (a Tela liberada ou fora de vista); sem série nenhuma, o {@code sample} é
 * um {@code isEmpty()}. A miniatura do editor tem as suas, à parte ({@code preview}), soltas ao fechar o editor.
 */
final class TvHistories {
    /** Um minuto sem desenho pedindo: a série é solta. */
    private static final long IDLE_TICKS = 1200;
    private static final Map<Key, Series> SERIES = new HashMap<>();

    private TvHistories() {
    }

    private record Key(long pos, boolean preview, TvBinding binding) {
    }

    private static final class Series {
        final TvGraphHistory history = new TvGraphHistory();
        TvLayout values;
        int revision;
        long lastWanted;
    }

    /**
     * O histórico da ligação para a Tela em {@code pos}, criado no primeiro pedido (já com o número de agora) e
     * renovado a cada desenho. {@code values} e {@code revision} dizem de onde ler o número a cada segundo.
     */
    static TvGraphHistory wanted(long pos, boolean preview, TvBinding binding, TvLayout values, int revision,
            long gameTime) {
        Key key = new Key(pos, preview, binding);
        Series series = SERIES.get(key);
        if (series == null) {
            series = new Series();
            SERIES.put(key, series);
            series.history.push(TvPaintNumbers.of(binding, values, pos, revision, preview));
        }
        series.values = values;
        series.revision = revision;
        series.lastWanted = gameTime;
        return series.history;
    }

    /** Uma vez por segundo: guarda o número de agora em cada série e solta as que ninguém pede há um minuto. */
    static void sample(long gameTime) {
        if (SERIES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Key, Series>> it = SERIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Series> entry = it.next();
            Series series = entry.getValue();
            if (gameTime - series.lastWanted > IDLE_TICKS || gameTime < series.lastWanted) {
                it.remove();
                continue;
            }
            Key key = entry.getKey();
            series.history.push(TvPaintNumbers.of(key.binding(), series.values, key.pos(), series.revision,
                    key.preview()));
        }
    }

    /** O editor fechou: solta as séries da miniatura. */
    static void dropPreview() {
        SERIES.keySet().removeIf(Key::preview);
    }

    /** Ao trocar de dimensão e ao sair do mundo. */
    static void clear() {
        SERIES.clear();
    }

    /** Quantas séries há (gancho do e2e e dos testes). */
    static int sizeForTests() {
        return SERIES.size();
    }
}
