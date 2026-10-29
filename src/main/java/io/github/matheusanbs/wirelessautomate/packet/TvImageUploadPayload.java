package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: o envio de uma imagem à Tela de Sinal, em partes. {@code START} declara o resumo, o tamanho em
 * bytes, o formato, a largura, a altura, os quadros e o nome na biblioteca; se o servidor pede os bytes, eles vão em
 * {@code CHUNK}s de até {@link #MAX_CHUNK} na ordem, e o {@code END} fecha. {@code CANCEL} desiste. O servidor confere
 * tudo de novo ({@code TvImageUploads}); o resumo vai em 32 bytes.
 */
public record TvImageUploadPayload(Kind kind, String hash, int size, int format, int width, int height, int frames,
        String name, int offset, byte[] data) implements CustomPacketPayload {
    public static final int MAX_CHUNK = 512 * 1024;
    public static final int MAX_NAME = 64;
    public static final Type<TvImageUploadPayload> TYPE = new Type<>(WirelessAutomate.id("tv_image_upload"));

    public enum Kind { START, CHUNK, END, CANCEL }

    private static final Kind[] KINDS = Kind.values();
    private static final byte[] NONE = new byte[0];
    private static final StreamCodec<ByteBuf, byte[]> BYTES = ByteBufCodecs.byteArray(MAX_CHUNK);

    public static final StreamCodec<RegistryFriendlyByteBuf, TvImageUploadPayload> STREAM_CODEC = StreamCodec.of(
            TvImageUploadPayload::write, TvImageUploadPayload::read);

    public static TvImageUploadPayload start(String hash, int size, int format, int width, int height, int frames,
            String name) {
        return new TvImageUploadPayload(Kind.START, hash, size, format, width, height, frames,
                name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name, 0, NONE);
    }

    public static TvImageUploadPayload chunk(String hash, int offset, byte[] data) {
        return new TvImageUploadPayload(Kind.CHUNK, hash, 0, 0, 0, 0, 0, "", offset, data);
    }

    public static TvImageUploadPayload end(String hash) {
        return new TvImageUploadPayload(Kind.END, hash, 0, 0, 0, 0, 0, "", 0, NONE);
    }

    public static TvImageUploadPayload cancel(String hash) {
        return new TvImageUploadPayload(Kind.CANCEL, hash, 0, 0, 0, 0, 0, "", 0, NONE);
    }

    private static void write(RegistryFriendlyByteBuf buf, TvImageUploadPayload p) {
        buf.writeByte(p.kind.ordinal());
        buf.writeBytes(TvImageHash.bytes(p.hash));
        switch (p.kind) {
            case START -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.size);
                buf.writeByte(p.format);
                ByteBufCodecs.VAR_INT.encode(buf, p.width);
                ByteBufCodecs.VAR_INT.encode(buf, p.height);
                ByteBufCodecs.VAR_INT.encode(buf, p.frames);
                buf.writeUtf(p.name, MAX_NAME);
            }
            case CHUNK -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.offset);
                BYTES.encode(buf, p.data);
            }
            default -> {
            }
        }
    }

    private static TvImageUploadPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readUnsignedByte();
        if (id >= KINDS.length) {
            throw new DecoderException("envio de imagem com tipo desconhecido");
        }
        Kind kind = KINDS[id];
        byte[] raw = new byte[TvImageHash.BYTES];
        buf.readBytes(raw);
        String hash = TvImageHash.hex(raw);
        return switch (kind) {
            case START -> new TvImageUploadPayload(kind, hash, ByteBufCodecs.VAR_INT.decode(buf), buf.readUnsignedByte(),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), buf.readUtf(MAX_NAME), 0, NONE);
            case CHUNK -> new TvImageUploadPayload(kind, hash, 0, 0, 0, 0, 0, "", ByteBufCodecs.VAR_INT.decode(buf),
                    BYTES.decode(buf));
            default -> new TvImageUploadPayload(kind, hash, 0, 0, 0, 0, 0, "", 0, NONE);
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
