package io.github.matheusanbs.wirelessautomate.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A Tela de Sinal: o bloco na parede, o block entity e o NBT. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvBlockGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final BlockPos WALL = new BlockPos(1, 1, 2);

    private TvBlockGameTests() {
    }

    private static TvPiece text(int x, int y, int width, int height) {
        return new TvPiece(TvPieceType.TEXT, x, y, width, height, 0, "t", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT);
    }

    private static BlockState screen(Direction facing) {
        return ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, facing);
    }

    private static BlockPlaceContext placing(GameTestHelper helper, ServerPlayer player, Direction face) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(WALL)), face,
                helper.absolutePos(WALL), false);
        return new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.SIGNAL_SCREEN.get()), hit);
    }

    /** Põe a Tela em {@code pos} (com a parede atrás) e espera o `onLoad`, que só roda no tick seguinte. */
    private static void withScreen(GameTestHelper helper, Direction facing, java.util.function.Consumer<TvBlockEntity> body) {
        helper.setBlock(A.relative(facing.getOpposite()), Blocks.STONE);
        helper.setBlock(A, screen(facing));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem block entity"))
                .thenExecute(() -> body.accept((TvBlockEntity) helper.getBlockEntity(A)))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void colocaContraParedeEOlhaParaFora(GameTestHelper helper) {
        helper.setBlock(WALL, Blocks.STONE);
        ServerPlayer player = TestPlayers.inLevel(helper);
        BlockState north = ModBlocks.SIGNAL_SCREEN.get().getStateForPlacement(placing(helper, player, Direction.NORTH));
        helper.assertTrue(north != null && north.getValue(TvBlock.FACING) == Direction.NORTH, "olha para o norte");
        helper.assertTrue(ModBlocks.SIGNAL_SCREEN.get().getStateForPlacement(placing(helper, player, Direction.UP)) == null,
                "no chão não se coloca");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void semParedeNaoSobrevive(GameTestHelper helper) {
        helper.setBlock(WALL, Blocks.STONE);
        BlockState state = screen(Direction.NORTH);
        helper.assertTrue(state.canSurvive(helper.getLevel(), helper.absolutePos(A)), "com parede sobrevive");
        helper.setBlock(A, state);
        helper.setBlock(WALL, Blocks.AIR);
        helper.assertFalse(state.canSurvive(helper.getLevel(), helper.absolutePos(A)), "sem parede não sobrevive");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlock(A, b -> b == Blocks.AIR, () -> "a Tela devia sumir"))
                .thenWaitUntil(() -> helper.assertItemEntityPresent(ModItems.SIGNAL_SCREEN.get(), A, 2.0))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void nbtGuardaTudo(GameTestHelper helper) {
        BlockState state = screen(Direction.NORTH);
        TvBlockEntity original = new TvBlockEntity(helper.absolutePos(A), state);
        UUID owner = UUID.randomUUID();
        original.setOwner(owner);
        original.setArea(new TvArea(4, 2, 3, 0));
        original.setLayout(TvLayout.of(List.of(text(4, 4, 40, 10), text(60, 30, 30, 12))));
        CompoundTag tag = original.saveWithFullMetadata(helper.getLevel().registryAccess());
        TvBlockEntity back = new TvBlockEntity(helper.absolutePos(A), state);
        back.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(back.area().equals(original.area()), "área");
        helper.assertTrue(back.layout().equals(original.layout()), "layout");
        helper.assertTrue(owner.equals(back.owner()), "dono");
        helper.assertTrue(back.revision() == original.revision(), "revisão");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void updateTagSemDono(GameTestHelper helper) {
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen(Direction.NORTH));
        tv.setOwner(UUID.randomUUID());
        tv.setArea(new TvArea(2, 1, 0, 0));
        CompoundTag tag = tv.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertFalse(tag.contains("owner"), "o update tag não leva o dono");
        helper.assertTrue(tag.contains("width") && tag.contains("layout") && tag.contains("revision"), "faltam chaves");
        helper.assertTrue(tag.getInt("width") == 2, "largura");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void revisaoSobeACadaMudanca(GameTestHelper helper) {
        withScreen(helper, Direction.NORTH, tv -> {
            helper.assertFalse(helper.getBlockState(A).getValue(TvBlock.EXPANDED), "1 x 1 não é expandida");
            tv.setArea(new TvArea(4, 2, 0, 0));
            helper.assertTrue(tv.revision() == 1, "setArea sobe a revisão");
            helper.assertTrue(helper.getBlockState(A).getValue(TvBlock.EXPANDED), "área maior liga o expanded");
            tv.setLayout(TvLayout.of(List.of(text(100, 40, 20, 10))));
            helper.assertTrue(tv.revision() == 2, "setLayout sobe a revisão");
            tv.setArea(TvArea.SINGLE);
            helper.assertTrue(tv.revision() == 3, "setArea menor sobe a revisão");
            helper.assertFalse(helper.getBlockState(A).getValue(TvBlock.EXPANDED), "1 x 1 desliga o expanded");
            TvPiece piece = tv.layout().pieces().get(0);
            helper.assertTrue(piece.x() + piece.width() <= TvArea.SINGLE.pixelWidth()
                    && piece.y() + piece.height() <= TvArea.SINGLE.pixelHeight(), "a peça veio para dentro");
            tv.setArea(TvArea.SINGLE);
            helper.assertTrue(tv.revision() == 3, "sem mudança não sobe");
        });
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void quemEdita(GameTestHelper helper) {
        ServerPlayer owner = TestPlayers.inLevel(helper);
        ServerPlayer other = TestPlayers.inLevel(helper);
        ServerPlayer operator = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-mock-operator"), ClientInformation.createDefault()) {
            @Override
            protected int getPermissionLevel() {
                return 2;
            }
        };
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen(Direction.NORTH));
        helper.assertFalse(tv.canEdit(owner), "sem dono, um jogador comum não edita");
        helper.assertTrue(tv.canEdit(operator), "sem dono, o operador edita");
        tv.setOwner(owner.getUUID());
        helper.assertTrue(tv.canEdit(owner), "o dono edita");
        helper.assertFalse(tv.canEdit(other), "outro jogador não edita");
        helper.assertTrue(tv.canEdit(operator), "o operador edita");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void celulaNoMundoPelaFrente(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 1), screen(Direction.NORTH));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockEntity(new BlockPos(2, 1, 1)) instanceof TvBlockEntity, "sem block entity"))
                .thenExecute(() -> {
                    TvBlockEntity tv = (TvBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 1));
                    tv.setArea(new TvArea(3, 2, 0, 0));
                    helper.assertTrue(tv.cellPos(0).equals(helper.absolutePos(new BlockPos(2, 1, 1))), "célula 0");
                    helper.assertTrue(tv.cellPos(1).equals(helper.absolutePos(new BlockPos(1, 1, 1))), "célula 1 a oeste");
                    helper.assertTrue(tv.cellPos(3).equals(helper.absolutePos(new BlockPos(2, 2, 1))), "célula 3 em cima");
                })
                .thenExecute(() -> helper.setBlock(new BlockPos(2, 1, 1), Blocks.AIR))
                .thenExecute(() -> helper.setBlock(new BlockPos(1, 1, 2), Blocks.STONE))
                .thenExecute(() -> helper.setBlock(A, screen(Direction.EAST)))
                .thenExecute(() -> helper.setBlock(new BlockPos(0, 1, 1), Blocks.STONE))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem block entity"))
                .thenExecute(() -> {
                    TvBlockEntity tv = (TvBlockEntity) helper.getBlockEntity(A);
                    tv.setArea(new TvArea(2, 1, 0, 0));
                    helper.assertTrue(tv.cellPos(1).equals(helper.absolutePos(new BlockPos(1, 1, 0))),
                            "virada para o leste, a direita é o norte");
                })
                .thenSucceed();
    }
}
