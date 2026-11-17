package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * A casa de uma Laje do Ressonador na camada do chão da Armilar, no referencial local (frente para o sul): solta,
 * ou {@code p<x><z>} com x e z de 0 a 4 ({@code p20} é a casa do Sintonizador, que a Laje pode ocupar). Ficam de
 * fora as casas do Núcleo ({@code p24}) e das Portas ({@code p42}, {@code p02}): são 22 casas, mais a solta.
 */
public enum BasePosition implements StringRepresentable {
    LOOSE("loose"),
    P00("p00"), P01("p01"), P03("p03"), P04("p04"),
    P10("p10"), P11("p11"), P12("p12"), P13("p13"), P14("p14"),
    P20("p20"), P21("p21"), P22("p22"), P23("p23"),
    P30("p30"), P31("p31"), P32("p32"), P33("p33"), P34("p34"),
    P40("p40"), P41("p41"), P43("p43"), P44("p44");

    private static final BasePosition[] VALUES = values();

    private final String serialized;

    BasePosition(String serialized) {
        this.serialized = serialized;
    }

    @Override
    public String getSerializedName() {
        return serialized;
    }

    /** A posição pelo nome serializado ({@code "p20"}); um nome desconhecido cai em {@link #LOOSE}. */
    public static BasePosition byName(String name) {
        for (BasePosition position : VALUES) {
            if (position.serialized.equals(name)) {
                return position;
            }
        }
        return LOOSE;
    }
}
