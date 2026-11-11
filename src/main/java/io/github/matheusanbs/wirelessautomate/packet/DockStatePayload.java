package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Servidor → cliente: o estado da tela da Doca de Padrões aberta. {@code total} são as máquinas vinculadas somando os
 * seis lados, {@code max} o limite do tier e {@code radius} o raio; {@code lines} tem uma {@link Line} por lado, no
 * índice {@code Direction.ordinal()}. Mandado só com a tela aberta e só com mudança ({@code DockMenu}).
 */
public record DockStatePayload(int containerId, int total, int max, int radius, List<Line> lines)
        implements CustomPacketPayload {
    public static final Type<DockStatePayload> TYPE = new Type<>(WirelessAutomate.id("dock_state"));
    public static final int SIDES = 6;
    /** À mão: as seis linhas vão sem o tamanho da lista. */
    public static final StreamCodec<RegistryFriendlyByteBuf, DockStatePayload> STREAM_CODEC =
            StreamCodec.of(DockStatePayload::encode, DockStatePayload::decode);

    /**
     * Um lado. {@code block} é o bloco das máquinas ({@code null} sem bloco) e {@code machine} o nome dele, já pelo
     * {@code RouterBlock.machineName} (vazio sem bloco); {@code provider} é o nome do provedor encostado, ou
     * {@code null} sem provedor. {@code state} é o {@code DockStatus.State.ordinal()}.
     */
    public record Line(@Nullable ResourceLocation block, Component machine, @Nullable Component provider, int linked,
            int working, int unloaded, int outOfReach, int state) {
    }

    private static void encode(RegistryFriendlyByteBuf buf, DockStatePayload p) {
        ByteBufCodecs.VAR_INT.encode(buf, p.containerId);
        ByteBufCodecs.VAR_INT.encode(buf, p.total);
        ByteBufCodecs.VAR_INT.encode(buf, p.max);
        ByteBufCodecs.VAR_INT.encode(buf, p.radius);
        for (int i = 0; i < SIDES; i++) {
            Line line = p.lines.get(i);
            buf.writeBoolean(line.block != null);
            if (line.block != null) {
                ResourceLocation.STREAM_CODEC.encode(buf, line.block);
                ComponentSerialization.STREAM_CODEC.encode(buf, line.machine);
            }
            buf.writeBoolean(line.provider != null);
            if (line.provider != null) {
                ComponentSerialization.STREAM_CODEC.encode(buf, line.provider);
            }
            ByteBufCodecs.VAR_INT.encode(buf, line.linked);
            ByteBufCodecs.VAR_INT.encode(buf, line.working);
            ByteBufCodecs.VAR_INT.encode(buf, line.unloaded);
            ByteBufCodecs.VAR_INT.encode(buf, line.outOfReach);
            buf.writeByte(line.state);
        }
    }

    private static DockStatePayload decode(RegistryFriendlyByteBuf buf) {
        int containerId = ByteBufCodecs.VAR_INT.decode(buf);
        int total = ByteBufCodecs.VAR_INT.decode(buf);
        int max = ByteBufCodecs.VAR_INT.decode(buf);
        int radius = ByteBufCodecs.VAR_INT.decode(buf);
        List<Line> lines = new ArrayList<>(SIDES);
        for (int i = 0; i < SIDES; i++) {
            ResourceLocation block = null;
            Component machine = Component.empty();
            if (buf.readBoolean()) {
                block = ResourceLocation.STREAM_CODEC.decode(buf);
                machine = ComponentSerialization.STREAM_CODEC.decode(buf);
            }
            Component provider = buf.readBoolean() ? ComponentSerialization.STREAM_CODEC.decode(buf) : null;
            lines.add(new Line(block, machine, provider, ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), buf.readUnsignedByte()));
        }
        return new DockStatePayload(containerId, total, max, radius, List.copyOf(lines));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
