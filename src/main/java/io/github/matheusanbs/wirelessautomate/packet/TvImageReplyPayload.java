package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: a resposta a um envio de imagem ({@link TvImageUploadPayload}). {@code SEND} pede os bytes;
 * {@code OK} diz que a imagem está no armazém e na biblioteca do jogador (com ou sem os bytes: o resumo que já existia
 * só registra o uso); {@code REFUSED} traz o motivo, o nome de um {@code TvImageRefusal}.
 */
public record TvImageReplyPayload(String hash, Status status, String reason) implements CustomPacketPayload {
    public static final Type<TvImageReplyPayload> TYPE = new Type<>(WirelessAutomate.id("tv_image_reply"));

    public enum Status { SEND, OK, REFUSED }

    private static final Status[] STATUSES = Status.values();

    public static final StreamCodec<RegistryFriendlyByteBuf, TvImageReplyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBytes(TvImageHash.bytes(p.hash));
                buf.writeByte(p.status.ordinal());
                buf.writeUtf(p.reason, 32);
            },
            buf -> {
                byte[] raw = new byte[TvImageHash.BYTES];
                buf.readBytes(raw);
                int status = buf.readUnsignedByte();
                if (status >= STATUSES.length) {
                    throw new DecoderException("resposta de imagem com estado desconhecido");
                }
                return new TvImageReplyPayload(TvImageHash.hex(raw), STATUSES[status], buf.readUtf(32));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
