package io.github.matheusanbs.wirelessautomate.client.guide;

import io.github.matheusanbs.wirelessautomate.client.guide.GuideInk.Ink;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideSearch.Hit;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.GuideMetrics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * O campo da busca da folha de rosto e os resultados da folha direita. Guarda o texto digitado, filtra as páginas
 * ({@link GuideSearch}) só quando o texto muda e monta a tinta dos resultados nessa hora (e quando a marca anda); o
 * quadro só desenha o que está pronto. Mede e quebra com a fonte do jogo, por isso fica fora da {@link GuideSearch},
 * que é pura.
 */
final class GuideSearchBox {
    /** O que o campo aceita de largura (unidades de folha), com folga para o cursor. */
    private static final int FIELD_W = GuideMetrics.SHEET_W - 16;
    private static final int MAX_CHARS = 48;
    /** O realce da palavra (coral claro), o fundo e a barra do resultado marcado. */
    private static final int MARK = 0x4DF0603E;
    private static final int ROW_BG = 0x21F0603E;
    private static final int ROW_BAR = 0xFFF0603E;
    private static final int PAD = 6;
    private static final int ROWS_Y = 28;
    private static final int ROW_H = 28;
    private static final int KEYS_H = 12;
    private static final Style BOLD = Style.EMPTY.withBold(true);

    private final Font font;
    private final StringBuilder query = new StringBuilder();
    private boolean focused;
    private List<Hit> hits = List.of();
    private int visible;
    private int selected;
    private List<Ink> inks = List.of();
    private final List<String> problems = new ArrayList<>();
    private FormattedCharSequence queryText = FormattedCharSequence.EMPTY;
    private int queryWidth;
    private FormattedCharSequence count = FormattedCharSequence.EMPTY;

    GuideSearchBox(Font font) {
        this.font = font;
    }

    boolean isEmpty() {
        return query.length() == 0;
    }

    boolean focused() {
        return focused;
    }

    void focus() {
        focused = true;
    }

    FormattedCharSequence queryText() {
        return queryText;
    }

    int queryWidth() {
        return queryWidth;
    }

    /** "4 páginas encontradas", ou vazio quando não há resultado. */
    FormattedCharSequence count() {
        return count;
    }

    List<Ink> inks() {
        return inks;
    }

    List<String> problems() {
        return problems;
    }

    /** O total achado (pode ser maior que {@link #visible()}). */
    int total() {
        return hits.size();
    }

    /** A página do resultado marcado, ou {@code null} sem resultado. */
    String selectedPage() {
        return selected < visible ? hits.get(selected).id() : null;
    }

    /** O texto que a busca tem agora (para o e2e e para a tela conferir). */
    String text() {
        return query.toString();
    }

    /** Aceita o caractere se é imprimível e cabe no campo; refaz a busca. */
    boolean type(int codePoint) {
        if (codePoint < 32 || codePoint == 127 || query.length() >= MAX_CHARS) {
            return false;
        }
        int before = query.length();
        query.appendCodePoint(codePoint);
        if (font.width(query.toString()) > FIELD_W) {
            query.setLength(before);
            return false;
        }
        focused = true;
        changed();
        return true;
    }

    /** Cola o texto (só a primeira linha), caractere a caractere pelas mesmas regras do teclado. */
    void paste(String text) {
        int end = text.indexOf('\n');
        String line = end < 0 ? text : text.substring(0, end);
        boolean any = false;
        for (int i = 0; i < line.length();) {
            int cp = line.codePointAt(i);
            i += Character.charCount(cp);
            any |= type(cp);
        }
        focused |= any;
    }

    void backspace() {
        if (query.length() > 0) {
            query.setLength(query.offsetByCodePoints(query.length(), -1));
            changed();
        }
    }

    void clear() {
        query.setLength(0);
        changed();
    }

    /** Anda a marca pelos resultados à vista. */
    void move(int delta) {
        if (visible > 0) {
            int to = Math.clamp(selected + delta, 0, visible - 1);
            if (to != selected) {
                selected = to;
                build();
            }
        }
    }

    private void changed() {
        queryText = FormattedCharSequence.forward(query.toString(), Style.EMPTY);
        queryWidth = font.width(queryText);
        hits = GuideSearch.search(GuidePages.searchPages(), query.toString());
        int fit = (GuideMetrics.SHEET_H - ROWS_Y - KEYS_H) / ROW_H;
        visible = Math.min(fit, hits.size());
        selected = 0;
        count = hits.isEmpty() ? FormattedCharSequence.EMPTY : Component.translatable(
                hits.size() == 1 ? "gui.wirelessautomate.guide.search.found.one"
                        : "gui.wirelessautomate.guide.search.found.many", hits.size()).getVisualOrderText();
        build();
    }

    // ---- a tinta dos resultados

    private void build() {
        problems.clear();
        if (isEmpty()) {
            inks = List.of();
            return;
        }
        List<Ink> out = new ArrayList<>(visible * 8 + 4);
        out.add(text(0, 0, Component.translatable("gui.wirelessautomate.guide.search.results").getString(), BOLD,
                GuideInk.TITLE));
        out.add(new GuideInk.Ornament(0, 12, GuideMetrics.SHEET_W, 10));
        if (hits.isEmpty()) {
            out.add(text(PAD, ROWS_Y, Component.translatable("gui.wirelessautomate.guide.search.none").getString(),
                    Style.EMPTY, GuideInk.FAINT));
        }
        for (int i = 0; i < visible; i++) {
            row(out, hits.get(i), ROWS_Y + i * ROW_H, i == selected);
        }
        if (visible > 0) {
            String keys = Component.translatable("gui.wirelessautomate.guide.search.keys").getString();
            out.add(text(0, GuideMetrics.SHEET_H - font.lineHeight, fit(keys, Style.EMPTY, GuideMetrics.SHEET_W),
                    Style.EMPTY, GuideInk.FAINT));
        }
        inks = List.copyOf(out);
    }

    private void row(List<Ink> out, Hit hit, int y, boolean marked) {
        int w = GuideMetrics.SHEET_W;
        int avail = w - 2 * PAD;
        if (marked) {
            out.add(new GuideInk.Fill(0, y, w, ROW_H - 2, ROW_BG));
            out.add(new GuideInk.Fill(0, y, 3, ROW_H - 2, ROW_BAR));
        }
        String title = fit(hit.title(), BOLD, avail);
        int titleY = y + 4;
        if (hit.titleMark() >= 0 && hit.titleMark() + hit.titleMarkLen() <= title.length()) {
            mark(out, title, BOLD, hit.titleMark(), hit.titleMarkLen(), PAD, titleY);
        }
        out.add(text(PAD, titleY, title, BOLD, GuideInk.TEXT));
        int snipY = titleY + font.lineHeight + 3;
        snippet(out, hit, snipY, avail);
        out.add(new GuideInk.Link(0, y, w, ROW_H - 2, hit.id(), Component.literal(GuidePages.title(hit.id()))));
    }

    /** O trecho em três pedaços (antes, a palavra, depois); o que passa da largura é cortado com "…" no fim. */
    private void snippet(List<Ink> out, Hit hit, int y, int avail) {
        String s = hit.snippet();
        String shown = fit(s, Style.EMPTY, avail);
        int ms = hit.markStart();
        int me = ms + hit.markLen();
        boolean cutShort = !shown.equals(s);
        if (ms < 0 || cutShort && me > shown.length() - 1) {
            out.add(text(PAD, y, shown, Style.EMPTY, GuideInk.FAINT));
            return;
        }
        String before = shown.substring(0, ms);
        String word = shown.substring(ms, me);
        int wordX = PAD + font.width(before);
        mark(out, shown, Style.EMPTY, ms, hit.markLen(), PAD, y);
        out.add(text(PAD, y, before, Style.EMPTY, GuideInk.FAINT));
        out.add(text(wordX, y, word, Style.EMPTY, GuideInk.TEXT));
        out.add(text(wordX + font.width(word), y, shown.substring(me), Style.EMPTY, GuideInk.FAINT));
    }

    private void mark(List<Ink> out, String s, Style style, int start, int len, int x, int y) {
        int x0 = x + width(s.substring(0, start), style);
        out.add(new GuideInk.Fill(x0 - 1, y - 1, width(s.substring(start, start + len), style) + 2,
                font.lineHeight + 1, MARK));
    }

    private Ink text(int x, int y, String s, Style style, int color) {
        int right = x + width(s, style);
        if (right > GuideMetrics.SHEET_W || y + font.lineHeight > GuideMetrics.SHEET_H) {
            problems.add("o texto \"" + s + "\" dos resultados sai da folha (até " + right + ", " + (y
                    + font.lineHeight) + ")");
        }
        return new GuideInk.Txt(x, y, FormattedCharSequence.forward(s, style), color, 1f, 0);
    }

    private int width(String s, Style style) {
        return font.width(FormattedText.of(s, style));
    }

    /** {@code s} com o fim cortado e "…" até caber em {@code max}. */
    private String fit(String s, Style style, int max) {
        if (width(s, style) <= max) {
            return s;
        }
        int n = s.length();
        while (n > 0 && width(s.substring(0, n) + "…", style) > max) {
            n--;
        }
        return s.substring(0, n) + "…";
    }
}
