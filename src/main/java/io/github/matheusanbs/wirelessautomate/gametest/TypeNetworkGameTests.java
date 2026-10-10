package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import com.mojang.serialization.DynamicOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
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
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
        router.setNetworkId(others);
        router.setNetworkId(ResourceType.ITEM, items);
        return router;
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        ChestBlockEntity chest = GameTestCompat.getBlockEntity(helper, pos);
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
     *
     * <p>Porte 1.20.1: os fluidos ficam em tanques de teste ({@link TestMachines#SIMPLE_TANK}) no lugar dos
     * caldeirões do {@code main}, que o NeoForge expõe como handler de fluido e o Forge 1.20.1 não.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void eachTypeFollowsItsOwnNetwork(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            // Porte 1.20.1: o tanque de teste (TestMachines.SIMPLE_TANK) faz o papel do caldeirão; sem ele o
            // teste não prova nada, então falha em vez de passar.
            helper.fail("precisa das máquinas de teste (-Dwirelessautomate.gameTests=true)");
            return;
        }
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
        BlockState tank = TestMachines.SIMPLE_TANK.get().defaultBlockState();
        for (BlockPos pos : List.of(d, e, f)) {
            TestMachines.reset(helper.absolutePos(pos));
        }
        TestMachines.simpleTank(helper.absolutePos(d)).fill(new FluidStack(Fluids.WATER, 1_000),
                IFluidHandler.FluidAction.EXECUTE);
        RouterBlockEntity rd = place(helper, d, tank, x, y);
        RouterBlockEntity re = place(helper, e, tank, x, y);
        RouterBlockEntity rf = place(helper, f, tank, y, x);
        ra.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        rb.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        rc.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        rd.setMode(ResourceType.FLUID, Direction.UP, PortMode.EXTRACT);
        re.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);
        rf.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);
        ChestBlockEntity source = GameTestCompat.getBlockEntity(helper, a);
        source.setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, ra, rb, rc, rd, re, rf))
                // Todo nó tem algum tipo em cada rede.
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, nodes(helper, x), 6, "nós na rede X");
                    GameTestCompat.assertValueEqual(helper, nodes(helper, y), 6, "nós na rede Y");
                })
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, count(helper, b, Items.DIAMOND), 10, "diamantes em B");
                    GameTestCompat.assertValueEqual(helper, TestMachines.simpleTank(helper.absolutePos(e)).getFluidAmount(), 1_000,
                            "água em E");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, count(helper, c, Items.DIAMOND), 0, "itens cruzaram para a rede Y");
                    GameTestCompat.assertValueEqual(helper, TestMachines.simpleTank(helper.absolutePos(f)).getFluidAmount(), 0,
                            "fluidos cruzaram para a rede X");
                    GameTestCompat.assertValueEqual(helper, TestMachines.simpleTank(helper.absolutePos(d)).getFluidAmount(), 0,
                            "água ficou em D");
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
        ChestBlockEntity sourceChest = GameTestCompat.getBlockEntity(helper, a);
        sourceChest.setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenIdle(20)
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, count(helper, b, Items.DIAMOND), 0, "redes diferentes trocaram");
                    GameTestCompat.assertValueEqual(helper, nodes(helper, first), 1, "nós na rede 1");
                    GameTestCompat.assertValueEqual(helper, nodes(helper, second), 1, "nós na rede 2");
                    source.setNetworkId(ResourceType.ITEM, first);
                })
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, count(helper, b, Items.DIAMOND), 10, "diamantes em B"))
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, nodes(helper, first), 2, "nós na rede 1 depois da troca");
                    // Fluidos, energia e químicos do roteador A continuam na rede 2.
                    GameTestCompat.assertValueEqual(helper, nodes(helper, second), 1, "nós na rede 2 depois da troca");
                    source.setNetworkId(first);
                })
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, nodes(helper, second), 0, "rede 2 sem nós"))
                .thenSucceed();
    }

    /** O NBT antigo, com uma rede única em {@code network}, vale para todos os tipos; o novo salva por tipo. */
    @GameTest(template = "empty")
    public static void legacyNetworkTagAppliesToAllTypes(GameTestHelper helper) {
        BlockState state = ModBlocks.ROUTER.get().defaultBlockState();
        UUID legacy = UUID.randomUUID();
        CompoundTag old = new CompoundTag();
        old.putUUID("network", legacy);

        RouterBlockEntity router = new RouterBlockEntity(BlockPos.ZERO, state);
        router.load(old);
        for (ResourceType type : ResourceType.values()) {
            GameTestCompat.assertValueEqual(helper, router.networkId(type), legacy, "rede antiga em " + type);
        }

        UUID energy = UUID.randomUUID();
        router.setNetworkId(ResourceType.ENERGY, energy);
        router.setNetworkId(ResourceType.CHEMICAL, null);
        CompoundTag saved = router.saveWithoutMetadata();
        helper.assertFalse(saved.contains("network"), "salvou o formato antigo");
        RouterBlockEntity copy = new RouterBlockEntity(BlockPos.ZERO, state);
        copy.load(saved);
        GameTestCompat.assertValueEqual(helper, copy.networkId(ResourceType.ITEM), legacy, "itens");
        GameTestCompat.assertValueEqual(helper, copy.networkId(ResourceType.FLUID), legacy, "fluidos");
        GameTestCompat.assertValueEqual(helper, copy.networkId(ResourceType.ENERGY), energy, "energia");
        helper.assertTrue(copy.networkId(ResourceType.CHEMICAL) == null, "químicos sem rede");
        helper.succeed();
    }

    /**
     * Vinculador com o componente antigo {@code linker_type} em Energia (item da 0.1.0) põe só a
     * energia na rede ativa; sem componente (Todos), todas as abas que existem (Químicos só com o
     * Mekanism).
     */
    @GameTest(template = "empty")
    public static void linkerWithTypeLinksOnlyThatType(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = newNetwork(helper, "teste-vinculador-antes");
        RouterBlockEntity router = place(helper, new BlockPos(1, 1, 1), Blocks.CHEST.defaultBlockState(),
                before, before);
        @SuppressWarnings("removal")
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        WaNetwork active = data.create(player.getUUID(), "Base " + player.getUUID());
        data.setActiveNetwork(player.getUUID(), active.id());
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        ModDataComponents.LINKER_TYPE.set(linker, ResourceType.ENERGY);
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        try {
            helper.assertTrue(use(player, ModItems.LINKER.get(), router, false), "vinculador não agiu");
            GameTestCompat.assertValueEqual(helper, router.networkId(ResourceType.ENERGY), active.id(), "energia");
            GameTestCompat.assertValueEqual(helper, router.networkId(ResourceType.ITEM), before, "itens mudaram");
            GameTestCompat.assertValueEqual(helper, router.networkId(ResourceType.FLUID), before, "fluidos mudaram");
            GameTestCompat.assertValueEqual(helper, router.networkId(ResourceType.CHEMICAL), before, "químicos mudaram");

            ModDataComponents.LINKER_TYPE.remove(linker);
            GameTestCompat.assertValueEqual(helper, LinkerItem.tabs(linker), LinkerTabs.ALL, "sem componente não é Todos");
            use(player, ModItems.LINKER.get(), router, false);
            for (ResourceType type : LoadedTypes.LIST) {
                GameTestCompat.assertValueEqual(helper, router.networkId(type), active.id(), "Todos não vinculou " + type);
            }
        } finally {
            data.remove(active.id());
            data.remove(before);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Shift + roda: Todos → Itens → Fluidos → Energia (→ Químicos com o Mekanism) → Todos, e para
     * trás; uma combinação vai para Todos; só com o Vinculador na mão.
     */
    @GameTest(template = "empty")
    public static void cycleLinkerTypePayloadCycles(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        try {
            ItemStack linker = new ItemStack(ModItems.LINKER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, linker);
            List<LinkerTabs> forward = new ArrayList<>();
            for (ResourceType type : LoadedTypes.LIST) {
                forward.add(LinkerTabs.of(type));
            }
            forward.add(LinkerTabs.ALL);
            for (LinkerTabs expected : forward) {
                helper.assertTrue(ModPayloads.handleCycleLinkerType(player, new CycleLinkerTypePayload(1)),
                        "recusou avançar");
                GameTestCompat.assertValueEqual(helper, LinkerItem.tabs(linker), expected, "avançar");
            }
            helper.assertFalse(ModDataComponents.LINKER_TABS.has(linker), "Todos deixou o componente");
            helper.assertTrue(ModPayloads.handleCycleLinkerType(player, new CycleLinkerTypePayload(-1)),
                    "recusou voltar");
            GameTestCompat.assertValueEqual(helper, LinkerItem.tabs(linker),
                    LinkerTabs.of(Chemicals.LOADED ? ResourceType.CHEMICAL : ResourceType.ENERGY), "voltar de Todos");
            // uma combinação marcada na tela vai para Todos
            LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ITEM, ResourceType.FLUID));
            helper.assertTrue(ModPayloads.handleCycleLinkerType(player, new CycleLinkerTypePayload(1)),
                    "recusou avançar da combinação");
            GameTestCompat.assertValueEqual(helper, LinkerItem.tabs(linker), LinkerTabs.ALL, "combinação não foi para Todos");
            // o componente antigo vale como a aba dele e some na primeira troca
            ModDataComponents.LINKER_TYPE.set(linker, ResourceType.FLUID);
            helper.assertTrue(ModPayloads.handleCycleLinkerType(player, new CycleLinkerTypePayload(1)),
                    "recusou avançar do tipo antigo");
            GameTestCompat.assertValueEqual(helper, LinkerItem.tabs(linker), LinkerTabs.of(ResourceType.ENERGY), "depois de Fluidos");
            helper.assertFalse(ModDataComponents.LINKER_TYPE.has(linker), "o componente antigo ficou");
            helper.assertFalse(ModPayloads.handleCycleLinkerType(player, new CycleLinkerTypePayload(0)),
                    "aceitou direção 0");

            ItemStack other = new ItemStack(ModItems.CONFIGURATOR.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, other);
            helper.assertFalse(ModPayloads.handleCycleLinkerType(player, new CycleLinkerTypePayload(1)),
                    "aceitou sem o Vinculador na mão");
            helper.assertFalse(ModDataComponents.LINKER_TABS.has(other), "mexeu noutro item");
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
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
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
            RouterPreset preset = ModDataComponents.PRESET.get(configurator);
            helper.assertTrue(preset != null, "nada copiado");
            GameTestCompat.assertValueEqual(helper, preset.network(ResourceType.ITEM), lines.id(), "itens no preset");
            GameTestCompat.assertValueEqual(helper, preset.network(ResourceType.FLUID), fluids.id(), "fluidos no preset");
            GameTestCompat.assertValueEqual(helper, preset.network(ResourceType.ENERGY), foreign.id(), "energia no preset");
            helper.assertTrue(preset.network(ResourceType.CHEMICAL) == null, "químicos no preset");

            helper.assertTrue(use(player, ModItems.CONFIGURATOR.get(), target, false), "colar não agiu");
            GameTestCompat.assertValueEqual(helper, target.networkId(ResourceType.ITEM), lines.id(), "itens colados");
            GameTestCompat.assertValueEqual(helper, target.networkId(ResourceType.FLUID), fluids.id(), "fluidos colados");
            GameTestCompat.assertValueEqual(helper, target.networkId(ResourceType.ENERGY), targetNetwork.id(),
                    "colou a rede de outro dono");
            GameTestCompat.assertValueEqual(helper, target.networkId(ResourceType.CHEMICAL), targetNetwork.id(),
                    "tipo sem rede no preset mexeu na rede");

            // Codecs: por tipo vai e volta; o formato antigo (rede única) vale para todos os tipos.
            DynamicOps<Tag> ops = NbtOps.INSTANCE;
            Tag tag = RouterPreset.CODEC.encodeStart(ops, preset).getOrThrow(false, error -> {});
            GameTestCompat.assertValueEqual(helper, RouterPreset.CODEC.parse(ops, tag).getOrThrow(false, error -> {}), preset, "codec por tipo");
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());
            try {
                RouterPreset.STREAM_CODEC.encode(buf, preset);
                GameTestCompat.assertValueEqual(helper, RouterPreset.STREAM_CODEC.decode(buf), preset, "stream codec por tipo");
            } finally {
                buf.release();
            }
            CompoundTag legacy = new CompoundTag();
            legacy.putUUID("network", lines.id());
            RouterPreset old = RouterPreset.CODEC.parse(ops, legacy).getOrThrow(false, error -> {});
            for (ResourceType type : ResourceType.values()) {
                GameTestCompat.assertValueEqual(helper, old.network(type), lines.id(), "preset antigo em " + type);
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
