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

    /**
     * Dois mods com o mesmo material e receitas que aceitam a tag nos dois sentidos: cada menor sobe para os dois
     * maiores. Pela ordem só, os dois iriam para o primeiro maior e o outro mod ficaria sem cadeia; com a família,
     * cada mod fica com a própria.
     */
    @Test
    void receitasCruzadasDeDoisModsDaoUmaCadeiaACada() {
        ChainGraph<String> g = new ChainGraph<>(Comparator.<String>naturalOrder(),
                (small, big) -> small.substring(0, small.indexOf(':')).equals(big.substring(0, big.indexOf(':'))));
        for (String small : List.of("mi:raw_silver", "occultism:raw_silver")) {
            for (String big : List.of("mi:raw_silver_block", "occultism:raw_silver_block")) {
                g.link(small, big, 9);
            }
        }
        List<ChainGraph.Chain<String>> chains = g.chains();
        assertEquals(2, chains.size());
        assertEquals(List.of("mi:raw_silver", "mi:raw_silver_block"), chains.get(0).forms());
        assertEquals(List.of("occultism:raw_silver", "occultism:raw_silver_block"), chains.get(1).forms());
    }

    /** Sem a família, o empate segue a ordem: o caso cruzado deixa um mod sem cadeia (o que a família conserta). */
    @Test
    void semFamiliaOEmpateSegueAOrdem() {
        ChainGraph<String> g = graph();
        for (String small : List.of("mi:raw_silver", "occultism:raw_silver")) {
            for (String big : List.of("mi:raw_silver_block", "occultism:raw_silver_block")) {
                g.link(small, big, 9);
            }
        }
        List<ChainGraph.Chain<String>> chains = g.chains();
        assertEquals(1, chains.size());
        assertEquals(List.of("mi:raw_silver", "mi:raw_silver_block"), chains.get(0).forms());
    }

    /** O menor da mesma família ganha o maior, mesmo depois de outro na ordem. */
    @Test
    void maiorFicaComOMenorDaMesmaFamilia() {
        ChainGraph<String> g = new ChainGraph<>(Comparator.<String>naturalOrder(),
                (small, big) -> small.charAt(0) == big.charAt(0));
        g.link("a:x", "b:block", 9);
        g.link("b:x", "b:block", 9);
        List<ChainGraph.Chain<String>> chains = g.chains();
        assertEquals(1, chains.size());
        assertEquals(List.of("b:x", "b:block"), chains.get(0).forms());
    }
}
