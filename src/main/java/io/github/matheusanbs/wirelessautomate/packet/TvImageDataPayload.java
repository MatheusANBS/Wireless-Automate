package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: um pedaço dos bytes de uma imagem enviada, como está no armazém (o cliente decodifica). Sai pela
 * fila com orçamento do {@code TvArtSender}, em pedaços de até {@link #MAX_CHUNK} na ordem, só a quem está perto de
 * uma Tela que a usa ou abriu o editor com ela na biblioteca e ainda não a tem. Leva o resumo, o autor (para a opção
 * do cliente de esconder as imagens dos outros), o tamanho total e a posição do pedaço.
 */
public record TvImageDataPayload(String hash, Optional<UUID> author, int total, int offset, byte[] data)
        implements CustomPacketPayload {
    public static final int MAX_CHUNK = 256 * 1024;
    public static final Type<TvImageDataPayload> TYPE = new Type<>(WirelessAutomate.id("tv_image_data"));
    private static final StreamCodec<ByteBuf, byte[]> BYTES = ByteBufCodecs.byteArray(MAX_CHUNK);
    private static final StreamCodec<ByteBuf, Optional<UUID>> AUTHOR = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, TvImageDataPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBytes(TvImageHash.bytes(p.hash));
                AUTHOR.encode(buf, p.author);
                ByteBufCodecs.VAR_INT.encode(buf, p.total);
                ByteBufCodecs.VAR_INT.encode(buf, p.offset);
                BYTES.encode(buf, p.data);
            },
            buf -> {
                byte[] raw = new byte[TvImageHash.BYTES];
                buf.readBytes(raw);
                return new TvImageDataPayload(TvImageHash.hex(raw), AUTHOR.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), BYTES.decode(buf));
            });

    /** Os bytes que o pedaço ocupa no orçamento por tick (o cabeçalho é pequeno). */
    public int budgetSize() {
        return data.length + 64;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
