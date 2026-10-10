package io.github.matheusanbs.wirelessautomate.net;

import java.util.function.Function;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

/**
 * Envio dos pacotes do mod. Imita o {@code net.neoforged.neoforge.network.PacketDistributor} (só o que o mod
 * usa) sobre o {@link SimpleChannel} do mod, para quem manda mudar só o import. O {@link PayloadRegistrar} liga
 * o canal com {@link #bind} ao registrá-lo; cada payload precisa estar registrado naquela direção (senão é
 * {@link IllegalArgumentException}), e vai dentro do envelope da direção.
 */
public final class PacketDistributor {
    private static SimpleChannel channel;
    private static Function<CustomPacketPayload, Object> toServer;
    private static Function<CustomPacketPayload, Object> toClient;

    private PacketDistributor() {
    }

    /** O canal do mod, depois de registrados os pacotes, e o envelope de cada direção. */
    public static void bind(SimpleChannel simpleChannel, Function<CustomPacketPayload, Object> serverbound,
            Function<CustomPacketPayload, Object> clientbound) {
        channel = simpleChannel;
        toServer = serverbound;
        toClient = clientbound;
    }

    /** Cliente → servidor. Só no cliente. */
    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        channel().sendToServer(toServer.apply(payload));
        for (CustomPacketPayload other : payloads) {
            channel().sendToServer(toServer.apply(other));
        }
    }

    /** Servidor → um jogador. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        var target = net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player);
        channel().send(target, toClient.apply(payload));
        for (CustomPacketPayload other : payloads) {
            channel().send(target, toClient.apply(other));
        }
    }

    /**
     * Se o cliente dessa conexão negociou o canal do mod: o {@code connection.hasChannel(payload)} do NeoForge.
     * Troque {@code viewer.connection.hasChannel(payload)} por {@code PacketDistributor.hasChannel(viewer.connection,
     * payload)}. Falso sem conexão ou com uma conexão sem canal de rede (jogadores falsos dos GameTests e de mods
     * de automação).
     */
    public static boolean hasChannel(@Nullable ServerGamePacketListenerImpl listener, CustomPacketPayload payload) {
        Connection connection = listener == null ? null : listener.connection;
        return channel != null && connection != null && connection.channel() != null
                && channel.isRemotePresent(connection);
    }

    private static SimpleChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("Canal de rede do Wireless Automate ainda não registrado");
        }
        return channel;
    }
}
