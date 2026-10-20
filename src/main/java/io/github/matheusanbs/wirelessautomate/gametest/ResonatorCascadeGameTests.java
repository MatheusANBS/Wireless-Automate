package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.resonator.TuneCrystalRecipe;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Rebalanceamento do Ressonador, etapa 1b: a cascata de itens, da Liga de Sinal crua ao Núcleo Condensado. Cada receita
 * casa e rende o que a spec manda; a Liga crua e a Lente só aceitam cristal cheio e sem rachadura.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorCascadeGameTests {
    private ResonatorCascadeGameTests() {
    }

    private static ItemStack of(Item item) {
        return new ItemStack(item);
    }

    /** A grade 3×3 pelas linhas, de cima para baixo; {@code null} é casa vazia. */
    private static Optional<ItemStack> craft(GameTestHelper helper, Item... grade) {
        ItemStack[] stacks = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            stacks[i] = i < grade.length && grade[i] != null ? of(grade[i]) : ItemStack.EMPTY;
        }
        return craft(helper, stacks);
    }

    private static Optional<ItemStack> craft(GameTestHelper helper, ItemStack... grade) {
        ItemStack[] stacks = Arrays.copyOf(grade, 9);
        for (int i = 0; i < 9; i++) {
            if (stacks[i] == null) {
                stacks[i] = ItemStack.EMPTY;
            }
        }
        CraftingInput input = CraftingInput.of(3, 3, Arrays.asList(stacks));
        var level = helper.getLevel();
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()));
    }

    private static void assertYields(GameTestHelper helper, Optional<ItemStack> out, Item expected, int count,
            String what) {
        helper.assertTrue(out.isPresent(), what + ": a receita não casou");
        helper.assertTrue(out.get().is(expected), what + ": saiu " + out.get());
        helper.assertValueEqual(out.get().getCount(), count, what + ": contagem");
    }

    private static ItemStack fullSignal() {
        return crystal(false, CrystalCharge.FULL);
    }

    private static ItemStack fullCut() {
        return crystal(true, CrystalCharge.FULL);
    }

    private static ItemStack cracked(ItemStack stack) {
        SignalCrystalItem.setIntegrity(stack, 0);
        return stack;
    }

    private static Optional<ItemStack> alloy(GameTestHelper helper, ItemStack a, ItemStack b) {
        return craft(helper, a, of(Items.IRON_INGOT), b, of(Items.IRON_INGOT), of(Items.REDSTONE));
    }

    private static Optional<ItemStack> lens(GameTestHelper helper, ItemStack cut) {
        ItemStack glass = of(ModItems.RESONATOR_GLASS.get());
        return craft(helper, glass, of(Items.DIAMOND), glass, glass.copy(), cut, glass.copy(), null,
                of(Items.DIAMOND), null);
    }

    @GameTest(template = "empty")
    public static void cascataRende(GameTestHelper helper) {
        assertYields(helper, alloy(helper, fullSignal(), fullSignal()), ModItems.SIGNAL_ALLOY_RAW.get(), 2, "Liga crua");
        Item ingot = ModItems.SIGNAL_ALLOY_INGOT.get();
        assertYields(helper, craft(helper, ingot, ingot, ingot), ModItems.RESONANCE_PLATE.get(), 1, "Placa");
        assertYields(helper, craft(helper, Items.COPPER_INGOT, ingot, Items.COPPER_INGOT, null, Items.GOLD_INGOT),
                ModItems.RESONANCE_WIRE.get(), 4, "Fio");
        Item wire = ModItems.RESONANCE_WIRE.get();
        assertYields(helper, craft(helper, wire, wire, wire, wire, Items.IRON_BLOCK, wire, wire, wire, wire),
                ModItems.RESONANCE_COIL.get(), 1, "Bobina");
        assertYields(helper, lens(helper, fullCut()), ModItems.HARMONIC_LENS.get(), 1, "Lente");
        Item plate = ModItems.RESONANCE_PLATE.get();
        assertYields(helper, craft(helper, Items.QUARTZ, Items.REDSTONE_BLOCK, Items.QUARTZ, Items.GOLD_INGOT, plate,
                Items.GOLD_INGOT, Items.QUARTZ, Items.REDSTONE_BLOCK, Items.QUARTZ), ModItems.PHASE_CIRCUIT.get(), 2,
                "Circuito");
        Item coil = ModItems.RESONANCE_COIL.get();
        Item circuit = ModItems.PHASE_CIRCUIT.get();
        assertYields(helper, craft(helper, coil, circuit, coil, plate, ModItems.HARMONIC_LENS.get(), plate, coil,
                circuit, coil), ModItems.PHASE_MATRIX.get(), 1, "Matriz");
        Item matrix = ModItems.PHASE_MATRIX.get();
        assertYields(helper, craft(helper, matrix, Items.ENDER_EYE, matrix, Items.CRYING_OBSIDIAN, Items.DIAMOND_BLOCK,
                Items.CRYING_OBSIDIAN, matrix, null, matrix), ModItems.FIELD_STABILIZER.get(), 1, "Estabilizador");
        Item stab = ModItems.FIELD_STABILIZER.get();
        assertYields(helper, craft(helper, stab, Items.NETHERITE_BLOCK, stab, Items.NETHERITE_BLOCK, Items.NETHER_STAR,
                Items.NETHERITE_BLOCK, stab, Items.NETHERITE_BLOCK, stab), ModItems.CONDENSED_CORE.get(), 1,
                "Núcleo Condensado");
        helper.succeed();
    }

    /** Os Corações do Básico ao Esmeralda e o Ultimate sem o Allthemodium; os do Allthemodium só com o mod. */
    @GameTest(template = "empty")
    public static void coracoesDoRessonador(GameTestHelper helper) {
        Item matrix = ModItems.PHASE_MATRIX.get();
        Item stab = ModItems.FIELD_STABILIZER.get();
        Item condensed = ModItems.CONDENSED_CORE.get();
        Item redstone = Items.REDSTONE_BLOCK;
        Item gold = Items.GOLD_BLOCK;
        Item netherite = Items.NETHERITE_INGOT;
        Item emerald = Items.EMERALD_BLOCK;
        Item star = Items.NETHER_STAR;
        Item block = Items.NETHERITE_BLOCK;
        assertYields(helper, craft(helper, redstone, matrix, redstone, null, redstone, null, redstone),
                heart(RouterTier.BASIC), 1, "Coração Básico");
        assertYields(helper, craft(helper, gold, matrix, gold, matrix, gold, null, gold),
                heart(RouterTier.ADVANCED), 1, "Coração Avançado");
        assertYields(helper, craft(helper, netherite, matrix, netherite, matrix, matrix, netherite, netherite),
                heart(RouterTier.ELITE), 1, "Coração Elite");
        assertYields(helper, craft(helper, emerald, emerald, emerald, emerald, stab, emerald, emerald, emerald, star),
                heart(RouterTier.EMERALD), 1, "Coração Esmeralda");
        assertYields(helper, craft(helper, condensed, block, condensed, block, star, block, condensed, block, star),
                heart(RouterTier.ULTIMATE), 1, "Coração Ultimate sem o Allthemodium");
        helper.assertTrue(craft(helper, redstone, matrix, redstone, null, redstone).isEmpty(),
                "o Coração Básico saiu com 3 blocos de redstone");
        var recipes = helper.getLevel().getRecipeManager();
        for (String tier : new String[] {"allthemodium", "vibranium", "unobtainium", "ultimate"}) {
            helper.assertTrue(recipes.byKey(WirelessAutomate.id("resonator_heart_" + tier)).isEmpty(),
                    "a receita do Coração " + tier + " carregou sem o Allthemodium");
        }
        helper.succeed();
    }

    private static Item heart(RouterTier tier) {
        return ModItems.RESONATOR_HEARTS.get(tier).get();
    }

    @GameTest(template = "empty")
    public static void cascataRecusaCristalGasto(GameTestHelper helper) {
        helper.assertTrue(alloy(helper, fullSignal(), crystal(false, CrystalCharge.FULL - 1)).isEmpty(),
                "Liga crua com um Cristal de carga parcial");
        helper.assertTrue(alloy(helper, crystal(false, 0), fullSignal()).isEmpty(), "Liga crua com um Opaco");
        helper.assertTrue(alloy(helper, fullSignal(), cracked(fullSignal())).isEmpty(), "Liga crua com um rachado");
        helper.assertTrue(lens(helper, crystal(true, CrystalCharge.FULL - 1)).isEmpty(),
                "Lente com um Lapidado de carga parcial");
        helper.assertTrue(lens(helper, crystal(true, 0)).isEmpty(), "Lente com um Lapidado Opaco");
        helper.assertTrue(lens(helper, cracked(fullCut())).isEmpty(), "Lente com um Lapidado rachado");
        helper.assertTrue(lens(helper, fullSignal()).isEmpty(), "Lente com um Cristal de Sinal no lugar do Lapidado");
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : new String[] {"signal_alloy_raw", "harmonic_lens"}) {
            var holder = recipes.byKey(WirelessAutomate.id(id));
            helper.assertTrue(holder.isPresent() && holder.get().value() instanceof TuneCrystalRecipe,
                    id + " não é tune_crystal");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ligaNoAltoForno(GameTestHelper helper) {
        var level = helper.getLevel();
        SingleRecipeInput raw = new SingleRecipeInput(of(ModItems.SIGNAL_ALLOY_RAW.get()));
        var blasting = level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, raw, level);
        helper.assertTrue(blasting.isPresent() && blasting.get().value() instanceof BlastingRecipe,
                "a Liga crua sem receita de alto-forno");
        helper.assertValueEqual(blasting.get().id(), WirelessAutomate.id("signal_alloy_ingot"), "id da receita");
        ItemStack out = blasting.get().value().assemble(raw, level.registryAccess());
        helper.assertTrue(out.is(ModItems.SIGNAL_ALLOY_INGOT.get()) && out.getCount() == 1, "saiu " + out);
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, raw, level).isEmpty(),
                "a Liga crua também funde na fornalha comum");
        helper.succeed();
    }
}
