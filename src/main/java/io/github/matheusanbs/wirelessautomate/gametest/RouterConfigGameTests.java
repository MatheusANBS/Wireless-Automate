package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Configuração por face, rotação relativa, caches de capability e redstone do roteador. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RouterConfigGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos ROUTER = MACHINE.above();

    private static BlockState router(Direction facing) {
        return ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, facing);
    }

    private static RouterBlockEntity placeOnChest(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.CHEST);
        helper.setBlock(ROUTER, router(Direction.UP));
        return helper.getBlockEntity(ROUTER);
    }

    @GameTest(template = "empty")
    public static void facesAreConfiguredByAbsoluteFace(GameTestHelper helper) {
        RouterBlockEntity node = placeOnChest(helper);

        helper.assertTrue(node.face(ResourceType.ITEM, Direction.NORTH).isDefault(), "face começou fora do padrão");
        node.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        node.setPriority(ResourceType.FLUID, Direction.DOWN, 5);
        node.setRedstone(ResourceType.ENERGY, Direction.UP, RedstoneMode.HIGH);
        node.setMode(ResourceType.ENERGY, Direction.UP, PortMode.INSERT);

        helper.assertValueEqual(node.face(ResourceType.ITEM, Direction.NORTH).mode(), PortMode.EXTRACT, "modo");
        helper.assertValueEqual(node.face(ResourceType.ITEM, Direction.SOUTH).mode(), PortMode.NONE, "outra face");
        helper.assertValueEqual(node.face(ResourceType.FLUID, Direction.NORTH).mode(), PortMode.NONE, "outro tipo");
        helper.assertValueEqual(node.face(ResourceType.FLUID, Direction.DOWN).priority(), 5, "prioridade");
        FaceConfig energy = node.face(ResourceType.ENERGY, Direction.UP);
        helper.assertValueEqual(energy.redstone(), RedstoneMode.HIGH, "redstone");
        helper.assertTrue(energy.isActive(true) && !energy.isActive(false), "isActive ignorou o redstone");
        helper.assertTrue(!node.face(ResourceType.FLUID, Direction.DOWN).isActive(true), "face NONE ativa");
        // facing=UP: a face de cima da máquina é a FRONT.
        helper.assertTrue(node.face(ResourceType.ENERGY, RelativeSide.FRONT) == energy, "FRONT não é o facing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void configSurvivesSaveAndLoad(GameTestHelper helper) {
        RouterBlockEntity node = placeOnChest(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        CompoundTag empty = node.saveWithoutMetadata(registries);
        helper.assertTrue(!empty.contains("faces"), "faces padrão foram salvas");

        UUID network = UUID.randomUUID();
        node.setNetworkId(network);
        node.setMode(ResourceType.ITEM, Direction.EAST, PortMode.BOTH);
        node.setPriority(ResourceType.ITEM, Direction.EAST, -3);
        node.setRedstone(ResourceType.FLUID, Direction.DOWN, RedstoneMode.LOW);
        CompoundTag tag = node.saveWithoutMetadata(registries);

        RouterBlockEntity copy = new RouterBlockEntity(node.getBlockPos(), node.getBlockState());
        copy.loadWithComponents(tag, registries);
        helper.assertValueEqual(copy.networkId(), network, "rede");
        for (ResourceType type : ResourceType.values()) {
            for (Direction face : Direction.values()) {
                helper.assertValueEqual(copy.face(type, face), node.face(type, face), type + " " + face);
            }
        }

        CompoundTag bad = new CompoundTag();
        bad.putString("mode", "nao_existe");
        bad.putString("redstone", "HIGH");
        FaceConfig parsed = FaceConfig.load(bad);
        helper.assertValueEqual(parsed.mode(), PortMode.NONE, "enum inválido sem fallback");
        helper.assertValueEqual(parsed.redstone(), RedstoneMode.HIGH, "redstone válido perdido");
        helper.assertTrue(new FaceConfig().save().isEmpty(), "padrão não é tag vazia");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void relativeSidesFollowModelRotation(GameTestHelper helper) {
        for (Direction facing : Direction.values()) {
            Set<Direction> seen = EnumSet.noneOf(Direction.class);
            for (RelativeSide side : RelativeSide.values()) {
                Direction absolute = RelativeSide.toAbsolute(facing, side);
                seen.add(absolute);
                helper.assertValueEqual(RelativeSide.fromAbsolute(facing, absolute), side, facing + " " + side);
            }
            helper.assertValueEqual(seen.size(), 6, "não é bijeção para " + facing);
            helper.assertValueEqual(RelativeSide.FRONT.toAbsolute(facing), facing, "FRONT");
            helper.assertValueEqual(RelativeSide.BACK.toAbsolute(facing), facing.getOpposite(), "BACK");
        }

        // Rotação do blockstate (x depois y) aplicada às direções do caso facing=up.
        Map<Direction, Direction[]> topLeft = Map.of(
                Direction.UP, new Direction[] {Direction.SOUTH, Direction.EAST},
                Direction.DOWN, new Direction[] {Direction.NORTH, Direction.EAST},
                Direction.NORTH, new Direction[] {Direction.UP, Direction.EAST},
                Direction.SOUTH, new Direction[] {Direction.UP, Direction.WEST},
                Direction.EAST, new Direction[] {Direction.UP, Direction.SOUTH},
                Direction.WEST, new Direction[] {Direction.UP, Direction.NORTH});
        topLeft.forEach((facing, expected) -> {
            helper.assertValueEqual(RelativeSide.TOP.toAbsolute(facing), expected[0], "TOP com " + facing);
            helper.assertValueEqual(RelativeSide.LEFT.toAbsolute(facing), expected[1], "LEFT com " + facing);
            helper.assertValueEqual(RelativeSide.BOTTOM.toAbsolute(facing), expected[0].getOpposite(), "BOTTOM");
            helper.assertValueEqual(RelativeSide.RIGHT.toAbsolute(facing), expected[1].getOpposite(), "RIGHT");
        });

        // A mesma configuração salva vale em relação ao facing de cada roteador.
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        RouterBlockEntity up = new RouterBlockEntity(BlockPos.ZERO, router(Direction.UP));
        up.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        up.setMode(ResourceType.ITEM, Direction.SOUTH, PortMode.INSERT);
        CompoundTag tag = up.saveWithoutMetadata(registries);

        RouterBlockEntity east = new RouterBlockEntity(BlockPos.ZERO, router(Direction.EAST));
        east.loadWithComponents(tag, registries);
        helper.assertValueEqual(east.face(ResourceType.ITEM, Direction.EAST).mode(), PortMode.EXTRACT, "FRONT girada");
        helper.assertValueEqual(east.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT, "TOP girada");
        helper.assertValueEqual(east.face(ResourceType.ITEM, Direction.SOUTH).mode(), PortMode.NONE, "sobrou na face antiga");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capabilityCacheFollowsMachine(GameTestHelper helper) {
        RouterBlockEntity node = placeOnChest(helper);
        BlockPos hopper = ROUTER.north();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(node), "sem onLoad"))
                .thenExecute(() -> {
                    helper.assertTrue(node.items(Direction.UP) != null, "baú sem IItemHandler");
                    helper.assertTrue(node.energy(Direction.UP) == null, "baú com energia");
                    helper.setBlock(MACHINE, Blocks.STONE);
                    helper.assertTrue(node.items(Direction.UP) == null, "cache não viu o baú sair");
                    helper.setBlock(MACHINE, Blocks.CHEST);
                    helper.assertTrue(node.items(Direction.UP) != null, "cache não viu o baú voltar");

                    // Girar o roteador: a configuração relativa vai junto e a máquina passa a ser o funil.
                    node.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    helper.setBlock(hopper, Blocks.HOPPER);
                    helper.setBlock(ROUTER, router(Direction.SOUTH));
                    helper.assertTrue(helper.getBlockEntity(ROUTER) == node, "block entity trocado ao girar");
                    helper.assertValueEqual(node.machinePos(), helper.absolutePos(hopper), "máquina");
                    helper.assertValueEqual(node.face(ResourceType.ITEM, Direction.SOUTH).mode(), PortMode.EXTRACT,
                            "configuração não girou");
                    var items = node.items(Direction.SOUTH);
                    helper.assertTrue(items != null && items.getSlots() == 5, "cache não foi refeito para o funil");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void redstoneSignalIsTracked(GameTestHelper helper) {
        BlockPos power = ROUTER.west();
        helper.setBlock(MACHINE, Blocks.CHEST);
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        helper.setBlock(ROUTER, router(Direction.UP));
        RouterBlockEntity node = helper.getBlockEntity(ROUTER);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(node.powered(), "onLoad não leu o sinal"))
                .thenExecute(() -> helper.setBlock(power, Blocks.AIR))
                .thenExecute(() -> helper.assertTrue(!node.powered(), "sinal não caiu"))
                .thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK))
                .thenExecute(() -> helper.assertTrue(node.powered(), "sinal não voltou"))
                .thenSucceed();
    }
}
