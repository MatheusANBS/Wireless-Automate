package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.ArrayList;
import java.util.List;

/** Lê o texto em linha: negrito, código, links, imagens e as tags de item. Uma passada, sem regex. */
final class InlineParser {
    private InlineParser() {}

    static List<Span> parse(String s, Source src) {
        List<Span> out = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int n = s.length();
        int i = 0;
        while (i < n) {
            int next = special(s, i, out, text, src);
            if (next < 0) {
                text.append(s.charAt(i));
                i++;
            } else {
                i = next;
            }
        }
        flush(text, out);
        return out;
    }

    /** Se em {@code i} começa um trecho especial, o põe em {@code out} e devolve onde continua; senão, -1. */
    private static int special(String s, int i, List<Span> out, StringBuilder text, Source src) {
        char c = s.charAt(i);
        if (c == '*' && s.startsWith("**", i)) {
            int end = s.indexOf("**", i + 2);
            if (end > i + 2) {
                flush(text, out);
                out.add(new Span.Bold(s.substring(i + 2, end)));
                return end + 2;
            }
        } else if (c == '`') {
            int end = s.indexOf('`', i + 1);
            if (end > i + 1) {
                flush(text, out);
                out.add(new Span.Code(s.substring(i + 1, end)));
                return end + 1;
            }
        } else if (c == '!' && s.startsWith("![", i)) {
            return bracketed(s, i, i + 2, true, out, text);
        } else if (c == '[') {
            return bracketed(s, i, i + 1, false, out, text);
        } else if (c == '<') {
            Tag tag = Tag.read(s, i);
            if (tag != null && !tag.closing()) {
                flush(text, out);
                tag(tag, out, src);
                return tag.end();
            }
        }
        return -1;
    }

    /** {@code [texto](destino)} ou, com {@code image}, {@code ![alt](caminho)}; -1 se não fecha como tal. */
    private static int bracketed(String s, int start, int labelStart, boolean image, List<Span> out,
            StringBuilder text) {
        int close = s.indexOf(']', labelStart);
        if (close < 0 || close + 1 >= s.length() || s.charAt(close + 1) != '(') {
            return -1;
        }
        int paren = s.indexOf(')', close + 2);
        if (paren < 0) {
            return -1;
        }
        flush(text, out);
        String label = s.substring(labelStart, close);
        String target = s.substring(close + 2, paren);
        out.add(image ? new Span.InlineImage(target, label) : new Span.Link(target, label));
        return paren + 1;
    }

    private static void tag(Tag tag, List<Span> out, Source src) {
        switch (tag.name()) {
            case "ItemImage" -> {
                String id = tag.get("id");
                if (id == null) {
                    src.warn("<ItemImage> sem id");
                } else {
                    String comps = tag.get("components");
                    out.add(new Span.ItemIcon(id, tag.num("scale", 1, src), comps == null ? "" : comps));
                }
            }
            case "ItemLink" -> {
                String id = tag.get("id");
                if (id == null) {
                    src.warn("<ItemLink> sem id");
                } else {
                    out.add(new Span.ItemLink(id));
                }
            }
            case "br" -> { }
            default -> src.warn("tag desconhecida no texto: <" + tag.name() + ">");
        }
    }

    private static void flush(StringBuilder text, List<Span> out) {
        if (!text.isEmpty()) {
            out.add(new Span.Text(text.toString()));
            text.setLength(0);
        }
    }
}
