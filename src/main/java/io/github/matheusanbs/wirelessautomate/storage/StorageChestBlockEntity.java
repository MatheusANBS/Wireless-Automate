package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestCompacting;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestUpgrades;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Baú: itens por tipo ({@link ItemStorage}), vistos pelos outros mods como um inventário comum
 * ({@link ItemStorageHandler}) e pelo roteador como {@link BulkItems}. Com o Upgrade de Compactação, o
 * {@link ChestCompacting} converte as cadeias sujas no fim do tick.
 */
public class StorageChestBlockEntity extends KeyedStorageBlockEntity<ItemStack> {
    private final ItemStorage storage = new ItemStorage(this::markContentsChanged, this::capacity);
    private final ItemStorageHandler handler = new ItemStorageHandler(storage);
    private final ChestUpgrades upgrades = new ChestUpgrades(this::upgradesChanged);
    private final ChestCompacting compacting = new ChestCompacting(storage, this::capacity, this::markContentsChanged);

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

    /** A Compactação do Baú; sem o upgrade, as regras são nulas e ela não faz nada. */
    public ChestCompacting compacting() {
        return compacting;
    }

    /**
     * Um upgrade foi trocado ou a grade mudou: salva e, se as regras da Compactação mudaram de verdade (o upgrade
     * entrou, saiu ou foi reconfigurado), aplica as novas e põe todas as cadeias presentes na fila do fim do tick.
     * Sem a Mesa, a grade que sobrou volta para o Baú.
     */
    private void upgradesChanged() {
        setChanged();
        CompactingRules next = slotRules();
        if (!Objects.equals(next, compacting.rules())) {
            compacting.setRules(next);
            if (next != null) {
                compacting.markAll();
                markContentsChanged();
            }
        }
        if (level != null && !level.isClientSide && !upgrades.has(ChestUpgradeItem.Kind.CRAFTING) && !upgrades.gridEmpty()) {
            returnGrid(null);
        }
    }

    /**
     * Devolve a grade da Mesa: cada pilha entra no Baú como uma inserção comum (com o filtro); o que não coube vai
     * para o inventário do jogador e depois para o chão. Sem jogador (a Mesa saiu do slot), o resto cai em cima do
     * Baú. Cada slot é esvaziado antes da inserção, e o aviso sai uma vez no fim (ele volta a este método, que já
     * acha a grade vazia).
     *
     * @return se havia algo na grade
     */
    public boolean returnGrid(@Nullable Player player) {
        NonNullList<ItemStack> grid = upgrades.grid();
        boolean any = false;
        for (int i = 0; i < grid.size(); i++) {
            ItemStack stack = grid.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            any = true;
            grid.set(i, ItemStack.EMPTY);
            long accepted = storage.insert(stack, stack.getCount(), false);
            stack.shrink((int) accepted);
            if (stack.isEmpty()) {
                continue;
            }
            if (player != null) {
                // Slot a slot, sem o Inventory.add: no criativo ele apaga o que não cabe.
                StorageListMenu.addToInventory(player.getInventory(), stack);
                if (!stack.isEmpty()) {
                    player.drop(stack, false);
                }
            } else if (level != null) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                        worldPosition.getZ() + 0.5, stack);
            }
        }
        if (any) {
            upgrades.changed();
        }
        return any;
    }

    /** As regras do upgrade de Compactação dos slots, ou {@code null} sem ele. */
    private @Nullable CompactingRules slotRules() {
        ItemStack stack = upgrades.find(ChestUpgradeItem.Kind.COMPACTING);
        return stack == null ? null : stack.getOrDefault(ModDataComponents.COMPACTING_RULES.get(), CompactingRules.DEFAULT);
    }

    /**
     * Converte as cadeias sujas antes de fechar os buracos da lista. A conversão muda o conteúdo e põe o Baú de novo
     * na fila do {@link StorageNeighbors} (a do tick seguinte, porque a lista foi trocada): um aviso a mais aos
     * vizinhos, uma vez, sem conversão nova (a conversão não suja cadeia).
     */
    @Override
    protected void compactContents() {
        compacting.convertDirty();
        super.compactContents();
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
        // O conteúdo salvo já estava convertido: só as regras, sem sujar nada.
        compacting.setRules(slotRules());
    }

    /**
     * O drop leva também os upgrades e a grade quando há algum. O Baú vazio, sem filtro e sem upgrades não leva
     * componente nenhum e empilha; com upgrades ou grade leva os componentes e não empilha. Os upgrades vão no
     * próprio componente do item, não no {@link StorageSavedData}.
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
        compacting.setRules(slotRules());
    }

    /** O Baú sem conteúdo mas com upgrades ou itens na grade também vira item na quebra: eles só existem nele. */
    @Override
    public boolean keepsOnBreak() {
        return super.keepsOnBreak() || !upgrades.isEmpty();
    }

    /**
     * Além do conteúdo, os upgrades e a grade: se o bloco sai do mundo sem que um item do Baú os leve
     * ({@code /setblock}, a remoção de outro mod sem loot), eles caem no chão como itens, como o baú comum solta os
     * dele. Com o item feito (a quebra pelo jogador, a loot table), ficam só no item.
     */
    @Override
    void stash() {
        super.stash();
        if (dropExpected() || upgrades.isEmpty() || level == null || level.isClientSide) {
            return;
        }
        for (int i = 0; i < ChestUpgrades.SLOTS; i++) {
            dropOnTop(upgrades.upgrade(i));
        }
        for (ItemStack stack : upgrades.grid()) {
            dropOnTop(stack);
        }
    }

    private void dropOnTop(ItemStack stack) {
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, stack.copy());
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("upgrades");
        tag.remove("craft_grid");
    }

    /**
     * Quanto do tipo cabe: só até o limite dele (o da entrada que casa, ou o limite por tipo do Baú),
     * contando só o mesmo item ou, se o filtro exige componentes, o mesmo item com os mesmos componentes; com a
     * Compactação, a cadeia inteira na unidade do item ({@link ItemStorage#stockCount}).
     */
    private long admit(ItemStack key, long amount) {
        Filter filter = filter();
        long limit = filter.itemTypeLimit(key);
        if (limit <= 0) {
            return amount;
        }
        long present = storage.stockCount(key, filter.matchComponents());
        return StockLimit.acceptable(present, limit, amount);
    }
}
