package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/** A montagem da Armilar, da cena do filme ({@code scene-armilar.js}: {@code a = seg(t, 3,3; 5,8)}). Lógica pura. */
final class ArmilarAssembly {
    /** A duração da montagem, em s: 2,5, como no filme (5,8 − 3,3). */
    static final double FORM_SECONDS = 2.5;

    private ArmilarAssembly() {
    }

    /**
     * O fim da entrada do último cristal ({@code 0,85 + 0,05 · 8}). No filme o {@code a} para em 1 e os cristais de
     * {@code j} maior ficam murchos; no jogo o {@code a} corre até aqui para que todos terminem inteiros.
     */
    static final double A_MAX = 0.85 + 0.05 * 8;

    /**
     * O quanto a montagem andou: {@link #A_MAX} se já estava formada, senão {@code sinceFormed / FORM_SECONDS} de 0 a
     * {@code A_MAX} ({@code FORM_SECONDS} é o tempo até 1; a entrada inteira leva {@code A_MAX · FORM_SECONDS}).
     */
    static double a(double sinceFormed, boolean alreadyFormed) {
        return alreadyFormed ? A_MAX : Math.max(0, Math.min(sinceFormed / FORM_SECONDS, A_MAX));
    }

    static double ring(double a, int orbit) {
        return Ease.eob(Ease.seg(a, 0.15 + orbit * 0.18, 0.6 + orbit * 0.18), 1.4);
    }

    static double crystal(double a, int popIndex) {
        return Ease.eob(Ease.seg(a, 0.55 + popIndex * 0.05, 0.85 + popIndex * 0.05), 1.6);
    }

    static double ignition(double a) {
        return Ease.eob(Ease.seg(a, 0, 0.3), 1.5);
    }
}
