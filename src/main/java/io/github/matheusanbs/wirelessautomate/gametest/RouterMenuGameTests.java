package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Lado do servidor da tela do roteador: o snapshot, os handlers dos pacotes do cliente, a versão
 * de mudanças e a vazão. Os handlers são chamados direto, com um {@link RouterMenu} atribuído ao
 * {@code containerMenu} do jogador falso, e o menu volta ao inventário no fim do teste para o tick
 * do jogador não mexer nele.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RouterMenuGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final int CONTAINER_ID = 42;

    /** Baú em {@code machine} com um roteador em cima (facing=UP). */
    private static RouterBlockEntity chestWithRouter(GameTestHelper helper, BlockPos machine) {
        helper.setBlock(machine, Blocks.CHEST);
        BlockPos pos = machine.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        return helper.getBlockEntity(pos);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerNear(GameTestHelper helper, RouterBlockEntity router) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        return player;
    }

    private static RouterMenu openMenu(ServerPlayer player, RouterBlockEntity router) {
        RouterMenu menu = new RouterMenu(CONTAINER_ID, player.getInventory(), router,
                RouterSnapshot.capture(router, player));
        player.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
    }

    @GameTest(template = "empty")
    public static void snapshotReflectsRouter(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, CHEST);
        ServerPlayer player = playerNear(helper, router);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork own = data.create(player.getUUID(), "Própria");
        WaNetwork foreign = data.create(UUID.randomUUID(), "Alheia");
        router.setNetworkId(own.id());
        router.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 5, RedstoneMode.HIGH);
        router.setMode(ResourceType.FLUID, Direction.NORTH, PortMode.INSERT);
        router.setName("  Baú de minérios  ");

        RouterSnapshot snapshot = RouterSnapshot.capture(router, player);
        helper.assertValueEqual(snapshot.pos(), router.getBlockPos(), "posição");
        helper.assertValueEqual(snapshot.name(), "Baú de minérios", "nome aparado");
        helper.assertValueEqual(snapshot.tier(), RouterTier.BASIC, "tier");
        helper.assertValueEqual(snapshot.facing(), Direction.UP, "facing");
        helper.assertValueEqual(snapshot.network(ResourceType.ITEM), Optional.of(own.id()), "rede");
        helper.assertTrue(snapshot.networks().contains(new RouterSnapshot.NetworkEntry(own.id(), own.name(),
                own.color(), true)), "rede própria fora do seletor");
        helper.assertFalse(snapshot.networks().stream().anyMatch(e -> e.id().equals(foreign.id())),
                "rede alheia que não é a do roteador no seletor");
        helper.assertTrue(snapshot.machine().is(Items.CHEST), "ícone da máquina");
        helper.assertValueEqual(snapshot.faces().size(), ResourceType.values().length * 6, "faces");
        helper.assertValueEqual(snapshot.face(ResourceType.ITEM, Direction.UP),
                new RouterSnapshot.FaceView(PortMode.EXTRACT, 5, RedstoneMode.HIGH, 27, 0, false), "itens em cima");
        helper.assertValueEqual(snapshot.face(ResourceType.FLUID, Direction.NORTH).mode(), PortMode.INSERT,
                "fluido ao norte");
        helper.assertValueEqual(snapshot.face(ResourceType.FLUID, Direction.NORTH).slots(), -1, "baú sem tanque");
        helper.assertValueEqual(snapshot.face(ResourceType.ENERGY, Direction.UP).slots(), -1, "baú sem energia");
        helper.assertValueEqual(snapshot.face(ResourceType.CHEMICAL, Direction.UP).slots(), -1, "químicos");

        // Rede de outro dono como rede atual: aparece no seletor, sem ser do jogador.
        router.setNetworkId(foreign.id());
        RouterSnapshot other = RouterSnapshot.capture(router, player);
        helper.assertTrue(other.networks().contains(new RouterSnapshot.NetworkEntry(foreign.id(), foreign.name(),
                foreign.color(), false)), "rede atual alheia fora do seletor");

        // Rede removida: o snapshot fica sem rede.
        data.remove(foreign.id());
        helper.assertValueEqual(RouterSnapshot.capture(router, player).network(ResourceType.ITEM), Optional.<UUID>empty(),
                "rede removida");

        // Ida e volta pelo codec de rede.
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
        RouterSnapshot decoded = RouterSnapshot.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded.faces(), snapshot.faces(), "faces pelo codec");
        helper.assertValueEqual(decoded.networks(), snapshot.networks(), "redes pelo codec");
        helper.assertValueEqual(decoded.name(), snapshot.name(), "nome pelo codec");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void setFaceNeedsTheOpenMenu(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, CHEST);
        ServerPlayer player = playerNear(helper, router);
        RouterMenu menu = openMenu(player, router);
        try {
            helper.assertTrue(ModPayloads.handleSetFace(player, new SetFacePayload(menu.containerId,
                    ResourceType.ITEM, Direction.UP, PortMode.INSERT, 5000, RedstoneMode.LOW)), "recusou com a tela aberta");
            FaceConfig face = router.face(ResourceType.ITEM, Direction.UP);
            helper.assertValueEqual(face.mode(), PortMode.INSERT, "modo");
            helper.assertValueEqual(face.priority(), RouterMenu.MAX_PRIORITY, "prioridade limitada");
            helper.assertValueEqual(face.redstone(), RedstoneMode.LOW, "redstone");

            SetFacePayload other = new SetFacePayload(menu.containerId + 1, ResourceType.ITEM, Direction.UP,
                    PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
            helper.assertFalse(ModPayloads.handleSetFace(player, other), "aceitou outro containerId");
            helper.assertFalse(ModPayloads.handleSetFace(player, new SetFacePayload(menu.containerId,
                    ResourceType.CHEMICAL, Direction.UP, PortMode.EXTRACT, 0, RedstoneMode.IGNORE)), "aceitou químicos");

            // Jogador longe demais: a tela não vale mais.
            player.moveTo(Vec3.atCenterOf(router.getBlockPos()).add(0, 20, 0));
            helper.assertFalse(ModPayloads.handleSetFace(player, new SetFacePayload(menu.containerId,
                    ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0, RedstoneMode.IGNORE)), "aceitou de longe");
            player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));

            // Menu de outro tipo com o mesmo id.
            close(player);
            helper.assertFalse(ModPayloads.handleSetFace(player, new SetFacePayload(player.inventoryMenu.containerId,
                    ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0, RedstoneMode.IGNORE)), "aceitou sem a tela");
            helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT,
                    "face mudou sem a tela");
        } finally {
            close(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void setNetworkChecksPermission(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, CHEST);
        ServerPlayer player = playerNear(helper, router);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork own = data.create(player.getUUID(), "Minha");
        WaNetwork foreign = data.create(UUID.randomUUID(), "Do vizinho");
        RouterMenu menu = openMenu(player, router);
        try {
            helper.assertFalse(player.hasPermissions(2), "jogador falso é operador");
            helper.assertTrue(ModPayloads.handleSetNetwork(player,
                    new SetNetworkPayload(menu.containerId, ResourceType.ITEM, Optional.of(own.id()))), "recusou a própria rede");
            helper.assertValueEqual(router.networkId(ResourceType.ITEM), own.id(), "rede própria");
            helper.assertFalse(ModPayloads.handleSetNetwork(player,
                    new SetNetworkPayload(menu.containerId, ResourceType.ITEM, Optional.of(foreign.id()))), "aceitou rede alheia");
            helper.assertFalse(ModPayloads.handleSetNetwork(player,
                    new SetNetworkPayload(menu.containerId, ResourceType.ITEM, Optional.of(UUID.randomUUID()))), "aceitou rede inexistente");
            helper.assertValueEqual(router.networkId(ResourceType.ITEM), own.id(), "rede mudou numa recusa");
            helper.assertTrue(ModPayloads.handleSetNetwork(player,
                    new SetNetworkPayload(menu.containerId, ResourceType.ITEM, Optional.empty())), "recusou tirar da rede");
            helper.assertTrue(router.networkId(ResourceType.ITEM) == null, "continuou na rede");

            helper.assertTrue(ModPayloads.handleRename(player, new RenameRouterPayload(menu.containerId, " Forno ")),
                    "recusou o nome");
            helper.assertValueEqual(router.name(), "Forno", "nome");
            helper.assertFalse(ModPayloads.handleRename(player, new RenameRouterPayload(menu.containerId,
                    "x".repeat(RenameRouterPayload.MAX_LENGTH + 1))), "aceitou nome comprido");
            helper.assertValueEqual(router.name(), "Forno", "nome mudou numa recusa");
        } finally {
            close(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void changeVersionTracksWhatTheScreenShows(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, CHEST);
        ServerPlayer player = playerNear(helper, router);
        // Sem atribuir ao containerMenu: o tick do jogador não chama este menu.
        RouterMenu menu = new RouterMenu(CONTAINER_ID, player.getInventory(), router,
                RouterSnapshot.capture(router, player));
        helper.assertTrue(menu.pollSnapshot() == null, "snapshot sem mudança");

        int version = router.changeVersion();
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        helper.assertTrue(router.changeVersion() != version, "versão não mudou com a face");
        RouterSnapshot changed = menu.pollSnapshot();
        helper.assertTrue(changed != null && changed.face(ResourceType.ITEM, Direction.UP).mode() == PortMode.EXTRACT,
                "snapshot não refletiu a face");
        helper.assertTrue(menu.pollSnapshot() == null, "snapshot repetido");

        version = router.changeVersion();
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        helper.assertValueEqual(router.changeVersion(), version, "versão mudou sem mudança");
        router.setName("Nó");
        helper.assertTrue(router.changeVersion() != version, "versão não mudou com o nome");
        helper.assertValueEqual(menu.pollSnapshot().name(), "Nó", "nome no snapshot");

        // Rede nova do jogador muda o seletor.
        WaNetwork created = NetworkSavedData.get(helper.getLevel().getServer()).create(player.getUUID(), "Nova");
        RouterSnapshot withNetwork = menu.pollSnapshot();
        helper.assertTrue(withNetwork != null
                && withNetwork.networks().stream().anyMatch(e -> e.id().equals(created.id())), "seletor sem a rede nova");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void throughputCountsTheSource(GameTestHelper helper) {
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "Vazão").id();
        RouterBlockEntity source = chestWithRouter(helper, A);
        RouterBlockEntity target = chestWithRouter(helper, B);
        source.setNetworkId(network);
        target.setNetworkId(network);
        source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        target.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        ChestBlockEntity chest = helper.getBlockEntity(A);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
        ServerPlayer player = playerNear(helper, source);
        RouterMenu menu = new RouterMenu(CONTAINER_ID, player.getInventory(), source,
                RouterSnapshot.capture(source, player));
        long start = helper.getLevel().getServer().getTickCount();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source), "não registrado"))
                .thenWaitUntil(() -> helper.assertValueEqual(source.moved(ResourceType.ITEM), 10L, "movidos pela origem"))
                .thenExecute(() -> {
                    helper.assertValueEqual(target.moved(ResourceType.ITEM), 0L, "destino contou como origem");
                    helper.assertTrue(menu.pollThroughput(start + RouterMenu.SAMPLE_TICKS - 1) == null,
                            "amostra antes de um segundo");
                    long[] rates = menu.pollThroughput(start + RouterMenu.SAMPLE_TICKS);
                    helper.assertTrue(rates != null, "sem amostra depois de um segundo");
                    helper.assertValueEqual(rates[ResourceType.ITEM.ordinal()], 10L, "itens/s");
                    helper.assertValueEqual(rates[ResourceType.FLUID.ordinal()], 0L, "mB/s");
                    long[] idle = menu.pollThroughput(start + 2 * RouterMenu.SAMPLE_TICKS);
                    helper.assertTrue(idle != null && idle[ResourceType.ITEM.ordinal()] == 0, "vazão não zerou");
                    helper.assertTrue(menu.pollThroughput(start + 3 * RouterMenu.SAMPLE_TICKS) == null,
                            "reenviou vazão igual");
                })
                .thenSucceed();
    }
}
