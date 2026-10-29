package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Baú: itens por tipo ({@link ItemStorage}), vistos pelos outros mods como um inventário comum
 * ({@link ItemStorageHandler}) e pelo roteador como {@link BulkItems}.
 */
public class StorageChestBlockEntity extends KeyedStorageBlockEntity<ItemStack> {
    private final ItemStorage storage = new ItemStorage(this::markContentsChanged, this::capacity);
    private final ItemStorageHandler handler = new ItemStorageHandler(storage);

    public StorageChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEST.get(), StorageKind.CHEST, pos, state);
        storage.setAdmission(this::admit);
    }

    public ItemStorage storage() {
        return storage;
    }

    @Override
    public KeyedStorage<ItemStack> keyed() {
        return storage;
    }

    public ItemStorageHandler handler() {
        return handler;
    }

    @Override
    protected String contentsKey() {
        return "items";
    }

    /**
     * Quanto do tipo cabe: só até o limite dele (o da entrada que casa, ou o limite por tipo do Baú),
     * contando só o mesmo item ou, se o filtro exige componentes, o mesmo item com os mesmos componentes.
     */
    private long admit(ItemStack key, long amount) {
        Filter filter = filter();
        long limit = filter.itemTypeLimit(key);
        if (limit <= 0) {
            return amount;
        }
        long present = filter.matchComponents() ? storage.count(key) : storage.countItem(key);
        return StockLimit.acceptable(present, limit, amount);
    }
}
