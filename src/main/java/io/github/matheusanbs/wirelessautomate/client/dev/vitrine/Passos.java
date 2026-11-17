package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Action;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Check;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import java.util.function.DoubleConsumer;

/** Os passos pequenos que as cenas da vitrine repetem: agir, esperar uma condição, esperar um tempo e animar. */
final class Passos {
    private static final long LIMITE_MS = 60_000;

    private Passos() {
    }

    /** Roda {@code acao} e termina no mesmo tick. */
    static Step agir(String nome, Action acao) {
        return new Step(nome, LIMITE_MS, acao, () -> true, () -> nome);
    }

    /** Roda {@code acao} e espera {@code pronto} valer. */
    static Step ate(String nome, Action acao, Check pronto) {
        return new Step(nome, LIMITE_MS, acao, pronto, () -> nome);
    }

    /** Só espera a condição. */
    static Step ate(String nome, Check pronto) {
        return ate(nome, () -> {
        }, pronto);
    }

    /** Espera {@code ms} de relógio de parede. */
    static Step esperar(long ms) {
        long[] inicio = new long[1];
        return new Step("esperar " + ms + " ms", LIMITE_MS + ms, () -> inicio[0] = System.nanoTime(),
                () -> (System.nanoTime() - inicio[0]) / 1_000_000 >= ms, () -> ms + " ms");
    }

    /** Chama {@code f(t)} com {@code t} de 0 a 1 a cada tick durante {@code ms}; o último tick chama com 1. */
    static Step durante(String nome, long ms, DoubleConsumer f) {
        long[] inicio = new long[1];
        return new Step(nome, LIMITE_MS + ms, () -> inicio[0] = System.nanoTime(), () -> {
            double t = Math.min(1, (System.nanoTime() - inicio[0]) / 1e6 / ms);
            f.accept(t);
            return t >= 1;
        }, () -> nome);
    }

    /** Suaviza 0..1 (começa e termina devagar). */
    static double suave(double t) {
        return t * t * (3 - 2 * t);
    }
}
