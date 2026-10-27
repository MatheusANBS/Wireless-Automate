package io.github.matheusanbs.wirelessautomate.client.router;

import java.text.Normalizer;
import java.util.Locale;

/**
 * A busca das listas de redes (a do painel do nó e a da tela da Tela de Sinal): sem acento nem maiúscula, por
 * trecho do nome. Pura, com JUnit. Cada caractere vira o seu correspondente sem acento sozinho, então o texto dobrado
 * tem o mesmo tamanho do original e a posição achada vale para desenhar o trecho em destaque no nome.
 */
public final class NetworkSearch {
    private NetworkSearch() {
    }

    /** {@code text} sem acentos e em minúsculas, com o mesmo tamanho. */
    public static String fold(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            String decomposed = Normalizer.normalize(String.valueOf(text.charAt(i)), Normalizer.Form.NFD);
            out.append(decomposed.charAt(0));
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /** Onde {@code foldedQuery} (já dobrado) aparece em {@code name}, ou -1; a busca vazia acha tudo, no 0. */
    public static int find(String name, String foldedQuery) {
        return fold(name).indexOf(foldedQuery);
    }
}
