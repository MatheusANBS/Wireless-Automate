package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

/**
 * O que o Baú guarda além do conteúdo: os dois slots de upgrade (um de cada tipo) e a grade 3×3 da Mesa de
 * Trabalho, 11 pilhas ao todo. Só guarda, salva e leva no drop; quem age sobre os upgrades são a Compactação e a
 * Mesa. NBT {@code upgrades} e {@code craft_grid}, só gravados quando não vazios.
 */
public final class ChestUpgrades {
    public static final int SLOTS = 2;
    public static final int GRID_SIZE = 9;

    private static final String UPGRADES_KEY = "upgrades";
    private static final String GRID_KEY = "craft_grid";

    private final NonNullList<ItemStack> upgrades = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> grid = NonNullList.withSize(GRID_SIZE, ItemStack.EMPTY);
    private final Runnable onChange;
    /** Os menus abertos neste Baú, avisados de toda mudança (só servidor); criada no primeiro menu. */
    private @Nullable List<Runnable> listeners;

    /**
     * {@code onChange} é o {@code setChanged} do Baú: chamado quando um upgrade é trocado ou em {@link #changed()},
     * antes dos menus abertos.
     */
    public ChestUpgrades(Runnable onChange) {
        this.onChange = onChange;
    }

    public ItemStack upgrade(int slot) {
        return upgrades.get(slot);
    }

    public void setUpgrade(int slot, ItemStack stack) {
        upgrades.set(slot, stack);
        fire();
    }

    /** Só um upgrade do Baú, e de um tipo que ainda não está no outro slot. */
    public boolean accepts(int slot, ItemStack stack) {
        return fits(stack, upgrades.get(SLOTS - 1 - slot));
    }

    /** Se {@code stack} cabe num slot de upgrade quando o outro slot tem {@code beside}; vale também para o cliente. */
    public static boolean fits(ItemStack stack, ItemStack beside) {
        if (!(stack.getItem() instanceof ChestUpgradeItem item)) {
            return false;
        }
        return !(beside.getItem() instanceof ChestUpgradeItem other) || other.kind() != item.kind();
    }

    public boolean has(ChestUpgradeItem.Kind kind) {
        return find(kind) != null;
    }

    /** A pilha do upgrade do tipo, ou {@code null} se nenhum slot o tem. */
    public @Nullable ItemStack find(ChestUpgradeItem.Kind kind) {
        for (ItemStack stack : upgrades) {
            if (stack.getItem() instanceof ChestUpgradeItem item && item.kind() == kind) {
                return stack;
            }
        }
        return null;
    }

    /** A grade 3×3 da Mesa; quem a muda chama {@link #changed()}. */
    public NonNullList<ItemStack> grid() {
        return grid;
    }

    /** Marca o Baú para salvar depois de uma mudança direta na grade e avisa os menus abertos. */
    public void changed() {
        fire();
    }

    /** Um menu aberto neste Baú passa a ser avisado das mudanças dos upgrades e da grade; tirado no fechamento. */
    public void listen(Runnable listener) {
        if (listeners == null) {
            listeners = new ArrayList<>(2);
        }
        listeners.add(listener);
    }

    public void unlisten(Runnable listener) {
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    private void fire() {
        onChange.run();
        if (listeners != null) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).run();
            }
        }
    }

    public boolean gridEmpty() {
        return allEmpty(grid);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        saveList(tag, UPGRADES_KEY, upgrades, registries);
        saveList(tag, GRID_KEY, grid, registries);
    }

    private static void saveList(CompoundTag tag, String key, NonNullList<ItemStack> stacks, HolderLookup.Provider registries) {
        if (!allEmpty(stacks)) {
            tag.put(key, ContainerHelper.saveAllItems(new CompoundTag(), stacks, registries));
        }
    }

    /** Lê sem avisar. */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        loadList(tag, UPGRADES_KEY, upgrades, registries);
        loadList(tag, GRID_KEY, grid, registries);
    }

    private static void loadList(CompoundTag tag, String key, NonNullList<ItemStack> stacks, HolderLookup.Provider registries) {
        stacks.replaceAll(stack -> ItemStack.EMPTY);
        if (tag.contains(key, Tag.TAG_COMPOUND)) {
            ContainerHelper.loadAllItems(tag.getCompound(key), stacks, registries);
        }
    }

    /** Os componentes do drop, só dos que não estão vazios. */
    public void collect(DataComponentMap.Builder components) {
        if (!allEmpty(upgrades)) {
            components.set(ModDataComponents.STORAGE_UPGRADES.get(), ItemContainerContents.fromItems(upgrades));
        }
        if (!allEmpty(grid)) {
            components.set(ModDataComponents.STORAGE_CRAFT_GRID.get(), ItemContainerContents.fromItems(grid));
        }
    }

    /**
     * O Baú foi colocado a partir de um item: lê os componentes do drop, sem avisar. Recebe o conteúdo já tirado do
     * componente porque o {@code DataComponentInput} do {@link net.minecraft.world.level.block.entity.BlockEntity} é protegido.
     */
    public void apply(ItemContainerContents savedUpgrades, ItemContainerContents savedGrid) {
        savedUpgrades.copyInto(upgrades);
        savedGrid.copyInto(grid);
    }

    private static boolean allEmpty(NonNullList<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
