package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: o cubo do alcance de uma Doca de Padrões e as máquinas dela, para o desenho no mundo
 * ({@code client/dock/DockReachPaint}). {@code radius} é o raio de Chebyshev, {@code tier} o ordinal do
 * {@code RouterTier} (a cor do cubo sai dele no cliente), {@code machines} as posições ({@code BlockPos.asLong}) e
 * {@code sides} o lado de cada uma ({@code Direction.ordinal()}), no mesmo índice, no máximo {@link #MAX_MACHINES}.
 * {@code ticks} 0: vale enquanto o Vinculador com esta Doca escolhida está na mão; acima de 0, por esse prazo (ao
 * colocar a Doca). Mandado só ao jogador que agiu ({@code dock/DockReachSync}).
 */
public record DockReachPayload(BlockPos dock, int radius, int tier, long[] machines, byte[] sides, int ticks)
        implements CustomPacketPayload {
    public static final Type<DockReachPayload> TYPE = new Type<>(WirelessAutomate.id("dock_reach"));
    /** O teto de máquinas num pacote: o máximo padrão da Doca Ultimate. */
    public static final int MAX_MACHINES = 256;
    public static final StreamCodec<RegistryFriendlyByteBuf, DockReachPayload> STREAM_CODEC =
            StreamCodec.of(DockReachPayload::encode, DockReachPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, DockReachPayload p) {
        BlockPos.STREAM_CODEC.encode(buf, p.dock);
        ByteBufCodecs.VAR_INT.encode(buf, p.radius);
        buf.writeByte(p.tier);
        int count = Math.min(Math.min(p.machines.length, p.sides.length), MAX_MACHINES);
        ByteBufCodecs.VAR_INT.encode(buf, count);
        for (int i = 0; i < count; i++) {
            buf.writeLong(p.machines[i]);
            buf.writeByte(p.sides[i]);
        }
        ByteBufCodecs.VAR_INT.encode(buf, p.ticks);
    }

    private static DockReachPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos dock = BlockPos.STREAM_CODEC.decode(buf);
        int radius = ByteBufCodecs.VAR_INT.decode(buf);
        int tier = buf.readUnsignedByte();
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        if (count < 0 || count > MAX_MACHINES) {
            throw new IllegalArgumentException("Doca: " + count + " máquinas no cubo, acima de " + MAX_MACHINES);
        }
        long[] machines = new long[count];
        byte[] sides = new byte[count];
        for (int i = 0; i < count; i++) {
            machines[i] = buf.readLong();
            sides[i] = buf.readByte();
        }
        return new DockReachPayload(dock, radius, tier, machines, sides, ByteBufCodecs.VAR_INT.decode(buf));
    }

    @Override
    public Type<DockReachPayload> type() {
        return TYPE;
    }
}
