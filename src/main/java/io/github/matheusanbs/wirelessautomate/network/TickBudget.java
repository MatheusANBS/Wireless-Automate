package io.github.matheusanbs.wirelessautomate.network;

/**
 * Orçamento de tempo do mod por tick. Quem processa rotas pergunta {@link #hasTime} entre um lote
 * e outro; o que não couber continua no tick seguinte. O teto encolhe quando o MSPT do servidor
 * sobe (ver {@link #adapt}).
 *
 * <p>Lógica pura, sem classes do Minecraft, para ser testada com JUnit.
 */
public final class TickBudget {
    /** Abaixo deste MSPT o teto fica no valor configurado. */
    static final double MSPT_RELAXED = 40.0;
    /** A partir deste MSPT o teto fica no mínimo. */
    static final double MSPT_OVERLOADED = 50.0;
    static final double MIN_FACTOR = 0.25;
    private static final double AVERAGE_WEIGHT = 0.05;

    private long baseNanos;
    private double factor = 1.0;
    private long startedAt;
    private long lastUsedNanos;
    private double averageUsedNanos;

    public TickBudget(long baseNanos) {
        setBaseNanos(baseNanos);
    }

    public void setBaseNanos(long baseNanos) {
        if (baseNanos <= 0) {
            throw new IllegalArgumentException("baseNanos deve ser positivo: " + baseNanos);
        }
        this.baseNanos = baseNanos;
    }

    public long baseNanos() {
        return baseNanos;
    }

    /** Teto efetivo deste tick, já com o ajuste pelo MSPT. */
    public long limitNanos() {
        return (long) (baseNanos * factor);
    }

    /** Ajusta o teto ao MSPT médio do servidor, de 100% (até 40 ms) a 25% (50 ms ou mais). */
    public void adapt(double serverMspt) {
        if (serverMspt <= MSPT_RELAXED) {
            factor = 1.0;
        } else if (serverMspt >= MSPT_OVERLOADED) {
            factor = MIN_FACTOR;
        } else {
            double t = (serverMspt - MSPT_RELAXED) / (MSPT_OVERLOADED - MSPT_RELAXED);
            factor = 1.0 - t * (1.0 - MIN_FACTOR);
        }
    }

    public void begin(long now) {
        startedAt = now;
    }

    public boolean hasTime(long now) {
        return now - startedAt < limitNanos();
    }

    public void end(long now) {
        lastUsedNanos = now - startedAt;
        averageUsedNanos += (lastUsedNanos - averageUsedNanos) * AVERAGE_WEIGHT;
    }

    public long lastUsedNanos() {
        return lastUsedNanos;
    }

    public double averageUsedNanos() {
        return averageUsedNanos;
    }
}
