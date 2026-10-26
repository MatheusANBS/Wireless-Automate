package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import net.minecraft.world.item.ItemStack;

/** O custo em FE de reparar um cristal, pela config: o fator vezes o FE por cristal do tier do tipo dele. */
public final class RepairerCosts {
    /** Os tiers por posição, sem copiar o array do enum a cada consulta. */
    private static final RouterTier[] TIERS = RouterTier.values();

    private RepairerCosts() {
    }

    /** O custo do reparo de {@code crystal}; 0 para o que não é cristal (nunca chega aqui: o slot não o aceita). */
    public static long of(ItemStack crystal) {
        if (!(crystal.getItem() instanceof SignalCrystalItem item)) {
            return 0;
        }
        RouterTier tuned = item.tunedTier();
        int tier = RepairMath.costTier(item.isCut(), tuned == null ? -1 : tuned.ordinal());
        return RepairMath.cost(Config.resonatorFePerCrystal(TIERS[Math.min(tier, TIERS.length - 1)]),
                Config.repairCostFactor());
    }
}
