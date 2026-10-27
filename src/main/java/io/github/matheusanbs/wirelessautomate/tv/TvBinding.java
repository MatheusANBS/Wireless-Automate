package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * De onde uma peça tira o número, por fonte ({@link Source}): uma rede (rede, tipo e {@link TvMetric}, a ligação das
 * etapas 1 e 2), um armazenamento do mod ligado à Tela ({@link Storage}) ou o relógio do jogo ({@link TvClockMetric}).
 * O NBT sem {@code source} lê como rede. Fora da rede, {@code metric} é só um valor de enchimento ({@code IN}) sem
 * significado e {@code network} é nulo; {@code type} é o recurso do armazenamento (a unidade) e, no relógio, só
 * enchimento.
 */
public record TvBinding(@Nullable UUID network, ResourceType type, TvMetric metric, @Nullable Storage storage,
        @Nullable TvClockMetric clock) {
    /** A fonte de um dado. */
    public enum Source implements StringRepresentable {
        NETWORK("network"),
        STORAGE("storage"),
        CLOCK("clock");

        public static final Codec<Source> CODEC = StringRepresentable.fromEnum(Source::values);

        private final String id;

        Source(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /**
     * Um armazenamento ligado: a posição (qualquer dimensão), a métrica e, no Baú, o item (o id, o estoque é do item
     * sem os componentes).
     */
    public record Storage(GlobalPos pos, TvStorageMetric metric, Optional<ResourceLocation> item) {
    }

    private static final Codec<ResourceType> TYPE_CODEC = Codec.STRING.comapFlatMap(key -> {
        ResourceType type = ResourceType.byKey(key);
        return type == null ? DataResult.error(() -> "tipo desconhecido: " + key) : DataResult.success(type);
    }, ResourceType::key);

    /** Os campos como estão no NBT, antes de saber a fonte; a conversão valida a combinação. */
    private record Raw(Source source, Optional<UUID> network, ResourceType type, String metric,
            Optional<GlobalPos> pos, Optional<ResourceLocation> item) {
    }

    private static final Codec<Raw> RAW = RecordCodecBuilder.create(i -> i.group(
            Source.CODEC.optionalFieldOf("source", Source.NETWORK).forGetter(Raw::source),
            UUIDUtil.CODEC.optionalFieldOf("network").forGetter(Raw::network),
            TYPE_CODEC.optionalFieldOf("type", ResourceType.ITEM).forGetter(Raw::type),
            Codec.STRING.fieldOf("metric").forGetter(Raw::metric),
            GlobalPos.CODEC.optionalFieldOf("pos").forGetter(Raw::pos),
            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Raw::item)).apply(i, Raw::new));

    public static final Codec<TvBinding> CODEC = RAW.flatXmap(TvBinding::fromRaw,
            binding -> DataResult.success(binding.raw()));

    public TvBinding {
        if (storage != null && clock != null) {
            throw new IllegalArgumentException("uma ligação tem uma fonte só");
        }
    }

    /** Uma ligação a uma rede. */
    public TvBinding(UUID network, ResourceType type, TvMetric metric) {
        this(network, type, metric, null, null);
    }

    /** Uma ligação a um armazenamento ligado à Tela; {@code type} é o recurso dele, para a unidade. */
    public static TvBinding storage(ResourceType type, GlobalPos pos, TvStorageMetric metric,
            Optional<ResourceLocation> item) {
        return new TvBinding(null, type, TvMetric.IN, new Storage(pos, metric, item), null);
    }

    /** Uma ligação ao relógio do jogo. */
    public static TvBinding clock(TvClockMetric metric) {
        return new TvBinding(null, ResourceType.ITEM, TvMetric.IN, null, metric);
    }

    public Source source() {
        return storage != null ? Source.STORAGE : clock != null ? Source.CLOCK : Source.NETWORK;
    }

    /** Se a métrica é uma taxa por segundo (a unidade da peça vale) e não uma quantidade, fração ou contagem. */
    public boolean rate() {
        return switch (source()) {
            case NETWORK -> metric.rate();
            case STORAGE -> storage.metric().rate();
            case CLOCK -> false;
        };
    }

    /** Se o número é uma fração de 0 a 1, que a peça mostra em porcentagem. */
    public boolean fraction() {
        return switch (source()) {
            case NETWORK -> metric.fraction();
            case STORAGE -> storage.metric() == TvStorageMetric.FILL;
            case CLOCK -> false;
        };
    }

    private Raw raw() {
        return switch (source()) {
            case NETWORK -> new Raw(Source.NETWORK, Optional.ofNullable(network), type, metric.getSerializedName(),
                    Optional.empty(), Optional.empty());
            case STORAGE -> new Raw(Source.STORAGE, Optional.empty(), type, storage.metric().getSerializedName(),
                    Optional.of(storage.pos()), storage.item());
            case CLOCK -> new Raw(Source.CLOCK, Optional.empty(), type, clock.getSerializedName(), Optional.empty(),
                    Optional.empty());
        };
    }

    private static DataResult<TvBinding> fromRaw(Raw raw) {
        return switch (raw.source()) {
            case NETWORK -> {
                if (raw.network().isEmpty()) {
                    yield DataResult.error(() -> "ligação a rede sem rede");
                }
                yield TvMetric.CODEC.parse(JavaOps.INSTANCE, raw.metric())
                        .map(metric -> new TvBinding(raw.network().get(), raw.type(), metric));
            }
            case STORAGE -> {
                if (raw.pos().isEmpty()) {
                    yield DataResult.error(() -> "ligação a armazenamento sem posição");
                }
                yield TvStorageMetric.CODEC.parse(JavaOps.INSTANCE, raw.metric())
                        .map(metric -> storage(raw.type(), raw.pos().get(), metric, raw.item()));
            }
            case CLOCK -> TvClockMetric.CODEC.parse(JavaOps.INSTANCE, raw.metric())
                    .map(TvBinding::clock);
        };
    }
}
