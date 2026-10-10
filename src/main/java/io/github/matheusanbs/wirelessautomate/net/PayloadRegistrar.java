package io.github.matheusanbs.wirelessautomate.net;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Registro dos pacotes do mod. Imita o {@code net.neoforged.neoforge.network.registration.PayloadRegistrar}
 * do 1.21 ({@link #playToClient}, {@link #playToServer}), para o {@code ModPayloads} do {@code main} mudar
 * só os imports e a primeira e a última linha do {@code register}.
 *
 * <p>Por baixo, um {@link SimpleChannel} do Forge com duas mensagens, um envelope por direção
 * ({@link ToServer} com {@link NetworkDirection#PLAY_TO_SERVER}, {@link ToClient} com
 * {@link NetworkDirection#PLAY_TO_CLIENT}): o Forge derruba a conexão que manda um envelope na direção
 * errada. Dentro do envelope vai o índice do payload na tabela daquela direção (VarInt) e o payload pelo
 * seu {@link StreamCodec}; um índice fora da tabela da direção é erro de decodificação. Assim o servidor
 * nunca decodifica um pacote de cliente com handler de cliente, e vice-versa. O envelope não precisa da
 * classe de cada payload (o {@link CustomPacketPayload.Type} do 1.21 só tem o id) e não tem o teto de 256
 * mensagens do discriminador de um byte do {@code SimpleChannel}.
 *
 * <p><b>Tamanho:</b> o 1.20.1 não divide pacotes grandes como o NeoForge 1.21. O vanilla recusa um pacote do
 * servidor acima de 1 MiB (exceção no envio) e o servidor derruba o cliente que manda mais de 32767 bytes. Por
 * isso cada payload é codificado uma vez, no envio ({@link PacketDistributor}), e medido: acima de
 * {@link #MAX_TO_CLIENT} ou {@link #MAX_TO_SERVER} ele não sai, com um aviso no log (a ação se perde, a conexão
 * fica). Os bytes medidos vão no envelope, sem codificar de novo. Quem pode passar do teto em uso normal manda
 * em lotes menores (a lista do Baú) ou reduz os dados (a abertura das telas, {@link ServerMenus}).
 *
 * <p>Os handlers rodam na thread principal ({@code consumerMainThread}, que também marca o pacote como
 * tratado), como o padrão {@code HandlerThread.MAIN} do NeoForge. A versão do canal é a do registrador,
 * aceita só igual dos dois lados.
 */
public final class PayloadRegistrar {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Teto de um payload servidor → cliente: o 1 MiB do vanilla menos uma folga para o discriminador do canal. */
    public static final int MAX_TO_CLIENT = 1048576 - 64;
    /** Teto de um payload cliente → servidor: os 32767 bytes do vanilla menos a folga. */
    public static final int MAX_TO_SERVER = 32767 - 64;

    private final String version;
    private final List<Entry<?>> toServer = new ArrayList<>();
    private final List<Entry<?>> toClient = new ArrayList<>();
    private final Map<ResourceLocation, Entry<?>> serverById = new HashMap<>();
    private final Map<ResourceLocation, Entry<?>> clientById = new HashMap<>();

    public PayloadRegistrar(String version) {
        this.version = version;
    }

    /** Servidor → cliente. O handler roda no cliente, na thread principal. */
    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        add(toClient, clientById, type, codec, handler);
        return this;
    }

    /** Cliente → servidor. O handler roda no servidor, na thread principal. */
    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        add(toServer, serverById, type, codec, handler);
        return this;
    }

    /**
     * Cria o canal {@code name} com os pacotes registrados e o liga ao {@link PacketDistributor}. Chame uma vez,
     * antes de o registro de canais do Forge fechar (no {@code FMLCommonSetupEvent} ou antes).
     */
    public SimpleChannel register(ResourceLocation name) {
        SimpleChannel channel = NetworkRegistry.newSimpleChannel(name, () -> version, version::equals,
                version::equals);
        channel.messageBuilder(ToServer.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buf) -> write(serverById, message.payload(), message.encoded(), buf))
                .decoder(buf -> new ToServer(decode(toServer, buf), null))
                .consumerMainThread((message, context) -> handle(serverById, message.payload(), context))
                .add();
        channel.messageBuilder(ToClient.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buf) -> write(clientById, message.payload(), message.encoded(), buf))
                .decoder(buf -> new ToClient(decode(toClient, buf), null))
                .consumerMainThread((message, context) -> handle(clientById, message.payload(), context))
                .add();
        PacketDistributor.bind(channel, payload -> {
            ByteBuf encoded = measured(serverById, payload, MAX_TO_SERVER, "cliente → servidor");
            return encoded == null ? null : new ToServer(payload, encoded);
        }, payload -> {
            ByteBuf encoded = measured(clientById, payload, MAX_TO_CLIENT, "servidor → cliente");
            return encoded == null ? null : new ToClient(payload, encoded);
        });
        return channel;
    }

    private static <T extends CustomPacketPayload> void add(List<Entry<?>> table, Map<ResourceLocation, Entry<?>> byId,
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) {
        if (byId.containsKey(type.id())) {
            throw new IllegalArgumentException("Pacote registrado duas vezes: " + type.id());
        }
        Entry<T> entry = new Entry<>(table.size(), codec, handler);
        table.add(entry);
        byId.put(type.id(), entry);
    }

    /**
     * O payload codificado (índice + corpo), ou {@code null} com um aviso no log se passa de {@code max}. Um
     * payload não registrado nessa direção é {@link IllegalArgumentException}, como no NeoForge.
     */
    private static @Nullable ByteBuf measured(Map<ResourceLocation, Entry<?>> byId, CustomPacketPayload payload,
            int max, String direction) {
        if (!byId.containsKey(payload.type().id())) {
            throw new IllegalArgumentException("Pacote não registrado nessa direção: " + payload.type().id());
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encode(byId, payload, buf);
        if (buf.readableBytes() > max) {
            LOGGER.warn("Pacote {} ({}) com {} bytes passa do teto de {}; não foi enviado", payload.type().id(),
                    direction, buf.readableBytes(), max);
            return null;
        }
        return buf;
    }

    /** Os bytes já medidos no envio; sem eles (quem usa o canal direto), codifica aqui. */
    private static void write(Map<ResourceLocation, Entry<?>> byId, CustomPacketPayload payload,
            @Nullable ByteBuf encoded, FriendlyByteBuf buf) {
        if (encoded != null) {
            buf.writeBytes(encoded, encoded.readerIndex(), encoded.readableBytes());
        } else {
            encode(byId, payload, buf);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void encode(Map<ResourceLocation, Entry<?>> byId,
            CustomPacketPayload payload, FriendlyByteBuf buf) {
        Entry<T> entry = (Entry<T>) byId.get(payload.type().id());
        if (entry == null) {
            throw new EncoderException("Pacote não registrado nessa direção: " + payload.type().id());
        }
        RegistryFriendlyByteBuf target = RegistryFriendlyByteBuf.wrap(buf);
        target.writeVarInt(entry.index());
        entry.codec().encode(target, (T) payload);
    }

    private static CustomPacketPayload decode(List<Entry<?>> table, FriendlyByteBuf buf) {
        RegistryFriendlyByteBuf source = RegistryFriendlyByteBuf.wrap(buf);
        int index = source.readVarInt();
        if (index < 0 || index >= table.size()) {
            throw new DecoderException("Pacote desconhecido nessa direção: " + index);
        }
        CustomPacketPayload payload = table.get(index).codec().decode(source);
        if (source.isReadable()) {
            throw new DecoderException("Pacote " + payload.type().id() + " maior que o esperado: sobraram "
                    + source.readableBytes() + " bytes");
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void handle(Map<ResourceLocation, Entry<?>> byId,
            CustomPacketPayload payload, Supplier<NetworkEvent.Context> context) {
        Entry<T> entry = (Entry<T>) byId.get(payload.type().id());
        entry.handler().handle((T) payload, IPayloadContext.of(context));
    }

    private record Entry<T extends CustomPacketPayload>(int index, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) {
    }

    /** Envelope cliente → servidor; {@code encoded} são os bytes medidos no envio (nulo no recebido). */
    public record ToServer(CustomPacketPayload payload, @Nullable ByteBuf encoded) {
    }

    /** Envelope servidor → cliente; {@code encoded} são os bytes medidos no envio (nulo no recebido). */
    public record ToClient(CustomPacketPayload payload, @Nullable ByteBuf encoded) {
    }
}
