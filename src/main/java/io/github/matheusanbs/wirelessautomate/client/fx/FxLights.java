package io.github.matheusanbs.wirelessautomate.client.fx;

/**
 * As luzes do quadro: quem desenha efeito anota aqui (no mundo, em blocos) e a {@link FxLightPass} acende o que está
 * em volta. Arrays estáticos reaproveitados, nada alocado por quadro; só a thread de render os usa. Cabem
 * {@link #CAPACITY} luzes anotadas, e o shader recebe as {@link #SHADER_MAX} mais próximas da câmera.
 */
public final class FxLights {
    /** Quantas luzes cabem no quadro; as que passam disso são descartadas. */
    public static final int CAPACITY = 64;
    /** Quantas luzes o shader recebe (o tamanho dos arrays dele). */
    public static final int SHADER_MAX = 32;

    private static final double[] POS = new double[CAPACITY * 3];
    private static final float[] COLOR = new float[CAPACITY * 4];
    private static final float[] RADIUS = new float[CAPACITY];
    private static final double[] DIST = new double[CAPACITY];
    private static int count;
    /** As luzes do último quadro que as teve, guardadas antes de o {@link #clear()} zerar; só do e2e. */
    private static int last;
    /** O {@link #last} é do quadro que acabou (alguém limpou com luzes anotadas), não de um mais velho. */
    private static boolean lastFresh;

    private FxLights() {
    }

    /**
     * Anota uma luz: a posição no mundo, a cor, a intensidade {@code k} e o raio, em blocos. Recusa na passada de
     * sombra (a luz é da passada do mundo) e quando o quadro já tem {@link #CAPACITY} luzes.
     */
    public static void add(double wx, double wy, double wz, float r, float g, float b, float k, float radiusBlocks) {
        if (count >= CAPACITY || FxQueue.inShadowPass()) {
            return;
        }
        POS[count * 3] = wx;
        POS[count * 3 + 1] = wy;
        POS[count * 3 + 2] = wz;
        COLOR[count * 4] = r;
        COLOR[count * 4 + 1] = g;
        COLOR[count * 4 + 2] = b;
        COLOR[count * 4 + 3] = k;
        RADIUS[count] = radiusBlocks;
        count++;
    }

    public static int count() {
        return count;
    }

    /** Zera as luzes do quadro; se havia luz anotada, guarda quantas eram para {@link #lastFrameCount()}. */
    public static void clear() {
        if (count > 0) {
            last = count;
            lastFresh = true;
        }
        count = 0;
    }

    /**
     * O começo de um quadro: limpa como o {@link #clear()}, e o valor guardado só sobrevive a esta virada se o quadro
     * que acabou teve luz (um quadro sem luz zera a contagem do e2e).
     */
    public static void beginFrame() {
        clear();
        if (!lastFresh) {
            last = 0;
        }
        lastFresh = false;
    }

    /** Só do e2e: quantas luzes o último quadro desenhado anotou (o {@link #count()} já é zero depois do quadro). */
    public static int lastFrameCount() {
        return last;
    }

    /**
     * A queda em degraus: {@code f = 1 − d/r}; {@code floor(f² × 5 + 0,35) / 5}; o último degrau já é 0 e a borda não
     * corta seco. É o que o shader faz, para os testes.
     */
    static double step(double d, double radius) {
        if (d >= radius) {
            return 0;
        }
        double f = 1 - d / radius;
        return Math.floor(f * f * 5 + 0.35) / 5;
    }

    /** O divisor do teto suave: 1 até a soma chegar a 1 e {@code 1 + (topo − 1) × 1,2} depois. */
    static double ceilDivisor(double top) {
        return top > 1 ? 1 + (top - 1) * 1.2 : 1;
    }

    /**
     * Copia para os arrays do shader as até {@link #SHADER_MAX} luzes mais próximas da câmera, da mais próxima à mais
     * distante, e devolve quantas. {@code pos}: xyz relativo à câmera e w = raio, em blocos (4 floats por luz);
     * {@code col}: rgb e k (4 floats por luz). Seleção simples, sem ordenar objetos.
     */
    public static int fill(double camX, double camY, double camZ, float[] pos, float[] col) {
        for (int i = 0; i < count; i++) {
            double dx = POS[i * 3] - camX;
            double dy = POS[i * 3 + 1] - camY;
            double dz = POS[i * 3 + 2] - camZ;
            DIST[i] = dx * dx + dy * dy + dz * dz;
        }
        int n = Math.min(count, SHADER_MAX);
        for (int o = 0; o < n; o++) {
            int best = 0;
            double bestDist = Double.POSITIVE_INFINITY;
            for (int i = 0; i < count; i++) {
                if (DIST[i] < bestDist) {
                    bestDist = DIST[i];
                    best = i;
                }
            }
            DIST[best] = Double.POSITIVE_INFINITY;
            pos[o * 4] = (float) (POS[best * 3] - camX);
            pos[o * 4 + 1] = (float) (POS[best * 3 + 1] - camY);
            pos[o * 4 + 2] = (float) (POS[best * 3 + 2] - camZ);
            pos[o * 4 + 3] = RADIUS[best];
            System.arraycopy(COLOR, best * 4, col, o * 4, 4);
        }
        return n;
    }
}
