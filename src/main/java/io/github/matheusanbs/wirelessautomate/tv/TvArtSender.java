package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.packet.TvArtsPayload;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * O envio dos pixels das artes das Telas de Sinal, que o update tag leva só como referência ({@link TvArtRef}). Na
 * volta do {@link TvManager} (cada Tela uma vez por segundo), uma Tela com arte põe na fila de cada jogador perto (o
 * chunk dela já mandado a ele) que ainda não tem a chave atual das artes ({@link TvBlockEntity#artKey}): uma vez por
 * mudança delas por jogador, esquecido quando ele sai de perto. A fila de cada jogador sai com um orçamento de
 * {@link #BUDGET_BYTES} por tick ({@link TvSendQueue}), para uma área cheia de Telas não dar pico; o payload de uma
 * Tela é codificado uma vez e dividido entre todos. A abertura do menu da Tela manda na hora ({@link #sendOnOpen}),
 * antes do menu, para o editor abrir já com as artes. Um chunk com Tela mandado de novo a quem já tinha as artes
 * ({@link #onChunkSent}: saiu e voltou entre duas voltas, ou trocou de dimensão e voltou) faz elas irem de novo, porque
 * o cliente montou a Tela do zero e pode já ter soltado as artes.
 * <p>
 * Parado custa zero: uma Tela sem arte ou sem ninguém perto não guarda nada, e sem fila o tick é a checagem do mapa
 * vazio. Só na thread do servidor.
 */
public final class TvArtSender {
    /** Quantos bytes de artes cada jogador recebe por tick, no máximo (o primeiro payload do tick sempre vai). */
    public static final int BUDGET_BYTES = 256 * 1024;
    private static final Map<UUID, TvSendQueue<Pending>> QUEUES = new HashMap<>();
    /** Jogador → chunk → o tick em que o chunk foi mandado a ele; guardado {@link #CHUNK_TICKS} ticks. */
    private static final Map<UUID, Long2LongOpenHashMap> CHUNKS_SENT = new HashMap<>();
    /** Duas voltas do {@link TvManager}: toda Tela do chunk foi visitada pelo menos uma vez depois do envio. */
    private static final int CHUNK_TICKS = 40;

    /** Nos testes: sem conexão de verdade, conta o envio em vez de mandar e não confere o canal nem o chunk. */
    private static boolean forced;

    private TvArtSender() {
    }

    /** O que uma Tela com arte e alguém perto guarda: a chave que cada jogador recebeu e o payload da chave atual. */
    public static final class Feed {
        /** Jogador → a chave das artes que ele tem (0, o padrão, é nenhuma: a chave de uma Tela com arte nunca é 0). */
        final Object2LongOpenHashMap<UUID> sent = new Object2LongOpenHashMap<>();
        /** Jogador → o tick em que as artes entraram na fila dele. */
        final Object2LongOpenHashMap<UUID> sentAt = new Object2LongOpenHashMap<>();
        private @Nullable TvArtsPayload payload;
        private long payloadKey;
        long sends;

        /** Solta os bytes do payload: ninguém perto precisa dele agora (quem chegar o faz de novo). */
        void dropPayload() {
            payload = null;
        }

        /** O payload das artes da Tela, refeito só quando a chave muda (ou depois de solto). */
        TvArtsPayload payload(TvBlockEntity tv) {
            long key = tv.artKey();
            if (payload == null || payloadKey != key) {
                payload = new TvArtsPayload(tv.layout().images());
                payloadKey = key;
            }
            return payload;
        }
    }

    private record Pending(TvBlockEntity tv, long key) {
    }

    private static Feed feedOf(TvBlockEntity tv) {
        Feed feed = tv.artFeed();
        if (feed == null) {
            feed = new Feed();
            tv.setArtFeed(feed);
        }
        return feed;
    }

    /**
     * A Tela na volta dela ({@code now}, o tick do servidor): põe na fila de quem está perto e ainda não tem as artes da
     * chave atual, ou as tinha antes de o chunk ser mandado de novo a ele.
     */
    static void visit(TvBlockEntity tv, List<ServerPlayer> near, long now) {
        if (near.isEmpty() || tv.layout().images().isEmpty()) {
            if (tv.artFeed() != null) {
                tv.setArtFeed(null);
            }
            return;
        }
        long key = tv.artKey();
        Feed feed = feedOf(tv);
        BlockPos pos = tv.getBlockPos();
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        boolean queued = false;
        for (int i = 0; i < near.size(); i++) {
            ServerPlayer player = near.get(i);
            UUID id = player.getUUID();
            if (feed.sent.getLong(id) == key && chunkSentAt(id, chunk) <= feed.sentAt.getLong(id)) {
                continue;
            }
            // Sem o canal, não recebe e não entra no sent; com o chunk ainda na fila do envio, as artes chegariam
            // antes da Tela: espera a volta seguinte.
            if (!forced && (!canReceive(player) || player.connection.chunkSender.isPending(chunk))) {
                continue;
            }
            feed.sent.put(id, key);
            feed.sentAt.put(id, now);
            QUEUES.computeIfAbsent(id, unused -> new TvSendQueue<>()).add(new Pending(tv, key));
            queued = true;
        }
        if (!queued) {
            // Todos perto já têm as artes: os bytes codificados (até ~130 KB) não ficam presos na memória.
            feed.dropPayload();
        }
        if (feed.sent.size() > near.size()) {
            feed.sent.keySet().removeIf(id -> !TvManager.contains(near, id));
            feed.sentAt.keySet().removeIf(id -> !TvManager.contains(near, id));
        }
    }

    /** O tick em que o chunk foi mandado de novo ao jogador nos últimos {@link #CHUNK_TICKS} ticks, ou o menor long. */
    private static long chunkSentAt(UUID player, long chunk) {
        Long2LongOpenHashMap chunks = CHUNKS_SENT.get(player);
        return chunks == null ? Long.MIN_VALUE : chunks.getOrDefault(chunk, Long.MIN_VALUE);
    }

    /**
     * Um chunk foi mandado a um jogador: anota o tick, para a volta seguinte das Telas dele mandar as artes de novo. Sem
     * Tela carregada não anota nada.
     */
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        if (TvLoaded.serverCount() == 0) {
            return;
        }
        CHUNKS_SENT.computeIfAbsent(event.getPlayer().getUUID(), unused -> new Long2LongOpenHashMap())
                .put(event.getPos().toLong(), event.getLevel().getServer().getTickCount());
    }

    /** Uma vez por volta: esquece os envios de chunk mais velhos que {@link #CHUNK_TICKS} ticks. */
    static void sweep(long now) {
        if (CHUNKS_SENT.isEmpty()) {
            return;
        }
        Iterator<Long2LongOpenHashMap> it = CHUNKS_SENT.values().iterator();
        while (it.hasNext()) {
            Long2LongOpenHashMap chunks = it.next();
            LongIterator ticks = chunks.values().iterator();
            while (ticks.hasNext()) {
                if (now - ticks.nextLong() > CHUNK_TICKS) {
                    ticks.remove();
                }
            }
            if (chunks.isEmpty()) {
                it.remove();
            }
        }
    }

    private static boolean canReceive(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(TvArtsPayload.TYPE);
    }

    /** A cada tick do servidor: a fila de cada jogador sai dentro do orçamento. */
    static void tick(MinecraftServer server) {
        if (QUEUES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, TvSendQueue<Pending>>> it = QUEUES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TvSendQueue<Pending>> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || drain(player, entry.getValue())) {
                it.remove();
            }
        }
    }

    /** Manda o que cabe no orçamento do tick; verdadeiro se a fila esvaziou. */
    private static boolean drain(ServerPlayer player, TvSendQueue<Pending> queue) {
        queue.drain(BUDGET_BYTES, TvArtSender::size, pending -> send(player, pending.tv));
        return queue.isEmpty();
    }

    /** O tamanho do envio, ou -1 se venceu: a Tela saiu, as artes mudaram (vai outro) ou o payload passa do teto. */
    private static int size(Pending pending) {
        TvBlockEntity tv = pending.tv;
        if (tv.isRemoved() || tv.artKey() != pending.key) {
            return -1;
        }
        int size = feedOf(tv).payload(tv).encodedSize();
        if (size > TvArtsPayload.MAX_BYTES) {
            WirelessAutomate.LOGGER.warn("Tela de Sinal em {}: as artes passam de {} bytes e não foram enviadas",
                    tv.getBlockPos(), TvArtsPayload.MAX_BYTES);
            return -1;
        }
        return size;
    }

    private static void send(ServerPlayer player, TvBlockEntity tv) {
        Feed feed = feedOf(tv);
        TvArtsPayload payload = feed.payload(tv);
        feed.sends++;
        if (!forced) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * A abertura do menu da Tela: as artes vão na hora (antes do menu, então o editor já abre com elas), a não ser que
     * o jogador já as tenha; o que estava na fila dele para esta Tela sai da fila.
     */
    public static void sendOnOpen(ServerPlayer player, TvBlockEntity tv) {
        if (tv.layout().images().isEmpty() || !forced && !canReceive(player)) {
            return;
        }
        UUID id = player.getUUID();
        long key = tv.artKey();
        Feed feed = feedOf(tv);
        TvSendQueue<Pending> queue = QUEUES.get(id);
        boolean queued = queue != null && queue.removeIf(pending -> pending.tv == tv);
        if (!queued && feed.sent.getLong(id) == key) {
            return;
        }
        feed.sent.put(id, key);
        feed.sentAt.put(id, player.getServer().getTickCount());
        if (feed.payload(tv).encodedSize() <= TvArtsPayload.MAX_BYTES) {
            send(player, tv);
        }
    }

    /** Solta a fila do jogador que saiu. */
    public static void forget(UUID player) {
        QUEUES.remove(player);
        CHUNKS_SENT.remove(player);
    }

    /** Parada do servidor. */
    static void reset() {
        QUEUES.clear();
        CHUNKS_SENT.clear();
        forced = false;
    }

    /** O caminho da volta com os jogadores perto dados, sem conexão: põe na fila e manda o que cabe num tick. */
    public static void visitForTests(TvBlockEntity tv, List<ServerPlayer> near) {
        forced = true;
        try {
            visit(tv, near, near.isEmpty() ? 0 : near.get(0).getServer().getTickCount());
            for (ServerPlayer player : near) {
                TvSendQueue<Pending> queue = QUEUES.get(player.getUUID());
                if (queue != null && drain(player, queue)) {
                    QUEUES.remove(player.getUUID());
                }
            }
        } finally {
            forced = false;
        }
    }

    /** O caminho da abertura do menu, sem conexão. */
    public static void openForTests(ServerPlayer player, TvBlockEntity tv) {
        forced = true;
        try {
            sendOnOpen(player, tv);
        } finally {
            forced = false;
        }
    }

    /** Quantas vezes as artes desta Tela foram mandadas desde que alguém chegou perto (0 sem ninguém perto). */
    public static long sendsForTests(TvBlockEntity tv) {
        Feed feed = tv.artFeed();
        return feed == null ? 0 : feed.sends;
    }

    /** Se o jogador está anotado como quem já tem as artes da chave atual da Tela. */
    public static boolean sentToForTests(TvBlockEntity tv, UUID player) {
        Feed feed = tv.artFeed();
        return feed != null && feed.sent.getLong(player) == tv.artKey();
    }

    /** O payload que a Tela manda agora. */
    public static TvArtsPayload payloadForTests(TvBlockEntity tv) {
        return feedOf(tv).payload(tv);
    }
}
