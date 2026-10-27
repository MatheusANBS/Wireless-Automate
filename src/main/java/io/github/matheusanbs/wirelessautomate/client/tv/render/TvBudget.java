package io.github.matheusanbs.wirelessautomate.client.tv.render;

/**
 * A resolução das imagens das Telas pelo tamanho em que aparecem na janela, dentro de um orçamento de memória de
 * vídeo. Os pixels por bloco (ppb) desejados são a potência de 2 acima de 1,5 vez os pixels da janela por bloco,
 * entre {@link #MIN_PPB} e {@link #MAX_PPB}, vezes a qualidade da config ({@code tv.pixelsPerBlock}: 16 metade, 32
 * normal, 64 dobro), com o teto de {@link #MAX_SIDE} px no maior lado da textura. A histerese evita o pisca-pisca ao
 * andar, o {@link #fit} corta pela metade quem mais gasta por pixel visto até caber no orçamento, e o {@link #choose}
 * escolhe no máximo {@link #MAX_RESIZES} mudanças por avaliação, sem nunca passar do orçamento com o que já está
 * alocado. Lógica pura, com JUnit.
 */
public final class TvBudget {
    public static final int MIN_PPB = 32;
    public static final int MAX_PPB = 512;
    public static final int MAX_SIDE = 4096;
    /** O piso dos cortes do orçamento: abaixo dele a Tela fica como está, mesmo acima do orçamento. */
    public static final int FLOOR_PPB = 4;
    public static final int MAX_RESIZES = 2;
    /** O fator pelo ângulo nunca desce disso: uma Tela vista quase de lado ainda pede alguma resolução. */
    public static final double MIN_FACING = 0.25;
    private static final double MARGIN = 1.5;
    /** A folga da descida: desce só quando até com √2 vez o tamanho na janela o desejado é a metade do atual. */
    private static final double SHRINK_MARGIN = Math.sqrt(2);
    private static final int NORMAL_QUALITY = 32;
    private static final double MIN_DISTANCE = 0.5;

    private TvBudget() {
    }

    /**
     * Quantos pixels da janela um bloco da Tela ocupa: {@code altura / (2 × distância × tan(fov / 2))}, vezes o
     * fator do ângulo ({@code max(0,25, |cos|)} entre a normal da Tela e a direção da câmera).
     */
    public static double screenPixelsPerBlock(double windowHeight, double distance, double fovDegrees,
            double cosine) {
        double tan = Math.tan(Math.toRadians(fovDegrees) / 2);
        double perBlock = windowHeight / (2 * Math.max(MIN_DISTANCE, distance) * Math.max(1e-3, tan));
        return perBlock * Math.max(MIN_FACING, Math.abs(cosine));
    }

    /** O teto por tamanho: {@code MAX_SIDE} (ou o máximo da placa, se menor) dividido pelo maior lado. */
    public static int maxPpb(int width, int height, int maxTextureSize) {
        int side = Math.max(1, Math.max(width, height));
        return Math.max(1, Math.min(MAX_SIDE, maxTextureSize) / side);
    }

    /** Os ppb desejados para uma Tela que ocupa {@code screenPpb} pixels da janela por bloco. */
    public static int desired(double screenPpb, int quality, int width, int height, int maxTextureSize) {
        int base = Math.max(MIN_PPB, Math.min(MAX_PPB, ceilPow2(MARGIN * screenPpb)));
        int scaled = Math.max(1, base * Math.max(1, quality) / NORMAL_QUALITY);
        return Math.min(scaled, maxPpb(width, height, maxTextureSize));
    }

    /**
     * Os ppb da Tela depois da histerese: sobe para o desejado quando ele é pelo menos o dobro do atual; desce só
     * quando até o desejado com √2 vez o tamanho na janela é no máximo a metade do atual, e então para esse valor.
     * Entre os dois fica como está (só cai no teto, se a área mudou): andar perto de um limite não alterna entre
     * dois tamanhos. Sem textura ({@code current} 0), o desejado.
     */
    public static int target(int current, double screenPpb, int quality, int width, int height,
            int maxTextureSize) {
        int up = desired(screenPpb, quality, width, height, maxTextureSize);
        if (current <= 0 || up >= 2 * current) {
            return up;
        }
        int down = desired(SHRINK_MARGIN * screenPpb, quality, width, height, maxTextureSize);
        if (2 * down <= current) {
            return down;
        }
        return Math.min(current, maxPpb(width, height, maxTextureSize));
    }

    /** Os bytes da textura com os mipmaps: largura × altura × ppb² × 4 × 4/3. */
    public static long bytes(int width, int height, int ppb) {
        return (long) width * height * ppb * ppb * 16 / 3;
    }

    /**
     * Corta pela metade os {@code ppb} da Tela que mais gasta por pixel da janela visto ({@code ppb / screenPpb})
     * até a soma caber no {@code budget}, sem descer de {@link #FLOOR_PPB}. Muda {@code ppb} no lugar.
     */
    public static void fit(int count, int[] widths, int[] heights, double[] screenPpb, int[] ppb, long budget) {
        long total = 0;
        for (int i = 0; i < count; i++) {
            total += bytes(widths[i], heights[i], ppb[i]);
        }
        while (total > budget) {
            int worst = -1;
            double worstRatio = -1;
            for (int i = 0; i < count; i++) {
                if (ppb[i] <= FLOOR_PPB) {
                    continue;
                }
                double ratio = ppb[i] / Math.max(1e-3, screenPpb[i]);
                if (ratio > worstRatio) {
                    worstRatio = ratio;
                    worst = i;
                }
            }
            if (worst < 0) {
                return;
            }
            long before = bytes(widths[worst], heights[worst], ppb[worst]);
            ppb[worst] = Math.max(FLOOR_PPB, ppb[worst] / 2);
            total += bytes(widths[worst], heights[worst], ppb[worst]) - before;
        }
    }

    /**
     * Até {@link #MAX_RESIZES} Telas a redimensionar de {@code current} para {@code fitted}: primeiro as reduções
     * que mais liberam, depois os aumentos mais carentes ({@code fitted / current} maior) que cabem no orçamento
     * com o que está alocado menos o que as reduções escolhidas liberam. Escreve os índices em {@code out} e
     * devolve quantos.
     */
    public static int choose(int count, int[] widths, int[] heights, int[] current, int[] fitted, long allocated,
            long budget, int[] out) {
        int chosen = 0;
        long total = allocated;
        while (chosen < MAX_RESIZES) {
            int best = -1;
            long bestSaving = 0;
            for (int i = 0; i < count; i++) {
                if (fitted[i] < current[i] && !contains(out, chosen, i)) {
                    long saving = bytes(widths[i], heights[i], current[i]) - bytes(widths[i], heights[i], fitted[i]);
                    if (best < 0 || saving > bestSaving) {
                        best = i;
                        bestSaving = saving;
                    }
                }
            }
            if (best < 0) {
                break;
            }
            out[chosen++] = best;
            total -= bestSaving;
        }
        while (chosen < MAX_RESIZES) {
            int best = -1;
            double bestNeed = 0;
            long bestCost = 0;
            for (int i = 0; i < count; i++) {
                if (fitted[i] <= current[i] || contains(out, chosen, i)) {
                    continue;
                }
                long cost = bytes(widths[i], heights[i], fitted[i]) - bytes(widths[i], heights[i], current[i]);
                double need = fitted[i] / (double) Math.max(1, current[i]);
                if (total + cost <= budget && (best < 0 || need > bestNeed)) {
                    best = i;
                    bestNeed = need;
                    bestCost = cost;
                }
            }
            if (best < 0) {
                break;
            }
            out[chosen++] = best;
            total += bestCost;
        }
        return chosen;
    }

    /** A menor potência de 2 maior ou igual a {@code value} (1 para valores até 1). */
    static int ceilPow2(double value) {
        if (value <= 1) {
            return 1;
        }
        if (value >= 1 << 30) {
            return 1 << 30;
        }
        int n = (int) Math.ceil(value);
        return Integer.highestOneBit(n - 1) << 1;
    }

    private static boolean contains(int[] values, int count, int value) {
        for (int i = 0; i < count; i++) {
            if (values[i] == value) {
                return true;
            }
        }
        return false;
    }
}
