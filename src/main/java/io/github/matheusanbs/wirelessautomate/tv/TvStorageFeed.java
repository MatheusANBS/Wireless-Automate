package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.storage.StorageBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageMath;
import io.github.matheusanbs.wirelessautomate.storage.StorageWatch;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A leitura dos armazenamentos do mod ligados a uma Tela de Sinal, na volta dela (uma vez por segundo, só com alguém
 * perto): pelo block entity do mod, direto, sem capability nem cache, e só se o chunk está carregado
 * ({@code isLoaded}, nunca forçando a carga). Num chunk descarregado a ligação mostra o último número lido, marcado
 * como antigo ({@link #wasStale}); sem ele, ou com a ligação desfeita ou o bloco trocado, é "—" ({@code NaN}).
 *
 * <p>As taxas (entra, sai e saldo) precisam dos contadores do armazenamento, que só existem vigiados
 * ({@link StorageWatch}): ler uma taxa liga a vigia e renova um arrendamento de {@link #LEASE_TICKS}; o
 * {@link #sweep} (uma vez por segundo, só com vigias) desliga as que ninguém renovou, e sem Tela carregada
 * {@link #releaseAll} solta tudo. Uma Tela sem ninguém perto não lê, então a vigia some sozinha.
 */
final class TvStorageFeed {
    /** Dois segundos e meio: a volta de cada Tela é de 20 ticks, então quem ainda lê renova bem antes. */
    static final long LEASE_TICKS = 50;
    private static final Map<StorageBlockEntity, Long> LEASES = new IdentityHashMap<>();
    private static boolean stale;

    private TvStorageFeed() {
    }

    /** Se a última {@link #read} devolveu o último número lido, de um chunk descarregado. */
    static boolean wasStale() {
        return stale;
    }

    /** O número da ligação a armazenamento, com o acesso já conferido: ligada à Tela e o bloco do mod. */
    static double read(MinecraftServer server, TvBlockEntity tv, TvFeed feed, TvBinding binding) {
        stale = false;
        TvBinding.Storage storage = binding.storage();
        if (!tv.hasLink(storage.pos())) {
            forget(feed, binding);
            return Double.NaN;
        }
        ServerLevel level = server.getLevel(storage.pos().dimension());
        BlockPos pos = storage.pos().pos();
        if (level == null || !level.isLoaded(pos)) {
            return lastKnown(feed, binding);
        }
        if (!(level.getBlockEntity(pos) instanceof StorageBlockEntity found) || found.isRemoved()) {
            forget(feed, binding);
            return Double.NaN;
        }
        double value = metric(server, found, storage);
        if (feed.lastKnown == null) {
            feed.lastKnown = new Object2DoubleOpenHashMap<>();
            feed.lastKnown.defaultReturnValue(Double.NaN);
        }
        feed.lastKnown.put(binding, value);
        return value;
    }

    private static double lastKnown(TvFeed feed, TvBinding binding) {
        double last = feed.lastKnown == null ? Double.NaN : feed.lastKnown.getDouble(binding);
        stale = !Double.isNaN(last);
        return last;
    }

    private static void forget(TvFeed feed, TvBinding binding) {
        if (feed.lastKnown != null) {
            feed.lastKnown.removeDouble(binding);
        }
    }

    /** O item do Baú, só se a ligação o pede, o armazenamento é um Baú e o item existe. */
    private static @Nullable Item itemOf(StorageBlockEntity found, TvBinding.Storage storage) {
        if (storage.item().isEmpty() || !(found instanceof StorageChestBlockEntity)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(storage.item().get());
        return item == Items.AIR ? null : item;
    }

    private static double metric(MinecraftServer server, StorageBlockEntity found, TvBinding.Storage storage) {
        if (storage.item().isPresent() && found instanceof StorageChestBlockEntity && itemOf(found, storage) == null) {
            return Double.NaN;
        }
        Item item = itemOf(found, storage);
        long capacity = found.capacity();
        return switch (storage.metric()) {
            case STORED -> stored(found, item);
            case CAPACITY -> capacity;
            case FILL -> StorageMath.fraction(stored(found, item), capacity);
            case IN -> rates(server, found, item, 0);
            case OUT -> rates(server, found, item, 1);
            case BALANCE -> rates(server, found, item, 2);
        };
    }

    private static long stored(StorageBlockEntity found, @Nullable Item item) {
        return item != null ? ((StorageChestBlockEntity) found).keyed().countOfGroup(item) : found.total();
    }

    /** Liga a vigia, renova o arrendamento e lê a taxa: 0 entra, 1 sai, 2 o saldo. */
    private static double rates(MinecraftServer server, StorageBlockEntity found, @Nullable Item item, int which) {
        long now = server.getTickCount();
        StorageWatch watch = found.startWatch();
        LEASES.put(found, now);
        if (item != null) {
            watch.watchItem(item);
        }
        watch.sample(now);
        double in = watch.inPerSecond(item);
        double out = watch.outPerSecond(item);
        return which == 0 ? in : which == 1 ? out : in - out;
    }

    /** Uma vez por segundo: desliga a vigia dos armazenamentos que nenhuma Tela renovou. */
    static void sweep(long now) {
        if (LEASES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<StorageBlockEntity, Long>> it = LEASES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<StorageBlockEntity, Long> entry = it.next();
            if (now - entry.getValue() > LEASE_TICKS) {
                entry.getKey().stopWatch();
                it.remove();
            }
        }
    }

    /** Solta todas as vigias: sem Tela carregada ou na parada do servidor. */
    static void releaseAll() {
        if (LEASES.isEmpty()) {
            return;
        }
        for (StorageBlockEntity found : LEASES.keySet()) {
            found.stopWatch();
        }
        LEASES.clear();
    }

    /** Quantos armazenamentos estão vigiados (para os testes e o {@code /wa profile}). */
    static int watchedCount() {
        return LEASES.size();
    }
}
