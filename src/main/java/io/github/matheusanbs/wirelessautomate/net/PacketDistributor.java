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
 * {@link IllegalArgumentException}), e vai dentro do envelope da direção. Um payload acima do teto do vanilla
 * não sai (aviso no log; ver {@link PayloadRegistrar}), em vez de derrubar a conexão.
 */
public final class PacketDistributor {
    private static SimpleChannel channel;
    private static Function<CustomPacketPayload, @Nullable Object> toServer;
    private static Function<CustomPacketPayload, @Nullable Object> toClient;

    private PacketDistributor() {
    }

    /** O canal do mod, depois de registrados os pacotes, e o envelope de cada direção (nulo: grande demais). */
    public static void bind(SimpleChannel simpleChannel, Function<CustomPacketPayload, @Nullable Object> serverbound,
            Function<CustomPacketPayload, @Nullable Object> clientbound) {
        channel = simpleChannel;
        toServer = serverbound;
        toClient = clientbound;
    }

    /** Cliente → servidor. Só no cliente. */
    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        send(toServer.apply(payload), null);
        for (CustomPacketPayload other : payloads) {
            send(toServer.apply(other), null);
        }
    }

    /** Servidor → um jogador. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        var target = net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player);
        send(toClient.apply(payload), target);
        for (CustomPacketPayload other : payloads) {
            send(toClient.apply(other), target);
        }
    }

    private static void send(@Nullable Object envelope,
            net.minecraftforge.network.PacketDistributor.@Nullable PacketTarget target) {
        if (envelope == null) {
            return;
        }
        if (target == null) {
            channel().sendToServer(envelope);
        } else {
            channel().send(target, envelope);
        }
    }

    /**
     * Se o cliente dessa conexão negociou o canal do mod: o {@code connection.hasChannel(payload)} do NeoForge.
     * Troque {@code viewer.connection.hasChannel(payload)} por {@code PacketDistributor.hasChannel(viewer.connection,
     * payload)}. O {@code payload} não é usado: o canal do mod é um só e leva todos os pacotes, então basta saber
     * se o cliente tem o canal (o parâmetro fica para a chamada continuar igual à do main). Falso sem conexão ou com uma conexão sem canal de rede (jogadores falsos dos GameTests e de mods
     * de automação).
     */
    public static boolean hasChannel(@Nullable ServerGamePacketListenerImpl listener, CustomPacketPayload payload) {
        Connection connection = listener == null ? null : listener.connection;
        return channel != null && connection != null && connection.channel() != null
                && channel.isRemotePresent(connection);
    }

    /**
     * Do lado do cliente, se o servidor negociou o canal do mod: o {@code ClientPacketListener.hasChannel(type)}
     * do NeoForge. Troque {@code connection.hasChannel(X.TYPE)} por
     * {@code PacketDistributor.hasChannel(connection.getConnection(), X.TYPE)}. Há um canal só, então o tipo não
     * muda a resposta.
     */
    public static boolean hasChannel(@Nullable Connection connection, CustomPacketPayload.Type<?> type) {
        return channel != null && connection != null && channel.isRemotePresent(connection);
    }

    private static SimpleChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("Canal de rede do Wireless Automate ainda não registrado");
        }
        return channel;
    }
}
