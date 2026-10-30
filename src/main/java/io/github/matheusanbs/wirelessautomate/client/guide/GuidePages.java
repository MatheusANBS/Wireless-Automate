package io.github.matheusanbs.wirelessautomate.client.guide;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideDoc;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideHeader;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideParser;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.GuideLayout;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.GuideMetrics;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.Sheet;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.TextMeasure;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

/**
 * As páginas do livro no idioma do jogo: lidas pelo {@code ResourceManager} uma vez ({@code guide/<idioma>/*.md}, com o
 * {@code en_us} como reserva por página), paginadas sob demanda e guardadas, com o índice por grupo e o mapa
 * item → página. O recarregar de recursos esquece tudo; a próxima abertura do livro lê de novo.
 */
public final class GuidePages {
    /** O recarregar de recursos do cliente (registrado no {@code ClientSetup}). */
    public static final ResourceManagerReloadListener RELOAD = manager -> clear();

    private static final String FALLBACK = "en_us";

    private static @Nullable String language;
    private static Map<String, GuideDoc> docs = Map.of();
    private static List<String> order = List.of();
    private static Map<String, String> itemPages = Map.of();
    private static final Map<String, List<Sheet>> SHEETS = new HashMap<>();
    private static @Nullable GuideIndex index;
    private static @Nullable FontMeasure measure;

    private GuidePages() {
    }

    /** Esquece as páginas, as folhas e o índice (recarregar de recursos ou saída do mundo). */
    public static void clear() {
        language = null;
        docs = Map.of();
        order = List.of();
        itemPages = Map.of();
        SHEETS.clear();
        index = null;
        measure = null;
    }

    /** Lê as páginas se ainda não leu (ou se o idioma mudou). */
    static void ensure() {
        Minecraft mc = Minecraft.getInstance();
        String want = mc.getLanguageManager().getSelected();
        if (want.equals(language)) {
            return;
        }
        clear();
        language = want;
        ResourceManager rm = mc.getResourceManager();
        measure = new FontMeasure(mc.font, rm);
        Map<String, GuideDoc> read = new HashMap<>();
        load(rm, FALLBACK, read);
        if (!FALLBACK.equals(want)) {
            load(rm, want, read);
        }
        List<String> ids = new ArrayList<>(read.keySet());
        ids.sort(Comparator.comparingInt((String id) -> GuideIndex.groupOrder(read.get(id).group()))
                .thenComparingInt(id -> position(read.get(id).header()))
                .thenComparing(id -> id));
        Map<String, GuideDoc> sorted = new LinkedHashMap<>();
        Map<String, String> items = new HashMap<>();
        for (String id : ids) {
            GuideDoc doc = read.get(id);
            sorted.put(id, doc);
            for (String item : doc.header().itemIds()) {
                items.putIfAbsent(item, id);
            }
        }
        docs = sorted;
        order = List.copyOf(ids);
        itemPages = items;
    }

    /** A página sem pai (a raiz) vem antes das outras do grupo; sem {@code position}, depois das que têm. */
    private static int position(GuideHeader h) {
        return h.parent().isEmpty() ? Integer.MIN_VALUE : h.position();
    }

    private static void load(ResourceManager rm, String lang, Map<String, GuideDoc> out) {
        String folder = "guide/" + lang;
        Map<ResourceLocation, Resource> found = rm.listResources(folder,
                loc -> loc.getNamespace().equals(WirelessAutomate.MODID) && loc.getPath().endsWith(".md"));
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            String path = e.getKey().getPath();
            String id = path.substring(folder.length() + 1, path.length() - ".md".length());
            try (Reader reader = e.getValue().openAsReader()) {
                GuideDoc doc = GuideParser.parse(IOUtils.toString(reader));
                for (String w : doc.warnings()) {
                    WirelessAutomate.LOGGER.warn("Livro: {}/{}.md, {}", lang, id, w);
                }
                out.put(id, doc);
            } catch (IOException ex) {
                WirelessAutomate.LOGGER.warn("Livro: não deu para ler {}", e.getKey(), ex);
            }
        }
    }

    /** As páginas na ordem do índice (grupo, depois posição). */
    public static List<String> pageIds() {
        ensure();
        return order;
    }

    static boolean exists(String id) {
        return docs.containsKey(id);
    }

    static String title(String id) {
        GuideDoc doc = docs.get(id);
        return doc == null || doc.title().isEmpty() ? id : doc.title();
    }

    static String group(String id) {
        GuideDoc doc = docs.get(id);
        return doc == null ? "" : doc.group();
    }

    static @Nullable String pageForItem(String itemId) {
        return itemPages.get(itemId);
    }

    /** As folhas da página, paginadas na primeira vez e guardadas. */
    static List<Sheet> sheets(String id) {
        List<Sheet> s = SHEETS.get(id);
        if (s == null) {
            GuideDoc doc = docs.get(id);
            s = doc == null ? List.of() : GuideLayout.paginate(doc, GuideMetrics.SHEET_W, GuideMetrics.SHEET_H, measure);
            SHEETS.put(id, s);
        }
        return s;
    }

    static TextMeasure measure() {
        return measure;
    }

    static GuideIndex index() {
        if (index == null) {
            index = GuideIndex.build(order, docs, measure);
        }
        return index;
    }

    /**
     * O destino de um link como está no texto ({@code networks.md}, {@code ./networks.md#x},
     * {@code wirelessautomate:networks.md}) para o id da página, ou {@code null} se não há essa página.
     */
    static @Nullable String resolve(String target) {
        String t = target;
        int hash = t.indexOf('#');
        if (hash >= 0) {
            t = t.substring(0, hash);
        }
        t = t.substring(Math.max(t.lastIndexOf('/'), t.lastIndexOf(':')) + 1);
        if (t.endsWith(".md")) {
            t = t.substring(0, t.length() - 3);
        }
        return docs.containsKey(t) ? t : null;
    }
}
