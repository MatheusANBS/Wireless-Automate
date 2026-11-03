package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * O filtro da grade de ícones: o texto (sem acento e sem caixa) dentro do id ou do nome do item. A ordem põe primeiro o
 * que é exatamente o texto (o caminho do id ou o nome), depois o que começa por ele, depois o resto, cada parte na
 * ordem do registro: "diamond" dá o diamante antes do minério de diamante. Pura, com JUnit.
 */
final class IconSearch {
    static final int MIN_QUERY = 2;
    static final int MAX_RESULTS = 256;
    private static final Pattern MARKS = Pattern.compile("\\p{M}");

    private IconSearch() {
    }

    static boolean searches(String query) {
        return query.strip().length() >= MIN_QUERY;
    }

    static boolean matches(String query, String id, String name) {
        return rank(fold(query.strip()), fold(id), fold(name)) >= 0;
    }

    /** Minúsculas e sem acento; texto só ASCII não passa pelo {@link Normalizer}. */
    static String fold(String text) {
        for (int i = 0, n = text.length(); i < n; i++) {
            if (text.charAt(i) >= 0x80) {
                return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("")
                        .toLowerCase(Locale.ROOT);
            }
        }
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * Os índices dos itens que casam com {@code query}, na ordem da grade, no máximo {@code max}. {@code ids} e
     * {@code names} já vêm dobrados ({@link #fold}); só a consulta é dobrada aqui, uma vez.
     */
    static int[] order(String query, String[] ids, String[] names, int max) {
        String q = fold(query.strip());
        int[][] buckets = new int[3][max];
        int[] counts = new int[3];
        for (int i = 0; i < ids.length && counts[0] < max; i++) {
            int r = rank(q, ids[i], names[i]);
            if (r >= 0 && counts[r] < max) {
                buckets[r][counts[r]++] = i;
            }
        }
        int total = Math.min(max, counts[0] + counts[1] + counts[2]);
        int[] out = new int[total];
        int at = 0;
        for (int b = 0; b < 3 && at < total; b++) {
            int n = Math.min(counts[b], total - at);
            System.arraycopy(buckets[b], 0, out, at, n);
            at += n;
        }
        return out;
    }

    /** 0: o caminho do id ou o nome é o texto; 1: começa por ele; 2: contém; -1: não casa. Tudo já dobrado. */
    private static int rank(String q, String id, String name) {
        int path = id.indexOf(':') + 1;
        int qn = q.length();
        boolean pathStarts = id.regionMatches(path, q, 0, qn);
        if (pathStarts && id.length() - path == qn || name.equals(q) || id.equals(q)) {
            return 0;
        }
        if (pathStarts || name.startsWith(q) || id.startsWith(q)) {
            return 1;
        }
        return id.contains(q) || name.contains(q) ? 2 : -1;
    }
}
