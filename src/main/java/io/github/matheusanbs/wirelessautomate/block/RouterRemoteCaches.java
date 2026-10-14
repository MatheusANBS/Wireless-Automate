package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Os caches de capability das máquinas remotas de um {@link RouterBlockEntity}, por {@code pos.asLong()}:
 * criados sob demanda na leitura das portas e descartados (com os listeners) ao desvincular ou quando o
 * upgrade de alcance deixa de valer. Sem nenhuma remota, o mapa nem existe.
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

    /** Descarta os caches das remotas fora de {@code remotes}; sem {@code keepAny}, de todas. Só com {@link #isEmpty()} falso. */
    void prune(boolean keepAny, RemoteMachines remotes) {
        var iterator = caches.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!keepAny || !remotes.contains(entry.getValue().pos())) {
                entry.getValue().discard();
                iterator.remove();
            }
        }
        if (caches.isEmpty()) {
            caches = null;
        }
    }
}
