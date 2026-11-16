package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/** A linha do tempo da conjunção, do {@code fase(t, u)} do filme ({@code partes.js}). Lógica pura, uma por cena. */
final class ArmilarPhase {
    double d, dis, prep, cresce, raio, clarao, onda, cabeca, arco, porta, surge, onda2, clarao2;

    /**
     * A linha do tempo da conjunção em {@code t} (segundos do mundo). {@code alive} é 0 sem conjunção (fora de
     * sintonia, a spec manda não haver) e a sintonia suavizada com ela (o {@code u} do filme).
     */
    void set(double t, double alive) {
        d = ArmilarSpec.tau(t);
        dis = Ease.ss(1.6, 2.4, d);
        double vivo = alive * (d > -0.6 && d < 2.4 ? 1 : 0);
        prep = vivo * Ease.ss(-0.6, 0, d) * (1 - Ease.ss(0, 0.2, d));
        cresce = Ease.ss(-0.05, 0.2, d);
        raio = vivo * Ease.ss(-0.05, 0.05, d) * (1 - dis);
        clarao = vivo * Ease.ss(0.1, 0.18, d) * (1 - Ease.ss(0.25, 0.5, d));
        onda = Ease.ss(0.15, 0.7, d);
        cabeca = Ease.ss(0.1, 0.6, d);
        arco = vivo * Ease.ss(0.08, 0.15, d) * (1 - dis);
        porta = vivo * Ease.ss(0.55, 0.65, d) * (1 - dis);
        surge = Ease.ss(0.55, 0.85, d);
        onda2 = Ease.ss(0.6, 1.2, d);
        clarao2 = vivo * Ease.ss(0.55, 0.62, d) * (1 - Ease.ss(0.75, 1.0, d));
    }

    /**
     * O buffer cheio: o sol sob pressão, o encolher e escurecer da preparação parado em 60% e pulsando a 0,5 Hz. A spec
     * não diz a amplitude: 0,1 (de 50% a 70%) é a proposta, num lugar só.
     */
    static double fullPrep(double t) {
        return 0.6 + 0.1 * Math.sin(2 * Math.PI * 0.5 * t);
    }
}
