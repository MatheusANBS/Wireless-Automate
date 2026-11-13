package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockChoice;
import io.github.matheusanbs.wirelessautomate.dock.DockLinks;
import io.github.matheusanbs.wirelessautomate.dock.DockSameBlock;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RangeTiers;
import io.github.matheusanbs.wirelessautomate.range.SameBlockLinks;
import io.github.matheusanbs.wirelessautomate.range.SameBlockScan;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * O Ctrl + clique pelas que se tocam ({@link SameBlockScan}, pelo {@link SameBlockLinks} e pelo
 * {@link DockSameBlock}): pela face, pela aresta e pela ponta; o grupo passa pelas que não entram; as de outro
 * roteador não gastam a busca; o raio corta o grupo; com o limite, entram as mais perto da clicada; o desvincular tira
 * só o grupo, mesmo fora do raio; na Doca, o mesmo; e o custo, as leituras proporcionais ao grupo. O roteador é o de
 * {@link RangeTestSupport#chestWithRouter} (em (1, 2, 1), preso no baú em (1, 1, 1)).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TouchingLinkGameTests {
    private static final String KEY = "item.wirelessautomate.linker.range.same.";

    private TouchingLinkGameTests() {
    }

    private static void furnaces(GameTestHelper helper, BlockPos... positions) {
        for (BlockPos pos : positions) {
            helper.setBlock(pos, Blocks.FURNACE);
        }
    }

    /** Roteador Avançado em cima de {@code machine} (o bloco já posto), virado para ela. */
    private static RouterBlockEntity routerOn(GameTestHelper helper, BlockPos machine) {
        helper.setBlock(machine.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        return helper.getBlockEntity(machine.above());
    }

    /** Um jogador em sobrevivência ao lado do roteador, com o Vinculador já com {@code router} escolhido. */
    private static TestPlayers.MenuPlayer player(GameTestHelper helper, RouterBlockEntity router) {
        TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 3, 2.5)));
        ItemStack linker = machinesLinker();
        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        return player;
    }

    /** O Ctrl + clique, sem o descanso do anterior (os testes clicam várias vezes no mesmo tick). */
    private static RangeLinks.@Nullable Result ctrlClick(GameTestHelper helper, TestPlayers.MenuPlayer player,
            BlockPos relative) {
        player.getCooldowns().removeCooldown(ModItems.LINKER.get());
        return SameBlockLinks.link(player, helper.absolutePos(relative));
    }

    private static boolean linked(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return router.remoteMachines().contains(helper.absolutePos(relative));
    }

    private static void assertLinked(GameTestHelper helper, RouterBlockEntity router, boolean expected,
            BlockPos... positions) {
        for (BlockPos pos : positions) {
            helper.assertValueEqual(linked(helper, router, pos), expected, "a fornalha " + pos + " vinculada");
        }
    }

    private static boolean hasPart(@Nullable Component message, String key) {
        return message != null && message.getSiblings().stream().anyMatch(part -> part.getContents()
                instanceof TranslatableContents c && c.getKey().equals(key));
    }

    /**
     * A clicada, uma encostada pela face, uma pela aresta (diagonal no plano) e uma pela ponta (diagonal em 3D)
     * entram; uma separada das outras por um bloco de ar fica.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void touchingByFaceEdgeAndCorner(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        BlockPos clicked = new BlockPos(3, 1, 3);
        BlockPos face = new BlockPos(3, 1, 4);
        BlockPos edge = new BlockPos(4, 1, 2);
        BlockPos corner = new BlockPos(2, 2, 2);
        BlockPos apart = new BlockPos(5, 1, 4);
        furnaces(helper, clicked, face, edge, corner, apart);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    RangeLinks.Result result = ctrlClick(helper, player(helper, router), clicked);
                    helper.assertTrue(result != null && result.status() == RangeLinks.Status.SAME_BLOCK_DONE
                            && result.linked() == 4, "resultado " + result);
                    assertLinked(helper, router, true, clicked, face, edge, corner);
                    assertLinked(helper, router, false, apart);
                })
                .thenSucceed();
    }

    /**
     * Numa fila, depois da presa, de uma já vinculada e de uma de outro roteador, as seguintes entram: o grupo passa
     * pelas que não entram.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void touchingPassesThroughTheOnesThatDoNotJoin(GameTestHelper helper) {
        BlockPos clicked = new BlockPos(0, 1, 1);
        BlockPos attached = new BlockPos(1, 1, 1);
        BlockPos already = new BlockPos(2, 1, 1);
        BlockPos ofOther = new BlockPos(3, 1, 1);
        BlockPos free1 = new BlockPos(4, 1, 1);
        BlockPos free2 = new BlockPos(5, 1, 1);
        furnaces(helper, clicked, attached, already, ofOther, free1, free2);
        // a presa do escolhido é a fornalha da fila
        RouterBlockEntity router = routerOn(helper, attached);
        BlockPos otherChest = new BlockPos(4, 1, 4);
        helper.setBlock(otherChest, Blocks.CHEST);
        RouterBlockEntity other = routerOn(helper, otherChest);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, router);
                    waitLoaded(helper, other);
                })
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    other.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, already);
                    link(helper, other, ofOther);
                    RangeLinks.Result result = ctrlClick(helper, player(helper, router), clicked);
                    helper.assertTrue(result != null && result.linked() == 3 && result.already() == 1
                            && result.otherRouter() == 1, "resultado " + result);
                    assertLinked(helper, router, true, clicked, already, free1, free2);
                    assertLinked(helper, router, false, attached, ofOther);
                    helper.assertTrue(linked(helper, other, ofOther), "a do outro roteador saiu dele");
                })
                .thenSucceed();
    }

    /**
     * Com vaga para uma e as duas mais perto da clicada (ela e a vizinha) de outro roteador, a livre logo depois delas
     * entra: as de outro dono não contam para a busca, e nenhuma fica "além do limite".
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config_touching_taken")
    public static void touchingOtherOwnerDoesNotUseUpTheSearch(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        BlockPos otherChest = new BlockPos(5, 1, 4);
        helper.setBlock(otherChest, Blocks.CHEST);
        RouterBlockEntity other = routerOn(helper, otherChest);
        BlockPos taken1 = new BlockPos(2, 1, 3);
        BlockPos taken2 = new BlockPos(3, 1, 3);
        BlockPos free = new BlockPos(4, 1, 3);
        furnaces(helper, taken1, taken2, free);
        var max = Config.TIERS.get(RouterTier.ADVANCED).maxMachines();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, router);
                    waitLoaded(helper, other);
                })
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    other.setRangeUpgrade(rangeUpgrade());
                    link(helper, other, taken1);
                    link(helper, other, taken2);
                    int before = max.get();
                    try {
                        max.set(1);
                        TestPlayers.MenuPlayer player = player(helper, router);
                        RangeLinks.Result result = ctrlClick(helper, player, taken1);
                        helper.assertTrue(result != null && result.linked() == 1 && result.otherRouter() == 2
                                && result.overLimit() == 0, "resultado " + result);
                        assertLinked(helper, router, true, free);
                        assertLinked(helper, router, false, taken1, taken2);
                        helper.assertFalse(hasPart(player.actionBar, KEY + "limit"),
                                "acusou o limite: " + player.actionBar);
                        helper.assertTrue(hasPart(player.actionBar, KEY + "other_router"),
                                "sem o aviso de outro roteador: " + player.actionBar);
                    } finally {
                        max.set(before);
                    }
                })
                .thenSucceed();
    }

    /**
     * Com o raio em 2, numa fila que sai do raio as de fora não entram e o grupo não continua por elas: uma que volta
     * para dentro do raio só por elas também fica.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config_touching_radius")
    public static void touchingRadiusCutsTheGroup(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        BlockPos near = new BlockPos(2, 1, 0);
        BlockPos clicked = new BlockPos(3, 1, 0);
        // a 3 do roteador em (1, 2, 1): fora do raio 2
        BlockPos out1 = new BlockPos(4, 1, 0);
        BlockPos out2 = new BlockPos(4, 1, 1);
        // a 2 do roteador, encostada só em out2
        BlockPos back = new BlockPos(3, 1, 2);
        furnaces(helper, near, clicked, out1, out2, back);
        var radius = Config.TIERS.get(RouterTier.ADVANCED).machineRadius();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    int before = radius.get();
                    try {
                        radius.set(2);
                        router.setRangeUpgrade(rangeUpgrade());
                        RangeLinks.Result result = ctrlClick(helper, player(helper, router), clicked);
                        helper.assertTrue(result != null && result.linked() == 2, "resultado " + result);
                        assertLinked(helper, router, true, clicked, near);
                        assertLinked(helper, router, false, out1, out2, back);
                    } finally {
                        radius.set(before);
                    }
                })
                .thenSucceed();
    }

    /**
     * Com vagas para duas e uma fila que vai do roteador para longe, o Ctrl + clique na ponta mais longe vincula a
     * clicada e a vizinha dela, não as mais perto do roteador.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config_touching_order")
    public static void touchingLimitTakesTheNearestToTheClicked(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        // a 2, 2, 2, 2, 3 e 4 blocos do roteador em (1, 2, 1)
        BlockPos[] row = new BlockPos[6];
        for (int x = 0; x < row.length; x++) {
            row[x] = new BlockPos(x, 1, 3);
        }
        furnaces(helper, row);
        var max = Config.TIERS.get(RouterTier.ADVANCED).maxMachines();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    int before = max.get();
                    try {
                        max.set(2);
                        router.setRangeUpgrade(rangeUpgrade());
                        RangeLinks.Result result = ctrlClick(helper, player(helper, router), row[5]);
                        helper.assertTrue(result != null && result.linked() == 2 && result.overLimit() == 1,
                                "resultado " + result);
                        assertLinked(helper, router, true, row[5], row[4]);
                        assertLinked(helper, router, false, row[0], row[1], row[2], row[3]);
                    } finally {
                        max.set(before);
                    }
                })
                .thenSucceed();
    }

    /**
     * Dois grupos de fornalhas vinculados ao mesmo roteador: o Ctrl + clique num tira só o dele, e a vinculada
     * encostada nele fora do raio (2, pela config) sai junto.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config_touching_unlink")
    public static void touchingUnlinkTakesOnlyTheGroup(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        BlockPos first1 = new BlockPos(2, 1, 0);
        BlockPos first2 = new BlockPos(3, 1, 0);
        // a 3 do roteador: fora do raio 2
        BlockPos firstOut = new BlockPos(4, 1, 0);
        BlockPos second1 = new BlockPos(2, 1, 3);
        BlockPos second2 = new BlockPos(3, 1, 3);
        furnaces(helper, first1, first2, firstOut, second1, second2);
        var radius = Config.TIERS.get(RouterTier.ADVANCED).machineRadius();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    for (BlockPos pos : List.of(first1, first2, firstOut, second1, second2)) {
                        link(helper, router, pos);
                    }
                    int before = radius.get();
                    try {
                        radius.set(2);
                        RangeLinks.Result result = ctrlClick(helper, player(helper, router), first1);
                        helper.assertTrue(result != null && result.status() == RangeLinks.Status.SAME_BLOCK_UNLINKED
                                && result.linked() == 3, "resultado " + result);
                        assertLinked(helper, router, false, first1, first2, firstOut);
                        assertLinked(helper, router, true, second1, second2);
                    } finally {
                        radius.set(before);
                    }
                })
                .thenSucceed();
    }

    /** Na Doca: o Ctrl + clique num lado vincula só o grupo da clicada, e o desvincular tira só o grupo. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void touchingDockLinksAndUnlinksOnlyTheGroup(GameTestHelper helper) {
        BlockPos dockPos = new BlockPos(1, 1, 1);
        helper.setBlock(dockPos, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        PatternDockBlockEntity dock = helper.getBlockEntity(dockPos);
        List<BlockPos> first = List.of(new BlockPos(3, 1, 0), new BlockPos(4, 1, 0), new BlockPos(5, 1, 0));
        List<BlockPos> second = List.of(new BlockPos(3, 1, 3), new BlockPos(4, 1, 3));
        furnaces(helper, first.toArray(BlockPos[]::new));
        furnaces(helper, second.toArray(BlockPos[]::new));
        int side = Direction.NORTH.ordinal();
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
                    player.moveTo(helper.absoluteVec(new Vec3(2.5, 3, 1.5)));
                    ItemStack linker = machinesLinker();
                    LinkerItem.setDock(linker, new DockChoice(GlobalPos.of(helper.getLevel().dimension(),
                            helper.absolutePos(dockPos)), side));
                    player.setItemInHand(InteractionHand.MAIN_HAND, linker);
                    DockLinks.Result result = dockCtrlClick(helper, player, first.get(0));
                    helper.assertTrue(result != null && result.status() == DockLinks.Status.SAME_BLOCK_DONE
                            && result.linked() == 3, "vincular o primeiro grupo: " + result);
                    assertInDock(helper, dock, true, first);
                    assertInDock(helper, dock, false, second);
                    result = dockCtrlClick(helper, player, second.get(0));
                    helper.assertTrue(result != null && result.linked() == 2 && result.already() == 0,
                            "vincular o segundo grupo: " + result);
                    result = dockCtrlClick(helper, player, first.get(1));
                    helper.assertTrue(result != null && result.status() == DockLinks.Status.SAME_BLOCK_UNLINKED
                            && result.linked() == 3, "desvincular o primeiro grupo: " + result);
                    assertInDock(helper, dock, false, first);
                    assertInDock(helper, dock, true, second);
                })
                .thenSucceed();
    }

    private static DockLinks.@Nullable Result dockCtrlClick(GameTestHelper helper, TestPlayers.MenuPlayer player,
            BlockPos relative) {
        player.getCooldowns().removeCooldown(ModItems.LINKER.get());
        return DockSameBlock.link(player, helper.absolutePos(relative));
    }

    private static void assertInDock(GameTestHelper helper, PatternDockBlockEntity dock, boolean expected,
            List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            helper.assertValueEqual(dock.machineAt(helper.absolutePos(pos)) != null, expected,
                    "a fornalha " + pos + " na Doca");
        }
    }

    /**
     * O custo: num grupo de 5 fornalhas, com o raio maior e outras fornalhas soltas no raio, a varredura lê no máximo
     * os 26 vizinhos de cada uma do grupo, as candidatas são o grupo na ordem da busca e as soltas ficam de fora; com
     * vagas para 2, para logo depois da segunda.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void touchingScanReadsOnlyTheGroup(GameTestHelper helper) {
        int size = 5;
        BlockPos[] group = new BlockPos[size];
        for (int x = 0; x < size; x++) {
            group[x] = new BlockPos(x, 1, 0);
        }
        furnaces(helper, group);
        BlockPos[] loose = {new BlockPos(0, 1, 3), new BlockPos(2, 1, 3), new BlockPos(4, 1, 3)};
        furnaces(helper, loose);
        ServerPlayer player = TestPlayers.inLevel(helper);
        BlockPos start = helper.absolutePos(group[0]);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));

        SameBlockScan.Scan scan = SameBlockScan.scan(player, start, origin, RangeTiers.MAX_RADIUS, Blocks.FURNACE,
                LongSets.EMPTY_SET, LongSets.EMPTY_SET, key -> false, Integer.MAX_VALUE);
        helper.assertTrue(scan.complete() && !scan.capped(), "a varredura não leu o grupo inteiro: " + scan);
        helper.assertTrue(scan.reads() <= 26 * size, "leituras " + scan.reads() + " para " + size + " no grupo");
        helper.assertValueEqual(scan.candidates().size(), size, "as candidatas");
        for (int i = 0; i < size; i++) {
            helper.assertValueEqual(scan.candidates().getLong(i), helper.absolutePos(group[i]).asLong(),
                    "a candidata " + i + " na ordem da busca");
        }
        for (BlockPos pos : loose) {
            helper.assertFalse(scan.candidates().contains(helper.absolutePos(pos).asLong()),
                    "a solta " + pos + " virou candidata");
        }

        SameBlockScan.Scan early = SameBlockScan.scan(player, start, origin, RangeTiers.MAX_RADIUS, Blocks.FURNACE,
                LongSets.EMPTY_SET, LongSets.EMPTY_SET, key -> false, 2);
        helper.assertTrue(early.candidates().size() == 2 && !early.complete() && early.reads() <= 26,
                "a varredura não parou nas vagas: " + early);
        helper.succeed();
    }
}
