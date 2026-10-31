package io.github.matheusanbs.wirelessautomate.client.guide;

import io.github.matheusanbs.wirelessautomate.client.guide.doc.Block;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideDoc;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.Span;
import io.github.matheusanbs.wirelessautomate.client.router.NetworkSearch;
import java.util.ArrayList;
import java.util.List;

/**
 * A busca do livro, sem classes do Minecraft: sem acento nem maiúscula (a mesma {@link NetworkSearch#fold} da busca
 * de redes), cada palavra da busca tem de aparecer no título ou no texto da página. Título casando vem antes de corpo;
 * dentro de cada grupo vale a ordem em que as páginas foram dadas (a do índice). O texto dobrado de cada página é
 * feito uma vez, em {@link #page}; cada tecla só percorre a lista.
 */
public final class GuideSearch {
    /** Tamanho alvo do trecho mostrado, em caracteres. */
    static final int SNIPPET = 40;

    private GuideSearch() {
    }

    /** Uma página pronta para a busca: o título e o texto corrido, mais os dois dobrados (mesmo tamanho). */
    public record Page(String id, String title, String text, String foldedTitle, String foldedText) {
    }

    /**
     * Um resultado: o trecho do corpo com a palavra em {@code [markStart, markStart + markLen)} ({@code markStart}
     * -1 quando a palavra só aparece no título) e a palavra no título ({@code titleMark}, -1 se não há).
     */
    public record Hit(String id, String title, String snippet, int markStart, int markLen, int titleMark,
            int titleMarkLen) {
    }

    public static Page page(String id, String title, String text) {
        String clean = collapse(text);
        return new Page(id, title, clean, NetworkSearch.fold(title), NetworkSearch.fold(clean));
    }

    /** O texto corrido da página: títulos, parágrafos, listas, tabelas e linhas, sem as tags. */
    public static String textOf(GuideDoc doc) {
        StringBuilder sb = new StringBuilder();
        for (Block b : doc.blocks()) {
            append(sb, b);
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, Block b) {
        switch (b) {
            case Block.Heading h -> spans(sb, h.spans());
            case Block.Paragraph p -> spans(sb, p.spans());
            case Block.ListBlock l -> l.items().forEach(i -> spans(sb, i));
            case Block.Table t -> {
                t.header().forEach(c -> spans(sb, c));
                t.rows().forEach(r -> r.forEach(c -> spans(sb, c)));
            }
            case Block.Row r -> r.children().forEach(c -> append(sb, c));
            default -> {
            }
        }
        sb.append(' ');
    }

    private static void spans(StringBuilder sb, List<Span> spans) {
        for (Span s : spans) {
            switch (s) {
                case Span.Text t -> sb.append(t.text());
                case Span.Bold t -> sb.append(t.text());
                case Span.Code t -> sb.append(t.text());
                case Span.Link t -> sb.append(t.text());
                default -> {
                }
            }
        }
        sb.append(' ');
    }

    private static String collapse(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean space = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!space) {
                    sb.append(' ');
                }
                space = true;
            } else {
                sb.append(c);
                space = false;
            }
        }
        int n = sb.length();
        return n > 0 && sb.charAt(n - 1) == ' ' ? sb.substring(0, n - 1) : sb.toString();
    }

    /** As palavras da busca, dobradas; vazio quando não há nenhuma. */
    public static List<String> words(String query) {
        List<String> out = new ArrayList<>();
        for (String w : NetworkSearch.fold(query).trim().split("\\s+")) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /** Os resultados da busca, na ordem; vazio para busca vazia. */
    public static List<Hit> search(List<Page> pages, String query) {
        List<String> words = words(query);
        List<Hit> byTitle = new ArrayList<>();
        if (words.isEmpty()) {
            return byTitle;
        }
        List<Hit> byBody = new ArrayList<>();
        for (Page p : pages) {
            boolean inTitle = true;
            boolean found = true;
            for (String w : words) {
                boolean t = p.foldedTitle().contains(w);
                inTitle &= t;
                found &= t || p.foldedText().contains(w);
            }
            if (found) {
                (inTitle ? byTitle : byBody).add(hit(p, words.get(0)));
            }
        }
        byTitle.addAll(byBody);
        return byTitle;
    }

    private static Hit hit(Page p, String word) {
        int titleMark = p.foldedTitle().indexOf(word);
        int titleLen = titleMark < 0 ? 0 : word.length();
        String text = p.text();
        int at = p.foldedText().indexOf(word);
        if (at < 0) {
            int end = cut(text, Math.min(text.length(), SNIPPET), 0);
            return new Hit(p.id(), p.title(), snippet(text, 0, end), -1, 0, titleMark, titleLen);
        }
        int start = Math.max(0, at - (SNIPPET - word.length()) / 3);
        int end = Math.min(text.length(), Math.max(start + SNIPPET, at + word.length()));
        if (start > 0) {
            int sp = text.indexOf(' ', start);
            start = sp >= 0 && sp < at ? sp + 1 : start;
        }
        end = cut(text, end, at + word.length());
        int lead = start > 0 ? 1 : 0;
        return new Hit(p.id(), p.title(), snippet(text, start, end), at - start + lead, word.length(), titleMark,
                titleLen);
    }

    /** O fim do trecho recuado até um espaço, sem passar para antes de {@code floor}. */
    private static int cut(String text, int end, int floor) {
        if (end >= text.length() || text.charAt(end) == ' ') {
            return end;
        }
        int sp = text.lastIndexOf(' ', end);
        return sp > floor ? sp : end;
    }

    private static String snippet(String text, int start, int end) {
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }
}
