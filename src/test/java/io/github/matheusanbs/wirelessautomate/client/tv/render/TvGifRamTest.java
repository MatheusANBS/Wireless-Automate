package io.github.matheusanbs.wirelessautomate.client.tv.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvGifRamTest {
    private static final long MB = 1L << 20;

    @Test
    void variosPedidosJuntosNaoPassamDoTeto() {
        TvGifRam ram = new TvGifRam(256 * MB);
        long opened = 0;
        for (int i = 0; i < 8; i++) {
            opened += ram.reserve(200 * MB);
        }
        assertEquals(200 * MB, opened, "só o primeiro GIF de 200 MB abre inteiro; os outros vão comprimidos");
        assertEquals(56 * MB, ram.room());
    }

    @Test
    void reservaViraUsoDeVerdadeNaVolta() {
        TvGifRam ram = new TvGifRam(256 * MB);
        long reserved = ram.reserve(100 * MB);
        ram.release(reserved);
        ram.use(90 * MB);
        assertEquals(90 * MB, ram.used(), "a animação conta pelo tamanho decodificado");
        ram.release(90 * MB);
        assertEquals(0, ram.used(), "o GIF solto devolve tudo");
    }

    @Test
    void falhaOuComprimidoDevolveAReserva() {
        TvGifRam ram = new TvGifRam(256 * MB);
        long reserved = ram.reserve(256 * MB);
        assertEquals(0, ram.reserve(1), "o teto inteiro reservado: nada mais abre inteiro");
        ram.release(reserved);
        assertEquals(256 * MB, ram.room(), "a decodificação que falhou devolve a reserva");
    }

    @Test
    void naoGifOuMaiorQueOTetoNaoReserva() {
        TvGifRam ram = new TvGifRam(256 * MB);
        assertEquals(0, ram.reserve(0), "imagem parada");
        assertEquals(0, ram.reserve(300 * MB), "maior que o teto vai comprimido");
        assertEquals(0, ram.used());
    }

    @Test
    void devolverAMaisNaoFicaNegativo() {
        TvGifRam ram = new TvGifRam(256 * MB);
        ram.release(10 * MB);
        assertEquals(0, ram.used());
        assertEquals(256 * MB, ram.room());
    }
}
