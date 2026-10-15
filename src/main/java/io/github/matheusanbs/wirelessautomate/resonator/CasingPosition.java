package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * A posição de uma Carcaça de Porcelana na estrutura, para a textura conectada: solta, ou três letras
 * (uma por eixo x, y, z) em que {@code n} é a camada de menor coordenada, {@code p} a de maior e {@code c} as do meio
 * (as mesmas que {@link ResonatorLayout.Member#casing()} devolve). Não existe {@code ccc}: o centro é o interior.
 */
public enum CasingPosition implements StringRepresentable {
    LOOSE,
    NNN,
    NNC,
    NNP,
    NCN,
    NCC,
    NCP,
    NPN,
    NPC,
    NPP,
    CNN,
    CNC,
    CNP,
    CCN,
    CCP,
    CPN,
    CPC,
    CPP,
    PNN,
    PNC,
    PNP,
    PCN,
    PCC,
    PCP,
    PPN,
    PPC,
    PPP;

    private final String serialized = name().toLowerCase(Locale.ROOT);

    @Override
    public String getSerializedName() {
        return serialized;
    }

    /** A posição pelo nome serializado ({@code "cnn"}); um nome desconhecido cai em {@link #LOOSE}. */
    public static CasingPosition byName(String name) {
        for (CasingPosition position : values()) {
            if (position.serialized.equals(name)) {
                return position;
            }
        }
        return LOOSE;
    }
}
