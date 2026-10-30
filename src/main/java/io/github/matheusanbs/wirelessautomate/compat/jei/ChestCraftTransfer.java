package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.menu.ChestUpgradeSlots;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.packet.ChestCraftFillPayload;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestUpgrades;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * O "+" do JEI nas receitas de crafting, com o menu do Baú aberto: manda ao servidor as alternativas de cada slot da
 * grade ({@link ChestCraftFillPayload}) e o servidor monta a receita pelo Baú e pelo inventário. Sem a Mesa no Baú, o
 * JEI mostra o aviso. O Shift no "+" ({@code maxTransfer}) enche os slots.
 */
final class ChestCraftTransfer implements IRecipeTransferHandler<StorageListMenu<?>, RecipeHolder<CraftingRecipe>> {
    private final IRecipeTransferHandlerHelper helper;

    ChestCraftTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<? extends StorageListMenu<?>> getContainerClass() {
        return (Class) StorageListMenu.class;
    }

    @Override
    public Optional<MenuType<StorageListMenu<?>>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @SuppressWarnings("removal")
    @Override
    public @Nullable IRecipeTransferError transferRecipe(StorageListMenu<?> menu, RecipeHolder<CraftingRecipe> recipe,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        ChestUpgradeSlots upgrades = menu.upgradeSlots();
        if (upgrades == null || !upgrades.craftingActive()) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.wirelessautomate.chest_craft.needs_upgrade"));
        }
        if (doTransfer) {
            PacketDistributor.sendToServer(new ChestCraftFillPayload(menu.containerId, gridAlternatives(recipe, slots),
                    maxTransfer));
        }
        return null;
    }

    /**
     * As alternativas de cada slot da grade 3×3. O JEI costuma dar um slot de entrada por posição da grade (os vazios
     * sem ingrediente); com menos slots que isso, a receita com forma respeita a largura e a sem forma ocupa a grade
     * da esquerda para a direita.
     */
    static List<List<ItemStack>> gridAlternatives(RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots) {
        List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        int width = recipe.value() instanceof ShapedRecipe shaped ? shaped.getWidth() : 0;
        int size = ChestUpgrades.GRID_SIZE;
        List<List<ItemStack>> grid = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            grid.add(List.of());
        }
        boolean positioned = inputs.size() >= size;
        for (int i = 0; i < inputs.size(); i++) {
            int target = positioned || width <= 0 ? i : i / width * 3 + i % width;
            if (target < size) {
                grid.set(target, alternatives(inputs.get(i)));
            }
        }
        return grid;
    }

    private static List<ItemStack> alternatives(IRecipeSlotView view) {
        List<ItemStack> stacks = new ArrayList<>();
        view.getItemStacks().limit(ChestCraftFillPayload.MAX_ALTERNATIVES).forEach(stack -> {
            if (!stack.isEmpty()) {
                stacks.add(stack.copyWithCount(1));
            }
        });
        return stacks;
    }
}
