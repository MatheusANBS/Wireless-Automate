package io.github.matheusanbs.wirelessautomate.tv;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A busca da grade de itens do Baú ligado na aba Dado: palavras que precisam estar todas no nome ou no id do item e
 * {@code @mod} para o namespace, sem acento nem maiúscula ({@code @minecraft iron}). Lógica pura, com JUnit; o
 * servidor a roda uma vez por pedido.
 */
public record TvItemSearch(List<String> words, List<String> mods) {
    public static final int MAX_QUERY = 48;
    public static final TvItemSearch EMPTY = new TvItemSearch(List.of(), List.of());

    /** Minúsculas e sem acentos. */
    public static String fold(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (char c : Normalizer.normalize(lower, Normalizer.Form.NFD).toCharArray()) {
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static TvItemSearch parse(String query) {
        String text = fold(query.length() > MAX_QUERY ? query.substring(0, MAX_QUERY) : query).trim();
        if (text.isEmpty()) {
            return EMPTY;
        }
        List<String> words = new ArrayList<>();
        List<String> mods = new ArrayList<>();
        for (String word : text.split("\s+")) {
            if (word.startsWith("@")) {
                if (word.length() > 1) {
                    mods.add(word.substring(1));
                }
            } else {
                words.add(word);
            }
        }
        return words.isEmpty() && mods.isEmpty() ? EMPTY : new TvItemSearch(List.copyOf(words), List.copyOf(mods));
    }

    public boolean isEmpty() {
        return words.isEmpty() && mods.isEmpty();
    }

    /** Se o item casa: o mod pelo namespace e cada palavra no nome ou no caminho do id. */
    public boolean matches(String name, String namespace, String path) {
        String foldedNamespace = fold(namespace);
        for (String mod : mods) {
            if (!foldedNamespace.contains(mod)) {
                return false;
            }
        }
        if (words.isEmpty()) {
            return true;
        }
        String foldedName = fold(name);
        String foldedPath = fold(path);
        for (String word : words) {
            if (!foldedName.contains(word) && !foldedPath.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
