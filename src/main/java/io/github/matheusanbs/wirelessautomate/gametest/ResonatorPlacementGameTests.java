package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A frente do Núcleo, das Portas e do Sintonizador quando o jogador os coloca: para fora da parede ou para ele. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorPlacementGameTests {
    /** Núcleo solto virado para o norte: a caixa vai de (1, 1, 1) a (5, 5, 5). */
    private static final BlockPos CORE = new BlockPos(3, 3, 1);

    private ResonatorPlacementGameTests() {
    }

    @GameTest(template = "empty7")
    public static void portaNaParedeOlhaParaFora(GameTestHelper helper) {
        looseCore(helper, Direction.NORTH);
        BlockPos support = new BlockPos(5, 1, 3);
        Player player = player(helper, new Vec3(3.5, 4, 8), 180, 30);
        BlockState state = place(helper, player, ModBlocks.RESONATOR_ENERGY_PORT.get(), support);
        assertFacing(helper, state, Direction.EAST, "Porta na parede leste");
        helper.succeed();
    }

    /** O caminho real: o item na mão e o {@code useOn} do {@code BlockItem}, clicando em cima de um bloco. */
    @GameTest(template = "empty7")
    public static void portaPostaPeloItemOlhaParaFora(GameTestHelper helper) {
        looseCore(helper, Direction.NORTH);
        BlockPos support = new BlockPos(5, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        Player player = player(helper, new Vec3(3.5, 4, 8), 180, 30);
        ItemStack stack = new ItemStack(ModBlocks.RESONATOR_CRYSTAL_PORT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hitTop(helper, support)));
        BlockState placed = helper.getBlockState(support.above());
        helper.assertTrue(placed.is(ModBlocks.RESONATOR_CRYSTAL_PORT.get()), "a Porta foi posta: " + placed);
        assertFacing(helper, placed, Direction.EAST, "Porta posta pelo item");
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void sintonizadorNaArestaOlhaParaOJogador(GameTestHelper helper) {
        looseCore(helper, Direction.NORTH);
        BlockPos support = new BlockPos(5, 1, 1);
        Block tuner = ModBlocks.RESONATOR_TUNER.get();
        Player east = player(helper, new Vec3(10.5, 2, -0.5), 90, 0);
        assertFacing(helper, place(helper, east, tuner, support), Direction.EAST, "jogador mais para o leste");
        Player north = player(helper, new Vec3(7.5, 2, -3.5), 180, 0);
        assertFacing(helper, place(helper, north, tuner, support), Direction.NORTH, "jogador mais para o norte");
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void semNucleoOlhaParaOJogador(GameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        Block port = ModBlocks.RESONATOR_ENERGY_PORT.get();
        Player south = player(helper, new Vec3(3.5, 2, 7.5), 180, 10);
        assertFacing(helper, place(helper, south, port, support), Direction.SOUTH, "olhando para o norte");
        Player down = player(helper, new Vec3(3.5, 4, 3.5), 180, 90);
        assertFacing(helper, place(helper, down, port, support), Direction.UP, "olhando para baixo");
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void nucleoOlhaParaOJogador(GameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        Player west = player(helper, new Vec3(-1.5, 2.5, 3.5), -90, 0);
        BlockState state = place(helper, west, ModBlocks.RESONANCE_CORE.get(), support);
        assertFacing(helper, state, Direction.WEST, "Núcleo de frente para o jogador");
        helper.succeed();
    }

    /** As peças postas com a frente errada: montada, a estrutura vira cada uma para fora da sua parede. */
    @GameTest(template = "empty7")
    public static void montarContinuaVirandoParaFora(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        BlockPos core = ResonatorTestSupport.build(helper, origin, Layout.VITRINE, RouterTier.BASIC);
        BlockPos energy = origin.offset(0, 2, 2);
        BlockPos crystal = origin.offset(2, 2, 0);
        BlockPos tuner = origin.offset(2, 4, 2);
        for (BlockPos pos : new BlockPos[] {core, energy, crystal, tuner}) {
            helper.setBlock(pos, helper.getBlockState(pos).setValue(ResonatorPartBlock.FACING, Direction.SOUTH));
        }
        helper.startSequence().thenWaitUntil(() -> {
            ResonanceCoreBlockEntity entity = ResonatorTestSupport.core(helper, core);
            helper.assertTrue(entity != null && entity.formed(), "a estrutura montou");
        }).thenExecute(() -> {
            assertFacing(helper, helper.getBlockState(core), Direction.EAST, "Núcleo montado");
            assertFacing(helper, helper.getBlockState(energy), Direction.WEST, "Porta de Energia montada");
            assertFacing(helper, helper.getBlockState(crystal), Direction.NORTH, "Porta de Cristais montada");
            assertFacing(helper, helper.getBlockState(tuner), Direction.UP, "Sintonizador montado");
        }).thenSucceed();
    }

    private static void looseCore(GameTestHelper helper, Direction facing) {
        helper.setBlock(CORE, ModBlocks.RESONANCE_CORE.get().defaultBlockState().setValue(ResonatorPartBlock.FACING, facing));
    }

    /** Um jogador com o olho em {@code eye} (relativo ao teste), virado por {@code yaw} e {@code pitch} do Minecraft. */
    private static Player player(GameTestHelper helper, Vec3 eye, float yaw, float pitch) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 abs = helper.absoluteVec(eye);
        player.setPos(abs.x, abs.y - player.getEyeHeight(), abs.z);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        return player;
    }

    /** O estado que o bloco escolhe ao ser posto em cima de {@code support} (um bloco de pedra que o teste põe). */
    private static BlockState place(GameTestHelper helper, Player player, Block block, BlockPos support) {
        helper.setBlock(support, Blocks.STONE);
        ItemStack stack = new ItemStack(block);
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hitTop(helper, support));
        helper.assertTrue(context.getClickedPos().equals(helper.absolutePos(support.above())), "posição de colocar");
        BlockState state = block.getStateForPlacement(context);
        helper.assertTrue(state != null, "o bloco aceita ser posto");
        return state;
    }

    private static BlockHitResult hitTop(GameTestHelper helper, BlockPos support) {
        BlockPos abs = helper.absolutePos(support);
        return new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    }

    private static void assertFacing(GameTestHelper helper, BlockState state, Direction expected, String what) {
        Direction facing = state.getValue(ResonatorPartBlock.FACING);
        helper.assertTrue(facing == expected, what + ": esperado " + expected + ", veio " + facing);
    }
}
