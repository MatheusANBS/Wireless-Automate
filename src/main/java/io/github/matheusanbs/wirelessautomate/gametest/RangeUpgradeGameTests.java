package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Roteador de alcance, o Upgrade de Alcance: o slot na tela (aceita num tier com raio, recusa no Básico), o NBT
 * ({@code range_upgrade}) e o drop ao quebrar. Os outros assuntos ficam nos {@code Range*GameTests} ao lado, com o
 * apoio comum no {@link RangeTestSupport}.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeUpgradeGameTests {
    @GameTest(template = "empty")
    public static void slotAcceptsOnAdvanced(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    helper.assertTrue(menu.getSlot(RouterMenu.RANGE_SLOT).mayPlace(rangeUpgrade()),
                            "slot de alcance recusou o upgrade no Avançado");
                    helper.assertFalse(menu.getSlot(RouterMenu.RANGE_SLOT).mayPlace(new ItemStack(Items.DIAMOND)),
                            "slot de alcance aceitou diamante");
                    helper.assertFalse(menu.getSlot(RouterMenu.RANGE_SLOT).mayPlace(
                            new ItemStack(ModItems.CHUNK_LOADER_UPGRADE.get())), "slot de alcance aceitou o de chunk loading");
                    helper.assertFalse(menu.getSlot(RouterMenu.UPGRADE_SLOT).mayPlace(rangeUpgrade()),
                            "slot de chunk loading aceitou o de alcance");

                    // Shift + clique no inventário leva ao slot de alcance; no slot, traz de volta.
                    player.getInventory().setItem(0, rangeUpgrade());
                    int hotbar0 = RouterMenu.UPGRADE_SLOT - 9;
                    menu.clicked(hotbar0, 0, ClickType.QUICK_MOVE, player);
                    helper.assertTrue(router.rangeUpgrade().is(ModItems.RANGE_UPGRADE.get()),
                            "o upgrade de alcance não foi para o roteador");
                    helper.assertTrue(router.rangeActive(), "o upgrade de alcance não vale no Avançado");
                    helper.assertTrue(menu.getSlot(hotbar0).getItem().isEmpty(), "o upgrade ficou no inventário");
                    helper.assertFalse(router.hasChunkUpgrade(), "o upgrade de alcance foi para o slot de chunk loading");

                    menu.clicked(RouterMenu.RANGE_SLOT, 0, ClickType.QUICK_MOVE, player);
                    helper.assertTrue(router.rangeUpgrade().isEmpty(), "o upgrade de alcance ficou no roteador");
                    helper.assertFalse(router.rangeActive(), "alcance ativo sem o upgrade");
                    helper.assertTrue(player.getInventory().contains(rangeUpgrade()), "o upgrade não voltou ao inventário");
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void slotRefusesOnBasic(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.BASIC);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    helper.assertFalse(router.acceptsRangeUpgradeHere(rangeUpgrade()), "o Básico aceitou o upgrade");
                    helper.assertFalse(menu.getSlot(RouterMenu.RANGE_SLOT).mayPlace(rangeUpgrade()),
                            "slot de alcance aceitou o upgrade no Básico");

                    player.getInventory().setItem(0, rangeUpgrade());
                    int hotbar0 = RouterMenu.UPGRADE_SLOT - 9;
                    menu.clicked(hotbar0, 0, ClickType.QUICK_MOVE, player);
                    helper.assertTrue(router.rangeUpgrade().isEmpty(), "o upgrade de alcance entrou no Básico");
                    helper.assertTrue(player.getInventory().contains(rangeUpgrade()), "o upgrade sumiu do inventário");
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void rangeUpgradeSavedAndReloaded(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    CompoundTag tag = router.saveWithFullMetadata(helper.getLevel().registryAccess());
                    helper.assertTrue(tag.contains("range_upgrade"), "range_upgrade não foi salvo");
                    helper.assertFalse(tag.contains("upgrade"), "o upgrade de alcance foi salvo na chave do outro");

                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(tag, helper.getLevel().registryAccess());
                    helper.assertTrue(copy.rangeUpgrade().is(ModItems.RANGE_UPGRADE.get()), "range_upgrade não foi lido");
                    helper.assertTrue(copy.upgrade().isEmpty(), "o upgrade de alcance foi lido no slot do outro");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void breakDropsRangeUpgrade(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        BlockPos relative = new BlockPos(1, 2, 1);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    helper.destroyBlock(relative);
                    helper.assertBlockNotPresent(ModBlocks.ROUTER.get(), relative);
                    helper.assertItemEntityPresent(ModItems.RANGE_UPGRADE.get(), relative, 2.0);
                })
                .thenSucceed();
    }
}
