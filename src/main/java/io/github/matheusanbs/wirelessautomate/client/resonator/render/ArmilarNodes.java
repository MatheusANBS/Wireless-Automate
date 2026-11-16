package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/** O mapa dos oito nós da Armilar (câmara i no nó i, de dentro para fora). Lógica pura. */
final class ArmilarNodes {
    private static final int[] FIRST = {0, 2, 5};

    private ArmilarNodes() {
    }

    /** A órbita do nó: a câmara i ocupa o nó i, de dentro para fora: 0 e 1; 2 a 4; 5 a 7. */
    static int orbit(int node) {
        return node < 2 ? 0 : node < 5 ? 1 : 2;
    }

    static int slot(int node) {
        return node - FIRST[orbit(node)];
    }

    /** O ângulo do nó na órbita: {@code k · 2π / nos}. */
    static double angle(int node) {
        return slot(node) * 2 * Math.PI / ArmilarSpec.NOS[orbit(node)];
    }

    /** O {@code j = i · 3 + k} do filme, que atrasa a entrada de cada cristal na montagem. */
    static int popIndex(int node) {
        return orbit(node) * 3 + slot(node);
    }

    /**
     * O centro do cristal do nó, em px locais, o {@code M.ap(m, [0,0,0])} do filme: {@code (r, 5, 0)} girado por
     * {@code spin + ângulo} em y, escalado pela órbita, inclinado por {@code tilt} em x, girado por {@code prec} em y e
     * somado ao sol.
     */
    static void center(int node, double ringScale, double prec, double tilt, double spin, double[] out) {
        double r = ArmilarSpec.RAIOS[orbit(node)];
        double a = spin + angle(node);
        double x = ringScale * (r * Math.cos(a));
        double y = ringScale * ArmilarSpec.CENTRO;
        double z = ringScale * (-r * Math.sin(a));
        double y2 = y * Math.cos(tilt) - z * Math.sin(tilt);
        double z2 = y * Math.sin(tilt) + z * Math.cos(tilt);
        out[0] = x * Math.cos(prec) + z2 * Math.sin(prec);
        out[1] = y2 + ArmilarSpec.SOL_Y;
        out[2] = -x * Math.sin(prec) + z2 * Math.cos(prec);
    }
}
