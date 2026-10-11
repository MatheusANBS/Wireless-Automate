package io.github.matheusanbs.wirelessautomate.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A busca da lista dos armazenamentos, separada em partes: palavras do nome, {@code @mod} e
 * {@code #tag}. O {@code #} vale até o próximo {@code @} ou {@code #}, para procurar uma frase do
 * tooltip ({@code #not yet eaten}); as palavras antes do primeiro {@code #} são do nome. Lógica pura.
 */
public record StorageSearch(List<String> words, List<String> mods, List<String> tags) {
    public static final StorageSearch EMPTY = new StorageSearch(List.of(), List.of(), List.of());

    public static StorageSearch parse(String search) {
        String text = search.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return EMPTY;
        }
        List<String> words = new ArrayList<>();
        List<String> mods = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        StringBuilder tag = null;
        for (String word : text.split("\\s+")) {
            if (word.startsWith("@")) {
                tag = close(tag, tags);
                if (word.length() > 1) {
                    mods.add(word.substring(1));
                }
            } else if (word.startsWith("#")) {
                close(tag, tags);
                tag = new StringBuilder(word.substring(1));
            } else if (tag != null) {
                if (!tag.isEmpty()) {
                    tag.append(' ');
                }
                tag.append(word);
            } else {
                words.add(word);
            }
        }
        close(tag, tags);
        return new StorageSearch(List.copyOf(words), List.copyOf(mods), List.copyOf(tags));
    }

    private static StringBuilder close(StringBuilder tag, List<String> tags) {
        if (tag != null && !tag.isEmpty()) {
            tags.add(tag.toString());
        }
        return null;
    }

    public boolean isEmpty() {
        return words.isEmpty() && mods.isEmpty() && tags.isEmpty();
    }
}
