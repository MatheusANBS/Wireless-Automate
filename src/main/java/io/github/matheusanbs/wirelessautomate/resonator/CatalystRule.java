package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * A regra pura do catalisador por tier do Núcleo, pela posição do tier no {@code RouterTier}: a redstone e o Pó de
 * Ressonância no Básico e no Avançado, só o Pó no Elite e na Esmeralda e, do Allthemodium para cima, só o Pó
 * Carregado do próprio tier. O {@link CoreCatalyst} traduz o item para o {@link Kind}.
 */
public final class CatalystRule {
    /** A posição do Elite: daqui para cima a redstone não entra. */
    public static final int FIRST_DUST_ONLY = 2;
    /** A posição do Allthemodium: daqui para cima só o Pó Carregado do tier. */
    public static final int FIRST_CHARGED = 4;

    public enum Kind { REDSTONE, DUST, CHARGED }

    private CatalystRule() {
    }

    /**
     * Se o Núcleo na posição {@code coreTier} aceita o catalisador; {@code chargedTier} é a posição do tier do Pó
     * Carregado e só vale com {@link Kind#CHARGED}.
     */
    public static boolean accepts(int coreTier, Kind kind, int chargedTier) {
        if (coreTier >= FIRST_CHARGED) {
            return kind == Kind.CHARGED && chargedTier == coreTier;
        }
        return switch (kind) {
            case REDSTONE -> coreTier < FIRST_DUST_ONLY;
            case DUST -> true;
            case CHARGED -> false;
        };
    }

    /** Se o Núcleo da posição pede o Pó Carregado (a mensagem de recusa muda). */
    public static boolean needsCharged(int coreTier) {
        return coreTier >= FIRST_CHARGED;
    }
}
