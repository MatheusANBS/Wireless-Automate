package io.github.matheusanbs.wirelessautomate.recipe.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rascunho imutável de uma receita de bancada 3x3, sem classes do Minecraft. */
public record RecipeDraft(Shape shape, List<RecipeSlot> slots, int count, boolean disabled) {
    public enum Shape { SHAPED, SHAPELESS }

    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 64;
    public static final int SIZE = 3;
    public static final int SLOTS = SIZE * SIZE;

    /** Padrão com bordas cortadas e a chave de letras. */
    public record ShapedLayout(List<String> pattern, LinkedHashMap<Character, RecipeSlot> key) {
    }

    public RecipeDraft {
        if (shape == null || slots == null || slots.size() != SLOTS) {
            throw new IllegalArgumentException("o rascunho exige forma e 9 slots");
        }
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new IllegalArgumentException("quantidade fora de " + MIN_COUNT + ".." + MAX_COUNT + ": " + count);
        }
        slots = List.copyOf(slots);
    }

    public RecipeDraft withSlot(int i, RecipeSlot s) {
        List<RecipeSlot> copy = new ArrayList<>(slots);
        copy.set(i, s);
        return new RecipeDraft(shape, copy, count, disabled);
    }

    public RecipeDraft withCount(int newCount) {
        return new RecipeDraft(shape, slots, Math.max(MIN_COUNT, Math.min(MAX_COUNT, newCount)), disabled);
    }

    public RecipeDraft withDisabled(boolean newDisabled) {
        return new RecipeDraft(shape, slots, count, newDisabled);
    }

    public boolean isEmpty() {
        return slots.stream().allMatch(RecipeSlot::isEmpty);
    }

    public ShapedLayout layout() {
        if (isEmpty()) {
            throw new IllegalStateException("grade vazia");
        }
        int minRow = SIZE, maxRow = -1, minCol = SIZE, maxCol = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (!slots.get(i).isEmpty()) {
                int r = i / SIZE, c = i % SIZE;
                minRow = Math.min(minRow, r);
                maxRow = Math.max(maxRow, r);
                minCol = Math.min(minCol, c);
                maxCol = Math.max(maxCol, c);
            }
        }
        LinkedHashMap<Character, RecipeSlot> key = new LinkedHashMap<>();
        Map<RecipeSlot, Character> letters = new LinkedHashMap<>();
        List<String> pattern = new ArrayList<>();
        char next = 'A';
        for (int r = minRow; r <= maxRow; r++) {
            StringBuilder line = new StringBuilder();
            for (int c = minCol; c <= maxCol; c++) {
                RecipeSlot s = slots.get(r * SIZE + c);
                if (s.isEmpty()) {
                    line.append(' ');
                } else {
                    Character letter = letters.get(s);
                    if (letter == null) {
                        letter = next++;
                        letters.put(s, letter);
                        key.put(letter, s);
                    }
                    line.append(letter);
                }
            }
            pattern.add(line.toString());
        }
        return new ShapedLayout(List.copyOf(pattern), key);
    }

    public List<RecipeSlot> ingredients() {
        return slots.stream().filter(s -> !s.isEmpty()).toList();
    }

    public static RecipeDraft fromShaped(List<String> pattern, Map<Character, RecipeSlot> key, int count) {
        if (pattern.size() > SIZE) {
            throw new IllegalArgumentException("padrão com mais de 3 linhas");
        }
        List<RecipeSlot> grid = new ArrayList<>(Collections.nCopies(SLOTS, RecipeSlot.EMPTY));
        for (int r = 0; r < pattern.size(); r++) {
            String line = pattern.get(r);
            if (line.length() > SIZE) {
                throw new IllegalArgumentException("padrão com mais de 3 colunas");
            }
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch != ' ') {
                    RecipeSlot s = key.get(ch);
                    if (s == null) {
                        throw new IllegalArgumentException("letra sem chave: " + ch);
                    }
                    grid.set(r * SIZE + c, s);
                }
            }
        }
        return new RecipeDraft(Shape.SHAPED, grid, count, false);
    }

    public static RecipeDraft fromShapeless(List<RecipeSlot> ingredients, int count) {
        if (ingredients.size() > SLOTS) {
            throw new IllegalArgumentException("mais de 9 ingredientes");
        }
        List<RecipeSlot> grid = new ArrayList<>(ingredients);
        while (grid.size() < SLOTS) {
            grid.add(RecipeSlot.EMPTY);
        }
        return new RecipeDraft(Shape.SHAPELESS, grid, count, false);
    }
}
