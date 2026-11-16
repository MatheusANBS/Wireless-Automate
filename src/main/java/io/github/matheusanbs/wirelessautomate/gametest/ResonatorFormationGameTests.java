package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.BasePosition;
import io.github.matheusanbs.wirelessautomate.resonator.Corner;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorCasingBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorFormation;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorColumnBlock;
import io.github.matheusanbs.wirelessautomate.resonator.StackPart;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Etapa 2 do Ressonador de Sinal: os blocos da estrutura, montar e desmontar, o Núcleo com tier, as receitas e a config. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorFormationGameTests {
    /** As casas das peças na caixa que começa em {@code origin}, com a frente para o sul. */
    private static final java.util.function.UnaryOperator<BlockPos> ENERGY_PORT = origin -> origin.offset(4, 0, 2);
    private static final java.util.function.UnaryOperator<BlockPos> CRYSTAL_PORT = origin -> origin.offset(0, 0, 2);
    private static final java.util.function.UnaryOperator<BlockPos> TUNER = origin -> origin.offset(2, 0, 0);

    private ResonatorFormationGameTests() {
    }

    /** Sem a Porta de Cristais a Armilar não monta: depois de o Núcleo revalidar, tudo continua solto. */
    @GameTest(template = "empty7")
    public static void blocosNascemSoltos(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.ELITE);
        helper.setBlock(CRYSTAL_PORT.apply(origin), ModBlocks.RESONATOR_CASING.get());
        helper.startSequence().thenWaitUntil(() -> assertRevalidated(helper, core)).thenExecute(() -> {
            BlockState casing = helper.getBlockState(origin);
            helper.assertTrue(casing.is(ModBlocks.RESONATOR_CASING.get()), "laje no canto");
            helper.assertTrue(casing.getValue(ResonatorCasingBlock.POSITION) == BasePosition.LOOSE, "laje solta");
            assertColumn(helper, origin.offset(1, 1, 1), StackPart.LOOSE, Corner.SE);
            for (BlockPos pos : List.of(ENERGY_PORT.apply(origin), TUNER.apply(origin), core)) {
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
        BlockPos energy = ENERGY_PORT.apply(origin);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            assertCasing(helper, origin, BasePosition.P00);
            assertCasing(helper, origin.offset(4, 0, 4), BasePosition.P44);
            assertColumn(helper, origin.offset(3, 1, 3), StackPart.LOWER, Corner.SE);
            assertColumn(helper, origin.offset(1, 2, 1), StackPart.UPPER, Corner.NW);
            assertPart(helper, energy, Direction.EAST, true);
            assertPart(helper, CRYSTAL_PORT.apply(origin), Direction.WEST, true);
            assertPart(helper, TUNER.apply(origin), Direction.NORTH, true);
            assertPart(helper, core, Direction.SOUTH, true);
            helper.assertValueEqual(ResonatorTestSupport.core(helper, core).members().size(), 35, "membros da Armilar");
            helper.assertTrue(ResonatorPorts.core(helper.getLevel(), helper.absolutePos(energy))
                    == ResonatorTestSupport.core(helper, core), "a Porta de Energia acha o Núcleo");
        }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void quebrarUmaCarcacaDesmonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos energy = ENERGY_PORT.apply(origin);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true))
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(origin.offset(1, 0, 0)), false))
                .thenWaitUntil(() -> assertFormed(helper, core, false)).thenExecute(() -> {
                    assertCasing(helper, origin, BasePosition.LOOSE);
                    assertColumn(helper, origin.offset(1, 2, 1), StackPart.LOOSE, Corner.SE);
                    assertPart(helper, energy, Direction.EAST, false);
                    assertPart(helper, CRYSTAL_PORT.apply(origin), Direction.WEST, false);
                    assertPart(helper, TUNER.apply(origin), Direction.NORTH, false);
                    assertPart(helper, core, Direction.SOUTH, false);
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
        BlockPos energy = ENERGY_PORT.apply(origin);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            helper.getLevel().destroyBlock(helper.absolutePos(core), false);
            assertCasing(helper, origin.offset(1, 0, 0), BasePosition.LOOSE);
            assertColumn(helper, origin.offset(1, 2, 1), StackPart.LOOSE, Corner.SE);
            assertPart(helper, energy, Direction.EAST, false);
            assertPart(helper, CRYSTAL_PORT.apply(origin), Direction.WEST, false);
            assertPart(helper, TUNER.apply(origin), Direction.NORTH, false);
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

    /** Um pistão ligado contra a Armilar montada não a empurra: a Laje fica no lugar, montada. */
    @GameTest(template = "empty7")
    public static void pistaoNaoEmpurraACarcaca(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos edge = origin.offset(1, 0, 0);
        BlockPos piston = edge.north();
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.SOUTH));
            helper.setBlock(piston.west(), Blocks.REDSTONE_BLOCK);
        }).thenIdle(10).thenExecute(() -> {
            assertCasing(helper, edge, BasePosition.P10);
            helper.assertTrue(helper.getBlockState(edge.south(4)).is(ModBlocks.RESONATOR_CASING.get()),
                    "a fila de Lajes andou");
            helper.assertTrue(helper.getBlockState(edge.south(5)).isAir(), "uma Laje saiu da caixa");
            assertFormed(helper, core, true);
        }).thenSucceed();
    }

    /** Revalidar a estrutura intacta (como na carga do chunk) não marca o chunk para gravar. */
    @GameTest(template = "empty7")
    public static void revalidarIntactoNaoSujaOChunk(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(core));
            chunk.setUnsaved(false);
            ResonatorFormation.revalidate(helper.getLevel(), ResonatorTestSupport.core(helper, core));
            assertFormed(helper, core, true);
            helper.assertFalse(chunk.isUnsaved(), "a revalidação sem mudança marcou o chunk");
        }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void repararRemonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos edge = origin.offset(1, 0, 0);
        double[] station = new double[1];
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            station[0] = ResonatorTestSupport.core(helper, core).station();
            helper.getLevel().destroyBlock(helper.absolutePos(edge), false);
        }).thenWaitUntil(() -> assertFormed(helper, core, false))
                .thenExecute(() -> helper.setBlock(edge, ModBlocks.RESONATOR_CASING.get()))
                .thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
                    assertCasing(helper, edge, BasePosition.P10);
                    helper.assertValueEqual(ResonatorTestSupport.core(helper, core).station(), station[0],
                            "remontar mantém a estação");
                }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void semPortaDeCristaisNaoMonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        helper.setBlock(CRYSTAL_PORT.apply(origin), ModBlocks.RESONATOR_CASING.get());
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
        double[] station = new double[1];
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            station[0] = ResonatorTestSupport.core(helper, core).station();
            helper.assertTrue(station[0] >= 88.5 && station[0] <= 107.5, "estação fora da faixa: " + station[0]);
            ResonatorFormation.revalidate(helper.getLevel(), ResonatorTestSupport.core(helper, core));
            helper.assertValueEqual(ResonatorTestSupport.core(helper, core).station(), station[0],
                    "a estação não muda numa segunda revalidação");
        }).thenSucceed();
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

    private static void assertCasing(GameTestHelper helper, BlockPos pos, BasePosition expected) {
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.is(ModBlocks.RESONATOR_CASING.get()) && state.getValue(ResonatorCasingBlock.POSITION) == expected,
                "laje em " + pos + ": " + state + ", esperado " + expected);
    }

    private static void assertColumn(GameTestHelper helper, BlockPos pos, StackPart part, Corner corner) {
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.is(ModBlocks.RESONATOR_COLUMN.get()) && state.getValue(ResonatorColumnBlock.PART) == part
                && (part == StackPart.LOOSE || state.getValue(ResonatorColumnBlock.CORNER) == corner),
                "coluna em " + pos + ": " + state + ", esperado " + part + " " + corner);
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
        assertCrafts(helper, "resonator_casing", grid(terracotta, iron, terracotta, iron, crystal, iron,
                terracotta, iron, terracotta), casing, 4);
        assertCrafts(helper, "resonance_core", grid(casing, casing, casing, casing, cut, casing, casing,
                ModItems.RESONATOR_HEARTS.get(RouterTier.BASIC).get(), casing), ModItems.RESONANCE_CORE.get(), 1);
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
        helper.assertValueEqual(Config.resonatorFePerCrystal(RouterTier.BASIC), 216L, "FE por cristal, Básico");
        helper.assertValueEqual(Config.resonatorFePerCrystal(RouterTier.ULTIMATE), 268_435_456L,
                "FE por cristal, Ultimate");
        helper.assertValueEqual(Config.resonatorFePerCrystal(RouterTier.UNOBTAINIUM), 10_700_000L,
                "FE por cristal, Unobtainium");
        helper.assertValueEqual(Config.resonatorFePerCrystal(RouterTier.ELITE), 10_000L, "FE por cristal, Elite");
        helper.assertValueEqual(Config.crystalLifeTicks(false), 12_000L, "vida do Cristal de Sinal");
        helper.assertValueEqual(Config.crystalLifeTicks(true), 24_000L, "vida do Lapidado");
        helper.assertValueEqual(Config.rechargeTicks(), 600L, "recarga");
        helper.succeed();
    }
}
