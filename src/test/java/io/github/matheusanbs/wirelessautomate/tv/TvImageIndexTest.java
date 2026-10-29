package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TvImageIndexTest {
    private static final long GRACE = 10 * 60_000L;
    private static final UUID ALICE = new UUID(1, 1);
    private static final UUID BOB = new UUID(2, 2);

    private static String hash(int n) {
        return TvImageHash.of(new byte[] {(byte) n});
    }

    private static TvImageIndex.Entry entry(int n, long bytes, UUID author, long now) {
        return new TvImageIndex.Entry(hash(n), TvImageFormat.PNG, 10, 10, 1, bytes, author, now);
    }

    @Test
    void usoContaTelasEBibliotecasPelaDiferenca() {
        TvImageIndex<String> index = new TvImageIndex<>();
        index.add(entry(1, 100, ALICE, 0), false, 0);
        index.add(entry(2, 50, BOB, 0), false, 0);
        assertTrue(index.setScreen("a", List.of(hash(1), hash(2)), 10));
        assertTrue(index.setScreen("b", List.of(hash(1)), 10));
        assertFalse(index.setScreen("b", List.of(hash(1)), 20), "a mesma anotação não muda nada");
        index.setLibrary(ALICE, List.of(hash(1)), 10);
        assertEquals(2, index.get(hash(1)).screens());
        assertEquals(1, index.get(hash(1)).libraries());
        assertEquals(1, index.get(hash(2)).uses());
        assertTrue(index.setScreen("a", List.of(hash(1)), 30), "a Tela a parou de usar a 2");
        assertEquals(0, index.get(hash(2)).uses());
        assertEquals(30, index.get(hash(2)).unusedSince());
        assertTrue(index.removeScreen("b", 40));
        assertFalse(index.removeScreen("b", 40), "já tirada");
        assertEquals(2, index.get(hash(1)).uses());
        assertEquals(150, index.totalBytes());
        assertEquals(100, index.bytesOf(ALICE));
    }

    @Test
    void resumoDesconhecidoNaoEAnotado() {
        TvImageIndex<String> index = new TvImageIndex<>();
        assertFalse(index.setScreen("a", List.of(hash(9)), 0), "o índice não tem a imagem");
        assertTrue(index.screens().isEmpty());
        index.add(entry(9, 10, ALICE, 0), false, 0);
        assertEquals(0, index.get(hash(9)).uses(), "a anotação que não entrou não conta depois");
    }

    @Test
    void imagemAnotadaAntesDeChegarContaAoChegar() {
        TvImageIndex<String> index = new TvImageIndex<>();
        index.add(entry(1, 10, ALICE, 0), false, 0);
        index.setScreen("a", List.of(hash(1)), 0);
        index.remove(hash(1));
        assertTrue(index.screens().isEmpty(), "remover tira das anotações");
        index.add(entry(1, 10, ALICE, 5), false, 5);
        assertEquals(0, index.get(hash(1)).uses());
    }

    @Test
    void limpezaSoDepoisDaFolgaComRelogioFalso() {
        TvImageIndex<String> index = new TvImageIndex<>();
        index.add(entry(1, 10, ALICE, 0), false, 0);
        index.add(entry(2, 10, ALICE, 0), false, 0);
        index.setLibrary(ALICE, List.of(hash(2)), 0);
        assertEquals(List.of(), index.expired(GRACE - 1, GRACE, 10), "antes da folga, nada");
        assertEquals(List.of(hash(1)), index.expired(GRACE, GRACE, 10), "a sem uso, depois da folga");
        index.setScreen("a", List.of(hash(1)), GRACE);
        assertEquals(List.of(), index.expired(GRACE * 3, GRACE, 10), "usada de novo, fica");
        index.removeScreen("a", GRACE * 3);
        assertEquals(List.of(), index.expired(GRACE * 4 - 1, GRACE, 10), "a folga recomeça quando perde o uso");
        assertEquals(List.of(hash(1)), index.expired(GRACE * 4, GRACE, 10));
        assertEquals(List.of(), index.expired(GRACE * 4, GRACE, 0), "o lote tem teto");
    }

    @Test
    void recuperadaNaoSaiPelaLimpezaAteVoltarAoUso() {
        TvImageIndex<String> index = new TvImageIndex<>();
        index.add(entry(1, 10, null, 0), true, 0);
        assertTrue(index.get(hash(1)).recovered());
        assertEquals(List.of(), index.expired(GRACE * 100, GRACE, 10));
        index.setScreen("a", List.of(hash(1)), GRACE);
        index.removeScreen("a", GRACE * 2);
        assertFalse(index.get(hash(1)).recovered(), "usada de novo, volta à limpeza normal");
        assertEquals(List.of(hash(1)), index.expired(GRACE * 3, GRACE, 10));
    }

    @Test
    void cargaDevolveAFolgaGravada() {
        TvImageIndex<String> index = new TvImageIndex<>();
        index.add(entry(1, 10, ALICE, 0), false, 1000);
        index.restoreUnusedSince(hash(1), 5);
        assertEquals(5, index.get(hash(1)).unusedSince());
        index.setLibrary(BOB, Set.of(hash(1)), 2000);
        index.restoreUnusedSince(hash(1), 7);
        assertEquals(-1, index.get(hash(1)).unusedSince(), "em uso, não volta");
    }

    @Test
    void removerTiraOsBytesDoAutor() {
        TvImageIndex<String> index = new TvImageIndex<>();
        index.add(entry(1, 10, ALICE, 0), false, 0);
        index.add(entry(2, 20, ALICE, 0), false, 0);
        index.setLibrary(ALICE, List.of(hash(1), hash(2)), 0);
        assertEquals(30, index.bytesOf(ALICE));
        index.remove(hash(1));
        assertEquals(20, index.bytesOf(ALICE));
        assertEquals(20, index.totalBytes());
        assertNull(index.get(hash(1)));
        assertEquals(1, index.get(hash(2)).libraries());
    }
}
