package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/** O que uma peça Valor lê de uma rede e de um tipo: uma taxa por segundo ou uma contagem de estados. */
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
    REDSTONE("redstone", false);

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
        };
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
