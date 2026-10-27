package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** A que uma peça Valor está ligada: uma rede, um tipo de recurso e uma métrica. */
public record TvBinding(UUID network, ResourceType type, TvMetric metric) {
    private static final Codec<ResourceType> TYPE_CODEC = Codec.STRING.comapFlatMap(key -> {
        ResourceType type = ResourceType.byKey(key);
        return type == null ? DataResult.error(() -> "tipo desconhecido: " + key) : DataResult.success(type);
    }, ResourceType::key);

    public static final Codec<TvBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("network").forGetter(TvBinding::network),
            TYPE_CODEC.fieldOf("type").forGetter(TvBinding::type),
            TvMetric.CODEC.fieldOf("metric").forGetter(TvBinding::metric)).apply(i, TvBinding::new));
}
