package io.github.matheusanbs.wirelessautomate.client;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * Busca do campo de encantamento da aba Regra e o campo de nível, sem classes do Minecraft (JUnit em
 * {@code EnchantSearchTest}). A busca olha o nome traduzido e o id ({@code fortune},
 * {@code minecraft:fortune}), sem diferença de acentos nem de maiúsculas: primeiro quem começa com o
 * texto (no nome, no caminho do id ou no id inteiro), depois quem só contém; dentro de cada grupo, a
 * ordem alfabética do nome.
 */
public final class EnchantSearch {
    /** O nível mínimo vai de 1 a 255 para qualquer encantamento ({@code ItemRule.Enchant.MAX_LEVEL}). */
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 255;

    private EnchantSearch() {}

    /** Os itens que casam com {@code query}, na ordem da spec; consulta vazia devolve todos em ordem alfabética. */
    public static <T> List<T> search(List<T> all, Function<T, String> name, Function<T, String> id, String query) {
        String q = fold(query == null ? "" : query.strip());
        List<Hit<T>> starts = new ArrayList<>();
        List<Hit<T>> contains = new ArrayList<>();
        for (T item : all) {
            String n = fold(name.apply(item));
            String full = fold(id.apply(item));
            int colon = full.indexOf(':');
            String path = colon < 0 ? full : full.substring(colon + 1);
            Hit<T> hit = new Hit<>(item, n);
            if (n.startsWith(q) || path.startsWith(q) || full.startsWith(q)) {
                starts.add(hit);
            } else if (n.contains(q) || full.contains(q)) {
                contains.add(hit);
            }
        }
        Comparator<Hit<T>> byName = Comparator.comparing(Hit::folded);
        starts.sort(byName);
        contains.sort(byName);
        List<T> out = new ArrayList<>(starts.size() + contains.size());
        starts.forEach(h -> out.add(h.item()));
        contains.forEach(h -> out.add(h.item()));
        return out;
    }

    /** Sem acentos e em minúsculas: "Proteção" vira "protecao". */
    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** O nível digitado: só dígitos, de 1 a 255; senão vazio. */
    public static OptionalInt parseLevel(String text) {
        if (text == null || text.isEmpty() || text.length() > 3) {
            return OptionalInt.empty();
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return OptionalInt.empty();
            }
        }
        int level = Integer.parseInt(text);
        return level < MIN_LEVEL || level > MAX_LEVEL ? OptionalInt.empty() : OptionalInt.of(level);
    }

    /** A roda do mouse no campo de nível: soma {@code delta}, limitado a 1..255. */
    public static int stepLevel(int level, int delta) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level + delta));
    }

    private record Hit<T>(T item, String folded) {}
}
