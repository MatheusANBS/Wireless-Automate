package io.github.matheusanbs.wirelessautomate.recipe.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeDraft.ShapedLayout;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeDraftTest {
    private static final RecipeSlot AMETHYST = RecipeSlot.item("minecraft:amethyst_shard");
    private static final RecipeSlot STICK = RecipeSlot.item("minecraft:stick");
    private static final RecipeSlot X = RecipeSlot.item("minecraft:x");

    private static RecipeDraft grid(Map<Character, RecipeSlot> key, String... rows) {
        return RecipeDraft.fromShaped(List.of(rows), key, 1);
    }

    @Test
    void configuratorPatternRenumbersLettersByReadingOrder() {
        // ametista na coluna da direita, graveto na da esquerda: sem borda vazia
        RecipeDraft d = grid(Map.of('A', AMETHYST, 'S', STICK), "  A", " S ", "S  ");
        ShapedLayout l = d.layout();
        assertEquals(List.of("  A", " B ", "B  "), l.pattern());
        assertEquals(AMETHYST, l.key().get('A'));
        assertEquals(STICK, l.key().get('B'));
        assertEquals(2, l.key().size());
    }

    @Test
    void emptyBottomRowIsCut() {
        RecipeSlot p = RecipeSlot.item("minecraft:paper");
        RecipeSlot r = RecipeSlot.item("minecraft:redstone");
        RecipeSlot c = RecipeSlot.item("minecraft:chest");
        RecipeDraft d = grid(Map.of('P', p, 'R', r, 'C', c), "PRP", "PCP", "   ");
        assertEquals(List.of("ABA", "ACA"), d.layout().pattern());
    }

    @Test
    void emptyEdgeColumnsAreCut() {
        RecipeDraft d = grid(Map.of('X', X), " X ", " X ", " X ");
        assertEquals(List.of("A", "A", "A"), d.layout().pattern());
    }

    @Test
    void innerEmptyRowStays() {
        RecipeDraft d = grid(Map.of('X', X), "X  ", "   ", "X  ");
        assertEquals(List.of("A", " ", "A"), d.layout().pattern());
    }

    @Test
    void itemAndTagWithSameIdAreDifferentLetters() {
        RecipeDraft d = grid(Map.of('I', RecipeSlot.item("c:planks"), 'T', RecipeSlot.tag("c:planks")), "IT ");
        ShapedLayout l = d.layout();
        assertEquals(List.of("AB"), l.pattern());
        assertEquals(2, l.key().size());
    }

    @Test
    void shapelessIngredientsKeepGridOrder() {
        RecipeSlot a = RecipeSlot.item("a:a");
        RecipeSlot b = RecipeSlot.tag("b:b");
        RecipeSlot c = RecipeSlot.item("c:c");
        RecipeDraft d = RecipeDraft.fromShapeless(List.of(a), 1).withSlot(4, b).withSlot(8, c);
        assertEquals(List.of(a, b, c), d.ingredients());
    }

    @Test
    void emptyGrid() {
        RecipeDraft d = RecipeDraft.fromShapeless(List.of(), 1);
        assertTrue(d.isEmpty());
        assertThrows(IllegalStateException.class, d::layout);
        assertFalse(d.withSlot(4, X).isEmpty());
    }

    @Test
    void countIsClamped() {
        RecipeDraft d = RecipeDraft.fromShapeless(List.of(X), 5);
        assertEquals(1, d.withCount(0).count());
        assertEquals(64, d.withCount(99).count());
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeDraft(RecipeDraft.Shape.SHAPELESS, d.slots(), 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeDraft(RecipeDraft.Shape.SHAPELESS, d.slots(), 65, false));
    }

    @Test
    void fromShapedPlacesTopLeft() {
        RecipeDraft d = grid(Map.of('X', X), "XX", "XX");
        for (int i = 0; i < 9; i++) {
            boolean filled = i == 0 || i == 1 || i == 3 || i == 4;
            assertEquals(filled, !d.slots().get(i).isEmpty(), "slot " + i);
        }
        assertThrows(IllegalArgumentException.class, () -> grid(Map.of('X', X), "XXXX"));
        assertThrows(IllegalArgumentException.class, () -> grid(Map.of('X', X), "X", "X", "X", "X"));
    }

    @Test
    void roundTrip() {
        RecipeDraft d = RecipeDraft.fromShaped(List.of("AB", " A"), Map.of('A', STICK, 'B', X), 3);
        ShapedLayout l = d.layout();
        assertEquals(d, RecipeDraft.fromShaped(l.pattern(), l.key(), d.count()));
        assertNotEquals(d, d.withCount(4));
    }
}
