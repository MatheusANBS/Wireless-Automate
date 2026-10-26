package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: o estado da tela do Reparador aberta. {@code ends} é o tick de jogo em que cada linha termina
 * (0 parada) e {@code blocked} marca a linha que terminou sem saída livre; o cliente anima o progresso pelo relógio,
 * sem pacote por tick. {@code duration} é o tempo de um reparo, {@code needed} os cristais cheios que ele gasta e
 * {@code cost0} o custo do primeiro rachado que espera (0 sem nenhum), para a linha de estado dizer quanto falta. Os
 * itens vão pelos slots do próprio menu.
 */
public record RepairerStatePayload(int containerId, long energy, long capacity, long[] ends, boolean[] blocked,
        int duration, int needed, long cost0) implements CustomPacketPayload {
    public static final Type<RepairerStatePayload> TYPE = new Type<>(WirelessAutomate.id("repairer_state"));
    /** À mão: o {@code StreamCodec.composite} só vai até 6 campos. */
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairerStatePayload> STREAM_CODEC =
            StreamCodec.of(RepairerStatePayload::encode, RepairerStatePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, RepairerStatePayload p) {
        ByteBufCodecs.VAR_INT.encode(buf, p.containerId);
        ByteBufCodecs.VAR_LONG.encode(buf, p.energy);
        ByteBufCodecs.VAR_LONG.encode(buf, p.capacity);
        int blockedBits = 0;
        for (int i = 0; i < RepairMath.LINES; i++) {
            ByteBufCodecs.VAR_LONG.encode(buf, p.ends[i]);
            blockedBits |= p.blocked[i] ? 1 << i : 0;
        }
        buf.writeByte(blockedBits);
        ByteBufCodecs.VAR_INT.encode(buf, p.duration);
        ByteBufCodecs.VAR_INT.encode(buf, p.needed);
        ByteBufCodecs.VAR_LONG.encode(buf, p.cost0);
    }

    private static RepairerStatePayload decode(RegistryFriendlyByteBuf buf) {
        int containerId = ByteBufCodecs.VAR_INT.decode(buf);
        long energy = ByteBufCodecs.VAR_LONG.decode(buf);
        long capacity = ByteBufCodecs.VAR_LONG.decode(buf);
        long[] ends = new long[RepairMath.LINES];
        for (int i = 0; i < ends.length; i++) {
            ends[i] = ByteBufCodecs.VAR_LONG.decode(buf);
        }
        int blockedBits = buf.readUnsignedByte();
        boolean[] blocked = new boolean[RepairMath.LINES];
        for (int i = 0; i < blocked.length; i++) {
            blocked[i] = (blockedBits & 1 << i) != 0;
        }
        return new RepairerStatePayload(containerId, energy, capacity, ends, blocked, ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RepairerStatePayload p && containerId == p.containerId && energy == p.energy
                && capacity == p.capacity && Arrays.equals(ends, p.ends) && Arrays.equals(blocked, p.blocked)
                && duration == p.duration && needed == p.needed && cost0 == p.cost0;
    }

    @Override
    public int hashCode() {
        int hash = Integer.hashCode(containerId);
        hash = 31 * hash + Long.hashCode(energy);
        hash = 31 * hash + Long.hashCode(capacity);
        hash = 31 * hash + Arrays.hashCode(ends);
        hash = 31 * hash + Arrays.hashCode(blocked);
        hash = 31 * hash + duration;
        hash = 31 * hash + needed;
        return 31 * hash + Long.hashCode(cost0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
