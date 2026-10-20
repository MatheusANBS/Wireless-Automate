package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** A soma do rendimento em quartos das câmaras de um Núcleo, comum ao Núcleo, ao menu e às capturas. */
public final class CoreYield {
    private CoreYield() {
    }

    /**
     * Os quartos dos cristais das primeiras {@code chambers} câmaras no Núcleo do tier. O rachado nunca conta; o
     * Opaco só com {@code skipDull} falso (a geração possível com todos cheios).
     */
    public static int quarters(List<ItemStack> crystals, int chambers, RouterTier coreTier, boolean skipDull) {
        int sum = 0;
        int n = Math.min(chambers, crystals.size());
        for (int i = 0; i < n; i++) {
            ItemStack stack = crystals.get(i);
            if (stack.getItem() instanceof SignalCrystalItem item && !SignalCrystalItem.isCracked(stack)
                    && !(skipDull && SignalCrystalItem.isDull(stack))) {
                sum += item.yieldQuarters(coreTier);
            }
        }
        return sum;
    }
}
