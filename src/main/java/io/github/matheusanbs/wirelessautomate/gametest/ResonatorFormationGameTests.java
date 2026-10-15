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
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorCasingBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorGlassBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPorts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Etapa 2 do Ressonador de Sinal: os blocos da estrutura, montar e desmontar, o Núcleo com tier, as receitas e a config. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorFormationGameTests {
    private ResonatorFormationGameTests() {
    }

    /** Sem a Porta de Cristais a Vitrine não monta: depois de o Núcleo revalidar, tudo continua solto. */
    @GameTest(template = "empty7")
    public static void blocosNascemSoltos(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.ELITE);
        helper.setBlock(origin.offset(2, 2, 0), ModBlocks.RESONATOR_CASING.get());
        helper.startSequence().thenWaitUntil(() -> assertRevalidated(helper, core)).thenExecute(() -> {
            BlockState casing = helper.getBlockState(origin);
            helper.assertTrue(casing.is(ModBlocks.RESONATOR_CASING.get()), "carcaça no canto");
            helper.assertTrue(casing.getValue(ResonatorCasingBlock.POSITION) == CasingPosition.LOOSE, "carcaça solta");
            BlockState glass = helper.getBlockState(origin.offset(2, 2, 4));
            helper.assertTrue(glass.is(ModBlocks.RESONATOR_GLASS.get()), "vidro na janela");
            helper.assertTrue(glass.getValue(ResonatorGlassBlock.FACE) == GlassFace.LOOSE, "vidro solto");
            for (BlockPos pos : List.of(origin.offset(0, 2, 2), origin.offset(2, 4, 2), core)) {
                helper.assertTrue(!helper.getBlockState(pos).getValue(ResonatorPartBlock.FORMED), "peça solta em " + pos);
            }
            helper.assertTrue(helper.getBlockState(core).getValue(RouterBlock.TIER) == RouterTier.ELITE, "tier do Núcleo");
            helper.assertTrue(ResonatorTestSupport.core(helper, core).tier() == RouterTier.ELITE, "tier pela entidade");
        }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void vitrineMonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos energy = origin.offset(0, 2, 2);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            assertCasing(helper, origin.offset(2, 0, 0), CasingPosition.CNN);
            assertCasing(helper, origin.offset(1, 4, 1), CasingPosition.CPC);
            BlockState glass = helper.getBlockState(origin.offset(2, 2, 4));
            helper.assertTrue(glass.getValue(ResonatorGlassBlock.FACE) == GlassFace.SOUTH, "vidro na face sul: " + glass);
            helper.assertValueEqual(glass.getValue(ResonatorGlassBlock.CONNECTIONS), 15, "vidro do centro da janela");
            assertPart(helper, energy, Direction.WEST, true);
            assertPart(helper, origin.offset(2, 2, 0), Direction.NORTH, true);
            assertPart(helper, origin.offset(2, 4, 2), Direction.UP, true);
            assertPart(helper, core, Direction.EAST, true);
            helper.assertValueEqual(ResonatorTestSupport.core(helper, core).members().size(), 98, "membros da casca");
            helper.assertTrue(ResonatorPorts.core(helper.getLevel(), helper.absolutePos(energy))
                    == ResonatorTestSupport.core(helper, core), "a Porta de Energia acha o Núcleo");
        }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void quebrarUmaCarcacaDesmonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos energy = origin.offset(0, 2, 2);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true))
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(origin.offset(2, 0, 0)), false))
                .thenWaitUntil(() -> assertFormed(helper, core, false)).thenExecute(() -> {
                    assertCasing(helper, origin, CasingPosition.LOOSE);
                    assertCasing(helper, origin.offset(1, 4, 1), CasingPosition.LOOSE);
                    BlockState glass = helper.getBlockState(origin.offset(2, 2, 4));
                    helper.assertTrue(glass.getValue(ResonatorGlassBlock.FACE) == GlassFace.LOOSE, "vidro solto");
                    helper.assertValueEqual(glass.getValue(ResonatorGlassBlock.CONNECTIONS), 0, "vidro sem conexões");
                    assertPart(helper, energy, Direction.WEST, false);
                    assertPart(helper, origin.offset(2, 2, 0), Direction.NORTH, false);
                    assertPart(helper, origin.offset(2, 4, 2), Direction.UP, false);
                    assertPart(helper, core, Direction.EAST, false);
                    helper.assertTrue(ResonatorPorts.core(helper.getLevel(), helper.absolutePos(energy)) == null,
                            "a Porta de Energia não acha mais o Núcleo");
                    helper.assertTrue(ResonatorPorts.registered(helper.getLevel().dimension(), helper.absolutePos(energy))
                            == null, "a Porta de Energia sai do índice");
                }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void quebrarONucleoDesmonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos energy = origin.offset(0, 2, 2);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            helper.getLevel().destroyBlock(helper.absolutePos(core), false);
            assertCasing(helper, origin.offset(2, 0, 0), CasingPosition.LOOSE);
            assertCasing(helper, origin.offset(1, 4, 1), CasingPosition.LOOSE);
            BlockState glass = helper.getBlockState(origin.offset(2, 2, 4));
            helper.assertTrue(glass.getValue(ResonatorGlassBlock.FACE) == GlassFace.LOOSE, "vidro solto");
            assertPart(helper, energy, Direction.WEST, false);
            assertPart(helper, origin.offset(2, 2, 0), Direction.NORTH, false);
            assertPart(helper, origin.offset(2, 4, 2), Direction.UP, false);
            helper.assertTrue(ResonatorPorts.registered(helper.getLevel().dimension(), helper.absolutePos(energy)) == null,
                    "a Porta de Energia sai do índice");
        }).thenSucceed();
    }

    /** O Núcleo que soltou a Porta não tira o registro que outro Núcleo fez depois. */
    @GameTest(template = "empty")
    public static void unregisterNaoTiraAPortaDeOutroNucleo(GameTestHelper helper) {
        var dimension = helper.getLevel().dimension();
        BlockPos port = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos first = helper.absolutePos(new BlockPos(0, 1, 1));
        BlockPos second = helper.absolutePos(new BlockPos(2, 1, 1));
        try {
            ResonatorPorts.register(dimension, port, first);
            ResonatorPorts.register(dimension, port, second);
            ResonatorPorts.unregister(dimension, port, first);
            helper.assertTrue(second.equals(ResonatorPorts.registered(dimension, port)), "a Porta continua do segundo Núcleo");
            ResonatorPorts.unregister(dimension, port, second);
            helper.assertTrue(ResonatorPorts.registered(dimension, port) == null, "o dono tira a Porta");
        } finally {
            ResonatorPorts.unregister(dimension, port, second);
        }
        helper.succeed();
    }

    /** Um pistão ligado contra a casca montada não a empurra: a Carcaça fica no lugar, montada. */
    @GameTest(template = "empty7")
    public static void pistaoNaoEmpurraACarcaca(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos edge = origin.offset(2, 0, 0);
        BlockPos piston = edge.north();
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.SOUTH));
            helper.setBlock(piston.west(), Blocks.REDSTONE_BLOCK);
        }).thenIdle(10).thenExecute(() -> {
            assertCasing(helper, edge, CasingPosition.CNN);
            helper.assertTrue(helper.getBlockState(edge.south(4)).is(ModBlocks.RESONATOR_CASING.get()),
                    "a fila de Carcaças andou");
            helper.assertTrue(helper.getBlockState(edge.south(5)).isAir(), "uma Carcaça saiu da caixa");
            assertFormed(helper, core, true);
        }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void repararRemonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos edge = origin.offset(2, 0, 0);
        double[] station = new double[1];
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            station[0] = ResonatorTestSupport.core(helper, core).station();
            helper.getLevel().destroyBlock(helper.absolutePos(edge), false);
        }).thenWaitUntil(() -> assertFormed(helper, core, false))
                .thenExecute(() -> helper.setBlock(edge, ModBlocks.RESONATOR_CASING.get()))
                .thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
                    assertCasing(helper, edge, CasingPosition.CNN);
                    helper.assertValueEqual(ResonatorTestSupport.core(helper, core).station(), station[0],
                            "remontar mantém a estação");
                }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void semPortaDeCristaisNaoMonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        helper.setBlock(origin.offset(2, 2, 0), ModBlocks.RESONATOR_CASING.get());
        helper.startSequence().thenWaitUntil(() -> assertRevalidated(helper, core)).thenExecute(() -> {
            ResonanceCoreBlockEntity entity = ResonatorTestSupport.core(helper, core);
            helper.assertTrue(entity.lastProblem().problem() == ResonatorLayout.Problem.MISSING_CRYSTAL_PORT,
                    "problema: " + entity.lastProblem());
            helper.assertTrue(!entity.formed(), "não montou");
        }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void estacaoNasceNaFaixa(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos field = origin.offset(3, 4, 3);
        double[] station = new double[1];
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            station[0] = ResonatorTestSupport.core(helper, core).station();
            helper.assertTrue(station[0] >= 88.5 && station[0] <= 107.5, "estação fora da faixa: " + station[0]);
            helper.setBlock(field, ModBlocks.RESONATOR_GLASS.get());
        }).thenWaitUntil(() -> helper.assertTrue(
                helper.getBlockState(field).getValue(ResonatorGlassBlock.FACE) == GlassFace.UP, "a segunda revalidação"))
                .thenExecute(() -> helper.assertValueEqual(ResonatorTestSupport.core(helper, core).station(), station[0],
                        "a estação não muda numa segunda revalidação")).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void vidroNovoNaFaceRegravaAsConexoes(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos field = origin.offset(1, 4, 1);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true))
                .thenExecute(() -> helper.setBlock(field, ModBlocks.RESONATOR_GLASS.get()))
                .thenWaitUntil(() -> {
                    BlockState glass = helper.getBlockState(field);
                    helper.assertTrue(glass.getValue(ResonatorGlassBlock.FACE) == GlassFace.UP, "vidro novo: " + glass);
                }).thenExecute(() -> assertFormed(helper, core, true)).thenSucceed();
    }

    private static void assertRevalidated(GameTestHelper helper, BlockPos core) {
        ResonanceCoreBlockEntity entity = ResonatorTestSupport.core(helper, core);
        helper.assertTrue(entity != null && entity.lastProblem() != null, "o Núcleo ainda não revalidou");
    }

    private static void assertFormed(GameTestHelper helper, BlockPos core, boolean formed) {
        ResonanceCoreBlockEntity entity = ResonatorTestSupport.core(helper, core);
        helper.assertTrue(entity != null && entity.formed() == formed, (formed ? "não montou" : "não desmontou")
                + (entity == null ? "" : ": " + entity.lastProblem()));
    }

    private static void assertCasing(GameTestHelper helper, BlockPos pos, CasingPosition expected) {
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.is(ModBlocks.RESONATOR_CASING.get()) && state.getValue(ResonatorCasingBlock.POSITION) == expected,
                "carcaça em " + pos + ": " + state + ", esperado " + expected);
    }

    private static void assertPart(GameTestHelper helper, BlockPos pos, Direction facing, boolean formed) {
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.getValue(ResonatorPartBlock.FACING) == facing
                && state.getValue(ResonatorPartBlock.FORMED) == formed, "peça em " + pos + ": " + state);
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
