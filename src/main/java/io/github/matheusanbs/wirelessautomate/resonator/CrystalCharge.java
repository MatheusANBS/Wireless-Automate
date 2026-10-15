package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * A carga de um Cristal de Sinal ou Lapidado, de 0 (Opaco) a {@link #FULL} (cheio). A carga é uma fração
 * da vida: a duração em ticks vem da config e só muda a velocidade do gasto, então mudar a config não
 * mexe nos cristais já guardados. Lógica pura, sem classes do Minecraft.
 */
public final class CrystalCharge {
    public static final int FULL = 1_000_000;

    private CrystalCharge() {
    }

    public static int clamp(int charge) {
        return Math.max(0, Math.min(FULL, charge));
    }

    public static boolean isDull(int charge) {
        return charge <= 0;
    }

    /**
     * Gasta {@code ticks} de uma vida de {@code lifeTicks}. O gasto arredonda para cima, para o cristal
     * nunca durar mais que a vida; sem ticks ou sem vida, não gasta.
     */
    public static int drain(int charge, long ticks, long lifeTicks) {
        int atual = clamp(charge);
        if (ticks <= 0 || lifeTicks <= 0) {
            return atual;
        }
        long gasto = (ticks * FULL + lifeTicks - 1) / lifeTicks;
        return (int) Math.max(0, atual - gasto);
    }

    /** A porcentagem da dica, arredondada para cima: um cristal com carga nunca mostra 0%. */
    public static int percent(int charge) {
        return (int) (((long) clamp(charge) * 100 + FULL - 1) / FULL);
    }
}
