package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Baú: itens por tipo ({@link ItemStorage}), vistos pelos outros mods como um inventário comum
 * ({@link ItemStorageHandler}) e pelo roteador como {@link BulkItems}.
 */
public class StorageChestBlockEntity extends KeyedStorageBlockEntity<ItemStack> {
    private final ItemStorage storage = new ItemStorage(this::markContentsChanged, this::capacity);
    private final ItemStorageHandler handler = new ItemStorageHandler(storage);
    private final ChestUpgrades upgrades = new ChestUpgrades(this::setChanged);

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

    /** Os dois slots de upgrade e a grade da Mesa. */
    public ChestUpgrades upgrades() {
        return upgrades;
    }

    @Override
    protected String contentsKey() {
        return "items";
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        upgrades.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        upgrades.load(tag, registries);
    }

    /**
     * O drop leva também os upgrades e a grade quando há algum. Antes o Baú vazio e sem filtro não levava
     * componente nenhum e empilhava; com upgrades ou grade ele passa a levar os componentes (e deixa de empilhar). Os
     * upgrades vão no próprio componente do item, não no {@link StorageSavedData}.
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        upgrades.collect(components);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        upgrades.apply(input.getOrDefault(ModDataComponents.STORAGE_UPGRADES.get(), ItemContainerContents.EMPTY),
                input.getOrDefault(ModDataComponents.STORAGE_CRAFT_GRID.get(), ItemContainerContents.EMPTY));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("upgrades");
        tag.remove("craft_grid");
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
