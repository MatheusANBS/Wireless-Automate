package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import net.minecraft.world.item.ItemStack;

/** Os 17 slots do Reparador e o que cada um aceita: 8 rachados, 1 estoque de cristais cheios, 8 saídas. */
public final class RepairerSlots {
    public static final int INPUTS = RepairMath.LINES;
    public static final int FEED = INPUTS;
    public static final int OUTPUT_FIRST = FEED + 1;
    public static final int OUTPUTS = RepairMath.LINES;
    public static final int TOTAL = OUTPUT_FIRST + OUTPUTS;

    private RepairerSlots() {
    }

    public static boolean isInput(int slot) {
        return slot >= 0 && slot < INPUTS;
    }

    public static boolean isOutput(int slot) {
        return slot >= OUTPUT_FIRST && slot < TOTAL;
    }

    /** Um cristal rachado (qualquer tipo: Sinal, Lapidado ou Afinado). */
    public static boolean acceptsCracked(ItemStack stack) {
        return stack.getItem() instanceof SignalCrystalItem && SignalCrystalItem.isCracked(stack);
    }

    /** O mesmo cristal que a {@code RecastCrystalRecipe} pede: o Cristal de Sinal comum com a carga cheia. */
    public static boolean acceptsFeed(ItemStack stack) {
        return stack.is(ModItems.SIGNAL_CRYSTAL.get()) && SignalCrystalItem.isFull(stack) && !SignalCrystalItem.isCracked(stack);
    }
}
