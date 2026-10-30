package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Junta as cadeias que são variantes do mesmo material (a prata crua de cada mod): o mesmo número de formas, os mesmos
 * fatores e, em cada nível, pelo menos uma tag específica do namespace {@code c} em comum ({@link #specific}). Os
 * grupos se formam por união (A ~ B e B ~ C juntam os três). Puro, com JUnit.
 *
 * <p>Custo: as cadeias são separadas em baldes pela forma (fatores) e por uma tag da base; só as do mesmo balde são
 * comparadas nível a nível. Roda uma vez por carga de receitas.
 */
public final class VariantGroups {
    /** Uma cadeia vista por fora: os fatores e, por nível, as tags da forma em texto ({@code c:ingots/silver}). */
    public record Shape(int[] factors, List<Set<String>> tags) {
    }

    private VariantGroups() {
    }

    /** A tag conta para juntar variantes: do namespace {@code c} e com {@code /} (as tags-pai, como {@code c:ingots}, não). */
    public static boolean specific(String tag) {
        return tag.startsWith("c:") && tag.indexOf('/', 2) > 2;
    }

    /**
     * Os grupos com mais de uma cadeia. Cada grupo traz os índices das cadeias em ordem crescente, e os grupos vêm na
     * ordem do menor índice: o resultado só depende da ordem de entrada.
     */
    public static List<int[]> of(List<Shape> chains) {
        int[] parent = new int[chains.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        Map<String, List<Integer>> buckets = new HashMap<>();
        for (int i = 0; i < chains.size(); i++) {
            Shape shape = chains.get(i);
            if (shape.tags().size() != shape.factors().length) {
                continue;
            }
            String form = Arrays.toString(shape.factors());
            for (String tag : shape.tags().get(0)) {
                if (specific(tag)) {
                    buckets.computeIfAbsent(form + tag, k -> new ArrayList<>(2)).add(i);
                }
            }
        }
        for (List<Integer> bucket : buckets.values()) {
            for (int a = 0; a < bucket.size(); a++) {
                for (int b = a + 1; b < bucket.size(); b++) {
                    int x = bucket.get(a);
                    int y = bucket.get(b);
                    if (find(parent, x) != find(parent, y) && allLevelsShare(chains.get(x), chains.get(y))) {
                        union(parent, x, y);
                    }
                }
            }
        }
        Map<Integer, List<Integer>> byRoot = new HashMap<>();
        List<Integer> roots = new ArrayList<>();
        for (int i = 0; i < parent.length; i++) {
            int root = find(parent, i);
            List<Integer> members = byRoot.get(root);
            if (members == null) {
                members = new ArrayList<>(2);
                byRoot.put(root, members);
                roots.add(root);
            }
            members.add(i);
        }
        List<int[]> groups = new ArrayList<>();
        for (int root : roots) {
            List<Integer> members = byRoot.get(root);
            if (members.size() > 1) {
                groups.add(members.stream().mapToInt(Integer::intValue).toArray());
            }
        }
        return groups;
    }

    private static boolean allLevelsShare(Shape a, Shape b) {
        if (!Arrays.equals(a.factors(), b.factors())) {
            return false;
        }
        for (int level = 0; level < a.tags().size(); level++) {
            boolean shared = false;
            for (String tag : a.tags().get(level)) {
                if (specific(tag) && b.tags().get(level).contains(tag)) {
                    shared = true;
                    break;
                }
            }
            if (!shared) {
                return false;
            }
        }
        return true;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** A raiz fica sempre com o menor índice. */
    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra < rb) {
            parent[rb] = ra;
        } else if (rb < ra) {
            parent[ra] = rb;
        }
    }
}
