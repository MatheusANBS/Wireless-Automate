package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CasingPosition;
import io.github.matheusanbs.wirelessautomate.resonator.GlassFace;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorCasingBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorGlassBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Etapa 2 do Ressonador de Sinal: os blocos da estrutura, o Núcleo com tier, as receitas e a config. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorFormationGameTests {
    private ResonatorFormationGameTests() {
    }

    @GameTest(template = "empty7")
    public static void blocosNascemSoltos(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.ELITE);
        BlockState casing = helper.getBlockState(origin);
        helper.assertTrue(casing.is(ModBlocks.RESONATOR_CASING.get()), "carcaça no canto");
        helper.assertTrue(casing.getValue(ResonatorCasingBlock.POSITION) == CasingPosition.LOOSE, "carcaça solta");
        BlockState glass = helper.getBlockState(origin.offset(2, 2, 4));
        helper.assertTrue(glass.is(ModBlocks.RESONATOR_GLASS.get()), "vidro na janela");
        helper.assertTrue(glass.getValue(ResonatorGlassBlock.FACE) == GlassFace.LOOSE, "vidro solto");
        for (BlockPos pos : List.of(origin.offset(0, 2, 2), origin.offset(2, 2, 0), origin.offset(2, 4, 2), core)) {
            helper.assertTrue(!helper.getBlockState(pos).getValue(ResonatorPartBlock.FORMED), "peça solta em " + pos);
        }
        helper.assertTrue(helper.getBlockState(core).getValue(RouterBlock.TIER) == RouterTier.ELITE, "tier do Núcleo");
        helper.assertTrue(ResonatorTestSupport.core(helper, core).tier() == RouterTier.ELITE, "tier pela entidade");
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void nucleoQuebradoVoltaComOTier(GameTestHelper helper) {
        BlockPos rel = new BlockPos(3, 3, 3);
        helper.setBlock(rel, ModBlocks.RESONANCE_CORE.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.EMERALD));
        BlockState state = helper.getBlockState(rel);
        List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(rel), null, null,
                new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1, "um drop: " + drops);
        ItemStack drop = drops.get(0);
        helper.assertTrue(drop.is(ModItems.RESONANCE_CORE.get()), "o item do Núcleo: " + drop);
        helper.assertTrue(ResonanceCoreItem.tierOf(drop) == RouterTier.EMERALD, "o tier volta no item");
        helper.succeed();
    }

    private static CraftingInput grid(ItemLike... items) {
        List<ItemStack> stacks = new ArrayList<>(9);
        for (ItemLike item : items) {
            stacks.add(item == null ? ItemStack.EMPTY : new ItemStack(item));
        }
        while (stacks.size() < 9) {
            stacks.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, stacks);
    }

    private static void assertCrafts(GameTestHelper helper, String id, CraftingInput input, Item result, int count) {
        RecipeHolder<CraftingRecipe> holder = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new GameTestAssertException("nenhuma receita para " + id));
        helper.assertValueEqual(holder.id(), WirelessAutomate.id(id), "receita de " + id);
        ItemStack out = holder.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(result), "resultado de " + id + ": " + out);
        helper.assertValueEqual(out.getCount(), count, "quantidade de " + id);
    }

    @GameTest(template = "empty")
    public static void receitasDoRessonador(GameTestHelper helper) {
        Item casing = ModItems.RESONATOR_CASING.get();
        Item crystal = ModItems.SIGNAL_CRYSTAL.get();
        Item cut = ModItems.CUT_SIGNAL_CRYSTAL.get();
        Item terracotta = Items.WHITE_TERRACOTTA;
        Item iron = Items.IRON_INGOT;
        Item glass = Items.GLASS;
        assertCrafts(helper, "resonator_casing", grid(terracotta, iron, terracotta, iron, crystal, iron,
                terracotta, iron, terracotta), casing, 4);
        assertCrafts(helper, "resonator_glass", grid(null, glass, null, glass, crystal, glass, null, glass, null),
                ModItems.RESONATOR_GLASS.get(), 4);
        assertCrafts(helper, "resonance_core", grid(casing, casing, casing, casing, cut, casing, casing,
                Items.DIAMOND, casing), ModItems.RESONANCE_CORE.get(), 1);
        assertCrafts(helper, "resonator_energy_port", grid(casing, Items.REDSTONE_BLOCK),
                ModItems.RESONATOR_ENERGY_PORT.get(), 1);
        assertCrafts(helper, "resonator_crystal_port", grid(casing, Items.HOPPER),
                ModItems.RESONATOR_CRYSTAL_PORT.get(), 1);
        assertCrafts(helper, "resonator_tuner", grid(casing, Items.CLOCK, Items.COMPARATOR),
                ModItems.RESONATOR_TUNER.get(), 1);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "wirelessautomate_config")
    public static void configDoRessonadorTemOsPadroes(GameTestHelper helper) {
        helper.assertValueEqual(Config.resonatorFePerCrystal(RouterTier.BASIC), 64L, "FE por cristal, Básico");
        helper.assertValueEqual(Config.resonatorFePerCrystal(RouterTier.ULTIMATE), 134_217_728L, "FE por cristal, Ultimate");
        helper.assertValueEqual(Config.crystalLifeTicks(false), 12_000L, "vida do Cristal de Sinal");
        helper.assertValueEqual(Config.crystalLifeTicks(true), 24_000L, "vida do Lapidado");
        helper.assertValueEqual(Config.rechargeTicks(), 600L, "recarga");
        helper.succeed();
    }
}
