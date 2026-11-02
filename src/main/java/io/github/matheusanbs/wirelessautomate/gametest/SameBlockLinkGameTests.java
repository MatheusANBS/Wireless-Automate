package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import io.github.matheusanbs.wirelessautomate.range.SameBlockLinks;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * O Ctrl + clique do Vinculador ({@link SameBlockLinks}, pelo handler do {@code LinkSameBlockPayload}): vincula as
 * máquinas do mesmo bloco no raio, das mais perto para as mais longe, até o limite; deixa de fora as já vinculadas, a
 * presa e as de fora do raio; recusa sem roteador escolhido e no modo aventura.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class SameBlockLinkGameTests {
    private static final String KEY = "item.wirelessautomate.linker.range.same.";
    /** No chão do template: a 2 blocos (Chebyshev) do roteador em (1, 2, 1); as fornalhas A e B ficam a 1. */
    private static final List<BlockPos> FAR_FURNACES = List.of(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0),
            new BlockPos(2, 0, 2));

    private SameBlockLinkGameTests() {
    }

    /** Um jogador em sobrevivência ao lado do roteador, com o Vinculador já com o roteador escolhido. */
    private static TestPlayers.MenuPlayer player(GameTestHelper helper, RouterBlockEntity router, boolean choose) {
        TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        ItemStack linker = machinesLinker();
        if (choose) {
            LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        return player;
    }

    private static RangeLinks.@Nullable Result ctrlClick(GameTestHelper helper, TestPlayers.MenuPlayer player,
            BlockPos relative) {
        return SameBlockLinks.link(player, helper.absolutePos(relative));
    }

    private static boolean linked(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return router.remoteMachines().contains(helper.absolutePos(relative));
    }

    private static TranslatableContents message(GameTestHelper helper, @Nullable Component message, String key) {
        helper.assertTrue(message != null && message.getContents() instanceof TranslatableContents,
                "sem mensagem traduzível: " + message);
        TranslatableContents contents = (TranslatableContents) message.getContents();
        helper.assertValueEqual(contents.getKey(), key, "a mensagem");
        return contents;
    }

    private static boolean hasPart(@Nullable Component message, String key) {
        return message != null && message.getSiblings().stream().anyMatch(part -> part.getContents()
                instanceof TranslatableContents c && c.getKey().equals(key));
    }

    /** As duas fornalhas entram com um Ctrl + clique numa delas; o barril, outro bloco, não. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sameBlockLinksOnlyThatBlock(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    TestPlayers.MenuPlayer player = player(helper, router, true);
                    RangeLinks.Result result = ctrlClick(helper, player, FURNACE_A);
                    helper.assertTrue(result != null && result.status() == RangeLinks.Status.SAME_BLOCK_DONE
                            && result.linked() == 2, "resultado " + result);
                    helper.assertTrue(linked(helper, router, FURNACE_A) && linked(helper, router, FURNACE_B),
                            "as fornalhas não entraram");
                    helper.assertFalse(linked(helper, router, BARREL), "o barril entrou");
                    TranslatableContents text = message(helper, player.actionBar, KEY + "linked");
                    helper.assertValueEqual(text.getArgs()[0], 2, "a contagem");
                    // sem o Vinculador na mão, o pacote é ignorado
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    helper.assertTrue(ctrlClick(helper, player, BARREL) == null, "aceitou sem o Vinculador");
                    helper.assertFalse(linked(helper, router, BARREL), "o barril entrou sem o Vinculador");
                })
                .thenSucceed();
    }

    /**
     * A já vinculada e a presa ficam de fora (o baú preso é o modelo, mas não entra); com todas vinculadas, o aviso diz
     * que não há nada novo.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sameBlockSkipsLinkedAndAttached(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        BlockPos chestA = new BlockPos(2, 1, 2);
        BlockPos chestB = new BlockPos(0, 1, 1);
        helper.setBlock(chestA, Blocks.CHEST);
        helper.setBlock(chestB, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, chestA);
                    TestPlayers.MenuPlayer player = player(helper, router, true);
                    RangeLinks.Result result = ctrlClick(helper, player, CHEST);
                    helper.assertTrue(result != null && result.linked() == 1 && result.already() == 1,
                            "resultado " + result);
                    helper.assertTrue(linked(helper, router, chestB), "o baú solto não entrou");
                    helper.assertFalse(linked(helper, router, CHEST), "a presa entrou como remota");
                    result = ctrlClick(helper, player, chestB);
                    helper.assertTrue(result != null && result.linked() == 0, "o segundo vinculou: " + result);
                    message(helper, player.actionBar, KEY + "none");
                })
                .thenSucceed();
    }

    /**
     * Com o limite em 3 e cinco fornalhas, entram as duas a 1 bloco e a primeira (pela posição) das três a 2; o aviso
     * diz "3 de 5" e o limite do tier.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config_sameblock_limit")
    public static void sameBlockNearestFirstUpToTheLimit(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        for (BlockPos pos : FAR_FURNACES) {
            helper.setBlock(pos, Blocks.FURNACE);
        }
        int before = Config.TIERS.get(RouterTier.ADVANCED).maxMachines().get();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    Config.TIERS.get(RouterTier.ADVANCED).maxMachines().set(3);
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        TestPlayers.MenuPlayer player = player(helper, router, true);
                        RangeLinks.Result result = ctrlClick(helper, player, FURNACE_B);
                        helper.assertTrue(result != null && result.linked() == 3 && result.overLimit() == 2,
                                "resultado " + result);
                        helper.assertTrue(linked(helper, router, FURNACE_A) && linked(helper, router, FURNACE_B),
                                "as mais perto ficaram de fora");
                        BlockPos first = FAR_FURNACES.stream()
                                .min(Comparator.comparingLong(pos -> helper.absolutePos(pos).asLong())).orElseThrow();
                        for (BlockPos pos : FAR_FURNACES) {
                            helper.assertValueEqual(linked(helper, router, pos), pos.equals(first),
                                    "a fornalha " + pos + " (empate pela posição)");
                        }
                        TranslatableContents text = message(helper, player.actionBar, KEY + "linked_of");
                        helper.assertValueEqual(text.getArgs()[0], 3, "as vinculadas");
                        helper.assertValueEqual(text.getArgs()[1], 5, "as achadas");
                        helper.assertTrue(hasPart(player.actionBar, KEY + "limit"),
                                "sem o limite: " + player.actionBar.getString());
                    } finally {
                        Config.TIERS.get(RouterTier.ADVANCED).maxMachines().set(before);
                    }
                })
                .thenSucceed();
    }

    /** Com o raio em 1, as fornalhas a 2 blocos ficam de fora e as a 1 entram. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config_sameblock_radius")
    public static void sameBlockOutOfRadiusStaysOut(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        for (BlockPos pos : FAR_FURNACES) {
            helper.setBlock(pos, Blocks.FURNACE);
        }
        int before = Config.TIERS.get(RouterTier.ADVANCED).machineRadius().get();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    Config.TIERS.get(RouterTier.ADVANCED).machineRadius().set(1);
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        TestPlayers.MenuPlayer player = player(helper, router, true);
                        RangeLinks.Result result = ctrlClick(helper, player, FURNACE_A);
                        helper.assertTrue(result != null && result.linked() == 2, "resultado " + result);
                        for (BlockPos pos : FAR_FURNACES) {
                            helper.assertFalse(linked(helper, router, pos), "a fornalha " + pos + " fora do raio entrou");
                        }
                        // clicar numa de fora do raio: a recusa de sempre
                        result = ctrlClick(helper, player, FAR_FURNACES.get(0));
                        helper.assertTrue(result != null && result.refusal() == LinkRefusal.OUT_OF_RANGE,
                                "fora do raio: " + result);
                    } finally {
                        Config.TIERS.get(RouterTier.ADVANCED).machineRadius().set(before);
                    }
                })
                .thenSucceed();
    }

    /** Sem roteador escolhido recusa; no modo aventura ({@code mayBuild}) também, e nada entra. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sameBlockRefusals(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    TestPlayers.MenuPlayer player = player(helper, router, false);
                    RangeLinks.Result result = ctrlClick(helper, player, FURNACE_A);
                    helper.assertTrue(result != null && result.status() == RangeLinks.Status.NO_ROUTER,
                            "sem roteador: " + result);
                    message(helper, player.actionBar, "item.wirelessautomate.linker.range.no_router");

                    LinkerItem.setRouter(player.getMainHandItem(),
                            GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                    player.setGameMode(GameType.ADVENTURE);
                    result = ctrlClick(helper, player, FURNACE_A);
                    helper.assertTrue(result != null && result.refusal() == LinkRefusal.PROTECTED,
                            "no modo aventura: " + result);
                    helper.assertFalse(linked(helper, router, FURNACE_A) || linked(helper, router, FURNACE_B),
                            "vinculou recusado");
                })
                .thenSucceed();
    }
}
