package io.github.matheusanbs.wirelessautomate.recipe.edit;

/** Um slot da grade do editor: vazio, um item ou uma tag. Os ids são texto, sem classes do Minecraft. */
public record RecipeSlot(Kind kind, String id) {
    public enum Kind { EMPTY, ITEM, TAG }

    public static final RecipeSlot EMPTY = new RecipeSlot(Kind.EMPTY, "");

    public RecipeSlot {
        if (kind == null || id == null) {
            throw new IllegalArgumentException("kind e id são obrigatórios");
        }
    }

    public static RecipeSlot item(String id) {
        return new RecipeSlot(Kind.ITEM, id);
    }

    public static RecipeSlot tag(String id) {
        return new RecipeSlot(Kind.TAG, id);
    }

    public boolean isEmpty() {
        return kind == Kind.EMPTY;
    }
}
