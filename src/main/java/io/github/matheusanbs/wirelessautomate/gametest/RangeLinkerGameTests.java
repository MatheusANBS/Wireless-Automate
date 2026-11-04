package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Roteador de alcance, o Vinculador no modo Vincular: vincular e desvincular pelo clique e as recusas.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeLinkerGameTests {
    private static final BlockPos SECOND_ROUTER_MACHINE = new BlockPos(2, 1, 2);
    private static final BlockPos STONE = new BlockPos(0, 1, 1);
    private static final BlockPos COMPOSTER = new BlockPos(2, 1, 1);

    private static void assertRefused(GameTestHelper helper, RangeLinks.Result result, LinkRefusal refusal, String what) {
        helper.assertTrue(result.status() == RangeLinks.Status.REFUSED && result.refusal() == refusal,
                what + ": esperava " + refusal + ", veio " + result);
    }

    /**
     * Modo Máquinas pelo clique: sem roteador escolhido não vincula; o clique no roteador o escolhe;
     * sem o upgrade recusa; com ele, o clique na fornalha vincula (e entra no índice) e o segundo
     * desvincula.
     */
    @GameTest(template = "empty")
    public static void linkerClickLinksAndUnlinks(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        BlockPos furnace = helper.absolutePos(FURNACE_A);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), RangeLinks.Status.NO_ROUTER,
                                "sem roteador escolhido");
                        click(helper, player, ROUTER, false);
                        helper.assertValueEqual(LinkerItem.router(linker),
                                GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()), "roteador escolhido");
                        assertRefused(helper, toggle(helper, player, FURNACE_A), LinkRefusal.NO_UPGRADE, "sem upgrade");
                        router.setRangeUpgrade(rangeUpgrade());
                        click(helper, player, FURNACE_A, false);
                        helper.assertTrue(router.remoteMachines().contains(furnace), "o clique não vinculou");
                        helper.assertTrue(RemoteIndex.get().isIndexed(router, furnace), "fora do índice");
                        click(helper, player, FURNACE_A, false);
                        helper.assertFalse(router.remoteMachines().contains(furnace), "o segundo clique não desvinculou");
                        helper.assertFalse(RemoteIndex.get().isIndexed(router, furnace), "ficou no índice");
                        click(helper, player, FURNACE_A, true);
                        helper.assertFalse(router.remoteMachines().contains(furnace), "Shift + clique num bloco vinculou");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Recusas do clique: o próprio roteador, outro roteador, a máquina presa, um bloco sem capability,
     * ar e uma máquina fora do raio. Um bloco com capability sem block entity (composteira) vincula.
     * Com o roteador escolhido quebrado, a resposta é própria e o componente fica.
     */
    @GameTest(template = "empty")
    public static void linkerClickRefusals(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.setBlock(SECOND_ROUTER_MACHINE, Blocks.CHEST);
        helper.setBlock(SECOND_ROUTER_MACHINE.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        helper.setBlock(STONE, Blocks.STONE);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        helper.setBlock(outside, Blocks.BARREL);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        assertRefused(helper, toggle(helper, player, ROUTER), LinkRefusal.SELF, "o próprio roteador");
                        assertRefused(helper, toggle(helper, player, SECOND_ROUTER_MACHINE.above()), LinkRefusal.ROUTER,
                                "outro roteador");
                        assertRefused(helper, toggle(helper, player, CHEST), LinkRefusal.ATTACHED, "a máquina presa");
                        assertRefused(helper, toggle(helper, player, STONE), LinkRefusal.NOT_A_MACHINE, "pedra");
                        assertRefused(helper, toggle(helper, player, new BlockPos(0, 2, 0)), LinkRefusal.NOT_A_MACHINE, "ar");
                        assertRefused(helper, toggle(helper, player, outside), LinkRefusal.OUT_OF_RANGE, "fora do raio");
                        helper.assertTrue(router.remoteMachines().machines().isEmpty(), "uma recusa vinculou");
                        helper.assertValueEqual(toggle(helper, player, COMPOSTER).status(), RangeLinks.Status.LINKED,
                                "a composteira (capability sem block entity) no clique");

                        helper.destroyBlock(ROUTER);
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(),
                                RangeLinks.Status.ROUTER_MISSING, "roteador quebrado");
                        helper.assertTrue(LinkerItem.router(linker) != null, "limpou o roteador escolhido");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** Com o limite do Avançado cheio, o clique seguinte é recusado com {@code LIMIT}. */
    @GameTest(template = "empty")
    public static void linkerClickRefusesOverLimit(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int max = Config.maxMachines(RouterTier.ADVANCED);
        BlockPos[] barrels = new BlockPos[max + 1];
        int n = 0;
        for (int y = 3; y <= 5 && n < barrels.length; y++) {
            for (int x = 0; x <= 2 && n < barrels.length; x += 2) {
                for (int z = 0; z <= 2 && n < barrels.length; z += 2) {
                    barrels[n] = new BlockPos(x, y, z);
                    helper.setBlock(barrels[n++], Blocks.BARREL);
                }
            }
        }
        int placed = n;
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(placed == max + 1, "a config padrão mudou: limite " + max);
                        router.setRangeUpgrade(rangeUpgrade());
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        for (int i = 0; i < max; i++) {
                            helper.assertValueEqual(toggle(helper, player, barrels[i]).status(), RangeLinks.Status.LINKED,
                                    "barril " + i);
                        }
                        assertRefused(helper, toggle(helper, player, barrels[max]), LinkRefusal.LIMIT, "além do limite");
                        helper.assertFalse(router.remoteMachines().contains(helper.absolutePos(barrels[max])),
                                "vinculou além do limite");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }
}
