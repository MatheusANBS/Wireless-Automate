package io.github.matheusanbs.wirelessautomate.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Componente do item de um Baú quebrado cheio: só a referência ao conteúdo, guardado no servidor
 * ({@link StorageSavedData}), e um resumo para o tooltip. Assim o item não carrega milhões de tipos
 * nem estoura o limite de pacote.
 */
public record StorageContents(UUID id, int types, long total) {
    public static final Codec<StorageContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(StorageContents::id),
            Codec.INT.fieldOf("types").forGetter(StorageContents::types),
            Codec.LONG.fieldOf("total").forGetter(StorageContents::total)).apply(instance, StorageContents::new));

    public static final StreamCodec<ByteBuf, StorageContents> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, StorageContents::id,
            ByteBufCodecs.VAR_INT, StorageContents::types,
            ByteBufCodecs.VAR_LONG, StorageContents::total,
            StorageContents::new);
}
