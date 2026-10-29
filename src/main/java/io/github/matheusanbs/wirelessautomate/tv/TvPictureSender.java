package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvImageDataPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvImageHavePayload;
import java.util.ArrayDeque;
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
 * Tela, com as imagens dela e as da biblioteca do jogador ({@link #sendOnOpen}). O pedido entra em {@link #WANTED},
 * que guarda só o resumo; o tick tira dele uma imagem de cada vez para uma {@link Delivery}, lê os bytes do armazém
 * (no executor, uma vez) e, quando chegam, põe a imagem na fila em pedaços de até {@link TvImageDataPayload#MAX_CHUNK}.
 * <p>
 * <b>A memória presa pela entrega tem teto</b>, independente de quantas imagens foram pedidas: os pedaços na fila
 * seguram o arquivo inteiro até o último sair, então cada jogador tem no máximo {@link #PLAYER_BYTES} de imagens na
 * fila (ou uma só, se ela for maior) e todos juntos {@link #TOTAL_BYTES} (ou uma só); quem não cabe espera o tick
 * seguinte com o pedido só como resumo. Com {@code tv.images.enabled} desligado, nada vai. Sem pedido, o tick é a
 * checagem do mapa vazio. Só na thread do servidor.
 */
public final class TvPictureSender {
    private static final Map<UUID, Set<String>> KNOWN = new HashMap<>();
    private static final Map<UUID, Set<String>> WANTED = new HashMap<>();
    /** Quem já mandou a lista do cache nesta conexão (a segunda é ignorada). */
    private static final Set<UUID> HAVE = new HashSet<>();
    /** As imagens de cada jogador já tiradas do pedido: na fila ou esperando os bytes. */
    private static final Map<UUID, Deliveries> ACTIVE = new HashMap<>();
    /** Os bytes de imagem na fila de um jogador, no máximo: 4 ticks do orçamento dele. */
    static final long PLAYER_BYTES = 4L * TvArtSender.BUDGET_BYTES;
    /** Os bytes de imagem nas filas de todos os jogadores somados, no máximo. */
    static final long TOTAL_BYTES = 64L << 20;
    private static long playerBytes = PLAYER_BYTES;
    private static long totalBytes = TOTAL_BYTES;
    /** Os bytes presos agora pelas entregas de todos os jogadores. */
    private static long pinned;
    private static long chunksSent;

    private TvPictureSender() {
    }

    /** As entregas de um jogador, na ordem da fila, e os bytes delas somados. */
    private static final class Deliveries {
        final ArrayDeque<Delivery> active = new ArrayDeque<>();
        long bytes;
        /** A entrega que espera os bytes do armazém (no máximo uma por jogador). */
        @Nullable Delivery waiting;
    }

    /** Uma imagem tirada do pedido de um jogador: reservada no teto, depois com os bytes e os pedaços na fila. */
    private static final class Delivery {
        final UUID player;
        final Deliveries owner;
        final String hash;
        final Optional<UUID> author;
        long reserved;
        byte @Nullable [] data;
        boolean done;

        Delivery(UUID player, Deliveries owner, String hash, Optional<UUID> author, long reserved) {
            this.player = player;
            this.owner = owner;
            this.hash = hash;
            this.author = author;
            this.reserved = reserved;
        }

        /** O último pedaço saiu (ou a entrega foi largada): solta os bytes e a reserva, uma vez só. */
        void finish() {
            if (done) {
                return;
            }
            done = true;
            data = null;
            pinned -= reserved;
            owner.bytes -= reserved;
            owner.active.remove(this);
            if (owner.waiting == this) {
                owner.waiting = null;
            }
            if (owner.active.isEmpty() && ACTIVE.get(player) == owner) {
                ACTIVE.remove(player);
            }
        }
    }

    /** Um pedaço de uma imagem na fila de um jogador; o último fecha a entrega. */
    private record Chunk(Delivery delivery, int offset, int length, boolean last) implements TvArtSender.Item {
        @Override
        public int size() {
            if (delivery.done) {
                return -1;
            }
            if (!TvImageConfig.enabled()) {
                if (last) {
                    delivery.finish();
                }
                return -1;
            }
            return length + 64;
        }

        @Override
        public void send(ServerPlayer player) {
            chunksSent++;
            TvArtSender.count(length, player.getServer().getTickCount());
            byte[] data = delivery.data;
            if (data != null && !TvArtSender.forced() && player.connection != null
                    && player.connection.hasChannel(TvImageDataPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, new TvImageDataPayload(delivery.hash, delivery.author,
                        data.length, offset, Arrays.copyOfRange(data, offset, offset + length)));
            }
            if (last) {
                delivery.finish();
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
     * A cada tick do servidor, só com pedido: cada jogador tira do pedido as imagens que cabem nos tetos
     * ({@link #PLAYER_BYTES}, {@link #TOTAL_BYTES}), uma esperando os bytes por vez, e as que já têm os bytes entram na
     * fila em pedaços; as que o armazém não tem mais saem do pedido (e do anotado, para irem se voltarem).
     */
    static void tick(MinecraftServer server) {
        if (WANTED.isEmpty()) {
            return;
        }
        TvImageStore store = TvImageStore.get(server);
        Iterator<Map.Entry<UUID, Set<String>>> players = WANTED.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, Set<String>> entry = players.next();
            UUID id = entry.getKey();
            if (server.getPlayerList().getPlayer(id) == null) {
                players.remove();
                release(id);
                continue;
            }
            Set<String> wanted = entry.getValue();
            Deliveries mine = ACTIVE.get(id);
            while (mine == null || mine.waiting == null || fill(store, mine.waiting)) {
                String hash = next(store, id, wanted);
                if (hash == null) {
                    break;
                }
                TvImageIndex.Entry stored = store.entry(hash);
                long bytes = stored.bytes;
                long held = mine == null ? 0 : mine.bytes;
                if (held > 0 && held + bytes > playerBytes || pinned > 0 && pinned + bytes > totalBytes) {
                    break;
                }
                wanted.remove(hash);
                if (mine == null || mine.active.isEmpty()) {
                    mine = new Deliveries();
                    ACTIVE.put(id, mine);
                }
                Delivery delivery = new Delivery(id, mine, hash, Optional.ofNullable(stored.author), bytes);
                mine.active.add(delivery);
                mine.bytes += bytes;
                mine.waiting = delivery;
                pinned += bytes;
            }
            if (wanted.isEmpty() && (mine == null || mine.waiting == null)) {
                players.remove();
            }
        }
    }

    /** O primeiro resumo do pedido que o armazém ainda tem; os que ele não tem saem do pedido e do anotado. */
    private static @Nullable String next(TvImageStore store, UUID player, Set<String> wanted) {
        Iterator<String> it = wanted.iterator();
        while (it.hasNext()) {
            String hash = it.next();
            if (store.entry(hash) != null && !store.unreadable(hash)) {
                return hash;
            }
            it.remove();
            unknown(player, hash);
        }
        return null;
    }

    /**
     * Pede os bytes da entrega que espera; quando chegam, a imagem entra na fila em pedaços. Verdadeiro se a entrega
     * deixou de esperar (entrou na fila ou foi largada porque o armazém não tem mais a imagem).
     */
    private static boolean fill(TvImageStore store, Delivery delivery) {
        if (store.entry(delivery.hash) == null || store.unreadable(delivery.hash)) {
            delivery.finish();
            unknown(delivery.player, delivery.hash);
            return true;
        }
        byte[] data = store.loaded(delivery.hash);
        if (data == null) {
            return false;
        }
        delivery.owner.waiting = null;
        if (data.length == 0) {
            delivery.finish();
            return true;
        }
        // O arquivo lido manda no que fica preso, mesmo que o índice diga outro tamanho.
        long diff = data.length - delivery.reserved;
        delivery.reserved = data.length;
        delivery.owner.bytes += diff;
        pinned += diff;
        delivery.data = data;
        for (int offset = 0; offset < data.length; offset += TvImageDataPayload.MAX_CHUNK) {
            int length = Math.min(TvImageDataPayload.MAX_CHUNK, data.length - offset);
            TvArtSender.enqueue(delivery.player, new Chunk(delivery, offset, length, offset + length >= data.length));
        }
        return true;
    }

    /** A imagem não foi ao jogador: sai do anotado, para ir se voltar ao armazém. */
    private static void unknown(UUID player, String hash) {
        Set<String> known = KNOWN.get(player);
        if (known != null) {
            known.remove(hash);
        }
    }

    /** Larga as entregas do jogador (os pedaços que restarem na fila dele vencem sem ir). */
    private static void release(UUID player) {
        Deliveries mine = ACTIVE.remove(player);
        if (mine == null) {
            return;
        }
        Delivery delivery;
        while ((delivery = mine.active.peekFirst()) != null) {
            delivery.finish();
        }
    }

    /** Solta o jogador que saiu (a conexão seguinte começa sem nada anotado). */
    public static void forget(UUID player) {
        KNOWN.remove(player);
        WANTED.remove(player);
        HAVE.remove(player);
        release(player);
    }

    /** Parada do servidor. */
    static void reset() {
        KNOWN.clear();
        WANTED.clear();
        HAVE.clear();
        ACTIVE.clear();
        pinned = 0;
        playerBytes = PLAYER_BYTES;
        totalBytes = TOTAL_BYTES;
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

    /** Os bytes de imagem presos agora pelas entregas, somados. */
    public static long pinnedForTests() {
        return pinned;
    }

    /** Se o jogador ainda tem imagem pedida, esperando os bytes ou na fila. */
    public static boolean pendingForTests(UUID player) {
        return WANTED.containsKey(player) || ACTIVE.containsKey(player);
    }

    /** Baixa os tetos da entrega (só os testes; {@code -1} volta ao de verdade). */
    public static void setCapsForTests(long player, long total) {
        playerBytes = player < 0 ? PLAYER_BYTES : player;
        totalBytes = total < 0 ? TOTAL_BYTES : total;
    }

    /** A abertura do menu da Tela, sem conexão (os pedidos entram; o tick os põe na fila). */
    public static void openForTests(ServerPlayer player, TvBlockEntity tv) {
        TvArtSender.runForced(() -> sendOnOpen(player, tv));
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
