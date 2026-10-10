package io.github.matheusanbs.wirelessautomate.client;

import org.jetbrains.annotations.Nullable;

/**
 * Geometria da roda do Configurador (lógica pura): um disco central, um anel de dentro e um anel de
 * fora, cada anel dividido em fatias iguais. Ângulos em radianos, 0 no topo e crescendo no sentido
 * horário (com y para baixo, como na tela); a fatia 0 fica centrada no topo. Entre o disco central e
 * os anéis, e entre as fatias, há um vão de largura constante ({@link #gap()}).
 *
 * @param innerRadius começo do anel de dentro (antes dele, o disco central, não há escolha)
 * @param ringSplit   limite entre os dois anéis (o meio do vão entre eles)
 * @param outerRadius raio externo do anel de fora, o desenhado
 * @param outerLimit  até onde o mouse ainda conta como anel de fora (um pouco além do desenho)
 */
public record WheelLayout(double innerRadius, double ringSplit, double outerRadius, double outerLimit) {
    public enum Ring { INNER, OUTER }

    /** A fatia {@code index} do anel {@code ring}. */
    public record Hit(Ring ring, int index) {
    }

    /**
     * Proporções do raio externo: o disco central até 0,40 (grande, para o nome e a descrição caberem
     * em texto de escala 1), o vão entre as peças, o anel de dentro de 0,43 a 0,70 e o de fora de 0,73
     * a 1; o mouse ainda conta até 1,06.
     */
    private static final double GAP = 0.03;
    private static final double INNER_START = 0.43;
    private static final double INNER_END = 0.70;
    private static final double OUTER_START = 0.73;
    private static final double LIMIT = 1.06;

    /** A roda de raio externo {@code outer}. */
    public static WheelLayout forRadius(double outer) {
        return new WheelLayout(INNER_START * outer, (INNER_END + OUTER_START) / 2 * outer, outer, LIMIT * outer);
    }

    /** Largura do vão entre o disco central e os anéis, entre os anéis e entre as fatias. */
    public double gap() {
        return GAP * outerRadius;
    }

    /** Raio do disco central (o vão antes do anel de dentro). */
    public double centerRadius() {
        return innerRadius - gap();
    }

    /** Fim do anel de dentro (desenho). */
    public double innerRingEnd() {
        return INNER_END * outerRadius;
    }

    /** Começo do anel de fora (desenho). */
    public double outerRingStart() {
        return OUTER_START * outerRadius;
    }

    /** Raio de dentro do anel {@code ring}. */
    public double ringStart(Ring ring) {
        return ring == Ring.INNER ? innerRadius : outerRingStart();
    }

    /** Raio de fora do anel {@code ring}. */
    public double ringEnd(Ring ring) {
        return ring == Ring.INNER ? innerRingEnd() : outerRadius;
    }

    /** Raio do meio do anel: onde fica o centro do conteúdo (ícone e nome) de cada fatia. */
    public double ringMiddle(Ring ring) {
        return (ringStart(ring) + ringEnd(ring)) / 2;
    }

    /**
     * Comprimento do arco disponível para um nome que acompanha a curva no raio {@code radius}, numa
     * fatia de {@code count}: o arco da fatia menos o vão e uma margem de {@code inset} de cada lado.
     */
    public double labelArc(double radius, int count, double inset) {
        return Math.max(0, radius * 2 * Math.PI / count - gap() - 2 * inset);
    }

    /**
     * Se o conteúdo da fatia no ângulo {@code angle} fica na metade de baixo da roda: lá o nome que
     * acompanha a curva é virado (lido da esquerda para a direita, sem ficar de cabeça para baixo) e o
     * ícone, que fica sempre "acima" do nome na tela, vai para o lado do centro.
     */
    public static boolean flipped(double angle) {
        return Math.cos(angle) < -1e-9;
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
