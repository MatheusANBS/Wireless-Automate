package io.github.matheusanbs.wirelessautomate.client.guide.layout;

import io.github.matheusanbs.wirelessautomate.client.guide.doc.Span;
import java.util.ArrayList;
import java.util.List;

/**
 * Quebra os trechos de um parágrafo, item de lista ou célula em linhas, uma por chamada de {@link #next}: quem chama
 * diz a largura de cada linha (a de ao lado de um ícone flutuante é menor). Mede uma vez, na criação, por palavra.
 */
final class TextWrap {
    /** Um pedaço indivisível de uma palavra: um texto num estilo, um ícone ou uma imagem. */
    record Atom(Line.Kind kind, String text, TextStyle style, String page, int w, int h, String id, double scale,
            String components) {}

    private static final class Word {
        final List<Atom> atoms = new ArrayList<>(2);
        boolean spaceBefore;
        int width;

        void add(Atom a) {
            atoms.add(a);
            width += a.w();
        }
    }

    private final TextMeasure measure;
    private final TextStyle base;
    private final double scale;
    private final int textH;
    private final List<Word> words = new ArrayList<>();
    private int next;
    private Word cur;
    private boolean pendingSpace;

    TextWrap(List<Span> spans, TextStyle base, double scale, TextMeasure measure) {
        this.measure = measure;
        this.base = base;
        this.scale = scale;
        this.textH = (int) Math.ceil(measure.lineHeight() * scale);
        for (Span span : spans) {
            add(span);
        }
        endWord();
    }

    /** A altura de uma linha só de texto neste estilo. */
    int textHeight() {
        return textH;
    }

    private TextStyle emphasis(TextStyle s) {
        return base.isTitle() ? base : s;
    }

    private void add(Span span) {
        switch (span) {
            case Span.Text t -> text(t.text(), base, null);
            case Span.Bold b -> text(b.text(), emphasis(TextStyle.BOLD), null);
            case Span.Code c -> text(c.text(), emphasis(TextStyle.CODE), null);
            case Span.Link l -> text(l.text(), emphasis(TextStyle.LINK), l.page());
            case Span.ItemIcon i -> icon(i.id(), i.scale(), i.components());
            case Span.ItemLink i -> icon(i.id(), 1.0, "");
            case Span.InlineImage i -> {
                TextMeasure.Size s = measure.imageSize(i.path());
                addAtom(new Atom(Line.Kind.IMAGE, i.path(), TextStyle.NORMAL, null, s.w(), s.h(), null, 1.0, null));
            }
        }
    }

    private void icon(String id, double iconScale, String components) {
        int side = (int) Math.round(GuideMetrics.ICON * iconScale);
        addAtom(new Atom(Line.Kind.ICON, null, TextStyle.NORMAL, null, side, side, id, iconScale, components));
    }

    private int textWidth(String text, TextStyle style) {
        return (int) Math.ceil(measure.width(text, style) * scale);
    }

    private void text(String t, TextStyle style, String page) {
        int i = 0;
        int n = t.length();
        while (i < n) {
            if (Character.isWhitespace(t.charAt(i))) {
                while (i < n && Character.isWhitespace(t.charAt(i))) {
                    i++;
                }
                endWord();
                pendingSpace = true;
                continue;
            }
            int j = i;
            while (j < n && !Character.isWhitespace(t.charAt(j))) {
                j++;
            }
            String piece = t.substring(i, j);
            addAtom(textAtom(piece, style, page));
            i = j;
        }
    }

    private Atom textAtom(String piece, TextStyle style, String page) {
        return new Atom(Line.Kind.TEXT, piece, style, page, textWidth(piece, style), textH, null, scale, null);
    }

    private void addAtom(Atom a) {
        if (cur == null) {
            cur = new Word();
            cur.spaceBefore = pendingSpace;
            pendingSpace = false;
        }
        cur.add(a);
    }

    private void endWord() {
        if (cur != null) {
            words.add(cur);
            cur = null;
        }
    }

    private int spaceWidth(Word w) {
        Atom first = w.atoms.get(0);
        return textWidth(" ", first.kind() == Line.Kind.TEXT ? first.style() : TextStyle.NORMAL);
    }

    /** A próxima linha com no máximo {@code width} unidades (uma palavra mais larga que a linha é cortada), ou nula no fim. */
    Line next(int width) {
        if (next >= words.size()) {
            return null;
        }
        Line line = new Line(scale);
        int x = 0;
        while (next < words.size()) {
            Word w = words.get(next);
            int gap = x > 0 && w.spaceBefore ? spaceWidth(w) : 0;
            if (x > 0 && x + gap + w.width > width) {
                break;
            }
            if (x == 0 && w.width > width) {
                split(next, width);
                w = words.get(next);
            }
            for (Atom a : w.atoms) {
                x = line.add(a, x, gap);
                gap = 0;
            }
            next++;
        }
        return line;
    }

    /** Parte a palavra {@code idx} em duas: a que cabe em {@code width} (ao menos um caractere) e o resto. */
    private void split(int idx, int width) {
        Word w = words.get(idx);
        Word head = new Word();
        head.spaceBefore = w.spaceBefore;
        Word rest = new Word();
        int i = 0;
        for (; i < w.atoms.size(); i++) {
            Atom a = w.atoms.get(i);
            if (head.width + a.w() <= width) {
                head.add(a);
                continue;
            }
            if (a.kind() == Line.Kind.TEXT) {
                int k = fit(a, width - head.width);
                if (head.atoms.isEmpty()) {
                    k = Math.max(k, 1);
                }
                k = Math.min(k, a.text().length() - 1);
                if (k <= 0 && head.atoms.isEmpty()) {
                    head.add(a);
                    i++;
                    break;
                }
                if (k > 0) {
                    head.add(textAtom(a.text().substring(0, k), a.style(), a.page()));
                    a = textAtom(a.text().substring(k), a.style(), a.page());
                }
                rest.add(a);
            } else if (head.atoms.isEmpty()) {
                head.add(a);
            } else {
                rest.add(a);
            }
            i++;
            break;
        }
        for (; i < w.atoms.size(); i++) {
            rest.add(w.atoms.get(i));
        }
        words.set(idx, head);
        if (!rest.atoms.isEmpty()) {
            words.add(idx + 1, rest);
        }
    }

    /** Quantos caracteres do texto do átomo cabem em {@code room}. */
    private int fit(Atom a, int room) {
        String t = a.text();
        int k = 0;
        while (k < t.length() && textWidth(t.substring(0, k + 1), a.style()) <= room) {
            k++;
        }
        return k;
    }
}
