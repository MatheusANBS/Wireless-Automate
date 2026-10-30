package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Monta as cadeias de compactação a partir dos pares conferidos (menor, maior, fator 4 ou 9). Cada forma
 * sobe por uma ligação só: a 3×3 antes da 2×2 e, no empate, o maior primeiro na ordem dada. Cada maior aceita
 * um menor só (o primeiro na ordem). Ciclos são cortados. A ordem dada torna o resultado determinístico.
 * Puro, com JUnit.
 */
public final class ChainGraph<T> {
    public record Chain<T>(List<T> forms, int[] factors) {
    }

    private record Up<T>(T big, int factor) {
    }

    private final Comparator<T> order;
    private final Map<T, Up<T>> up = new HashMap<>();

    public ChainGraph(Comparator<T> order) {
        this.order = order;
    }

    public void link(T small, T big, int factor) {
        if (small.equals(big)) {
            return;
        }
        Up<T> current = up.get(small);
        if (current == null || factor > current.factor()
                || (factor == current.factor() && order.compare(big, current.big()) < 0)) {
            up.put(small, new Up<>(big, factor));
        }
    }

    public List<Chain<T>> chains() {
        // Cada maior fica com um menor só: o primeiro na ordem.
        Map<T, T> down = new HashMap<>();
        TreeSet<T> smalls = new TreeSet<>(order);
        smalls.addAll(up.keySet());
        for (T small : smalls) {
            down.putIfAbsent(up.get(small).big(), small);
        }
        List<Chain<T>> out = new ArrayList<>();
        Set<T> used = new HashSet<>();
        for (T start : smalls) {
            if (down.containsKey(start) && down.get(start) != null && !isCycleRoot(start, down)) {
                continue;
            }
            if (used.contains(start)) {
                continue;
            }
            List<T> forms = new ArrayList<>();
            List<Integer> factors = new ArrayList<>();
            forms.add(start);
            factors.add(1);
            used.add(start);
            T at = start;
            while (true) {
                Up<T> next = up.get(at);
                if (next == null || used.contains(next.big()) || !at.equals(down.get(next.big()))) {
                    break;
                }
                forms.add(next.big());
                factors.add(next.factor());
                used.add(next.big());
                at = next.big();
            }
            if (forms.size() >= 2) {
                int[] f = new int[factors.size()];
                for (int i = 0; i < f.length; i++) {
                    f[i] = factors.get(i);
                }
                out.add(new Chain<>(List.copyOf(forms), f));
            }
        }
        return out;
    }

    /** Num ciclo ninguém fica sem "de baixo": a raiz é o menor do ciclo na ordem. */
    private boolean isCycleRoot(T start, Map<T, T> down) {
        Set<T> seen = new HashSet<>();
        T at = start;
        while (at != null && seen.add(at)) {
            at = down.get(at);
        }
        if (at == null) {
            return false;
        }
        T min = at;
        T walk = down.get(at);
        while (walk != null && !walk.equals(at)) {
            if (order.compare(walk, min) < 0) {
                min = walk;
            }
            walk = down.get(walk);
        }
        return min.equals(start);
    }
}
