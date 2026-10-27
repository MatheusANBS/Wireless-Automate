package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** O que uma peça lê de um armazenamento do mod ligado à Tela: a quantidade, a capacidade, a fração e as taxas. */
public enum TvStorageMetric implements StringRepresentable {
    STORED("stored", false),
    CAPACITY("capacity", false),
    FILL("fill", false),
    IN("in", true),
    OUT("out", true),
    BALANCE("balance", true);

    public static final Codec<TvStorageMetric> CODEC = StringRepresentable.fromEnum(TvStorageMetric::values);

    private final String id;
    private final boolean rate;

    TvStorageMetric(String id, boolean rate) {
        this.id = id;
        this.rate = rate;
    }

    /** Se é uma taxa por segundo (a unidade da peça vale) ou uma quantidade. */
    public boolean rate() {
        return rate;
    }

    /** Se precisa dos contadores de entra e sai, que só existem com o armazenamento vigiado. */
    public boolean watched() {
        return rate;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
