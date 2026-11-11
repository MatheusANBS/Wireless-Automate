package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * As duas receitas da Doca de Padrões, consultadas no {@code RecipeManager} do servidor: {@link #AE2} (com o AE2) e
 * {@link #RS2} (com o RS2 e sem o AE2). Cada run confere quais carregam: a comum (nenhuma), a do AE2, a do RS2 e a dos
 * químicos da Doca, que tem os dois mods (só a do AE2).
 */
final class DockRecipes {
    static final String AE2 = "pattern_dock";
    static final String RS2 = "pattern_dock_rs2";

    private DockRecipes() {
    }

    /** A receita {@code path} do mod está carregada. */
    static boolean loaded(GameTestHelper helper, String path) {
        return helper.getLevel().getRecipeManager().byKey(WirelessAutomate.id(path)).isPresent();
    }

    /** Só a receita {@code expected} (ou nenhuma, com {@code null}) das duas está carregada. */
    static void assertOnly(GameTestHelper helper, String expected) {
        for (String path : new String[] {AE2, RS2}) {
            helper.assertTrue(loaded(helper, path) == path.equals(expected),
                    "receita " + path + (loaded(helper, path) ? " carregada" : " não carregada"));
        }
    }

    /**
     * O desenho {@code KLK / SPS / KRK} com o processador {@code processor} e o provedor {@code provider} faz a Doca
     * Básica pela receita {@code path}.
     */
    static void assertCrafts(GameTestHelper helper, String path, String processor, String provider) {
        Item k = ModItems.RESONATOR_CASING.get();
        Item l = item(helper, processor);
        Item s = ModItems.SIGNAL_ALLOY_INGOT.get();
        Item p = item(helper, provider);
        Item r = ModItems.ROUTER.get();
        List<ItemStack> grid = new ArrayList<>(9);
        for (Item item : new Item[] {k, l, k, s, p, s, k, r, k}) {
            grid.add(new ItemStack(item));
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        RecipeHolder<CraftingRecipe> holder = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new GameTestAssertException("a grade da Doca não casa com nenhuma receita"));
        helper.assertTrue(holder.id().equals(WirelessAutomate.id(path)), "receita da grade: " + holder.id());
        ItemStack out = holder.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ModItems.PATTERN_DOCK.get()) && out.getCount() == 1, "resultado: " + out);
    }

    private static Item item(GameTestHelper helper, String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        helper.assertTrue(item != Items.AIR, id + " não existe no registro");
        return item;
    }
}
