package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TabletMapPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Os pontos do Mapa do Tablet ({@link TabletMapPayload}): todos os roteadores que o jogador vê, sem depender da busca
 * nem dos filtros da Lista, só com a aba Mapa aberta, só quando mudam, e a ida e volta pelo pacote. Os testes com nós
 * em rede pública alheia ficam no lote deles, porque o Tablet os mostra a todo jogador.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletMapGameTests {
    private static final String PUBLIC_NODES_BATCH = "wirelessautomate_public_nodes";
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);

    private static RouterBlockEntity chestWithRouter(GameTestHelper helper, BlockPos machine, UUID network) {
        helper.setBlock(machine, Blocks.CHEST);
        BlockPos pos = machine.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        router.setNetworkId(network);
        return router;
    }

    private static ServerPlayer playerWithTablet(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(A).above(2)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player;
    }

    private static TabletMenu open(ServerPlayer player) {
        TabletMenu menu = new TabletMenu(95, player.getInventory(), player);
        player.containerMenu = menu;
        return menu;
    }

    private static @Nullable TabletMapPayload mapIn(List<CustomPacketPayload> payloads) {
        for (CustomPacketPayload payload : payloads) {
            if (payload instanceof TabletMapPayload map) {
                return map;
            }
        }
        return null;
    }

    private static boolean has(TabletMapPayload map, RouterBlockEntity router) {
        long pos = router.getBlockPos().asLong();
        for (long p : map.positions()) {
            if (p == pos) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = "empty", batch = PUBLIC_NODES_BATCH)
    public static void theMapSendsEveryVisibleNodeWhateverTheListQueryIs(GameTestHelper helper) {
        ServerPlayer friend = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork shared = data.create(UUID.randomUUID(), "teste-mapa-publica").withPublic(true);
        data.update(shared);
        WaNetwork hidden = data.create(UUID.randomUUID(), "teste-mapa-privada");
        RouterBlockEntity first = chestWithRouter(helper, A, shared.id());
        RouterBlockEntity second = chestWithRouter(helper, B, shared.id());
        RouterBlockEntity secret = chestWithRouter(helper, B.east(2), hidden.id());
        TabletMenu menu = open(friend);
        try {
            // uma busca que não acha nada esvazia a página da Lista, mas não o Mapa
            menu.setQuery(Query.of("nada-assim-xyz"));
            helper.assertTrue(menu.setTab(TabletMenu.MAP_TAB), "a aba Mapa não existe");
            List<CustomPacketPayload> payloads = menu.pollPayloads();
            helper.assertTrue(menu.snapshot().nodes().isEmpty(), "a busca não esvaziou a página da Lista");
            TabletMapPayload map = mapIn(payloads);
            helper.assertTrue(map != null, "o Mapa não recebeu os pontos");
            helper.assertTrue(has(map, first) && has(map, second), "um roteador da rede pública não está no Mapa");
            helper.assertFalse(has(map, secret), "o Mapa trouxe um roteador de rede privada alheia");
            helper.assertTrue(map.count() == map.positions().length && map.total() >= 2, "contagem do Mapa");
            helper.assertTrue(map.status(0) != null, "estado do ponto");
            helper.succeed();
        } finally {
            friend.containerMenu = friend.inventoryMenu;
            data.remove(shared.id());
            data.remove(hidden.id());
        }
    }

    @GameTest(template = "empty", batch = PUBLIC_NODES_BATCH)
    public static void theMapOnlyComesWithTheMapTabAndOnlyWhenItChanges(GameTestHelper helper) {
        ServerPlayer friend = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork shared = data.create(UUID.randomUUID(), "teste-mapa-aba").withPublic(true);
        data.update(shared);
        chestWithRouter(helper, A, shared.id());
        TabletMenu menu = open(friend);
        try {
            helper.assertTrue(mapIn(menu.pollPayloads()) == null, "pontos do Mapa fora da aba Mapa");
            menu.setTab(TabletMenu.MAP_TAB);
            helper.assertTrue(mapIn(menu.pollPayloads()) != null, "a aba Mapa não recebeu os pontos");
            helper.assertTrue(mapIn(menu.pollPayloads()) == null, "os pontos vieram de novo sem mudar nem passar a amostra");
            menu.setTab(0);
            helper.assertTrue(mapIn(menu.pollPayloads()) == null, "pontos depois de sair da aba");
            menu.setTab(TabletMenu.MAP_TAB);
            helper.assertTrue(mapIn(menu.pollPayloads()) != null, "ao voltar à aba, os pontos não vieram de novo");
            helper.succeed();
        } finally {
            friend.containerMenu = friend.inventoryMenu;
            data.remove(shared.id());
        }
    }

    @GameTest(template = "empty")
    public static void theMapPayloadSurvivesTheWire(GameTestHelper helper) {
        TabletMapPayload original = new TabletMapPayload(7, 3, 5000,
                new long[] {BlockPos.asLong(1, 2, 3), BlockPos.asLong(-40, 70, 900)},
                new byte[] {(byte) NodeStatus.ACTIVE.ordinal(), (byte) NodeStatus.UNLOADED.ordinal()});
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TabletMapPayload.STREAM_CODEC.encode(buf, original);
        TabletMapPayload decoded = TabletMapPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(decoded.equals(original), "o pacote mudou na ida e volta");
        helper.assertTrue(decoded.status(1) == NodeStatus.UNLOADED, "estado do segundo ponto");
        helper.assertTrue(decoded.otherDimension() == 3 && decoded.total() == 5000, "contagens");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aMapPayloadWithMorePointsThanTheCapIsRefused(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        buf.writeVarInt(1);
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        buf.writeVarInt(TabletMapPayload.MAX_POINTS + 1);
        boolean refused = false;
        try {
            TabletMapPayload.STREAM_CODEC.decode(buf);
        } catch (io.netty.handler.codec.DecoderException e) {
            refused = true;
        }
        helper.assertTrue(refused, "um pacote com pontos demais foi aceito");
        helper.succeed();
    }
}
