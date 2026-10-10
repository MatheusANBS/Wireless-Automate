package io.github.matheusanbs.wirelessautomate.net;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Envio dos pacotes do mod. Imita o {@code net.neoforged.neoforge.network.PacketDistributor} (só o que o mod
 * usa) sobre o {@link SimpleChannel} do mod, para quem manda mudar só o import. O {@code ModPayloads} liga o
 * canal com {@link #bind} ao registrá-lo; cada payload precisa estar registrado no canal pela sua classe.
 */
public final class PacketDistributor {
    private static SimpleChannel channel;

    private PacketDistributor() {
    }

    /** O canal do mod, depois de registrados os pacotes. */
    public static void bind(SimpleChannel simpleChannel) {
        channel = simpleChannel;
    }

    /** Cliente → servidor. Só no cliente. */
    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        channel().sendToServer(payload);
        for (CustomPacketPayload other : payloads) {
            channel().sendToServer(other);
        }
    }

    /** Servidor → um jogador. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        var target = net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player);
        channel().send(target, payload);
        for (CustomPacketPayload other : payloads) {
            channel().send(target, other);
        }
    }

    private static SimpleChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("Canal de rede do Wireless Automate ainda não registrado");
        }
        return channel;
    }
}
