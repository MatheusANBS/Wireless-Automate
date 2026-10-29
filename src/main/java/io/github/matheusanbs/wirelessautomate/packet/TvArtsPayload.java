package io.github.matheusanbs.wirelessautomate.packet;

import com.mojang.serialization.Codec;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: as pixel arts de uma Tela de Sinal, que o update tag do bloco leva só como referência
 * ({@code TvArtRef}). Sai pelo {@code TvArtSender}, a quem está perto e não tem as artes da Tela, uma vez por mudança
 * delas, e na abertura do menu da Tela. Vai como NBT em bytes (os pixels em meio byte), codificado uma vez e dividido
 * entre todos que o recebem.
 */
public final class TvArtsPayload implements CustomPacketPayload {
    /** As artes de uma Tela no teto de pixels mais alto da config (262.144) dão uns 130 KB. */
    public static final int MAX_BYTES = 512 * 1024;
    public static final Type<TvArtsPayload> TYPE = new Type<>(WirelessAutomate.id("tv_arts"));
    private static final StreamCodec<ByteBuf, byte[]> BYTES = ByteBufCodecs.byteArray(MAX_BYTES);
    private static final Codec<List<TvPixelArt>> BODY = TvPixelArt.CODEC.listOf().fieldOf("arts").codec();

    public static final StreamCodec<RegistryFriendlyByteBuf, TvArtsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> BYTES.encode(buf, payload.body()),
            buf -> new TvArtsPayload(TvNbtBytes.decode(BODY, BYTES.decode(buf), MAX_BYTES)));

    private final List<TvPixelArt> arts;
    /** Nulo até o primeiro uso de {@link #body()}. */
    private byte[] encoded;

    public TvArtsPayload(List<TvPixelArt> arts) {
        this.arts = List.copyOf(arts);
    }

    public List<TvPixelArt> arts() {
        return arts;
    }

    private byte[] body() {
        if (encoded == null) {
            encoded = TvNbtBytes.encode(BODY, arts);
        }
        return encoded;
    }

    /** Quantos bytes o payload ocupa: o orçamento por tick do envio e a conferência do teto. */
    public int encodedSize() {
        return body().length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TvArtsPayload p && p.arts.equals(arts);
    }

    @Override
    public int hashCode() {
        return arts.hashCode();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
