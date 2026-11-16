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
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorCradleBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.StackPart;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPorts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
    private static final UnaryOperator<BlockPos> ENERGY_PORT = origin -> origin.offset(4, 0, 2);
    private static final UnaryOperator<BlockPos> CRYSTAL_PORT = origin -> origin.offset(0, 0, 2);
    private static final UnaryOperator<BlockPos> TUNER = origin -> origin.offset(2, 0, 0);

    private ResonatorFormationGameTests() {
    }

    /** Sem a Porta de Cristais a Armilar não monta: depois de o Núcleo revalidar, tudo continua solto. */
    @GameTest(template = "empty7")
    public static void blocosNascemSoltos(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.ARMILAR, RouterTier.ELITE);
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

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void armilarMontaDeFrenteParaOSul(GameTestHelper helper) {
        montaNaFrente(helper, Face.SOUTH, Layout.ARMILAR);
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void armilarMontaDeFrenteParaOOeste(GameTestHelper helper) {
        montaNaFrente(helper, Face.WEST, Layout.ARMILAR);
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void armilarMontaDeFrenteParaONorte(GameTestHelper helper) {
        montaNaFrente(helper, Face.NORTH, Layout.ARMILAR);
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void armilarMontaDeFrenteParaOLeste(GameTestHelper helper) {
        montaNaFrente(helper, Face.EAST, Layout.ARMILAR);
    }

    /** O Sintonizador é opcional: a Laje na casa dele monta do mesmo jeito, ainda com 35 membros. */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void armilarSemSintonizadorMonta(GameTestHelper helper) {
        montaNaFrente(helper, Face.SOUTH, Layout.SEM_SINTONIZADOR);
    }

    /**
     * Monta a Armilar de frente {@code front} e confere os estados de todas as 125 casas, com o giro pela tabela da
     * spec (o deslocamento local a partir do centro, levado ao mundo), sem passar pela regra.
     */
    private static void montaNaFrente(GameTestHelper helper, Face front, Layout layout) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, front, layout, RouterTier.BASIC);
        Direction frontDirection = ResonatorTestSupport.direction(front, Face.SOUTH);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true)).thenExecute(() -> {
            for (int lx = 0; lx < 5; lx++) {
                for (int ly = 0; ly < 5; ly++) {
                    for (int lz = 0; lz < 5; lz++) {
                        int[] rotated = rotate(front, lx - 2, lz - 2);
                        BlockPos pos = origin.offset(2 + rotated[0], ly, 2 + rotated[1]);
                        assertCell(helper, pos, front, layout, lx, ly, lz);
                    }
                }
            }
            int[] coreCell = rotate(front, 0, 2);
            helper.assertTrue(core.equals(origin.offset(2 + coreCell[0], 0, 2 + coreCell[1])),
                    "o Núcleo na casa (2, 0, 4) girada");
            assertPart(helper, core, frontDirection, true);
            helper.assertValueEqual(ResonatorTestSupport.core(helper, core).members().size(), 35, "membros da Armilar");
            helper.assertTrue(ResonatorTestSupport.core(helper, core).hasTuner() == (layout == Layout.ARMILAR),
                    "o Sintonizador conta só quando há um");
        }).thenSucceed();
    }

    /** O deslocamento local (dx, dz) levado ao mundo pela frente: a tabela do brief, escrita à parte da regra. */
    private static int[] rotate(Face front, int dx, int dz) {
        return switch (front) {
            case SOUTH -> new int[] {dx, dz};
            case WEST -> new int[] {-dz, dx};
            case NORTH -> new int[] {-dx, -dz};
            case EAST -> new int[] {dz, -dx};
            default -> throw new IllegalArgumentException(front.name());
        };
    }

    private static Direction outward(Face front, int dx, int dz) {
        int[] r = rotate(front, dx, dz);
        return r[0] > 0 ? Direction.EAST : r[0] < 0 ? Direction.WEST : r[1] > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void assertCell(GameTestHelper helper, BlockPos pos, Face front, Layout layout, int lx, int ly,
            int lz) {
        BlockState state = helper.getBlockState(pos);
        switch (ResonatorLayout.expected(lx, ly, lz)) {
            case CASING -> {
                helper.assertTrue(state.is(ModBlocks.RESONATOR_CASING.get()), "laje em " + pos + ": " + state);
                helper.assertValueEqual(state.getValue(ResonatorCasingBlock.POSITION),
                        BasePosition.byName("p" + lx + lz), "posição da laje (" + lx + ", " + lz + ")");
                helper.assertValueEqual(state.getValue(ResonatorCasingBlock.FACING),
                        ResonatorTestSupport.direction(front, Face.SOUTH), "frente da laje");
            }
            case COLUMN -> {
                int[] r = rotate(front, lx - 2, lz - 2);
                Corner corner = r[0] > 0 ? (r[1] > 0 ? Corner.SE : Corner.NE) : (r[1] > 0 ? Corner.SW : Corner.NW);
                assertColumn(helper, pos, ly == 1 ? StackPart.LOWER : StackPart.UPPER, corner);
            }
            case CRADLE -> helper.assertTrue(state.is(ModBlocks.RESONATOR_CRADLE.get())
                    && state.getValue(ResonatorCradleBlock.PART) == (ly == 1 ? StackPart.LOWER : StackPart.UPPER),
                    "berço em " + pos + ": " + state);
            case CORE -> assertPart(helper, pos, ResonatorTestSupport.direction(front, Face.SOUTH), true);
            case ENERGY_PORT -> assertPart(helper, pos, outward(front, 2, 0), true);
            case CRYSTAL_PORT -> assertPart(helper, pos, outward(front, -2, 0), true);
            case TUNER -> {
                if (layout == Layout.ARMILAR) {
                    assertPart(helper, pos, outward(front, 0, -2), true);
                } else {
                    helper.assertValueEqual(state.getValue(ResonatorCasingBlock.POSITION), BasePosition.P20,
                            "Laje no lugar do Sintonizador");
                }
            }
            default -> helper.assertTrue(state.isAir(), "ar em " + pos + ": " + state);
        }
    }

    /** Um bloco numa casa que devia ser ar: não monta e o problema aponta a casa. */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void blocoNoArNaoMonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.ARMILAR, RouterTier.BASIC);
        BlockPos dirt = origin.offset(1, 1, 2);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.startSequence().thenWaitUntil(() -> assertRevalidated(helper, core)).thenExecute(() -> {
            ResonanceCoreBlockEntity entity = ResonatorTestSupport.core(helper, core);
            helper.assertTrue(!entity.formed(), "montou com terra no ar");
            ResonatorLayout.Broken problem = entity.lastProblem();
            helper.assertTrue(problem.problem() == ResonatorLayout.Problem.INTERIOR_NOT_EMPTY, "problema: " + problem);
            BlockPos at = helper.absolutePos(dirt);
            helper.assertTrue(problem.x() == at.getX() && problem.y() == at.getY() && problem.z() == at.getZ(),
                    "a posição do problema é a da terra: " + problem);
        }).thenSucceed();
    }

    /** Quebrar uma Coluna desmonta: tudo volta a solto e o Núcleo guarda o tier. */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void quebrarUmaColunaDesmonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.ARMILAR, RouterTier.ELITE);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, core, true))
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(origin.offset(3, 2, 3)), false))
                .thenWaitUntil(() -> assertFormed(helper, core, false)).thenExecute(() -> {
                    assertColumn(helper, origin.offset(1, 1, 1), StackPart.LOOSE, Corner.SE);
                    assertColumn(helper, origin.offset(3, 1, 3), StackPart.LOOSE, Corner.SE);
                    helper.assertTrue(helper.getBlockState(origin.offset(2, 1, 2)).getValue(ResonatorCradleBlock.PART)
                            == StackPart.LOOSE, "berço solto");
                    assertCasing(helper, origin, BasePosition.LOOSE);
                    assertPart(helper, ResonatorTestSupport.ENERGY_PORT, Direction.EAST, false);
                    assertPart(helper, core, Direction.SOUTH, false);
                    helper.assertTrue(ResonatorTestSupport.core(helper, core).tier() == RouterTier.ELITE,
                            "tier do Núcleo");
                }).thenSucceed();
    }

    /**
     * Um mundo antigo: a caixa 5×5×5 de Lajes soltas em volta de um Núcleo, como o Ressonador de antes da Armilar.
     * Não monta, e o Núcleo guarda o tier e os itens.
     */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void mundoAntigoNaoMontaEGuardaOsItens(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    if (x == 1 || x == 5 || y == 1 || y == 5 || z == 1 || z == 5) {
                        helper.setBlock(new BlockPos(x, y, z), ModBlocks.RESONATOR_CASING.get());
                    }
                }
            }
        }
        BlockPos core = new BlockPos(3, 3, 1);
        helper.setBlock(core, ModBlocks.RESONANCE_CORE.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ELITE).setValue(ResonatorPartBlock.FACING, Direction.NORTH));
        ResonanceCoreBlockEntity entity = ResonatorTestSupport.core(helper, core);
        ResonatorTestSupport.assertInserted(helper, entity, ResonatorTestSupport.crystal(false, 500));
        entity.addRedstone(7);
        helper.startSequence().thenWaitUntil(() -> assertRevalidated(helper, core)).thenIdle(5).thenExecute(() -> {
            ResonanceCoreBlockEntity after = ResonatorTestSupport.core(helper, core);
            helper.assertTrue(!after.formed(), "o mundo antigo montou: " + after.lastProblem());
            helper.assertTrue(after.tier() == RouterTier.ELITE, "o Núcleo perdeu o tier");
            helper.assertTrue(!after.crystals().get(0).isEmpty(), "o Núcleo perdeu o cristal");
            helper.assertValueEqual(after.redstone(), 7, "a redstone do Núcleo");
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).getValue(ResonatorCasingBlock.POSITION)
                    == BasePosition.LOOSE, "a Laje do chão antigo segue solta");
        }).thenSucceed();
    }

    /** O nome antigo do vidro do Ressonador vale a Laje, no bloco e no item. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void vidroAntigoViraLaje(GameTestHelper helper) {
        ResourceLocation old = ResourceLocation.parse("wirelessautomate:resonator_glass");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(old) == ModBlocks.RESONATOR_CASING.get(),
                "o bloco antigo não vira a Laje");
        helper.assertTrue(BuiltInRegistries.ITEM.get(old) == ModItems.RESONATOR_CASING.get(),
                "o item antigo não vira a Laje");
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void quebrarUmaLajeDesmonta(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.ARMILAR, RouterTier.BASIC);
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
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.ARMILAR, RouterTier.BASIC);
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
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.ARMILAR, RouterTier.BASIC);
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
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.ARMILAR, RouterTier.BASIC);
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
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.ARMILAR, RouterTier.BASIC);
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
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.ARMILAR, RouterTier.BASIC);
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
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.ARMILAR, RouterTier.BASIC);
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
