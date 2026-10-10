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
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
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
 * <p>Por baixo, um {@link SimpleChannel} do Forge com um envelope por direção ({@link ToServer} com
 * {@link NetworkDirection#PLAY_TO_SERVER}, {@link ToClient} com {@link NetworkDirection#PLAY_TO_CLIENT}) e uma
 * parte por direção ({@link PartToServer}, {@link PartToClient}): o Forge derruba a conexão que manda uma mensagem
 * na direção errada. Dentro do envelope vai o índice do payload na tabela daquela direção (VarInt) e o payload
 * pelo seu {@link StreamCodec}; um índice fora da tabela da direção é erro de decodificação. Assim o servidor
 * nunca decodifica um pacote de cliente com handler de cliente, e vice-versa. O envelope não precisa da
 * classe de cada payload (o {@link CustomPacketPayload.Type} do 1.21 só tem o id) e não tem o teto de 256
 * mensagens do discriminador de um byte do {@code SimpleChannel}.
 *
 * <p><b>Tamanho:</b> o 1.20.1 não divide pacotes grandes como o NeoForge 1.21. O vanilla recusa um pacote do
 * servidor acima de 1 MiB (exceção no envio) e o servidor derruba o cliente que manda mais de 32767 bytes. Por
 * isso cada payload é codificado uma vez, no envio ({@link PacketDistributor}), e medido: até {@link #MAX_TO_CLIENT}
 * ou {@link #MAX_TO_SERVER} ele vai inteiro no envelope, com os bytes medidos (sem codificar de novo); acima, vai em
 * partes ({@link PartToClient} de {@link #PART_TO_CLIENT} bytes, {@link PartToServer} de {@link #PART_TO_SERVER}),
 * e o outro lado as junta ({@link PacketParts}) e decodifica o payload quando chega a última, como o divisor do
 * NeoForge. O lado que recebe limita o payload montado: o servidor não junta mais que
 * {@link #MAX_ASSEMBLED_TO_SERVER} de um cliente, e o cliente, {@link #MAX_ASSEMBLED_TO_CLIENT}. Só um payload
 * acima desse teto não sai (aviso no log).
 *
 * <p>Os handlers rodam na thread principal ({@code consumerMainThread}, que também marca o pacote como
 * tratado), como o padrão {@code HandlerThread.MAIN} do NeoForge; as partes também são juntadas nela, na ordem
 * em que chegaram. A versão do canal é a do registrador, aceita só igual dos dois lados.
 */
public final class PayloadRegistrar {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Teto de um payload servidor → cliente num pacote só: o 1 MiB do vanilla menos uma folga para o discriminador. */
    public static final int MAX_TO_CLIENT = 1048576 - 64;
    /** Teto de um payload cliente → servidor num pacote só: os 32767 bytes do vanilla menos a folga. */
    public static final int MAX_TO_SERVER = 32767 - 64;
    /** Bytes de cada parte servidor → cliente (lotes de 512 KiB, como a lista do Baú). */
    public static final int PART_TO_CLIENT = 512 * 1024;
    /** Bytes de cada parte cliente → servidor: cabe nos 32767 com o discriminador e o cabeçalho da parte. */
    public static final int PART_TO_SERVER = 32000;
    /** Maior payload que o servidor junta a partir das partes de um cliente (o teto do NBT de um pacote). */
    public static final int MAX_ASSEMBLED_TO_SERVER = 2 * 1024 * 1024;
    /** Maior payload que o cliente junta a partir das partes do servidor. */
    public static final int MAX_ASSEMBLED_TO_CLIENT = 64 * 1024 * 1024;

    /** O registrador ligado ao canal ({@link #register}); os GameTests leem os pacotes por ele. */
    private static volatile @Nullable PayloadRegistrar registered;

    private final String version;
    private final List<Entry<?>> toServer = new ArrayList<>();
    private final List<Entry<?>> toClient = new ArrayList<>();
    private final Map<ResourceLocation, Entry<?>> serverById = new HashMap<>();
    private final Map<ResourceLocation, Entry<?>> clientById = new HashMap<>();
    /** Partes recebidas por conexão de cliente (servidor; só a thread principal mexe). */
    private final Map<Connection, PacketParts.Assembly> serverAssemblies = new WeakHashMap<>();
    /** Partes recebidas do servidor (cliente; só a thread principal mexe). */
    private final PacketParts.Assembly clientAssembly = new PacketParts.Assembly(MAX_ASSEMBLED_TO_CLIENT);
    private @Nullable SimpleChannel channel;

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
        channel.messageBuilder(PartToServer.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buf) -> writePart(message.part(), buf))
                .decoder(buf -> new PartToServer(readPart(buf, MAX_ASSEMBLED_TO_SERVER, PART_TO_SERVER)))
                .consumerMainThread((message, context) -> {
                    Connection connection = context.get().getNetworkManager();
                    PacketParts.Assembly assembly = serverAssemblies.computeIfAbsent(connection,
                            c -> new PacketParts.Assembly(MAX_ASSEMBLED_TO_SERVER));
                    CustomPacketPayload payload = assemble(toServer, assembly, message.part());
                    if (payload != null) {
                        handle(serverById, payload, context);
                    }
                })
                .add();
        channel.messageBuilder(PartToClient.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buf) -> writePart(message.part(), buf))
                .decoder(buf -> new PartToClient(readPart(buf, MAX_ASSEMBLED_TO_CLIENT, PART_TO_CLIENT)))
                .consumerMainThread((message, context) -> {
                    CustomPacketPayload payload = assemble(toClient, clientAssembly, message.part());
                    if (payload != null) {
                        handle(clientById, payload, context);
                    }
                })
                .add();
        PacketDistributor.bind(channel, this::envelopesToServer, this::envelopesToClient);
        this.channel = channel;
        registered = this;
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

    private List<Object> envelopesToServer(CustomPacketPayload payload) {
        return envelopes(serverById, payload, MAX_TO_SERVER, PART_TO_SERVER, MAX_ASSEMBLED_TO_SERVER,
                "cliente → servidor", ToServer::new, PartToServer::new);
    }

    private List<Object> envelopesToClient(CustomPacketPayload payload) {
        return envelopes(clientById, payload, MAX_TO_CLIENT, PART_TO_CLIENT, MAX_ASSEMBLED_TO_CLIENT,
                "servidor → cliente", ToClient::new, PartToClient::new);
    }

    /**
     * Os envelopes de um payload, codificado uma vez: um só com os bytes medidos, se ele cabe em {@code max};
     * senão as partes de {@code partSize} bytes. Vazio, com um aviso no log, só acima de {@code assembledMax} (o
     * outro lado não juntaria). Um payload não registrado nessa direção é {@link IllegalArgumentException}, como
     * no NeoForge.
     */
    private static List<Object> envelopes(Map<ResourceLocation, Entry<?>> byId, CustomPacketPayload payload, int max,
            int partSize, int assembledMax, String direction, BiFunction<CustomPacketPayload, ByteBuf, Object> whole,
            Function<PacketParts.Part, Object> part) {
        if (!byId.containsKey(payload.type().id())) {
            throw new IllegalArgumentException("Pacote não registrado nessa direção: " + payload.type().id());
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encode(byId, payload, buf);
        int size = buf.readableBytes();
        if (size <= max) {
            return List.of(whole.apply(payload, buf));
        }
        if (size > assembledMax) {
            LOGGER.warn("Pacote {} ({}) com {} bytes passa do teto de {} mesmo em partes; não foi enviado",
                    payload.type().id(), direction, size, assembledMax);
            return List.of();
        }
        byte[] bytes = new byte[size];
        buf.getBytes(buf.readerIndex(), bytes);
        List<Object> parts = new ArrayList<>();
        for (PacketParts.Part p : PacketParts.split(bytes, partSize)) {
            parts.add(part.apply(p));
        }
        return parts;
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

    private static void writePart(PacketParts.Part part, FriendlyByteBuf buf) {
        buf.writeVarInt(part.total());
        buf.writeVarInt(part.offset());
        buf.writeBytes(part.bytes());
    }

    /**
     * Lê uma parte (o resto do pacote são os bytes dela). Recusa, sem alocar, um total acima de
     * {@code assembledMax} ou uma parte maior que as do envio ({@code partSize}).
     */
    private static PacketParts.Part readPart(FriendlyByteBuf buf, int assembledMax, int partSize) {
        int total = buf.readVarInt();
        int offset = buf.readVarInt();
        int length = buf.readableBytes();
        if (total <= 0 || total > assembledMax || length <= 0 || length > partSize || offset < 0
                || offset > total - length) {
            throw new DecoderException("Parte de pacote inválida: total " + total + ", deslocamento " + offset
                    + ", " + length + " bytes");
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new PacketParts.Part(total, offset, bytes);
    }

    /**
     * Junta a parte; o payload decodificado quando ela é a última, ou {@code null}. Parte fora de ordem, ou
     * payload montado que não decodifica: aviso no log e o pacote se perde (a conexão fica).
     */
    private static @Nullable CustomPacketPayload assemble(List<Entry<?>> table, PacketParts.Assembly assembly,
            PacketParts.Part part) {
        byte[] whole;
        try {
            whole = assembly.accept(part);
        } catch (IllegalStateException e) {
            LOGGER.warn("Parte de pacote recusada: {}", e.getMessage());
            return null;
        }
        if (whole == null) {
            return null;
        }
        try {
            return decode(table, new FriendlyByteBuf(Unpooled.wrappedBuffer(whole)));
        } catch (RuntimeException e) {
            LOGGER.warn("Pacote montado de {} bytes não decodificou: {}", whole.length, e.toString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void handle(Map<ResourceLocation, Entry<?>> byId,
            CustomPacketPayload payload, Supplier<NetworkEvent.Context> context) {
        Entry<T> entry = (Entry<T>) byId.get(payload.type().id());
        entry.handler().handle((T) payload, IPayloadContext.of(context));
    }

    // ------------------------------------------------------------------ GameTests

    /**
     * Os pacotes do vanilla que levam {@code payload} ao servidor, como o cliente os manda: um só, ou as partes.
     * Para os GameTests do caminho cliente → servidor.
     */
    public static List<Packet<?>> packetsToServer(CustomPacketPayload payload) {
        PayloadRegistrar registrar = requireRegistered();
        List<Packet<?>> packets = new ArrayList<>();
        for (Object envelope : registrar.envelopesToServer(payload)) {
            packets.add(registrar.channel.toVanillaPacket(envelope, NetworkDirection.PLAY_TO_SERVER));
        }
        return packets;
    }

    /**
     * Lê os dados de um pacote do canal do mod como o lado que recebe: o discriminador do {@code SimpleChannel}
     * (o que o Forge lê) e a mensagem, pelos mesmos leitores e a mesma montagem do canal. Devolve o payload de um
     * envelope inteiro, o payload montado na última parte, ou {@code null} numa parte do meio. Para os GameTests.
     *
     * @param toServer os dados vão ao servidor (senão, ao cliente)
     * @param assembly a montagem daquele lado da conexão (uma por conexão)
     */
    public static @Nullable CustomPacketPayload receive(boolean toServer, PacketParts.Assembly assembly,
            FriendlyByteBuf data) {
        PayloadRegistrar registrar = requireRegistered();
        List<Entry<?>> table = toServer ? registrar.toServer : registrar.toClient;
        int discriminator = data.readUnsignedByte();
        if (discriminator == (toServer ? 0 : 1)) {
            return decode(table, data);
        }
        if (discriminator == (toServer ? 2 : 3)) {
            PacketParts.Part part = toServer ? readPart(data, MAX_ASSEMBLED_TO_SERVER, PART_TO_SERVER)
                    : readPart(data, MAX_ASSEMBLED_TO_CLIENT, PART_TO_CLIENT);
            return assemble(table, assembly, part);
        }
        throw new DecoderException("Discriminador " + discriminator + " fora da direção");
    }

    private static PayloadRegistrar requireRegistered() {
        PayloadRegistrar registrar = registered;
        if (registrar == null || registrar.channel == null) {
            throw new IllegalStateException("Canal de rede do Wireless Automate ainda não registrado");
        }
        return registrar;
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

    /** Uma parte de um payload cliente → servidor grande demais para um pacote só. */
    public record PartToServer(PacketParts.Part part) {
    }

    /** Uma parte de um payload servidor → cliente grande demais para um pacote só. */
    public record PartToClient(PacketParts.Part part) {
    }
}
