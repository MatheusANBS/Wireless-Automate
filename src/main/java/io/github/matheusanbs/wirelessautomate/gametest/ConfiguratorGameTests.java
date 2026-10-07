package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Configurador (pincel e colar em área na mesma máquina) e o {@link RouterPreset} que ele copia e cola. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ConfiguratorGameTests {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();

    private static BlockState router(Direction facing) {
        return ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, facing);
    }

    /** Roteador preso na face {@code facing} de um bloco de pedra em {@code machine}. */
    private static RouterBlockEntity place(GameTestHelper helper, BlockPos machine, Direction facing) {
        return place(helper, machine, facing, Blocks.STONE);
    }

    /** Roteador preso na face {@code facing} de um bloco {@code block} em {@code machine}. */
    private static RouterBlockEntity place(GameTestHelper helper, BlockPos machine, Direction facing, Block block) {
        BlockPos pos = machine.relative(facing);
        helper.setBlock(machine, block);
        helper.setBlock(pos, router(facing));
        return helper.getBlockEntity(pos);
    }

    private static boolean use(ServerPlayer player, RouterBlockEntity router, boolean sneak) {
        player.setShiftKeyDown(sneak);
        BlockPos pos = router.getBlockPos();
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return ModItems.CONFIGURATOR.get().useOn(context).consumesAction();
    }

    /** Configuração variada, pelas faces absolutas de um roteador com {@code facing=up}. */
    private static void configureUp(RouterBlockEntity up) {
        up.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);         // FRONT
        up.setMode(ResourceType.ITEM, Direction.SOUTH, PortMode.INSERT);       // TOP
        up.setPriority(ResourceType.FLUID, Direction.EAST, 7);                 // LEFT
        up.setMode(ResourceType.ENERGY, Direction.DOWN, PortMode.BOTH);        // BACK
        up.setRedstone(ResourceType.ENERGY, Direction.DOWN, RedstoneMode.HIGH);
    }

    @GameTest(template = "empty")
    public static void copyFromUpAndPasteOnNorth(GameTestHelper helper) {
        RouterBlockEntity source = place(helper, new BlockPos(0, 0, 0), Direction.UP);
        RouterBlockEntity target = place(helper, new BlockPos(2, 1, 2), Direction.NORTH);
        RouterBlockEntity foreign = place(helper, new BlockPos(0, 1, 2), Direction.EAST);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WaNetwork own = data.create(player.getUUID(), "Pincel " + player.getUUID());
        WaNetwork other = data.create(UUID.randomUUID(), "Alheia " + player.getUUID());
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, configurator);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(use(player, target, false), "colar sem cópia não respondeu");
                        helper.assertTrue(!configurator.has(ModDataComponents.PRESET.get()), "cópia do nada");

                        configureUp(source);
                        source.setNetworkId(own.id());
                        helper.assertTrue(use(player, source, true), "copiar não agiu");
                        RouterPreset preset = configurator.get(ModDataComponents.PRESET.get());
                        helper.assertTrue(preset != null, "nada copiado para o item");
                        helper.assertValueEqual(preset.configuredFaces(), 4, "faces configuradas");
                        helper.assertValueEqual(preset.network(ResourceType.ITEM), own.id(), "rede não copiada");

                        helper.assertTrue(use(player, target, false), "colar não agiu");
                        for (ResourceType type : TYPES) {
                            for (RelativeSide side : SIDES) {
                                helper.assertValueEqual(target.face(type, side), source.face(type, side),
                                        type + " " + side);
                            }
                        }
                        // facing=north: FRONT norte, TOP cima, LEFT leste, BACK sul.
                        helper.assertValueEqual(target.face(ResourceType.ITEM, Direction.NORTH).mode(),
                                PortMode.EXTRACT, "FRONT");
                        helper.assertValueEqual(target.face(ResourceType.ITEM, Direction.UP).mode(),
                                PortMode.INSERT, "TOP");
                        helper.assertValueEqual(target.face(ResourceType.ITEM, Direction.SOUTH).mode(),
                                PortMode.NONE, "a face absoluta TOP da origem não pode ser copiada como absoluta");
                        helper.assertValueEqual(target.face(ResourceType.FLUID, Direction.EAST).priority(), 7, "LEFT");
                        helper.assertValueEqual(target.face(ResourceType.ENERGY, Direction.SOUTH).redstone(),
                                RedstoneMode.HIGH, "BACK");
                        helper.assertValueEqual(target.networkId(ResourceType.ITEM), own.id(), "rede própria não colada");

                        // Rede de outro dono: só as faces vão.
                        source.setNetworkId(other.id());
                        use(player, source, true);
                        use(player, foreign, false);
                        helper.assertValueEqual(foreign.face(ResourceType.ITEM, RelativeSide.TOP).mode(),
                                PortMode.INSERT, "faces não coladas sem a rede");
                        helper.assertTrue(foreign.networkId(ResourceType.ITEM) == null, "colou rede de outro dono");
                    } finally {
                        data.remove(own.id());
                        data.remove(other.id());
                        helper.getLevel().getServer().getPlayerList().remove(player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void pasteResetsFacesDefaultInPreset(GameTestHelper helper) {
        RouterBlockEntity source = new RouterBlockEntity(BlockPos.ZERO, router(Direction.WEST));
        source.setMode(ResourceType.ITEM, Direction.WEST, PortMode.INSERT);
        RouterPreset preset = RouterPreset.copyOf(source);
        helper.assertTrue(!preset.hasNetwork(), "rede do nada");

        RouterBlockEntity target = new RouterBlockEntity(BlockPos.ZERO, router(Direction.DOWN));
        UUID network = UUID.randomUUID();
        target.setNetworkId(network);
        target.setMode(ResourceType.ITEM, Direction.DOWN, PortMode.EXTRACT);
        target.setPriority(ResourceType.FLUID, Direction.NORTH, 3);
        target.setRedstone(ResourceType.ENERGY, Direction.EAST, RedstoneMode.LOW);

        preset.applyTo(target);
        helper.assertValueEqual(target.face(ResourceType.ITEM, Direction.DOWN).mode(), PortMode.INSERT, "FRONT");
        int configured = 0;
        for (ResourceType type : TYPES) {
            for (RelativeSide side : SIDES) {
                if (!target.face(type, side).isDefault()) {
                    configured++;
                }
            }
        }
        helper.assertValueEqual(configured, 1, "faces configuradas no destino não foram zeradas");
        helper.assertValueEqual(target.networkId(ResourceType.ITEM), network, "preset sem rede mexeu na rede");
        helper.assertValueEqual(RouterPreset.copyOf(target).withoutNetworks(), preset,
                "copiar o colado não dá o mesmo preset");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void presetRoundTripsThroughCodecs(GameTestHelper helper) {
        RouterBlockEntity up = new RouterBlockEntity(BlockPos.ZERO, router(Direction.UP));
        configureUp(up);
        up.setPriority(ResourceType.CHEMICAL, Direction.WEST, -2);
        up.setNetworkId(UUID.randomUUID());
        RouterPreset preset = RouterPreset.copyOf(up);

        RegistryAccess registries = helper.getLevel().registryAccess();
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (RouterPreset original : new RouterPreset[] {preset, preset.withoutNetworks(), RouterPreset.EMPTY}) {
            Tag tag = RouterPreset.CODEC.encodeStart(ops, original).getOrThrow();
            RouterPreset decoded = RouterPreset.CODEC.parse(ops, tag).getOrThrow();
            helper.assertValueEqual(decoded, original, "codec persistente");
            helper.assertValueEqual(decoded.hashCode(), original.hashCode(), "hashCode");

            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                RouterPreset.STREAM_CODEC.encode(buf, original);
                helper.assertValueEqual(RouterPreset.STREAM_CODEC.decode(buf), original, "stream codec");
            } finally {
                buf.release();
            }
        }
        helper.assertTrue(!preset.equals(preset.withoutNetworks()), "equals ignorou a rede");
        helper.assertValueEqual(preset.face(ResourceType.CHEMICAL, RelativeSide.RIGHT).priority(), -2, "RIGHT");
        helper.succeed();
    }

    /** Clique direito com o Configurador num bloco (posição relativa à estrutura), pelo caminho do jogo. */
    private static void clickBlock(GameTestHelper helper, ServerPlayer player, BlockPos relative, boolean sneak) {
        player.setShiftKeyDown(sneak);
        BlockPos pos = helper.absolutePos(relative);
        ItemStack stack = player.getMainHandItem();
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        if (!stack.onItemUseFirst(context).consumesAction()) {
            stack.useOn(context);
        }
    }

    /** Clique direito com o Configurador no ar. */
    private static void clickAir(GameTestHelper helper, ServerPlayer player, boolean sneak) {
        player.setShiftKeyDown(sneak);
        ModItems.CONFIGURATOR.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    /**
     * Modo Área: Shift + clique no ar troca o modo; cliques em dois blocos marcam a área; clique no
     * ar cola só nos roteadores presos à mesma máquina do copiado.
     */
    @GameTest(template = "empty")
    public static void areaPastesOnlyOnSameMachine(GameTestHelper helper) {
        // Roteadores em (0,1,0) copiado, (2,1,0) e (2,0,1) em fornalhas, (0,1,2) num baú.
        RouterBlockEntity source = place(helper, new BlockPos(0, 0, 0), Direction.UP, Blocks.FURNACE);
        RouterBlockEntity sameA = place(helper, new BlockPos(2, 0, 0), Direction.UP, Blocks.FURNACE);
        RouterBlockEntity sameB = place(helper, new BlockPos(2, 0, 2), Direction.NORTH, Blocks.FURNACE);
        RouterBlockEntity chest = place(helper, new BlockPos(0, 0, 2), Direction.UP, Blocks.CHEST);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        // O jogador falso nasce longe da estrutura; a área exige estar perto.
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, configurator);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(chest), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        configureUp(source);
                        clickBlock(helper, player, new BlockPos(0, 1, 0), true);
                        helper.assertTrue(configurator.has(ModDataComponents.PRESET.get()), "nada copiado");
                        helper.assertValueEqual(ConfiguratorItem.machine(configurator),
                                BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE), "máquina copiada");

                        clickAir(helper, player, true);
                        helper.assertValueEqual(ConfiguratorItem.mode(configurator), LinkerMode.AREA, "modo Área");
                        clickAir(helper, player, false);
                        helper.assertTrue(sameA.face(ResourceType.ITEM, RelativeSide.FRONT).isDefault(),
                                "colou sem área");

                        // Área de (1,0,0) a (2,2,2): só os dois roteadores das fornalhas da direita.
                        clickBlock(helper, player, new BlockPos(1, 0, 0), false);
                        clickBlock(helper, player, new BlockPos(2, 2, 2), false);
                        helper.assertTrue(ConfiguratorItem.area(configurator) != null
                                && ConfiguratorItem.area(configurator).complete(), "área não marcada");
                        clickAir(helper, player, false);
                        for (ResourceType type : TYPES) {
                            for (RelativeSide side : SIDES) {
                                helper.assertValueEqual(sameA.face(type, side), source.face(type, side),
                                        "mesma máquina, " + type + " " + side);
                                helper.assertValueEqual(sameB.face(type, side), source.face(type, side),
                                        "mesma máquina girada, " + type + " " + side);
                            }
                        }

                        // Área de (0,0,0) a (0,2,2): o copiado e o do baú; o do baú fica como estava.
                        clickBlock(helper, player, new BlockPos(0, 0, 0), false);
                        clickBlock(helper, player, new BlockPos(0, 2, 2), false);
                        clickAir(helper, player, false);
                        for (ResourceType type : TYPES) {
                            for (RelativeSide side : SIDES) {
                                helper.assertTrue(chest.face(type, side).isDefault(),
                                        "colou noutra máquina: " + type + " " + side);
                            }
                        }

                        // Shift + clique num bloco sem roteador limpa a área; num roteador, continua copiando.
                        clickBlock(helper, player, new BlockPos(1, 0, 1), true);
                        helper.assertTrue(ConfiguratorItem.area(configurator) == null, "área não foi limpa");
                        clickBlock(helper, player, new BlockPos(0, 1, 2), true);
                        helper.assertValueEqual(ConfiguratorItem.machine(configurator),
                                BuiltInRegistries.BLOCK.getKey(Blocks.CHEST), "Shift + clique no roteador não copiou");

                        clickAir(helper, player, true);
                        helper.assertValueEqual(ConfiguratorItem.mode(configurator), LinkerMode.SINGLE,
                                "volta ao pincel");
                    } finally {
                        player.setShiftKeyDown(false);
                        helper.getLevel().getServer().getPlayerList().remove(player);
                    }
                })
                .thenSucceed();
    }

    /** Uma área acima do volume da config não é marcada, e colar sem a área completa não muda nada. */
    @GameTest(template = "empty")
    public static void areaTooBigIsRefused(GameTestHelper helper) {
        RouterBlockEntity source = place(helper, new BlockPos(0, 0, 0), Direction.UP, Blocks.FURNACE);
        RouterBlockEntity target = place(helper, new BlockPos(2, 0, 0), Direction.UP, Blocks.FURNACE);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, configurator);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        configureUp(source);
                        clickBlock(helper, player, new BlockPos(0, 1, 0), true);
                        ConfiguratorItem.setMode(configurator, LinkerMode.AREA);
                        clickBlock(helper, player, new BlockPos(2, 0, 0), false);
                        // 300 × 1 × 1000 = 300.000 blocos, acima do padrão de 262.144.
                        clickBlock(helper, player, new BlockPos(301, 0, 999), false);
                        helper.assertTrue(!ConfiguratorItem.area(configurator).complete(), "área grande marcada");
                        clickAir(helper, player, false);
                        helper.assertTrue(target.face(ResourceType.ITEM, RelativeSide.FRONT).isDefault(),
                                "colou numa área incompleta");
                    } finally {
                        player.setShiftKeyDown(false);
                        helper.getLevel().getServer().getPlayerList().remove(player);
                    }
                })
                .thenSucceed();
    }
}
