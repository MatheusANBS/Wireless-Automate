package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        assertEquals(25, pages.size());
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
        assertEquals(53, recipeFor);
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
