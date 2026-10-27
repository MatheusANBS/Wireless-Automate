package io.github.matheusanbs.wirelessautomate.tv;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.UUID;

/**
 * O que o servidor guarda de uma Tela para mandar os números: o último valor de cada ligação, a versão dele, de que
 * amostra e de que layout veio, e a versão que cada jogador perto já recebeu. Só no servidor, criado sob demanda.
 * O vetor {@link #values} nunca é alterado depois de publicado (o pacote o leva até a rede): uma mudança o troca.
 */
public final class TvFeed {
    private static final double[] NONE = new double[0];

    /** Um valor por ligação de {@link TvLayout#bindings()}. */
    double[] values = NONE;
    /** Sobe a cada mudança; um jogador que viu outra versão recebe o pacote. Começa em 1, o padrão do mapa é 0. */
    int valuesVersion = 1;
    /** O {@code sampledAt} do relatório lido por último. */
    long readSample = Long.MIN_VALUE;
    /** A revisão do layout lida por último. */
    int readRevision = -1;
    /** Jogador → versão que recebeu. */
    final Object2IntOpenHashMap<UUID> seen = new Object2IntOpenHashMap<>();
    /** Pacotes mandados por esta Tela, para os testes. */
    long packets;

    /** Os últimos valores lidos; vazio antes da primeira leitura. */
    public double[] values() {
        return values;
    }

    public int valuesVersion() {
        return valuesVersion;
    }

    public long packets() {
        return packets;
    }

    /** Volta ao vazio: sem ligação ou sem ninguém perto não há o que guardar. */
    void clear() {
        values = NONE;
        readSample = Long.MIN_VALUE;
        readRevision = -1;
        seen.clear();
    }
}
