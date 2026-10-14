package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.TabletSearch.Candidate;
import io.github.matheusanbs.wirelessautomate.client.TabletSearch.Group;
import io.github.matheusanbs.wirelessautomate.client.TabletSearch.Suggestion;
import java.util.List;
import org.junit.jupiter.api.Test;

class TabletSearchTest {
    private static final List<Candidate<String>> MACHINES = List.of(
            new Candidate<>("furnace", "Fornalha", 10),
            new Candidate<>("blast", "Alto-fundidor", 2),
            new Candidate<>("smelter", "Fundição Elite", 4),
            new Candidate<>("chest", "Baú", 6));
    private static final List<Candidate<String>> NETWORKS = List.of(
            new Candidate<>("n1", "Linha de fundição", 12),
            new Candidate<>("n2", "Armazém", 20));
    private static final List<Candidate<String>> NAMES = List.of(
            new Candidate<>("Fundição de ouro", "Fundição de ouro", 1));

    @Test
    void emptyTextSuggestsNothing() {
        assertTrue(TabletSearch.suggest("", MACHINES, NETWORKS, NAMES).isEmpty());
        assertTrue(TabletSearch.suggest("   ", MACHINES, NETWORKS, NAMES).isEmpty());
        assertTrue(TabletSearch.matchingIds("", MACHINES).isEmpty());
    }

    @Test
    void groupsInOrderWithTheMatchedPart() {
        List<Suggestion<?>> list = TabletSearch.suggest("fund", MACHINES, NETWORKS, NAMES);
        assertEquals(List.of(Group.MACHINES, Group.MACHINES, Group.NETWORKS, Group.NAMES),
                list.stream().map(Suggestion::group).toList());
        // começa com o texto primeiro, mesmo com menos nós
        assertEquals("smelter", list.get(0).id());
        assertEquals(0, list.get(0).start());
        assertEquals(4, list.get(0).end());
        assertEquals("blast", list.get(1).id());
        assertEquals(5, list.get(1).start());
        assertEquals(9, list.get(1).end());
        assertEquals(12, list.get(2).count());
        assertEquals("Linha de fundição", list.get(2).label());
    }

    @Test
    void ignoresCaseAndAccents() {
        List<Suggestion<?>> list = TabletSearch.suggest("FUNDICAO", MACHINES, NETWORKS, NAMES);
        assertEquals(List.of("smelter", "n1", "Fundição de ouro"), list.stream().map(Suggestion::id).toList());
        Suggestion<?> network = list.get(1);
        assertEquals("fundição", network.label().substring(network.start(), network.end()));
        assertEquals(List.of("chest"), TabletSearch.matchingIds("bau", MACHINES));
        assertEquals("bau", TabletSearch.fold("BAÚ"));
    }

    @Test
    void foldKeepsTheLength() {
        String label = "Ção Ü ñ";
        assertEquals(label.length(), TabletSearch.fold(label).length());
        assertEquals("cao u n", TabletSearch.fold(label));
    }

    @Test
    void atMostThreePerGroupByCountThenName() {
        List<Candidate<String>> many = List.of(
                new Candidate<>("a", "Tanque B", 1),
                new Candidate<>("b", "Tanque A", 1),
                new Candidate<>("c", "Tanque C", 5),
                new Candidate<>("d", "Meu tanque", 9),
                new Candidate<>("e", "Tanque D", 2));
        List<Suggestion<?>> list = TabletSearch.suggest("tan", many, List.of(), List.of());
        // começo do nome antes; depois mais nós; depois o nome
        assertEquals(List.of("c", "e", "b"), list.stream().map(Suggestion::id).toList());
        assertEquals(TabletSearch.PER_GROUP, list.size());
    }

    @Test
    void matchingIdsTakesEveryMachineThatContainsTheText() {
        assertEquals(List.of("blast", "smelter"), TabletSearch.matchingIds("fund", MACHINES));
        assertEquals(List.of("furnace"), TabletSearch.matchingIds(" forna ", MACHINES));
        assertTrue(TabletSearch.matchingIds("xyz", MACHINES).isEmpty());
    }
}
