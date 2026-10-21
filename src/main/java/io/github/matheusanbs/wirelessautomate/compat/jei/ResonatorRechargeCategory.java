package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CoreCatalyst;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Categoria Recarga no Núcleo: um cristal Opaco e um catalisador viram o cristal cheio, com 1 de integridade a menos,
 * dentro de uma câmara do Núcleo montado. Uma receita por tipo de cristal; o catalisador e o Núcleo giram juntos pelos
 * pares que a regra do tier aceita (redstone ou Pó no Básico e no Avançado, só o Pó no Elite e na Esmeralda, o Pó
 * Carregado do tier do Allthemodium para cima), e o texto diz o catalisador do Núcleo à vista. O tempo vem da config.
 */
final class ResonatorRechargeCategory implements IRecipeCategory<ResonatorRechargeCategory.Recharge> {
    static final RecipeType<Recharge> TYPE =
            RecipeType.create(WirelessAutomate.MODID, "resonator_recharge", Recharge.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 92;
    private static final int TEXT_Y = 28;
    private static final String CORE_SLOT = "core";
    private static final String KEY = "jei.wirelessautomate.resonator.recharge.";

    /**
     * O cristal Opaco, o cheio depois da recarga e os pares Núcleo × catalisador aceitos, em listas do mesmo tamanho:
     * o JEI gira todas as listas de uma receita pelo mesmo índice, então o Núcleo e o catalisador à vista são um par.
     */
    record Recharge(ItemStack dull, ItemStack full, List<ItemStack> cores, List<ItemStack> catalysts) {
        static Recharge of(Item crystal, List<ItemStack> cores, List<ItemStack> catalysts) {
            ItemStack dull = new ItemStack(crystal);
            SignalCrystalItem.setCharge(dull, 0);
            ItemStack full = new ItemStack(crystal);
            SignalCrystalItem.setIntegrity(full, Config.crystalIntegrity() - 1);
            return new Recharge(dull, full, cores, catalysts);
        }
    }

    private final IDrawable icon;
    /** O texto do catalisador por tier, guardado até o idioma mudar. */
    private final Map<RouterTier, List<FormattedCharSequence>> catalystLines = new EnumMap<>(RouterTier.class);
    private List<FormattedCharSequence> footer = List.of();
    private String textLanguage;
    private long textTicks = -1;

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

    /**
     * Com o foco num catalisador ou num Núcleo, só os pares dele, para o JEI (que mostra só o ingrediente do foco
     * naquele slot) não desemparelhar as duas listas.
     */
    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recharge recipe, IFocusGroup focuses) {
        List<ItemStack> focused = focuses.getItemStackFocuses().map(f -> f.getTypedValue().getIngredient()).toList();
        List<ItemStack> cores = new ArrayList<>();
        List<ItemStack> catalysts = new ArrayList<>();
        for (int i = 0; i < recipe.cores().size(); i++) {
            if (matches(focused, recipe.cores().get(i), recipe.catalysts().get(i))) {
                cores.add(recipe.cores().get(i));
                catalysts.add(recipe.catalysts().get(i));
            }
        }
        if (cores.isEmpty()) {
            cores.addAll(recipe.cores());
            catalysts.addAll(recipe.catalysts());
        }
        builder.addInputSlot(16, 6).addItemStack(recipe.dull());
        builder.addInputSlot(52, 6).addItemStacks(catalysts);
        builder.addOutputSlot(106, 6).addItemStack(recipe.full());
        builder.addSlot(RecipeIngredientRole.CATALYST, 150, 6).setSlotName(CORE_SLOT).addItemStacks(cores);
    }

    /** Se o par serve ao foco: sem foco de catalisador nem de Núcleo, todos servem. */
    private static boolean matches(List<ItemStack> focused, ItemStack core, ItemStack catalyst) {
        boolean anyPairFocus = false;
        for (ItemStack stack : focused) {
            boolean isCore = stack.getItem() instanceof ResonanceCoreItem;
            if (!isCore && !CoreCatalyst.isCatalyst(stack.getItem())) {
                continue;
            }
            anyPairFocus = true;
            if (isCore ? ResonanceCoreItem.tierOf(stack) == ResonanceCoreItem.tierOf(core) : stack.is(catalyst.getItem())) {
                return true;
            }
        }
        return !anyPairFocus;
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recharge recipe, IFocusGroup focuses) {
        builder.addRecipePlusSignWidget().setPosition(36, 9);
        builder.addAnimatedRecipeArrowWidget((int) Math.min(Integer.MAX_VALUE, Math.max(20L, Config.rechargeTicks())))
                .setPosition(76, 6);
    }

    @Override
    public void draw(Recharge recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        refreshText();
        RouterTier tier = slots.findSlotByName(CORE_SLOT).flatMap(slot -> slot.getDisplayedItemStack())
                .map(ResonanceCoreItem::tierOf).orElse(RouterTier.BASIC);
        List<FormattedCharSequence> catalyst = catalystLines.computeIfAbsent(tier, t -> JeiText.split(
                Component.translatable(KEY + "catalyst", Component.translatable(t.translationKey()),
                        catalystNames(recipe, t)), WIDTH - 8));
        int y = JeiText.drawCentered(graphics, catalyst, WIDTH, TEXT_Y);
        JeiText.drawCentered(graphics, footer, WIDTH, y + 4);
    }

    /** Refaz o texto guardado quando o idioma ou o tempo da recarga mudam. */
    private void refreshText() {
        String language = JeiText.language();
        long ticks = Config.rechargeTicks();
        if (Objects.equals(language, textLanguage) && ticks == textTicks) {
            return;
        }
        textLanguage = language;
        textTicks = ticks;
        catalystLines.clear();
        List<FormattedCharSequence> lines = new ArrayList<>(JeiText.split(Component.translatable(KEY + "time",
                JeiText.decimal(ticks / 20.0)), WIDTH - 8));
        lines.addAll(JeiText.split(Component.translatable(KEY + "note"), WIDTH - 8));
        footer = List.copyOf(lines);
    }

    /** Os catalisadores que o Núcleo do tier aceita, pelos pares da receita, como "A ou B". */
    private static Component catalystNames(Recharge recipe, RouterTier tier) {
        Component names = null;
        for (int i = 0; i < recipe.cores().size(); i++) {
            if (ResonanceCoreItem.tierOf(recipe.cores().get(i)) != tier) {
                continue;
            }
            Component name = recipe.catalysts().get(i).getHoverName();
            names = names == null ? name : Component.translatable(KEY + "or", names, name);
        }
        return names == null ? Component.empty() : names;
    }
}
