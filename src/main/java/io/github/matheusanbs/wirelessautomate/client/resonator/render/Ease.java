package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/** As curvas do filme ({@code partes.js}, linhas 7 a 11), portadas ao pé da letra. Lógica pura. */
final class Ease {
    private Ease() {
    }

    static double cl(double x) {
        return Math.max(0, Math.min(1, x));
    }

    static double seg(double t, double a, double b) {
        return cl((t - a) / (b - a));
    }

    static double lerp(double a, double b, double k) {
        return a + (b - a) * k;
    }

    static double eo(double x) {
        return 1 - Math.pow(1 - cl(x), 3);
    }

    static double eio(double x) {
        x = cl(x);
        return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }

    /** O {@code eob} do filme (sobressalto): 0 em 0, 1 em 1, passa de 1 no caminho. */
    static double eob(double x, double k) {
        x = cl(x);
        return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
    }

    static double ss(double a, double b, double x) {
        double k = cl((x - a) / (b - a));
        return k * k * (3 - 2 * k);
    }

    /** O ângulo reduzido a (−π, π], para mandar ao {@code float} sem perder precisão. */
    static float wrap(double radians) {
        double r = Math.IEEEremainder(radians, 2 * Math.PI);
        return (float) r;
    }
}
