package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerOpen;
import io.github.matheusanbs.wirelessautomate.menu.RemoteRouterMenu;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O modo Abrir do Vinculador: o clique numa máquina abre a tela do roteador que chega nela (preso ou de alcance),
 * os candidatos respeitam o alcance do tier e as proteções de outros mods, e a escolha na lista é recalculada no
 * servidor. O roteador de alcance fica no baú (1, 1, 1); a fornalha A é a máquina clicada.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class LinkerOpenGameTests {
    private static ItemStack openLinker() {
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        LinkerItem.setMode(linker, LinkerMode.OPEN);
        return linker;
    }

    /** Um jogador que abre telas sem mandar o pacote da NeoForge (a conexão falsa não negocia canais), com o Vinculador em Abrir. */
    private static ServerPlayer openPlayer(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.menuOpener(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, openLinker());
        return player;
    }

    /** Um roteador preso na fornalha A (por cima dela), sem upgrade: o segundo caminho até a mesma máquina. */
    private static RouterBlockEntity attachedToFurnaceA(GameTestHelper helper) {
        BlockPos above = FURNACE_A.above();
        helper.setBlock(above, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        return helper.getBlockEntity(above);
    }

    /** O roteador de alcance só com a fornalha A vinculada: um candidato, que abre direto na máquina. */
    @GameTest(template = "empty")
    public static void singleRemoteRouterOpensOnTheMachine(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = openPlayer(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    BlockPos furnace = helper.absolutePos(FURNACE_A);
                    List<RouterBlockEntity> candidates = LinkerOpen.candidates(player, furnace, InteractionHand.MAIN_HAND);
                    helper.assertTrue(candidates.size() == 1 && candidates.getFirst() == router,
                            "candidatos: " + candidates);
                    LinkerOpen.click(player, player.getMainHandItem(), furnace);
                    helper.assertTrue(player.containerMenu instanceof RemoteRouterMenu, "não abriu a tela do roteador");
                    RemoteRouterMenu menu = (RemoteRouterMenu) player.containerMenu;
                    helper.assertValueEqual(menu.targetRef().target().kind(), EditTarget.Kind.MACHINE,
                            "alvo da tela");
                })
                .thenSucceed();
    }

    /** O roteador preso na máquina abre na presa. */
    @GameTest(template = "empty")
    public static void attachedRouterOpensOnTheAttachedMachine(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = openPlayer(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    LinkerOpen.click(player, player.getMainHandItem(), helper.absolutePos(CHEST));
                    helper.assertTrue(player.containerMenu instanceof RemoteRouterMenu, "não abriu a tela do roteador");
                    helper.assertValueEqual(((RemoteRouterMenu) player.containerMenu).targetRef().target().kind(),
                            EditTarget.Kind.ATTACHED, "alvo da tela");
                })
                .thenSucceed();
    }

    /** Preso e de alcance chegam na mesma máquina: os dois são candidatos, o preso primeiro. */
    @GameTest(template = "empty")
    public static void attachedAndRemoteAreBothCandidates(GameTestHelper helper) {
        RouterBlockEntity range = routerWithMachines(helper);
        RouterBlockEntity attached = attachedToFurnaceA(helper);
        ServerPlayer player = openPlayer(helper);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, range);
                    waitLoaded(helper, attached);
                })
                .thenExecute(() -> {
                    range.setRangeUpgrade(rangeUpgrade());
                    link(helper, range, FURNACE_A);
                    List<RouterBlockEntity> candidates = LinkerOpen.candidates(player, helper.absolutePos(FURNACE_A),
                            InteractionHand.MAIN_HAND);
                    helper.assertTrue(candidates.size() == 2, "candidatos: " + candidates.size());
                    helper.assertTrue(candidates.get(0) == attached && candidates.get(1) == range,
                            "ordem: o preso primeiro");
                    // a escolha na lista abre o de alcance, já na máquina
                    helper.assertTrue(LinkerOpen.pick(player, helper.absolutePos(FURNACE_A), range.getBlockPos()),
                            "a escolha do roteador de alcance recusou");
                    helper.assertValueEqual(((RemoteRouterMenu) player.containerMenu).targetRef().target().kind(),
                            EditTarget.Kind.MACHINE, "alvo da tela");
                })
                .thenSucceed();
    }

    /** Sem candidato nada abre, e a escolha de um roteador que não chega na máquina, ou com outro item na mão, é recusada. */
    @GameTest(template = "empty")
    public static void pickIsRevalidated(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = openPlayer(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    BlockPos furnace = helper.absolutePos(FURNACE_A);
                    LinkerOpen.click(player, player.getMainHandItem(), furnace);
                    helper.assertFalse(player.containerMenu instanceof RemoteRouterMenu,
                            "abriu uma máquina que nenhum roteador alcança");
                    helper.assertFalse(LinkerOpen.pick(player, furnace, router.getBlockPos()),
                            "a escolha de um roteador que não chega na máquina foi aceita");

                    link(helper, router, FURNACE_A);
                    helper.assertFalse(LinkerOpen.pick(player, furnace, furnace.above(5)),
                            "a escolha de uma posição sem roteador foi aceita");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
                    helper.assertFalse(LinkerOpen.pick(player, furnace, router.getBlockPos()),
                            "a escolha valeu com o Vinculador em modo Vincular");
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    helper.assertFalse(LinkerOpen.pick(player, furnace, router.getBlockPos()),
                            "a escolha valeu sem o Vinculador na mão");
                    player.setItemInHand(InteractionHand.MAIN_HAND, openLinker());
                    helper.assertTrue(LinkerOpen.pick(player, furnace, router.getBlockPos()),
                            "a escolha válida recusou");
                })
                .thenSucceed();
    }

    /** Um roteador que um listener do {@code RightClickBlock} protege (como um claim) não é candidato. */
    @GameTest(template = "empty")
    public static void claimedRouterIsNotACandidate(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = openPlayer(helper);
        BlockPos routerPos = helper.absolutePos(ROUTER);
        Consumer<PlayerInteractEvent.RightClickBlock> claim = event -> {
            if (event.getEntity() == player && event.getPos().equals(routerPos)) {
                event.setCanceled(true);
            }
        };
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        link(helper, router, FURNACE_A);
                        BlockPos furnace = helper.absolutePos(FURNACE_A);
                        helper.assertTrue(LinkerOpen.candidates(player, furnace, InteractionHand.MAIN_HAND).size() == 1,
                                "sem a proteção devia ser candidato");
                        NeoForge.EVENT_BUS.addListener(claim);
                        helper.assertTrue(LinkerOpen.candidates(player, furnace, InteractionHand.MAIN_HAND).isEmpty(),
                                "o roteador protegido é candidato");
                        helper.assertFalse(LinkerOpen.pick(player, furnace, routerPos),
                                "a escolha do roteador protegido foi aceita");
                    } finally {
                        NeoForge.EVENT_BUS.unregister(claim);
                    }
                })
                .thenSucceed();
    }

    /** O roteador fora do alcance do tier (a config reduzida) não é candidato. */
    @GameTest(template = "empty", batch = "wirelessautomate_config_linker_open")
    public static void routerOutOfReachIsNotACandidate(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = openPlayer(helper);
        int before = Config.TIERS.get(RouterTier.ADVANCED).range().get();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        link(helper, router, FURNACE_A);
                        BlockPos furnace = helper.absolutePos(FURNACE_A);
                        Config.TIERS.get(RouterTier.ADVANCED).range().set(5);
                        helper.assertTrue(LinkerOpen.candidates(player, furnace, InteractionHand.MAIN_HAND).size() == 1,
                                "perto do roteador devia ser candidato");
                        player.moveTo(Vec3.atCenterOf(furnace.offset(0, 40, 0)));
                        helper.assertTrue(LinkerOpen.candidates(player, furnace, InteractionHand.MAIN_HAND).isEmpty(),
                                "o roteador fora do alcance é candidato");
                    } finally {
                        Config.TIERS.get(RouterTier.ADVANCED).range().set(before);
                    }
                })
                .thenSucceed();
    }
}
