package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: uma ação na tela do Vinculador aberta ({@code containerId}).
 *
 * @param value o desvincular ({@link Op#SET_UNLINK}: 1 liga, 0 desliga)
 */
public record LinkerActionPayload(int containerId, Op op, int value) implements CustomPacketPayload {
    public enum Op {
        /** Liga ou desliga o desvincular. */
        SET_UNLINK,
        CLEAR_AREA,
        /** Vincula (ou desvincula) as máquinas da área ao roteador escolhido. */
        LINK
    }

    public static final Type<LinkerActionPayload> TYPE = new Type<>(WirelessAutomate.id("linker_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LinkerActionPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(Op.class), LinkerActionPayload::op,
            ByteBufCodecs.VAR_INT, LinkerActionPayload::value,
            LinkerActionPayload::new);

    public static LinkerActionPayload of(int containerId, Op op) {
        return new LinkerActionPayload(containerId, op, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
