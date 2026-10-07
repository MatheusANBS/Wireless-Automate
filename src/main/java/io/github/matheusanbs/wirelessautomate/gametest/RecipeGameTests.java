package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
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
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Receitas do mod, consultadas no {@code RecipeManager} do servidor com uma grade montada à mão:
 * núcleos de tier (só vanilla), Cartão de Filtro e a duplicação de cartão.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeGameTests {
    private static ItemStack stack(ItemLike item) {
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Grade 3×3 a partir de nove itens (null = vazio). */
    private static CraftingInput grid(ItemLike... items) {
        List<ItemStack> stacks = new ArrayList<>(9);
        for (ItemLike item : items) {
            stacks.add(stack(item));
        }
        return CraftingInput.of(3, 3, stacks);
    }

    private static Optional<RecipeHolder<CraftingRecipe>> find(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }

    /** A grade casa com a receita {@code id} e o resultado é {@code count} de {@code result}. */
    private static void assertCrafts(GameTestHelper helper, String id, CraftingInput input, Item result, int count) {
        RecipeHolder<CraftingRecipe> holder = find(helper, input)
                .orElseThrow(() -> new GameTestAssertException("nenhuma receita para " + id));
        helper.assertValueEqual(holder.id(), WirelessAutomate.id(id), "receita");
        ItemStack out = holder.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(result), "resultado de " + id + ": " + out);
        helper.assertValueEqual(out.getCount(), count, "quantidade de " + id);
    }

    @GameTest(template = "empty")
    public static void tierCoreRecipes(GameTestHelper helper) {
        Item gold = Items.GOLD_INGOT;
        Item diamond = Items.DIAMOND;
        assertCrafts(helper, "tier_core_advanced",
                grid(gold, diamond, gold, diamond, Items.GOLD_BLOCK, diamond, gold, diamond, gold),
                ModItems.TIER_CORES.get(RouterTier.ADVANCED).get(), 1);

        Item netherite = Items.NETHERITE_INGOT;
        assertCrafts(helper, "tier_core_elite",
                grid(null, Items.NETHER_STAR, null,
                        netherite, ModItems.TIER_CORES.get(RouterTier.ADVANCED).get(), netherite,
                        null, netherite, null),
                ModItems.TIER_CORES.get(RouterTier.ELITE).get(), 1);

        Item block = Items.NETHERITE_BLOCK;
        Item echo = Items.ECHO_SHARD;
        assertCrafts(helper, "tier_core_ultimate",
                grid(echo, Items.DRAGON_EGG, echo,
                        block, ModItems.TIER_CORES.get(RouterTier.ELITE).get(), block,
                        echo, block, echo),
                ModItems.TIER_CORES.get(RouterTier.ULTIMATE).get(), 1);

        // Sem o núcleo anterior no centro não sai nada: a progressão é obrigatória.
        helper.assertTrue(find(helper, grid(null, Items.NETHER_STAR, null,
                netherite, Items.DIAMOND, netherite, null, netherite, null)).isEmpty(), "Elite sem o Avançado");
        // Não há núcleo Básico: o roteador já nasce Básico.
        helper.assertTrue(!ModItems.TIER_CORES.containsKey(RouterTier.BASIC)
                && !BuiltInRegistries.ITEM.containsKey(WirelessAutomate.id("tier_core_basic")),
                "não deveria existir núcleo Básico");
        helper.succeed();
    }

    /** Roteador + núcleo do tier seguinte, em qualquer posição da grade, sobe um tier; o resto não casa. */
    @GameTest(template = "empty")
    public static void routerUpgradeRecipe(GameTestHelper helper) {
        RouterTier[] tiers = RouterTier.values();
        for (RouterTier tier : tiers) {
            RouterTier next = tier.next();
            if (next == null) {
                continue;
            }
            ItemStack router = RouterBlockItem.withTier(ModItems.ROUTER.get(), tier);
            router.set(DataComponents.CUSTOM_NAME, Component.literal("Fornalha 1"));
            CraftingInput input = CraftingInput.of(3, 3, Arrays.asList(ItemStack.EMPTY, router, ItemStack.EMPTY,
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                    new ItemStack(ModItems.TIER_CORES.get(next).get()), ItemStack.EMPTY, ItemStack.EMPTY));
            RecipeHolder<CraftingRecipe> holder = find(helper, input)
                    .orElseThrow(() -> new GameTestAssertException("sem upgrade de " + tier));
            helper.assertValueEqual(holder.id(), WirelessAutomate.id("router_upgrade"), "receita");
            ItemStack out = holder.value().assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(out.is(ModItems.ROUTER.get()), "resultado: " + out);
            helper.assertValueEqual(out.getCount(), 1, "quantidade");
            helper.assertValueEqual(RouterBlockItem.tierOf(out), next, "tier de " + tier);
            helper.assertValueEqual(out.getHoverName().getString(), "Fornalha 1", "nome perdido");
        }
        ItemStack basic = RouterBlockItem.withTier(ModItems.ROUTER.get(), RouterTier.BASIC);
        ItemStack elite = new ItemStack(ModItems.TIER_CORES.get(RouterTier.ELITE).get());
        ItemStack advanced = new ItemStack(ModItems.TIER_CORES.get(RouterTier.ADVANCED).get());
        // Pular tier, roteador Ultimate, dois núcleos, dois roteadores ou só o roteador: nada.
        helper.assertTrue(find(helper, CraftingInput.of(2, 1, List.of(basic, elite))).isEmpty(), "pulou tier");
        helper.assertTrue(find(helper, CraftingInput.of(2, 1, List.of(
                RouterBlockItem.withTier(ModItems.ROUTER.get(), RouterTier.ULTIMATE),
                new ItemStack(ModItems.TIER_CORES.get(RouterTier.ULTIMATE).get())))).isEmpty(), "Ultimate subiu");
        helper.assertTrue(find(helper, CraftingInput.of(3, 1, List.of(basic, advanced, advanced.copy()))).isEmpty(),
                "dois núcleos");
        helper.assertTrue(find(helper, CraftingInput.of(3, 1, List.of(basic, basic.copy(), advanced))).isEmpty(),
                "dois roteadores");
        helper.assertTrue(find(helper, CraftingInput.of(1, 1, List.of(basic))).isEmpty(), "só o roteador");

        // Clique do meio no bloco devolve o roteador no tier dele.
        BlockState elitePlaced = ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ELITE);
        ItemStack picked = ModBlocks.ROUTER.get().getCloneItemStack(helper.getLevel(), BlockPos.ZERO, elitePlaced);
        helper.assertValueEqual(RouterBlockItem.tierOf(picked), RouterTier.ELITE, "clique do meio");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void filterCardRecipe(GameTestHelper helper) {
        Item paper = Items.PAPER;
        CraftingInput input = CraftingInput.of(3, 2, Arrays.asList(
                stack(paper), stack(Items.REDSTONE), stack(paper),
                stack(paper), stack(Items.COMPARATOR), stack(paper)));
        assertCrafts(helper, "filter_card", input, ModItems.FILTER_CARD.get(), 2);
        helper.succeed();
    }

    private static ItemStack configuredCard() {
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());
        Filter filter = Filter.EMPTY.withListMode(Filter.ListMode.BLACKLIST)
                .withEntry(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 0))
                .withEntry(new FilterEntry.TagEntry(ResourceLocation.parse("c:ingots"), 32));
        FilterCardItem.setContents(card, new FilterCardItem.Contents(ResourceType.ITEM, filter));
        return card;
    }

    @GameTest(template = "empty")
    public static void filterCardCopyDuplicatesAndKeepsOriginal(GameTestHelper helper) {
        ItemStack original = configuredCard();
        FilterCardItem.Contents contents = FilterCardItem.contents(original);
        ItemStack blank = new ItemStack(ModItems.FILTER_CARD.get());
        CraftingInput input = CraftingInput.of(3, 3, Arrays.asList(
                ItemStack.EMPTY, blank.copy(), ItemStack.EMPTY,
                original.copy(), ItemStack.EMPTY, blank.copy(),
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));

        RecipeHolder<CraftingRecipe> holder = find(helper, input)
                .orElseThrow(() -> new GameTestAssertException("duplicação não casou"));
        helper.assertValueEqual(holder.id(), WirelessAutomate.id("filter_card_copy"), "receita");

        ItemStack out = holder.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ModItems.FILTER_CARD.get()), "resultado não é cartão: " + out);
        helper.assertValueEqual(out.getCount(), 2, "um cartão por vazio");
        helper.assertValueEqual(FilterCardItem.contents(out), contents, "filtro copiado");

        NonNullList<ItemStack> remaining = holder.value().getRemainingItems(input);
        int returned = 0;
        for (ItemStack stack : remaining) {
            if (stack.isEmpty()) {
                continue;
            }
            returned++;
            helper.assertTrue(stack.is(ModItems.FILTER_CARD.get()) && stack.getCount() == 1, "sobra: " + stack);
            helper.assertValueEqual(FilterCardItem.contents(stack), contents, "original devolvido");
        }
        helper.assertValueEqual(returned, 1, "sobras");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void filterCardCopyRejectsInvalidGrids(GameTestHelper helper) {
        ItemStack blank = new ItemStack(ModItems.FILTER_CARD.get());
        ItemStack configured = configuredCard();
        // Só vazios, só o configurado, dois configurados e um item estranho: nada casa.
        List<List<ItemStack>> grids = List.of(
                List.of(blank.copy(), blank.copy()),
                List.of(configured.copy(), ItemStack.EMPTY),
                List.of(configured.copy(), configured.copy(), blank.copy(), ItemStack.EMPTY),
                List.of(configured.copy(), blank.copy(), new ItemStack(Items.PAPER), ItemStack.EMPTY));
        for (List<ItemStack> stacks : grids) {
            CraftingInput input = CraftingInput.of(2, stacks.size() / 2, stacks);
            helper.assertTrue(find(helper, input).isEmpty(), "não deveria casar: " + stacks);
        }
        // Um cartão de fluidos vazio tem o componente (o tipo): vale como configurado.
        ItemStack fluid = new ItemStack(ModItems.FILTER_CARD.get());
        FilterCardItem.setContents(fluid, new FilterCardItem.Contents(ResourceType.FLUID, Filter.EMPTY));
        helper.assertTrue(fluid.has(ModDataComponents.CARD_FILTER.get()), "cartão de fluidos sem componente");
        CraftingInput input = CraftingInput.of(2, 1, List.of(fluid, blank.copy()));
        helper.assertTrue(find(helper, input).isPresent(), "cartão de fluidos não copiou");
        helper.succeed();
    }
}
