package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockChoice;
import io.github.matheusanbs.wirelessautomate.dock.DockLinks;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import io.github.matheusanbs.wirelessautomate.range.SameBlockLinks;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Uma máquina, um roteador, pelo caminho do Vinculador ({@link RangeLinks#toggle} e {@link SameBlockLinks}): a
 * vinculada a outro roteador (ativa ou não), a presa dele, a duplicada de antes que ainda desvincula, o roteador
 * quebrado e o Ctrl + clique com uma de outro roteador no meio de uma fila. A Doca não conta.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class OneOwnerLinkGameTests {
    private static final String REFUSAL = "item.wirelessautomate.linker.range.refusal.other_router";
    private static final String SAME = "item.wirelessautomate.linker.range.same.other_router";
    private static final BlockPos CHEST_A = new BlockPos(1, 1, 1);
    private static final BlockPos CHEST_B = new BlockPos(5, 1, 1);
    private static final BlockPos FURNACE = new BlockPos(3, 1, 4);
    /** Acima do raio do B (o {@code linkMachine} não confere o raio), para a vinculada inativa. */
    private static final BlockPos FAR_FURNACE = new BlockPos(3, 40, 4);
    private static final BlockPos DOCK = new BlockPos(6, 1, 6);

    private OneOwnerLinkGameTests() {
    }

    /** Baú em {@code chest}, roteador Avançado em cima dele e o upgrade de alcance. */
    private static RouterBlockEntity router(GameTestHelper helper, BlockPos chest) {
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(chest.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        return helper.getBlockEntity(chest.above());
    }

    /** Um jogador em sobrevivência ao lado dos roteadores, com o Vinculador já com {@code router} escolhido. */
    private static TestPlayers.MenuPlayer player(GameTestHelper helper, RouterBlockEntity router) {
        TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        ItemStack linker = machinesLinker();
        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        return player;
    }

    private static RangeLinks.@Nullable Result ctrlClick(GameTestHelper helper, TestPlayers.MenuPlayer player,
            BlockPos relative) {
        player.getCooldowns().removeCooldown(ModItems.LINKER.get());
        return SameBlockLinks.link(player, helper.absolutePos(relative));
    }

    private static boolean linked(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return router.remoteMachines().contains(helper.absolutePos(relative));
    }

    private static boolean hasPart(@Nullable Component message, String key) {
        return message != null && message.getSiblings().stream().anyMatch(part -> part.getContents()
                instanceof TranslatableContents c && c.getKey().equals(key));
    }

    /** A recusa {@code OTHER_ROUTER}: a chave, o nome da máquina, o nome e a posição do outro; a máquina não entra. */
    private static void assertRefusedFor(GameTestHelper helper, TestPlayers.MenuPlayer player, RouterBlockEntity chosen,
            RouterBlockEntity owner, BlockPos furnace, String what) {
        RangeLinks.Result result = toggle(helper, player, furnace);
        helper.assertTrue(result.status() == RangeLinks.Status.REFUSED && result.refusal() == LinkRefusal.OTHER_ROUTER,
                what + ": resultado " + result);
        helper.assertTrue(player.actionBar != null && player.actionBar.getContents() instanceof TranslatableContents,
                what + ": sem mensagem traduzível: " + player.actionBar);
        TranslatableContents text = (TranslatableContents) player.actionBar.getContents();
        helper.assertValueEqual(text.getKey(), REFUSAL, what + ": a chave");
        helper.assertTrue(text.getArgs().length == 3, what + ": argumentos " + text.getArgs().length);
        helper.assertTrue(text.getArgs()[1] instanceof Component name
                && name.getString().equals(RangeLinks.routerName(owner).getString()), what + ": o nome do outro");
        helper.assertTrue(text.getArgs()[2] instanceof Component position && position.getString()
                .equals(LinkerActions.position(owner.getBlockPos()).getString()), what + ": a posição do outro");
        helper.assertFalse(linked(helper, chosen, furnace), what + ": entrou no escolhido");
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void linkedToAnotherRouterIsRefused(GameTestHelper helper) {
        RouterBlockEntity a = router(helper, CHEST_A);
        RouterBlockEntity b = router(helper, CHEST_B);
        helper.setBlock(FURNACE, Blocks.FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    b.setRangeUpgrade(rangeUpgrade());
                    link(helper, b, FURNACE);
                    assertRefusedFor(helper, player(helper, a), a, b, FURNACE, "ativa em outro");
                    helper.assertTrue(linked(helper, b, FURNACE), "saiu do outro");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void inactiveInAnotherRouterIsRefused(GameTestHelper helper) {
        RouterBlockEntity a = router(helper, CHEST_A);
        RouterBlockEntity b = router(helper, CHEST_B);
        helper.setBlock(FAR_FURNACE, Blocks.FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    b.setRangeUpgrade(rangeUpgrade());
                    // fora do raio do B: vinculada e inativa
                    link(helper, b, FAR_FURNACE);
                    assertRefusedFor(helper, player(helper, a), a, b, FAR_FURNACE, "inativa em outro");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void attachedToAnotherRouterIsRefused(GameTestHelper helper) {
        RouterBlockEntity a = router(helper, CHEST_A);
        // B de frente para a fornalha, sem vínculo: a presa dele é a fornalha
        helper.setBlock(FURNACE, Blocks.FURNACE);
        helper.setBlock(FURNACE.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        RouterBlockEntity b = helper.getBlockEntity(FURNACE.above());
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    helper.assertTrue(b.machinePos().equals(helper.absolutePos(FURNACE)), "a presa de B");
                    assertRefusedFor(helper, player(helper, a), a, b, FURNACE, "presa de outro");
                })
                .thenSucceed();
    }

    /** A regra é só entre roteadores: uma fornalha de um lado da Doca entra no roteador. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dockMachineStillJoinsTheRouter(GameTestHelper helper) {
        RouterBlockEntity a = router(helper, CHEST_A);
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        PatternDockBlockEntity dock = helper.getBlockEntity(DOCK);
        helper.setBlock(FURNACE, Blocks.FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, a))
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    TestPlayers.MenuPlayer player = player(helper, a);
                    ItemStack dockLinker = machinesLinker();
                    LinkerItem.setDock(dockLinker, new DockChoice(GlobalPos.of(helper.getLevel().dimension(),
                            helper.absolutePos(DOCK)), Direction.NORTH.ordinal()));
                    DockLinks.Result onDock = DockLinks.toggle(player, dockLinker, helper.absolutePos(FURNACE));
                    helper.assertTrue(dock.machineAt(helper.absolutePos(FURNACE)) != null,
                            "a fornalha não entrou na Doca: " + onDock);
                    RangeLinks.Result result = toggle(helper, player, FURNACE);
                    helper.assertTrue(result.status() == RangeLinks.Status.LINKED, "resultado " + result);
                    helper.assertTrue(linked(helper, a, FURNACE), "não entrou no roteador");
                    helper.assertTrue(dock.machineAt(helper.absolutePos(FURNACE)) != null, "saiu da Doca");
                })
                .thenSucceed();
    }

    /** Vinculada nos dois roteadores (de antes da regra): o clique do A tira dela de A, e o B segue com ela. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void duplicatedMachineStillUnlinksFromTheChosen(GameTestHelper helper) {
        RouterBlockEntity a = router(helper, CHEST_A);
        RouterBlockEntity b = router(helper, CHEST_B);
        helper.setBlock(FURNACE, Blocks.FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    b.setRangeUpgrade(rangeUpgrade());
                    link(helper, a, FURNACE);
                    link(helper, b, FURNACE);
                    RangeLinks.Result result = toggle(helper, player(helper, a), FURNACE);
                    helper.assertTrue(result.status() == RangeLinks.Status.UNLINKED, "resultado " + result);
                    helper.assertFalse(linked(helper, a, FURNACE), "continua em A");
                    helper.assertTrue(linked(helper, b, FURNACE), "saiu de B");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void linksAfterTheOtherRouterIsBroken(GameTestHelper helper) {
        RouterBlockEntity a = router(helper, CHEST_A);
        RouterBlockEntity b = router(helper, CHEST_B);
        helper.setBlock(FURNACE, Blocks.FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    b.setRangeUpgrade(rangeUpgrade());
                    link(helper, b, FURNACE);
                    assertRefusedFor(helper, player(helper, a), a, b, FURNACE, "antes de quebrar");
                    helper.setBlock(CHEST_B.above(), Blocks.AIR);
                })
                .thenExecuteAfter(1, () -> {
                    RangeLinks.Result result = toggle(helper, player(helper, a), FURNACE);
                    helper.assertTrue(result.status() == RangeLinks.Status.LINKED, "resultado " + result);
                    helper.assertTrue(linked(helper, a, FURNACE), "não entrou");
                })
                .thenSucceed();
    }

    private static final BlockPos ROW_0 = new BlockPos(3, 1, 4);
    private static final BlockPos ROW_1 = new BlockPos(4, 1, 4);
    private static final BlockPos ROW_2 = new BlockPos(5, 1, 4);

    /** Três fornalhas encostadas em fila, a do meio de B: as das pontas entram em A e a do meio conta no aviso. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void ctrlClickLeavesOutTheOnesOfAnotherRouter(GameTestHelper helper) {
        RouterBlockEntity a = router(helper, CHEST_A);
        RouterBlockEntity b = router(helper, CHEST_B);
        for (BlockPos pos : new BlockPos[] {ROW_0, ROW_1, ROW_2}) {
            helper.setBlock(pos, Blocks.FURNACE);
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    b.setRangeUpgrade(rangeUpgrade());
                    link(helper, b, ROW_1);
                    TestPlayers.MenuPlayer player = player(helper, a);
                    RangeLinks.Result result = ctrlClick(helper, player, ROW_0);
                    helper.assertTrue(result != null && result.status() == RangeLinks.Status.SAME_BLOCK_DONE
                            && result.linked() == 2 && result.otherRouter() == 1, "resultado " + result);
                    helper.assertTrue(linked(helper, a, ROW_0) && linked(helper, a, ROW_2), "as pontas não entraram");
                    helper.assertFalse(linked(helper, a, ROW_1), "a do meio entrou em A");
                    helper.assertTrue(linked(helper, b, ROW_1), "a do meio saiu de B");
                    helper.assertTrue(hasPart(player.actionBar, SAME), "sem o aviso: " + player.actionBar);
                })
                .thenSucceed();
    }

    /** O Ctrl + clique na fornalha de B: ela fica em B e as encostadas entram em A. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void ctrlClickOnTheOneOfAnotherRouterLinksTheRest(GameTestHelper helper) {
        RouterBlockEntity a = router(helper, CHEST_A);
        RouterBlockEntity b = router(helper, CHEST_B);
        for (BlockPos pos : new BlockPos[] {ROW_0, ROW_1, ROW_2}) {
            helper.setBlock(pos, Blocks.FURNACE);
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    b.setRangeUpgrade(rangeUpgrade());
                    link(helper, b, ROW_1);
                    TestPlayers.MenuPlayer player = player(helper, a);
                    RangeLinks.Result result = ctrlClick(helper, player, ROW_1);
                    helper.assertTrue(result != null && result.status() == RangeLinks.Status.SAME_BLOCK_DONE
                            && result.linked() == 2 && result.otherRouter() == 1, "resultado " + result);
                    helper.assertTrue(linked(helper, a, ROW_0) && linked(helper, a, ROW_2), "as encostadas não entraram");
                    helper.assertFalse(linked(helper, a, ROW_1), "a clicada entrou em A");
                    helper.assertTrue(linked(helper, b, ROW_1), "a clicada saiu de B");
                })
                .thenSucceed();
    }
}
