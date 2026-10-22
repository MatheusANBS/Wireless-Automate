package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.netty.handler.codec.DecoderException;
import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: os pontos do Mapa do Tablet, todos os roteadores que o jogador vê na dimensão dele (até
 * {@link #MAX_POINTS}, os mais perto dele), sem depender da busca, dos filtros, da visão nem do teto da Lista.
 * Só sai com o Tablet aberto na aba Mapa, no máximo uma vez por segundo e só quando mudou.
 *
 * @param otherDimension quantos roteadores que o jogador vê estão em outras dimensões (o Mapa só desenha esta)
 * @param positions      {@code BlockPos.asLong()} de cada ponto
 * @param statuses       {@link NodeStatus#ordinal()} de cada ponto, na mesma ordem
 * @param total          quantos roteadores o jogador vê nesta dimensão (mais que os pontos quando passa do teto)
 */
public record TabletMapPayload(int containerId, int otherDimension, int total, long[] positions, byte[] statuses)
        implements CustomPacketPayload {
    /** Teto de pontos por pacote. */
    public static final int MAX_POINTS = 1024;
    public static final Type<TabletMapPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletMapPayload> STREAM_CODEC =
            StreamCodec.of(TabletMapPayload::encode, TabletMapPayload::decode);

    private static final NodeStatus[] STATUSES = NodeStatus.values();

    public TabletMapPayload {
        if (positions.length != statuses.length) {
            throw new IllegalArgumentException("pontos e estados de tamanhos diferentes");
        }
    }

    public int count() {
        return positions.length;
    }

    public NodeStatus status(int index) {
        return STATUSES[Math.min(Math.max(statuses[index], 0), STATUSES.length - 1)];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TabletMapPayload p && containerId == p.containerId
                && otherDimension == p.otherDimension && total == p.total && Arrays.equals(positions, p.positions)
                && Arrays.equals(statuses, p.statuses);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(positions) + Arrays.hashCode(statuses)) + total;
    }

    @Override
    public String toString() {
        return "TabletMapPayload[" + positions.length + " pontos de " + total + ", outras dimensões " + otherDimension
                + "]";
    }

    private static void encode(RegistryFriendlyByteBuf buf, TabletMapPayload p) {
        buf.writeVarInt(p.containerId);
        buf.writeVarInt(p.otherDimension);
        buf.writeVarInt(p.total);
        buf.writeVarInt(p.positions.length);
        for (int i = 0; i < p.positions.length; i++) {
            buf.writeLong(p.positions[i]);
            buf.writeByte(p.statuses[i]);
        }
    }

    private static TabletMapPayload decode(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int otherDimension = buf.readVarInt();
        int total = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_POINTS) {
            throw new DecoderException("pontos do mapa demais: " + count);
        }
        long[] positions = new long[count];
        byte[] statuses = new byte[count];
        for (int i = 0; i < count; i++) {
            positions[i] = buf.readLong();
            statuses[i] = buf.readByte();
        }
        return new TabletMapPayload(containerId, otherDimension, total, positions, statuses);
    }
}
