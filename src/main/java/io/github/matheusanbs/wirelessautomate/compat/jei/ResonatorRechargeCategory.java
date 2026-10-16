package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Categoria Recarga no Núcleo: um cristal Opaco e um Pó de Redstone viram o cristal cheio, dentro de uma câmara
 * do Núcleo montado. O tempo vem da config.
 */
final class ResonatorRechargeCategory implements IRecipeCategory<ResonatorRechargeCategory.Recharge> {
    static final RecipeType<Recharge> TYPE =
            RecipeType.create(WirelessAutomate.MODID, "resonator_recharge", Recharge.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 62;
    private static final int TEXT_COLOR = 0xFF404040;

    /** O cristal Opaco e o cheio do mesmo tipo. */
    record Recharge(ItemStack dull, ItemStack full) {
        static Recharge of(Item crystal) {
            ItemStack dull = new ItemStack(crystal);
            SignalCrystalItem.setCharge(dull, 0);
            return new Recharge(dull, new ItemStack(crystal));
        }
    }

    private final IDrawable icon;

    ResonatorRechargeCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(
                ResonanceCoreItem.withTier(ModItems.RESONANCE_CORE.get(), RouterTier.BASIC));
    }

    @Override
    public RecipeType<Recharge> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.wirelessautomate.resonator.recharge.title");
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
    public void setRecipe(IRecipeLayoutBuilder builder, Recharge recipe, IFocusGroup focuses) {
        builder.addInputSlot(32, 6).addItemStack(recipe.dull());
        builder.addInputSlot(58, 6).addItemStack(new ItemStack(Items.REDSTONE));
        builder.addOutputSlot(122, 6).addItemStack(recipe.full());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recharge recipe, IFocusGroup focuses) {
        builder.addRecipePlusSignWidget().setPosition(45, 9);
        builder.addAnimatedRecipeArrowWidget(Math.max(20, (int) Config.rechargeTicks())).setPosition(86, 6);
    }

    @Override
    public void draw(Recharge recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        Component time = Component.translatable("jei.wirelessautomate.resonator.recharge.time", seconds());
        graphics.drawString(font, time, (WIDTH - font.width(time)) / 2, 26, TEXT_COLOR, false);
        int y = 38;
        for (var line : font.split(Component.translatable("jei.wirelessautomate.resonator.recharge.note"), WIDTH - 8)) {
            graphics.drawString(font, line, (WIDTH - font.width(line)) / 2, y, TEXT_COLOR, false);
            y += font.lineHeight + 1;
        }
    }

    /** Segundos da recarga; sem casas quando inteiro. */
    private static String seconds() {
        double value = Config.rechargeTicks() / 20.0;
        return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.1f", value);
    }
}
