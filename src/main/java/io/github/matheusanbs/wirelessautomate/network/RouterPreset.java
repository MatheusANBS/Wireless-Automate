package io.github.matheusanbs.wirelessautomate.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Configuração copiável de um roteador: as faces de cada tipo, por {@link RelativeSide}, e a rede.
 * Por ser relativa ao {@code facing}, colar num roteador virado para outro lado gira a configuração
 * junto. Imutável; vai no componente de item do Configurador e, mais tarde, na biblioteca.
 */
public final class RouterPreset {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();
    /** Só lida por {@link FaceConfig#copyFrom}; nunca mude. */
    private static final FaceConfig DEFAULT = new FaceConfig();

    public static final RouterPreset EMPTY = new RouterPreset(new FaceConfig[TYPES.length][SIDES.length], null);

    private static final Codec<ResourceType> TYPE_CODEC =
            keyCodec(TYPES, type -> type.name().toLowerCase(Locale.ROOT));
    private static final Codec<RelativeSide> SIDE_CODEC = keyCodec(SIDES, RelativeSide::key);
    /**
     * O codec da própria {@link FaceConfig}: mesmo formato do NBT da face (presets antigos continuam
     * lendo) e, por ser um codec de registro, recebe as {@code RegistryOps} de quem salva o
     * componente do item, que o filtro precisa para os componentes das entradas.
     */
    private static final Codec<FaceConfig> FACE_CODEC = FaceConfig.CODEC;

    public static final Codec<RouterPreset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(TYPE_CODEC, Codec.unboundedMap(SIDE_CODEC, FACE_CODEC))
                    .optionalFieldOf("faces", Map.of()).forGetter(RouterPreset::facesByType),
            UUIDUtil.CODEC.optionalFieldOf("network").forGetter(preset -> Optional.ofNullable(preset.network))
    ).apply(instance, RouterPreset::fromMaps));

    /** Pelo {@link #CODEC} com registros (as entradas de filtro levam componentes). */
    public static final StreamCodec<RegistryFriendlyByteBuf, RouterPreset> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** [tipo][lado relativo]; {@code null} é face padrão. Cópias próprias, nunca expostas. */
    private final FaceConfig[][] faces;
    private final @Nullable UUID network;

    private RouterPreset(FaceConfig[][] faces, @Nullable UUID network) {
        this.faces = faces;
        this.network = network;
    }

    /** Copia as faces e a rede do roteador. */
    public static RouterPreset copyOf(RouterBlockEntity router) {
        FaceConfig[][] faces = new FaceConfig[TYPES.length][SIDES.length];
        for (ResourceType type : TYPES) {
            for (RelativeSide side : SIDES) {
                FaceConfig config = router.face(type, side);
                if (!config.isDefault()) {
                    faces[type.ordinal()][side.ordinal()] = copy(config);
                }
            }
        }
        return new RouterPreset(faces, router.networkId());
    }

    /**
     * Aplica todas as faces (as padrão no preset voltam ao padrão no roteador) e, se o preset
     * levar rede, a rede. Quem chama decide se o jogador pode usar a rede: veja {@link #withoutNetwork()}.
     */
    public void applyTo(RouterBlockEntity router) {
        for (ResourceType type : TYPES) {
            for (RelativeSide side : SIDES) {
                FaceConfig config = faces[type.ordinal()][side.ordinal()];
                router.setFace(type, side, config != null ? config : DEFAULT);
            }
        }
        if (network != null) {
            router.setNetworkId(network);
        }
    }

    /** Configuração de uma face (cópia; padrão se não configurada). */
    public FaceConfig face(ResourceType type, RelativeSide side) {
        FaceConfig config = faces[type.ordinal()][side.ordinal()];
        return config != null ? copy(config) : new FaceConfig();
    }

    public @Nullable UUID network() {
        return network;
    }

    public RouterPreset withoutNetwork() {
        return network == null ? this : new RouterPreset(faces, null);
    }

    /** Quantas faces (tipo × lado) estão fora do padrão. */
    public int configuredFaces() {
        int count = 0;
        for (FaceConfig[] row : faces) {
            for (FaceConfig config : row) {
                if (config != null) {
                    count++;
                }
            }
        }
        return count;
    }

    private Map<ResourceType, Map<RelativeSide, FaceConfig>> facesByType() {
        Map<ResourceType, Map<RelativeSide, FaceConfig>> result = new EnumMap<>(ResourceType.class);
        for (ResourceType type : TYPES) {
            Map<RelativeSide, FaceConfig> sides = new EnumMap<>(RelativeSide.class);
            for (RelativeSide side : SIDES) {
                FaceConfig config = faces[type.ordinal()][side.ordinal()];
                if (config != null) {
                    sides.put(side, config);
                }
            }
            if (!sides.isEmpty()) {
                result.put(type, sides);
            }
        }
        return result;
    }

    private static RouterPreset fromMaps(Map<ResourceType, Map<RelativeSide, FaceConfig>> byType,
            Optional<UUID> network) {
        FaceConfig[][] faces = new FaceConfig[TYPES.length][SIDES.length];
        byType.forEach((type, sides) -> sides.forEach((side, config) -> {
            if (!config.isDefault()) {
                faces[type.ordinal()][side.ordinal()] = copy(config);
            }
        }));
        return new RouterPreset(faces, network.orElse(null));
    }

    private static FaceConfig copy(FaceConfig config) {
        FaceConfig copy = new FaceConfig();
        copy.copyFrom(config);
        return copy;
    }

    private static <E extends Enum<E>> Codec<E> keyCodec(E[] values, Function<E, String> key) {
        return Codec.STRING.comapFlatMap(name -> {
            for (E value : values) {
                if (key.apply(value).equals(name)) {
                    return DataResult.success(value);
                }
            }
            return DataResult.error(() -> "chave desconhecida: " + name);
        }, key);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RouterPreset other
                && Objects.equals(network, other.network) && Arrays.deepEquals(faces, other.faces);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.deepHashCode(faces) + Objects.hashCode(network);
    }

    @Override
    public String toString() {
        return "RouterPreset[" + facesByType() + ", network=" + network + "]";
    }
}
