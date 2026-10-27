package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.packet.TvStorageContentsPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvStorageContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A grade de itens do Baú ligado à Tela: o servidor filtra pela busca (nome e id, {@code @mod}), manda até 256 mais o
 * total que casa, e sem busca vai do mais numeroso ao menos. Um Baú com mais de 256 tipos prova que o que estaria
 * fora dos 256 primeiros se acha pela busca.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvStorageContentsGameTests {
    private static final BlockPos CHEST = new BlockPos(0, 1, 1);
    private static final int TYPES = 600;

    private TvStorageContentsGameTests() {
    }

    /** Um Baú com {@link #TYPES} tipos, o i-ésimo com menos unidades que o anterior (de 21 a 1). */
    private static StorageChestBlockEntity filled(GameTestHelper helper, Item[] order) {
        helper.setBlock(CHEST, ModBlocks.STORAGE_CHEST.get().defaultBlockState());
        StorageChestBlockEntity chest = helper.getBlockEntity(CHEST);
        int n = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || n >= TYPES) {
                continue;
            }
            order[n] = item;
            chest.storage().insert(new ItemStack(item), 1 + (TYPES - n) / 30, false);
            n++;
        }
        return chest;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void withoutSearchGivesTheMostNumerousFirstAndCutsAt256(GameTestHelper helper) {
        Item[] order = new Item[TYPES];
        StorageChestBlockEntity chest = filled(helper, order);
        TvStorageContents.Result result = TvStorageContents.list(chest, "");
        helper.assertValueEqual(result.total(), chest.storage().types(), "total sem busca");
        helper.assertTrue(result.total() > TvStorageContentsPayload.MAX_ITEMS, "mais de 256 tipos");
        helper.assertValueEqual(result.items().size(), TvStorageContentsPayload.MAX_ITEMS, "cortado em 256");
        for (int i = 1; i < result.items().size(); i++) {
            helper.assertTrue(result.items().get(i - 1).count() >= result.items().get(i).count(), "ordem por quantidade");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void searchFindsATypeOutsideTheFirst256(GameTestHelper helper) {
        Item[] order = new Item[TYPES];
        StorageChestBlockEntity chest = filled(helper, order);
        Item far = order[TYPES - 1];
        helper.assertTrue(chest.storage().count(new ItemStack(far)) > 0, "o último tipo entrou");
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(far);
        TvStorageContents.Result result = TvStorageContents.list(chest, id.getPath());
        helper.assertTrue(result.total() >= 1, "a busca acha");
        helper.assertTrue(result.items().stream().anyMatch(entry -> entry.item().equals(id)),
                "o item fora dos 256 primeiros está nos resultados");
        helper.assertValueEqual(TvStorageContents.list(chest, "zzzzqqqq").total(), 0, "nada casa");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void modFilterAndTotalCountEveryMatch(GameTestHelper helper) {
        Item[] order = new Item[TYPES];
        StorageChestBlockEntity chest = filled(helper, order);
        int vanilla = 0;
        for (Item item : order) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) {
                vanilla++;
            }
        }
        TvStorageContents.Result result = TvStorageContents.list(chest, "@minecraft");
        helper.assertValueEqual(result.total(), vanilla, "total do mod");
        helper.assertValueEqual(result.items().size(), Math.min(vanilla, TvStorageContentsPayload.MAX_ITEMS), "lista");
        helper.succeed();
    }
}
