package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
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

/** Etapa 1 do Ressonador de Sinal: os cristais, o minério, a geração e a receita do Lapidado. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorCrystalGameTests {
    private ResonatorCrystalGameTests() {
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static boolean hasLine(List<Component> lines, String translationKey) {
        return lines.stream().anyMatch(line -> key(line).equals(translationKey));
    }

    @GameTest(template = "empty")
    public static void cristalNovoEstaCheioESemComponente(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
        helper.assertValueEqual(SignalCrystalItem.charge(stack), CrystalCharge.FULL, "carga do cristal novo");
        helper.assertTrue(SignalCrystalItem.isFull(stack), "cristal novo está cheio");
        helper.assertFalse(SignalCrystalItem.isDull(stack), "cristal novo não está Opaco");
        SignalCrystalItem.setCharge(stack, 400_000);
        SignalCrystalItem.setCharge(stack, CrystalCharge.FULL);
        helper.assertTrue(ItemStack.isSameItemSameComponents(stack, new ItemStack(ModItems.SIGNAL_CRYSTAL.get())),
                "cheio de novo volta a empilhar com o novo (sem componente)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cargaZeroDeixaOpacoComNomeProprioEEmpilha(GameTestHelper helper) {
        for (Item item : List.of(ModItems.SIGNAL_CRYSTAL.get(), ModItems.CUT_SIGNAL_CRYSTAL.get())) {
            ItemStack a = new ItemStack(item);
            ItemStack b = new ItemStack(item);
            SignalCrystalItem.setCharge(a, 0);
            SignalCrystalItem.setCharge(b, -3);
            helper.assertTrue(SignalCrystalItem.isDull(a), "carga 0 é Opaco");
            helper.assertValueEqual(key(a.getHoverName()), item.getDescriptionId() + ".dull", "nome do Opaco");
            helper.assertTrue(ItemStack.isSameItemSameComponents(a, b), "Opacos do mesmo tipo empilham");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dicaMostraCargaOuRecarga(GameTestHelper helper) {
        Item.TooltipContext ctx = Item.TooltipContext.of(helper.getLevel());
        ItemStack meio = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(meio, 500_000);
        helper.assertTrue(hasLine(meio.getTooltipLines(ctx, null, TooltipFlag.NORMAL),
                "item.wirelessautomate.signal_crystal.tooltip.charge"), "dica com a carga");
        ItemStack opaco = new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(opaco, 0);
        List<Component> linhas = opaco.getTooltipLines(ctx, null, TooltipFlag.NORMAL);
        helper.assertTrue(hasLine(linhas, "item.wirelessautomate.signal_crystal.tooltip.dull"), "dica da recarga");
        helper.assertTrue(hasLine(linhas, "item.wirelessautomate.cut_signal_crystal.tooltip.yield"), "dica do rendimento");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lapidadoRendeODobro(GameTestHelper helper) {
        helper.assertValueEqual(ModItems.SIGNAL_CRYSTAL.get().yield(), 1, "rendimento do Cristal de Sinal");
        helper.assertValueEqual(ModItems.CUT_SIGNAL_CRYSTAL.get().yield(), 2, "rendimento do Lapidado");
        helper.assertTrue(ModItems.CUT_SIGNAL_CRYSTAL.get().isCut(), "o Lapidado é lapidado");
        helper.succeed();
    }
}
