package io.github.matheusanbs.wirelessautomate.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.guide.GuideSearch.Hit;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideSearch.Page;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideParser;
import io.github.matheusanbs.wirelessautomate.client.router.NetworkSearch;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuideSearchTest {
    private static final String LONG = "Os itens vão de um roteador a outro sem fio, e quando o destino é um baú wireless "
            + "ele guarda milhões de itens por tipo, mesmo com o chunk descarregado e o dono longe da base.";

    private static List<Page> pages() {
        return List.of(
                GuideSearch.page("primeiros", "Primeiros passos", "Ligue dois baús trocando itens."),
                GuideSearch.page("bau", "Baú Wireless", "Guarda milhões de itens por tipo."),
                GuideSearch.page("roteador", "Roteador", LONG));
    }

    @Test
    void bauFindsBauIgnoringAccentAndCase() {
        List<Hit> hits = GuideSearch.search(pages(), "bau");
        assertEquals("bau", hits.get(0).id());
        assertEquals(3, hits.size());
        assertEquals(0, hits.get(0).titleMark());
        assertEquals(3, hits.get(0).titleMarkLen());
        assertEquals(3, GuideSearch.search(pages(), "BAÚ").size());
    }

    @Test
    void titleComesBeforeBodyThenTheOrderOfTheIndex() {
        List<Hit> hits = GuideSearch.search(pages(), "bau");
        assertEquals(List.of("bau", "primeiros", "roteador"), hits.stream().map(Hit::id).toList());
    }

    @Test
    void snippetHasTheWordAndEllipsisOnTheCutEnds() {
        Hit hit = GuideSearch.search(pages(), "bau").get(2);
        assertTrue(hit.snippet().startsWith("…"), hit.snippet());
        assertTrue(hit.snippet().endsWith("…"), hit.snippet());
        assertEquals("baú", hit.snippet().substring(hit.markStart(), hit.markStart() + hit.markLen()));
        assertTrue(hit.snippet().length() <= GuideSearch.SNIPPET + 4, hit.snippet());
        assertTrue(!hit.snippet().substring(1).startsWith(" "), hit.snippet());
    }

    @Test
    void shortTextHasNoEllipsis() {
        Hit hit = GuideSearch.search(pages(), "bau").get(0);
        assertEquals("Guarda milhões de itens por tipo.", hit.snippet());
        assertEquals(-1, hit.markStart());
    }

    @Test
    void emptyQueryReturnsNothing() {
        assertTrue(GuideSearch.search(pages(), "").isEmpty());
        assertTrue(GuideSearch.search(pages(), "   ").isEmpty());
    }

    @Test
    void everyWordMustAppearAndNothingFoundIsEmpty() {
        assertEquals(List.of("bau", "roteador"), GuideSearch.search(pages(), "bau tipo").stream().map(Hit::id).toList());
        assertTrue(GuideSearch.search(pages(), "zzzz").isEmpty());
    }

    @Test
    void usesTheSameFoldAsTheNetworkSearch() {
        Page p = GuideSearch.page("x", "Fábrica", "Química do João");
        assertEquals(NetworkSearch.fold("Fábrica"), p.foldedTitle());
        assertEquals(NetworkSearch.fold("Química do João"), p.foldedText());
        assertEquals(List.of("quimica"), GuideSearch.words("Química"));
    }

    @Test
    void whitespaceOfTheTextIsCollapsed() {
        assertEquals("a b c", GuideSearch.page("x", "T", "  a\n b\t\tc \n").text());
    }

    @Test
    void textOfCollectsSpansWithoutTags() {
        String text = GuideSearch.textOf(GuideParser.parse("---\nnavigation:\n  title: Teste\n---\n\n# Teste\n\n"
                + "Um **baú** com `código` e [link](outra.md).\n\n- item um\n- item dois\n"));
        assertTrue(GuideSearch.page("x", "T", text).text().contains("Um baú com código e link."), text);
        assertTrue(text.contains("item dois"), text);
    }
}
