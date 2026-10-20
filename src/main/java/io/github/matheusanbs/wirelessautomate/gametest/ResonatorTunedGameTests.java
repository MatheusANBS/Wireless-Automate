package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertInserted;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Rebalanceamento do Ressonador, etapa 1: os Cristais Afinados, o rendimento em quartos por tier do Núcleo e o
 * cristal rachado (integridade 0), que não gera.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorTunedGameTests {
    private ResonatorTunedGameTests() {
    }

    private static ItemStack tuned(RouterTier tier) {
        return new ItemStack(ModItems.TUNED_CRYSTALS.get(tier).get());
    }

    /** Estação e dial juntos: eficiência 100%, para a geração não ser zero. */
    private static void tune(ResonanceCoreBlockEntity core) {
        core.setStationForTest(98.0);
        core.setDial(98.0);
    }

    private static void assertGeneration(GameTestHelper helper, ResonanceCoreBlockEntity core, int quarters, String what) {
        long esperado = CoreMath.perTick(Config.resonatorFePerCrystal(core.tier()), quarters, core.efficiency());
        helper.assertTrue(esperado > 0, "a geração esperada é zero: " + what);
        helper.assertValueEqual(core.generationPerTick(), esperado, what);
    }

    @GameTest(template = "empty7")
    public static void afinadoDoTierRendeCheio(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            tune(core);
            for (int i = 0; i < 4; i++) {
                assertInserted(helper, core, tuned(RouterTier.EMERALD));
            }
            assertGeneration(helper, core, 32, "quatro Afinados Esmeralda, 8 quartos cada");
        });
    }

    @GameTest(template = "empty7")
    public static void lapidadoComumRendeUmQuartoNoElite(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            tune(core);
            for (int i = 0; i < 3; i++) {
                assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            }
            assertGeneration(helper, core, 6, "três Lapidados no Elite, 2 quartos cada");
        });
    }

    @GameTest(template = "empty7")
    public static void afinadoDeTierMenorRendeComoLapidado(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            tune(core);
            assertInserted(helper, core, tuned(RouterTier.ELITE));
            helper.assertValueEqual(ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get().yieldQuarters(RouterTier.EMERALD),
                    2, "quartos do Afinado Elite no Esmeralda");
            assertGeneration(helper, core, 2, "um Afinado Elite no Esmeralda");
        });
    }

    @GameTest(template = "empty7")
    public static void basicoAceitaQualquerCristalCheio(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            tune(core);
            assertInserted(helper, core, tuned(RouterTier.ULTIMATE));
            assertGeneration(helper, core, 8, "um Afinado Ultimate no Básico");
        });
    }

    @GameTest(template = "empty7")
    public static void rachadoNaoGera(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            tune(core);
            assertInserted(helper, core, tuned(RouterTier.EMERALD));
            assertInserted(helper, core, tuned(RouterTier.EMERALD));
            // A pilha da câmara é a do Núcleo: racha o segundo com a carga cheia, para só a integridade decidir.
            SignalCrystalItem.setIntegrity(core.crystals().get(1), 0);
            helper.assertTrue(SignalCrystalItem.isCracked(core.crystals().get(1)), "o cristal não rachou");
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(1)), "o rachado perdeu a carga");
            assertGeneration(helper, core, 8, "só o Afinado íntegro gera");
        });
    }

    @GameTest(template = "empty")
    public static void nomeEDicaDoRachado(GameTestHelper helper) {
        int max = Config.crystalIntegrity();
        ItemStack stack = tuned(RouterTier.ELITE);
        helper.assertTrue(!SignalCrystalItem.isCracked(stack), "o cristal novo está rachado");
        helper.assertValueEqual(SignalCrystalItem.integrity(stack), max, "integridade do cristal novo");

        SignalCrystalItem.setIntegrity(stack, max - 3);
        List<Component> lines = tooltip(stack);
        helper.assertTrue(hasLine(lines, "item.wirelessautomate.signal_crystal.tooltip.integrity", max - 3, max),
                "a dica não mostra a integridade: " + lines);
        helper.assertTrue(hasLine(lines, "item.wirelessautomate.signal_crystal.tooltip.tuned"),
                "a dica não diz o tier do Afinado: " + lines);
        helper.assertValueEqual(key(stack.getHoverName()), "item.wirelessautomate.tuned_signal_crystal_elite",
                "nome do Afinado gasto");

        SignalCrystalItem.setIntegrity(stack, 0);
        helper.assertTrue(SignalCrystalItem.isCracked(stack), "integridade 0 não é rachado");
        helper.assertValueEqual(key(stack.getHoverName()), "item.wirelessautomate.tuned_signal_crystal_elite.cracked",
                "nome do Afinado rachado");
        helper.assertTrue(hasLine(tooltip(stack), "item.wirelessautomate.signal_crystal.tooltip.cracked",
                Config.recastCrystals()), "a dica do rachado não diz como refazer");

        ItemStack sinal = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(sinal, 0);
        SignalCrystalItem.setIntegrity(sinal, 0);
        helper.assertValueEqual(key(sinal.getHoverName()), "item.wirelessautomate.signal_crystal.cracked",
                "o rachado ganha do Opaco no nome");

        SignalCrystalItem.setIntegrity(stack, max);
        helper.assertTrue(!stack.has(ModDataComponents.CRYSTAL_INTEGRITY.get()), "a integridade cheia não tirou o componente");
        helper.assertTrue(ItemStack.isSameItemSameComponents(stack, tuned(RouterTier.ELITE)),
                "o cristal refeito não empilha com o novo");
        helper.succeed();
    }

    private static List<Component> tooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        return lines;
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    private static boolean hasLine(List<Component> lines, String key, Object... args) {
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
                if (args.length == 0) {
                    return true;
                }
                Object[] got = t.getArgs();
                if (got.length != args.length) {
                    continue;
                }
                boolean same = true;
                for (int i = 0; i < args.length; i++) {
                    same &= String.valueOf(args[i]).equals(String.valueOf(got[i]));
                }
                if (same) {
                    return true;
                }
            }
        }
        return false;
    }
}
