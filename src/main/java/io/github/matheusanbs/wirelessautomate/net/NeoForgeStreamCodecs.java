package io.github.matheusanbs.wirelessautomate.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Imita o {@code net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs} (só o que o mod usa), para os
 * payloads do {@code main} mudarem só os imports.
 */
public final class NeoForgeStreamCodecs {
    private NeoForgeStreamCodecs() {
    }

    /** Uma constante de enum pelo índice em VarInt ({@link FriendlyByteBuf#writeEnum}). */
    public static <B extends FriendlyByteBuf, V extends Enum<V>> StreamCodec<B, V> enumCodec(Class<V> enumClass) {
        return StreamCodec.of(FriendlyByteBuf::writeEnum, buf -> buf.readEnum(enumClass));
    }
}
