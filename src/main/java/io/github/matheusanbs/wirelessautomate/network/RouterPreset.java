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
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Configuração copiável de um roteador: as faces da máquina presa de cada tipo, por
 * {@link RelativeSide}, cada uma com a sua rede (dentro da {@link FaceConfig}). Por ser relativa ao
 * {@code facing}, colar num roteador virado para outro lado gira a configuração junto. Imutável; vai
 * no componente de item do Configurador.
 *
 * <p>Formato: {@code faces} (tipo → lado → face, com a rede no codec da face). Os campos antigos
 * {@code networks} (mapa tipo → rede) e {@code network} (rede única, de todos os tipos) são só lidos:
 * a rede antiga do tipo vale nas seis faces dele que ainda não têm rede.
 */
public final class RouterPreset {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();
    /** Só lida por {@link FaceConfig#copySettingsFrom}; nunca mude. */
    private static final FaceConfig DEFAULT = new FaceConfig();

    public static final RouterPreset EMPTY = new RouterPreset(new FaceConfig[TYPES.length][SIDES.length]);

    private static final Codec<ResourceType> TYPE_CODEC =
            keyCodec(TYPES, ResourceType::key);
    private static final Codec<RelativeSide> SIDE_CODEC = keyCodec(SIDES, RelativeSide::key);
    /**
     * O codec da própria {@link FaceConfig}: mesmo formato do NBT da face (presets antigos continuam
     * lendo, e a rede da face vem junto) e, por ser um codec de registro, recebe as
     * {@code RegistryOps} de quem salva o componente do item, que o filtro precisa para os
     * componentes das entradas.
     */
    private static final Codec<FaceConfig> FACE_CODEC = FaceConfig.CODEC;

    public static final Codec<RouterPreset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(TYPE_CODEC, Codec.unboundedMap(SIDE_CODEC, FACE_CODEC))
                    .optionalFieldOf("faces", Map.of()).forGetter(RouterPreset::facesByType),
            // Formatos antigos, só lidos: o getter devolve o padrão, que o optionalFieldOf não escreve.
            Codec.unboundedMap(TYPE_CODEC, UUIDUtil.CODEC)
                    .optionalFieldOf("networks", Map.of()).forGetter(preset -> Map.of()),
            UUIDUtil.CODEC.optionalFieldOf("network").forGetter(preset -> Optional.empty())
    ).apply(instance, RouterPreset::fromMaps));

    /** Pelo {@link #CODEC} com registros (as entradas de filtro levam componentes). */
    public static final StreamCodec<RegistryFriendlyByteBuf, RouterPreset> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** [tipo][lado relativo]; {@code null} é face padrão (sem rede). Cópias próprias, nunca expostas. */
    private final FaceConfig[][] faces;

    private RouterPreset(FaceConfig[][] faces) {
        this.faces = faces;
    }

    /** Copia as faces da máquina presa, cada uma com a sua rede. */
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
        return new RouterPreset(faces);
    }

    /**
     * Aplica todas as faces (as padrão no preset voltam ao padrão no roteador) e a rede de cada face
     * que o preset levar; uma face sem rede no preset fica com a rede que a face do roteador já tem.
     * Quem chama decide se o jogador pode usar cada rede: veja {@link #withoutNetwork(UUID)}.
     */
    public void applyTo(RouterBlockEntity router) {
        applyTo(router, null);
    }

    /**
     * Como {@link #applyTo(RouterBlockEntity)}, mas só a aba {@code only}: as faces (e as redes delas)
     * desse tipo; as outras abas do roteador ficam como estavam. {@code null} = todas.
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
                // setFace copia só as configurações; a rede vai à parte, e só se a face do preset tem uma.
                router.setFace(type, side, config != null ? config : DEFAULT);
                if (config != null && config.network() != null) {
                    router.setFaceNetwork(type, side, config.network());
                }
            }
        }
    }

    /** Configuração de uma face, com a rede (cópia; padrão se não configurada). */
    public FaceConfig face(ResourceType type, RelativeSide side) {
        FaceConfig config = faces[type.ordinal()][side.ordinal()];
        return config != null ? copy(config) : new FaceConfig();
    }

    /** Rede da face no preset; {@code null} = colar não mexe na rede dessa face. */
    public @Nullable UUID network(ResourceType type, RelativeSide side) {
        FaceConfig config = faces[type.ordinal()][side.ordinal()];
        return config != null ? config.network() : null;
    }

    /** As redes das faces do tipo, sem repetir, na ordem dos lados. */
    public List<UUID> networks(ResourceType type) {
        List<UUID> result = new ArrayList<>(2);
        for (FaceConfig config : faces[type.ordinal()]) {
            if (config != null && config.network() != null && !result.contains(config.network())) {
                result.add(config.network());
            }
        }
        return result;
    }

    /** Alguma face leva rede. */
    public boolean hasNetwork() {
        for (FaceConfig[] row : faces) {
            for (FaceConfig config : row) {
                if (config != null && config.network() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** As redes de todas as faces, sem repetir, na ordem dos tipos e dos lados. */
    public List<UUID> distinctNetworks() {
        List<UUID> result = new ArrayList<>(2);
        for (ResourceType type : TYPES) {
            for (UUID network : networks(type)) {
                if (!result.contains(network)) {
                    result.add(network);
                }
            }
        }
        return result;
    }

    /** O mesmo preset sem a rede {@code id} em nenhuma face (essas faces ficam sem rede). */
    public RouterPreset withoutNetwork(UUID id) {
        return dropNetworks((type, network) -> network.equals(id));
    }

    /**
     * O mesmo preset só com as redes das faces do tipo {@code only} (as faces ficam todas; as dos
     * outros tipos, sem rede); {@code null} = o próprio preset. Serve para a regra das redes
     * ({@code PresetApplier}) olhar só a aba colada.
     */
    public RouterPreset onlyNetworkOf(@Nullable ResourceType only) {
        return only == null ? this : dropNetworks((type, network) -> type != only);
    }

    /** O mesmo preset sem nenhuma rede (só as configurações das faces). */
    public RouterPreset withoutNetworks() {
        return dropNetworks((type, network) -> true);
    }

    /** Tira a rede das faces em que {@code drop} diz sim; o próprio preset se nada muda. */
    private RouterPreset dropNetworks(BiPredicate<ResourceType, UUID> drop) {
        FaceConfig[][] copy = null;
        for (ResourceType type : TYPES) {
            for (RelativeSide side : SIDES) {
                FaceConfig config = faces[type.ordinal()][side.ordinal()];
                if (config == null || config.network() == null || !drop.test(type, config.network())) {
                    continue;
                }
                if (copy == null) {
                    copy = new FaceConfig[TYPES.length][];
                    for (int i = 0; i < TYPES.length; i++) {
                        copy[i] = faces[i].clone();
                    }
                }
                FaceConfig without = new FaceConfig();
                without.copySettingsFrom(config);
                copy[type.ordinal()][side.ordinal()] = without.isDefault() ? null : without;
            }
        }
        return copy == null ? this : new RouterPreset(copy);
    }

    /**
     * Quantas faces (tipo × lado) têm configuração fora do padrão (modo, prioridade, redstone ou
     * filtro). A rede não conta: um roteador só numa rede não aparece com todas as faces configuradas.
     */
    public int configuredFaces() {
        int count = 0;
        for (FaceConfig[] row : faces) {
            for (FaceConfig config : row) {
                if (config != null && hasSettings(config)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean hasSettings(FaceConfig config) {
        if (config.network() == null) {
            return !config.isDefault();
        }
        FaceConfig settings = new FaceConfig();
        settings.copySettingsFrom(config);
        return !settings.isDefault();
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

    /**
     * Monta o preset lido. Formato antigo (decisão 12): a rede de um tipo em {@code networks} (ou,
     * sem o mapa, a rede única em {@code network}, de todos os tipos) vale nas seis faces do tipo
     * que ainda não têm rede, criando a face que estava padrão.
     */
    private static RouterPreset fromMaps(Map<ResourceType, Map<RelativeSide, FaceConfig>> byType,
            Map<ResourceType, UUID> legacyNetworks, Optional<UUID> legacyNetwork) {
        FaceConfig[][] faces = new FaceConfig[TYPES.length][SIDES.length];
        byType.forEach((type, sides) -> sides.forEach((side, config) -> {
            if (!config.isDefault()) {
                faces[type.ordinal()][side.ordinal()] = copy(config);
            }
        }));
        for (ResourceType type : TYPES) {
            UUID network = legacyNetworks.isEmpty() ? legacyNetwork.orElse(null) : legacyNetworks.get(type);
            if (network == null) {
                continue;
            }
            for (RelativeSide side : SIDES) {
                FaceConfig config = faces[type.ordinal()][side.ordinal()];
                if (config == null) {
                    config = new FaceConfig();
                    faces[type.ordinal()][side.ordinal()] = config;
                }
                if (config.network() == null) {
                    config.setNetwork(network);
                }
            }
        }
        return new RouterPreset(faces);
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
        return o instanceof RouterPreset other && Arrays.deepEquals(faces, other.faces);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(faces);
    }

    @Override
    public String toString() {
        return "RouterPreset[" + facesByType() + "]";
    }
}
