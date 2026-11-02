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
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
import io.github.matheusanbs.wirelessautomate.preset.PresetPaste;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * O {@link RouterPreset} e o {@link PresetPaste}, a lógica de copiar e colar a configuração de um roteador que o
 * Tablet usa: faces pela frente do roteador, a rede de cada face e o colar por tipo.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class PresetPasteGameTests {
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
        ServerPlayer player = TestPlayers.inLevel(helper);
        WaNetwork own = data.create(player.getUUID(), "Pincel " + player.getUUID());
        WaNetwork other = data.create(UUID.randomUUID(), "Alheia " + player.getUUID());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        configureUp(source);
                        source.setNetworkId(own.id());
                        RouterPreset preset = PresetPaste.copy(player, source);
                        helper.assertValueEqual(preset.configuredFaces(), 4, "faces configuradas");
                        helper.assertValueEqual(preset.networks(ResourceType.ITEM), List.of(own.id()), "rede não copiada");

                        PresetPaste.paste(player, target, preset, null);
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
                        assertNetwork(helper, target.networkId(ResourceType.ITEM), own.id(), "rede própria não colada");

                        // Rede de outro dono: só as faces vão.
                        source.setNetworkId(other.id());
                        PresetPaste.paste(player, foreign, PresetPaste.copy(player, source), null);
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
        RouterBlockEntity source = place(helper, new BlockPos(2, 2, 0), Direction.WEST, Blocks.CHEST);
        source.setMode(ResourceType.ITEM, Direction.WEST, PortMode.INSERT);
        RouterPreset preset = RouterPreset.copyOf(source);
        helper.assertTrue(!preset.hasNetwork(), "rede do nada");

        RouterBlockEntity target = place(helper, new BlockPos(2, 2, 2), Direction.DOWN, Blocks.CHEST);
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
                if (!settings(target.face(type, side)).isDefault()) {
                    configured++;
                }
            }
        }
        helper.assertValueEqual(configured, 1, "faces configuradas no destino não foram zeradas");
        assertNetwork(helper, target.networkId(ResourceType.ITEM), network, "preset sem rede mexeu na rede");
        helper.assertValueEqual(RouterPreset.copyOf(target).withoutNetworks(), preset,
                "copiar o colado não dá o mesmo preset");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void presetRoundTripsThroughCodecs(GameTestHelper helper) {
        RouterBlockEntity up = place(helper, new BlockPos(0, 1, 0), Direction.UP, Blocks.CHEST);
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

    /** Destino com faces e rede próprias em todas as abas, para ver o que o colar por tipo não toca. */
    private static void configureTarget(RouterBlockEntity target, UUID network) {
        target.setNetworkId(network);
        target.setMode(ResourceType.ITEM, target.getBlockState().getValue(RouterBlock.FACING).getOpposite(),
                PortMode.INSERT);
        target.setPriority(ResourceType.ENERGY, Direction.EAST, -4);
        target.setPriority(ResourceType.FLUID, Direction.WEST, 9);
    }

    /** A face sem a rede: só modo, prioridade, redstone e filtro. */
    private static FaceConfig settings(FaceConfig config) {
        FaceConfig copy = new FaceConfig();
        copy.copySettingsFrom(config);
        return copy;
    }

    /** Confere que as configurações das faces do tipo no roteador são as do preset {@code expected} (sem a rede). */
    private static void assertFaces(GameTestHelper helper, RouterBlockEntity router, ResourceType type,
            RouterPreset expected, String message) {
        for (RelativeSide side : SIDES) {
            helper.assertValueEqual(settings(router.face(type, side)), settings(expected.face(type, side)),
                    message + " " + type + " " + side);
        }
    }

    /**
     * Seletor em Fluidos: colar muda só as faces e a rede dos fluidos; itens e energia do destino
     * ficam como estavam (faces e rede). Em Todos, tudo é colado.
     */
    @GameTest(template = "empty")
    public static void pasteWithTypeTouchesOnlyThatTab(GameTestHelper helper) {
        RouterBlockEntity source = place(helper, new BlockPos(0, 0, 0), Direction.UP);
        RouterBlockEntity target = place(helper, new BlockPos(2, 1, 2), Direction.NORTH);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        ServerPlayer player = TestPlayers.inLevel(helper);
        WaNetwork items = data.create(player.getUUID(), "Itens " + player.getUUID());
        WaNetwork fluids = data.create(player.getUUID(), "Fluidos " + player.getUUID());
        WaNetwork energy = data.create(player.getUUID(), "Energia " + player.getUUID());
        WaNetwork before = data.create(player.getUUID(), "Antes " + player.getUUID());
        // Rede de outro dono na aba de itens: colar só Fluidos não pode avisar dela.
        WaNetwork foreign = data.create(UUID.randomUUID(), "Alheia " + player.getUUID());

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
                        RouterPreset copied = PresetPaste.copy(player, source);

                        PresetPaste.paste(player, target, copied, ResourceType.FLUID);
                        assertFaces(helper, target, ResourceType.FLUID, copied, "fluidos não colados:");
                        assertNetwork(helper, target.networkId(ResourceType.FLUID), fluids.id(), "rede dos fluidos");
                        for (ResourceType other : new ResourceType[] {ResourceType.ITEM, ResourceType.ENERGY,
                                ResourceType.CHEMICAL, ResourceType.SOURCE}) {
                            assertFaces(helper, target, other, targetBefore, "aba mexida:");
                            assertNetwork(helper, target.networkId(other), before.id(), "rede mexida: " + other);
                        }

                        source.setNetworkId(ResourceType.ITEM, foreign.id());
                        RouterPreset withForeign = RouterPreset.copyOf(source);
                        PresetApplier.Checked onlyFluids = PresetApplier.check(player, withForeign, ResourceType.FLUID);
                        helper.assertTrue(!onlyFluids.droppedAny(), "avisou da rede de outra aba");
                        helper.assertValueEqual(onlyFluids.applied(), List.of(fluids.id()), "redes da aba colada");
                        helper.assertTrue(PresetApplier.check(player, withForeign).droppedAny(),
                                "Todos não recusou a rede alheia");
                        source.setNetworkId(ResourceType.ITEM, items.id());

                        // Todos: tudo colado.
                        PresetPaste.paste(player, target, copied, null);
                        for (ResourceType type : TYPES) {
                            assertFaces(helper, target, type, copied, "Todos:");
                            helper.assertTrue(Objects.equals(target.networkId(type), copied.network(type, RelativeSide.FRONT)),
                                    "Todos, rede " + type);
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

    // ------------------------------------------------------------------ rede por face

    /** Compara redes (podem ser nulas: o {@code assertValueEqual} derruba o servidor com {@code null}). */
    private static void assertNetwork(GameTestHelper helper, @Nullable UUID actual, @Nullable UUID expected,
            String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperava " + expected + ", veio " + actual);
    }

    /**
     * Origem (facing=up) com Cima (FRONT) na rede A e Baixo (BACK) na B: colar num roteador virado
     * para o norte põe A na frente (norte) e B atrás (sul); as outras faces ficam sem rede.
     */
    @GameTest(template = "empty")
    public static void copyAndPastePerFaceNetworks(GameTestHelper helper) {
        RouterBlockEntity source = place(helper, new BlockPos(0, 0, 0), Direction.UP);
        RouterBlockEntity target = place(helper, new BlockPos(2, 1, 2), Direction.NORTH);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        ServerPlayer player = TestPlayers.inLevel(helper);
        WaNetwork a = data.create(player.getUUID(), "Face A " + player.getUUID());
        WaNetwork b = data.create(player.getUUID(), "Face B " + player.getUUID());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        source.setFaceNetwork(ResourceType.ITEM, Direction.UP, a.id());
                        source.setFaceNetwork(ResourceType.ITEM, Direction.DOWN, b.id());
                        RouterPreset preset = PresetPaste.copy(player, source);
                        helper.assertValueEqual(preset.networks(ResourceType.ITEM), List.of(a.id(), b.id()),
                                "redes das faces no preset");
                        helper.assertValueEqual(preset.configuredFaces(), 0, "a rede contou como configuração");

                        PresetPaste.paste(player, target, preset, null);
                        assertNetwork(helper, target.face(ResourceType.ITEM, RelativeSide.FRONT).network(), a.id(), "FRONT");
                        assertNetwork(helper, target.face(ResourceType.ITEM, RelativeSide.BACK).network(), b.id(), "BACK");
                        assertNetwork(helper, target.faceNetwork(ResourceType.ITEM, Direction.NORTH), a.id(), "norte");
                        assertNetwork(helper, target.faceNetwork(ResourceType.ITEM, Direction.SOUTH), b.id(), "sul");
                        for (RelativeSide side : new RelativeSide[] {RelativeSide.TOP, RelativeSide.BOTTOM,
                                RelativeSide.LEFT, RelativeSide.RIGHT}) {
                            assertNetwork(helper, target.face(ResourceType.ITEM, side).network(), null, "item " + side);
                        }
                        for (ResourceType type : TYPES) {
                            if (type != ResourceType.ITEM) {
                                helper.assertTrue(target.networks(type).length == 0, "rede em " + type);
                            }
                        }
                    } finally {
                        data.remove(a.id());
                        data.remove(b.id());
                        helper.getLevel().getServer().getPlayerList().remove(player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Baixo numa rede privada de outro dono: no destino, a face de baixo fica com a rede que tinha e
     * a de cima recebe A; o aviso (o {@code Checked} que a action bar usa) cita a aba e a rede.
     */
    @GameTest(template = "empty")
    public static void pasteSkipsForbiddenFaceNetwork(GameTestHelper helper) {
        RouterBlockEntity source = place(helper, new BlockPos(0, 0, 0), Direction.UP);
        RouterBlockEntity target = place(helper, new BlockPos(2, 0, 2), Direction.UP);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        ServerPlayer player = TestPlayers.inLevel(helper);
        WaNetwork a = data.create(player.getUUID(), "Própria " + player.getUUID());
        WaNetwork before = data.create(player.getUUID(), "Antes " + player.getUUID());
        WaNetwork foreign = data.create(UUID.randomUUID(), "Privada " + player.getUUID());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(!foreign.isPublic(), "a rede alheia nasceu pública");
                        source.setFaceNetwork(ResourceType.ITEM, Direction.UP, a.id());
                        source.setFaceNetwork(ResourceType.ITEM, Direction.DOWN, foreign.id());
                        target.setNetworkId(before.id());
                        RouterPreset preset = PresetPaste.copy(player, source);

                        PresetApplier.Checked checked = PresetApplier.check(player, preset);
                        helper.assertValueEqual(checked.applied(), List.of(a.id()), "redes aplicadas");
                        helper.assertValueEqual(checked.kept(), List.of(foreign.id()), "redes recusadas");
                        helper.assertValueEqual(checked.keptTypes(), List.of(ResourceType.ITEM), "abas avisadas");
                        helper.assertTrue(checked.denied() != null && checked.denied().id().equals(foreign.id()),
                                "rede recusada no aviso");
                        helper.assertTrue(!checked.preset().distinctNetworks().contains(foreign.id()),
                                "a rede recusada ficou no preset");

                        PresetPaste.paste(player, target, preset, null);
                        assertNetwork(helper, target.faceNetwork(ResourceType.ITEM, Direction.UP), a.id(), "Cima");
                        assertNetwork(helper, target.faceNetwork(ResourceType.ITEM, Direction.DOWN), before.id(),
                                "Baixo (rede proibida)");
                        assertNetwork(helper, target.faceNetwork(ResourceType.ITEM, Direction.NORTH), before.id(),
                                "face sem rede no preset");
                        helper.assertTrue(!List.of(target.networks(ResourceType.ITEM)).contains(foreign.id()),
                                "a rede proibida vazou");
                    } finally {
                        data.remove(a.id());
                        data.remove(before.id());
                        data.remove(foreign.id());
                        helper.getLevel().getServer().getPlayerList().remove(player);
                    }
                })
                .thenSucceed();
    }

    /** Uma face sem rede no preset não tira a rede da face de destino; a com rede troca. */
    @GameTest(template = "empty")
    public static void presetFaceWithoutNetworkKeepsTarget(GameTestHelper helper) {
        UUID a = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        RouterBlockEntity source = place(helper, new BlockPos(0, 1, 0), Direction.UP, Blocks.CHEST);
        source.setFaceNetwork(ResourceType.ITEM, Direction.UP, a);
        source.setMode(ResourceType.ITEM, Direction.DOWN, PortMode.INSERT);
        RouterPreset preset = RouterPreset.copyOf(source);
        assertNetwork(helper, preset.network(ResourceType.ITEM, RelativeSide.BACK), null, "BACK no preset");

        RouterBlockEntity target = place(helper, new BlockPos(2, 1, 2), Direction.UP, Blocks.CHEST);
        target.setNetworkId(kept);
        preset.applyTo(target);
        assertNetwork(helper, target.faceNetwork(ResourceType.ITEM, Direction.UP), a, "FRONT");
        assertNetwork(helper, target.faceNetwork(ResourceType.ITEM, Direction.DOWN), kept, "BACK, configurada sem rede");
        helper.assertValueEqual(target.face(ResourceType.ITEM, Direction.DOWN).mode(), PortMode.INSERT, "modo de BACK");
        assertNetwork(helper, target.faceNetwork(ResourceType.ITEM, Direction.EAST), kept, "face padrão");
        assertNetwork(helper, target.networkId(ResourceType.FLUID), kept, "outra aba");
        helper.succeed();
    }

    /** Colar só Fluidos troca as redes das faces de fluidos e não toca nas das faces de itens. */
    @GameTest(template = "empty")
    public static void pasteOnlyTypeKeepsOtherTypes(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork items = data.create(player.getUUID(), "Só itens " + player.getUUID());
        WaNetwork fluids = data.create(player.getUUID(), "Só fluidos " + player.getUUID());
        WaNetwork before = data.create(player.getUUID(), "Só antes " + player.getUUID());
        try {
            RouterBlockEntity source = place(helper, new BlockPos(0, 1, 0), Direction.UP, Blocks.CHEST);
            source.setFaceNetwork(ResourceType.ITEM, Direction.UP, items.id());
            source.setFaceNetwork(ResourceType.FLUID, Direction.UP, fluids.id());
            source.setFaceNetwork(ResourceType.FLUID, Direction.EAST, items.id());
            RouterPreset preset = RouterPreset.copyOf(source);

            PresetApplier.Checked checked = PresetApplier.check(player, preset, ResourceType.FLUID);
            helper.assertValueEqual(checked.applied(), List.of(fluids.id(), items.id()), "redes da aba de fluidos");
            helper.assertTrue(checked.preset().networks(ResourceType.ITEM).isEmpty(), "ficou rede de itens");

            RouterBlockEntity target = place(helper, new BlockPos(2, 1, 2), Direction.UP, Blocks.CHEST);
            target.setNetworkId(before.id());
            checked.preset().applyTo(target, ResourceType.FLUID);
            assertNetwork(helper, target.faceNetwork(ResourceType.FLUID, Direction.UP), fluids.id(), "fluidos, Cima");
            assertNetwork(helper, target.faceNetwork(ResourceType.FLUID, Direction.EAST), items.id(), "fluidos, Leste");
            assertNetwork(helper, target.faceNetwork(ResourceType.FLUID, Direction.DOWN), before.id(), "fluidos, Baixo");
            for (Direction face : Direction.values()) {
                assertNetwork(helper, target.faceNetwork(ResourceType.ITEM, face), before.id(), "itens, " + face);
            }
        } finally {
            data.remove(items.id());
            data.remove(fluids.id());
            data.remove(before.id());
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
