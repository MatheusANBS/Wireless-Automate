package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NetworkStats;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.CycleLinkerTypePayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Rede por aba: cada tipo de recurso de um roteador entra numa rede própria. Motor, migração do
 * NBT antigo, Vinculador com tipo, preset do Configurador e o pacote do seletor do Vinculador.
 * Máquinas em y=1 com o roteador em cima (facing=UP), então a face configurada é {@link Direction#UP}.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TypeNetworkGameTests {
    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Máquina em {@code machine} e roteador em cima, com itens em {@code items} e o resto em {@code others}. */
    private static RouterBlockEntity place(GameTestHelper helper, BlockPos machine, BlockState machineState,
            @Nullable UUID items, @Nullable UUID others) {
        helper.setBlock(machine, machineState);
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(others);
        router.setNetworkId(ResourceType.ITEM, items);
        return router;
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void waitRegistered(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
        }
    }

    private static @Nullable NetworkStats stats(GameTestHelper helper, UUID network) {
        for (NetworkStats stats : NetworkManager.get().stats(helper.getLevel().getServer())) {
            if (stats.id().equals(network)) {
                return stats;
            }
        }
        return null;
    }

    private static int nodes(GameTestHelper helper, UUID network) {
        NetworkStats stats = stats(helper, network);
        return stats == null ? 0 : stats.nodes();
    }

    private static boolean use(ServerPlayer player, Item item, RouterBlockEntity router, boolean sneak) {
        player.setShiftKeyDown(sneak);
        BlockPos pos = router.getBlockPos();
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return item.useOn(context).consumesAction();
    }

    /**
     * O caso do dono com o que o vanilla tem: nenhum bloco vanilla tem itens e fluidos juntos, então
     * cada roteador põe itens numa rede e fluidos na outra, e há um chamariz por tipo na rede errada.
     * Itens: A (extrai) e B na rede X, o chamariz C na Y. Fluidos: D (extrai) e E na rede Y, o
     * chamariz F na X. Os diamantes vão de A para B e a água de D para E; C e F ficam vazios. Os
     * baús ficam em diagonal para não virarem baú duplo.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void eachTypeFollowsItsOwnNetwork(GameTestHelper helper) {
        UUID x = newNetwork(helper, "teste-aba-x");
        UUID y = newNetwork(helper, "teste-aba-y");
        BlockPos a = new BlockPos(0, 1, 0);
        BlockPos b = new BlockPos(2, 1, 0);
        BlockPos c = new BlockPos(1, 1, 1);
        BlockPos d = new BlockPos(1, 1, 0);
        BlockPos e = new BlockPos(0, 1, 2);
        BlockPos f = new BlockPos(2, 1, 2);
        BlockState chest = Blocks.CHEST.defaultBlockState();
        RouterBlockEntity ra = place(helper, a, chest, x, y);
        RouterBlockEntity rb = place(helper, b, chest, x, y);
        RouterBlockEntity rc = place(helper, c, chest, y, x);
        RouterBlockEntity rd = place(helper, d,
                Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), x, y);
        RouterBlockEntity re = place(helper, e, Blocks.CAULDRON.defaultBlockState(), x, y);
        RouterBlockEntity rf = place(helper, f, Blocks.CAULDRON.defaultBlockState(), y, x);
        ra.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        rb.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        rc.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        rd.setMode(ResourceType.FLUID, Direction.UP, PortMode.EXTRACT);
        re.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);
        rf.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);
        ChestBlockEntity source = helper.getBlockEntity(a);
        source.setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, ra, rb, rc, rd, re, rf))
                // Todo nó tem algum tipo em cada rede.
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(nodes(helper, x), 6, "nós na rede X");
                    helper.assertValueEqual(nodes(helper, y), 6, "nós na rede Y");
                })
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(count(helper, b, Items.DIAMOND), 10, "diamantes em B");
                    helper.assertBlockPresent(Blocks.WATER_CAULDRON, e);
                    helper.assertBlockProperty(e, LayeredCauldronBlock.LEVEL, 3);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(count(helper, c, Items.DIAMOND), 0, "itens cruzaram para a rede Y");
                    helper.assertBlockPresent(Blocks.CAULDRON, f);
                    helper.assertBlockPresent(Blocks.CAULDRON, d);
                    helper.assertTrue(source.isEmpty(), "origem não esvaziou");
                })
                .thenSucceed();
    }

    /** Trocar a rede de uma aba move só aquele tipo: o nó continua nas outras redes pelos outros tipos. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void changingOneTabMovesOnlyThatType(GameTestHelper helper) {
        UUID first = newNetwork(helper, "teste-aba-1");
        UUID second = newNetwork(helper, "teste-aba-2");
        BlockPos a = new BlockPos(0, 1, 0);
        BlockPos b = new BlockPos(2, 1, 2);
        BlockState chest = Blocks.CHEST.defaultBlockState();
        RouterBlockEntity source = place(helper, a, chest, second, second);
        RouterBlockEntity target = place(helper, b, chest, first, first);
        source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        target.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        ChestBlockEntity sourceChest = helper.getBlockEntity(a);
        sourceChest.setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertValueEqual(count(helper, b, Items.DIAMOND), 0, "redes diferentes trocaram");
                    helper.assertValueEqual(nodes(helper, first), 1, "nós na rede 1");
                    helper.assertValueEqual(nodes(helper, second), 1, "nós na rede 2");
                    source.setNetworkId(ResourceType.ITEM, first);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(count(helper, b, Items.DIAMOND), 10, "diamantes em B"))
                .thenExecute(() -> {
                    helper.assertValueEqual(nodes(helper, first), 2, "nós na rede 1 depois da troca");
                    // Fluidos, energia e químicos do roteador A continuam na rede 2.
                    helper.assertValueEqual(nodes(helper, second), 1, "nós na rede 2 depois da troca");
                    source.setNetworkId(first);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(nodes(helper, second), 0, "rede 2 sem nós"))
                .thenSucceed();
    }

    /** O NBT antigo, com uma rede única em {@code network}, vale para todos os tipos; o novo salva por tipo. */
    @GameTest(template = "empty")
    public static void legacyNetworkTagAppliesToAllTypes(GameTestHelper helper) {
        BlockState state = ModBlocks.ROUTER.get().defaultBlockState();
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        UUID legacy = UUID.randomUUID();
        CompoundTag old = new CompoundTag();
        old.putUUID("network", legacy);

        RouterBlockEntity router = new RouterBlockEntity(BlockPos.ZERO, state);
        router.loadWithComponents(old, registries);
        for (ResourceType type : ResourceType.values()) {
            helper.assertValueEqual(router.networkId(type), legacy, "rede antiga em " + type);
        }

        UUID energy = UUID.randomUUID();
        router.setNetworkId(ResourceType.ENERGY, energy);
        router.setNetworkId(ResourceType.CHEMICAL, null);
        CompoundTag saved = router.saveWithoutMetadata(registries);
        helper.assertFalse(saved.contains("network"), "salvou o formato antigo");
        RouterBlockEntity copy = new RouterBlockEntity(BlockPos.ZERO, state);
        copy.loadWithComponents(saved, registries);
        helper.assertValueEqual(copy.networkId(ResourceType.ITEM), legacy, "itens");
        helper.assertValueEqual(copy.networkId(ResourceType.FLUID), legacy, "fluidos");
        helper.assertValueEqual(copy.networkId(ResourceType.ENERGY), energy, "energia");
        helper.assertTrue(copy.networkId(ResourceType.CHEMICAL) == null, "químicos sem rede");
        helper.succeed();
    }

    /** Vinculador em Energia põe só a energia na rede ativa; em Todos, todos os tipos. */
    @GameTest(template = "empty")
    public static void linkerWithTypeLinksOnlyThatType(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = newNetwork(helper, "teste-vinculador-antes");
        RouterBlockEntity router = place(helper, new BlockPos(1, 1, 1), Blocks.CHEST.defaultBlockState(),
                before, before);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WaNetwork active = data.create(player.getUUID(), "Base " + player.getUUID());
        data.setActiveNetwork(player.getUUID(), active.id());
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        linker.set(ModDataComponents.LINKER_TYPE.get(), ResourceType.ENERGY);
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        try {
            helper.assertTrue(use(player, ModItems.LINKER.get(), router, false), "vinculador não agiu");
            helper.assertValueEqual(router.networkId(ResourceType.ENERGY), active.id(), "energia");
            helper.assertValueEqual(router.networkId(ResourceType.ITEM), before, "itens mudaram");
            helper.assertValueEqual(router.networkId(ResourceType.FLUID), before, "fluidos mudaram");
            helper.assertValueEqual(router.networkId(ResourceType.CHEMICAL), before, "químicos mudaram");

            linker.remove(ModDataComponents.LINKER_TYPE.get());
            helper.assertTrue(LinkerItem.type(linker) == null, "sem componente não é Todos");
            use(player, ModItems.LINKER.get(), router, false);
            for (ResourceType type : ResourceType.values()) {
                helper.assertValueEqual(router.networkId(type), active.id(), "Todos não vinculou " + type);
            }
        } finally {
            data.remove(active.id());
            data.remove(before);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** Shift + roda: Todos → Itens → Fluidos → Energia → Todos, e para trás; só com o Vinculador na mão. */
    @GameTest(template = "empty")
    public static void cycleLinkerTypePayloadCycles(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack linker = new ItemStack(ModItems.LINKER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, linker);
            ResourceType[] forward = {ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY, null};
            for (ResourceType expected : forward) {
                helper.assertTrue(ModPayloads.handleCycleLinkerType(player, new CycleLinkerTypePayload(1)),
                        "recusou avançar");
                helper.assertTrue(LinkerItem.type(linker) == expected, "avançar: " + LinkerItem.type(linker));
            }
            helper.assertFalse(linker.has(ModDataComponents.LINKER_TYPE.get()), "Todos deixou o componente");
            helper.assertTrue(ModPayloads.handleCycleLinkerType(player, new CycleLinkerTypePayload(-1)),
                    "recusou voltar");
            helper.assertValueEqual(LinkerItem.type(linker), ResourceType.ENERGY, "voltar de Todos");
            helper.assertFalse(ModPayloads.handleCycleLinkerType(player, new CycleLinkerTypePayload(0)),
                    "aceitou direção 0");

            ItemStack other = new ItemStack(ModItems.CONFIGURATOR.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, other);
            helper.assertFalse(ModPayloads.handleCycleLinkerType(player, new CycleLinkerTypePayload(1)),
                    "aceitou sem o Vinculador na mão");
            helper.assertFalse(other.has(ModDataComponents.LINKER_TYPE.get()), "mexeu noutro item");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * O Configurador copia a rede de cada aba; ao colar, aplica as que o jogador pode usar e deixa
     * as outras como estavam. Um tipo sem rede no preset não mexe na rede do destino.
     */
    @GameTest(template = "empty")
    public static void presetCopiesNetworksPerType(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WaNetwork lines = data.create(player.getUUID(), "Linha " + player.getUUID());
        WaNetwork fluids = data.create(player.getUUID(), "Fluidos " + player.getUUID());
        WaNetwork targetNetwork = data.create(player.getUUID(), "Destino " + player.getUUID());
        WaNetwork foreign = data.create(UUID.randomUUID(), "Alheia " + player.getUUID());
        RouterBlockEntity source = place(helper, new BlockPos(0, 1, 0), Blocks.STONE.defaultBlockState(),
                lines.id(), null);
        source.setNetworkId(ResourceType.FLUID, fluids.id());
        source.setNetworkId(ResourceType.ENERGY, foreign.id());
        RouterBlockEntity target = place(helper, new BlockPos(2, 1, 2), Blocks.STONE.defaultBlockState(),
                targetNetwork.id(), targetNetwork.id());
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, configurator);
        try {
            helper.assertTrue(use(player, ModItems.CONFIGURATOR.get(), source, true), "copiar não agiu");
            RouterPreset preset = configurator.get(ModDataComponents.PRESET.get());
            helper.assertTrue(preset != null, "nada copiado");
            helper.assertValueEqual(preset.network(ResourceType.ITEM), lines.id(), "itens no preset");
            helper.assertValueEqual(preset.network(ResourceType.FLUID), fluids.id(), "fluidos no preset");
            helper.assertValueEqual(preset.network(ResourceType.ENERGY), foreign.id(), "energia no preset");
            helper.assertTrue(preset.network(ResourceType.CHEMICAL) == null, "químicos no preset");

            helper.assertTrue(use(player, ModItems.CONFIGURATOR.get(), target, false), "colar não agiu");
            helper.assertValueEqual(target.networkId(ResourceType.ITEM), lines.id(), "itens colados");
            helper.assertValueEqual(target.networkId(ResourceType.FLUID), fluids.id(), "fluidos colados");
            helper.assertValueEqual(target.networkId(ResourceType.ENERGY), targetNetwork.id(),
                    "colou a rede de outro dono");
            helper.assertValueEqual(target.networkId(ResourceType.CHEMICAL), targetNetwork.id(),
                    "tipo sem rede no preset mexeu na rede");

            // Codecs: por tipo vai e volta; o formato antigo (rede única) vale para todos os tipos.
            RegistryAccess registries = helper.getLevel().registryAccess();
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            Tag tag = RouterPreset.CODEC.encodeStart(ops, preset).getOrThrow();
            helper.assertValueEqual(RouterPreset.CODEC.parse(ops, tag).getOrThrow(), preset, "codec por tipo");
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                RouterPreset.STREAM_CODEC.encode(buf, preset);
                helper.assertValueEqual(RouterPreset.STREAM_CODEC.decode(buf), preset, "stream codec por tipo");
            } finally {
                buf.release();
            }
            CompoundTag legacy = new CompoundTag();
            legacy.putUUID("network", lines.id());
            RouterPreset old = RouterPreset.CODEC.parse(ops, legacy).getOrThrow();
            for (ResourceType type : ResourceType.values()) {
                helper.assertValueEqual(old.network(type), lines.id(), "preset antigo em " + type);
            }
            helper.assertTrue(!preset.equals(preset.withoutNetwork(ResourceType.FLUID)), "equals ignorou uma aba");
        } finally {
            data.remove(lines.id());
            data.remove(fluids.id());
            data.remove(targetNetwork.id());
            data.remove(foreign.id());
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
