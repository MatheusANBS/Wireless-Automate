package io.github.matheusanbs.wirelessautomate.client;

/**
 * Como a linha de abas de tipo cabe na largura (lógica pura): todas com nome; só a ativa com nome e as
 * outras no ícone; ou só ícones.
 */
public final class TabLayout {
    /** Largura de uma aba só com a bolinha da rede e o ícone 9 × 9. */
    public static final int ICON_TAB = 24;
    public static final int GAP = 3;

    public enum Mode { FULL, ACTIVE_NAME, ICONS }

    public record Result(Mode mode, int[] widths) {
    }

    private TabLayout() {
    }

    /** {@code fullWidths}: largura de cada aba com o nome; {@code available}: espaço para as abas. */
    public static Result choose(int[] fullWidths, int active, int available) {
        if (total(fullWidths) <= available) {
            return new Result(Mode.FULL, fullWidths.clone());
        }
        int[] compact = new int[fullWidths.length];
        for (int i = 0; i < compact.length; i++) {
            compact[i] = i == active ? fullWidths[i] : ICON_TAB;
        }
        if (total(compact) <= available) {
            return new Result(Mode.ACTIVE_NAME, compact);
        }
        int[] icons = new int[fullWidths.length];
        java.util.Arrays.fill(icons, ICON_TAB);
        return new Result(Mode.ICONS, icons);
    }

    private static int total(int[] widths) {
        int sum = GAP * Math.max(0, widths.length - 1);
        for (int w : widths) {
            sum += w;
        }
        return sum;
    }
}
