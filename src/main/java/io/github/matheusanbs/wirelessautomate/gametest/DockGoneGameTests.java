package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockChoice;
import io.github.matheusanbs.wirelessautomate.dock.DockLinks;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockTuner;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A máquina que sai do lugar sem ser desvinculada (quebrada, trocada por outro bloco): a Doca a desvincula sozinha
 * pelos avisos que já tem (o cache invalidado, o aviso do mixin, a carga da Doca), a vaga volta ao limite e o lado sem
 * máquinas solta o bloco, menos com pendente. O chunk da máquina que descarrega não conta como quebra. O Vinculador
 * numa posição vinculada já vazia desvincula com o nome do bloco do lado.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockGoneGameTests {
    private static final String KEY = "item.wirelessautomate.linker.dock.";
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);
    private static final BlockPos FURNACE_A = new BlockPos(3, 1, 1);
    private static final BlockPos FURNACE_B = new BlockPos(3, 1, 2);
    private static final BlockPos BLAST = new BlockPos(3, 1, 3);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation FURNACE = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);

    private DockGoneGameTests() {
    }

    private static PatternDockBlockEntity dock(GameTestHelper helper, RouterTier tier) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(DOCK);
    }

    private static void place(GameTestHelper helper, BlockPos relative, Block block) {
        helper.setBlock(relative, block.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
    }

    private static void link(GameTestHelper helper, PatternDockBlockEntity dock, BlockPos relative) {
        helper.assertTrue(dock.link(NORTH, helper.absolutePos(relative), FURNACE, Direction.NORTH.ordinal()),
                "não vinculou " + relative.toShortString());
    }

    /** Um jogador em sobrevivência com o Vinculador na mão e o lado Norte da Doca escolhido. */
    private static TestPlayers.MenuPlayer player(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        LinkerItem.setDock(linker, new DockChoice(GlobalPos.of(helper.getLevel().dimension(),
                helper.absolutePos(DOCK)), NORTH));
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        return player;
    }

    private static DockLinks.Result toggle(GameTestHelper helper, TestPlayers.MenuPlayer player, BlockPos relative) {
        return DockLinks.toggle(player, player.getMainHandItem(), helper.absolutePos(relative));
    }

    /** Quebrada (trocada por ar): sai do lado, do índice das remotas e do das Docas, e a vaga volta ao limite. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void brokenMachineUnlinks(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        place(helper, FURNACE_A, Blocks.FURNACE);
        place(helper, FURNACE_B, Blocks.FURNACE);
        BlockPos broken = helper.absolutePos(FURNACE_A);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    link(helper, dock, FURNACE_A);
                    link(helper, dock, FURNACE_B);
                    helper.assertValueEqual(dock.activeCount(), 2, "as ativas antes");
                })
                .thenIdle(2)
                .thenExecute(() -> helper.setBlock(FURNACE_A, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(dock.machineAt(broken) == null, "a quebrada ficou vinculada"))
                .thenExecute(() -> {
                    helper.assertValueEqual(dock.activeCount(), 1, "a vaga não voltou");
                    helper.assertValueEqual(dock.machineCount(), 1, "as vinculadas");
                    helper.assertFalse(RemoteIndex.get().isIndexed(dock, broken), "ficou no índice das remotas");
                    helper.assertFalse(DockTuner.linked(helper.getLevel(), broken), "ficou no índice das Docas");
                    DockSide side = dock.side(NORTH);
                    helper.assertTrue(side != null && FURNACE.equals(side.block()), "o lado perdeu o bloco");
                    helper.assertTrue(dock.machineAt(helper.absolutePos(FURNACE_B)) != null, "a outra saiu");
                })
                .thenSucceed();
    }

    /**
     * Trocada por outro bloco: um com block entity (o alto-forno) e um sem (a pedra) saem do lado; sem máquinas e sem
     * pendente, o lado deixa de existir.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void replacedMachineUnlinks(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        place(helper, FURNACE_A, Blocks.FURNACE);
        place(helper, FURNACE_B, Blocks.FURNACE);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    link(helper, dock, FURNACE_A);
                    link(helper, dock, FURNACE_B);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    place(helper, FURNACE_A, Blocks.BLAST_FURNACE);
                    helper.setBlock(FURNACE_B, Blocks.STONE);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(dock.machineCount(), 0, "as trocadas ficaram"))
                .thenExecute(() -> {
                    helper.assertTrue(dock.side(NORTH) == null, "o lado sem máquinas ficou");
                    helper.assertValueEqual(dock.activeCount(), 0, "as vagas");
                })
                .thenSucceed();
    }

    /** Com as máquinas do lado quebradas, o Vinculador põe nele um bloco de outro tipo. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void emptiedSideAcceptsOtherBlock(GameTestHelper helper) {
        dock(helper, RouterTier.BASIC);
        place(helper, FURNACE_A, Blocks.FURNACE);
        place(helper, BLAST, Blocks.BLAST_FURNACE);
        TestPlayers.MenuPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), DockLinks.Status.LINKED,
                            "a fornalha");
                    DockLinks.Result refused = toggle(helper, player, BLAST);
                    helper.assertValueEqual(refused.refusal(), DockLinks.Refusal.OTHER_BLOCK, "o alto-forno antes");
                })
                .thenIdle(2)
                .thenExecute(() -> helper.setBlock(FURNACE_A, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.<PatternDockBlockEntity>getBlockEntity(DOCK).side(NORTH) == null, "o lado guardou o bloco"))
                .thenExecute(() -> {
                    helper.assertValueEqual(toggle(helper, player, BLAST).status(), DockLinks.Status.LINKED,
                            "o alto-forno depois");
                    DockSide side = helper.<PatternDockBlockEntity>getBlockEntity(DOCK).side(NORTH);
                    helper.assertTrue(side != null
                            && BuiltInRegistries.BLOCK.getKey(Blocks.BLAST_FURNACE).equals(side.block()),
                            "o lado não ficou com o alto-forno");
                })
                .thenSucceed();
    }

    /** Com pendente, o lado sem máquinas guarda o bloco depois da quebra e recusa outro bloco. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pendingKeepsBlockAfterBreak(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        place(helper, FURNACE_A, Blocks.FURNACE);
        place(helper, BLAST, Blocks.BLAST_FURNACE);
        BlockPos broken = helper.absolutePos(FURNACE_A);
        TestPlayers.MenuPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    // o perfil padrão não tem face Insere: o pendente não sai na fornalha antes da quebra
                    link(helper, dock, FURNACE_A);
                    dock.side(NORTH).setPending(List.of(new ItemStack(Items.IRON_ORE, 3)), List.of(), broken);
                })
                .thenIdle(2)
                .thenExecute(() -> helper.setBlock(FURNACE_A, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(dock.machineAt(broken) == null, "a quebrada ficou vinculada"))
                .thenExecute(() -> {
                    DockSide side = dock.side(NORTH);
                    helper.assertTrue(side != null && FURNACE.equals(side.block()), "o lado soltou o bloco");
                    helper.assertTrue(side.hasPending(), "o pendente saiu");
                    helper.assertValueEqual(toggle(helper, player, BLAST).refusal(), DockLinks.Refusal.OTHER_BLOCK,
                            "o alto-forno com pendente");
                })
                .thenSucceed();
    }

    /**
     * O Vinculador numa posição vinculada que ficou vazia (antes de a Doca conferir) desvincula com a mensagem de
     * sempre e o nome do bloco do lado, não o do ar.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void linkerOnEmptyPositionUnlinks(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        place(helper, FURNACE_A, Blocks.FURNACE);
        TestPlayers.MenuPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock, FURNACE_A))
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.setBlock(FURNACE_A, Blocks.AIR);
                    helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), DockLinks.Status.UNLINKED,
                            "o clique no lugar vazio");
                    Component message = player.actionBar;
                    helper.assertTrue(message != null && message.getContents() instanceof TranslatableContents c
                            && c.getKey().equals(KEY + "unlinked"), "a mensagem: " + message);
                    Object name = ((TranslatableContents) message.getContents()).getArgs()[0];
                    helper.assertTrue(name instanceof Component part && part.getContents() instanceof TranslatableContents c
                            && c.getKey().equals(Blocks.FURNACE.getDescriptionId()), "o nome na mensagem: " + name);
                    helper.assertValueEqual(dock.machineCount(), 0, "as vinculadas");
                })
                .thenSucceed();
    }

    /**
     * Quebrada com a Doca descarregada (sem vigia, nenhum aviso chega): sai na carga da Doca. Num lote só dele, como o
     * {@code DockReturnGameTests.ingotReturnsAfterDockLoad}: a carga de chunk simulada em paralelo
     * ({@code RangeWakeGameTests}) avisaria as máquinas.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_dock_load")
    public static void goneMachineLeavesOnDockLoad(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        place(helper, FURNACE_A, Blocks.FURNACE);
        BlockPos broken = helper.absolutePos(FURNACE_A);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock, FURNACE_A))
                .thenIdle(2)
                .thenExecute(() -> {
                    dock.onChunkUnloaded();
                    helper.setBlock(FURNACE_A, Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(dock.machineAt(broken) != null, "saiu com a Doca descarregada");
                    dock.onLoad();
                })
                .thenWaitUntil(() -> helper.assertTrue(dock.machineAt(broken) == null, "ficou depois da carga"))
                .thenSucceed();
    }

    /**
     * O chunk da máquina descarrega de verdade (o cache dela é invalidado na descarga): ela continua vinculada, e a
     * conferência não carrega o chunk. A máquina vai a 96 blocos da Doca Ultimate, num chunk descarregado que o teste
     * força para pôr a fornalha e depois solta, como no {@code DockDeliveryGameTests.unloadedMachineSkipped}.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void unloadedChunkKeepsMachine(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.ULTIMATE);
        BlockPos dockPos = helper.absolutePos(DOCK);
        BlockPos[] far = new BlockPos[1];
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    int reach = Config.dockRadius(RouterTier.ULTIMATE);
                    for (int i = 0; i < 8 && far[0] == null; i++) {
                        int dx = i == 0 || i == 4 || i == 5 ? -reach : i == 1 || i == 6 || i == 7 ? reach : 0;
                        int dz = i == 2 || i == 4 || i == 6 ? -reach : i == 3 || i == 5 || i == 7 ? reach : 0;
                        BlockPos candidate = dockPos.offset(dx, 0, dz);
                        if (!helper.getLevel().isLoaded(candidate)) {
                            far[0] = candidate;
                        }
                    }
                    helper.assertTrue(far[0] != null, "os oito chunks a " + reach + " blocos estão carregados");
                    ChunkPos chunk = new ChunkPos(far[0]);
                    helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
                    helper.getLevel().setBlockAndUpdate(far[0], Blocks.FURNACE.defaultBlockState());
                    helper.assertTrue(dock.link(NORTH, far[0], FURNACE, Direction.NORTH.ordinal()), "não vinculou");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    ChunkPos chunk = new ChunkPos(far[0]);
                    helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
                })
                .thenWaitUntil(() -> helper.assertFalse(helper.getLevel().isLoaded(far[0]), "o chunk não descarregou"))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(dock.machineAt(far[0]) != null, "a descarga desvinculou a máquina");
                    helper.assertFalse(helper.getLevel().isLoaded(far[0]), "a conferência carregou o chunk");
                    ChunkPos chunk = new ChunkPos(far[0]);
                    helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(dock.machineAt(far[0]) != null, "a carga desvinculou a máquina");
                    helper.getLevel().setBlockAndUpdate(far[0], Blocks.AIR.defaultBlockState());
                    ChunkPos chunk = new ChunkPos(far[0]);
                    helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
                })
                .thenSucceed();
    }
}
