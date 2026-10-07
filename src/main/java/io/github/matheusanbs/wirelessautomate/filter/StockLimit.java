package io.github.matheusanbs.wirelessautomate.filter;

/**
 * As contas do estoque das entradas de filtro ({@link FilterEntry#stock()}), em lógica pura
 * testada por JUnit. Estoque 0 ou negativo = sem estoque, sem limite.
 */
public final class StockLimit {
    /** Quanto pode sair de uma origem que tem {@code present} e precisa manter {@code stock}, até {@code wanted}. */
    public static long extractable(long present, long stock, long wanted) {
        if (stock <= 0) {
            return Math.max(0, wanted);
        }
        return clamp(present - stock, wanted);
    }

    /** Quanto pode entrar num destino que tem {@code present} e aceita só até {@code stock}, até {@code offered}. */
    public static long acceptable(long present, long stock, long offered) {
        if (stock <= 0) {
            return Math.max(0, offered);
        }
        return clamp(stock - present, offered);
    }

    private static long clamp(long room, long max) {
        return Math.max(0, Math.min(room, max));
    }

    private StockLimit() {
    }
}
