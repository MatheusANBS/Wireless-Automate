package io.github.matheusanbs.wirelessautomate.client.tv.render;

/**
 * A conta do teto de memória dos GIFs abertos com todos os quadros (pura, com JUnit). A memória é reservada na hora
 * do pedido ({@link #reserve}), antes de a decodificação começar, para vários GIFs pedidos no mesmo quadro não verem
 * todos o teto inteiro livre; quando a decodificação volta, a reserva é devolvida ({@link #release}) e a animação
 * aberta inteira passa a contar pelo tamanho de verdade ({@link #use}), até ser solta.
 */
final class TvGifRam {
    private final long budget;
    private long used;

    TvGifRam(long budget) {
        this.budget = budget;
    }

    /**
     * Reserva {@code bytes} se ainda cabem no teto; devolve o reservado ({@code bytes} ou 0, que é abrir o GIF
     * comprimido).
     */
    long reserve(long bytes) {
        if (bytes <= 0 || bytes > budget - used) {
            return 0;
        }
        used += bytes;
        return bytes;
    }

    /** Conta {@code bytes} já abertos (a animação inteira), mesmo acima do teto. */
    void use(long bytes) {
        used += bytes;
    }

    /** Devolve uma reserva ou a memória de uma animação solta. */
    void release(long bytes) {
        used = Math.max(0, used - bytes);
    }

    /** A memória que ainda cabe no teto. */
    long room() {
        return Math.max(0, budget - used);
    }

    long used() {
        return used;
    }
}
