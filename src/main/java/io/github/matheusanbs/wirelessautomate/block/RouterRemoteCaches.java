package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Os caches de capability das máquinas remotas de um {@link RouterBlockEntity}, por {@code pos.asLong()}:
 * criados sob demanda na leitura das portas e descartados (com os listeners) ao desvincular, quando o
 * upgrade de alcance deixa de valer e quando a máquina sai do raio ou do limite. Sem nenhuma remota, o mapa nem existe.
 */
final class RouterRemoteCaches {
    private final RouterBlockEntity router;
    private @Nullable Long2ObjectOpenHashMap<MachineCaches> caches;

    RouterRemoteCaches(RouterBlockEntity router) {
        this.router = router;
    }

    /** Ver {@link RouterBlockEntity#remoteCaches(BlockPos)}. */
    MachineCaches get(BlockPos pos) {
        if (caches == null) {
            caches = new Long2ObjectOpenHashMap<>();
        }
        long key = pos.asLong();
        MachineCaches found = caches.get(key);
        if (found == null) {
            found = MachineCaches.remote(router, pos);
            caches.put(key, found);
        }
        return found;
    }

    /** Não há cache de nenhuma remota. */
    boolean isEmpty() {
        return caches == null;
    }

    /**
     * Descarta os caches das remotas que não estão ativas em {@code reach} (fora de {@code remotes}, do raio ou do
     * limite), e com eles a vigia delas ({@code RemoteMachine.watched}); com {@code reach} nulo, de todas. Só com
     * {@link #isEmpty()} falso.
     */
    void prune(RangeRules.Reach @Nullable [] reach, RemoteMachines remotes) {
        LongOpenHashSet active = new LongOpenHashSet();
        for (int i = 0, n = remotes.size(); i < n; i++) {
            if (reach != null && i < reach.length && reach[i] == RangeRules.Reach.ACTIVE) {
                active.add(remotes.machine(i).pos().asLong());
            } else {
                // sem os caches, a vigia dela também caiu: a conferência a liga de novo quando ela voltar a ser ativa
                remotes.machine(i).setWatched(false);
            }
        }
        var iterator = caches.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!active.contains(entry.getLongKey())) {
                entry.getValue().discard();
                iterator.remove();
            }
        }
        if (caches.isEmpty()) {
            caches = null;
        }
    }
}
