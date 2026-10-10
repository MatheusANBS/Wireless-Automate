package io.github.matheusanbs.wirelessautomate.client;

import org.jetbrains.annotations.Nullable;

/**
 * Geometria da roda do Configurador (lógica pura): um disco central, um anel de dentro e um anel de
 * fora, cada anel dividido em fatias iguais. Ângulos em radianos, 0 no topo e crescendo no sentido
 * horário (com y para baixo, como na tela); a fatia 0 fica centrada no topo.
 *
 * @param innerRadius raio do disco central (dentro dele não há escolha)
 * @param ringSplit   limite entre os dois anéis (o meio do vão entre eles)
 * @param outerRadius raio externo do anel de fora, o desenhado
 * @param outerLimit  até onde o mouse ainda conta como anel de fora (um pouco além do desenho)
 */
public record WheelLayout(double innerRadius, double ringSplit, double outerRadius, double outerLimit) {
    public enum Ring { INNER, OUTER }

    /** A fatia {@code index} do anel {@code ring}. */
    public record Hit(Ring ring, int index) {
    }

    /** Proporções do mockup aprovado: centro 46, anel de dentro até 112, de fora de 118 a 182, aceita até 192. */
    private static final double REFERENCE = 182;

    /** Raio do centro, fim do anel de dentro e começo do de fora, para um raio externo {@code outer}. */
    public static WheelLayout forRadius(double outer) {
        double scale = outer / REFERENCE;
        return new WheelLayout(46 * scale, 115 * scale, outer, 192 * scale);
    }

    /** Fim do anel de dentro (desenho). */
    public double innerRingEnd() {
        return 112 * outerRadius / REFERENCE;
    }

    /** Começo do anel de fora (desenho). */
    public double outerRingStart() {
        return 118 * outerRadius / REFERENCE;
    }

    /**
     * A fatia sob o ponto ({@code dx}, {@code dy}) em relação ao centro, ou {@code null} no disco
     * central, fora da roda ou num anel sem fatias.
     */
    public @Nullable Hit hit(double dx, double dy, int innerCount, int outerCount) {
        double distance = Math.hypot(dx, dy);
        if (distance < innerRadius || distance > outerLimit) {
            return null;
        }
        Ring ring = distance <= ringSplit ? Ring.INNER : Ring.OUTER;
        int count = ring == Ring.INNER ? innerCount : outerCount;
        if (count <= 0) {
            return null;
        }
        return new Hit(ring, slice(angle(dx, dy), count));
    }

    /** Ângulo do ponto: 0 no topo ({@code dy < 0}), horário, em [0, 2π). */
    public static double angle(double dx, double dy) {
        double angle = Math.atan2(dx, -dy);
        return angle < 0 ? angle + 2 * Math.PI : angle;
    }

    /** A fatia de um ângulo (0 centrada no topo). */
    public static int slice(double angle, int count) {
        double step = 2 * Math.PI / count;
        int index = (int) Math.floor((angle + step / 2) / step);
        return Math.floorMod(index, count);
    }

    /** Começo da fatia {@code index} de {@code count} (pode ser negativo na fatia 0). */
    public static double sliceStart(int index, int count) {
        double step = 2 * Math.PI / count;
        return index * step - step / 2;
    }

    /** Fim da fatia {@code index} de {@code count}. */
    public static double sliceEnd(int index, int count) {
        return sliceStart(index, count) + 2 * Math.PI / count;
    }

    /** Meio da fatia (onde vão o ícone e o nome). */
    public static double sliceMiddle(int index, int count) {
        return index * 2 * Math.PI / count;
    }
}
