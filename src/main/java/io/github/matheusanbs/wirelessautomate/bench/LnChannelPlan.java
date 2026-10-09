package io.github.matheusanbs.wirelessautomate.bench;

/**
 * Configuração do canal do Logistics Network que dá a mesma vazão de um tier do roteador
 * (docs/benchmark-logistics-network.md, "Equivalência de configuração"). Lógica pura, com JUnit.
 *
 * <p>O canal faz uma operação a cada {@code tickDelay} ticks, de até {@code batch} unidades, limitada
 * pelo teto do upgrade (valores padrão do {@code UpgradeLimitsConfig} dele, versão 1.17.2). Com atraso
 * 1, o lote é a vazão por tick. Escolhe o menor upgrade de atraso mínimo 1 (Diamante) quando os tetos
 * dele bastam; senão, o Netherite. Vazão 0 (sem limite, o Ultimate) vira o teto do Netherite.
 *
 * @param upgrade   id do item de upgrade ({@code logisticsnetworks:diamond_upgrade} ou {@code netherite_upgrade})
 * @param items     itens por operação
 * @param fluid     mB por operação
 * @param energy    FE por operação
 */
public record LnChannelPlan(String upgrade, int items, int fluid, int energy) {
    public static final String DIAMOND = "logisticsnetworks:diamond_upgrade";
    public static final String NETHERITE = "logisticsnetworks:netherite_upgrade";
    /** Tetos por operação do Diamante e do Netherite (atraso mínimo 1 nos dois). */
    static final int DIAMOND_ITEMS = 256;
    static final int DIAMOND_FLUID = 1_000_000;
    static final int DIAMOND_ENERGY = 250_000;
    static final int NETHERITE_ITEMS = 10_000;
    static final int NETHERITE_FLUID = 2_100_000_000;
    static final int NETHERITE_ENERGY = Integer.MAX_VALUE;
    /** O canal opera a cada tick. */
    public static final int TICK_DELAY = 1;

    /**
     * @param itemsPerSecond itens por segundo do tier (0 = sem limite)
     * @param fluidPerSecond mB por segundo (0 = sem limite)
     * @param energyPerTick  FE por tick (0 = sem limite)
     */
    public static LnChannelPlan forRates(long itemsPerSecond, long fluidPerSecond, long energyPerTick) {
        long items = perTick(itemsPerSecond);
        long fluid = perTick(fluidPerSecond);
        long energy = energyPerTick;
        boolean diamond = itemsPerSecond > 0 && fluidPerSecond > 0 && energyPerTick > 0
                && items <= DIAMOND_ITEMS && fluid <= DIAMOND_FLUID && energy <= DIAMOND_ENERGY;
        if (diamond) {
            return new LnChannelPlan(DIAMOND, (int) items, (int) fluid, (int) energy);
        }
        return new LnChannelPlan(NETHERITE, cap(items, NETHERITE_ITEMS), cap(fluid, NETHERITE_FLUID),
                cap(energy, NETHERITE_ENERGY));
    }

    /** Por segundo → por tick, arredondado para baixo (nunca acima da vazão do tier); 0 continua 0. */
    static long perTick(long perSecond) {
        return perSecond <= 0 ? 0 : Math.max(1, perSecond / 20);
    }

    /** 0 (sem limite) ou acima do teto vira o teto. */
    private static int cap(long value, int max) {
        return value <= 0 || value > max ? max : (int) value;
    }
}
