package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import io.github.matheusanbs.wirelessautomate.network.FlowSampler;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.storage.StorageMath;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * O que uma peça Valor lê de uma rede e de um tipo: uma taxa por segundo, uma contagem de estados ou o guardado nos
 * Armazéns da rede (conteúdo, capacidade e a fração), que não vem dos {@link FlowStats} e sim do pedido ao
 * {@link FlowSampler#requestHeld}.
 */
public enum TvMetric implements StringRepresentable {
    IN("in", true),
    OUT("out", true),
    STORED("stored", true),
    RELEASED("released", true),
    BALANCE("balance", true),
    MOVING("moving", false),
    EMPTY("empty", false),
    WAITING("waiting", false),
    FULL("full", false),
    REDSTONE("redstone", false),
    HELD("held", false),
    HELD_CAPACITY("held_capacity", false),
    HELD_FILL("held_fill", false);

    public static final Codec<TvMetric> CODEC = StringRepresentable.fromEnum(TvMetric::values);

    private final String id;
    private final boolean rate;

    TvMetric(String id, boolean rate) {
        this.id = id;
        this.rate = rate;
    }

    /** Se é uma taxa por segundo (a unidade da peça vale) ou uma contagem. */
    public boolean rate() {
        return rate;
    }

    /** Se é do guardado nos Armazéns (lê o {@link FlowSampler.Held}, não os fluxos). */
    public boolean held() {
        return this == HELD || this == HELD_CAPACITY || this == HELD_FILL;
    }

    /** Se o número é uma fração de 0 a 1 (a peça mostra em porcentagem). */
    public boolean fraction() {
        return this == HELD_FILL;
    }

    /** O valor de uma métrica do guardado; {@code NaN} (traço) sem número ainda. */
    public double readHeld(FlowSampler.@Nullable Held held) {
        if (held == null) {
            return Double.NaN;
        }
        return switch (this) {
            case HELD -> held.stored();
            case HELD_CAPACITY -> held.capacity();
            case HELD_FILL -> StorageMath.fraction(held.stored(), held.capacity());
            default -> Double.NaN;
        };
    }

    /** O valor desta métrica nos fluxos dados; 0 sem fluxos. */
    public double read(@Nullable FlowStats stats) {
        if (stats == null) {
            return 0;
        }
        FlowStats.States states = stats.states();
        return switch (this) {
            case IN -> stats.producedPerSecond();
            case OUT -> stats.consumedPerSecond();
            case STORED -> stats.storedPerSecond();
            case RELEASED -> stats.releasedPerSecond();
            case BALANCE -> stats.balancePerSecond();
            case MOVING -> states.sourcesMoving() + states.destinationsMoving();
            case EMPTY -> states.sourcesEmpty();
            case WAITING -> states.sourcesWaiting();
            case FULL -> states.destinationsFull();
            case REDSTONE -> states.redstoneStopped();
            case HELD, HELD_CAPACITY, HELD_FILL -> Double.NaN;
        };
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
