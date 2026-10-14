package io.github.matheusanbs.wirelessautomate.client;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * As sugestões da busca da Lista do Tablet (lógica pura): o texto digitado casa com os nomes das máquinas,
 * das redes e dos nós, sem diferenciar maiúsculas nem acentos. Em cada grupo vêm primeiro as que começam
 * com o texto, depois as que pegam mais nós e por fim pelo nome, no máximo {@link #PER_GROUP}; cada uma
 * leva o trecho casado (início e fim no nome original) para a tela destacar.
 */
public final class TabletSearch {
    /** Sugestões por grupo. */
    public static final int PER_GROUP = 3;

    /** Os grupos das sugestões, na ordem em que aparecem. */
    public enum Group { MACHINES, NETWORKS, NAMES }

    /** Um valor que a busca pode sugerir: o id (o que vira etiqueta), o nome mostrado e quantos nós ele pega. */
    public record Candidate<T>(T id, String label, int count) {
    }

    /**
     * Uma sugestão: de que grupo, o valor, o nome, quantos nós pega e o trecho casado, de {@code start}
     * (inclusive) a {@code end} (exclusive) em {@code label}.
     */
    public record Suggestion<T>(Group group, T id, String label, int count, int start, int end) {
    }

    private TabletSearch() {
    }

    /**
     * O texto em minúsculas e sem acentos, letra a letra: o resultado tem o mesmo tamanho, então uma posição
     * nele vale no texto original.
     */
    public static String fold(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
            out.append(base.length() == 1 ? base.charAt(0) : Character.toLowerCase(c));
        }
        return out.toString();
    }

    /** As sugestões dos três grupos, na ordem Máquinas, Redes e Nomes; vazio com o texto vazio. */
    public static List<Suggestion<?>> suggest(String text, List<? extends Candidate<?>> machines,
            List<? extends Candidate<?>> networks, List<? extends Candidate<?>> names) {
        String needle = fold(text.strip());
        List<Suggestion<?>> result = new ArrayList<>();
        if (needle.isEmpty()) {
            return result;
        }
        result.addAll(group(Group.MACHINES, needle, machines));
        result.addAll(group(Group.NETWORKS, needle, networks));
        result.addAll(group(Group.NAMES, needle, names));
        return result;
    }

    private static List<Suggestion<?>> group(Group group, String needle, List<? extends Candidate<?>> candidates) {
        List<Suggestion<?>> found = new ArrayList<>();
        for (Candidate<?> candidate : candidates) {
            int start = fold(candidate.label()).indexOf(needle);
            if (start >= 0) {
                found.add(suggestion(group, candidate, start, start + needle.length()));
            }
        }
        found.sort(Comparator.<Suggestion<?>>comparingInt(s -> s.start() == 0 ? 0 : 1)
                .thenComparing(Comparator.<Suggestion<?>>comparingInt(Suggestion::count).reversed())
                .thenComparing(s -> fold(s.label())));
        return found.size() > PER_GROUP ? found.subList(0, PER_GROUP) : found;
    }

    private static <T> Suggestion<T> suggestion(Group group, Candidate<T> candidate, int start, int end) {
        return new Suggestion<>(group, candidate.id(), candidate.label(), candidate.count(), start, end);
    }

    /** Os ids de todos os candidatos cujo nome contém o texto, na ordem recebida; vazio com o texto vazio. */
    public static <T> List<T> matchingIds(String text, List<Candidate<T>> candidates) {
        String needle = fold(text.strip());
        List<T> ids = new ArrayList<>();
        if (needle.isEmpty()) {
            return ids;
        }
        for (Candidate<T> candidate : candidates) {
            if (fold(candidate.label()).contains(needle)) {
                ids.add(candidate.id());
            }
        }
        return ids;
    }
}
