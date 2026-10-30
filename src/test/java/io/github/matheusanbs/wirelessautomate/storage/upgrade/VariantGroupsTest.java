package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VariantGroupsTest {
    private static final Set<String> RAW_SILVER = Set.of("c:raw_materials/silver", "c:raw_materials");
    private static final Set<String> RAW_SILVER_BLOCK = Set.of("c:storage_blocks/raw_silver", "c:storage_blocks");

    private static VariantGroups.Shape rawSilver() {
        return new VariantGroups.Shape(new int[] {1, 9}, List.of(RAW_SILVER, RAW_SILVER_BLOCK));
    }

    @SafeVarargs
    private static VariantGroups.Shape shape(int[] factors, Set<String>... levels) {
        return new VariantGroups.Shape(factors, List.of(levels));
    }

    @Test
    void duasPratasCruasFormamUmGrupo() {
        List<int[]> groups = VariantGroups.of(List.of(rawSilver(), rawSilver()));
        assertEquals(1, groups.size());
        assertArrayEquals(new int[] {0, 1}, groups.get(0));
    }

    @Test
    void tagPaiSemBarraNaoJunta() {
        assertFalse(VariantGroups.specific("c:ingots"));
        assertTrue(VariantGroups.specific("c:ingots/silver"));
        assertFalse(VariantGroups.specific("minecraft:logs/oak"));
        assertFalse(VariantGroups.specific("c:/x"));
        List<int[]> groups = VariantGroups.of(List.of(
                shape(new int[] {1, 9}, Set.of("c:ingots", "c:ingots/silver"), Set.of("c:storage_blocks")),
                shape(new int[] {1, 9}, Set.of("c:ingots", "c:ingots/silver"), Set.of("c:storage_blocks"))));
        assertTrue(groups.isEmpty(), "o bloco só divide a tag-pai");
    }

    @Test
    void tamanhoOuFatoresDiferentesNaoJuntam() {
        Set<String> nugget = Set.of("c:nuggets/silver");
        Set<String> ingot = Set.of("c:ingots/silver");
        Set<String> block = Set.of("c:storage_blocks/silver");
        assertTrue(VariantGroups.of(List.of(
                shape(new int[] {1, 9, 9}, nugget, ingot, block),
                shape(new int[] {1, 9}, ingot, block))).isEmpty(), "tamanhos diferentes");
        assertTrue(VariantGroups.of(List.of(
                shape(new int[] {1, 9}, ingot, block),
                shape(new int[] {1, 4}, ingot, block))).isEmpty(), "fatores diferentes");
    }

    @Test
    void umNivelSemTagEmComumNaoJunta() {
        assertTrue(VariantGroups.of(List.of(
                shape(new int[] {1, 9}, RAW_SILVER, RAW_SILVER_BLOCK),
                shape(new int[] {1, 9}, RAW_SILVER, Set.of("c:storage_blocks/silver")))).isEmpty());
    }

    @Test
    void uniaoTransitiva() {
        // A e C não dividem nada na base; B divide com os dois.
        VariantGroups.Shape a = shape(new int[] {1, 9}, Set.of("c:raw_materials/silver"), RAW_SILVER_BLOCK);
        VariantGroups.Shape b = shape(new int[] {1, 9}, Set.of("c:raw_materials/silver", "c:raw_materials/argentum"),
                RAW_SILVER_BLOCK);
        VariantGroups.Shape c = shape(new int[] {1, 9}, Set.of("c:raw_materials/argentum"), RAW_SILVER_BLOCK);
        VariantGroups.Shape other = shape(new int[] {1, 9}, Set.of("c:raw_materials/tin"), Set.of("c:storage_blocks/raw_tin"));
        List<int[]> groups = VariantGroups.of(List.of(c, other, a, b));
        assertEquals(1, groups.size());
        assertArrayEquals(new int[] {0, 2, 3}, groups.get(0));
    }

    @Test
    void ordemEstavel() {
        VariantGroups.Shape tin = shape(new int[] {1, 9}, Set.of("c:raw_materials/tin"), Set.of("c:storage_blocks/raw_tin"));
        List<VariantGroups.Shape> input = List.of(tin, rawSilver(), tin, rawSilver(), rawSilver());
        for (int run = 0; run < 5; run++) {
            List<int[]> groups = VariantGroups.of(input);
            assertEquals(2, groups.size());
            assertArrayEquals(new int[] {0, 2}, groups.get(0));
            assertArrayEquals(new int[] {1, 3, 4}, groups.get(1));
        }
    }
}
