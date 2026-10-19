package io.github.matheusanbs.wirelessautomate.storage;

import java.util.ArrayList;

/**
 * O aviso aos vizinhos dos armazenamentos do mod, um por tick. Cada mudança real do conteúdo marca o
 * chunk na hora ({@code level.blockEntityChanged}) e entra aqui uma vez só (a marca
 * {@link StorageBlockEntity#neighborsQueued} evita a duplicata); no fim do tick do servidor,
 * {@link #tick()} faz o {@code updateNeighbourForOutputSignal} de cada um: o comparador relê o sinal e o
 * roteador preso acorda ({@code RouterBlock.onNeighborChange}). Com 250 origens tirando do mesmo Baú no
 * mesmo tick, são um aviso aos 6 vizinhos em vez de 250.
 *
 * <p>O comparador vê a mudança no mesmo tick (ela acontece antes do fim do tick) ou no seguinte (a que
 * vem depois do {@link #tick()}, como a de outro mod no fim do tick). Só a thread do servidor mexe na
 * fila. Parado custa zero: sem mudança, a fila está vazia.
 */
public final class StorageNeighbors {
    private static ArrayList<StorageBlockEntity> pending = new ArrayList<>();
    /** A fila sendo avisada: o que entra durante o aviso (um vizinho que mexe no Baú) fica para o tick seguinte. */
    private static ArrayList<StorageBlockEntity> flushing = new ArrayList<>();

    static void enqueue(StorageBlockEntity storage) {
        pending.add(storage);
    }

    /** Fim do tick do servidor: avisa os vizinhos de cada armazenamento que mudou. */
    public static void tick() {
        if (pending.isEmpty()) {
            return;
        }
        ArrayList<StorageBlockEntity> batch = pending;
        pending = flushing;
        flushing = batch;
        for (int i = 0, n = batch.size(); i < n; i++) {
            batch.get(i).flushNeighbors();
        }
        // Sem trimToSize por tick: com carga constante, encolher e crescer de novo alocaria a cada tick. O
        // tamanho é limitado pelos armazenamentos carregados e volta ao mínimo na parada.
        batch.clear();
    }

    /** Quantas vezes o armazenamento está na fila (0 ou 1), para os testes. */
    public static int queued(StorageBlockEntity storage) {
        int count = 0;
        for (StorageBlockEntity queued : pending) {
            if (queued == storage) {
                count++;
            }
        }
        return count;
    }

    /** Parada do servidor: o próximo mundo começa sem fila. */
    public static void reset() {
        for (StorageBlockEntity storage : pending) {
            storage.neighborsQueued = false;
        }
        pending.clear();
        pending.trimToSize();
        flushing.clear();
        flushing.trimToSize();
    }

    private StorageNeighbors() {
    }
}
