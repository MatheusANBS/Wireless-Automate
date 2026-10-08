package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tiers do Allthemodium, num servidor de testes com o Allthemodium e o All The Tweaks na pasta mods
 * ({@code ./gradlew runGameTestServerAllthemodium}, namespace {@value #NAMESPACE}). Os itens dos dois mods
 * são lidos pelo registro, pelo id: nenhum tipo deles aqui.
 */
@GameTestHolder(AtmGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AtmGameTests {
    static final String NAMESPACE = "wirelessautomate_atm";

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos ROUTER = new BlockPos(1, 2, 1);
    private static final BlockPos STORAGE = new BlockPos(0, 1, 0);

    private AtmGameTests() {
    }

    /** Com o mod, a escada tem os oito degraus, e o roteador e o Baú sobem um de cada vez. */
    @GameTest(template = "empty")
    public static void fullLadderWithAllthemodium(GameTestHelper helper) {
        RouterTier[] tiers = RouterTier.values();
        for (int i = 0; i < tiers.length - 1; i++) {
            helper.assertTrue(tiers[i].loaded(), "tier não carregado: " + tiers[i]);
            helper.assertValueEqual(tiers[i].next(), tiers[i + 1], "depois de " + tiers[i]);
            helper.assertValueEqual(tiers[i + 1].previous(), tiers[i], "antes de " + tiers[i + 1]);
        }
        helper.setBlock(MACHINE, Blocks.FURNACE);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, RouterTier.EMERALD));
        helper.setBlock(STORAGE, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.EMERALD));
        BlockPos router = helper.absolutePos(ROUTER);
        BlockPos chest = helper.absolutePos(STORAGE);
        helper.assertFalse(RouterBlock.tryUpgrade(helper.getLevel(), router, RouterTier.ULTIMATE),
                "pulou os tiers do ATM");
        for (RouterTier target : List.of(RouterTier.ALLTHEMODIUM, RouterTier.VIBRANIUM, RouterTier.UNOBTAINIUM,
                RouterTier.ULTIMATE)) {
            helper.assertTrue(RouterBlock.tryUpgrade(helper.getLevel(), router, target), "roteador não subiu: " + target);
            helper.assertTrue(StorageBlock.tryUpgrade(helper.getLevel(), chest, target), "Baú não subiu: " + target);
        }
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ULTIMATE);
        helper.assertBlockProperty(STORAGE, RouterBlock.TIER, RouterTier.ULTIMATE);
        helper.succeed();
    }

    /**
     * As receitas dos cartões do ATM carregam e casam com os materiais do mod; o Ultimate do ATM10 pede o
     * fragmento de ATM Star e o Cartão Unobtainium, e as outras duas versões do Ultimate não carregam.
     */
    @GameTest(template = "empty")
    public static void allthemodiumRecipes(GameTestHelper helper) {
        Item atmIngot = item(helper, "allthemodium:allthemodium_ingot");
        Item atmBlock = item(helper, "allthemodium:allthemodium_block");
        assertCrafts(helper, "tier_core_allthemodium",
                grid(atmIngot, atmBlock, atmIngot, atmIngot, card(RouterTier.EMERALD), atmIngot,
                        atmIngot, atmBlock, atmIngot),
                card(RouterTier.ALLTHEMODIUM));

        Item vib = item(helper, "allthemodium:vibranium_ingot");
        Item vibAlloy = item(helper, "allthemodium:vibranium_allthemodium_alloy_ingot");
        Item vibBlock = item(helper, "allthemodium:vibranium_block");
        assertCrafts(helper, "tier_core_vibranium",
                grid(vib, vibAlloy, vib, vibBlock, card(RouterTier.ALLTHEMODIUM), vibBlock, vib, vibAlloy, vib),
                card(RouterTier.VIBRANIUM));

        Item unob = item(helper, "allthemodium:unobtainium_ingot");
        Item unobAlloy = item(helper, "allthemodium:unobtainium_vibranium_alloy_ingot");
        Item unobBlock = item(helper, "allthemodium:unobtainium_block");
        assertCrafts(helper, "tier_core_unobtainium",
                grid(unob, unobAlloy, unob, unobBlock, card(RouterTier.VIBRANIUM), unobBlock, unob, unobAlloy, unob),
                card(RouterTier.UNOBTAINIUM));

        Item shard = item(helper, "allthetweaks:atm_star_shard");
        Item alloyBlock = item(helper, "allthemodium:unobtainium_allthemodium_alloy_block");
        assertCrafts(helper, "tier_core_ultimate_atm_star",
                grid(shard, Items.DRAGON_EGG, shard, alloyBlock, card(RouterTier.UNOBTAINIUM), alloyBlock,
                        shard, alloyBlock, shard),
                card(RouterTier.ULTIMATE));

        for (String id : List.of("tier_core_ultimate", "tier_core_ultimate_atm")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(WirelessAutomate.id(id)).isEmpty(),
                    "outra versão do Ultimate carregou: " + id);
        }
        // Com o ATM, a Esmeralda não sobe direto para o Ultimate nem na bancada.
        Item echo = Items.ECHO_SHARD;
        Item netherite = Items.NETHERITE_BLOCK;
        helper.assertTrue(find(helper, grid(echo, Items.DRAGON_EGG, echo, netherite, card(RouterTier.EMERALD),
                netherite, echo, netherite, echo)).isEmpty(), "Ultimate vanilla com o ATM");
        helper.succeed();
    }

    private static Item card(RouterTier tier) {
        return ModItems.TIER_CORES.get(tier).get();
    }

    private static Item item(GameTestHelper helper, String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            throw new GameTestAssertException("item não existe: " + id);
        }
        return BuiltInRegistries.ITEM.get(key);
    }

    private static CraftingInput grid(Item... items) {
        return CraftingInput.of(3, 3, Arrays.stream(items).map(ItemStack::new).toList());
    }

    private static Optional<RecipeHolder<CraftingRecipe>> find(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }

    private static void assertCrafts(GameTestHelper helper, String id, CraftingInput input, Item result) {
        RecipeHolder<CraftingRecipe> holder = find(helper, input)
                .orElseThrow(() -> new GameTestAssertException("nenhuma receita para " + id));
        helper.assertValueEqual(holder.id(), WirelessAutomate.id(id), "receita");
        ItemStack out = holder.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(result) && out.getCount() == 1, "resultado de " + id + ": " + out);
    }
}
