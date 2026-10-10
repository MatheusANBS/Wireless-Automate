package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorWheelPayload;
import io.github.matheusanbs.wirelessautomate.packet.CycleConfiguratorTypePayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.preset.ConfiguratorArea;
import io.github.matheusanbs.wirelessautomate.preset.PasteMode;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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

/**
 * Configurador (pincel e colar em área na mesma máquina ou em qualquer uma, o seletor de tipo) e o
 * {@link RouterPreset} que ele copia e cola.
 */
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
                        helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.AREA_SAME,
                                "o primeiro Shift + clique vai à Área na mesma máquina");
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

                        // Shift + clique num bloco sem roteador limpa a cópia e a área; o modo fica.
                        clickBlock(helper, player, new BlockPos(1, 0, 1), true);
                        helper.assertTrue(ConfiguratorItem.area(configurator) == null, "área não foi limpa");
                        helper.assertTrue(!configurator.has(ModDataComponents.PRESET.get())
                                && ConfiguratorItem.machine(configurator) == null, "cópia não foi limpa");
                        helper.assertValueEqual(ConfiguratorItem.mode(configurator), LinkerMode.AREA, "modo mudou");
                        // Num roteador, Shift + clique continua copiando.
                        clickBlock(helper, player, new BlockPos(0, 1, 2), true);
                        helper.assertValueEqual(ConfiguratorItem.machine(configurator),
                                BuiltInRegistries.BLOCK.getKey(Blocks.CHEST), "Shift + clique no roteador não copiou");

                        // O ciclo tem três modos: Área (qualquer máquina) e depois o pincel.
                        clickAir(helper, player, true);
                        helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.AREA_ANY,
                                "Área na mesma máquina → qualquer máquina");
                        helper.assertTrue(configurator.getOrDefault(
                                ModDataComponents.CONFIGURATOR_ANY_MACHINE.get(), false), "sem o any_machine");
                        clickAir(helper, player, true);
                        helper.assertValueEqual(ConfiguratorItem.mode(configurator), LinkerMode.SINGLE,
                                "volta ao pincel");
                        helper.assertFalse(configurator.has(ModDataComponents.CONFIGURATOR_ANY_MACHINE.get()),
                                "o pincel guardou o any_machine");
                        // No pincel também limpa.
                        clickBlock(helper, player, new BlockPos(1, 0, 1), true);
                        helper.assertTrue(!configurator.has(ModDataComponents.PRESET.get()), "pincel não limpou");
                        helper.assertValueEqual(ConfiguratorItem.mode(configurator), LinkerMode.SINGLE,
                                "Shift + clique num bloco trocou o modo");
                    } finally {
                        player.setShiftKeyDown(false);
                        helper.getLevel().getServer().getPlayerList().remove(player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Área (qualquer máquina): a cópia de um roteador num barril vai para o de um baú na área. Na
     * mesma máquina o do baú fica como estava (e conta como outra máquina); o canto 2 usa a mensagem
     * sem a contagem da mesma máquina.
     */
    @GameTest(template = "empty")
    public static void areaAnyMachinePastesEverywhere(GameTestHelper helper) {
        RouterBlockEntity source = place(helper, new BlockPos(0, 0, 0), Direction.UP, Blocks.BARREL);
        RouterBlockEntity chest = place(helper, new BlockPos(2, 0, 0), Direction.UP, Blocks.CHEST);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, configurator);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(chest), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        configureUp(source);
                        clickBlock(helper, player, new BlockPos(0, 1, 0), true);
                        RouterPreset copied = configurator.get(ModDataComponents.PRESET.get());
                        helper.assertTrue(copied != null, "nada copiado");
                        helper.assertValueEqual(ConfiguratorItem.machine(configurator),
                                BuiltInRegistries.BLOCK.getKey(Blocks.BARREL), "máquina copiada");

                        // Mesma máquina: o do baú fica como estava.
                        ConfiguratorItem.setPasteMode(configurator, PasteMode.AREA_SAME);
                        clickBlock(helper, player, new BlockPos(0, 0, 0), false);
                        clickBlock(helper, player, new BlockPos(2, 2, 0), false);
                        ConfiguratorArea.Outcome same = ConfiguratorArea.paste(player, configurator, copied);
                        helper.assertValueEqual(same.otherMachine(), 1, "outra máquina na mesma máquina");
                        for (ResourceType type : TYPES) {
                            for (RelativeSide side : SIDES) {
                                helper.assertTrue(chest.face(type, side).isDefault(),
                                        "colou noutra máquina: " + type + " " + side);
                            }
                        }

                        // Qualquer máquina: o canto 2 sem a contagem e o do baú igual ao copiado.
                        ConfiguratorItem.setPasteMode(configurator, PasteMode.AREA_ANY);
                        clickBlock(helper, player, new BlockPos(0, 0, 0), false);
                        Component corner2 = ConfiguratorArea.markCorner(player, configurator,
                                helper.absolutePos(new BlockPos(2, 2, 0)));
                        helper.assertTrue(corner2.getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals("item.wirelessautomate.configurator.area.corner2_any"),
                                "canto 2: " + corner2.getString());
                        clickAir(helper, player, false);
                        for (ResourceType type : TYPES) {
                            for (RelativeSide side : SIDES) {
                                helper.assertValueEqual(chest.face(type, side), source.face(type, side),
                                        "qualquer máquina, " + type + " " + side);
                            }
                        }
                        ConfiguratorArea.Outcome any = ConfiguratorArea.paste(player, configurator, copied);
                        helper.assertValueEqual(any.otherMachine(), 0, "outra máquina em qualquer máquina");
                        helper.assertValueEqual(any.applied(), 2, "roteadores colados");
                    } finally {
                        player.setShiftKeyDown(false);
                        helper.getLevel().getServer().getPlayerList().remove(player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Uma varinha sem os componentes é pincel; só o modo Área vale mesma máquina; o {@code any_machine}
     * no pincel é ignorado (e o Shift + clique no ar segue o ciclo dele), e o pincel o remove.
     */
    @GameTest(template = "empty")
    public static void pasteModeDefaultsAndComponents(GameTestHelper helper) {
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.BRUSH, "varinha nova");

        ConfiguratorItem.setMode(configurator, LinkerMode.AREA);
        helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.AREA_SAME,
                "Área sem o any_machine");

        ConfiguratorItem.setPasteMode(configurator, PasteMode.AREA_ANY);
        helper.assertValueEqual(ConfiguratorItem.mode(configurator), LinkerMode.AREA, "AREA_ANY grava a Área");
        helper.assertTrue(configurator.getOrDefault(ModDataComponents.CONFIGURATOR_ANY_MACHINE.get(), false),
                "AREA_ANY sem o componente");
        ConfiguratorItem.setPasteMode(configurator, PasteMode.AREA_SAME);
        helper.assertFalse(configurator.has(ModDataComponents.CONFIGURATOR_ANY_MACHINE.get()),
                "AREA_SAME deixou o componente");
        ConfiguratorItem.setPasteMode(configurator, PasteMode.BRUSH);
        helper.assertFalse(configurator.has(ModDataComponents.CONFIGURATOR_MODE.get()), "pincel deixou o modo");
        helper.assertFalse(configurator.has(ModDataComponents.CONFIGURATOR_ANY_MACHINE.get()),
                "pincel deixou o any_machine");
        for (PasteMode mode : PasteMode.values()) {
            helper.assertValueEqual(PasteMode.of(mode.linkerMode(), mode.anyMachine()), mode, "ida e volta " + mode);
            ConfiguratorItem.setPasteMode(configurator, mode);
            helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), mode, "no item " + mode);
        }

        // any_machine perdido num pincel: continua pincel, e o Shift + clique no ar vai à Área mesma máquina.
        ConfiguratorItem.setPasteMode(configurator, PasteMode.BRUSH);
        configurator.set(ModDataComponents.CONFIGURATOR_ANY_MACHINE.get(), true);
        helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.BRUSH, "pincel com any_machine");
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, configurator);
            clickAir(helper, player, true);
            helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.AREA_SAME,
                    "Shift + clique no ar a partir do pincel");
            helper.assertFalse(configurator.has(ModDataComponents.CONFIGURATOR_ANY_MACHINE.get()),
                    "a Área mesma máquina ficou com o any_machine");
        } finally {
            player.setShiftKeyDown(false);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
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

    /** Destino com faces e rede próprias em todas as abas, para ver o que o colar por tipo não toca. */
    private static void configureTarget(RouterBlockEntity target, UUID network) {
        target.setNetworkId(network);
        target.setMode(ResourceType.ITEM, target.getBlockState().getValue(RouterBlock.FACING).getOpposite(),
                PortMode.INSERT);
        target.setPriority(ResourceType.ENERGY, Direction.EAST, -4);
        target.setPriority(ResourceType.FLUID, Direction.WEST, 9);
    }

    /** Confere que as faces do tipo no roteador são as do preset {@code expected}. */
    private static void assertFaces(GameTestHelper helper, RouterBlockEntity router, ResourceType type,
            RouterPreset expected, String message) {
        for (RelativeSide side : SIDES) {
            helper.assertValueEqual(router.face(type, side), expected.face(type, side), message + " " + type + " " + side);
        }
    }

    /**
     * Seletor em Fluidos: colar muda só as faces e a rede dos fluidos; itens e energia do destino
     * ficam como estavam (faces e rede). Em Todos, tudo é colado, como antes.
     */
    @GameTest(template = "empty")
    public static void pasteWithTypeTouchesOnlyThatTab(GameTestHelper helper) {
        RouterBlockEntity source = place(helper, new BlockPos(0, 0, 0), Direction.UP);
        RouterBlockEntity target = place(helper, new BlockPos(2, 1, 2), Direction.NORTH);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WaNetwork items = data.create(player.getUUID(), "Itens " + player.getUUID());
        WaNetwork fluids = data.create(player.getUUID(), "Fluidos " + player.getUUID());
        WaNetwork energy = data.create(player.getUUID(), "Energia " + player.getUUID());
        WaNetwork before = data.create(player.getUUID(), "Antes " + player.getUUID());
        // Rede de outro dono na aba de itens: colar só Fluidos não pode avisar dela.
        WaNetwork foreign = data.create(UUID.randomUUID(), "Alheia " + player.getUUID());
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, configurator);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        configureUp(source);
                        source.setNetworkId(ResourceType.ITEM, items.id());
                        source.setNetworkId(ResourceType.FLUID, fluids.id());
                        source.setNetworkId(ResourceType.ENERGY, energy.id());
                        source.setNetworkId(ResourceType.CHEMICAL, energy.id());
                        source.setNetworkId(ResourceType.SOURCE, energy.id());
                        configureTarget(target, before.id());
                        RouterPreset targetBefore = RouterPreset.copyOf(target);
                        helper.assertTrue(use(player, source, true), "copiar não agiu");
                        RouterPreset copied = configurator.get(ModDataComponents.PRESET.get());

                        ConfiguratorItem.setType(configurator, ResourceType.FLUID);
                        helper.assertTrue(use(player, target, false), "colar não agiu");
                        assertFaces(helper, target, ResourceType.FLUID, copied, "fluidos não colados:");
                        helper.assertValueEqual(target.networkId(ResourceType.FLUID), fluids.id(), "rede dos fluidos");
                        for (ResourceType other : new ResourceType[] {ResourceType.ITEM, ResourceType.ENERGY,
                                ResourceType.CHEMICAL, ResourceType.SOURCE}) {
                            assertFaces(helper, target, other, targetBefore, "aba mexida:");
                            helper.assertValueEqual(target.networkId(other), before.id(), "rede mexida: " + other);
                        }

                        source.setNetworkId(ResourceType.ITEM, foreign.id());
                        RouterPreset withForeign = RouterPreset.copyOf(source);
                        PresetApplier.Checked onlyFluids = PresetApplier.check(player, withForeign, ResourceType.FLUID);
                        helper.assertTrue(!onlyFluids.droppedAny(), "avisou da rede de outra aba");
                        helper.assertValueEqual(onlyFluids.applied(), List.of(fluids.id()), "redes da aba colada");
                        helper.assertTrue(PresetApplier.check(player, withForeign).droppedAny(),
                                "Todos não recusou a rede alheia");
                        source.setNetworkId(ResourceType.ITEM, items.id());

                        // Todos: o comportamento de sempre, tudo colado.
                        ConfiguratorItem.setType(configurator, null);
                        helper.assertTrue(!configurator.has(ModDataComponents.CONFIGURATOR_TYPE.get()),
                                "Todos deixou o componente");
                        use(player, target, false);
                        for (ResourceType type : TYPES) {
                            assertFaces(helper, target, type, copied, "Todos:");
                            helper.assertValueEqual(target.networkId(type), copied.network(type), "Todos, rede " + type);
                        }
                    } finally {
                        for (WaNetwork network : new WaNetwork[] {items, fluids, energy, before, foreign}) {
                            data.remove(network.id());
                        }
                        helper.getLevel().getServer().getPlayerList().remove(player);
                    }
                })
                .thenSucceed();
    }

    /** Colar em área com o seletor em Energia: só a aba de energia dos roteadores da mesma máquina muda. */
    @GameTest(template = "empty")
    public static void areaPasteWithType(GameTestHelper helper) {
        RouterBlockEntity source = place(helper, new BlockPos(0, 0, 0), Direction.UP, Blocks.FURNACE);
        RouterBlockEntity target = place(helper, new BlockPos(2, 0, 0), Direction.UP, Blocks.FURNACE);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WaNetwork power = data.create(player.getUUID(), "Energia área " + player.getUUID());
        WaNetwork before = data.create(player.getUUID(), "Antes área " + player.getUUID());
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, configurator);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        configureUp(source);
                        source.setNetworkId(power.id());
                        configureTarget(target, before.id());
                        RouterPreset targetBefore = RouterPreset.copyOf(target);
                        clickBlock(helper, player, new BlockPos(0, 1, 0), true);
                        RouterPreset copied = configurator.get(ModDataComponents.PRESET.get());
                        helper.assertTrue(copied != null, "nada copiado");

                        ConfiguratorItem.setMode(configurator, LinkerMode.AREA);
                        ConfiguratorItem.setType(configurator, ResourceType.ENERGY);
                        clickBlock(helper, player, new BlockPos(1, 0, 0), false);
                        clickBlock(helper, player, new BlockPos(2, 2, 0), false);
                        clickAir(helper, player, false);

                        assertFaces(helper, target, ResourceType.ENERGY, copied, "energia não colada:");
                        helper.assertValueEqual(target.networkId(ResourceType.ENERGY), power.id(), "rede da energia");
                        for (ResourceType other : new ResourceType[] {ResourceType.ITEM, ResourceType.FLUID,
                                ResourceType.CHEMICAL, ResourceType.SOURCE}) {
                            assertFaces(helper, target, other, targetBefore, "aba mexida:");
                            helper.assertValueEqual(target.networkId(other), before.id(), "rede mexida: " + other);
                        }
                    } finally {
                        player.setShiftKeyDown(false);
                        data.remove(power.id());
                        data.remove(before.id());
                        helper.getLevel().getServer().getPlayerList().remove(player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Shift + roda com o Configurador: Todos → Itens → Fluidos → Energia (→ Químicos, só com o
     * Mekanism) → Todos, e para trás; só com o Configurador na mão.
     */
    @GameTest(template = "empty")
    public static void cycleConfiguratorTypeSkipsChemicalsWithoutMekanism(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, configurator);
            List<ResourceType> forward = new ArrayList<>(List.of(ResourceType.ITEM, ResourceType.FLUID,
                    ResourceType.ENERGY));
            if (Chemicals.LOADED) {
                forward.add(ResourceType.CHEMICAL);
            }
            if (Sources.LOADED) {
                forward.add(ResourceType.SOURCE);
            }
            forward.add(null);
            for (ResourceType expected : forward) {
                helper.assertTrue(ModPayloads.handleCycleConfiguratorType(player, new CycleConfiguratorTypePayload(1)),
                        "recusou avançar");
                helper.assertTrue(ConfiguratorItem.type(configurator) == expected,
                        "avançar: " + ConfiguratorItem.type(configurator));
            }
            helper.assertFalse(configurator.has(ModDataComponents.CONFIGURATOR_TYPE.get()), "Todos deixou o componente");
            helper.assertTrue(ModPayloads.handleCycleConfiguratorType(player, new CycleConfiguratorTypePayload(-1)),
                    "recusou voltar");
            helper.assertValueEqual(ConfiguratorItem.type(configurator),
                    Sources.LOADED ? ResourceType.SOURCE
                            : Chemicals.LOADED ? ResourceType.CHEMICAL : ResourceType.ENERGY, "voltar de Todos");
            helper.assertFalse(ModPayloads.handleCycleConfiguratorType(player, new CycleConfiguratorTypePayload(0)),
                    "aceitou direção 0");

            ItemStack linker = new ItemStack(ModItems.LINKER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, linker);
            helper.assertFalse(ModPayloads.handleCycleConfiguratorType(player, new CycleConfiguratorTypePayload(1)),
                    "aceitou sem o Configurador na mão");
            helper.assertFalse(linker.has(ModDataComponents.CONFIGURATOR_TYPE.get()), "mexeu noutro item");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * A roda do Configurador grava o modo de colar e o tipo de uma vez; vazio é Todos. Recusa outra
     * coisa na mão e um tipo que não está carregado, sem mexer no item.
     */
    @GameTest(template = "empty")
    public static void wheelPayloadSetsModeAndType(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, configurator);
            helper.assertTrue(ModPayloads.handleConfiguratorWheel(player,
                    new ConfiguratorWheelPayload(PasteMode.AREA_ANY, Optional.of(ResourceType.FLUID))), "recusou");
            helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.AREA_ANY, "modo");
            helper.assertValueEqual(ConfiguratorItem.type(configurator), ResourceType.FLUID, "tipo");

            helper.assertTrue(ModPayloads.handleConfiguratorWheel(player,
                    new ConfiguratorWheelPayload(PasteMode.BRUSH, Optional.empty())), "recusou o pincel");
            helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.BRUSH, "pincel");
            helper.assertFalse(configurator.has(ModDataComponents.CONFIGURATOR_TYPE.get()), "Todos deixou o tipo");
            helper.assertFalse(configurator.has(ModDataComponents.CONFIGURATOR_ANY_MACHINE.get()),
                    "pincel guardou o qualquer máquina");

            if (!Chemicals.LOADED) {
                helper.assertFalse(ModPayloads.handleConfiguratorWheel(player,
                        new ConfiguratorWheelPayload(PasteMode.AREA_SAME, Optional.of(ResourceType.CHEMICAL))),
                        "aceitou Químicos sem o Mekanism");
                helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.BRUSH, "mudou ao recusar");
            }

            ItemStack linker = new ItemStack(ModItems.LINKER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, linker);
            helper.assertFalse(ModPayloads.handleConfiguratorWheel(player,
                    new ConfiguratorWheelPayload(PasteMode.AREA_ANY, Optional.empty())), "aceitou sem o Configurador");
            helper.assertFalse(linker.has(ModDataComponents.CONFIGURATOR_ANY_MACHINE.get())
                    || linker.has(ModDataComponents.CONFIGURATOR_MODE.get()), "mexeu noutro item");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** O componente do seletor passa pelos codecs; Químicos é lido mesmo sem o Mekanism. */
    @GameTest(template = "empty")
    public static void configuratorTypeRoundTripsThroughCodec(GameTestHelper helper) {
        for (ResourceType type : TYPES) {
            Tag tag = ConfiguratorItem.TYPE_CODEC.encodeStart(NbtOps.INSTANCE, type).getOrThrow();
            helper.assertValueEqual(ConfiguratorItem.TYPE_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(), type,
                    "codec " + type);
        }
        helper.assertTrue(ConfiguratorItem.TYPE_CODEC.parse(NbtOps.INSTANCE, NbtOps.INSTANCE.createString("lava"))
                .isError(), "aceitou um tipo desconhecido");
        helper.succeed();
    }
}
