package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: os Fluxos que o jogador vê, da última amostra do amostrador. Só sai com o Tablet aberto
 * na aba Fluxos, no máximo uma vez por segundo e só quando mudou.
 *
 * @param modNanos       tempo médio do mod por tick (todas as redes)
 * @param budgetNanos    teto atual do orçamento por tick
 * @param networks       as redes visíveis com algum tipo em uso, e as pausadas (sem rotas), pelo id
 * @param withoutNetwork faces com modo e sem rede, por {@link ResourceType#ordinal()}, nos nós carregados que o
 *                       jogador vê na Lista (todos para operador)
 */
public record TabletFlowsPayload(int containerId, long modNanos, long budgetNanos, List<NetworkFlows> networks,
        List<Integer> withoutNetwork) implements CustomPacketPayload {
    /** Teto de redes por pacote. */
    public static final int MAX_NETWORKS = 4096;
    public static final Type<TabletFlowsPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_flows"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletFlowsPayload> STREAM_CODEC =
            StreamCodec.of(TabletFlowsPayload::encode, TabletFlowsPayload::decode);

    /**
     * Os Fluxos de uma rede.
     *
     * @param paused está num grupo pausado: fora dos totais
     * @param nanos  tempo médio do mod por tick nesta rede
     * @param types  os tipos em uso, pela ordem do registro
     */
    public record NetworkFlows(UUID id, boolean paused, long nanos, List<FlowStats> types) {
    }

    /** As faces com modo e sem rede do tipo. */
    public int withoutNetwork(ResourceType type) {
        return type.ordinal() < withoutNetwork.size() ? withoutNetwork.get(type.ordinal()) : 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, TabletFlowsPayload p) {
        buf.writeVarInt(p.containerId);
        buf.writeVarLong(p.modNanos);
        buf.writeVarLong(p.budgetNanos);
        buf.writeVarInt(p.networks.size());
        for (NetworkFlows n : p.networks) {
            buf.writeUUID(n.id);
            buf.writeBoolean(n.paused);
            buf.writeVarLong(n.nanos);
            buf.writeVarInt(n.types.size());
            for (FlowStats f : n.types) {
                buf.writeEnum(f.type());
                buf.writeVarLong(f.produced());
                buf.writeVarLong(f.consumed());
                buf.writeVarLong(f.stored());
                buf.writeVarLong(f.released());
                buf.writeVarInt(f.seconds());
                FlowStats.States s = f.states();
                buf.writeVarInt(s.sourcesMoving());
                buf.writeVarInt(s.sourcesEmpty());
                buf.writeVarInt(s.sourcesWaiting());
                buf.writeVarInt(s.destinationsMoving());
                buf.writeVarInt(s.destinationsFull());
                buf.writeVarInt(s.redstoneStopped());
            }
        }
        buf.writeVarInt(p.withoutNetwork.size());
        for (int count : p.withoutNetwork) {
            buf.writeVarInt(count);
        }
    }

    private static TabletFlowsPayload decode(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        long modNanos = buf.readVarLong();
        long budgetNanos = buf.readVarLong();
        int count = buf.readVarInt();
        if (count > MAX_NETWORKS) {
            throw new DecoderException("networks " + count + " > " + MAX_NETWORKS);
        }
        List<NetworkFlows> networks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            boolean paused = buf.readBoolean();
            long nanos = buf.readVarLong();
            int typeCount = buf.readVarInt();
            if (typeCount > ResourceType.values().length) {
                throw new DecoderException("types " + typeCount);
            }
            List<FlowStats> types = new ArrayList<>(typeCount);
            for (int t = 0; t < typeCount; t++) {
                ResourceType type = buf.readEnum(ResourceType.class);
                long produced = buf.readVarLong();
                long consumed = buf.readVarLong();
                long stored = buf.readVarLong();
                long released = buf.readVarLong();
                int seconds = buf.readVarInt();
                FlowStats.States states = new FlowStats.States(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
                types.add(new FlowStats(id, type, produced, consumed, stored, released, seconds, states));
            }
            networks.add(new NetworkFlows(id, paused, nanos, List.copyOf(types)));
        }
        int typeCount = buf.readVarInt();
        if (typeCount > ResourceType.values().length) {
            throw new DecoderException("withoutNetwork " + typeCount);
        }
        List<Integer> withoutNetwork = new ArrayList<>(typeCount);
        for (int t = 0; t < typeCount; t++) {
            withoutNetwork.add(buf.readVarInt());
        }
        return new TabletFlowsPayload(containerId, modNanos, budgetNanos, List.copyOf(networks),
                List.copyOf(withoutNetwork));
    }
}
