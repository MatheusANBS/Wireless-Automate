package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Roteador de alcance, a memória sob demanda: uma máquina só com faces de itens cria só os arranjos de itens (dos
 * caches de capability e das portas), e continua movendo e acordando.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeMemoryGameTests {
    /**
     * A remota com uma face de itens em Extrair entrega na presa; depois de vazia e dormindo, um item posto nela pelo
     * {@code IItemHandler} (o mixin) a acorda e chega também. Nas duas máquinas, só o tipo de itens tem portas, e no
     * máximo as duas capabilities de itens têm o arranjo de caches.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void itemFacesAllocateOnlyItemArrays(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, REMOTE_CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        BlockPos remote = helper.absolutePos(REMOTE_CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, CHEST) == 3, "a remota não entregou"))
                .thenIdle(40)
                .thenExecute(() -> {
                    NetworkManager manager = NetworkManager.get();
                    helper.assertTrue(manager.portTypesForTests(router, remote) == 1,
                            "tipos com portas na remota: " + manager.portTypesForTests(router, remote));
                    helper.assertTrue(manager.portTypesForTests(router, null) == 1,
                            "tipos com portas na presa: " + manager.portTypesForTests(router, null));
                    int remoteArrays = router.remoteCaches(remote).capabilityArraysForTests();
                    helper.assertTrue(remoteArrays >= 1 && remoteArrays <= 2, "arranjos de caches na remota: " + remoteArrays);
                    int attachedArrays = router.attachedCaches().capabilityArraysForTests();
                    helper.assertTrue(attachedArrays >= 1 && attachedArrays <= 2, "arranjos de caches na presa: " + attachedArrays);
                    IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, remote, Direction.UP);
                    helper.assertTrue(handler != null, "sem IItemHandler na remota");
                    handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 2), false);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, CHEST) == 5, "a remota não acordou"))
                .thenSucceed();
    }
}
