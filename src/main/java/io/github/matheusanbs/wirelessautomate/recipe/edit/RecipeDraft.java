package io.github.matheusanbs.wirelessautomate.recipe.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rascunho imutável de uma receita de bancada 3x3, sem classes do Minecraft. A forma {@link Shape#RING} é o anel
 * de subir tier ({@code wirelessautomate:tier_upgrade}): o centro fica sempre vazio (travado), a quantidade é 1 e
 * o padrão não se corta (o anel precisa ocupar a grade 3×3 inteira).
 */
public record RecipeDraft(Shape shape, List<RecipeSlot> slots, int count, boolean disabled) {
    public enum Shape { SHAPED, SHAPELESS, RING }

    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 64;
    public static final int SIZE = 3;
    public static final int SLOTS = SIZE * SIZE;
    /** O centro da grade, travado no anel. */
    public static final int CENTER = SLOTS / 2;

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
        if (shape == Shape.RING && (!slots.get(CENTER).isEmpty() || count != MIN_COUNT)) {
            throw new IllegalArgumentException("o anel exige o centro vazio e quantidade 1");
        }
        slots = List.copyOf(slots);
    }

    /** O slot {@code i} está travado (o centro do anel)? */
    public boolean locked(int i) {
        return shape == Shape.RING && i == CENTER;
    }

    /** No anel, o centro não muda. */
    public RecipeDraft withSlot(int i, RecipeSlot s) {
        if (locked(i)) {
            return this;
        }
        List<RecipeSlot> copy = new ArrayList<>(slots);
        copy.set(i, s);
        return new RecipeDraft(shape, copy, count, disabled);
    }

    /** No anel, a quantidade fica em 1. */
    public RecipeDraft withCount(int newCount) {
        if (shape == Shape.RING) {
            return this;
        }
        return new RecipeDraft(shape, slots, Math.max(MIN_COUNT, Math.min(MAX_COUNT, newCount)), disabled);
    }

    public RecipeDraft withDisabled(boolean newDisabled) {
        return new RecipeDraft(shape, slots, count, newDisabled);
    }

    public boolean isEmpty() {
        return slots.stream().allMatch(RecipeSlot::isEmpty);
    }

    /** As bordas ocupadas: {minRow, maxRow, minCol, maxCol}. */
    private int[] bounds() {
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
        return new int[] {minRow, maxRow, minCol, maxCol};
    }

    /**
     * Os ingredientes chegam às quatro bordas? É o que o {@code tier_upgrade} exige: o padrão, sem as bordas
     * vazias, ainda ocupa a grade 3×3 inteira.
     */
    public boolean spansGrid() {
        int[] b = bounds();
        return b[0] == 0 && b[1] == SIZE - 1 && b[2] == 0 && b[3] == SIZE - 1;
    }

    /** O padrão com a chave; cortado nas bordas vazias, menos no anel (sempre 3×3, centro {@code ' '}). */
    public ShapedLayout layout() {
        if (isEmpty()) {
            throw new IllegalStateException("grade vazia");
        }
        int[] b = shape == Shape.RING ? new int[] {0, SIZE - 1, 0, SIZE - 1} : bounds();
        int minRow = b[0], maxRow = b[1], minCol = b[2], maxCol = b[3];
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

    /** O anel de um {@code tier_upgrade}: padrão 3×3 com o centro {@code ' '}. */
    public static RecipeDraft fromRing(List<String> pattern, Map<Character, RecipeSlot> key) {
        if (pattern.size() != SIZE || pattern.stream().anyMatch(line -> line.length() != SIZE)) {
            throw new IllegalArgumentException("o anel precisa de um padrão 3×3");
        }
        if (pattern.get(1).charAt(1) != ' ') {
            throw new IllegalArgumentException("o centro do anel precisa ser um espaço");
        }
        RecipeDraft shaped = fromShaped(pattern, key, MIN_COUNT);
        return new RecipeDraft(Shape.RING, shaped.slots(), MIN_COUNT, false);
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
