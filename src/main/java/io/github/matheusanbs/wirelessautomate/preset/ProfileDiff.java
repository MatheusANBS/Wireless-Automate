package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Optional;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Comparação de um nó com o perfil copiado no Tablet: igual, ou a primeira diferença achada. O lado da
 * diferença é o da máquina presa no nó ({@link RemoteSide}, relativo à frente dela), o mesmo que o painel do
 * nó mostra.
 */
public final class ProfileDiff {
    private static final RemoteSide[] SIDES = RemoteSide.values();
    private static final Direction[] DIRECTIONS = Direction.values();

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
     * @param side  quando diferente, o lado dessa face na máquina do nó; senão nulo
     * @param what  quando diferente, o que difere nela; senão nulo
     */
    public record ProfileMatch(boolean equal, @Nullable ResourceType type, @Nullable RemoteSide side,
            @Nullable What what) {
        public static final ProfileMatch EQUAL = new ProfileMatch(true, null, null, null);

        public static ProfileMatch differs(ResourceType type, RemoteSide side, What what) {
            return new ProfileMatch(false, type, side, what);
        }
    }

    /**
     * A primeira diferença entre o nó e o perfil, ou vazio se são iguais. Os presets guardam as faces pelos
     * lados do roteador ({@link RelativeSide}); {@code facing}, {@code spin} e {@code machineFront} (a frente da
     * máquina do nó, -1 sem frente) dão o lado da máquina de cada face. Percorre os tipos carregados
     * ({@link LoadedTypes#LIST}) e os lados da máquina na ordem de {@link RemoteSide}, e em cada face compara
     * modo, rede, filtro, prioridade e redstone, nessa ordem. Um perfil sem rede numa face não pede rede: o
     * colar mantém a rede que o nó já tem ({@link RouterPreset#applyTo}), então ela não conta.
     */
    public static Optional<ProfileMatch> firstDifference(RouterPreset profile, RouterPreset node, Direction facing,
            int spin, int machineFront) {
        for (ResourceType type : LoadedTypes.LIST) {
            for (RemoteSide side : SIDES) {
                RelativeSide relative = RelativeSide.fromAbsolute(facing, spin, DIRECTIONS[side.toAbsolute(machineFront)]);
                What what = difference(profile.face(type, relative), node.face(type, relative));
                if (what != null) {
                    return Optional.of(ProfileMatch.differs(type, side, what));
                }
            }
        }
        return Optional.empty();
    }

    /** A primeira diferença entre o roteador e o perfil, pelos lados da máquina presa nele. */
    public static Optional<ProfileMatch> firstDifference(RouterPreset profile, RouterBlockEntity router) {
        return firstDifference(profile, RouterPreset.copyOf(router), router.facing(), router.spin(),
                router.attachedFront());
    }

    /** Como {@link #firstDifference(RouterPreset, RouterBlockEntity)}, no formato que o {@code NodeView} leva. */
    public static ProfileMatch match(RouterPreset profile, RouterBlockEntity router) {
        return firstDifference(profile, router).orElse(ProfileMatch.EQUAL);
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
