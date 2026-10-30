package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.ArrayList;
import java.util.List;

/** Lê uma tabela de linhas {@code | a | b |}: cabeçalho, separador {@code ---} e linhas de células. */
final class TableParser {
    private TableParser() {}

    static Block.Table parse(List<String> lines, Source src) {
        List<List<Span>> header = cells(lines.get(0), src);
        int first = lines.size() > 1 && isSeparator(lines.get(1)) ? 2 : 1;
        List<List<List<Span>>> rows = new ArrayList<>();
        for (int i = first; i < lines.size(); i++) {
            List<List<Span>> row = cells(lines.get(i), src);
            if (row.size() != header.size()) {
                src.warn("linha de tabela com " + row.size() + " células; o cabeçalho tem " + header.size());
            }
            rows.add(row);
        }
        return new Block.Table(header, rows);
    }

    private static List<List<Span>> cells(String line, Source src) {
        List<List<Span>> out = new ArrayList<>();
        for (String cell : split(line)) {
            out.add(InlineParser.parse(cell.strip(), src));
        }
        return out;
    }

    /** Parte nas barras, menos as dentro de código e as escapadas ({@code \|}). */
    static List<String> split(String line) {
        String s = line.strip();
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean code = false;
        for (int i = s.startsWith("|") ? 1 : 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == '|') {
                cur.append('|');
                i++;
            } else if (c == '|' && !code) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                if (c == '`') {
                    code = !code;
                }
                cur.append(c);
            }
        }
        if (!cur.toString().isBlank()) {
            out.add(cur.toString());
        }
        return out;
    }

    private static boolean isSeparator(String line) {
        List<String> cells = split(line);
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            String t = cell.strip();
            if (t.isEmpty() || t.indexOf('-') < 0) {
                return false;
            }
            for (int i = 0; i < t.length(); i++) {
                if ("-:".indexOf(t.charAt(i)) < 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
