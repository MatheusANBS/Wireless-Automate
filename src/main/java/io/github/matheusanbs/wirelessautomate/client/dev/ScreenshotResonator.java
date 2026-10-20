package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevScreenshot.*;

import com.mojang.blaze3d.platform.Window;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.resonator.screen.ResonatorScreen;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import io.github.matheusanbs.wirelessautomate.resonator.Tuning;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * As capturas da tela do Núcleo, os seis estados da galeria do mockup aprovado ({@code ressonador-etapa3.html}), cada
 * um no tema dele: em sintonia, fora (1,3 MHz), sem sinal, o Sintonizador no teto do Ultimate, sem a peça (com a dica
 * do botão) e um Opaco em recarga. {@code WA_SCREENSHOT_ONLY=nucleo} tira só estas.
 */
final class ScreenshotResonator {
    private ScreenshotResonator() {
    }

    /** Desenhada no lugar das outras a partir do primeiro passo do Núcleo. */
    static ResonatorScreen resonatorScreen;

    /** Um estado da galeria: os cristais em "c.62" (cristal com 62%), "l.9" (Lapidado), "o.35" (Opaco com 35%). */
    private record Sample(RouterTier tier, double deviation, boolean auto, boolean tuner, List<String> crystals,
            int redstone, double energy, int energyPorts, int crystalPorts, int layout) {
    }

    static List<Step> resonatorSteps() {
        Sample sintonia = new Sample(RouterTier.ELITE, 0, false, true, List.of("c.62", "l.9", "c.3"), 14, 0.35, 2, 1, 1);
        Sample fora = new Sample(RouterTier.ELITE, 1.3, false, true, List.of("c.62", "l.9", "c.3"), 14, 0.12, 2, 1, 1);
        Sample zero = new Sample(RouterTier.ELITE, 3.6, false, true, List.of("c.62", "l.9", "c.3"), 14, 0.04, 2, 1, 1);
        Sample teto = new Sample(RouterTier.ULTIMATE, 0, true, true,
                List.of("l.95", "l.8", "l.72", "l.61", "l.5", "l.33", "l.2", "l.1"), 64, 1, 3, 2, 2);
        Sample semTuner = new Sample(RouterTier.BASIC, 0.4, false, false, List.of("c.5"), 0, 0.5, 1, 1, 1);
        Sample opaco = new Sample(RouterTier.EMERALD, 0, true, true, List.of("c.4", "o.35", "l.7", "c.9"), 7, 0.6, 1, 1,
                1);
        return List.of(
                new Step(() -> {
                    themeBefore = GuiThemes.current();
                    show(sintonia, GuiTheme.LIGHT);
                }, "nucleo-1-sintonia"),
                new Step(() -> show(fora, GuiTheme.DARK), "nucleo-2-fora"),
                new Step(() -> show(zero, GuiTheme.LIGHT), "nucleo-3-sem-sinal"),
                new Step(() -> show(teto, GuiTheme.DARK), "nucleo-4-teto"),
                new Step(() -> {
                    show(semTuner, GuiTheme.LIGHT);
                    int[] center = resonatorScreen.modeAutoCenter();
                    mouseX = center[0];
                    mouseY = center[1];
                }, "nucleo-5-sem-sintonizador"),
                new Step(() -> {
                    show(opaco, GuiTheme.DARK);
                    int[] center = resonatorScreen.coreSlotCenter(1);
                    mouseX = center[0];
                    mouseY = center[1];
                }, "nucleo-6-opaco"),
                new Step(() -> {
                    show(sintonia, GuiTheme.DARK);
                    int[] center = resonatorScreen.coreSlotCenter(0);
                    mouseX = center[0];
                    mouseY = center[1];
                }, "nucleo-7-escuro-dica"),
                new Step(() -> {
                    GuiThemes.preview(themeBefore);
                    show(sintonia, themeBefore);
                }, "nucleo-8-tema-de-antes"));
    }

    private static void show(Sample sample, GuiTheme theme) {
        mouseX = mouseY = -1;
        GuiThemes.preview(theme);
        int tier = sample.tier().ordinal();
        int chambers = CoreMath.chambers(tier);
        // uns itens no inventário, como no mockup: um Lapidado e redstone na barra
        Inventory inventory = new Inventory(null);
        ItemStack held = new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(held, CrystalCharge.FULL);
        inventory.setItem(0, held);
        inventory.setItem(1, new ItemStack(Items.REDSTONE, 32));
        ResonatorMenu menu = new ResonatorMenu(0, inventory, BlockPos.ZERO, sample.tier(), chambers);
        int activeYield = 0;
        int allYield = 0;
        int rechargePercent = 0;
        for (int i = 0; i < sample.crystals().size(); i++) {
            String[] spec = sample.crystals().get(i).split("\\.");
            double fraction = Double.parseDouble("0." + spec[1]);
            boolean cut = spec[0].equals("l");
            ItemStack stack = new ItemStack(cut ? ModItems.CUT_SIGNAL_CRYSTAL.get() : ModItems.SIGNAL_CRYSTAL.get());
            boolean dull = spec[0].equals("o");
            SignalCrystalItem.setCharge(stack, dull ? 0 : (int) Math.round(fraction * CrystalCharge.FULL));
            int yield = ((SignalCrystalItem) stack.getItem()).yield();
            allYield += yield;
            if (dull) {
                rechargePercent = (int) Math.round(fraction * 100);
            } else {
                activeYield += yield;
            }
            menu.getSlot(i).set(stack);
        }
        if (sample.redstone() > 0) {
            menu.getSlot(menu.redstoneSlot()).set(new ItemStack(Items.REDSTONE, sample.redstone()));
        }
        long fe = CoreMath.defaultFePerCrystal(tier);
        double efficiency = Tuning.capped(Tuning.efficiency(sample.deviation()), sample.auto(), tier);
        long capacity = CoreMath.buffer(chambers, fe);
        int rechargeTicks = 600;
        double dial = sample.auto() ? 98.15 : 94.0;
        menu.apply(new ResonatorStatePayload(0, Math.round(capacity * sample.energy()), capacity,
                CoreMath.perTick(fe, activeYield * 4, efficiency), CoreMath.perTick(fe, allYield * 4, 1.0), dial,
                (int) Math.round(efficiency * 1000), (int) Math.round(Tuning.ceiling(tier) * 1000), sample.auto(),
                sample.tuner(), TuneSignal.steps(dial + sample.deviation(), dial),
                TuneSignal.tuned(dial + sample.deviation(), dial), rechargeTicks * rechargePercent / 100,
                rechargeTicks, sample.energyPorts(), sample.crystalPorts(), sample.layout()));
        resonatorScreen = new ResonatorScreen(menu, inventory,
                Component.translatable("block.wirelessautomate.resonance_core"));
        // já no tamanho da janela: os ganchos dos centros dependem da posição da tela
        Window window = Minecraft.getInstance().getWindow();
        resonatorScreen.init(Minecraft.getInstance(), window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }
}
