package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockChoice;
import io.github.matheusanbs.wirelessautomate.dock.DockLinks;
import io.github.matheusanbs.wirelessautomate.dock.DockMachine;
import io.github.matheusanbs.wirelessautomate.dock.DockSameBlock;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * O Vinculador na Doca de Padrões ({@link DockLinks} e {@link DockSameBlock}): escolher o lado clicando na face,
 * vincular e desvincular uma máquina, o perfil do lado pelo Afinador, o Ctrl + clique até o limite do tier e as
 * recusas (outro bloco, outro lado, outra Doca, fora do cubo). Uma máquina de um roteador também entra num lado.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockLinkGameTests {
    private static final String KEY = "item.wirelessautomate.linker.dock.";
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final int SOUTH = Direction.SOUTH.ordinal();

    private DockLinkGameTests() {
    }

    private static PatternDockBlockEntity dock(GameTestHelper helper, BlockPos relative, RouterTier tier) {
        helper.setBlock(relative, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(relative);
    }

    /** Fornalha virada para {@code facing}. */
    private static void furnace(GameTestHelper helper, BlockPos relative, Direction facing) {
        helper.setBlock(relative, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, facing));
    }

    /** Um jogador em sobrevivência em cima do template, com o Vinculador na mão. */
    private static TestPlayers.MenuPlayer player(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
        return player;
    }

    /** O Vinculador do jogador com a Doca em {@code relative} e o lado {@code side} escolhidos. */
    private static void chooseDock(GameTestHelper helper, TestPlayers.MenuPlayer player, BlockPos relative, int side) {
        LinkerItem.setDock(player.getMainHandItem(), new DockChoice(GlobalPos.of(helper.getLevel().dimension(),
                helper.absolutePos(relative)), side));
    }

    /** O clique do Vinculador na face {@code face} do bloco em {@code relative}, pelo caminho do item. */
    private static void click(GameTestHelper helper, TestPlayers.MenuPlayer player, BlockPos relative, Direction face) {
        BlockPos pos = helper.absolutePos(relative);
        ItemStack stack = player.getMainHandItem();
        stack.getItem().onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)));
    }

    private static DockLinks.@Nullable Result ctrlClick(GameTestHelper helper, TestPlayers.MenuPlayer player,
            BlockPos relative) {
        player.getCooldowns().removeCooldown(ModItems.LINKER.get());
        return DockSameBlock.link(player, helper.absolutePos(relative));
    }

    private static @Nullable DockMachine machine(GameTestHelper helper, PatternDockBlockEntity dock,
            BlockPos relative) {
        return dock.machineAt(helper.absolutePos(relative));
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
     * O clique numa face escolhe a Doca e aquele lado e esquece o roteador; de novo no mesmo lado, solta; o clique
     * num roteador esquece a Doca.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void clickOnFaceChoosesSide(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, DOCK, RouterTier.BASIC);
        TestPlayers.MenuPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    ItemStack stack = player.getMainHandItem();
                    LinkerItem.setRouter(stack, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(DOCK)));
                    click(helper, player, DOCK, Direction.NORTH);
                    DockChoice choice = LinkerItem.dock(stack);
                    helper.assertTrue(choice != null && choice.side() == NORTH
                            && choice.pos().pos().equals(dock.getBlockPos()), "escolha: " + choice);
                    helper.assertTrue(LinkerItem.router(stack) == null, "o roteador ficou escolhido");
                    TranslatableContents text = message(helper, player.actionBar, KEY + "chosen");
                    helper.assertTrue(text.getArgs()[2] instanceof Component free
                            && free.getContents() instanceof TranslatableContents c
                            && c.getKey().equals(KEY + "free"), "o lado novo não aparece livre");
                    // outra face troca o lado
                    click(helper, player, DOCK, Direction.UP);
                    helper.assertTrue(LinkerItem.dock(stack).side() == Direction.UP.ordinal(), "não trocou o lado");
                    // o mesmo lado de novo solta
                    click(helper, player, DOCK, Direction.UP);
                    helper.assertTrue(LinkerItem.dock(stack) == null, "não soltou");
                    message(helper, player.actionBar, KEY + "released");
                })
                .thenSucceed();
    }

    /**
     * Vincular a primeira fornalha preenche o perfil do lado pelo Afinador (Cima e Trás Insere, Baixo Extrai, o resto
     * Nenhum, como no roteador); a segunda só vincula; o clique numa vinculada desvincula.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void linkFurnaceTunesSide(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, DOCK, RouterTier.BASIC);
        BlockPos first = new BlockPos(0, 1, 1);
        BlockPos second = new BlockPos(2, 1, 1);
        furnace(helper, first, Direction.EAST);
        furnace(helper, second, Direction.SOUTH);
        TestPlayers.MenuPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    chooseDock(helper, player, DOCK, NORTH);
                    click(helper, player, first, Direction.UP);
                    DockMachine machine = machine(helper, dock, first);
                    helper.assertTrue(machine != null && machine.side() == NORTH
                            && machine.front() == Direction.EAST.ordinal(), "a primeira não entrou: " + machine);
                    helper.assertTrue(RemoteIndex.get().isIndexed(dock, helper.absolutePos(first)),
                            "a primeira não está no índice");
                    message(helper, player.actionBar, KEY + "profile");
                    DockSide side = dock.side(NORTH);
                    helper.assertValueEqual(mode(side, RemoteSide.TOP), PortMode.INSERT, "Cima");
                    helper.assertValueEqual(mode(side, RemoteSide.BACK), PortMode.INSERT, "Trás (o combustível)");
                    helper.assertValueEqual(mode(side, RemoteSide.BOTTOM), PortMode.EXTRACT, "Baixo");
                    helper.assertValueEqual(mode(side, RemoteSide.FRONT), PortMode.NONE, "Frente");
                    helper.assertValueEqual(mode(side, RemoteSide.LEFT), PortMode.NONE, "Esquerda");
                    helper.assertValueEqual(mode(side, RemoteSide.RIGHT), PortMode.NONE, "Direita");

                    click(helper, player, second, Direction.UP);
                    helper.assertTrue(machine(helper, dock, second) != null, "a segunda não entrou");
                    TranslatableContents text = message(helper, player.actionBar, KEY + "linked");
                    helper.assertValueEqual(text.getArgs()[2], 2, "as vagas usadas");

                    click(helper, player, first, Direction.UP);
                    helper.assertTrue(machine(helper, dock, first) == null, "o clique não desvinculou");
                    message(helper, player.actionBar, KEY + "unlinked");
                    helper.assertFalse(RemoteIndex.get().isIndexed(dock, helper.absolutePos(first)),
                            "a desvinculada ficou no índice");
                })
                .thenSucceed();
    }

    private static PortMode mode(DockSide side, RemoteSide remote) {
        return side.profile().face(ResourceType.ITEM, remote).mode();
    }

    /**
     * Ctrl + clique numa fileira de 10 fornalhas com a Doca Básica (8 máquinas): vincula 8, diz o limite do tier e faz
     * o perfil; o Ctrl + clique numa delas desvincula o lado inteiro.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ctrlClickLinksUpToTierLimit(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, DOCK, RouterTier.BASIC);
        List<BlockPos> furnaces = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                furnaces.add(new BlockPos(x, 0, z));
            }
        }
        furnaces.add(new BlockPos(0, 1, 0));
        for (BlockPos pos : furnaces) {
            furnace(helper, pos, Direction.NORTH);
        }
        TestPlayers.MenuPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    chooseDock(helper, player, DOCK, SOUTH);
                    DockLinks.Result result = ctrlClick(helper, player, new BlockPos(0, 1, 0));
                    helper.assertTrue(result != null && result.status() == DockLinks.Status.SAME_BLOCK_DONE
                            && result.linked() == 8 && result.overLimit() > 0, "resultado " + result);
                    helper.assertTrue(dock.side(SOUTH).machines().size() == 8, "máquinas no lado: "
                            + dock.side(SOUTH).machines().size());
                    helper.assertTrue(dock.activeCount() == 8, "ativas: " + dock.activeCount());
                    // "Vinculou 8 Fornalhas" ou, com o cubo lido inteiro, "Vinculou 8 de 10 Fornalhas"
                    helper.assertTrue(player.actionBar != null && player.actionBar.getContents()
                            instanceof TranslatableContents c && c.getKey().startsWith(KEY + "same.linked"),
                            "a mensagem: " + player.actionBar);
                    helper.assertTrue(hasPart(player.actionBar, "item.wirelessautomate.linker.range.same.limit"),
                            "sem o limite do tier: " + player.actionBar);
                    helper.assertValueEqual(mode(dock.side(SOUTH), RemoteSide.BOTTOM), PortMode.EXTRACT,
                            "o Ctrl + clique não fez o perfil");
                    // logo em seguida, o descanso ignora o pacote
                    helper.assertTrue(DockSameBlock.link(player, helper.absolutePos(new BlockPos(0, 0, 0))) == null,
                            "aceitou no descanso");

                    BlockPos linked = BlockPos.ZERO;
                    for (BlockPos pos : furnaces) {
                        if (machine(helper, dock, pos) != null) {
                            linked = pos;
                            break;
                        }
                    }
                    result = ctrlClick(helper, player, linked);
                    helper.assertTrue(result != null && result.status() == DockLinks.Status.SAME_BLOCK_UNLINKED
                            && result.linked() == 8, "desvincular: " + result);
                    helper.assertTrue(dock.side(SOUTH) == null && dock.machineCount() == 0,
                            "o lado ficou com máquinas");
                    message(helper, player.actionBar, KEY + "same.unlinked");
                })
                .thenSucceed();
    }

    /** Um lado de fornalhas recusa um barril; uma fornalha de outro lado desta Doca é recusada com o lado dela. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void refusesOtherBlockAndOtherSide(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, DOCK, RouterTier.BASIC);
        BlockPos furnace = new BlockPos(0, 1, 1);
        BlockPos barrel = new BlockPos(2, 1, 1);
        furnace(helper, furnace, Direction.EAST);
        helper.setBlock(barrel, Blocks.BARREL);
        TestPlayers.MenuPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    chooseDock(helper, player, DOCK, NORTH);
                    click(helper, player, furnace, Direction.UP);
                    click(helper, player, barrel, Direction.UP);
                    helper.assertTrue(machine(helper, dock, barrel) == null, "o barril entrou num lado de fornalhas");
                    message(helper, player.actionBar, KEY + "refusal.other_block");
                    // a fornalha é do lado Norte: o Sul a recusa, com o lado de quem a tem
                    chooseDock(helper, player, DOCK, SOUTH);
                    click(helper, player, furnace, Direction.UP);
                    helper.assertTrue(machine(helper, dock, furnace).side() == NORTH, "a fornalha trocou de lado");
                    message(helper, player.actionBar, KEY + "refusal.other_side");
                    // o Ctrl + clique na fornalha do Norte, com o Sul escolhido, também recusa
                    DockLinks.Result result = ctrlClick(helper, player, furnace);
                    helper.assertTrue(result != null && result.refusal() == DockLinks.Refusal.OTHER_SIDE,
                            "Ctrl + clique: " + result);
                    // o barril vai bem num lado livre
                    click(helper, player, barrel, Direction.UP);
                    helper.assertTrue(machine(helper, dock, barrel) != null, "o barril não entrou no lado livre");
                })
                .thenSucceed();
    }

    /**
     * Uma fornalha no lado de outra Doca carregada é recusada, com a posição dela; o Ctrl + clique deixa essa de fora
     * e vincula as outras.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void refusesMachineOfOtherDock(GameTestHelper helper) {
        PatternDockBlockEntity first = dock(helper, new BlockPos(0, 1, 0), RouterTier.BASIC);
        PatternDockBlockEntity second = dock(helper, new BlockPos(2, 1, 2), RouterTier.BASIC);
        BlockPos taken = new BlockPos(1, 1, 1);
        BlockPos free = new BlockPos(2, 1, 0);
        furnace(helper, taken, Direction.NORTH);
        furnace(helper, free, Direction.NORTH);
        TestPlayers.MenuPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    chooseDock(helper, player, new BlockPos(0, 1, 0), NORTH);
                    click(helper, player, taken, Direction.UP);
                    helper.assertTrue(machine(helper, first, taken) != null, "a primeira Doca não vinculou");
                    chooseDock(helper, player, new BlockPos(2, 1, 2), NORTH);
                    click(helper, player, taken, Direction.UP);
                    helper.assertTrue(machine(helper, second, taken) == null, "entrou em duas Docas");
                    TranslatableContents text = message(helper, player.actionBar, KEY + "refusal.other_dock");
                    helper.assertValueEqual(((Component) text.getArgs()[2]).getString(),
                            LinkerActions.position(first.getBlockPos()).getString(), "a posição da outra Doca");
                    DockLinks.Result result = ctrlClick(helper, player, taken);
                    helper.assertTrue(result != null && result.linked() == 1 && result.otherDock() == 1,
                            "Ctrl + clique: " + result);
                    helper.assertTrue(machine(helper, second, free) != null, "a livre não entrou");
                    helper.assertTrue(machine(helper, second, taken) == null, "a da outra Doca entrou");
                })
                .thenSucceed();
    }

    /** Uma fornalha a 7 blocos da Doca Básica (raio 6) fica fora do cubo, no clique e no Ctrl + clique. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void refusesOutsideCube(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, DOCK, RouterTier.BASIC);
        BlockPos far = DOCK.above(7);
        furnace(helper, far, Direction.NORTH);
        TestPlayers.MenuPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    try {
                        chooseDock(helper, player, DOCK, NORTH);
                        click(helper, player, far, Direction.UP);
                        helper.assertTrue(machine(helper, dock, far) == null, "a de fora do cubo entrou");
                        TranslatableContents text = message(helper, player.actionBar,
                                "item.wirelessautomate.linker.range.refusal.out_of_range");
                        helper.assertValueEqual(text.getArgs()[0], 7, "a distância");
                        helper.assertValueEqual(text.getArgs()[1], DockLinks.radius(dock), "o raio");
                        player.moveTo(helper.absoluteVec(Vec3.atCenterOf(far.above())));
                        DockLinks.Result result = ctrlClick(helper, player, far);
                        helper.assertTrue(result != null && result.refusal() == DockLinks.Refusal.OUT_OF_RANGE,
                                "Ctrl + clique: " + result);
                    } finally {
                        helper.setBlock(far, Blocks.AIR);
                    }
                })
                .thenSucceed();
    }

    /** Uma fornalha vinculada a um roteador também entra num lado da Doca, e continua no roteador. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void routerMachineAlsoJoinsDock(GameTestHelper helper) {
        RouterBlockEntity router = RangeTestSupport.routerWithMachines(helper);
        PatternDockBlockEntity dock = dock(helper, new BlockPos(2, 1, 2), RouterTier.BASIC);
        TestPlayers.MenuPlayer player = player(helper);
        helper.startSequence()
                .thenWaitUntil(() -> RangeTestSupport.waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(RangeTestSupport.rangeUpgrade());
                    RangeTestSupport.link(helper, router, RangeTestSupport.FURNACE_A);
                    chooseDock(helper, player, new BlockPos(2, 1, 2), NORTH);
                    click(helper, player, RangeTestSupport.FURNACE_A, Direction.UP);
                    helper.assertTrue(machine(helper, dock, RangeTestSupport.FURNACE_A) != null,
                            "a fornalha do roteador não entrou na Doca");
                    helper.assertTrue(router.remoteMachines().contains(helper.absolutePos(RangeTestSupport.FURNACE_A)),
                            "a fornalha saiu do roteador");
                    helper.assertTrue(RemoteIndex.get().isIndexed(dock, helper.absolutePos(RangeTestSupport.FURNACE_A)),
                            "a fornalha não está no índice pela Doca");
                })
                .thenSucceed();
    }
}
