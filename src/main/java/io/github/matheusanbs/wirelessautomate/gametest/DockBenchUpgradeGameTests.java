package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockPendingContents;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.recipe.RouterUpgradeRecipe;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O Cartão de Upgrade na bancada com a Doca de Padrões ({@link RouterUpgradeRecipe}), sem o AE2 e o RS2: a Doca + o
 * cartão certo dá a Doca do tier seguinte, com o pendente e os outros componentes do item; um cartão que não vale
 * para o tier da Doca não casa; e o roteador continua subindo pela mesma receita.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockBenchUpgradeGameTests {
    private DockBenchUpgradeGameTests() {
    }

    private static ItemStack dock(RouterTier tier) {
        return StorageBlockItem.withTier(ModItems.PATTERN_DOCK.get(), tier);
    }

    private static Optional<RecipeHolder<CraftingRecipe>> recipe(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }

    /** {@code block} + {@code card} na bancada; falha se não casa com a receita bloco + cartão. */
    private static ItemStack craft(GameTestHelper helper, ItemStack block, ItemStack card) {
        CraftingInput input = CraftingInput.of(2, 1, List.of(block, card));
        RecipeHolder<CraftingRecipe> holder = recipe(helper, input)
                .orElseThrow(() -> new GameTestAssertException(block + " + " + card + " não casou"));
        helper.assertTrue(holder.value() instanceof RouterUpgradeRecipe, "casou com " + holder.id());
        NonNullList<ItemStack> remaining = holder.value().getRemainingItems(input);
        helper.assertTrue(remaining.stream().allMatch(ItemStack::isEmpty), "sobra na grade: " + remaining);
        ItemStack out = holder.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.getCount() == 1, "resultado: " + out);
        return out;
    }

    @GameTest(template = "empty")
    public static void dockUpgradesOnBench(GameTestHelper helper) {
        ItemStack out = craft(helper, dock(RouterTier.BASIC), TierCoreItem.card(RouterTier.ADVANCED, RouterTier.BASIC));
        helper.assertTrue(out.is(ModItems.PATTERN_DOCK.get()), "não é a Doca: " + out);
        helper.assertTrue(StorageBlockItem.tierOf(out) == RouterTier.ADVANCED, "tier: " + out);
        helper.assertFalse(out.has(ModDataComponents.PATTERN_DOCK_PENDING.get()), "Doca vazia ganhou pendente");
        // O cartão antigo, sem origem, sobe de qualquer tier abaixo, como no mundo.
        ItemStack legacy = craft(helper, dock(RouterTier.BASIC), TierCoreItem.card(RouterTier.ELITE, null));
        helper.assertTrue(StorageBlockItem.tierOf(legacy) == RouterTier.ELITE, "cartão antigo: " + legacy);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dockRefusesWrongCard(GameTestHelper helper) {
        List<List<ItemStack>> grids = List.of(
                List.of(dock(RouterTier.BASIC), TierCoreItem.card(RouterTier.ELITE, RouterTier.ADVANCED)),
                List.of(dock(RouterTier.ADVANCED), TierCoreItem.card(RouterTier.ELITE, RouterTier.BASIC)),
                List.of(dock(RouterTier.ELITE), TierCoreItem.card(RouterTier.ADVANCED, null)),
                List.of(dock(RouterTier.BASIC), dock(RouterTier.BASIC),
                        TierCoreItem.card(RouterTier.ADVANCED, RouterTier.BASIC)));
        for (List<ItemStack> grid : grids) {
            CraftingInput input = CraftingInput.of(grid.size(), 1, grid);
            helper.assertTrue(recipe(helper, input).isEmpty(), "casou: " + grid);
            helper.assertTrue(RouterUpgradeRecipe.upgraded(input).isEmpty(), "subiu: " + grid);
        }
        helper.succeed();
    }

    /** O pendente (um item, um fluido) e o nome vão inteiros para a Doca nova; o item da grade não muda. */
    @GameTest(template = "empty")
    public static void dockPendingSurvivesBench(GameTestHelper helper) {
        DockPendingContents pending = new DockPendingContents(List.of(new DockPendingContents.Side(
                Direction.NORTH.ordinal(), BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE),
                List.of(new DockPendingContents.ItemLine(new ItemStack(Items.IRON_INGOT), 7)),
                List.of(new DockPendingContents.FluidLine(new FluidStack(Fluids.WATER, 1), 500)),
                List.of())));
        ItemStack basic = dock(RouterTier.BASIC);
        basic.set(ModDataComponents.PATTERN_DOCK_PENDING.get(), pending);
        basic.set(DataComponents.CUSTOM_NAME, Component.literal("Doca 1"));
        ItemStack before = basic.copy();
        ItemStack out = craft(helper, basic, TierCoreItem.card(RouterTier.ADVANCED, RouterTier.BASIC));
        helper.assertTrue(StorageBlockItem.tierOf(out) == RouterTier.ADVANCED, "tier: " + out);
        DockPendingContents carried = out.get(ModDataComponents.PATTERN_DOCK_PENDING.get());
        helper.assertTrue(carried != null && carried.sides().size() == 1, "pendente perdido: " + carried);
        DockPendingContents.Side side = carried.sides().get(0);
        helper.assertTrue(side.items().size() == 1 && side.items().get(0).item().is(Items.IRON_INGOT)
                && side.items().get(0).count() == 7, "itens do pendente: " + side.items());
        helper.assertTrue(side.fluids().size() == 1 && side.fluids().get(0).amount() == 500,
                "fluido do pendente: " + side.fluids());
        helper.assertTrue(out.getHoverName().getString().equals("Doca 1"), "nome perdido");
        helper.assertTrue(ItemStack.isSameItemSameComponents(basic, before) && basic.getCount() == 1,
                "o item da grade mudou: " + basic);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void routerStillUpgradesOnBench(GameTestHelper helper) {
        ItemStack out = craft(helper, RouterBlockItem.withTier(ModItems.ROUTER.get(), RouterTier.BASIC),
                TierCoreItem.card(RouterTier.ADVANCED, RouterTier.BASIC));
        helper.assertTrue(out.is(ModItems.ROUTER.get()), "não é o roteador: " + out);
        helper.assertTrue(RouterBlockItem.tierOf(out) == RouterTier.ADVANCED, "tier: " + out);
        helper.succeed();
    }
}
