package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.range.RangeTiers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: o cubo do alcance de um roteador ({@link #ROUTER}) ou de uma Doca de Padrões ({@link #DOCK}) e as
 * máquinas dele, para o desenho no mundo ({@code client/dock/DockReachPaint}). {@code origin} é o bloco do dono,
 * {@code radius} o raio de Chebyshev (ou {@link #NO_CUBE}: sem o cubo, só as máquinas), {@code tier} o ordinal do
 * {@code RouterTier} (a cor do cubo sai dele no cliente), {@code machines} as posições ({@code BlockPos.asLong}) e
 * {@code colors} o índice da cor de cada uma, no mesmo índice: 0 a 5 são as cores de {@code DockColors.side} (o lado
 * da Doca, ou o grupo do roteador) e {@link #INACTIVE} é o cinza; no máximo {@link #MAX_MACHINES}. {@code ticks} 0: vale
 * enquanto o Vinculador com este dono escolhido está na mão; acima de 0, por esse prazo (ao colocar a Doca). Mandado só
 * ao jogador que agiu ({@code dock/DockReachSync}, {@code range/RouterReachSync}).
 *
 * <p>No fio, o raio vai em {@code VAR_INT} de {@code radius + 1}: 0 é {@link #NO_CUBE}.
 */
public record LinkerReachPayload(byte kind, BlockPos origin, int radius, int tier, long[] machines, byte[] colors,
        int ticks) implements CustomPacketPayload {
    public static final byte ROUTER = 0;
    public static final byte DOCK = 1;
    /** Sem cubo (o roteador sem o Upgrade de Alcance valendo): só as máquinas. */
    public static final int NO_CUBE = -1;
    /** A cor cinza: a máquina fora do raio ou além do limite. As cores 0 a 5 são as da paleta {@code DockColors.side}. */
    public static final byte INACTIVE = 6;
    /** O teto de máquinas num pacote: o maior limite de máquinas que a config aceita. */
    public static final int MAX_MACHINES = RangeTiers.MAX_MACHINES;
    public static final Type<LinkerReachPayload> TYPE = new Type<>(WirelessAutomate.id("linker_reach"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerReachPayload> STREAM_CODEC =
            StreamCodec.of(LinkerReachPayload::encode, LinkerReachPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, LinkerReachPayload p) {
        buf.writeByte(p.kind);
        BlockPos.STREAM_CODEC.encode(buf, p.origin);
        ByteBufCodecs.VAR_INT.encode(buf, Math.max(p.radius, NO_CUBE) + 1);
        buf.writeByte(p.tier);
        int count = Math.min(Math.min(p.machines.length, p.colors.length), MAX_MACHINES);
        ByteBufCodecs.VAR_INT.encode(buf, count);
        for (int i = 0; i < count; i++) {
            buf.writeLong(p.machines[i]);
            buf.writeByte(p.colors[i]);
        }
        ByteBufCodecs.VAR_INT.encode(buf, p.ticks);
    }

    private static LinkerReachPayload decode(RegistryFriendlyByteBuf buf) {
        byte kind = buf.readByte();
        BlockPos origin = BlockPos.STREAM_CODEC.decode(buf);
        int radius = ByteBufCodecs.VAR_INT.decode(buf) - 1;
        int tier = buf.readUnsignedByte();
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        if (count < 0 || count > MAX_MACHINES) {
            throw new IllegalArgumentException("Cubo: " + count + " máquinas, acima de " + MAX_MACHINES);
        }
        long[] machines = new long[count];
        byte[] colors = new byte[count];
        for (int i = 0; i < count; i++) {
            machines[i] = buf.readLong();
            colors[i] = buf.readByte();
        }
        return new LinkerReachPayload(kind, origin, radius, tier, machines, colors, ByteBufCodecs.VAR_INT.decode(buf));
    }

    @Override
    public Type<LinkerReachPayload> type() {
        return TYPE;
    }
}
