package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;

/**
 * As contas do Reparador de Cristais, em {@code long} e saturando: o custo de um reparo, a capacidade do buffer e o
 * progresso de uma linha. Lógica pura, sem classes do Minecraft.
 */
public final class RepairMath {
    /** As linhas de reparo: uma por câmara do Núcleo Ultimate. */
    public static final int LINES = 8;

    private RepairMath() {
    }

    /**
     * A posição do tier cujo FE por cristal fixa o custo: o Cristal de Sinal custa pelo Básico (0), o Lapidado pelo
     * Avançado (1) e o Afinado pelo tier dele; {@code tunedTierOrdinal < 0} é o cristal sem tier.
     */
    public static int costTier(boolean cut, int tunedTierOrdinal) {
        if (tunedTierOrdinal >= 0) {
            return tunedTierOrdinal;
        }
        return cut ? 1 : 0;
    }

    /** O custo de um reparo: {@code factor} vezes o FE por cristal; fator ou FE negativo vale 0. */
    public static long cost(long fePerCrystal, int factor) {
        return CoreMath.mulSat(Math.max(0, fePerCrystal), Math.max(0, factor));
    }

    /**
     * A capacidade do buffer: o reparo mais caro vezes as linhas, para as oito poderem começar juntas. No mínimo 1,
     * porque no {@code ScalarStore} a capacidade 0 quer dizer sem limite.
     */
    public static long bufferCapacity(long highestCost, int lines) {
        return Math.max(1L, CoreMath.mulSat(highestCost, lines));
    }

    /** Quanto da linha já andou, de 0 a 1000, pelo fim ({@code end}) e a duração total do reparo. */
    public static int progressPermille(long now, long end, int duration) {
        if (duration <= 0) {
            return 1000;
        }
        long left = end - now;
        if (left <= 0) {
            return 1000;
        }
        if (left >= duration) {
            return 0;
        }
        return (int) ((duration - left) * 1000 / duration);
    }
}
