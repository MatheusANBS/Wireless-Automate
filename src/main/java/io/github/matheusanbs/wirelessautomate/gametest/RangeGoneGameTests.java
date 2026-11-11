package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A remota que sai do lugar sem ser desvinculada (quebrada, trocada por outro bloco): o roteador de alcance a
 * desvincula sozinho pelos avisos que já tem (o cache invalidado, o aviso do mixin e da carga do chunk, a carga do
 * roteador), a vaga volta ao limite do tier e os cartões do grupo que esvazia caem no roteador, como no Desvincular
 * sem jogador. Girada (o mesmo bloco) fica; o chunk dela que descarrega não é quebra.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeGoneGameTests {
    private RangeGoneGameTests() {
    }

    private static void furnace(GameTestHelper helper, BlockPos relative, Direction facing) {
        helper.setBlock(relative, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, facing));
    }

    private static boolean linked(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return router.remoteMachines().index(helper.absolutePos(relative)) >= 0;
    }

    private static RouterBlockEntity router(GameTestHelper helper, RouterTier tier) {
        return chestWithRouter(helper, tier);
    }

    /**
     * Quebrada: sai da lista e do índice das remotas, e a vaga volta ao limite. A nona do Avançado (o limite padrão é
     * 8), além do limite, fica ativa quando a primeira quebra.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void brokenRemoteFreesSlot(GameTestHelper helper) {
        RouterBlockEntity router = router(helper, RouterTier.ADVANCED);
        int max = Config.maxMachines(RouterTier.ADVANCED);
        BlockPos[] machines = new BlockPos[max + 1];
        for (int i = 0; i < machines.length; i++) {
            machines[i] = new BlockPos(i % 5, 1, 3 + i / 5);
            furnace(helper, machines[i], Direction.NORTH);
        }
        BlockPos broken = helper.absolutePos(machines[0]);
        BlockPos last = helper.absolutePos(machines[max]);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    for (BlockPos machine : machines) {
                        link(helper, router, machine);
                    }
                    helper.assertValueEqual(router.machineReach()[max], RangeRules.Reach.OVER_LIMIT, "a nona antes");
                })
                .thenIdle(2)
                .thenExecute(() -> helper.setBlock(machines[0], Blocks.AIR))
                .thenWaitUntil(() -> helper.assertFalse(linked(helper, router, machines[0]), "a quebrada ficou vinculada"))
                .thenExecute(() -> {
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertValueEqual(remotes.size(), max, "as vinculadas");
                    helper.assertValueEqual(router.machineReach()[remotes.index(last)], RangeRules.Reach.ACTIVE,
                            "a vaga não voltou");
                    helper.assertFalse(RemoteIndex.get().isIndexed(router, broken), "a quebrada ficou no índice");
                    helper.assertTrue(RemoteIndex.get().isIndexed(router, last), "a nona não entrou no índice");
                })
                .thenSucceed();
    }

    /**
     * Trocada por outro bloco, com block entity (o alto-forno) ou sem (a pedra): sai. A girada, com o mesmo bloco e
     * outro estado, fica.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void replacedRemoteUnlinks(GameTestHelper helper) {
        RouterBlockEntity router = router(helper, RouterTier.ADVANCED);
        BlockPos rotated = BARREL;
        furnace(helper, FURNACE_A, Direction.NORTH);
        furnace(helper, FURNACE_B, Direction.NORTH);
        furnace(helper, rotated, Direction.NORTH);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, rotated);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.setBlock(FURNACE_A, Blocks.BLAST_FURNACE.defaultBlockState()
                            .setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
                    helper.setBlock(FURNACE_B, Blocks.STONE);
                    furnace(helper, rotated, Direction.EAST);
                })
                .thenWaitUntil(() -> {
                    helper.assertFalse(linked(helper, router, FURNACE_A), "a trocada por alto-forno ficou");
                    helper.assertFalse(linked(helper, router, FURNACE_B), "a trocada por pedra ficou");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(linked(helper, router, rotated), "a girada saiu");
                    helper.assertValueEqual(router.remoteMachines().size(), 1, "as vinculadas");
                })
                .thenSucceed();
    }

    /**
     * Os cartões não somem: o grupo que esvazia com a quebrada sai e os cartões caem no roteador, como no Desvincular
     * sem jogador; o grupo que fica com outra máquina guarda os dele.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void goneRemoteKeepsCards(GameTestHelper helper) {
        RouterBlockEntity router = router(helper, RouterTier.ADVANCED);
        furnace(helper, FURNACE_A, Direction.NORTH);
        furnace(helper, FURNACE_B, Direction.NORTH);
        helper.setBlock(BARREL, Blocks.BARREL);
        int[] groups = new int[2];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    groups[0] = groupOf(router, helper.absolutePos(FURNACE_A));
                    groups[1] = groupOf(router, helper.absolutePos(BARREL));
                    router.setCard(EditTarget.group(groups[0]), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                    router.setCard(EditTarget.group(groups[1]), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.setBlock(FURNACE_A, Blocks.AIR);
                    helper.setBlock(BARREL, Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    helper.assertFalse(linked(helper, router, FURNACE_A), "a fornalha quebrada ficou");
                    helper.assertFalse(linked(helper, router, BARREL), "o barril quebrado ficou");
                })
                .thenExecute(() -> {
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertTrue(remotes.group(groups[1]) == null, "o grupo vazio do barril ficou");
                    helper.assertItemEntityCountIs(ModItems.FILTER_CARD.get(), ROUTER, 3.0, 1);
                    helper.assertTrue(remotes.group(groups[0]) != null, "o grupo da fornalha que ficou saiu");
                    helper.assertTrue(router.card(EditTarget.group(groups[0]), ResourceType.ITEM, Direction.SOUTH, 0)
                            .is(ModItems.FILTER_CARD.get()), "o grupo que ficou perdeu o cartão");
                })
                .thenSucceed();
    }

    /**
     * O chunk da remota descarrega de verdade (o cache dela é invalidado na descarga): ela continua vinculada, e a
     * conferência não carrega o chunk. Carregado de novo, a quebra dela ainda é vista (a vigia volta pela carga do
     * chunk). A máquina vai no raio do Ultimate, num chunk descarregado que o teste força e depois solta.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void unloadedChunkKeepsRemote(GameTestHelper helper) {
        RouterBlockEntity router = router(helper, RouterTier.ULTIMATE);
        BlockPos[] far = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    far[0] = unloadedInReach(helper, router);
                    helper.assertTrue(far[0] != null, "nenhum chunk descarregado no raio do Ultimate");
                    ChunkPos chunk = new ChunkPos(far[0]);
                    helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
                    helper.getLevel().setBlockAndUpdate(far[0], Blocks.FURNACE.defaultBlockState());
                    router.setRangeUpgrade(rangeUpgrade());
                    helper.assertTrue(router.linkMachine(far[0]), "não vinculou");
                })
                .thenIdle(3)
                .thenExecute(() -> forced(helper, far[0], false))
                .thenWaitUntil(() -> helper.assertFalse(helper.getLevel().isLoaded(far[0]), "o chunk não descarregou"))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(router.remoteMachines().index(far[0]) >= 0, "a descarga desvinculou a máquina");
                    helper.assertFalse(helper.getLevel().isLoaded(far[0]), "a conferência carregou o chunk");
                    forced(helper, far[0], true);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(router.remoteMachines().index(far[0]) >= 0, "a carga desvinculou a máquina");
                    helper.getLevel().setBlockAndUpdate(far[0], Blocks.AIR.defaultBlockState());
                })
                .thenWaitUntil(() -> helper.assertTrue(router.remoteMachines().index(far[0]) < 0,
                        "quebrada depois da carga do chunk, ficou vinculada"))
                .thenExecute(() -> forced(helper, far[0], false))
                .thenSucceed();
    }

    private static void forced(GameTestHelper helper, BlockPos pos, boolean forced) {
        ChunkPos chunk = new ChunkPos(pos);
        helper.getLevel().setChunkForced(chunk.x, chunk.z, forced);
    }

    /** Uma posição num chunk descarregado dentro do raio do roteador (nas oito direções, do raio para dentro), ou nula. */
    private static BlockPos unloadedInReach(GameTestHelper helper, RouterBlockEntity router) {
        int reach = Config.machineRadius(router.tier());
        for (int distance = reach; distance >= 32; distance -= 16) {
            for (int i = 0; i < 8; i++) {
                int dx = i == 0 || i == 4 || i == 5 ? -distance : i == 1 || i == 6 || i == 7 ? distance : 0;
                int dz = i == 2 || i == 4 || i == 6 ? -distance : i == 3 || i == 5 || i == 7 ? distance : 0;
                BlockPos candidate = router.getBlockPos().offset(dx, -1, dz);
                if (!helper.getLevel().isLoaded(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Quebrada com o roteador descarregado (nenhum aviso chega a ele): sai na carga do roteador. Num lote só dele,
     * como o {@code DockGoneGameTests.goneMachineLeavesOnDockLoad}: a carga de chunk simulada em paralelo
     * ({@code RangeWakeGameTests}) avisaria as máquinas.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_range_load")
    public static void goneRemoteLeavesOnRouterLoad(GameTestHelper helper) {
        RouterBlockEntity router = router(helper, RouterTier.ADVANCED);
        furnace(helper, FURNACE_A, Direction.NORTH);
        furnace(helper, FURNACE_B, Direction.NORTH);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    router.onChunkUnloaded();
                    helper.setBlock(FURNACE_A, Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(linked(helper, router, FURNACE_A), "saiu com o roteador descarregado");
                    router.onLoad();
                })
                .thenWaitUntil(() -> helper.assertFalse(linked(helper, router, FURNACE_A), "ficou depois da carga"))
                .thenExecute(() -> helper.assertTrue(linked(helper, router, FURNACE_B), "a que estava no lugar saiu"))
                .thenSucceed();
    }
}
