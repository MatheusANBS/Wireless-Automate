package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerCosts;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Categoria do Reparador de Cristais: um exemplo por tipo de cristal. O rachado e os Cristais de Sinal cheios da config
 * entram no bloco e, gastando o FE do tipo, o cristal sai cheio e com a integridade cheia. Os Afinados só dos tiers
 * carregados.
 */
final class CrystalRepairerCategory implements IRecipeCategory<CrystalRepairerCategory.Repair> {
    static final RecipeType<Repair> TYPE = RecipeType.create(WirelessAutomate.MODID, "crystal_repairer", Repair.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 84;
    private static final String KEY = "jei.wirelessautomate.crystal_repairer.";

    /** O cristal rachado, os Cristais de Sinal da alimentação (numa pilha só), o cristal reparado e o custo em FE. */
    record Repair(ItemStack cracked, ItemStack feed, ItemStack repaired) {
        static Repair of(Item crystal) {
            ItemStack cracked = new ItemStack(crystal);
            SignalCrystalItem.setCharge(cracked, 0);
            SignalCrystalItem.setIntegrity(cracked, 0);
            ItemStack feed = new ItemStack(ModItems.SIGNAL_CRYSTAL.get(), Math.max(1, Config.recastCrystals()));
            ItemStack repaired = new ItemStack(crystal);
            return new Repair(cracked, feed, repaired);
        }
    }

    private final IDrawable icon;
    private List<FormattedCharSequence> note = List.of();
    private String noteLanguage;
    private int noteCrystals = -1;
    private long noteCost = -1;
    private long noteTicks = -1;

    CrystalRepairerCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(ModItems.CRYSTAL_REPAIRER.get()));
    }

    static void registerCategory(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CrystalRepairerCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    static void registerRecipes(IRecipeRegistration registration) {
        List<Repair> repairs = new ArrayList<>();
        repairs.add(Repair.of(ModItems.SIGNAL_CRYSTAL.get()));
        repairs.add(Repair.of(ModItems.CUT_SIGNAL_CRYSTAL.get()));
        for (var tuned : ModItems.TUNED_CRYSTALS.entrySet()) {
            if (tuned.getKey().loaded()) {
                repairs.add(Repair.of(tuned.getValue().get()));
            }
        }
        registration.addRecipes(TYPE, repairs);
    }

    static void registerCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(TYPE, new ItemStack(ModItems.CRYSTAL_REPAIRER.get()));
    }

    @Override
    public RecipeType<Repair> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(KEY + "title");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Repair recipe, IFocusGroup focuses) {
        builder.addInputSlot(26, 6).addItemStack(recipe.cracked());
        builder.addInputSlot(62, 6).addItemStack(recipe.feed());
        builder.addOutputSlot(118, 6).addItemStack(recipe.repaired());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Repair recipe, IFocusGroup focuses) {
        builder.addRecipePlusSignWidget().setPosition(46, 9);
        builder.addRecipeArrowWidget().setPosition(86, 6);
    }

    @Override
    public void draw(Repair recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        String language = JeiText.language();
        int crystals = Config.recastCrystals();
        long ticks = Config.repairTicks();
        long cost = RepairerCosts.of(recipe.repaired());
        if (!Objects.equals(language, noteLanguage) || crystals != noteCrystals || cost != noteCost
                || ticks != noteTicks) {
            noteLanguage = language;
            noteCrystals = crystals;
            noteCost = cost;
            noteTicks = ticks;
            note = JeiText.split(Component.translatable(KEY + "note", crystals, TierCoreItem.grouped(cost),
                    JeiText.decimal(ticks / 20.0)), WIDTH - 8);
        }
        JeiText.drawCentered(graphics, note, WIDTH, 28);
    }
}
