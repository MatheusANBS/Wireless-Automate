package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.Group;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.Row;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.menu.TabletTree;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.packet.TabletTreePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletTreePayload.Action;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O Tablet lembra a árvore da Lista ao reabrir ({@link TabletMenu.State}): os roteadores abertos, os grupos
 * recolhidos e os com "Mostrar todas" voltam na primeira página do snapshot de abertura, e o logout esquece.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletTreeRememberGameTests {
    private TabletTreeRememberGameTests() {
    }

    private static final ResourceLocation CHEST_ID = BuiltInRegistries.BLOCK.getKey(Blocks.CHEST);

    private static ServerPlayer playerWithTablet(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 4, 1.5)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player;
    }

    private static boolean tree(ServerPlayer player, TabletMenu menu, Action action, NodeKey node, int group) {
        return TabletPayloads.handleTree(player, new TabletTreePayload(menu.containerId, action, node, group));
    }

    private static void fakeLinks(RouterBlockEntity router, GameTestHelper helper, int count) {
        for (int i = 0; i < count; i++) {
            router.remoteMachines().link(helper.absolutePos(new BlockPos(20 + i, 1, 0)), CHEST_ID, -1, null);
        }
        NodeIndex.track(router);
    }

    /** Fechar e reabrir mantém o aberto, o recolhido e o "Mostrar todas", já na página do snapshot de abertura. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reopenKeepsTree(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        ServerPlayer player = playerWithTablet(helper);
        String name = "lemb" + Math.floorMod(helper.absolutePos(BlockPos.ZERO).hashCode(), 1_000_000);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setName(name);
                    fakeLinks(router, helper, 10);
                    NodeIndex.placedBy(router, player.getUUID());
                    NodeKey key = NodeKey.of(router.getLevel(), router.getBlockPos());
                    int group = router.remoteMachines().machine(0).group();

                    TabletMenu first = new TabletMenu(150, player.getInventory(), player);
                    player.containerMenu = first;
                    first.setQuery(Query.of(name));
                    helper.assertTrue(tree(player, first, Action.OPEN, key, 0), "não abriu");
                    helper.assertTrue(tree(player, first, Action.SHOW_ALL, key, group), "não mostrou todas");
                    first.removed(player);

                    TabletMenu second = new TabletMenu(151, player.getInventory(), player);
                    List<Row> rows = second.snapshot().page().rows().rows();
                    helper.assertTrue(rows.size() == 1 + 1 + 10 && rows.get(1) instanceof Group g && g.showAll(),
                            "a primeira página não veio com a árvore aberta e o Mostrar todas: " + rows.size());
                    player.containerMenu = second;
                    helper.assertTrue(tree(player, second, Action.COLLAPSE_GROUP, key, group), "não recolheu");
                    second.removed(player);

                    TabletMenu third = new TabletMenu(152, player.getInventory(), player);
                    rows = third.snapshot().page().rows().rows();
                    helper.assertTrue(rows.size() == 1 + 1 && rows.get(1) instanceof Group g && g.collapsed()
                            && g.showAll(), "o grupo recolhido não voltou: " + rows.size());
                    helper.assertTrue(third.treeState().isOpenForTests(key), "o roteador não voltou aberto");
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    /** O logout esquece a árvore com o resto do estado, e o que voltou respeita os tetos. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void logoutForgetsAndLimitsHold(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        TabletMenu menu = new TabletMenu(153, player.getInventory(), player);
        player.containerMenu = menu;
        TabletTree tree = menu.treeState();
        for (int i = 0; i < TabletTree.MAX_OPEN; i++) {
            NodeKey fake = new NodeKey(helper.getLevel().dimension(), new BlockPos(i, -50, 9));
            tree.openForTests(fake);
            tree.collapseForTests(new TabletTree.GroupKey(fake, 1));
        }
        menu.removed(player);
        TabletMenu.State saved = TabletMenu.savedState(player.getUUID());
        helper.assertTrue(saved != null && saved.tree().open().size() == TabletTree.MAX_OPEN
                && saved.tree().collapsed().size() == TabletTree.MAX_OPEN, "não guardou a árvore");
        TabletMenu.forget(player.getUUID());
        helper.assertTrue(TabletMenu.savedState(player.getUUID()) == null, "o logout não esqueceu");
        TabletMenu fresh = new TabletMenu(154, player.getInventory(), player);
        helper.assertTrue(fresh.treeState().openCountForTests() == 0, "abriu com a árvore de antes do logout");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }
}
