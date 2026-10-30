package io.github.matheusanbs.wirelessautomate.client.guide;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideInk.Craft;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideInk.Ink;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideInk.Item;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.SheetOp;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A receita na moldura do livro: achada no {@code RecipeManager} do cliente pelo id ({@code <Recipe>}) ou pelo item
 * de saída ({@code <RecipeFor>}), uma vez, quando a tinta da folha é preparada; desenhada como a grade 3 × 3 de
 * bancada (a com forma na posição do padrão, a sem forma em ordem), a seta de latão e o resultado num slot maior. Os
 * ingredientes de várias opções alternam a cada segundo pelo relógio: o quadro só escolhe o índice. Uma receita de
 * forno (o lingote de liga sai do alto-forno) mostra o ingrediente no meio da grade e a estação embaixo dele. Receita
 * que não existe (o editor de receitas a mudou ou tirou): a moldura diz "receita não encontrada".
 */
final class GuideRecipes {
    /** O slot da grade e o slot grande do resultado, em unidades de folha. */
    private static final int SLOT = 18;
    private static final int BIG = 26;
    /** Grade, espaço, seta, espaço e resultado, lado a lado. */
    private static final int ARROW_W = 20;
    private static final int GAP = 4;
    private static final int CONTENT_W = 3 * SLOT + GAP + ARROW_W + GAP + BIG;

    /** O fundo do slot (porcelana.2), a sombra em cima e à esquerda e a luz embaixo e à direita, como no mockup. */
    private static final int SLOT_FILL = 0xFFDDD3BF;
    private static final int SLOT_SHADOW = 0xFFBDB19A;
    private static final int SLOT_LIGHT = 0xFFFFFBF3;

    private static final Item[] NO_OPTIONS = new Item[0];

    private GuideRecipes() {
    }

    /** A tinta da moldura: a receita achada ou, se não há, a moldura com "receita não encontrada". */
    static Ink prepare(SheetOp.RecipeFrame r, @Nullable String current, Font font) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Recipe<?> recipe = null;
        if (level != null) {
            recipe = r.recipeId() != null ? byId(level.getRecipeManager(), r.recipeId())
                    : forItem(level.getRecipeManager(), level.registryAccess(), r.itemId());
        }
        if (recipe == null) {
            String id = r.itemId() != null ? r.itemId() : r.recipeId();
            WirelessAutomate.LOGGER.debug("Livro: receita não encontrada para {}", id);
            return GuideInk.frame(r, GuideInk.stack(id, ""),
                    Component.translatable("gui.wirelessautomate.guide.recipe_missing"), font);
        }
        return craft(r, recipe, level.registryAccess(), current);
    }

    /** A receita pelo id, se é de bancada ou de forno. */
    private static @Nullable Recipe<?> byId(RecipeManager recipes, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return null;
        }
        Recipe<?> recipe = recipes.byKey(rl).map(RecipeHolder::value).orElse(null);
        return recipe instanceof CraftingRecipe || recipe instanceof AbstractCookingRecipe ? recipe : null;
    }

    /**
     * A receita de bancada do mod cujo resultado é o item: a de id igual ao do item, senão a de menor id (a ordem do
     * {@code RecipeManager} não é fixa). Sem nenhuma, a de forno do mod, pela mesma regra.
     */
    private static @Nullable Recipe<?> forItem(RecipeManager recipes, RegistryAccess access, String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return null;
        }
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(rl);
        Recipe<?> found = pick(recipes.getAllRecipesFor(RecipeType.CRAFTING), access, item, rl);
        if (found == null) {
            found = pick(recipes.getAllRecipesFor(RecipeType.BLASTING), access, item, rl);
        }
        if (found == null) {
            found = pick(recipes.getAllRecipesFor(RecipeType.SMELTING), access, item, rl);
        }
        return found;
    }

    private static @Nullable Recipe<?> pick(List<? extends RecipeHolder<?>> holders, RegistryAccess access,
            net.minecraft.world.item.Item item, ResourceLocation itemId) {
        RecipeHolder<?> best = null;
        for (RecipeHolder<?> h : holders) {
            ResourceLocation id = h.id();
            if (!id.getNamespace().equals(WirelessAutomate.MODID) || !h.value().getResultItem(access).is(item)) {
                continue;
            }
            if (id.equals(itemId)) {
                return h.value();
            }
            if (best == null || id.compareTo(best.id()) < 0) {
                best = h;
            }
        }
        return best == null ? null : best.value();
    }

    /** Monta a grade, a seta e o resultado, centrados na moldura. */
    private static Craft craft(SheetOp.RecipeFrame r, Recipe<?> recipe, RegistryAccess access,
            @Nullable String current) {
        int gx = r.x() + Math.max(4, (r.w() - CONTENT_W) / 2);
        int gy = r.y() + (r.h() - 3 * SLOT) / 2;
        Item[][] cells = new Item[9][];
        int[] slotX;
        int[] slotY;
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof AbstractCookingRecipe) {
            // o ingrediente no meio e a estação (o forno) embaixo, sem slot, como o fogo da tela do forno
            slotX = new int[] {gx + SLOT};
            slotY = new int[] {gy + SLOT};
            cells[4] = options(ingredients.isEmpty() ? Ingredient.EMPTY : ingredients.get(0), gx + SLOT, gy + SLOT,
                    current);
            cells[7] = new Item[] {GuideInk.item(gx + SLOT + 1, gy + 2 * SLOT + 1, 16, recipe.getToastSymbol(),
                    current)};
        } else {
            slotX = new int[9];
            slotY = new int[9];
            int width = recipe instanceof ShapedRecipe s ? s.getWidth() : 3;
            for (int i = 0; i < 9; i++) {
                int col = i % 3;
                int row = i / 3;
                slotX[i] = gx + col * SLOT;
                slotY[i] = gy + row * SLOT;
                int index = recipe instanceof ShapedRecipe s
                        ? (col < s.getWidth() && row < s.getHeight() ? row * width + col : -1)
                        : i;
                if (index >= 0 && index < ingredients.size()) {
                    cells[i] = options(ingredients.get(index), slotX[i], slotY[i], current);
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            if (cells[i] == null) {
                cells[i] = NO_OPTIONS;
            }
        }
        int arrowX = gx + 3 * SLOT + GAP;
        int bigX = arrowX + ARROW_W + GAP;
        int bigY = r.y() + (r.h() - BIG) / 2;
        Item result = GuideInk.item(bigX + (BIG - 16) / 2, bigY + (BIG - 16) / 2, 16, recipe.getResultItem(access),
                current);
        return new Craft(r.x(), r.y(), r.w(), r.h(), slotX, slotY, cells, result, bigX, bigY, arrowX,
                r.y() + r.h() / 2);
    }

    /** As opções de um ingrediente, cada uma já como item na casa (os {@code getItems} do ingrediente, uma vez). */
    private static Item[] options(Ingredient ingredient, int slotX, int slotY, @Nullable String current) {
        ItemStack[] stacks = ingredient.getItems();
        Item[] out = new Item[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            out[i] = GuideInk.item(slotX + 1, slotY + 1, 16, stacks[i], current);
        }
        return out;
    }

    /** O segundo do relógio que escolhe a opção à vista de cada casa. */
    static long second() {
        return Util.getMillis() / 1000L;
    }

    /** A opção à vista da casa, ou {@code null} se a casa está vazia. */
    private static @Nullable Item shown(Item[] options, long second) {
        return options.length == 0 ? null : options[(int) (second % options.length)];
    }

    // ---- desenho (por quadro: só fills e itens já prontos)

    static void draw(GuiGraphics g, Font font, Craft c, long second) {
        for (int i = 0; i < c.slotX().length; i++) {
            slot(g, c.slotX()[i], c.slotY()[i], SLOT);
        }
        slot(g, c.bigX(), c.bigY(), BIG);
        arrow(g, c.arrowX(), c.arrowY());
        Item[][] cells = c.cells();
        for (int i = 0; i < cells.length; i++) {
            Item it = shown(cells[i], second);
            if (it != null) {
                GuidePainter.item(g, it.stack(), it.x(), it.y(), it.size());
            }
        }
        Item res = c.result();
        GuidePainter.item(g, res.stack(), res.x(), res.y(), res.size());
        if (res.stack().getCount() > 1) {
            g.renderItemDecorations(font, res.stack(), res.x(), res.y());
        }
    }

    /** Um slot de porcelana com chanfro: sombra em cima e à esquerda, luz embaixo e à direita. */
    private static void slot(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, SLOT_FILL);
        g.fill(x, y, x + size - 1, y + 1, SLOT_SHADOW);
        g.fill(x, y + 1, x + 1, y + size - 1, SLOT_SHADOW);
        g.fill(x + 1, y + size - 1, x + size, y + size, SLOT_LIGHT);
        g.fill(x + size - 1, y + 1, x + size, y + size - 1, SLOT_LIGHT);
    }

    /** A seta de latão: a haste e a ponta em degraus, com {@code y} no meio. */
    private static void arrow(GuiGraphics g, int x, int y) {
        int head = 7;
        g.fill(x + 2, y - 1, x + ARROW_W - head, y + 1, GuideInk.BRASS);
        for (int d = 0; d < head; d++) {
            int half = head - 1 - d;
            g.fill(x + ARROW_W - head + d, y - half - 1, x + ARROW_W - head + d + 1, y + half + 1, GuideInk.BRASS);
        }
    }

    /** O item à vista sob o ponto (unidades de folha), para a dica e o clique, ou {@code null}. */
    static @Nullable Item hit(Craft c, double ux, double uy) {
        long second = second();
        Item[][] cells = c.cells();
        for (int i = 0; i < cells.length; i++) {
            Item it = shown(cells[i], second);
            if (it != null && !it.stack().isEmpty() && GuidePainter.inside(ux, uy, it.x(), it.y(), 16, 16)) {
                return it;
            }
        }
        Item res = c.result();
        return !res.stack().isEmpty() && GuidePainter.inside(ux, uy, c.bigX(), c.bigY(), BIG, BIG) ? res : null;
    }
}
