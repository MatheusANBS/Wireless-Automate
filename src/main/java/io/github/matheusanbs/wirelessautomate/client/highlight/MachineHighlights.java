package io.github.matheusanbs.wirelessautomate.client.highlight;

/**
 * As máquinas acesas no mundo pelo Olho, só na sessão do cliente: cada uma com a dimensão, a posição da máquina
 * e a do roteador (para a linha tracejada), em arrays fixos, da mais antiga para a mais nova. Uma máquina é a
 * dimensão e a posição dela: acesa por um roteador, o Olho de outro roteador que a vincula a apaga. Até
 * {@link #LIMIT}; acender mais uma apaga a mais antiga. Posições em {@code long} ({@code BlockPos.asLong}), a
 * dimensão como chave comparada por {@code equals}. Nada aloca depois de criada: o desenho percorre por índice
 * ({@link #count}, {@link #dimension}, {@link #machine}, {@link #router}). Lógica pura, com JUnit.
 */
public final class MachineHighlights {
    /** O máximo de máquinas acesas ao mesmo tempo. */
    public static final int LIMIT = 64;

    /** O que o {@link #toggle} fez. */
    public enum Result { LIT, OFF, LIT_OLDEST_OFF }

    private final Object[] dimensions = new Object[LIMIT];
    private final long[] machines = new long[LIMIT];
    private final long[] routers = new long[LIMIT];
    private int count;
    private int version;

    /**
     * Acende a máquina, ou a apaga se ela já está acesa. Com {@link #LIMIT} acesas, acender apaga a mais antiga
     * ({@link Result#LIT_OLDEST_OFF}).
     */
    public Result toggle(Object dimension, long machine, long router) {
        version++;
        int index = indexOf(dimension, machine);
        if (index >= 0) {
            remove(index);
            return Result.OFF;
        }
        boolean full = count == LIMIT;
        if (full) {
            remove(0);
        }
        dimensions[count] = dimension;
        machines[count] = machine;
        routers[count] = router;
        count++;
        return full ? Result.LIT_OLDEST_OFF : Result.LIT;
    }

    public boolean isLit(Object dimension, long machine) {
        return indexOf(dimension, machine) >= 0;
    }

    public int count() {
        return count;
    }

    /** Sobe a cada mudança: quem guarda um texto pela contagem compara a versão. */
    public int version() {
        return version;
    }

    public void clear() {
        if (count == 0) {
            return;
        }
        version++;
        for (int i = 0; i < count; i++) {
            dimensions[i] = null;
        }
        count = 0;
    }

    /** A dimensão da acesa {@code i} (de 0, a mais antiga, a {@link #count} − 1). */
    public Object dimension(int i) {
        return dimensions[i];
    }

    public long machine(int i) {
        return machines[i];
    }

    public long router(int i) {
        return routers[i];
    }

    private int indexOf(Object dimension, long machine) {
        for (int i = 0; i < count; i++) {
            if (machines[i] == machine && dimensions[i].equals(dimension)) {
                return i;
            }
        }
        return -1;
    }

    private void remove(int index) {
        int tail = count - index - 1;
        System.arraycopy(dimensions, index + 1, dimensions, index, tail);
        System.arraycopy(machines, index + 1, machines, index, tail);
        System.arraycopy(routers, index + 1, routers, index, tail);
        count--;
        dimensions[count] = null;
    }
}
