package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import io.github.matheusanbs.wirelessautomate.range.SameBlockLinks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.function.Consumer;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * O Ctrl + clique do Vinculador ({@link SameBlockLinks}, pelo handler do {@code LinkSameBlockPayload}): vincula as
 * máquinas do mesmo bloco que se tocam com a clicada, da mais perto da clicada para a mais longe, até o limite; deixa
 * de fora as já vinculadas, a presa e as de fora do raio; desvincula só o grupo da clicada; recusa sem roteador
 * escolhido, no modo aventura e no descanso do item; o limite do tier só aparece com alguma de fora. Os casos da busca
 * pelas que se tocam (quinas, o raio, o outro dono, o custo) ficam em {@link TouchingLinkGameTests}.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class SameBlockLinkGameTests {
    private static final String KEY = "item.wirelessautomate.linker.range.same.";
    /**
     * No chão do template: a 2 blocos (Chebyshev) do roteador em (1, 2, 1); as fornalhas A e B ficam a 1. As duas
     * primeiras ficam embaixo de A e de B (encostadas pela face); a terceira não toca nenhuma.
     */
    private static final List<BlockPos> FAR_FURNACES = List.of(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0),
            new BlockPos(2, 0, 2));
    /** Entre as fornalhas A e B: com ela, A, ponte e B são uma fila que se toca pelas faces. */
    private static final BlockPos BRIDGE = new BlockPos(1, 1, 0);

    private SameBlockLinkGameTests() {
    }

    /** O roteador de {@link RangeTestSupport#routerWithMachines} com a fornalha que liga A e B (a fila A, ponte, B). */
    private static RouterBlockEntity routerWithRow(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.setBlock(BRIDGE, Blocks.FURNACE);
        return router;
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

    /** O Ctrl + clique, sem o descanso do anterior (os testes clicam várias vezes no mesmo tick). */
    private static RangeLinks.@Nullable Result ctrlClick(GameTestHelper helper, TestPlayers.MenuPlayer player,
            BlockPos relative) {
        player.getCooldowns().removeCooldown(ModItems.LINKER.get());
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

    /**
     * Dois grupos de fornalhas vinculados (A com a de baixo dela, B com a de baixo dela, sem se tocar): o Ctrl + clique
     * em B desvincula só o grupo de B, e o de A e o barril ficam; com o grupo de B solto, o mesmo clique o vincula de
     * novo, sem contar as de A como já vinculadas.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sameBlockUnlinksOnlyTheTouchingGroup(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        BlockPos underA = FAR_FURNACES.get(0);
        BlockPos underB = FAR_FURNACES.get(1);
        helper.setBlock(underA, Blocks.FURNACE);
        helper.setBlock(underB, Blocks.FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    for (BlockPos pos : List.of(FURNACE_A, underA, FURNACE_B, underB, BARREL)) {
                        link(helper, router, pos);
                    }
                    TestPlayers.MenuPlayer player = player(helper, router, true);
                    // a clicada está vinculada: o Ctrl + clique desvincula as que se tocam com ela
                    RangeLinks.Result result = ctrlClick(helper, player, FURNACE_B);
                    helper.assertTrue(result != null && result.status() == RangeLinks.Status.SAME_BLOCK_UNLINKED
                            && result.linked() == 2, "resultado " + result);
                    helper.assertFalse(linked(helper, router, FURNACE_B) || linked(helper, router, underB),
                            "uma fornalha do grupo de B ficou");
                    helper.assertTrue(linked(helper, router, FURNACE_A) && linked(helper, router, underA),
                            "o grupo de A saiu junto");
                    helper.assertTrue(linked(helper, router, BARREL), "o barril saiu junto");
                    TranslatableContents text = message(helper, player.actionBar, KEY + "unlinked");
                    helper.assertValueEqual(text.getArgs()[0], 2, "a contagem");
                    // com o grupo de B solto, o mesmo clique vincula as duas de novo; as de A não se tocam com elas
                    result = ctrlClick(helper, player, FURNACE_B);
                    helper.assertTrue(result != null && result.status() == RangeLinks.Status.SAME_BLOCK_DONE
                            && result.linked() == 2 && result.already() == 0, "segundo clique " + result);
                    helper.assertTrue(linked(helper, router, FURNACE_B) && linked(helper, router, underB),
                            "o grupo de B não voltou");
                    helper.assertTrue(linked(helper, router, BARREL), "o barril saiu no segundo clique");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sameBlockLinksOnlyThatBlock(GameTestHelper helper) {
        RouterBlockEntity router = routerWithRow(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    TestPlayers.MenuPlayer player = player(helper, router, true);
                    RangeLinks.Result result = ctrlClick(helper, player, FURNACE_A);
                    helper.assertTrue(result != null && result.status() == RangeLinks.Status.SAME_BLOCK_DONE
                            && result.linked() == 3, "resultado " + result);
                    helper.assertTrue(linked(helper, router, FURNACE_A) && linked(helper, router, BRIDGE)
                            && linked(helper, router, FURNACE_B), "as fornalhas não entraram");
                    helper.assertFalse(linked(helper, router, BARREL), "o barril entrou");
                    TranslatableContents text = message(helper, player.actionBar, KEY + "linked");
                    helper.assertValueEqual(text.getArgs()[0], 3, "a contagem");
                    // logo em seguida, o Vinculador está no descanso: o pacote é ignorado
                    helper.assertTrue(player.getCooldowns().isOnCooldown(ModItems.LINKER.get()), "sem descanso");
                    helper.assertTrue(SameBlockLinks.link(player, helper.absolutePos(FURNACE_A)) == null,
                            "aceitou no descanso");
                    // sem o Vinculador na mão, o pacote é ignorado
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    helper.assertTrue(ctrlClick(helper, player, BARREL) == null, "aceitou sem o Vinculador");
                    helper.assertFalse(linked(helper, router, BARREL), "o barril entrou sem o Vinculador");
                })
                .thenSucceed();
    }

    /**
     * A já vinculada e a presa ficam de fora (o baú preso é o modelo, mas não entra; o grupo passa por ele até os dois
     * baús encostados); com todas vinculadas, o aviso diz que não há nada novo.
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
                    // a presa não está vinculada: o clique nela vincula, e não há mais nenhuma nova
                    result = ctrlClick(helper, player, CHEST);
                    helper.assertTrue(result != null && result.linked() == 0, "o segundo vinculou: " + result);
                    message(helper, player.actionBar, KEY + "none");
                })
                .thenSucceed();
    }

    /**
     * Com o limite em 3 e uma fila de seis fornalhas que vai do roteador para longe (A, ponte e B a 1 bloco, as outras a
     * 2, 3 e 4), o Ctrl + clique na ponta mais longe vincula as três mais perto da clicada, não as mais perto do
     * roteador; o aviso diz o limite do tier.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config_sameblock_limit")
    public static void sameBlockNearestFirstUpToTheLimit(GameTestHelper helper) {
        RouterBlockEntity router = routerWithRow(helper);
        List<BlockPos> far = List.of(new BlockPos(3, 1, 0), new BlockPos(4, 1, 0), new BlockPos(5, 1, 0));
        for (BlockPos pos : far) {
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
                        RangeLinks.Result result = ctrlClick(helper, player, far.get(2));
                        helper.assertTrue(result != null && result.linked() == 3 && result.overLimit() == 1,
                                "resultado " + result);
                        for (BlockPos pos : far) {
                            helper.assertTrue(linked(helper, router, pos), "a fornalha " + pos + " ficou de fora");
                        }
                        for (BlockPos pos : List.of(FURNACE_A, BRIDGE, FURNACE_B)) {
                            helper.assertFalse(linked(helper, router, pos), "a fornalha " + pos
                                    + ", mais perto do roteador e mais longe da clicada, entrou");
                        }
                        // a busca para numa além das vagas, antes de ler o grupo inteiro: sem o "de N"
                        TranslatableContents text = message(helper, player.actionBar, KEY + "linked");
                        helper.assertValueEqual(text.getArgs()[0], 3, "as vinculadas");
                        helper.assertTrue(hasPart(player.actionBar, KEY + "limit"),
                                "sem o limite: " + player.actionBar.getString());
                    } finally {
                        Config.TIERS.get(RouterTier.ADVANCED).maxMachines().set(before);
                    }
                })
                .thenSucceed();
    }

    /**
     * Com o raio em 1, as fornalhas a 2 blocos ficam de fora, mesmo as encostadas na fila (embaixo de A e de B), e as
     * a 1 entram.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config_sameblock_radius")
    public static void sameBlockOutOfRadiusStaysOut(GameTestHelper helper) {
        RouterBlockEntity router = routerWithRow(helper);
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
                        helper.assertTrue(result != null && result.linked() == 3, "resultado " + result);
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

    /**
     * Com exatamente as vagas em candidatas (A e a ponte, com o limite em 3 e B já vinculada na ponta da fila), o grupo
     * é lido inteiro e o aviso não acusa o limite do tier: não ficou nenhuma de fora.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config_sameblock_exact")
    public static void sameBlockExactRoomIsNotTheLimit(GameTestHelper helper) {
        RouterBlockEntity router = routerWithRow(helper);
        int beforeMax = Config.TIERS.get(RouterTier.ADVANCED).maxMachines().get();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    Config.TIERS.get(RouterTier.ADVANCED).maxMachines().set(3);
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        link(helper, router, FURNACE_B);
                        TestPlayers.MenuPlayer player = player(helper, router, true);
                        RangeLinks.Result result = ctrlClick(helper, player, FURNACE_A);
                        helper.assertTrue(result != null && result.linked() == 2 && result.overLimit() == 0
                                && result.already() == 1, "resultado " + result);
                        message(helper, player.actionBar, KEY + "linked");
                        helper.assertFalse(hasPart(player.actionBar, KEY + "limit"),
                                "acusou o limite: " + player.actionBar.getString());
                    } finally {
                        Config.TIERS.get(RouterTier.ADVANCED).maxMachines().set(beforeMax);
                    }
                })
                .thenSucceed();
    }

    /** Todas as achadas recusadas pelo evento de clique das proteções: a recusa de área protegida, nada entra. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sameBlockAllRefusedByTheEvent(GameTestHelper helper) {
        RouterBlockEntity router = routerWithRow(helper);
        TestPlayers.MenuPlayer player = player(helper, router, true);
        int[] posted = new int[1];
        Consumer<PlayerInteractEvent.RightClickBlock> claim = event -> {
            if (event.getEntity() == player) {
                posted[0]++;
                event.setCanceled(true);
            }
        };
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    NeoForge.EVENT_BUS.addListener(claim);
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        RangeLinks.Result result = ctrlClick(helper, player, FURNACE_A);
                        helper.assertTrue(result != null && result.refusal() == LinkRefusal.PROTECTED,
                                "resultado " + result);
                        helper.assertValueEqual(posted[0], 3, "eventos postados");
                        helper.assertFalse(linked(helper, router, FURNACE_A) || linked(helper, router, BRIDGE)
                                || linked(helper, router, FURNACE_B), "vinculou recusada pelo evento");
                        helper.assertFalse(player.actionBar != null
                                && player.actionBar.getContents() instanceof TranslatableContents c
                                && c.getKey().startsWith(KEY + "linked"), "aviso de vinculadas: " + player.actionBar);
                    } finally {
                        NeoForge.EVENT_BUS.unregister(claim);
                    }
                })
                .thenSucceed();
    }

    /**
     * Shift + clique no ar solta o roteador escolhido (pelo {@code use} do item e por {@link LinkerActions#release}) e
     * avisa; sem nada escolhido, só avisa; a Tela escolhida também sai. Sem Shift, o clique no ar mostra o roteador
     * escolhido ou a dica do gesto, e não solta nada.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void shiftClickInTheAirReleasesTheChosen(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    TestPlayers.MenuPlayer player = player(helper, router, true);
                    ItemStack stack = player.getMainHandItem();
                    // sem Shift: mostra o roteador (nome e vagas) e o mantém
                    stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                    message(helper, player.actionBar, "item.wirelessautomate.linker.range.chosen");
                    helper.assertTrue(LinkerItem.router(stack) != null, "o clique no ar sem Shift soltou");
                    // com Shift: solta
                    player.setShiftKeyDown(true);
                    try {
                        stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                        message(helper, player.actionBar, "item.wirelessautomate.linker.released.router");
                        helper.assertTrue(LinkerItem.router(stack) == null, "o roteador continuou escolhido");
                        // sem nada escolhido: só o aviso
                        helper.assertValueEqual(LinkerActions.release(player, stack), LinkerActions.Release.NOTHING,
                                "soltar sem nada");
                        message(helper, player.actionBar, "item.wirelessautomate.linker.released.nothing");
                        // a Tela de Sinal escolhida também sai
                        LinkerItem.setTv(stack, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        helper.assertValueEqual(LinkerActions.release(player, stack), LinkerActions.Release.TV,
                                "soltar a Tela");
                        helper.assertTrue(LinkerItem.tv(stack) == null, "a Tela continuou escolhida");
                    } finally {
                        player.setShiftKeyDown(false);
                    }
                    // sem Shift e sem nada escolhido: a dica
                    stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                    message(helper, player.actionBar, "item.wirelessautomate.linker.hint.choose");
                })
                .thenSucceed();
    }
}
