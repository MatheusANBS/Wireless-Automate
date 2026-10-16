package io.github.matheusanbs.wirelessautomate.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBill;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint.Layout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Categoria Estrutura do Ressonador: uma receita por montagem de exemplo (Vitrine e Faixa). À esquerda a vista 3D,
 * à direita os blocos como slots de entrada com a quantidade (U numa peça abre a categoria), embaixo os botões de
 * camada e a lembrança de que outros arranjos também montam. A vista de cada montagem vive aqui, num mapa, e guarda
 * o giro, o zoom e a camada enquanto o jogo roda.
 */
final class ResonatorStructureCategory implements IRecipeCategory<ResonatorStructureCategory.Structure> {
    static final RecipeType<Structure> TYPE =
            RecipeType.create(WirelessAutomate.MODID, "resonator_structure", Structure.class);

    private static final int WIDTH = 160;
    private static final int VIEW_Y = 11;
    private static final int VIEW_SIZE = 118;
    private static final int SLOTS_X = 123;
    private static final int SLOT_STEP = 19;
    private static final int BUTTONS_Y = 133;
    private static final int BUTTON_HEIGHT = 11;
    private static final int BUTTON_GAP = 2;
    private static final int NOTE_Y = 148;
    /** As linhas da nota que cabem na altura base; uma tradução mais longa aumenta a categoria. */
    private static final int NOTE_LINES = 3;
    private static final int NOTE_LINE_HEIGHT = 10;
    /** Os botões 1 a 4 e o Tudo; o Tudo é a camada {@code LAYERS}, a caixa inteira montada. */
    private static final int BUTTONS = StructureView.LAYERS;

    private static final int TEXT_COLOR = 0xFF404040;
    private static final int VIEW_FILL = 0xFF8B8B8B;
    private static final int EDGE_DARK = 0xFF373737;
    private static final int EDGE_LIGHT = 0xFFFFFFFF;
    private static final int BUTTON_FILL = 0xFFA0A0A0;
    private static final int BUTTON_SHADE = 0xFF555555;
    private static final int PRESSED_FILL = 0xFF6E6E6E;
    private static final int PRESSED_LIGHT = 0xFFAAAAAA;
    private static final int PRESSED_DARK = 0xFF444444;

    /** Uma montagem de exemplo e a sua lista de peças. */
    record Structure(Layout layout, ResonatorBill bill) {
        static Structure of(Layout layout) {
            return new Structure(layout, ResonatorBill.of(layout));
        }
    }

    private final IDrawable icon;
    private final Map<Layout, StructureView> views = new EnumMap<>(Layout.class);
    /** Os textos e as medidas no idioma de agora; refeitos só quando o idioma muda. */
    private Texts texts;

    ResonatorStructureCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(ModItems.RESONATOR_CASING.get()));
    }

    @Override
    public RecipeType<Structure> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.wirelessautomate.resonator.structure.title");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return NOTE_Y + Math.max(NOTE_LINES, texts().note().size()) * NOTE_LINE_HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Structure recipe, IFocusGroup focuses) {
        ResonatorBill bill = recipe.bill();
        slot(builder, 0, stack(ModItems.RESONATOR_CASING.get(), bill.count(Part.CASING)));
        slot(builder, 1, stack(ModItems.RESONATOR_GLASS.get(), bill.count(Part.GLASS)));
        slot(builder, 2, ResonanceCoreItem.withTier(ModItems.RESONANCE_CORE.get(), RouterTier.EMERALD));
        slot(builder, 3, stack(ModItems.RESONATOR_ENERGY_PORT.get(), bill.count(Part.ENERGY_PORT)));
        slot(builder, 4, stack(ModItems.RESONATOR_CRYSTAL_PORT.get(), bill.count(Part.CRYSTAL_PORT)));
        slot(builder, 5, stack(ModItems.RESONATOR_TUNER.get(), bill.count(Part.TUNER)))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei.wirelessautomate.resonator.structure.optional")));
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    /** Os slots em duas colunas, na ordem Carcaça, Vidro, Núcleo, Porta de Energia, Porta de Cristais, Sintonizador. */
    private static IRecipeSlotBuilder slot(IRecipeLayoutBuilder builder, int index, ItemStack stack) {
        return builder.addInputSlot(SLOTS_X + (index % 2) * SLOT_STEP, VIEW_Y + (index / 2) * SLOT_STEP)
                .setStandardSlotBackground()
                .addItemStack(stack);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Structure recipe, IFocusGroup focuses) {
        StructureView view = view(recipe);
        builder.addInputHandler(new ViewInput(view));
        builder.addInputHandler(new LayerInput(view, this::texts));
    }

    private StructureView view(Structure recipe) {
        return views.computeIfAbsent(recipe.layout(), layout -> new StructureView(recipe.bill()));
    }

    @Override
    public void draw(Structure recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        Texts texts = texts();
        Component name = texts.names().get(recipe.layout());
        graphics.drawString(font, name, (VIEW_SIZE - font.width(name)) / 2, 1, TEXT_COLOR, false);

        graphics.fill(0, VIEW_Y, VIEW_SIZE, VIEW_Y + VIEW_SIZE, EDGE_DARK);
        graphics.fill(1, VIEW_Y + 1, VIEW_SIZE, VIEW_Y + VIEW_SIZE, EDGE_LIGHT);
        graphics.fill(1, VIEW_Y + 1, VIEW_SIZE - 1, VIEW_Y + VIEW_SIZE - 1, VIEW_FILL);
        StructureView view = view(recipe);
        view.draw(graphics, 1, VIEW_Y + 1, VIEW_SIZE - 2, VIEW_SIZE - 2);

        drawButtons(graphics, font, texts, view.layer());
        int y = NOTE_Y;
        for (FormattedCharSequence line : texts.note()) {
            graphics.drawString(font, line, 0, y, TEXT_COLOR, false);
            y += NOTE_LINE_HEIGHT;
        }
    }

    private Texts texts() {
        Minecraft minecraft = Minecraft.getInstance();
        String language = minecraft.getLanguageManager().getSelected();
        if (texts == null || !texts.language().equals(language)) {
            texts = Texts.of(language, minecraft.font);
        }
        return texts;
    }

    /** "Camada" e os botões 1 a 4 e Tudo; o da camada mostrada, afundado. */
    private static void drawButtons(GuiGraphics graphics, Font font, Texts texts, int layer) {
        graphics.drawString(font, texts.label(), 0, BUTTONS_Y + 2, TEXT_COLOR, false);
        for (int i = 0; i < BUTTONS; i++) {
            int left = texts.lefts()[i];
            int right = left + texts.widths()[i];
            int bottom = BUTTONS_Y + BUTTON_HEIGHT;
            boolean pressed = i + 1 == layer;
            graphics.fill(left, BUTTONS_Y, right, bottom, pressed ? PRESSED_LIGHT : BUTTON_SHADE);
            graphics.fill(left, BUTTONS_Y, right - 1, bottom - 1, pressed ? PRESSED_DARK : EDGE_LIGHT);
            graphics.fill(left + 1, BUTTONS_Y + 1, right - 1, bottom - 1, pressed ? PRESSED_FILL : BUTTON_FILL);
            graphics.drawString(font, texts.buttons()[i], texts.textXs()[i], BUTTONS_Y + 2,
                    pressed ? 0xFFFFFFFF : TEXT_COLOR, false);
        }
    }

    /**
     * Os textos da categoria num idioma, com as medidas dos botões e a nota já quebrada na largura: montados uma vez
     * por idioma, para o desenho não traduzir nem medir a cada quadro.
     */
    private record Texts(String language, Component label, Component[] buttons, int[] lefts, int[] widths,
            int[] textXs, Map<Layout, Component> names, List<FormattedCharSequence> note) {
        static Texts of(String language, Font font) {
            Component label = resolved("jei.wirelessautomate.resonator.structure.layer");
            Component[] buttons = new Component[BUTTONS];
            int[] lefts = new int[BUTTONS];
            int[] widths = new int[BUTTONS];
            int[] textXs = new int[BUTTONS];
            int left = font.width(label) + 4;
            for (int i = 0; i < BUTTONS; i++) {
                buttons[i] = i < BUTTONS - 1
                        ? Component.literal(Integer.toString(i + 1))
                        : resolved("jei.wirelessautomate.resonator.structure.all");
                int textWidth = font.width(buttons[i]);
                lefts[i] = left;
                widths[i] = Math.max(BUTTON_HEIGHT, textWidth + 6);
                textXs[i] = left + (widths[i] - textWidth + 1) / 2;
                left += widths[i] + BUTTON_GAP;
            }
            Map<Layout, Component> names = new EnumMap<>(Layout.class);
            for (Layout layout : Layout.values()) {
                names.put(layout, resolved("block.wirelessautomate.resonance_core.projection.layout."
                        + layout.name().toLowerCase(Locale.ROOT)));
            }
            List<FormattedCharSequence> note = List.copyOf(font.split(
                    Component.translatable("jei.wirelessautomate.resonator.structure.note"), WIDTH));
            return new Texts(language, label, buttons, lefts, widths, textXs, names, note);
        }

        /** O texto já traduzido, para o desenho não procurar a chave a cada quadro. */
        private static Component resolved(String key) {
            return Component.literal(Component.translatable(key).getString());
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, Structure recipe, IRecipeSlotsView slots, double mouseX,
            double mouseY) {
        if (mouseX >= 0 && mouseX < VIEW_SIZE && mouseY >= VIEW_Y && mouseY < VIEW_Y + VIEW_SIZE) {
            tooltip.add(Component.translatable("jei.wirelessautomate.resonator.structure.hint"));
        }
    }

    private static boolean isMouse(IJeiUserInput input) {
        return input.getKey().getType() == InputConstants.Type.MOUSE;
    }

    /** Arrastar gira e a roda dá zoom na vista; o clique na vista fica com ela, para o JEI não fazer nada. */
    private record ViewInput(StructureView view) implements IJeiInputHandler {
        @Override
        public ScreenRectangle getArea() {
            return new ScreenRectangle(0, VIEW_Y, VIEW_SIZE, VIEW_SIZE);
        }

        @Override
        public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
            return isMouse(input);
        }

        @Override
        public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key key, double dragX,
                double dragY) {
            view.drag(dragX, dragY);
            return true;
        }

        @Override
        public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            view.scroll(scrollY);
            return true;
        }
    }

    /** Os botões de camada; o JEI simula o clique ao apertar e o executa ao soltar. */
    private record LayerInput(StructureView view, Supplier<Texts> texts) implements IJeiInputHandler {
        @Override
        public ScreenRectangle getArea() {
            return new ScreenRectangle(0, BUTTONS_Y, WIDTH, BUTTON_HEIGHT);
        }

        @Override
        public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
            if (!isMouse(input)) {
                return false;
            }
            Texts current = texts.get();
            for (int i = 0; i < BUTTONS; i++) {
                int left = current.lefts()[i];
                if (mouseX >= left && mouseX < left + current.widths()[i]) {
                    if (!input.isSimulate()) {
                        view.setLayer(i + 1);
                    }
                    return true;
                }
            }
            return false;
        }
    }
}
