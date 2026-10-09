package io.github.matheusanbs.wirelessautomate.bench;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Quem transporta numa tarefa do benchmark (docs/benchmark-logistics-network.md). A cena, o reenchimento e
 * as medições são os mesmos; só muda a peça posta nas máquinas.
 *
 * <ul>
 *   <li>{@code wa}: roteadores do Wireless Automate, com o orçamento da config.</li>
 *   <li>{@code wa-full}: os mesmos, com orçamento de {@link #FULL_BUDGET_MS} ms e sem o adaptativo:
 *       faz todo o trabalho no tick, como o modo síncrono do Logistics Network.</li>
 *   <li>{@code ln}: nós do Logistics Network no modo padrão dele (síncrono).</li>
 *   <li>{@code ln-rr}: os mesmos, com a distribuição "Equal Distribution" ({@code round_robin}) no lugar da
 *       padrão ({@code priority}), que manda tudo para os primeiros destinos.</li>
 *   <li>{@code ln-async}: os mesmos, com o planejamento assíncrono dele ligado.</li>
 * </ul>
 */
public enum BenchTransport {
    WA("wa"),
    WA_FULL("wa-full"),
    LN("ln"),
    LN_RR("ln-rr"),
    LN_ASYNC("ln-async");

    static final double FULL_BUDGET_MS = 50.0;

    public final String id;

    BenchTransport(String id) {
        this.id = id;
    }

    public boolean logisticsNetwork() {
        return this == LN || this == LN_RR || this == LN_ASYNC;
    }

    /** Modo de distribuição do canal do Logistics Network, no formato do codec dele. */
    public String lnDistribution() {
        return this == LN_RR ? "round_robin" : "priority";
    }

    public static @Nullable BenchTransport byId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (BenchTransport transport : values()) {
            if (transport.id.equals(key)) {
                return transport;
            }
        }
        return null;
    }
}
