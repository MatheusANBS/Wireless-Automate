package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.network.FlowSampler;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TvValuesPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * O canal de dados das Telas de Sinal: a cada tick visita só as Telas do balde da vez (20 baldes pela posição, uma
 * volta por segundo, como o amostrador dos Fluxos). Uma Tela sem ligação ou sem jogador perto não lê nada e não manda
 * nada; com alguém perto, lê o relatório dos Fluxos só quando ele ou o layout mudaram e manda os números a quem ainda
 * não os tem, só se mudaram. Sem Tela carregada o tick é uma checagem de conjunto vazio.
 */
public final class TvManager {
    private static final int PHASES = 20;
    private static final int TYPE_COUNT = ResourceType.values().length;
    /** Os valores de uma leitura antes de saber se mudaram; o teto da config de ligações é 256. */
    private static final double[] SCRATCH = new double[256];
    /** Os bits de dado antigo de uma leitura, uma palavra por 64 ligações. */
    private static final long[] STALE_SCRATCH = new long[SCRATCH.length / 64];
    private static final List<ServerPlayer> NEAR = new ArrayList<>();
    /** Rede → o {@link FlowStats} de cada tipo, pela ordem do {@code ordinal}; refeito uma vez por volta do amostrador. */
    private static final Map<UUID, FlowStats[]> STATS = new HashMap<>();

    private static @Nullable FlowSampler.Report mapReport;
    private static @Nullable FlowSampler.Report forced;
    private static int mapBuilds;
    private static long packets;
    private static int loaded;
    private static int watched;
    private static int cycleWatched;
    private static double averageNanos;
    private static long peakNanos;

    private TvManager() {
    }

    /** O custo para o {@code /wa profile}: as Telas carregadas, as com alguém perto na última volta e os pacotes. */
    public record Cost(int loaded, int watched, long packets, double averageNanos, long peakNanos) {
    }

    public static Cost cost() {
        return new Cost(loaded, watched, packets, averageNanos, peakNanos);
    }

    /** Chamado a cada tick do servidor. */
    public static void tick(MinecraftServer server) {
        loaded = TvLoaded.serverCount();
        if (loaded == 0) {
            TvPreview.dropIdle();
            TvStorageFeed.releaseAll();
            return;
        }
        long start = System.nanoTime();
        TvPreview.tick(server);
        int phase = Math.floorMod(server.getTickCount(), PHASES);
        double maxSq = (double) Config.tvViewDistance() * Config.tvViewDistance();
        List<TvBlockEntity> bucket = TvLoaded.serverBucket(phase);
        for (int s = 0; s < bucket.size(); s++) {
            TvBlockEntity tv = bucket.get(s);
            if (!(tv.getLevel() instanceof ServerLevel level)) {
                continue;
            }
            List<ServerPlayer> players = level.players();
            NEAR.clear();
            BlockPos pos = tv.getBlockPos();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer player = players.get(i);
                if (isNear(pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()), maxSq,
                        player.getChunkTrackingView().contains(chunkX, chunkZ))) {
                    NEAR.add(player);
                }
            }
            visit(tv, server, NEAR);
        }
        NEAR.clear();
        if (phase == PHASES - 1) {
            TvStorageFeed.sweep(server.getTickCount());
            watched = cycleWatched;
            cycleWatched = 0;
        }
        long spent = System.nanoTime() - start;
        averageNanos += (spent - averageNanos) / PHASES;
        peakNanos = Math.max(peakNanos, spent);
    }

    /** Uma Tela na sua vez: lê se preciso e manda a quem está perto e não tem os números. */
    private static void visit(TvBlockEntity tv, @Nullable MinecraftServer server, List<ServerPlayer> near) {
        TvLayout layout = tv.layout();
        TvFeed feed = tv.feed();
        if (layout.bindings().isEmpty() || near.isEmpty() || server == null) {
            if (feed != null) {
                feed.clear();
            }
            return;
        }
        cycleWatched++;
        feed = tv.feedOrCreate();
        FlowSampler.Report report = forced != null ? forced : NetworkManager.get().flows().report();
        if (report.sampledAt() != feed.readSample || tv.revision() != feed.readRevision) {
            read(tv, server, feed, layout, report);
        }
        for (int i = 0; i < near.size(); i++) {
            ServerPlayer player = near.get(i);
            if (feed.seen.getInt(player.getUUID()) != feed.valuesVersion) {
                TvValuesPayload payload = new TvValuesPayload(tv.getBlockPos(), feed.readRevision, feed.values,
                        feed.stale);
                if (forced == null) {
                    // Sem o canal negociado (ou sem conexão) não recebe e não entra no seen: recebe quando passar a ter.
                    if (player.connection == null || !player.connection.hasChannel(payload)) {
                        continue;
                    }
                    PacketDistributor.sendToPlayer(player, payload);
                }
                feed.seen.put(player.getUUID(), feed.valuesVersion);
                feed.packets++;
                packets++;
            }
        }
        if (feed.seen.size() > near.size()) {
            feed.seen.keySet().removeIf(id -> !contains(near, id));
        }
    }

    /** Perto: dentro da distância de visão e com o chunk da Tela no cliente (senão o pacote chegaria sem a Tela). */
    public static boolean isNear(double distanceSq, double maxSq, boolean chunkTracked) {
        return chunkTracked && distanceSq <= maxSq;
    }

    private static boolean contains(List<ServerPlayer> near, UUID id) {
        for (int i = 0; i < near.size(); i++) {
            if (near.get(i).getUUID().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Lê o valor de cada ligação e sobe a versão se algum mudou, ou se o layout é outro. */
    private static void read(TvBlockEntity tv, MinecraftServer server, TvFeed feed, TvLayout layout,
            FlowSampler.Report report) {
        ensureStats(report);
        NetworkSavedData data = NetworkSavedData.get(server);
        List<TvBinding> bindings = layout.bindings();
        int count = Math.min(bindings.size(), SCRATCH.length);
        int words = (count + 63) >> 6;
        Arrays.fill(STALE_SCRATCH, 0, words, 0L);
        boolean anyStale = false;
        for (int i = 0; i < count; i++) {
            TvBinding binding = bindings.get(i);
            SCRATCH[i] = number(binding, data, tv.owner(), server, tv, feed);
            if (binding.storage() != null && TvStorageFeed.wasStale()) {
                STALE_SCRATCH[i >> 6] |= 1L << (i & 63);
                anyStale = true;
            }
        }
        boolean changed = tv.revision() != feed.readRevision || feed.values.length != count;
        for (int i = 0; !changed && i < count; i++) {
            changed = Double.compare(SCRATCH[i], feed.values[i]) != 0;
        }
        for (int w = 0; !changed && w < words; w++) {
            changed = (w < feed.stale.length ? feed.stale[w] : 0L) != STALE_SCRATCH[w];
        }
        if (changed) {
            feed.values = Arrays.copyOf(SCRATCH, count);
            feed.stale = anyStale ? Arrays.copyOf(STALE_SCRATCH, words) : TvFeed.NO_STALE;
            feed.valuesVersion++;
        }
        feed.readSample = report.sampledAt();
        feed.readRevision = tv.revision();
    }

    /** O número de uma ligação no relatório, com o acesso de {@code who}; a prévia do editor lê por aqui. */
    static double read(TvBinding binding, FlowSampler.Report report, NetworkSavedData data, UUID who,
            MinecraftServer server, @Nullable TvBlockEntity tv) {
        ensureStats(report);
        if (binding.storage() != null && tv == null) {
            return Double.NaN;
        }
        return number(binding, data, who, server, tv, tv == null ? null : tv.feedOrCreate());
    }

    /** O número de uma ligação por fonte: a rede pelo relatório, o armazenamento ligado à Tela, o relógio no cliente. */
    private static double number(TvBinding binding, NetworkSavedData data, @Nullable UUID who, MinecraftServer server,
            @Nullable TvBlockEntity tv, @Nullable TvFeed feed) {
        return switch (binding.source()) {
            case NETWORK -> canRead(data, who, binding.network()) ? networkNumber(binding, server) : Double.NaN;
            case STORAGE -> TvStorageFeed.read(server, tv, feed, binding);
            case CLOCK -> Double.NaN;
        };
    }

    /** A métrica de rede: dos fluxos, ou o guardado nos Armazéns (que este pedido liga no amostrador). */
    private static double networkNumber(TvBinding binding, MinecraftServer server) {
        TvMetric metric = binding.metric();
        if (!metric.held()) {
            return metric.read(statsOf(binding));
        }
        FlowSampler sampler = NetworkManager.get().flows();
        sampler.requestHeld(binding.network(), binding.type(), server.getTickCount());
        return metric.readHeld(sampler.held(binding.network(), binding.type()));
    }

    /** A rede existe e o dono da Tela pode usá-la; sem dono, não. */
    private static boolean canRead(NetworkSavedData data, @Nullable UUID owner, UUID network) {
        if (owner == null) {
            return false;
        }
        WaNetwork found = data.network(network);
        return found != null && found.canUse(owner, false);
    }

    private static @Nullable FlowStats statsOf(TvBinding binding) {
        FlowStats[] byType = STATS.get(binding.network());
        return byType == null ? null : byType[binding.type().ordinal()];
    }

    /** O mapa rede × tipo, refeito só quando o relatório é outro. */
    private static void ensureStats(FlowSampler.Report report) {
        if (report == mapReport) {
            return;
        }
        STATS.clear();
        for (FlowStats stats : report.flows()) {
            STATS.computeIfAbsent(stats.network(), id -> new FlowStats[TYPE_COUNT])[stats.type().ordinal()] = stats;
        }
        mapReport = report;
        mapBuilds++;
    }

    /** Parada do servidor: o próximo mundo começa sem estado. */
    public static void reset() {
        TvPreview.reset();
        TvStorageFeed.releaseAll();
        NEAR.clear();
        STATS.clear();
        mapReport = null;
        forced = null;
        mapBuilds = 0;
        packets = 0;
        loaded = 0;
        watched = 0;
        cycleWatched = 0;
        averageNanos = 0;
        peakNanos = 0;
    }

    /** O mesmo caminho da visita, com o relatório e os jogadores perto dados. */
    public static void sampleForTests(TvBlockEntity tv, FlowSampler.Report report, List<ServerPlayer> near) {
        forced = report;
        try {
            visit(tv, near.isEmpty() ? null : near.get(0).getServer(), near);
        } finally {
            forced = null;
        }
    }

    /** Quantos armazenamentos estão vigiados por alguma Tela. */
    public static int watchedStoragesForTests() {
        return TvStorageFeed.watchedCount();
    }

    /** Roda o varrer das vigias vencidas no tick dado (a volta do servidor roda uma vez por segundo). */
    public static void sweepForTests(long now) {
        TvStorageFeed.sweep(now);
    }

    public static long packetsForTests() {
        return packets;
    }

    public static int mapBuildsForTests() {
        return mapBuilds;
    }
}
