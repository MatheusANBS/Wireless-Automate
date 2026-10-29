package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor, uma vez por conexão: os resumos das imagens que o cliente já tem no cache em disco, as mais
 * recentes primeiro, até {@link #MAX}, para o servidor não mandar de novo. Só diminui o que o servidor manda (nunca
 * pede nada), e o servidor ignora o segundo da mesma conexão: não vira amplificação. 32 bytes por resumo.
 */
public record TvImageHavePayload(List<String> hashes) implements CustomPacketPayload {
    public static final int MAX = 2048;
    public static final Type<TvImageHavePayload> TYPE = new Type<>(WirelessAutomate.id("tv_image_have"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TvImageHavePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int count = Math.min(p.hashes.size(), MAX);
                ByteBufCodecs.VAR_INT.encode(buf, count);
                for (int i = 0; i < count; i++) {
                    buf.writeBytes(TvImageHash.bytes(p.hashes.get(i)));
                }
            },
            buf -> {
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                if (count < 0 || count > MAX) {
                    throw new DecoderException("lista de imagens grande demais");
                }
                List<String> hashes = new ArrayList<>(count);
                byte[] raw = new byte[TvImageHash.BYTES];
                for (int i = 0; i < count; i++) {
                    buf.readBytes(raw);
                    hashes.add(TvImageHash.hex(raw));
                }
                return new TvImageHavePayload(hashes);
            });

    public TvImageHavePayload {
        hashes = List.copyOf(hashes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
