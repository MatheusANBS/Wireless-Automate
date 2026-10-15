package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: o estado da tela do Núcleo aberta. A estação não vai: o desvio vai em passos de 0,05 MHz só
 * dentro de ±2 MHz ({@code TuneSignal.NO_SIGNAL} fora). Eficiência e teto em milésimos; {@code layout} 0 própria,
 * 1 Vitrine, 2 Faixa; {@code recharge} são os ticks andados da recarga em curso (0 sem Opaco ou sem redstone).
 */
public record ResonatorStatePayload(int containerId, long energy, long capacity, long generation, long maxGeneration,
        double dial, int efficiencyPermille, int ceilingPermille, boolean auto, boolean tuner, int deviationSteps,
        int recharge, int rechargeTicks, int energyPorts, int crystalPorts, int layout) implements CustomPacketPayload {
    public static final Type<ResonatorStatePayload> TYPE = new Type<>(WirelessAutomate.id("resonator_state"));
    /** À mão: o {@code StreamCodec.composite} só vai até 6 campos. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ResonatorStatePayload> STREAM_CODEC =
            StreamCodec.of(ResonatorStatePayload::encode, ResonatorStatePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ResonatorStatePayload p) {
        ByteBufCodecs.VAR_INT.encode(buf, p.containerId);
        ByteBufCodecs.VAR_LONG.encode(buf, p.energy);
        ByteBufCodecs.VAR_LONG.encode(buf, p.capacity);
        ByteBufCodecs.VAR_LONG.encode(buf, p.generation);
        ByteBufCodecs.VAR_LONG.encode(buf, p.maxGeneration);
        ByteBufCodecs.DOUBLE.encode(buf, p.dial);
        ByteBufCodecs.VAR_INT.encode(buf, p.efficiencyPermille);
        ByteBufCodecs.VAR_INT.encode(buf, p.ceilingPermille);
        ByteBufCodecs.BOOL.encode(buf, p.auto);
        ByteBufCodecs.BOOL.encode(buf, p.tuner);
        ByteBufCodecs.VAR_INT.encode(buf, p.deviationSteps);
        ByteBufCodecs.VAR_INT.encode(buf, p.recharge);
        ByteBufCodecs.VAR_INT.encode(buf, p.rechargeTicks);
        ByteBufCodecs.VAR_INT.encode(buf, p.energyPorts);
        ByteBufCodecs.VAR_INT.encode(buf, p.crystalPorts);
        ByteBufCodecs.VAR_INT.encode(buf, p.layout);
    }

    private static ResonatorStatePayload decode(RegistryFriendlyByteBuf buf) {
        return new ResonatorStatePayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
