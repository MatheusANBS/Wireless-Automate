package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** O que uma peça lê do relógio do jogo: a hora do dia e o dia. Lido no cliente, sem pacote. */
public enum TvClockMetric implements StringRepresentable {
    /** Minutos desde a meia-noite do relógio do jogo (0 a 1439): o tempo 0 do mundo é 06:00. */
    TIME("time"),
    /** O dia do mundo, contado de 0. */
    DAY("day");

    public static final Codec<TvClockMetric> CODEC = StringRepresentable.fromEnum(TvClockMetric::values);

    private final String id;

    TvClockMetric(String id) {
        this.id = id;
    }

    /** O valor com o tempo do dia do mundo ({@code Level.getDayTime}). */
    public double read(long dayTime) {
        return switch (this) {
            case TIME -> TvClock.minuteOfDay(dayTime);
            case DAY -> TvClock.day(dayTime);
        };
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
