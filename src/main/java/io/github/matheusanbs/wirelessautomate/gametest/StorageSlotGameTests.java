package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Os slots do Baú visto como {@link IItemHandler} ficam no lugar durante o tick: o tipo que acaba vira um
 * slot vazio e a lista só se fecha no fim do tick. O storage bus do AE2 tira de um slot em laço até ele
 * voltar vazio; se o tipo seguinte andasse para o slot esgotado, o laço o levaria contado como o primeiro
 * (o pedido duplicado, o outro tipo apagado).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class StorageSlotGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private static StorageChestBlockEntity chest(GameTestHelper helper) {
        helper.setBlock(A, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ELITE));
        return helper.getBlockEntity(A);
    }

    /** O laço do {@code ExternalStorageFacade.ItemHandlerFacade} do AE2 no modo MODULATE, num slot. */
    private static int ae2Extract(IItemHandler handler, int slot, int amount) {
        int total = 0;
        while (true) {
            int extracted = handler.extractItem(slot, amount - total, false).getCount();
            if (extracted <= 0) {
                return total;
            }
            total += extracted;
        }
    }

    /** Pedir mais do que o slot tem tira só o tipo dele, e o tipo seguinte fica intacto. */
    @GameTest(template = "empty")
    public static void exhaustedSlotDoesNotTakeTheNextType(GameTestHelper helper) {
        StorageChestBlockEntity entity = chest(helper);
        ItemStorage storage = entity.storage();
        IItemHandler handler = entity.handler();
        storage.insert(new ItemStack(Items.DIAMOND), 10, false);
        storage.insert(new ItemStack(Items.EMERALD), 100, false);
        storage.insert(new ItemStack(Items.GOLD_INGOT), 100, false);

        int taken = ae2Extract(handler, 0, 64);

        helper.assertValueEqual(taken, 10, "só os diamantes que havia");
        helper.assertValueEqual(storage.count(new ItemStack(Items.DIAMOND)), 0L, "diamantes");
        helper.assertValueEqual(storage.count(new ItemStack(Items.EMERALD)), 100L, "esmeraldas intactas");
        helper.assertValueEqual(storage.count(new ItemStack(Items.GOLD_INGOT)), 100L, "ouro intacto");
        helper.assertValueEqual(storage.total(), 200L, "total");
        helper.assertValueEqual(storage.types(), 2, "tipos");
        helper.succeed();
    }

    /**
     * O slot esgotado fica vazio no mesmo lugar até o fim do tick: aceita o mesmo tipo de volta, não entra no
     * NBT e some da lista no tick seguinte.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exhaustedSlotStaysUntilTheEndOfTheTick(GameTestHelper helper) {
        StorageChestBlockEntity entity = chest(helper);
        ItemStorage storage = entity.storage();
        IItemHandler handler = entity.handler();
        helper.startSequence()
                .thenExecute(() -> {
                    storage.insert(new ItemStack(Items.DIAMOND), 10, false);
                    storage.insert(new ItemStack(Items.EMERALD), 100, false);
                    handler.extractItem(0, 64, false);
                    helper.assertValueEqual(handler.getSlots(), 3, "o slot esgotado continua lá");
                    helper.assertTrue(handler.getStackInSlot(0).isEmpty(), "o slot esgotado aparece vazio");
                    helper.assertTrue(handler.extractItem(0, 64, true).isEmpty(), "nada sai do slot esgotado");
                    helper.assertTrue(handler.getStackInSlot(1).is(Items.EMERALD), "as esmeraldas no lugar");
                    helper.assertValueEqual(storage.save(helper.getLevel().registryAccess()).size(), 1,
                            "o slot esgotado não vai para o NBT");
                    helper.assertFalse(storage.isEmpty(), "ainda há esmeraldas");

                    helper.assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND, 5), false).isEmpty(),
                            "o mesmo tipo volta ao slot");
                    helper.assertValueEqual(handler.getStackInSlot(0).getCount(), 5, "diamantes de volta");
                    handler.extractItem(0, 64, false);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(handler.getSlots(), 2, "a lista se fecha no fim do tick"))
                .thenExecute(() -> {
                    helper.assertTrue(handler.getStackInSlot(0).is(Items.EMERALD), "as esmeraldas no slot 0");
                    helper.assertValueEqual(storage.types(), 1, "tipos");
                })
                .thenSucceed();
    }
}
