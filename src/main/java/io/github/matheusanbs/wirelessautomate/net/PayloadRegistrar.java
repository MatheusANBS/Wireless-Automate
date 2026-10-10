package io.github.matheusanbs.wirelessautomate.net;

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
 * <p>Os handlers rodam na thread principal ({@code consumerMainThread}, que também marca o pacote como
 * tratado), como o padrão {@code HandlerThread.MAIN} do NeoForge. A versão do canal é a do registrador,
 * aceita só igual dos dois lados.
 */
public final class PayloadRegistrar {
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
                .encoder((message, buf) -> encode(serverById, message.payload(), buf))
                .decoder(buf -> new ToServer(decode(toServer, buf)))
                .consumerMainThread((message, context) -> handle(serverById, message.payload(), context))
                .add();
        channel.messageBuilder(ToClient.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buf) -> encode(clientById, message.payload(), buf))
                .decoder(buf -> new ToClient(decode(toClient, buf)))
                .consumerMainThread((message, context) -> handle(clientById, message.payload(), context))
                .add();
        PacketDistributor.bind(channel, payload -> new ToServer(checked(serverById, payload)),
                payload -> new ToClient(checked(clientById, payload)));
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

    private static CustomPacketPayload checked(Map<ResourceLocation, Entry<?>> byId, CustomPacketPayload payload) {
        if (!byId.containsKey(payload.type().id())) {
            throw new IllegalArgumentException("Pacote não registrado nessa direção: " + payload.type().id());
        }
        return payload;
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

    /** Envelope cliente → servidor. */
    public record ToServer(CustomPacketPayload payload) {
    }

    /** Envelope servidor → cliente. */
    public record ToClient(CustomPacketPayload payload) {
    }
}
