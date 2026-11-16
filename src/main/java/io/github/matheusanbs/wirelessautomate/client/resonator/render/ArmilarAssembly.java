package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/** A montagem da Armilar, da cena do filme ({@code scene-armilar.js}: {@code a = seg(t, 3,3; 5,8)}). Lógica pura. */
final class ArmilarAssembly {
    /** A duração da montagem, em s: 2,5, como no filme (5,8 − 3,3). */
    static final double FORM_SECONDS = 2.5;

    private ArmilarAssembly() {
    }

    /** O quanto a montagem andou: 1 se já estava formada, senão {@code sinceFormed / FORM_SECONDS} de 0 a 1. */
    static double a(double sinceFormed, boolean alreadyFormed) {
        return alreadyFormed ? 1 : Ease.cl(sinceFormed / FORM_SECONDS);
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
