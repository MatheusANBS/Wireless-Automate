package io.github.matheusanbs.wirelessautomate.storage;

import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * Conteúdo de um valor só (a energia da Bateria, a Source do Tanque de Source), em {@code long}, até a capacidade do tier (lida a cada entrada;
 * {@code <= 0} é sem limite). Cada mudança real chama {@code onChange} e sobe a {@link #version()}.
 */
public final class ScalarStore implements BulkEnergy, BulkSource {
    private final Runnable onChange;
    private final LongSupplier capacity;
    private long stored;
    private int version;
    /** Os contadores de entra e sai, só enquanto uma Tela de Sinal vigia; nulo no resto do tempo. */
    private @Nullable StorageWatch watch;

    public ScalarStore(Runnable onChange, LongSupplier capacity) {
        this.onChange = onChange;
        this.capacity = capacity;
    }

    public long stored() {
        return stored;
    }

    public long capacity() {
        return capacity.getAsLong();
    }

    public int version() {
        return version;
    }

    /** Os contadores de entra e sai, ou {@code null} sem vigia. */
    public @Nullable StorageWatch watch() {
        return watch;
    }

    /** Liga a vigia (a mesma, se já ligada). */
    public StorageWatch startWatch() {
        if (watch == null) {
            watch = new StorageWatch();
        }
        return watch;
    }

    public void stopWatch() {
        watch = null;
    }

    public boolean isEmpty() {
        return stored <= 0;
    }

    /** Guarda até {@code amount}; devolve quanto coube. */
    @Override
    public long insert(long amount, boolean simulate) {
        long accepted = StorageMath.accept(stored, capacity.getAsLong(), amount);
        if (accepted > 0 && !simulate) {
            stored = StorageMath.add(stored, accepted);
            if (watch != null) {
                watch.inserted(null, accepted);
            }
            changed();
        }
        return accepted;
    }

    /** Tira até {@code amount}; devolve quanto saiu. */
    @Override
    public long extract(long amount, boolean simulate) {
        long taken = Math.max(0, Math.min(amount, stored));
        if (taken > 0 && !simulate) {
            stored -= taken;
            if (watch != null) {
                watch.extracted(null, taken);
            }
            changed();
        }
        return taken;
    }

    /** Troca o valor sem avisar (para carregar ou mover o conteúdo). */
    public void set(long value) {
        stored = Math.max(0, value);
        version++;
    }

    /** Troca o valor (limitado a zero e à capacidade) e avisa, como uma mudança real. Para o {@code setSource} do Ars. */
    public void replace(long value) {
        long limit = capacity.getAsLong() <= 0 ? Long.MAX_VALUE : capacity.getAsLong();
        long next = Math.max(0, Math.min(value, limit));
        if (next != stored) {
            if (watch != null) {
                if (next > stored) {
                    watch.inserted(null, next - stored);
                } else {
                    watch.extracted(null, stored - next);
                }
            }
            stored = next;
            changed();
        }
    }

    private void changed() {
        version++;
        onChange.run();
    }
}
