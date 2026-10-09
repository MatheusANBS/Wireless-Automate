package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
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
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
        helper.assertValueEqual(copy.networkId(ResourceType.ITEM), network, "rede");
        for (ResourceType type : ResourceType.values()) {
            for (Direction face : Direction.values()) {
                helper.assertValueEqual(copy.face(type, face), node.face(type, face), type + " " + face);
            }
        }

        CompoundTag bad = new CompoundTag();
        bad.putString("mode", "nao_existe");
        bad.putString("redstone", "HIGH");
        FaceConfig parsed = FaceConfig.load(bad, registries);
        helper.assertValueEqual(parsed.mode(), PortMode.NONE, "enum inválido sem fallback");
        helper.assertValueEqual(parsed.redstone(), RedstoneMode.HIGH, "redstone válido perdido");
        helper.assertTrue(new FaceConfig().save(registries).isEmpty(), "padrão não é tag vazia");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void relativeSidesFollowModelRotation(GameTestHelper helper) {
        for (Direction facing : Direction.values()) {
            for (int spin = 0; spin < RelativeSide.SPINS; spin++) {
                String where = facing + " spin " + spin;
                Set<Direction> seen = EnumSet.noneOf(Direction.class);
                for (RelativeSide side : RelativeSide.values()) {
                    Direction absolute = RelativeSide.toAbsolute(facing, spin, side);
                    seen.add(absolute);
                    helper.assertValueEqual(RelativeSide.fromAbsolute(facing, spin, absolute), side, where + " " + side);
                }
                helper.assertValueEqual(seen.size(), 6, "não é bijeção para " + where);
                helper.assertValueEqual(RelativeSide.FRONT.toAbsolute(facing, spin), facing, "FRONT com " + where);
                helper.assertValueEqual(RelativeSide.BACK.toAbsolute(facing, spin), facing.getOpposite(), "BACK com " + where);
            }
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
            helper.assertValueEqual(RelativeSide.TOP.toAbsolute(facing, 0), expected[0], "TOP com " + facing);
            helper.assertValueEqual(RelativeSide.LEFT.toAbsolute(facing, 0), expected[1], "LEFT com " + facing);
            helper.assertValueEqual(RelativeSide.BOTTOM.toAbsolute(facing, 0), expected[0].getOpposite(), "BOTTOM");
            helper.assertValueEqual(RelativeSide.RIGHT.toAbsolute(facing, 0), expected[1].getOpposite(), "RIGHT");
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

    /** Giro com facing=up: horário visto de cima, então os LEDs (TOP) vão de sul para oeste. */
    @GameTest(template = "empty")
    public static void spinTurnsClockwiseSeenFromOutside(GameTestHelper helper) {
        Direction[] tops = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        for (int spin = 0; spin < RelativeSide.SPINS; spin++) {
            helper.assertValueEqual(RelativeSide.TOP.toAbsolute(Direction.UP, spin), tops[spin], "TOP com spin " + spin);
        }
        helper.assertValueEqual(RelativeSide.LEFT.toAbsolute(Direction.UP, 1), Direction.SOUTH, "LEFT com spin 1");
        // Numa parede (facing=north, olhando para sul): TOP sai de cima e vai para a direita de quem olha.
        helper.assertValueEqual(RelativeSide.TOP.toAbsolute(Direction.NORTH, 1), Direction.WEST, "TOP na parede");
        helper.succeed();
    }

    /** Se o ponto (em pixels, dentro do bloco) cai em alguma caixa da forma do estado. */
    private static boolean shapeHas(BlockState state, double x, double y, double z) {
        Vec3 point = new Vec3(x / 16, y / 16, z / 16);
        return state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs().stream()
                .anyMatch(box -> box.contains(point));
    }

    /** Colisão com facing=up e spin 1: a antena do norte (x 1,5..3,5, z 2,5..4,5) vai para o leste. */
    @GameTest(template = "empty")
    public static void shapeFollowsSpin(GameTestHelper helper) {
        BlockState spin0 = router(Direction.UP);
        BlockState spin1 = spin0.setValue(RouterBlock.SPIN, 1);
        helper.assertTrue(shapeHas(spin0, 2.5, 10, 3.5), "antena no norte com spin 0");
        helper.assertTrue(shapeHas(spin1, 12.5, 10, 2.5), "antena girada para x 11,5..13,5, z 1,5..3,5");
        helper.assertFalse(shapeHas(spin1, 2.5, 10, 3.5), "antena ainda no lugar do spin 0");
        helper.succeed();
    }

    /**
     * Para os 24 (facing, spin), a forma bate com o {@link RelativeSide}: as antenas saem do corpo para o
     * FRONT, encostadas no lado BOTTOM (o oposto dos LEDs), uma de cada lado do eixo LEFT/RIGHT.
     */
    @GameTest(template = "empty")
    public static void shapeMatchesRelativeSides(GameTestHelper helper) {
        for (Direction facing : Direction.values()) {
            for (int spin = 0; spin < RelativeSide.SPINS; spin++) {
                BlockState state = router(facing).setValue(RouterBlock.SPIN, spin);
                Vec3i front = RelativeSide.FRONT.toAbsolute(facing, spin).getNormal();
                Vec3i bottom = RelativeSide.BOTTOM.toAbsolute(facing, spin).getNormal();
                Vec3i left = RelativeSide.LEFT.toAbsolute(facing, spin).getNormal();
                String where = facing + " spin " + spin;
                for (int lado : new int[] {-1, 1}) {
                    // Centro das antenas do caso up/spin 0: (2,5 ou 13,5; 11; 3,5) = centro + 3 FRONT + 4,5 BOTTOM ± 5,5.
                    double[] antena = point(front, 3, bottom, 4.5, left, 5.5 * lado);
                    double[] espelho = point(front, 3, bottom, -4.5, left, 5.5 * lado);
                    helper.assertTrue(shapeHas(state, antena[0], antena[1], antena[2]), "antena com " + where);
                    helper.assertFalse(shapeHas(state, espelho[0], espelho[1], espelho[2]), "antena do lado TOP com " + where);
                }
            }
        }
        helper.succeed();
    }

    /** Centro do bloco (8, 8, 8) somado a {@code a·da + b·db + c·dc}, em pixels. */
    private static double[] point(Vec3i da, double a, Vec3i db, double b, Vec3i dc, double c) {
        return new double[] {
                8 + a * da.getX() + b * db.getX() + c * dc.getX(),
                8 + a * da.getY() + b * db.getY() + c * dc.getY(),
                8 + a * da.getZ() + b * db.getZ() + c * dc.getZ()};
    }

    /** Estruturas: girar o roteador leva o TOP junto, escolhendo o spin. */
    @GameTest(template = "empty")
    public static void rotateKeepsTopWithStructure(GameTestHelper helper) {
        BlockState up = router(Direction.UP);
        BlockState turned = up.rotate(Rotation.CLOCKWISE_90);
        helper.assertValueEqual(turned.getValue(RouterBlock.FACING), Direction.UP, "facing");
        helper.assertValueEqual(turned.getValue(RouterBlock.SPIN), 1, "spin");
        helper.assertValueEqual(RelativeSide.TOP.toAbsolute(Direction.UP, turned.getValue(RouterBlock.SPIN)),
                Rotation.CLOCKWISE_90.rotate(Direction.SOUTH), "TOP girado");
        for (Direction facing : Direction.values()) {
            for (int spin = 0; spin < RelativeSide.SPINS; spin++) {
                BlockState state = router(facing).setValue(RouterBlock.SPIN, spin);
                Direction top = RelativeSide.TOP.toAbsolute(facing, spin);
                for (Rotation rotation : Rotation.values()) {
                    BlockState rotated = state.rotate(rotation);
                    helper.assertValueEqual(rotated.getValue(RouterBlock.FACING), rotation.rotate(facing), "facing " + rotation);
                    helper.assertValueEqual(RelativeSide.TOP.toAbsolute(rotated.getValue(RouterBlock.FACING),
                            rotated.getValue(RouterBlock.SPIN)), rotation.rotate(top), facing + " " + spin + " " + rotation);
                }
                for (Mirror mirror : Mirror.values()) {
                    BlockState mirrored = state.mirror(mirror);
                    helper.assertValueEqual(mirrored.getValue(RouterBlock.FACING), mirror.mirror(facing), "facing " + mirror);
                    helper.assertValueEqual(RelativeSide.TOP.toAbsolute(mirrored.getValue(RouterBlock.FACING),
                            mirrored.getValue(RouterBlock.SPIN)), mirror.mirror(top), facing + " " + spin + " " + mirror);
                }
            }
        }
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

    /** As chaves salvas por tipo continuam as de sempre, e um roteador salvo volta igual. */
    @GameTest(template = "empty")
    public static void savedKeysStayTheSame(GameTestHelper helper) {
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "teste-chaves").id();
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.CHEST);
        helper.setBlock(new BlockPos(0, 2, 0), router(Direction.UP));
        RouterBlockEntity node = helper.getBlockEntity(new BlockPos(0, 2, 0));
        node.setNetworkId(network);
        node.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);

        CompoundTag tag = node.saveWithoutMetadata(helper.getLevel().registryAccess());
        Set<String> networks = tag.getCompound("networks").getAllKeys();
        helper.assertTrue(networks.containsAll(Set.of("item", "fluid", "energy", "chemical")), "chaves de rede: " + networks);
        helper.assertTrue(tag.getCompound("faces").contains("fluid"), "face de fluido salva fora da chave fluid");

        helper.setBlock(new BlockPos(2, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 2, 2), router(Direction.UP));
        RouterBlockEntity copy = helper.getBlockEntity(new BlockPos(2, 2, 2));
        copy.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(network.equals(copy.networkId(ResourceType.ENERGY)), "rede de energia perdida");
        helper.assertTrue(copy.face(ResourceType.FLUID, Direction.UP).mode() == PortMode.INSERT, "modo de fluido perdido");
        helper.succeed();
    }
}
