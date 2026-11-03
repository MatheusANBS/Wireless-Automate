package io.github.matheusanbs.wirelessautomate.client.guide;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Onde o livro está e por onde passou nesta sessão: a página aberta ({@code null} = folha de rosto e índice), o par de
 * folhas à vista e o histórico do voltar. Estático, para o livro reabrir onde parou; solto na saída do mundo.
 */
final class GuideNav {
    /** Um lugar do livro: a página ({@code null} = índice) e o par de folhas. */
    record Place(@Nullable String page, int spread) {
    }

    private static final int HISTORY_MAX = 64;
    private static final Deque<Place> HISTORY = new ArrayDeque<>();
    private static @Nullable String page;
    private static int spread;

    private GuideNav() {
    }

    static @Nullable String page() {
        return page;
    }

    static int spread() {
        return spread;
    }

    /** Vai a outro lugar guardando o atual no histórico (se for outro). */
    static void go(@Nullable String to, int toSpread) {
        if (Objects.equals(to, page) && toSpread == spread) {
            return;
        }
        HISTORY.push(new Place(page, spread));
        while (HISTORY.size() > HISTORY_MAX) {
            HISTORY.removeLast();
        }
        page = to;
        spread = toSpread;
    }

    /** Vira as folhas dentro da mesma página, sem passar pelo histórico. */
    static void turnTo(int toSpread) {
        spread = toSpread;
    }

    /** Volta ao lugar anterior; {@code false} se o histórico está vazio. */
    static boolean back() {
        Place p = HISTORY.poll();
        if (p == null) {
            return false;
        }
        page = p.page();
        spread = p.spread();
        return true;
    }

    /** Esquece tudo (saída do mundo): a próxima abertura é a folha de rosto. */
    static void reset() {
        HISTORY.clear();
        page = null;
        spread = 0;
    }
}
