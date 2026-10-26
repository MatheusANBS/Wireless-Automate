package io.github.matheusanbs.wirelessautomate.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentQueryTest {
    /** O {@code productivebees:gene_group} como o codec dele o serializa: attribute, value e purity. */
    private static Map<String, Object> gene(String attribute, String value, int purity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("attribute", attribute);
        map.put("value", value);
        map.put("purity", purity);
        return map;
    }

    private static boolean test(String text, Object tree) {
        ComponentQuery query = ComponentQuery.parse(text);
        assertNotNull(query, text);
        return query.test(tree);
    }

    @Test
    void parsesIdOperatorAndValue() {
        ComponentQuery q = ComponentQuery.parse("apotheosis:purity=Perfect");
        assertEquals("apotheosis:purity", q.id());
        assertEquals(List.of(), q.path());
        assertEquals(ComponentQuery.Op.EQ, q.op());
        assertEquals("perfect", q.value());
        assertEquals(ComponentQuery.Op.EXISTS, ComponentQuery.parse("apotheosis:purity").op());
        assertEquals(ComponentQuery.Op.EXISTS, ComponentQuery.parse("apotheosis:purity=").op());
    }

    @Test
    void parsesPathAndEveryOperator() {
        ComponentQuery q = ComponentQuery.parse("productivebees:gene_group->purity>=100");
        assertEquals("productivebees:gene_group", q.id());
        assertEquals(List.of("purity"), q.path());
        assertEquals(ComponentQuery.Op.GE, q.op());
        assertEquals("100", q.value());
        assertEquals(List.of("a", "b", "*"), ComponentQuery.parse("minecraft:custom_data->a.b.*~x").path());
        assertEquals(ComponentQuery.Op.LE, ComponentQuery.parse("x:y->a<=1").op());
        assertEquals(ComponentQuery.Op.GT, ComponentQuery.parse("x:y->a>1").op());
        assertEquals(ComponentQuery.Op.LT, ComponentQuery.parse("x:y->a<1").op());
        assertEquals(ComponentQuery.Op.NE, ComponentQuery.parse("x:y->a!=1").op());
        assertEquals(ComponentQuery.Op.CONTAINS, ComponentQuery.parse("x:y->a~bee").op());
        assertEquals(ComponentQuery.Op.GT, ComponentQuery.parse("x:y>3").op());
    }

    @Test
    void rejectsBadlyWrittenQueries() {
        assertNull(ComponentQuery.parse(""));
        assertNull(ComponentQuery.parse("Foo:Bar"));
        assertNull(ComponentQuery.parse("x:y->a..b=1"));
        assertNull(ComponentQuery.parse("x:y->a>="));
        assertNull(ComponentQuery.parse("x:y->a~"));
        assertNull(ComponentQuery.parse("=1"));
    }

    @Test
    void parseAllSplitsOnSemicolonAndToleratesATrailingOne() {
        List<ComponentQuery> all = ComponentQuery.parseAll("productivebees:gene_group->attribute=productivity; "
                + "productivebees:gene_group->purity>=100");
        assertEquals(2, all.size());
        assertEquals(1, ComponentQuery.parseAll("a:b=1;").size());
        assertNull(ComponentQuery.parseAll("a:b=1;Bad"));
        assertNull(ComponentQuery.parseAll(";"));
    }

    @Test
    void fieldInsideAGeneGroup() {
        Map<String, Object> tree = gene("productivity", "productivebees:high", 100);
        assertTrue(test("productivebees:gene_group->purity>=100", tree));
        assertTrue(test("productivebees:gene_group->purity=100", tree));
        assertFalse(test("productivebees:gene_group->purity>100", tree));
        assertFalse(test("productivebees:gene_group->purity>=101", tree));
        assertTrue(test("productivebees:gene_group->purity<=100", tree));
        assertTrue(test("productivebees:gene_group->attribute=productivity", tree));
        assertFalse(test("productivebees:gene_group->attribute=endurance", tree));
        assertTrue(test("productivebees:gene_group->attribute!=endurance", tree));
        assertFalse(test("productivebees:gene_group->attribute!=productivity", tree));
        assertTrue(test("productivebees:gene_group->value=high", tree));
    }

    @Test
    void aMissingFieldOnlyMatchesNotEqual() {
        Map<String, Object> tree = gene("productivity", "x", 50);
        assertFalse(test("a:b->missing=1", tree));
        assertFalse(test("a:b->missing>=1", tree));
        assertFalse(test("a:b->missing~x", tree));
        assertTrue(test("a:b->missing!=1", tree));
        assertTrue(test("a:b->purity", tree));
        assertFalse(test("a:b->missing", tree));
    }

    @Test
    void listsAreSearchedWithAWildcardOrAnIndex() {
        Object list = List.of(gene("productivity", "a", 30), gene("endurance", "b", 100));
        assertTrue(test("productivebees:gene_group_list->*.purity>=100", list));
        assertFalse(test("productivebees:gene_group_list->*.purity>100", list));
        assertTrue(test("productivebees:gene_group_list->1.attribute=endurance", list));
        assertFalse(test("productivebees:gene_group_list->0.attribute=endurance", list));
        assertFalse(test("productivebees:gene_group_list->5.attribute=endurance", list));
        assertTrue(test("productivebees:gene_group_list->*.attribute!=weather_tolerance", list));
    }

    @Test
    void containsSearchesTextAndListElements() {
        Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("name", "Golden Bee");
        custom.put("tags", List.of("flying", "queen"));
        assertTrue(test("minecraft:custom_data->name~golden", custom));
        assertFalse(test("minecraft:custom_data->name~silver", custom));
        assertTrue(test("minecraft:custom_data->tags~que", custom));
        assertFalse(test("minecraft:custom_data->tags~worker", custom));
    }

    @Test
    void equalsAcceptsAMissingNamespaceAndNumbersAsText() {
        assertTrue(test("productivebees:bee_type=iron", "productivebees:iron"));
        assertTrue(test("productivebees:bee_type=productivebees:iron", "productivebees:iron"));
        assertFalse(test("productivebees:bee_type=gold", "productivebees:iron"));
        assertTrue(test("x:y->n=2", Map.of("n", "2")));
        assertTrue(test("x:y->n=2", Map.of("n", 2.0)));
        assertTrue(test("x:y->n>=2", Map.of("n", "3")));
        assertFalse(test("x:y->n>=2", Map.of("n", "many")));
    }

    @Test
    void leafPathsUseWildcardForLists() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("genes", List.of(gene("a", "b", 1)));
        tree.put("owner", "x");
        assertEquals(List.of("genes.*.attribute", "genes.*.value", "genes.*.purity", "owner"),
                List.copyOf(ComponentQuery.leafPaths(tree, 5, 50)));
        assertEquals(List.of("attribute", "value", "purity"),
                List.copyOf(ComponentQuery.leafPaths(gene("a", "b", 1), 5, 50)));
        assertEquals(1, ComponentQuery.leafPaths(tree, 5, 1).size());
    }

    @Test
    void leafValuesCollectTheScalarsAtAPath() {
        Object list = List.of(gene("productivity", "a", 30), gene("endurance", "b", 100), gene("x", "c", 100));
        assertEquals(List.of("30", "100"), List.copyOf(ComponentQuery.leafValues(list, List.of("*", "purity"), 10)));
        assertEquals(List.of("productivity", "endurance"),
                List.copyOf(ComponentQuery.leafValues(list, List.of("*", "attribute"), 2)));
    }

    @Test
    void partsReadWhatIsBeingTyped() {
        ComponentQuery.Parts typing = ComponentQuery.parts("productivebees:gene_group->pur");
        assertTrue(typing.arrow());
        assertEquals("pur", typing.path());
        assertEquals("", typing.op());
        ComponentQuery.Parts value = ComponentQuery.parts("productivebees:gene_group->purity>=10");
        assertEquals(">=", value.op());
        assertEquals("10", value.value());
        assertFalse(ComponentQuery.parts("productivebees:gene").arrow());
    }
}
