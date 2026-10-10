package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

/**
 * Baú: itens por tipo ({@link ItemStorage}), vistos pelos outros mods como um inventário comum
 * ({@link ItemStorageHandler}) e pelo roteador como {@link BulkItems}.
 */
public class StorageChestBlockEntity extends KeyedStorageBlockEntity<ItemStack> {
    private final ItemStorage storage = new ItemStorage(this::setChanged, this::capacity);
    private final ItemStorageHandler handler = new ItemStorageHandler(storage);
    private final FilterSet.ItemRule rule = new FilterSet.ItemRule();

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

    /** A de itens do Forge (para todo mundo) e a {@link BulkItems} (para o roteador). */
    @Override
    protected @Nullable Object exposed(Capability<?> capability) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return handler;
        }
        return capability == BulkItems.BLOCK ? storage : null;
    }

    @Override
    protected String contentsKey() {
        return "items";
    }

    /**
     * Quanto do tipo o filtro deixa entrar: nada se ele recusa; com estoque na entrada, só até o
     * Baú ter N do item (com componentes iguais se a entrada exigir).
     */
    private long admit(ItemStack key, long amount) {
        FilterSet set = filter().asSet();
        if (set.isEmpty()) {
            return amount;
        }
        if (!set.evaluateItem(key, rule)) {
            return 0;
        }
        if (rule.stock > 0) {
            long present = rule.matchComponents ? storage.count(key) : storage.countItem(key);
            return StockLimit.acceptable(present, rule.stock, amount);
        }
        return amount;
    }
}
