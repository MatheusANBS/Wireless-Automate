package io.github.matheusanbs.wirelessautomate.client.storage.upgrade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChainNamesTest {
    @Test
    void ferroEmPortugues() {
        List<String> names = List.of("Pepita de Ferro", "Lingote de Ferro", "Bloco de Ferro");
        assertEquals("Ferro", ChainNames.chainName(names));
        assertArrayEquals(new String[] {"Pepita", "Lingote", "Bloco"}, ChainNames.shortNames(names));
    }

    @Test
    void ferroEmInglesTiraOConectivoDaPonta() {
        List<String> names = List.of("Iron Nugget", "Iron Ingot", "Block of Iron");
        assertEquals("Iron", ChainNames.chainName(names));
        assertArrayEquals(new String[] {"Nugget", "Ingot", "Block"}, ChainNames.shortNames(names));
    }

    @Test
    void aBaseSemSobraFicaVazia() {
        List<String> names = List.of("Diamante", "Bloco de Diamante");
        assertEquals("Diamante", ChainNames.chainName(names));
        assertArrayEquals(new String[] {"", "Bloco"}, ChainNames.shortNames(names));
    }

    @Test
    void semPalavraComumONomeDaCadeiaEODaBase() {
        assertEquals("Lã", ChainNames.chainName(List.of("Lã", "Bloco Fofo")));
    }

    @Test
    void maiusculasNaoImportam() {
        List<String> names = List.of("Ferro Bruto", "Bloco de ferro bruto");
        assertEquals("Ferro Bruto", ChainNames.chainName(names));
        assertArrayEquals(new String[] {"", "Bloco"}, ChainNames.shortNames(names));
    }

    @Test
    void quartzoVoltaComOsNomesInteiros() {
        List<String> names = List.of("Nether Quartz", "Block of Quartz");
        assertEquals("Quartz", ChainNames.chainName(names));
        assertArrayEquals(new String[] {"Nether Quartz", "Block of Quartz"}, ChainNames.shortNames(names));
    }

    @Test
    void argilaVoltaComOsNomesInteiros() {
        List<String> names = List.of("Clay Ball", "Clay");
        assertEquals("Clay", ChainNames.chainName(names));
        assertArrayEquals(new String[] {"Clay Ball", "Clay"}, ChainNames.shortNames(names));
    }

    @Test
    void rotuloDoCanto() {
        assertEquals("3x", ChainNames.corner("cobblestone_3x"));
        assertEquals("9x", ChainNames.corner("compressed_dirt_9x"));
        assertNull(ChainNames.corner("iron_block"));
        assertNull(ChainNames.corner("box"));
    }

    @Test
    void escadaSoComTodasAsFormasDeCimaRotuladas() {
        assertTrue(ChainNames.ladder(new String[] {null, "1x", "2x"}));
        assertFalse(ChainNames.ladder(new String[] {null, "1x", null}));
        assertFalse(ChainNames.ladder(new String[] {null}));
    }

    /** Cada caractere mede 6, como a fonte do jogo em média. */
    private static int sixEach(String text) {
        return text.length() * 6;
    }

    @Test
    void nomeDoModQueCabeFicaInteiro() {
        assertEquals("Occultism", ChainNames.modShort("Occultism", ChainNamesTest::sixEach, 58));
    }

    @Test
    void nomeDoModLongoViraAsIniciais() {
        assertEquals("MI", ChainNames.modShort("Modern Industrialization", ChainNamesTest::sixEach, 58));
    }

    @Test
    void palavraUnicaLongaPerdeOFimSemReticencias() {
        assertEquals("Supercalif", ChainNames.modShort("Supercalifragilistic", ChainNamesTest::sixEach, 60));
    }

    @Test
    void iniciaisQueNaoCabemCaemNoComecoDoNome() {
        assertEquals("Ab", ChainNames.modShort("Ab Cd Ef", ChainNamesTest::sixEach, 12));
    }
}
