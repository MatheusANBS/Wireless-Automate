package io.github.matheusanbs.wirelessautomate.client.guide;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.Block;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideDoc;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideHeader;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.Span;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.GuideLayout;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.GuideMetrics;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.Sheet;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.SheetOp;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.TextMeasure;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * O índice do livro como uma página virtual: um título por grupo e uma linha por página (ícone e link), paginado pelo
 * mesmo {@link GuideLayout}. Guarda em que folha cada grupo começa (o clique na fita) e, para cada folha, o grupo que
 * está à vista (a fita comprida). Os grupos e as fitas são fixos, na ordem da spec.
 */
final class GuideIndex {
    /** Os grupos na ordem das fitas; página com grupo desconhecido vai para o último. */
    static final List<String> GROUPS = List.of("comecar", "ferramentas", "armazenamentos", "energia", "painel",
            "integracoes", "referencia");

    private static final ResourceLocation[] RIBBON = new ResourceLocation[GROUPS.size()];
    private static final ResourceLocation[] RIBBON_ON = new ResourceLocation[GROUPS.size()];

    static {
        for (int g = 0; g < GROUPS.size(); g++) {
            RIBBON[g] = WirelessAutomate.id("textures/gui/guide/ribbon_" + GROUPS.get(g) + ".png");
            RIBBON_ON[g] = WirelessAutomate.id("textures/gui/guide/ribbon_" + GROUPS.get(g) + "_on.png");
        }
    }

    final List<Sheet> sheets;
    /** A folha do índice onde o grupo começa, ou -1 se ele não tem páginas. */
    final int[] groupSheet;
    /** O grupo à vista em cada folha do índice: o primeiro título nela, ou o que vinha da folha anterior. */
    final int[] sheetGroup;
    /** O ícone da fita de cada grupo: o da primeira página dele (vazio se o grupo não tem páginas). */
    final String[] groupIcon;
    final Component[] groupName;

    private GuideIndex(List<Sheet> sheets, int[] groupSheet, int[] sheetGroup, String[] groupIcon, Component[] names) {
        this.sheets = sheets;
        this.groupSheet = groupSheet;
        this.sheetGroup = sheetGroup;
        this.groupIcon = groupIcon;
        this.groupName = names;
    }

    static int groupOrder(String group) {
        int i = GROUPS.indexOf(group);
        return i < 0 ? GROUPS.size() - 1 : i;
    }

    static ResourceLocation ribbon(int group, boolean on) {
        return on ? RIBBON_ON[group] : RIBBON[group];
    }

    static GuideIndex build(List<String> order, Map<String, GuideDoc> docs, TextMeasure measure) {
        int n = GROUPS.size();
        Component[] names = new Component[n];
        String[] icons = new String[n];
        Arrays.fill(icons, "");
        List<Block> blocks = new ArrayList<>();
        blocks.add(heading(1, Component.translatable("gui.wirelessautomate.guide.contents").getString()));
        List<String> headings = new ArrayList<>();
        int open = -1;
        for (int g = 0; g < n; g++) {
            names[g] = Component.translatable("gui.wirelessautomate.guide.group." + GROUPS.get(g));
        }
        for (String id : order) {
            GuideHeader h = docs.get(id).header();
            int g = groupOrder(h.group());
            if (g != open) {
                open = g;
                icons[g] = h.icon();
                String title = names[g].getString();
                headings.add(title);
                blocks.add(heading(3, title));
            }
            String title = h.title().isEmpty() ? id : h.title();
            List<Span> line = h.icon().isEmpty()
                    ? List.of(new Span.Link(id + ".md", title))
                    : List.of(new Span.ItemIcon(h.icon(), 1.0, ""), new Span.Text(" "), new Span.Link(id + ".md", title));
            blocks.add(new Block.Paragraph(line));
        }
        GuideDoc doc = new GuideDoc(GuideHeader.EMPTY, blocks, List.of());
        List<Sheet> sheets = GuideLayout.paginate(doc, GuideMetrics.SHEET_W, GuideMetrics.SHEET_H, measure);
        int[] groupSheet = new int[n];
        Arrays.fill(groupSheet, -1);
        int[] sheetGroup = new int[sheets.size()];
        int seen = 0;
        int current = 0;
        for (int s = 0; s < sheets.size(); s++) {
            int first = -1;
            for (SheetOp op : sheets.get(s).ops()) {
                if (op instanceof SheetOp.Text t && t.style() == TextStyle.TITLE3 && seen < headings.size()
                        && t.text().equals(headings.get(seen))) {
                    int g = indexOfName(names, headings.get(seen));
                    seen++;
                    if (g >= 0) {
                        groupSheet[g] = s;
                        current = g;
                        if (first < 0) {
                            first = g;
                        }
                    }
                }
            }
            sheetGroup[s] = first >= 0 ? first : current;
        }
        return new GuideIndex(sheets, groupSheet, sheetGroup, icons, names);
    }

    private static int indexOfName(Component[] names, String title) {
        for (int g = 0; g < names.length; g++) {
            if (names[g].getString().equals(title)) {
                return g;
            }
        }
        return -1;
    }

    private static Block heading(int level, String text) {
        return new Block.Heading(level, List.of(new Span.Text(text)));
    }
}
