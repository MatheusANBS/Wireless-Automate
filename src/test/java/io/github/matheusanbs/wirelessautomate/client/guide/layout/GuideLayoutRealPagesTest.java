package io.github.matheusanbs.wirelessautomate.client.guide.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideDoc;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** As páginas de verdade, nos dois idiomas, na medida real da folha: sem folha vazia nem operação fora dela. */
class GuideLayoutRealPagesTest {
    private static final Path GUIDE = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "assets",
            "wirelessautomate", "guide");

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "pt_br"})
    void paginasReaisCabemNasFolhas(String lang) throws IOException {
        List<Path> pages;
        try (Stream<Path> s = Files.list(GUIDE.resolve(lang))) {
            pages = s.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        }
        assertEquals(25, pages.size());
        List<String> problems = new ArrayList<>();
        StringBuilder counts = new StringBuilder();
        for (Path page : pages) {
            String name = page.getFileName().toString();
            GuideDoc doc = GuideParser.parse(Files.readString(page, StandardCharsets.UTF_8));
            List<Sheet> sheets = GuideLayout.paginate(doc, GuideMetrics.SHEET_W, GuideMetrics.SHEET_H,
                    GuideLayoutTest.FAKE);
            counts.append(name).append('=').append(sheets.size()).append(' ');
            assertFalse(sheets.isEmpty(), name);
            for (int i = 0; i < sheets.size(); i++) {
                Sheet sheet = sheets.get(i);
                if (sheet.ops().isEmpty()) {
                    problems.add(lang + "/" + name + ": folha " + (i + 1) + " vazia");
                }
                for (SheetOp op : sheet.ops()) {
                    if (op.x() < 0 || op.y() < 0 || op.x() + op.w() > GuideMetrics.SHEET_W
                            || op.y() + op.h() > GuideMetrics.SHEET_H) {
                        problems.add(lang + "/" + name + ": folha " + (i + 1) + " fora da área: " + op);
                    }
                }
            }
        }
        System.out.println("FOLHAS " + lang + ": " + counts);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
