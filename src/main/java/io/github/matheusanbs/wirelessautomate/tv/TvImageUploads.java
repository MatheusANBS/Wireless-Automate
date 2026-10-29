package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.packet.TvImageReplyPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvImageUploadPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * O envio de imagens dos jogadores ao armazém ({@link TvImageStore}), em partes. No {@code START} o servidor confere,
 * nesta ordem: {@code tv.images.enabled}, quem pode enviar ({@code uploaders}), a lista de banidos, a trava automática,
 * o cabeçalho declarado contra os tetos ({@link TvImageLimits#check}) e a biblioteca cheia. Um resumo que o armazém já
 * tem não pede os bytes: só entra na biblioteca. Senão confere o teto do mundo, a cota do jogador, a memória dos envios
 * em curso ({@link #MAX_IN_FLIGHT}) e o limite por minuto ({@link TvByteRate}, que reserva o arquivo inteiro; a
 * terceira batida em 10 minutos trava o jogador, {@link TvUploadAbuse}), e só então pede os bytes. Os pedaços chegam
 * na ordem, até o tamanho declarado; no {@code END}, o resumo e o cabeçalho de verdade são conferidos contra os bytes
 * e o arquivo é gravado, os dois fora da thread do servidor, e a imagem entra no armazém e na biblioteca.
 * <p>
 * Um envio por jogador (um novo descarta o anterior, a não ser o que já está no fim, que recusa o novo com
 * {@code BUSY}); o que fica {@link #TIMEOUT_MS} sem pedaço, ou cujo jogador sai, é descartado. Toda recusa avisa na
 * barra de ação e responde ao cliente com o motivo. Sem envio em curso, o tick é a checagem do mapa vazio.
 */
public final class TvImageUploads {
    static final long TIMEOUT_MS = 30_000L;
    /** Os bytes de todos os envios em curso somados, no máximo: oito arquivos no teto padrão. */
    static final long MAX_IN_FLIGHT = 64L << 20;
    private static final String KEY = "message.wirelessautomate.tv.image.";

    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final TvByteRate<UUID> RATE = new TvByteRate<>();
    private static final TvUploadAbuse<UUID> ABUSE = new TvUploadAbuse<>();
    /**
     * O limite de começos de envio, 8 por segundo: um começo com resumo que já existe pode mandar a biblioteca (até
     * 900 KB) de volta, e repetir isso não pode virar amplificação.
     */
    private static final TvRateGate START_GATE = new TvRateGate(8);
    private static long inFlight;
    private static int ticks;
    private static LongSupplier clock = Util::getMillis;

    private TvImageUploads() {
    }

    /** Um envio em curso: o que foi declarado e os bytes que já chegaram. */
    private static final class Upload {
        final String hash;
        final String name;
        final TvImageFormat format;
        final int width;
        final int height;
        final int frames;
        final byte[] data;
        int received;
        long last;
        boolean finishing;

        Upload(TvImageUploadPayload start, TvImageFormat format, long now) {
            this.hash = start.hash();
            this.name = start.name();
            this.format = format;
            this.width = start.width();
            this.height = start.height();
            this.frames = start.frames();
            this.data = new byte[start.size()];
            this.last = now;
        }
    }

    /** Trata um pacote do envio; devolve a resposta mandada (ou que iria, sem conexão), ou {@code null} sem nenhuma. */
    public static @Nullable TvImageReplyPayload handle(@Nullable ServerPlayer player, TvImageUploadPayload payload) {
        if (player == null || !TvImageHash.valid(payload.hash())) {
            return null;
        }
        return switch (payload.kind()) {
            case START -> START_GATE.allow(player.getUUID(), player.level().getGameTime())
                    ? start(player, payload) : null;
            case CHUNK -> chunk(player, payload);
            case END -> end(player, payload);
            case CANCEL -> {
                Upload upload = UPLOADS.get(player.getUUID());
                if (upload != null && upload.hash.equals(payload.hash()) && !upload.finishing) {
                    drop(player.getUUID());
                }
                yield null;
            }
        };
    }

    private static TvImageReplyPayload start(ServerPlayer player, TvImageUploadPayload p) {
        MinecraftServer server = player.server;
        TvImageLimits limits = TvImageConfig.limits(server);
        TvImageStore store = TvImageStore.get(server);
        UUID id = player.getUUID();
        long now = clock.getAsLong();
        if (!limits.enabled()) {
            return refuse(player, p.hash(), TvImageRefusal.DISABLED);
        }
        if (limits.opsOnly() && !player.hasPermissions(2)) {
            return refuse(player, p.hash(), TvImageRefusal.NOT_ALLOWED);
        }
        if (store.banned(id)) {
            return refuse(player, p.hash(), TvImageRefusal.BANNED);
        }
        if (ABUSE.cooldownLeft(id, now) > 0) {
            ABUSE.refused(id);
            return refuse(player, p.hash(), TvImageRefusal.COOLDOWN);
        }
        TvImageFormat format = TvImageFormat.byId(p.format());
        if (format == null) {
            return refuse(player, p.hash(), TvImageRefusal.BAD_FORMAT);
        }
        TvImageRefusal declared = limits.check(format, p.width(), p.height(), p.frames(), p.size());
        if (declared != null) {
            return refuse(player, p.hash(), declared);
        }
        TvLibrary library = TvLibrary.get(server);
        if (!library.hasPicture(id, p.hash()) && library.pictures(id).size() >= TvLibrary.MAX_PICTURES) {
            return refuse(player, p.hash(), TvImageRefusal.LIBRARY_FULL);
        }
        Upload current = UPLOADS.get(id);
        if (current != null && current.finishing) {
            // O fim do anterior ainda roda no executor com o arquivo: descartá-lo tiraria os bytes do inFlight cedo.
            return refuse(player, p.hash(), TvImageRefusal.BUSY);
        }
        drop(id);
        if (store.contains(p.hash())) {
            return accepted(player, p.hash(), p.name());
        }
        TvImageRefusal space = limits.checkSpace(store.totalBytes(), store.bytesOf(id), p.size());
        if (space != null) {
            return refuse(player, p.hash(), space);
        }
        if (inFlight + p.size() > MAX_IN_FLIGHT) {
            return refuse(player, p.hash(), TvImageRefusal.BUSY);
        }
        if (!RATE.tryUse(id, p.size(), limits.bytesPerMinute(), now)) {
            if (ABUSE.strike(id, now, limits.cooldownMinutes() * 60_000L)) {
                alertOperators(server, player, limits.cooldownMinutes());
            }
            return refuse(player, p.hash(), TvImageRefusal.RATE);
        }
        UPLOADS.put(id, new Upload(p, format, now));
        inFlight += p.size();
        return reply(player, new TvImageReplyPayload(p.hash(), TvImageReplyPayload.Status.SEND, ""));
    }

    private static @Nullable TvImageReplyPayload chunk(ServerPlayer player, TvImageUploadPayload p) {
        Upload upload = UPLOADS.get(player.getUUID());
        if (upload == null || !upload.hash.equals(p.hash()) || upload.finishing) {
            return null;
        }
        int length = p.data().length;
        if (p.offset() != upload.received || length == 0 || upload.received + length > upload.data.length) {
            drop(player.getUUID());
            return refuse(player, p.hash(), TvImageRefusal.MISMATCH);
        }
        System.arraycopy(p.data(), 0, upload.data, upload.received, length);
        upload.received += length;
        upload.last = clock.getAsLong();
        return null;
    }

    /**
     * O fim: com todos os bytes, o resumo, o cabeçalho de verdade (que tem de bater com o declarado e caber nos tetos
     * de agora) e a gravação do arquivo vão ao executor de entrada e saída; a volta põe a imagem no armazém e na
     * biblioteca e responde. Até lá o envio fica, marcado, para um novo {@code START} não o descartar no meio.
     */
    private static @Nullable TvImageReplyPayload end(ServerPlayer player, TvImageUploadPayload p) {
        UUID id = player.getUUID();
        Upload upload = UPLOADS.get(id);
        if (upload == null || !upload.hash.equals(p.hash()) || upload.finishing) {
            return null;
        }
        if (upload.received != upload.data.length) {
            drop(id);
            return refuse(player, p.hash(), TvImageRefusal.MISMATCH);
        }
        upload.finishing = true;
        MinecraftServer server = player.server;
        TvImageLimits limits = TvImageConfig.limits(server);
        TvImageStore store = TvImageStore.get(server);
        Util.ioPool().execute(() -> {
            TvImageHeader.Result header = TvImageHeader.read(upload.data, limits.maxSize(), limits.maxFrames());
            TvImageRefusal problem = verify(upload, header);
            boolean written = problem == null && store.writeFile(upload.hash, upload.format, upload.data);
            TvImageRefusal result = problem != null ? problem : written ? null : TvImageRefusal.FAILED;
            server.execute(() -> finish(server, id, upload, header.info(), result));
        });
        return null;
    }

    /** A conferência dos bytes contra o declarado (fora da thread do servidor); {@code null} se valem. */
    private static @Nullable TvImageRefusal verify(Upload upload, TvImageHeader.Result header) {
        if (!TvImageHash.of(upload.data).equals(upload.hash)) {
            return TvImageRefusal.MISMATCH;
        }
        if (!header.ok()) {
            return switch (header.problem()) {
                case TOO_LARGE -> TvImageRefusal.TOO_LARGE;
                case TOO_MANY_FRAMES -> TvImageRefusal.TOO_MANY_FRAMES;
                case null, default -> TvImageRefusal.BAD_FORMAT;
            };
        }
        TvImageHeader.Info info = header.info();
        if (info.format() != upload.format || info.width() != upload.width || info.height() != upload.height
                || info.frames() != upload.frames) {
            return TvImageRefusal.MISMATCH;
        }
        return null;
    }

    private static void finish(MinecraftServer server, UUID id, Upload upload, TvImageHeader.@Nullable Info info,
            @Nullable TvImageRefusal problem) {
        if (UPLOADS.get(id) == upload) {
            drop(id);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (problem != null) {
            if (player != null) {
                refuse(player, upload.hash, problem);
            }
            return;
        }
        TvImageStore store = TvImageStore.get(server);
        store.added(upload.hash, info, upload.data.length, id);
        TvLibrary library = TvLibrary.get(server);
        boolean added = library.addPicture(id, upload.hash, upload.name) == TvLibrary.Result.OK;
        store.libraryChanged(id, library.pictureHashes(id));
        if (player != null) {
            TvPictureSender.markKnown(player, upload.hash);
            if (added) {
                sendLibrary(player, library);
            }
            reply(player, new TvImageReplyPayload(upload.hash, TvImageReplyPayload.Status.OK, ""));
            player.displayClientMessage(Component.translatable(KEY + "saved"), true);
        }
    }

    /** A imagem já estava no armazém: entra na biblioteca sem bytes, e o cliente que a mandou já a tem. */
    private static TvImageReplyPayload accepted(ServerPlayer player, String hash, String name) {
        MinecraftServer server = player.server;
        TvLibrary library = TvLibrary.get(server);
        if (library.addPicture(player.getUUID(), hash, name) == TvLibrary.Result.OK) {
            TvImageStore.get(server).libraryChanged(player.getUUID(), library.pictureHashes(player.getUUID()));
            sendLibrary(player, library);
        }
        TvPictureSender.markKnown(player, hash);
        player.displayClientMessage(Component.translatable(KEY + "saved"), true);
        return reply(player, new TvImageReplyPayload(hash, TvImageReplyPayload.Status.OK, ""));
    }

    /** Com o editor da Tela aberto, a biblioteca nova vai a ele. */
    private static void sendLibrary(ServerPlayer player, TvLibrary library) {
        if (player.containerMenu instanceof TvMenu menu) {
            TvLibraryActions.send(player, menu.containerId, library);
        }
    }

    private static TvImageReplyPayload refuse(ServerPlayer player, String hash, TvImageRefusal refusal) {
        player.displayClientMessage(Component.translatable(refusal.key()), true);
        return reply(player, new TvImageReplyPayload(hash, TvImageReplyPayload.Status.REFUSED, refusal.name()));
    }

    private static TvImageReplyPayload reply(ServerPlayer player, TvImageReplyPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        return payload;
    }

    /** A terceira batida no limite: um aviso a cada operador online e uma linha no log. Ninguém é expulso. */
    private static void alertOperators(MinecraftServer server, ServerPlayer player, int minutes) {
        String name = player.getGameProfile().getName();
        WirelessAutomate.LOGGER.warn("Tela de Sinal: {} bateu no limite de envio de imagens {} vezes em 10 minutos e "
                + "fica {} min sem enviar", name, TvUploadAbuse.STRIKES, minutes);
        Component message = Component.translatable(KEY + "abuse_alert", name, minutes).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.hasPermissions(2)) {
                online.sendSystemMessage(message);
            }
        }
    }

    private static void drop(UUID player) {
        Upload upload = UPLOADS.remove(player);
        if (upload != null) {
            inFlight -= upload.data.length;
        }
    }

    /** A cada tick do servidor: uma vez por segundo, descarta os envios parados há {@link #TIMEOUT_MS}. */
    public static void tick(MinecraftServer server) {
        if (UPLOADS.isEmpty() || ++ticks < 20) {
            return;
        }
        ticks = 0;
        long now = clock.getAsLong();
        Iterator<Map.Entry<UUID, Upload>> it = UPLOADS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Upload> entry = it.next();
            Upload upload = entry.getValue();
            if (!upload.finishing && now - upload.last >= TIMEOUT_MS) {
                it.remove();
                inFlight -= upload.data.length;
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    refuse(player, upload.hash, TvImageRefusal.TIMEOUT);
                }
            }
        }
    }

    /** As recusas por jogador desde a subida do servidor (a lista {@code /wa tela imagens}). */
    public static Map<UUID, Integer> refusals() {
        return ABUSE.refusals();
    }

    /**
     * O jogador que saiu: o envio dele some (o que já está no fim termina antes). O que ele gastou no minuto fica
     * (sair e voltar não zera o limite), e a janela de todos anda, para quem não volta não ficar no mapa.
     */
    public static void forget(UUID player) {
        Upload upload = UPLOADS.get(player);
        if (upload == null || !upload.finishing) {
            // O que está no fim sai sozinho quando o executor volta (finish), com os bytes contados até lá.
            drop(player);
        }
        START_GATE.forget(player);
        RATE.sweep(clock.getAsLong());
    }

    /** Parada do servidor. */
    public static void reset() {
        UPLOADS.clear();
        START_GATE.clear();
        RATE.clear();
        ABUSE.clear();
        inFlight = 0;
        ticks = 0;
        clock = Util::getMillis;
    }

    /** O relógio dos limites e do prazo (só os testes; {@code null} volta ao de verdade). */
    public static void setClockForTests(@Nullable LongSupplier testClock) {
        clock = testClock == null ? Util::getMillis : testClock;
    }

    /** Se o jogador tem um envio em curso. */
    public static boolean uploadingForTests(UUID player) {
        return UPLOADS.containsKey(player);
    }

    /** Roda o descarte dos parados agora. */
    public static void timeoutNowForTests(MinecraftServer server) {
        ticks = 19;
        tick(server);
    }
}
