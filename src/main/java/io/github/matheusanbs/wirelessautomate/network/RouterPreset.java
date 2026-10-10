package io.github.matheusanbs.wirelessautomate.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Configuração copiável de um roteador: as faces de cada tipo, por {@link RelativeSide}, e a rede
 * de cada tipo (aba). Por ser relativa ao {@code facing}, colar num roteador virado para outro lado
 * gira a configuração junto. Imutável; vai no componente de item do Configurador.
 *
 * <p>Formato: {@code networks} é um mapa tipo → rede. Presets antigos, com uma rede única em
 * {@code network}, são lidos com essa rede em todos os tipos.
 */
public final class RouterPreset {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();
    /** Só lida por {@link FaceConfig#copyFrom}; nunca mude. */
    private static final FaceConfig DEFAULT = new FaceConfig();

    public static final RouterPreset EMPTY =
            new RouterPreset(new FaceConfig[TYPES.length][SIDES.length], new UUID[TYPES.length]);

    private static final Codec<ResourceType> TYPE_CODEC =
            keyCodec(TYPES, ResourceType::key);
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
            Codec.unboundedMap(TYPE_CODEC, UUIDUtil.CODEC)
                    .optionalFieldOf("networks", Map.of()).forGetter(RouterPreset::networksByType),
            // Formato antigo, só lido.
            UUIDUtil.CODEC.optionalFieldOf("network").forGetter(preset -> Optional.empty())
    ).apply(instance, RouterPreset::fromMaps));

    /** Pelo {@link #CODEC} (porte 1.20.1: em NBT, sem registros). */
    public static final StreamCodec<RegistryFriendlyByteBuf, RouterPreset> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** [tipo][lado relativo]; {@code null} é face padrão. Cópias próprias, nunca expostas. */
    private final FaceConfig[][] faces;
    /** Rede de cada tipo (por {@link ResourceType#ordinal()}); {@code null} = o preset não mexe na rede desse tipo. */
    private final @Nullable UUID[] networks;

    private RouterPreset(FaceConfig[][] faces, @Nullable UUID[] networks) {
        this.faces = faces;
        this.networks = networks;
    }

    /** Copia as faces e a rede de cada tipo do roteador. */
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
        UUID[] networks = new UUID[TYPES.length];
        for (ResourceType type : TYPES) {
            networks[type.ordinal()] = router.networkId(type);
        }
        return new RouterPreset(faces, networks);
    }

    /**
     * Aplica todas as faces (as padrão no preset voltam ao padrão no roteador) e a rede de cada
     * tipo que o preset levar; um tipo sem rede no preset fica com a rede que o roteador já tem.
     * Quem chama decide se o jogador pode usar cada rede: veja {@link #withoutNetwork(ResourceType)}.
     */
    public void applyTo(RouterBlockEntity router) {
        applyTo(router, null);
    }

    /**
     * Como {@link #applyTo(RouterBlockEntity)}, mas só a aba {@code only}: as faces e a rede desse
     * tipo; as outras abas do roteador ficam como estavam (faces e rede). {@code null} = todas.
     */
    public void applyTo(RouterBlockEntity router, @Nullable ResourceType only) {
        // Um aviso só ao motor, à tela e ao índice, em vez de um por face e por rede.
        router.batchChanges(() -> apply(router, only));
    }

    private void apply(RouterBlockEntity router, @Nullable ResourceType only) {
        for (ResourceType type : TYPES) {
            if (only != null && type != only) {
                continue;
            }
            for (RelativeSide side : SIDES) {
                FaceConfig config = faces[type.ordinal()][side.ordinal()];
                router.setFace(type, side, config != null ? config : DEFAULT);
            }
        }
        for (ResourceType type : TYPES) {
            UUID network = networks[type.ordinal()];
            if (network != null && (only == null || type == only)) {
                router.setNetworkId(type, network);
            }
        }
    }

    /** Configuração de uma face (cópia; padrão se não configurada). */
    public FaceConfig face(ResourceType type, RelativeSide side) {
        FaceConfig config = faces[type.ordinal()][side.ordinal()];
        return config != null ? copy(config) : new FaceConfig();
    }

    /** Rede do tipo no preset; {@code null} = colar não mexe na rede desse tipo. */
    public @Nullable UUID network(ResourceType type) {
        return networks[type.ordinal()];
    }

    /** Algum tipo leva rede. */
    public boolean hasNetwork() {
        for (UUID network : networks) {
            if (network != null) {
                return true;
            }
        }
        return false;
    }

    /** As redes do preset, sem repetir, na ordem dos tipos. */
    public List<UUID> distinctNetworks() {
        List<UUID> result = new ArrayList<>(networks.length);
        for (UUID network : networks) {
            if (network != null && !result.contains(network)) {
                result.add(network);
            }
        }
        return result;
    }

    /** O mesmo preset sem a rede do tipo. */
    public RouterPreset withoutNetwork(ResourceType type) {
        if (networks[type.ordinal()] == null) {
            return this;
        }
        UUID[] copy = networks.clone();
        copy[type.ordinal()] = null;
        return new RouterPreset(faces, copy);
    }

    /**
     * O mesmo preset só com a rede do tipo {@code only} (as faces ficam todas); {@code null} = o
     * próprio preset. Serve para a regra das redes ({@code PresetApplier}) olhar só a aba colada.
     */
    public RouterPreset onlyNetworkOf(@Nullable ResourceType only) {
        if (only == null) {
            return this;
        }
        UUID[] copy = new UUID[TYPES.length];
        copy[only.ordinal()] = networks[only.ordinal()];
        return Arrays.equals(copy, networks) ? this : new RouterPreset(faces, copy);
    }

    /** O mesmo preset sem nenhuma rede (só as faces). */
    public RouterPreset withoutNetworks() {
        return hasNetwork() ? new RouterPreset(faces, new UUID[TYPES.length]) : this;
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

    private Map<ResourceType, UUID> networksByType() {
        Map<ResourceType, UUID> result = new EnumMap<>(ResourceType.class);
        for (ResourceType type : TYPES) {
            if (networks[type.ordinal()] != null) {
                result.put(type, networks[type.ordinal()]);
            }
        }
        return result;
    }

    private static RouterPreset fromMaps(Map<ResourceType, Map<RelativeSide, FaceConfig>> byType,
            Map<ResourceType, UUID> networksByType, Optional<UUID> legacyNetwork) {
        FaceConfig[][] faces = new FaceConfig[TYPES.length][SIDES.length];
        byType.forEach((type, sides) -> sides.forEach((side, config) -> {
            if (!config.isDefault()) {
                faces[type.ordinal()][side.ordinal()] = copy(config);
            }
        }));
        UUID[] networks = new UUID[TYPES.length];
        if (networksByType.isEmpty()) {
            // Preset antigo: a rede única vale para todos os tipos.
            Arrays.fill(networks, legacyNetwork.orElse(null));
        } else {
            networksByType.forEach((type, network) -> networks[type.ordinal()] = network);
        }
        return new RouterPreset(faces, networks);
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
                && Arrays.equals(networks, other.networks) && Arrays.deepEquals(faces, other.faces);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.deepHashCode(faces) + Arrays.hashCode(networks);
    }

    @Override
    public String toString() {
        return "RouterPreset[" + facesByType() + ", networks=" + networksByType() + "]";
    }
}
