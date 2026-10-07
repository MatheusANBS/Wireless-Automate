package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.jetbrains.annotations.Nullable;

/**
 * Contadores cumulativos do motor que o benchmark usa quando existem no {@link NetworkManager}:
 * {@code visitCount()}, {@code exhaustedTicks()}, {@code rebuildCount()} e {@code rebuildNanos()}
 * (patch descrito em docs/benchmark.md). Sem eles o benchmark roda igual e estima o que der pelo
 * tempo por tick; cada leitura devolve -1.
 */
final class EngineCounters {
    private static final @Nullable MethodHandle VISITS = find("visitCount");
    private static final @Nullable MethodHandle EXHAUSTED = find("exhaustedTicks");
    private static final @Nullable MethodHandle REBUILDS = find("rebuildCount");
    private static final @Nullable MethodHandle REBUILD_NANOS = find("rebuildNanos");

    private static @Nullable MethodHandle find(String name) {
        try {
            return MethodHandles.publicLookup().findVirtual(NetworkManager.class, name, MethodType.methodType(long.class));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    static boolean available() {
        return VISITS != null && EXHAUSTED != null && REBUILDS != null && REBUILD_NANOS != null;
    }

    static long visits() {
        return read(VISITS);
    }

    static long exhaustedTicks() {
        return read(EXHAUSTED);
    }

    static long rebuilds() {
        return read(REBUILDS);
    }

    static long rebuildNanos() {
        return read(REBUILD_NANOS);
    }

    private static long read(@Nullable MethodHandle handle) {
        if (handle == null) {
            return -1;
        }
        try {
            return (long) handle.invoke(NetworkManager.get());
        } catch (Throwable e) {
            return -1;
        }
    }

    private EngineCounters() {
    }
}
