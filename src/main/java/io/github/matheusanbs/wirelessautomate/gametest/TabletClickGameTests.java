package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletClick;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Do mundo para a tela (etapa 2 da central): o clique do Tablet numa máquina abre o nó dela, e o Vinculador
 * diz a máquina e o grupo.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletClickGameTests {
    private static TestPlayers.MenuPlayer tabletPlayer(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player;
    }

    private static boolean click(GameTestHelper helper, TestPlayers.MenuPlayer player, BlockPos relative) {
        return TabletClick.open(player, helper.getLevel(), helper.absolutePos(relative));
    }

    private static void linkTwo(GameTestHelper helper, RouterBlockEntity router) {
        router.setRangeUpgrade(rangeUpgrade());
        link(helper, router, FURNACE_A);
        link(helper, router, FURNACE_B);
    }

    /** Tablet numa remota: abre a Lista no nó do roteador com o alvo MACHINE daquela máquina. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void tabletOnRemoteOpensItsNode(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        TestPlayers.MenuPlayer player = tabletPlayer(helper);
        NodeIndex.placedBy(router, player.getUUID());
        BlockPos furnaceB = helper.absolutePos(FURNACE_B);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkTwo(helper, router);
                    // o caminho real do jogo: o forno abriria a interface dele, e o Tablet vem antes
                    BlockPos pos = furnaceB;
                    player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().getItem(0));
                    InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(),
                            player.getMainHandItem(), InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                    helper.assertTrue(result == InteractionResult.SUCCESS, "useItemOn devolveu " + result);
                    helper.assertTrue(player.containerMenu instanceof TabletMenu, "não abriu o menu do Tablet");
                    TabletMenu.State state = TabletMenu.savedState(player.getUUID());
                    helper.assertTrue(state != null, "sem estado gravado");
                    helper.assertTrue(Objects.equals(state.chosen(), NodeKey.of(helper.getLevel(), router.getBlockPos())),
                            "o nó escolhido");
                    helper.assertTrue(state.target().target().equals(
                            EditTarget.machine(router.remoteMachines().index(furnaceB))), "o alvo não é a máquina");
                    helper.assertTrue(furnaceB.equals(state.target().pos()), "a posição do alvo");
                    helper.assertTrue(state.tab() == 0, "a aba não é a Lista");
                })
                .thenSucceed();
    }

    /** Tablet na máquina presa: o nó, com o alvo da presa. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void tabletOnAttachedOpensItsNode(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        TestPlayers.MenuPlayer player = tabletPlayer(helper);
        NodeIndex.placedBy(router, player.getUUID());
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    helper.assertTrue(click(helper, player, CHEST), "o Tablet não abriu o nó");
                    TabletMenu.State state = TabletMenu.savedState(player.getUUID());
                    helper.assertTrue(state != null
                            && Objects.equals(state.chosen(), NodeKey.of(helper.getLevel(), router.getBlockPos())),
                            "o nó escolhido");
                    helper.assertTrue(state.target().target().attached(), "o alvo não é a presa");
                })
                .thenSucceed();
    }

    private static InteractionResult useTablet(GameTestHelper helper, TestPlayers.MenuPlayer player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static boolean notLinkedNotice(TestPlayers.MenuPlayer player) {
        return player.actionBar != null && player.actionBar.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("item.wirelessautomate.network_tablet.not_linked");
    }

    /**
     * Sem roteador que o jogador veja, pelo caminho real do jogo: o forno vinculado a um roteador alheio abre a
     * interface dele e nunca o Tablet; a composteira (capability e nenhuma interface) mostra o aviso na barra de ação.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void tabletWithoutAccessOpensNothing(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        BlockPos composter = new BlockPos(2, 1, 2);
        helper.setBlock(composter, Blocks.COMPOSTER);
        TestPlayers.MenuPlayer player = tabletPlayer(helper);
        NodeIndex.placedBy(router, UUID.randomUUID());
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkTwo(helper, router);
                    useTablet(helper, player, FURNACE_A);
                    useTablet(helper, player, CHEST);
                    helper.assertFalse(player.containerMenu instanceof TabletMenu, "abriu o nó alheio");
                    TabletMenu.State state = TabletMenu.savedState(player.getUUID());
                    helper.assertTrue(state == null || state.chosen() == null, "escolheu o nó alheio");
                    player.actionBar = null;
                    player.containerMenu = player.inventoryMenu;
                    useTablet(helper, player, composter);
                    helper.assertTrue(player.containerMenu == player.inventoryMenu, "a composteira abriu um menu");
                    helper.assertTrue(notLinkedNotice(player), "sem o aviso na barra de ação");
                })
                .thenSucceed();
    }

    /** O Tablet clicado num roteador abre a tela do roteador, não o Tablet. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void tabletOnRouterOpensRouterScreen(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        TestPlayers.MenuPlayer player = tabletPlayer(helper);
        NodeIndex.placedBy(router, player.getUUID());
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    useTablet(helper, player, ROUTER);
                    helper.assertTrue(player.containerMenu instanceof RouterMenu, "não abriu a tela do roteador");
                })
                .thenSucceed();
    }

    /** Depois de o clique numa remota abrir o Tablet, o {@code use()} que o cliente manda em seguida não reabre. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void tabletUseAfterClickDoesNotReopen(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        TestPlayers.MenuPlayer player = tabletPlayer(helper);
        NodeIndex.placedBy(router, player.getUUID());
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkTwo(helper, router);
                    useTablet(helper, player, FURNACE_B);
                    helper.assertTrue(player.containerMenu instanceof TabletMenu, "o clique não abriu o Tablet");
                    int id = player.containerMenu.containerId;
                    player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(),
                            InteractionHand.MAIN_HAND);
                    helper.assertTrue(player.containerMenu instanceof TabletMenu
                            && player.containerMenu.containerId == id, "o use() reabriu o Tablet");
                })
                .thenSucceed();
    }

    /** A mensagem do Vinculador leva a máquina (rótulo, ou "bloco #N") e o grupo, ao vincular e ao desvincular. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void linkerMessageNamesMachineAndGroup(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        BlockPos furnace = helper.absolutePos(FURNACE_A);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                    helper.assertValueEqual(RangeLinks.toggle(player, linker, furnace).status(),
                            RangeLinks.Status.LINKED, "vincular");
                    Object[] args = messageArgs(helper, player, "item.wirelessautomate.linker.range.linked");
                    helper.assertTrue(args[0] instanceof Component machine && machine.getContents()
                            instanceof TranslatableContents name
                            && name.getKey().equals("gui.wirelessautomate.router.range.machine.name")
                            && name.getArgs()[1].equals(1), "sem rótulo: bloco #1, veio " + args[0]);
                    helper.assertTrue(args[1] instanceof Component group
                            && group.getString().equals(Blocks.FURNACE.getName().getString()), "grupo sem nome: o bloco");

                    router.renameTarget(EditTarget.machine(router.remoteMachines().index(furnace)), "Forno do ferro");
                    router.renameTarget(EditTarget.group(groupOf(router, furnace)), "Fornos");
                    helper.assertValueEqual(RangeLinks.toggle(player, linker, furnace).status(),
                            RangeLinks.Status.UNLINKED, "desvincular");
                    args = messageArgs(helper, player, "item.wirelessautomate.linker.range.unlinked");
                    helper.assertTrue(args[0] instanceof Component machine && machine.getString().equals("Forno do ferro"),
                            "rótulo, veio " + args[0]);
                    // grupo com nome e máquina sem rótulo: "Fornos #N"
                    BlockPos other = helper.absolutePos(FURNACE_B);
                    helper.assertValueEqual(RangeLinks.toggle(player, linker, other).status(),
                            RangeLinks.Status.LINKED, "vincular a segunda");
                    router.renameTarget(EditTarget.group(groupOf(router, other)), "Fornos");
                    helper.assertValueEqual(RangeLinks.toggle(player, linker, furnace).status(),
                            RangeLinks.Status.LINKED, "vincular de novo");
                    args = messageArgs(helper, player, "item.wirelessautomate.linker.range.linked");
                    helper.assertTrue(args[0] instanceof Component m && m.getContents() instanceof TranslatableContents n
                            && n.getArgs()[0] instanceof Component g && g.getString().equals("Fornos"),
                            "sem rótulo: o nome do grupo, veio " + args[0]);
                    helper.assertTrue(args[1] instanceof Component group && group.getString().equals("Fornos"),
                            "nome do grupo, veio " + args[1]);
                })
                .thenSucceed();
    }

    private static Object[] messageArgs(GameTestHelper helper, TestPlayers.MenuPlayer player, String key) {
        helper.assertTrue(player.actionBar != null && player.actionBar.getContents() instanceof TranslatableContents c
                && c.getKey().equals(key), "mensagem " + key);
        return ((TranslatableContents) player.actionBar.getContents()).getArgs();
    }
}
