package io.github.matheusanbs.wirelessautomate.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;

/**
 * O buffer dos payloads. Imita o {@code net.minecraft.network.RegistryFriendlyByteBuf} do 1.21, mas sem
 * registros (o 1.20.1 não manda registros dinâmicos no buffer): é só um {@link FriendlyByteBuf} com outro
 * nome, para os codecs do {@code main} mudarem só os imports.
 *
 * <p>O {@code SimpleChannel} do Forge e o {@code NetworkHooks.openScreen} entregam um {@link FriendlyByteBuf}:
 * envolva-o com {@link #wrap} (visão sem cópia; ler e escrever andam no mesmo buffer).
 */
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    public RegistryFriendlyByteBuf(ByteBuf source) {
        super(source);
    }

    /** O próprio buffer, se já for um {@link RegistryFriendlyByteBuf}; senão uma visão por cima dele. */
    public static RegistryFriendlyByteBuf wrap(ByteBuf buf) {
        return buf instanceof RegistryFriendlyByteBuf registry ? registry : new RegistryFriendlyByteBuf(buf);
    }
}
