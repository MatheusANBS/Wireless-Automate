package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import org.jetbrains.annotations.Nullable;

/** Comparação de um nó com o perfil copiado no Tablet: igual, ou a primeira diferença achada. */
public final class ProfileDiff {
    private ProfileDiff() {
    }

    /** O que difere numa face. */
    public enum What {
        MODE, NETWORK, FILTER, PRIORITY, REDSTONE
    }

    /**
     * O resultado para um nó.
     *
     * @param equal o nó tem o mesmo perfil
     * @param type  quando diferente, o tipo da primeira face que difere; senão nulo
     * @param side  quando diferente, o lado dessa face; senão nulo
     * @param what  quando diferente, o que difere nela; senão nulo
     */
    public record ProfileMatch(boolean equal, @Nullable ResourceType type, @Nullable RelativeSide side,
            @Nullable What what) {
        public static final ProfileMatch EQUAL = new ProfileMatch(true, null, null, null);

        public static ProfileMatch differs(ResourceType type, RelativeSide side, What what) {
            return new ProfileMatch(false, type, side, what);
        }
    }
}
