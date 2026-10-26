package io.github.matheusanbs.wirelessautomate.client.dev;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * O tempo do e2e por suíte e os passos mais lentos, para o fim do {@code result.txt}. O que vem antes do primeiro
 * marcador de suíte (a abertura e as redes de B, quando uma suíte as pede) conta como {@code abertura}.
 */
final class E2eTiming {
    /** O começo do nome do passo que abre uma suíte. */
    static final String MARKER = "== suíte ";

    private static final int SLOWEST = 10;

    private static final class Totals {
        int steps;
        int captures;
        long ms;
    }

    private final Map<String, Totals> suites = new LinkedHashMap<>();
    private final List<Map.Entry<String, Long>> slowest = new ArrayList<>();
    private String current = "abertura";

    /** Soma um passo concluído à suíte em curso; um marcador troca a suíte. */
    void step(String name, long ms) {
        if (name.startsWith(MARKER)) {
            current = name.substring(MARKER.length(), name.length() - 3);
            suites.computeIfAbsent(current, k -> new Totals());
            return;
        }
        Totals totals = suites.computeIfAbsent(current, k -> new Totals());
        totals.steps++;
        totals.ms += ms;
        if (name.startsWith("captura ") && !name.endsWith("(pulada)")) {
            totals.captures++;
        }
        slowest.add(Map.entry(name, ms));
    }

    /** As linhas "Tempo por suíte" e "10 passos mais lentos". */
    List<String> summary() {
        List<String> lines = new ArrayList<>();
        lines.add("# Tempo por suíte (nome, passos, segundos, capturas)");
        long totalMs = 0;
        int totalSteps = 0;
        int totalCaptures = 0;
        for (Map.Entry<String, Totals> entry : suites.entrySet()) {
            Totals t = entry.getValue();
            lines.add(String.format("%s: %d passos, %.1f s, %d capturas", entry.getKey(), t.steps, t.ms / 1000.0,
                    t.captures));
            totalMs += t.ms;
            totalSteps += t.steps;
            totalCaptures += t.captures;
        }
        lines.add(String.format("total: %d passos, %.1f s, %d capturas", totalSteps, totalMs / 1000.0, totalCaptures));
        lines.add("# 10 passos mais lentos");
        slowest.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()));
        for (int i = 0; i < Math.min(SLOWEST, slowest.size()); i++) {
            lines.add(String.format("%.1f s  %s", slowest.get(i).getValue() / 1000.0, slowest.get(i).getKey()));
        }
        return lines;
    }
}
