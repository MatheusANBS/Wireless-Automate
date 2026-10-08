package io.github.matheusanbs.wirelessautomate.storage;

import java.util.function.LongSupplier;

/**
 * Conteúdo de um valor só (a energia da Bateria, a Source do Tanque de Source), em {@code long}, até a capacidade do tier (lida a cada entrada;
 * {@code <= 0} é sem limite). Cada mudança real chama {@code onChange} e sobe a {@link #version()}.
 */
public final class ScalarStore implements BulkEnergy, BulkSource {
    private final Runnable onChange;
    private final LongSupplier capacity;
    private long stored;
    private int version;

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

    public boolean isEmpty() {
        return stored <= 0;
    }

    /** Guarda até {@code amount}; devolve quanto coube. */
    @Override
    public long insert(long amount, boolean simulate) {
        long accepted = StorageMath.accept(stored, capacity.getAsLong(), amount);
        if (accepted > 0 && !simulate) {
            stored = StorageMath.add(stored, accepted);
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
            stored = next;
            changed();
        }
    }

    private void changed() {
        version++;
        onChange.run();
    }
}
