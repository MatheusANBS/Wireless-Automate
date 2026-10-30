package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChainGraphTest {
    private static ChainGraph<String> graph() {
        return new ChainGraph<>(Comparator.<String>naturalOrder());
    }

    @Test
    void ligaParesEmCadeia() {
        ChainGraph<String> g = graph();
        g.link("ingot", "block", 9);
        g.link("nugget", "ingot", 9);
        g.link("block", "block_1x", 9);
        List<ChainGraph.Chain<String>> chains = g.chains();
        assertEquals(1, chains.size());
        assertEquals(List.of("nugget", "ingot", "block", "block_1x"), chains.get(0).forms());
        assertArrayEquals(new int[] {1, 9, 9, 9}, chains.get(0).factors());
    }

    @Test
    void preferePrimeiroO3x3() {
        ChainGraph<String> g = graph();
        g.link("a", "a_small_block", 4);
        g.link("a", "a_block", 9);
        assertEquals(List.of("a", "a_block"), g.chains().get(0).forms());
    }

    @Test
    void doisMaioresDoMesmoTamanhoUsaOPrimeiroNaOrdem() {
        ChainGraph<String> g = graph();
        g.link("x", "z_block", 9);
        g.link("x", "b_block", 9);
        assertEquals(List.of("x", "b_block"), g.chains().get(0).forms());
    }

    @Test
    void cortaCiclo() {
        ChainGraph<String> g = graph();
        g.link("a", "b", 9);
        g.link("b", "a", 9);
        for (ChainGraph.Chain<String> chain : g.chains()) {
            assertEquals(chain.forms().size(), chain.forms().stream().distinct().count());
        }
    }

    @Test
    void doisMenoresParaOMesmoMaiorFicaUmSo() {
        ChainGraph<String> g = graph();
        g.link("b_dust", "block", 9);
        g.link("a_ingot", "block", 9);
        List<ChainGraph.Chain<String>> chains = g.chains();
        assertEquals(1, chains.size());
        assertEquals(List.of("a_ingot", "block"), chains.get(0).forms());
    }
}
