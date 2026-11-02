package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: Copiar o perfil do alvo de edição da tela (⧉ ou Ctrl+C) ou, com {@code forget}, esquecer a
 * cópia do jogador (o ✕ do rodapé "Cópia: ...").
 */
public record MachineCopyPayload(int containerId, boolean forget) implements CustomPacketPayload {
    public static final Type<MachineCopyPayload> TYPE = new Type<>(WirelessAutomate.id("machine_copy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineCopyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MachineCopyPayload::containerId,
            ByteBufCodecs.BOOL, MachineCopyPayload::forget,
            MachineCopyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
