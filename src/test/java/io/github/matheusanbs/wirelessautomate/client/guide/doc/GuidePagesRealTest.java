package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** As páginas de verdade, nos dois idiomas: nenhuma deve gerar aviso do parser. */
class GuidePagesRealTest {
    private static final Path GUIDE = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "assets",
            "wirelessautomate", "guide");

    private static List<Path> pages(String lang) throws IOException {
        try (Stream<Path> s = Files.list(GUIDE.resolve(lang))) {
            return s.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "pt_br"})
    void paginasReaisSemAvisos(String lang) throws IOException {
        List<Path> pages = pages(lang);
        assertEquals(26, pages.size());
        List<String> problems = new ArrayList<>();
        for (Path page : pages) {
            GuideDoc doc = GuideParser.parse(Files.readString(page, StandardCharsets.UTF_8));
            String name = page.getFileName().toString();
            for (String w : doc.warnings()) {
                problems.add(lang + "/" + name + ": " + w);
            }
            assertFalse(doc.title().isBlank(), name + ": sem título");
            assertFalse(doc.group().isBlank(), name + ": sem grupo");
            Block.Heading h1 = assertInstanceOf(Block.Heading.class, doc.blocks().get(0), name);
            assertEquals(1, h1.level(), name);
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void routerCompleta() throws IOException {
        GuideDoc doc = GuideParser.parse(Files.readString(GUIDE.resolve("en_us/router.md"), StandardCharsets.UTF_8));
        assertEquals("Wireless Router", doc.title());
        assertEquals("ferramentas", doc.group());
        assertEquals(2, doc.header().position());
        assertEquals(1, count(doc, Block.BlockImage.class));
        assertEquals(2, count(doc, Block.Scene.class));
        assertEquals(1, count(doc, Block.RecipeFor.class));
        assertEquals(1, count(doc, Block.Row.class));
    }

    @Test
    void totaisDasTagsNasPaginasEmIngles() throws IOException {
        int scenes = 0;
        int sceneBlocks = 0;
        int recipeFor = 0;
        int notes = 0;
        for (Path page : pages("en_us")) {
            GuideDoc doc = GuideParser.parse(Files.readString(page, StandardCharsets.UTF_8));
            scenes += count(doc, Block.Scene.class);
            recipeFor += count(doc, Block.RecipeFor.class);
            for (Block b : doc.blocks()) {
                if (b instanceof Block.Scene s) {
                    sceneBlocks += s.blocks().size();
                    notes += s.notes().size();
                }
            }
        }
        assertEquals(10, scenes);
        assertEquals(143, sceneBlocks);
        assertEquals(29, notes);
        assertEquals(54, recipeFor);
    }

    /** O que o texto-fonte tem, contado sem o parser, contra o que o parser extraiu: nada pulado em silêncio. */
    @ParameterizedTest
    @ValueSource(strings = {"en_us", "pt_br"})
    void contagensDoTextoBatemComAsDoParser(String lang) throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path page : pages(lang)) {
            String text = Files.readString(page, StandardCharsets.UTF_8);
            Map<String, Integer> expected = fromText(text);
            Map<String, Integer> actual = fromDoc(GuideParser.parse(text));
            if (!expected.equals(actual)) {
                problems.add(lang + "/" + page.getFileName() + ": texto " + expected + " parser " + actual);
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void osDoisIdiomasTemAMesmaEstrutura() throws IOException {
        List<Path> en = pages("en_us");
        for (Path page : en) {
            Path other = GUIDE.resolve("pt_br").resolve(page.getFileName().toString());
            Map<String, Integer> a = fromDoc(GuideParser.parse(Files.readString(page, StandardCharsets.UTF_8)));
            Map<String, Integer> b = fromDoc(GuideParser.parse(Files.readString(other, StandardCharsets.UTF_8)));
            assertEquals(a, b, page.getFileName().toString());
        }
    }

    @Test
    void totaisDeConteudoNasPaginasEmIngles() throws IOException {
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Path page : pages("en_us")) {
            fromText(Files.readString(page, StandardCharsets.UTF_8)).forEach((k, v) -> total.merge(k, v, Integer::sum));
        }
        assertEquals(115, total.get("ItemImage"));
        assertEquals(6, total.get("Row"));
        assertEquals(16, total.get("br"));
        assertEquals(2, total.get("Recipe"));
        assertEquals(1, total.get("SubPages"));
        assertEquals(1, total.get("ItemLink"));
        assertEquals(1, total.get("BlockImage"));
    }

    private static int occurrences(String text, String what) {
        int n = 0;
        for (int i = text.indexOf(what); i >= 0; i = text.indexOf(what, i + what.length())) {
            n++;
        }
        return n;
    }

    /** A linha {@code | --- | --- |}; um cabeçalho vazio ({@code | | |}) é linha de tabela. */
    private static boolean isSeparator(String t) {
        return t.indexOf('-') >= 0 && t.replace("|", "").replace("-", "").replace(":", "").isBlank();
    }

    private static Map<String, Integer> fromText(String text) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String tag : List.of("ItemImage", "Row", "br", "SubPages", "ItemLink", "BlockImage", "RecipeFor",
                "GameScene", "BlockAnnotation", "LineAnnotation", "IsometricCamera")) {
            m.put(tag, occurrences(text, "<" + tag));
        }
        m.put("Recipe", occurrences(text, "<Recipe "));
        m.put("Block", occurrences(text, "<Block "));
        int images = occurrences(text, "![");
        m.put("Image", images);
        m.put("Link", occurrences(text, "](") - images);
        int tableRows = 0;
        int headings = 0;
        for (String line : text.lines().toList()) {
            String t = line.strip();
            if (t.startsWith("|") && !isSeparator(t)) {
                tableRows++;
            }
            if (t.startsWith("#")) {
                headings++;
            }
        }
        m.put("TableRow", tableRows);
        m.put("Heading", headings);
        return m;
    }

    private static Map<String, Integer> fromDoc(GuideDoc doc) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String k : fromText("").keySet()) {
            m.put(k, 0);
        }
        for (Block b : doc.blocks()) {
            add(m, b);
        }
        return m;
    }

    private static void bump(Map<String, Integer> m, String key) {
        m.merge(key, 1, Integer::sum);
    }

    private static void spans(Map<String, Integer> m, List<Span> spans) {
        for (Span s : spans) {
            switch (s) {
                case Span.ItemIcon i -> bump(m, "ItemImage");
                case Span.ItemLink i -> bump(m, "ItemLink");
                case Span.InlineImage i -> bump(m, "Image");
                case Span.Link i -> bump(m, "Link");
                default -> { }
            }
        }
    }

    private static void add(Map<String, Integer> m, Block b) {
        switch (b) {
            case Block.Heading h -> {
                bump(m, "Heading");
                spans(m, h.spans());
            }
            case Block.Paragraph p -> spans(m, p.spans());
            case Block.ListBlock l -> l.items().forEach(it -> spans(m, it));
            case Block.Table t -> {
                t.header().forEach(c -> spans(m, c));
                t.rows().forEach(r -> r.forEach(c -> spans(m, c)));
                m.merge("TableRow", 1 + t.rows().size(), Integer::sum);
            }
            case Block.ItemImage i -> bump(m, "ItemImage");
            case Block.BlockImage i -> bump(m, "BlockImage");
            case Block.Row r -> {
                bump(m, "Row");
                r.children().forEach(c -> add(m, c));
            }
            case Block.RecipeFor r -> bump(m, "RecipeFor");
            case Block.Recipe r -> bump(m, "Recipe");
            case Block.Image i -> bump(m, "Image");
            case Block.SubPages s -> bump(m, "SubPages");
            case Block.Break x -> bump(m, "br");
            case Block.Scene s -> {
                bump(m, "GameScene");
                m.merge("Block", s.blocks().size(), Integer::sum);
                m.merge("IsometricCamera", 1, Integer::sum);
                for (SceneNote n : s.notes()) {
                    bump(m, n instanceof SceneNote.OnBlock ? "BlockAnnotation" : "LineAnnotation");
                    spans(m, n.text());
                }
            }
        }
    }

    private static int count(GuideDoc doc, Class<? extends Block> type) {
        int n = 0;
        for (Block b : doc.blocks()) {
            if (type.isInstance(b)) {
                n++;
            }
        }
        return n;
    }
}
