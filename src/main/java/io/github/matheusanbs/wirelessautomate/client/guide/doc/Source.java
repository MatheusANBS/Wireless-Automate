package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.ArrayList;
import java.util.List;

/** As linhas do texto com um cursor e a lista de avisos; os leitores consomem daqui. */
final class Source {
    private final List<String> lines;
    private final List<String> warnings = new ArrayList<>();
    private int pos;

    Source(String text) {
        String clean = text.startsWith("﻿") ? text.substring(1) : text;
        this.lines = clean.lines().toList();
    }

    boolean hasNext() {
        return pos < lines.size();
    }

    /** A próxima linha sem consumir, ou {@code null} no fim. */
    String peek() {
        return pos < lines.size() ? lines.get(pos) : null;
    }

    String next() {
        return lines.get(pos++);
    }

    /** O aviso leva o número da linha que acabou de ser lida (1 a partir do topo). */
    void warn(String message) {
        warnings.add("linha " + Math.max(pos, 1) + ": " + message);
    }

    List<String> warnings() {
        return warnings;
    }
}
