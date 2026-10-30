package io.github.matheusanbs.wirelessautomate.client.storage.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Os nomes do painel da Compactação a partir dos nomes e ids das formas de uma cadeia, sem classes do Minecraft: o
 * nome da cadeia (as palavras que todas as formas têm: "Ferro" de "Pepita de Ferro", "Lingote de Ferro" e "Bloco de
 * Ferro"), o nome curto de cada forma (o que sobra: "Pepita", "Lingote", "Bloco") e o rótulo do canto das escadas de
 * compressão ("3x" de um id que termina em {@code _3x}).
 */
final class ChainNames {
    /** Conectivos que não ficam sozinhos na ponta de um nome ("Bloco de", "Block of"). */
    private static final Set<String> JOINERS = Set.of("de", "do", "da", "dos", "das", "of", "the", "del", "di", "du");
    private static final Pattern CORNER = Pattern.compile("(?:^|_)(\\d{1,2})x$");

    private ChainNames() {
    }

    /** O rótulo do canto ("3x") se o caminho do id termina em {@code _<n>x}; senão {@code null}. */
    static @Nullable String corner(String path) {
        Matcher matcher = CORNER.matcher(path);
        return matcher.find() ? matcher.group(1) + "x" : null;
    }

    /** Uma escada de compressão: toda forma acima da base tem rótulo de canto. */
    static boolean ladder(@Nullable String[] corners) {
        if (corners.length < 2) {
            return false;
        }
        for (int i = 1; i < corners.length; i++) {
            if (corners[i] == null) {
                return false;
            }
        }
        return true;
    }

    /** As palavras comuns a todas as formas, na ordem da base, sem conectivo nas pontas; sem nenhuma, o nome da base. */
    static String chainName(List<String> names) {
        List<String> base = words(names.getFirst());
        List<String> common = new ArrayList<>();
        for (String word : base) {
            if (inAll(word, names)) {
                common.add(word);
            }
        }
        String joined = trimJoiners(common);
        return joined.isEmpty() ? names.getFirst() : joined;
    }

    /**
     * Cada nome sem as palavras que todas as formas têm, sem conectivo nas pontas; vazio quando nada sobra. Os nomes
     * voltam inteiros quando o corte tiraria o substantivo: uma forma acima da base fica sem nada ("Clay Ball" e
     * "Clay"), ou na base a palavra que sobra vem colada antes das comuns, como um adjetivo ("Nether Quartz" e "Block
     * of Quartz" dariam "Nether" e "Block"; "Pepita de Ferro" tem o conectivo no meio e "Iron Nugget" começa pelas
     * comuns, e os dois cortam bem).
     */
    static String[] shortNames(List<String> names) {
        String[] out = cut(names);
        boolean whole = modifierFirst(names);
        for (int i = 1; i < out.length && !whole; i++) {
            whole = out[i].isEmpty();
        }
        return whole ? names.toArray(new String[0]) : out;
    }

    /** Na base, a primeira palavra comum (fora os conectivos) vem logo depois de uma palavra que não é comum. */
    private static boolean modifierFirst(List<String> names) {
        List<String> base = words(names.getFirst());
        for (int i = 0; i < base.size(); i++) {
            String word = base.get(i);
            if (!JOINERS.contains(word.toLowerCase(Locale.ROOT)) && inAll(word, names)) {
                if (i == 0) {
                    return false;
                }
                String before = base.get(i - 1);
                return !JOINERS.contains(before.toLowerCase(Locale.ROOT)) && !inAll(before, names);
            }
        }
        return false;
    }

    private static String[] cut(List<String> names) {
        String[] out = new String[names.size()];
        for (int i = 0; i < out.length; i++) {
            List<String> left = new ArrayList<>();
            for (String word : words(names.get(i))) {
                if (!inAll(word, names)) {
                    left.add(word);
                }
            }
            out[i] = trimJoiners(left);
        }
        return out;
    }

    private static boolean inAll(String word, List<String> names) {
        String key = word.toLowerCase(Locale.ROOT);
        for (String name : names) {
            boolean found = false;
            for (String other : words(name)) {
                if (other.toLowerCase(Locale.ROOT).equals(key)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static List<String> words(String name) {
        List<String> out = new ArrayList<>();
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty()) {
                out.add(word);
            }
        }
        return out;
    }

    private static String trimJoiners(List<String> words) {
        int from = 0;
        int to = words.size();
        while (from < to && JOINERS.contains(words.get(from).toLowerCase(Locale.ROOT))) {
            from++;
        }
        while (to > from && JOINERS.contains(words.get(to - 1).toLowerCase(Locale.ROOT))) {
            to--;
        }
        return String.join(" ", words.subList(from, to));
    }
}
