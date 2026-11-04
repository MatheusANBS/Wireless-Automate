package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: Colar a cópia do jogador no alvo de edição da tela (⎘ ou Ctrl+V); com {@code wholeGroup}
 * (Shift), no perfil do grupo do alvo. Com {@code undo}, o Desfazer do aviso do último colar ({@code wholeGroup}
 * não conta).
 */
public record MachinePastePayload(int containerId, boolean wholeGroup, boolean undo) implements CustomPacketPayload {
    public static final Type<MachinePastePayload> TYPE = new Type<>(WirelessAutomate.id("machine_paste"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MachinePastePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MachinePastePayload::containerId,
            ByteBufCodecs.BOOL, MachinePastePayload::wholeGroup,
            ByteBufCodecs.BOOL, MachinePastePayload::undo,
            MachinePastePayload::new);

    /** Colar no alvo ({@code wholeGroup}: no perfil do grupo dele). */
    public static MachinePastePayload paste(int containerId, boolean wholeGroup) {
        return new MachinePastePayload(containerId, wholeGroup, false);
    }

    /** O Desfazer do último colar. */
    public static MachinePastePayload undo(int containerId) {
        return new MachinePastePayload(containerId, false, true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
