package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Lê o corpo da página, bloco a bloco: títulos, parágrafos, listas, tabelas e as tags de bloco. */
final class BlockParser {
    private static final Set<String> BLOCK_TAGS =
            Set.of("ItemImage", "BlockImage", "Row", "RecipeFor", "Recipe", "SubPages", "GameScene", "br");
    private static final Set<String> INLINE_TAGS = Set.of("ItemImage", "ItemLink");

    private final Source src;
    private final List<Block> out = new ArrayList<>();

    private BlockParser(Source src) {
        this.src = src;
    }

    static List<Block> parse(Source src) {
        BlockParser p = new BlockParser(src);
        while (src.hasNext()) {
            p.block(src.next());
        }
        return p.out;
    }

    private void block(String line) {
        String t = line.strip();
        if (t.isEmpty()) {
            return;
        }
        if (heading(t) > 0) {
            int level = heading(t);
            out.add(new Block.Heading(level, InlineParser.parse(t.substring(level).strip(), src)));
        } else if (t.startsWith("|")) {
            table(t);
        } else if (marker(t) > 0) {
            list(t);
        } else if (!t.startsWith("<") || !tagBlock(t)) {
            paragraph(t);
        }
    }

    /** O nível do título ({@code #} seguidos de espaço), ou 0. */
    private static int heading(String t) {
        int n = 0;
        while (n < t.length() && t.charAt(n) == '#') {
            n++;
        }
        return n > 0 && n <= 6 && n < t.length() && t.charAt(n) == ' ' ? n : 0;
    }

    /** O tamanho do marcador de item de lista no começo de {@code t} ({@code "- "} ou {@code "12. "}), ou 0. */
    private static int marker(String t) {
        if (t.startsWith("- ")) {
            return 2;
        }
        int n = 0;
        while (n < t.length() && Character.isDigit(t.charAt(n))) {
            n++;
        }
        return n > 0 && t.startsWith(". ", n) ? n + 2 : 0;
    }

    private void table(String first) {
        List<String> lines = new ArrayList<>();
        lines.add(first);
        while (src.peek() != null && src.peek().strip().startsWith("|")) {
            lines.add(src.next());
        }
        out.add(TableParser.parse(lines, src));
    }

    private void list(String first) {
        boolean numbered = !first.startsWith("-");
        List<List<Span>> items = new ArrayList<>();
        StringBuilder item = new StringBuilder(first.substring(marker(first)));
        while (src.peek() != null) {
            String t = src.peek().strip();
            if (t.isEmpty() || startsBlock(t)) {
                break;
            }
            src.next();
            int m = marker(t);
            if (m > 0) {
                items.add(InlineParser.parse(item.toString().strip(), src));
                item.setLength(0);
                item.append(t.substring(m));
            } else {
                item.append(' ').append(t);
            }
        }
        items.add(InlineParser.parse(item.toString().strip(), src));
        out.add(new Block.ListBlock(numbered, items));
    }

    private void paragraph(String first) {
        StringBuilder sb = new StringBuilder(first);
        while (src.peek() != null) {
            String t = src.peek().strip();
            if (t.isEmpty() || startsBlock(t) || marker(t) > 0) {
                break;
            }
            sb.append(' ').append(src.next().strip());
        }
        List<Span> spans = InlineParser.parse(sb.toString(), src);
        if (spans.size() == 1 && spans.get(0) instanceof Span.InlineImage img) {
            out.add(new Block.Image(img.path()));
        } else {
            out.add(new Block.Paragraph(spans));
        }
    }

    /** Uma linha que não continua o parágrafo ou o item: título, tabela ou tag de bloco. */
    private static boolean startsBlock(String t) {
        if (heading(t) > 0 || t.startsWith("|")) {
            return true;
        }
        if (!t.startsWith("<")) {
            return false;
        }
        Tag tag = Tag.read(t, 0);
        return tag != null && (tag.closing() || isBlockTag(tag, t));
    }

    private static boolean isBlockTag(Tag tag, String t) {
        if (!BLOCK_TAGS.contains(tag.name())) {
            return false;
        }
        return !INLINE_TAGS.contains(tag.name()) || t.substring(tag.end()).isBlank();
    }

    /** Trata a linha como tag de bloco; {@code false} se for texto com tag em linha (parágrafo). */
    private boolean tagBlock(String t) {
        Tag tag = Tag.read(t, 0);
        if (tag == null) {
            return false;
        }
        if (tag.closing()) {
            src.warn("fechamento sem abertura: </" + tag.name() + ">");
            return true;
        }
        if (!isBlockTag(tag, t)) {
            if (INLINE_TAGS.contains(tag.name())) {
                return false;
            }
            src.warn("tag desconhecida: <" + tag.name() + ">");
            return true;
        }
        switch (tag.name()) {
            case "ItemImage" -> itemImage(tag).ifPresent(out::add);
            case "BlockImage" -> blockImage(tag);
            case "Row" -> row(tag);
            case "RecipeFor" -> id(tag, "RecipeFor", id -> new Block.RecipeFor(id));
            case "Recipe" -> id(tag, "Recipe", id -> new Block.Recipe(id));
            case "SubPages" -> out.add(new Block.SubPages(tag.flag("icons")));
            case "GameScene" -> out.add(SceneParser.parse(tag, src));
            default -> out.add(new Block.Break());
        }
        return true;
    }

    private Optional<Block> itemImage(Tag tag) {
        String id = tag.get("id");
        if (id == null) {
            src.warn("<ItemImage> sem id");
            return Optional.empty();
        }
        String comps = tag.get("components");
        return Optional.of(
                new Block.ItemImage(id, tag.num("scale", 1, src), tag.flow(src), comps == null ? "" : comps));
    }

    private void blockImage(Tag tag) {
        String id = tag.get("id");
        if (id == null) {
            src.warn("<BlockImage> sem id");
            return;
        }
        out.add(new Block.BlockImage(id, tag.props(), tag.num("scale", 1, src), tag.flow(src)));
    }

    private void id(Tag tag, String name, Function<String, Block> make) {
        String id = tag.get("id");
        if (id == null) {
            src.warn("<" + name + "> sem id");
        } else {
            out.add(make.apply(id));
        }
    }

    private void row(Tag open) {
        List<Block> children = new ArrayList<>();
        boolean closed = open.selfClosing();
        while (!closed && src.hasNext()) {
            String t = src.next().strip();
            if (t.isEmpty()) {
                continue;
            }
            Tag tag = t.startsWith("<") ? Tag.read(t, 0) : null;
            if (tag != null && tag.closing() && tag.name().equals("Row")) {
                closed = true;
            } else if (tag != null && !tag.closing() && tag.name().equals("ItemImage")) {
                itemImage(tag).ifPresent(children::add);
            } else {
                src.warn("linha inesperada no <Row>: " + t);
            }
        }
        if (!closed) {
            src.warn("o <Row> não fecha");
        }
        out.add(new Block.Row((int) open.num("gap", 0, src), children));
    }
}
