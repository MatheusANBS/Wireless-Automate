package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.DockMenu;
import io.github.matheusanbs.wirelessautomate.packet.DockStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A tela da Doca de Padrões: o clique que abre, o estado que vai na abertura e depois só com mudança (no máximo uma vez
 * por segundo), as contagens de um lado, a ida e volta pelo pacote e nada com a tela fechada.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockMenuGameTests {
    private static final BlockPos DOCK = new BlockPos(2, 1, 2);
    private static final int CONTAINER_ID = 1;
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation FURNACE = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);

    private DockMenuGameTests() {
    }

    /** A Doca Básica no teste, já com o block entity carregado. */
    private static void dock(GameTestHelper helper, java.util.function.Consumer<PatternDockBlockEntity> body) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(DOCK) instanceof PatternDockBlockEntity,
                        "sem a Doca"))
                .thenExecute(() -> body.accept(helper.getBlockEntity(DOCK)))
                .thenSucceed();
    }

    private static DockMenu openMenu(ServerPlayer player, PatternDockBlockEntity dock) {
        DockMenu menu = new DockMenu(CONTAINER_ID, player.getInventory(), dock);
        player.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer player) {
        player.containerMenu.removed(player);
        player.containerMenu = player.inventoryMenu;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cliqueAbreOMenu(GameTestHelper helper) {
        dock(helper, dock -> {
            TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
            player.moveTo(Vec3.atBottomCenterOf(dock.getBlockPos().east()));
            try {
                dock.getBlockState().useWithoutItem(helper.getLevel(), player,
                        ResonatorTestSupport.hit(dock.getBlockPos()));
                helper.assertTrue(player.containerMenu instanceof DockMenu menu && menu.pos().equals(dock.getBlockPos()),
                        "o clique não abriu o menu: " + player.containerMenu);
                helper.assertTrue(player.openData != null, "a abertura não escreveu os dados");
                DockMenu client = new DockMenu(CONTAINER_ID, player.getInventory(), player.openData);
                helper.assertValueEqual(client.pos(), dock.getBlockPos(), "posição no cliente");
                helper.assertValueEqual(client.slots.size(), 0, "slots no cliente");
            } finally {
                close(player);
            }
        });
    }

    /** O primeiro sai na abertura; sem mudança, nada; depois de um vínculo, sai na próxima conferência. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void estadoNaAberturaEDepoisSoComMudanca(GameTestHelper helper) {
        dock(helper, dock -> {
            ServerPlayer player = TestPlayers.inLevel(helper);
            try {
                DockMenu menu = openMenu(player, dock);
                DockStatePayload first = menu.pollState(0);
                helper.assertTrue(first != null, "o primeiro estado não saiu");
                helper.assertValueEqual(first.lines().size(), PatternDockBlockEntity.SIDES, "linhas");
                helper.assertValueEqual(first.total(), 0, "total");
                helper.assertValueEqual(first.max(), Config.dockMaxMachines(RouterTier.BASIC), "máximo");
                helper.assertValueEqual(first.radius(), Config.dockRadius(RouterTier.BASIC), "raio");
                for (DockStatePayload.Line line : first.lines()) {
                    helper.assertTrue(line.block() == null && line.linked() == 0
                            && line.state() == DockStatus.State.FREE.ordinal(), "lado novo não está livre: " + line);
                }
                helper.assertTrue(menu.pollState(5) == null, "saiu de novo dentro do intervalo");
                helper.assertTrue(menu.pollState(DockMenu.SYNC_INTERVAL) == null, "saiu sem mudar");
                BlockPos furnace = helper.absolutePos(DOCK.east(2));
                helper.getLevel().setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
                helper.assertTrue(dock.link(NORTH, furnace, FURNACE, -1), "não vinculou");
                helper.assertTrue(menu.pollState(DockMenu.SYNC_INTERVAL + 5) == null, "saiu antes do intervalo");
                DockStatePayload linked = menu.pollState(2L * DockMenu.SYNC_INTERVAL);
                helper.assertTrue(linked != null, "o vínculo não saiu");
                DockStatePayload.Line north = linked.lines().get(NORTH);
                helper.assertTrue(Objects.equals(north.block(), FURNACE) && north.linked() == 1
                        && north.outOfReach() == 0 && north.unloaded() == 0, "lado Norte: " + north);
                helper.assertValueEqual(north.machine().getString(), Blocks.FURNACE.getName().getString(), "nome");
                helper.assertTrue(north.provider() == null, "provedor sem AE2: " + north.provider());
                helper.assertValueEqual(north.state(), DockStatus.State.NO_PROVIDER.ordinal(), "estado");
                helper.assertValueEqual(linked.total(), 1, "total");
                helper.assertTrue(menu.pollState(4L * DockMenu.SYNC_INTERVAL) == null, "saiu de novo sem mudar");
            } finally {
                close(player);
            }
        });
    }

    /** Trabalhando nos últimos 15 s e fora do alcance, contados por lado. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void contaTrabalhandoEForaDoAlcance(GameTestHelper helper) {
        dock(helper, dock -> {
            ServerPlayer player = TestPlayers.inLevel(helper);
            try {
                DockMenu menu = openMenu(player, dock);
                BlockPos near = helper.absolutePos(DOCK.east(2));
                BlockPos far = helper.absolutePos(DOCK.east(Config.dockRadius(RouterTier.BASIC) + 2));
                dock.link(NORTH, near, FURNACE, -1);
                dock.link(NORTH, far, FURNACE, -1);
                long now = 1_000;
                dock.machineAt(near).setLastActive(now - 10);
                dock.machineAt(far).setLastActive(now - DockStatus.WORKING_TICKS - 1);
                DockStatePayload state = menu.pollState(now);
                DockStatePayload.Line north = state.lines().get(NORTH);
                helper.assertTrue(north.linked() == 2 && north.working() == 1 && north.outOfReach() == 1,
                        "lado Norte: " + north);
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void idaEVoltaPeloPacote(GameTestHelper helper) {
        dock(helper, dock -> {
            ServerPlayer player = TestPlayers.inLevel(helper);
            try {
                DockMenu menu = openMenu(player, dock);
                dock.link(NORTH, helper.absolutePos(DOCK.east(2)), FURNACE, -1);
                DockStatePayload state = menu.pollState(0);
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                        helper.getLevel().registryAccess());
                DockStatePayload.STREAM_CODEC.encode(buf, state);
                DockStatePayload back = DockStatePayload.STREAM_CODEC.decode(buf);
                helper.assertTrue(state.equals(back), "a ida e volta mudou o estado: " + state + " → " + back);
                helper.assertValueEqual(buf.readableBytes(), 0, "sobrou no pacote");
            } finally {
                close(player);
            }
        });
    }

    /** Fechada, nada sai mesmo com mudança; o menu do cliente nunca monta estado. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nadaComATelaFechada(GameTestHelper helper) {
        dock(helper, dock -> {
            ServerPlayer player = TestPlayers.inLevel(helper);
            DockMenu menu = openMenu(player, dock);
            helper.assertTrue(menu.pollState(0) != null, "o primeiro estado não saiu");
            close(player);
            dock.link(NORTH, helper.absolutePos(DOCK.east(2)), FURNACE, -1);
            helper.assertTrue(menu.pollState(10L * DockMenu.SYNC_INTERVAL) == null, "saiu com a tela fechada");
            DockMenu client = new DockMenu(CONTAINER_ID, dock.getBlockPos());
            helper.assertTrue(client.pollState(0) == null, "o menu do cliente montou estado");
        });
    }
}
