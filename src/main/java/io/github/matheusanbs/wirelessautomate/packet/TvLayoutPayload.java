package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvArtRef;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: o layout inteiro que o editor da Tela de Sinal quer salvar, com os pixels das artes. Vai como
 * NBT em bytes, com o tamanho conferido antes de decodificar: mais de {@link #MAX_BYTES} é recusado. O servidor valida
 * tudo.
 */
public record TvLayoutPayload(int containerId, TvLayout layout) implements CustomPacketPayload {
    public static final int MAX_BYTES = 256 * 1024;
    public static final Type<TvLayoutPayload> TYPE = new Type<>(WirelessAutomate.id("tv_layout"));
    private static final StreamCodec<ByteBuf, byte[]> BYTES = ByteBufCodecs.byteArray(MAX_BYTES);

    public static final StreamCodec<RegistryFriendlyByteBuf, TvLayoutPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.containerId);
                BYTES.encode(buf, encode(payload.layout));
            },
            buf -> new TvLayoutPayload(ByteBufCodecs.VAR_INT.decode(buf), decode(BYTES.decode(buf))));

    /** Quantos bytes o layout ocupa no pacote: o editor confere antes de mandar. */
    public static int encodedSize(TvLayout layout) {
        return encode(layout).length;
    }

    /**
     * Quantos bytes o layout ocupa como vai ao cliente no update tag da Tela, com as artes reduzidas a referências
     * ({@link TvArtRef#clientTag}): o que o {@code tv.maxLayoutBytes} mede. Calcula o resumo de cada arte.
     */
    public static int clientSize(TvLayout layout) {
        return clientSize(layout, TvArtRef.of(layout));
    }

    /** O mesmo com as referências das artes já calculadas ({@link TvArtRef#of(TvLayout)}, o SHA-256 de cada uma). */
    public static int clientSize(TvLayout layout, List<TvArtRef> refs) {
        return bytes(TvArtRef.clientTag(layout, refs)).length;
    }

    private static byte[] encode(TvLayout layout) {
        return bytes(TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).getOrThrow());
    }

    private static byte[] bytes(Tag tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            NbtIo.writeAnyTag(tag, data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static TvLayout decode(byte[] bytes) {
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Tag tag = NbtIo.readAnyTag(data, NbtAccounter.create(MAX_BYTES * 4L));
            if (!(tag instanceof CompoundTag)) {
                throw new DecoderException("layout da Tela de Sinal não é um composto");
            }
            return TvLayout.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(DecoderException::new);
        } catch (IOException e) {
            throw new DecoderException(e);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
