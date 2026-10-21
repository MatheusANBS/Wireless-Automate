package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Categoria Refazer o cristal: a receita {@code recast_crystal} da bancada é especial e o JEI não a mostra sozinha.
 * Um exemplo por tipo de cristal: o rachado e os Cristais de Sinal cheios da config viram o mesmo cristal, cheio e com a
 * integridade cheia.
 */
final class ResonatorRecastCategory implements IRecipeCategory<ResonatorRecastCategory.Recast> {
    static final RecipeType<Recast> TYPE = RecipeType.create(WirelessAutomate.MODID, "resonator_recast", Recast.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 74;
    private static final String KEY = "jei.wirelessautomate.resonator.recast.";

    /** O cristal rachado, os Cristais de Sinal que a bancada pede (numa pilha só) e o cristal refeito. */
    record Recast(ItemStack cracked, ItemStack signals, ItemStack full) {
        static Recast of(Item crystal) {
            ItemStack cracked = new ItemStack(crystal);
            SignalCrystalItem.setCharge(cracked, 0);
            SignalCrystalItem.setIntegrity(cracked, 0);
            ItemStack signals = new ItemStack(ModItems.SIGNAL_CRYSTAL.get(), Math.max(1, Config.recastCrystals()));
            return new Recast(cracked, signals, new ItemStack(crystal));
        }
    }

    private final IDrawable icon;
    private List<FormattedCharSequence> note = List.of();
    private String noteLanguage;
    private int noteCrystals = -1;

    ResonatorRecastCategory(IGuiHelper helper) {
        ItemStack cracked = new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(cracked, 0);
        SignalCrystalItem.setIntegrity(cracked, 0);
        this.icon = helper.createDrawableItemStack(cracked);
    }

    @Override
    public RecipeType<Recast> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, Recast recipe, IFocusGroup focuses) {
        builder.addInputSlot(26, 6).addItemStack(recipe.cracked());
        builder.addInputSlot(62, 6).addItemStack(recipe.signals());
        builder.addOutputSlot(118, 6).addItemStack(recipe.full());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recast recipe, IFocusGroup focuses) {
        builder.addRecipePlusSignWidget().setPosition(46, 9);
        builder.addRecipeArrowWidget().setPosition(86, 6);
    }

    @Override
    public void draw(Recast recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        String language = JeiText.language();
        int crystals = Config.recastCrystals();
        if (!Objects.equals(language, noteLanguage) || crystals != noteCrystals) {
            noteLanguage = language;
            noteCrystals = crystals;
            note = JeiText.split(Component.translatable(KEY + "note", crystals), WIDTH - 8);
        }
        JeiText.drawCentered(graphics, note, WIDTH, 28);
    }
}
