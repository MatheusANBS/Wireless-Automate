package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/** Comparação de um nó com o perfil copiado no Tablet: igual, ou a primeira diferença achada. */
public final class ProfileDiff {
    private static final RelativeSide[] SIDES = RelativeSide.values();

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

    /**
     * A primeira diferença entre o nó e o perfil, ou vazio se são iguais. Percorre os tipos carregados
     * ({@link LoadedTypes#LIST}) e os lados na ordem de {@link RelativeSide}, e em cada face compara modo,
     * rede, filtro, prioridade e redstone, nessa ordem. Um perfil sem rede numa face não pede rede: o colar
     * mantém a rede que o nó já tem ({@link RouterPreset#applyTo}), então ela não conta.
     */
    public static Optional<ProfileMatch> firstDifference(RouterPreset profile, RouterPreset node) {
        for (ResourceType type : LoadedTypes.LIST) {
            for (RelativeSide side : SIDES) {
                FaceConfig want = profile.face(type, side);
                FaceConfig have = node.face(type, side);
                What what = difference(want, have);
                if (what != null) {
                    return Optional.of(ProfileMatch.differs(type, side, what));
                }
            }
        }
        return Optional.empty();
    }

    /** Como {@link #firstDifference}, no formato que o {@code NodeView} leva. */
    public static ProfileMatch match(RouterPreset profile, RouterPreset node) {
        return firstDifference(profile, node).orElse(ProfileMatch.EQUAL);
    }

    private static @Nullable What difference(FaceConfig want, FaceConfig have) {
        if (want.mode() != have.mode()) {
            return What.MODE;
        }
        if (want.network() != null && !want.network().equals(have.network())) {
            return What.NETWORK;
        }
        if (!want.filter().equals(have.filter())) {
            return What.FILTER;
        }
        if (want.priority() != have.priority()) {
            return What.PRIORITY;
        }
        if (want.redstone() != have.redstone()) {
            return What.REDSTONE;
        }
        return null;
    }
}
