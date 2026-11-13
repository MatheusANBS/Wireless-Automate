package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockColors;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.packet.LinkerReachPayload;
import io.github.matheusanbs.wirelessautomate.range.RouterReachSync;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O cubo do alcance do roteador no Vinculador ({@link RouterReachSync}): o pacote com o raio, as remotas e a cor de
 * cada uma, o teto de máquinas e a regra do pedido do cliente. Os jogadores de teste não negociam os canais, então
 * o pacote é lido por {@link RouterReachSync#payload} e o pedido pela função que decide, {@link RouterReachSync#answers}.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RouterReachGameTests {
    /**
     * Com o upgrade: o raio do tier, as remotas na ordem de vínculo, a cor do grupo (dois blocos, duas cores) e a presa
     * fora; desvinculada, a máquina sai.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void payloadWithUpgrade(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, BARREL);
                    link(helper, router, FURNACE_B);
                    LinkerReachPayload payload = RouterReachSync.payload(router);
                    helper.assertValueEqual(payload.kind(), LinkerReachPayload.ROUTER, "o dono do cubo");
                    helper.assertValueEqual(payload.origin(), router.getBlockPos(), "o roteador do cubo");
                    helper.assertValueEqual(payload.radius(), Config.machineRadius(RouterTier.ADVANCED), "o raio");
                    helper.assertValueEqual(payload.tier(), RouterTier.ADVANCED.ordinal(), "o tier");
                    helper.assertValueEqual(payload.ticks(), 0, "o prazo");
                    helper.assertValueEqual(payload.machines().length, 3, "as remotas (a presa fica fora)");
                    helper.assertValueEqual(payload.machines()[0], helper.absolutePos(FURNACE_A).asLong(), "a 1ª");
                    helper.assertValueEqual(payload.machines()[1], helper.absolutePos(BARREL).asLong(), "a 2ª");
                    helper.assertValueEqual(payload.machines()[2], helper.absolutePos(FURNACE_B).asLong(), "a 3ª");
                    helper.assertValueEqual((int) payload.colors()[0], DockColors.ORDER[0], "a cor do 1º grupo");
                    helper.assertValueEqual((int) payload.colors()[1], DockColors.ORDER[1], "a cor do 2º grupo");
                    helper.assertValueEqual((int) payload.colors()[2], DockColors.ORDER[0], "a fornalha repete o grupo");
                    router.unlinkMachine(helper.absolutePos(BARREL));
                    helper.assertValueEqual(RouterReachSync.payload(router).machines().length, 2, "a desvinculada saiu");
                })
                .thenSucceed();
    }

    /** Uma remota fora do raio vem cinza; sem o upgrade, sem cubo e todas cinza. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void payloadInactiveAndNoCube(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    LinkerReachPayload without = RouterReachSync.payload(router);
                    helper.assertValueEqual(without.radius(), LinkerReachPayload.NO_CUBE, "sem o upgrade, sem cubo");
                    helper.assertValueEqual(without.machines().length, 1, "a remota fica");
                    helper.assertValueEqual(without.colors()[0], LinkerReachPayload.INACTIVE, "sem o upgrade, cinza");

                    router.setRangeUpgrade(rangeUpgrade());
                    helper.assertValueEqual((int) RouterReachSync.payload(router).colors()[0], DockColors.ORDER[0],
                            "com o upgrade, a cor do grupo");
                    // numa coluna abaixo do mundo: fora do raio, só a lista (o índice dos vizinhos não é tocado)
                    BlockPos origin = helper.absolutePos(ROUTER);
                    BlockPos far = new BlockPos(origin.getX(), helper.getLevel().getMinBuildHeight() - 1, origin.getZ());
                    router.remoteMachines().link(far, BuiltInRegistries.BLOCK.getKey(Blocks.BARREL), -1, null);
                    LinkerReachPayload with = RouterReachSync.payload(router);
                    helper.assertValueEqual(with.radius(), Config.machineRadius(RouterTier.ADVANCED), "o raio com o upgrade");
                    helper.assertValueEqual(with.machines().length, 2, "as duas remotas");
                    helper.assertValueEqual(with.machines()[1], far.asLong(), "a de fora do raio");
                    helper.assertValueEqual(with.colors()[1], LinkerReachPayload.INACTIVE, "fora do raio, cinza");
                })
                .thenSucceed();
    }

    /** O pacote leva no máximo {@link LinkerReachPayload#MAX_MACHINES} máquinas, mesmo com o roteador acima disso. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void payloadCapped(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    BlockPos origin = helper.absolutePos(ROUTER);
                    int below = helper.getLevel().getMinBuildHeight() - 1;
                    for (int i = 0; i < LinkerReachPayload.MAX_MACHINES + 4; i++) {
                        router.remoteMachines().link(new BlockPos(origin.getX(), below - i, origin.getZ()),
                                BuiltInRegistries.BLOCK.getKey(Blocks.BARREL), -1, null);
                    }
                    LinkerReachPayload payload = RouterReachSync.payload(router);
                    helper.assertValueEqual(router.remoteMachines().size(), LinkerReachPayload.MAX_MACHINES + 4,
                            "as vinculadas");
                    helper.assertValueEqual(payload.machines().length, LinkerReachPayload.MAX_MACHINES, "o teto");
                    helper.assertValueEqual(payload.colors().length, LinkerReachPayload.MAX_MACHINES, "as cores");
                })
                .thenSucceed();
    }

    /**
     * O pedido do cliente: só vale com o Vinculador de uma das mãos com este roteador escolhido; sem ele, com outra
     * posição ou com o Vinculador solto, não responde. Escolher, vincular e desvincular pelo clique mandam o cubo sem
     * erro com o jogador de teste.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void requestOnlyWithChosenRouter(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        BlockPos pos = router.getBlockPos();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        helper.assertFalse(RouterReachSync.answers(player, pos), "sem roteador escolhido");
                        RouterReachSync.request(player, pos);
                        router.setRangeUpgrade(rangeUpgrade());
                        click(helper, player, ROUTER, false);
                        helper.assertTrue(LinkerItem.router(linker) != null, "o clique não escolheu o roteador");
                        helper.assertTrue(RouterReachSync.answers(player, pos), "o escolhido não é respondido");
                        helper.assertFalse(RouterReachSync.answers(player, pos.above()), "outra posição");
                        RouterReachSync.request(player, pos);
                        RouterReachSync.request(null, pos);
                        click(helper, player, FURNACE_A, false);
                        helper.assertValueEqual(RouterReachSync.payload(router).machines().length, 1, "vinculou");
                        click(helper, player, FURNACE_A, false);
                        helper.assertValueEqual(RouterReachSync.payload(router).machines().length, 0, "desvinculou");
                        ItemStack off = player.getMainHandItem();
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        helper.assertFalse(RouterReachSync.answers(player, pos), "sem o Vinculador na mão");
                        player.setItemInHand(InteractionHand.OFF_HAND, off);
                        helper.assertTrue(RouterReachSync.answers(player, pos), "o Vinculador na outra mão");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }
}
