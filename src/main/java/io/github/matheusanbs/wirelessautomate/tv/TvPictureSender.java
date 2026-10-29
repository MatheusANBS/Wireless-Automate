package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvImageDataPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvImageHavePayload;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A entrega das imagens enviadas aos clientes, pela fila com orçamento do {@link TvArtSender}. Cada jogador recebe
 * cada imagem uma vez por conexão: as que ele já tem ficam anotadas ({@link #KNOWN}), seja porque já foram mandadas,
 * porque ele mesmo as enviou ({@link #markKnown}) ou porque o cliente disse que as tem no cache em disco
 * ({@link #onHave}: uma lista por conexão, limitada, só de resumos que o armazém tem, que só tira envios e nunca pede
 * nenhum, então não há amplificação).
 * <p>
 * Quem pede: a volta do {@link TvManager} com as Telas perto de cada jogador ({@link #visit}) e a abertura do menu da
 * Tela, com as imagens dela e as da biblioteca do jogador ({@link #sendOnOpen}). O pedido entra em
 * {@link #WANTED}; o tick lê os bytes do armazém (no executor, uma vez) e, quando chegam, põe a imagem na fila em
 * pedaços de até {@link TvImageDataPayload#MAX_CHUNK}. Com {@code tv.images.enabled} desligado, nada vai. Sem pedido,
 * o tick é a checagem do mapa vazio. Só na thread do servidor.
 */
public final class TvPictureSender {
    private static final Map<UUID, Set<String>> KNOWN = new HashMap<>();
    private static final Map<UUID, Set<String>> WANTED = new HashMap<>();
    /** Quem já mandou a lista do cache nesta conexão (a segunda é ignorada). */
    private static final Set<UUID> HAVE = new HashSet<>();
    private static long chunksSent;

    private TvPictureSender() {
    }

    /** Um pedaço de uma imagem na fila de um jogador. */
    private record Chunk(String hash, Optional<UUID> author, byte[] data, int offset, int length)
            implements TvArtSender.Item {
        @Override
        public int size() {
            return TvImageConfig.enabled() ? length + 64 : -1;
        }

        @Override
        public void send(ServerPlayer player) {
            chunksSent++;
            TvArtSender.count(length, player.getServer().getTickCount());
            if (!TvArtSender.forced() && player.connection != null
                    && player.connection.hasChannel(TvImageDataPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, new TvImageDataPayload(hash, author, data.length, offset,
                        Arrays.copyOfRange(data, offset, offset + length)));
            }
        }
    }

    /** A Tela na volta dela: as imagens que ela usa vão a quem está perto e não as tem. */
    static void visit(TvBlockEntity tv, List<ServerPlayer> near) {
        List<TvPicture> pictures = tv.layout().pictures();
        if (pictures.isEmpty() || near.isEmpty() || !TvImageConfig.enabled()) {
            return;
        }
        for (int i = 0; i < near.size(); i++) {
            ServerPlayer player = near.get(i);
            for (int j = 0; j < pictures.size(); j++) {
                want(player, pictures.get(j).hash());
            }
        }
    }

    /** A abertura do menu da Tela: as imagens dela e as da biblioteca do jogador que ele não tem. */
    public static void sendOnOpen(ServerPlayer player, TvBlockEntity tv) {
        if (!TvImageConfig.enabled()) {
            return;
        }
        for (TvPicture picture : tv.layout().pictures()) {
            want(player, picture.hash());
        }
        for (String hash : TvLibrary.get(player.server).pictureHashes(player.getUUID())) {
            want(player, hash);
        }
    }

    private static void want(ServerPlayer player, String hash) {
        UUID id = player.getUUID();
        Set<String> known = KNOWN.get(id);
        if (known != null && known.contains(hash)) {
            return;
        }
        if (!TvArtSender.forced() && (player.connection == null
                || !player.connection.hasChannel(TvImageDataPayload.TYPE))) {
            return;
        }
        KNOWN.computeIfAbsent(id, unused -> new HashSet<>()).add(hash);
        WANTED.computeIfAbsent(id, unused -> new LinkedHashSet<>()).add(hash);
    }

    /** O jogador tem a imagem (ele mesmo a enviou): não vai de volta a ele. */
    public static void markKnown(ServerPlayer player, String hash) {
        KNOWN.computeIfAbsent(player.getUUID(), unused -> new HashSet<>()).add(hash);
        Set<String> wanted = WANTED.get(player.getUUID());
        if (wanted != null) {
            wanted.remove(hash);
        }
    }

    /** A lista do cache do cliente, uma vez por conexão: só os resumos que o armazém tem contam. */
    public static void onHave(@Nullable ServerPlayer player, TvImageHavePayload payload) {
        if (player == null || !HAVE.add(player.getUUID())) {
            return;
        }
        TvImageStore store = TvImageStore.get(player.server);
        Set<String> known = KNOWN.computeIfAbsent(player.getUUID(), unused -> new HashSet<>());
        List<String> hashes = payload.hashes();
        for (int i = 0; i < hashes.size() && i < TvImageHavePayload.MAX; i++) {
            String hash = hashes.get(i);
            if (store.contains(hash)) {
                known.add(hash);
            }
        }
    }

    /**
     * A cada tick do servidor, só com pedido: as imagens cujos bytes já estão na memória entram na fila em pedaços; as
     * que o armazém não tem mais saem do pedido (e do anotado, para irem se voltarem).
     */
    static void tick(MinecraftServer server) {
        if (WANTED.isEmpty()) {
            return;
        }
        TvImageStore store = TvImageStore.get(server);
        Iterator<Map.Entry<UUID, Set<String>>> players = WANTED.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, Set<String>> entry = players.next();
            if (server.getPlayerList().getPlayer(entry.getKey()) == null) {
                players.remove();
                continue;
            }
            Iterator<String> it = entry.getValue().iterator();
            while (it.hasNext()) {
                String hash = it.next();
                TvImageIndex.Entry stored = store.entry(hash);
                if (stored == null || store.unreadable(hash)) {
                    it.remove();
                    Set<String> known = KNOWN.get(entry.getKey());
                    if (known != null) {
                        known.remove(hash);
                    }
                    continue;
                }
                byte[] data = store.loaded(hash);
                if (data != null) {
                    it.remove();
                    queue(entry.getKey(), hash, Optional.ofNullable(stored.author), data);
                }
            }
            if (entry.getValue().isEmpty()) {
                players.remove();
            }
        }
    }

    private static void queue(UUID player, String hash, Optional<UUID> author, byte[] data) {
        for (int offset = 0; offset < data.length; offset += TvImageDataPayload.MAX_CHUNK) {
            TvArtSender.enqueue(player, new Chunk(hash, author, data, offset,
                    Math.min(TvImageDataPayload.MAX_CHUNK, data.length - offset)));
        }
    }

    /** Solta o jogador que saiu (a conexão seguinte começa sem nada anotado). */
    public static void forget(UUID player) {
        KNOWN.remove(player);
        WANTED.remove(player);
        HAVE.remove(player);
    }

    /** Parada do servidor. */
    static void reset() {
        KNOWN.clear();
        WANTED.clear();
        HAVE.clear();
        chunksSent = 0;
    }

    /** Se o jogador está anotado como quem tem a imagem. */
    public static boolean knownForTests(UUID player, String hash) {
        Set<String> known = KNOWN.get(player);
        return known != null && known.contains(hash);
    }

    /** Quantos pedaços de imagem saíram desde a partida. */
    public static long chunksSentForTests() {
        return chunksSent;
    }

    /** A volta de uma Tela com os jogadores dados, sem conexão (os pedidos entram; o tick os põe na fila). */
    public static void visitForTests(TvBlockEntity tv, List<ServerPlayer> near) {
        TvArtSender.runForced(() -> visit(tv, near));
    }

    /** O tick da entrega e um tick da fila, sem conexão. */
    public static void tickForTests(MinecraftServer server) {
        TvArtSender.runForced(() -> {
            tick(server);
            TvArtSender.tick(server);
        });
    }
}
