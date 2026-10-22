package io.github.matheusanbs.wirelessautomate.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A escolha das sugestões do campo de convidado e de time: filtra pelo texto e ordena. Lógica pura, testada por
 * JUnit. Roda só quando o jogador digita (uma consulta por pausa na digitação), nunca num caminho quente.
 */
public final class AccessSuggestions {
    /** As linhas que uma consulta devolve, no máximo. */
    public static final int MAX = 8;

    /**
     * Um nome que pode ser sugerido.
     *
     * @param online quem está no jogo agora; vem antes de quem só já entrou no servidor
     * @param taken  já está na rede: aparece, apagado, depois de todos os outros
     */
    public record Candidate(UUID id, String name, boolean online, boolean taken) {
    }

    private static final Comparator<Candidate> BY_NAME = Comparator
            .comparing((Candidate c) -> c.name().toLowerCase(Locale.ROOT))
            .thenComparing(Candidate::id);

    private AccessSuggestions() {
    }

    /**
     * Até {@code max} candidatos cujo nome contém o texto (sem diferenciar maiúsculas, com as pontas aparadas;
     * texto vazio pega todos). Ordem: quem ainda não está na rede, quem começa com o texto, quem está online, o
     * nome e, no empate, o id.
     */
    public static List<Candidate> pick(Collection<Candidate> all, String text, int max) {
        String needle = text.strip().toLowerCase(Locale.ROOT);
        List<Candidate> matches = new ArrayList<>();
        for (Candidate candidate : all) {
            if (candidate.name().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(candidate);
            }
        }
        matches.sort(Comparator
                .comparing(Candidate::taken)
                .thenComparing((Candidate c) -> !c.name().toLowerCase(Locale.ROOT).startsWith(needle))
                .thenComparing((Candidate c) -> !c.online())
                .thenComparing(BY_NAME));
        return matches.size() > max ? new ArrayList<>(matches.subList(0, max)) : matches;
    }
}
