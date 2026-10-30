package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class CompactingMathTest {
    private static final int[] NINE = {1, 9, 9, 9};

    @Test
    void compactaComSobraNaFormaDeBaixo() {
        // 10 pedregulhos com alvo 1x: 1 de 1x e sobra 1
        assertArrayEquals(new long[] {1, 1, 0, 0}, CompactingMath.convert(new long[] {10, 0, 0, 0}, NINE, 1, Long.MAX_VALUE));
    }

    @Test
    void compactaAteOAlvoEmCascata() {
        // 100 lingotes (nível 1) com alvo no topo (3): 100 = 1×81 + 2×9 + 1
        assertArrayEquals(new long[] {0, 1, 2, 1}, CompactingMath.convert(new long[] {0, 100, 0, 0}, NINE, 3, Long.MAX_VALUE));
    }

    @Test
    void descompactaTudoQuandoCabe() {
        // alvo 1: 1 de nível 2 vale 9 de nível 1 e 1 de nível 3 vale 81
        assertArrayEquals(new long[] {0, 9 + 81, 0, 0}, CompactingMath.convert(new long[] {0, 0, 1, 1}, NINE, 1, Long.MAX_VALUE));
    }

    @Test
    void descompactaSoOQueCabeAFormaMaisBarataPrimeiro() {
        // alvo 0; 2 de nível 1 (cada um rende 9, custa 8 de espaço) e 1 de nível 2 (rende 81, custa 80); espaço 20
        // cabem os 2 de nível 1 (16), o de nível 2 não (80 > 4)
        assertArrayEquals(new long[] {18, 0, 1, 0}, CompactingMath.convert(new long[] {0, 2, 1, 0}, NINE, 0, 20));
    }

    @Test
    void compactarLiberaEspacoAntesDeDescompactar() {
        // alvo 1: 18 de nível 0 viram 2 de nível 1 (libera 16); 1 de nível 2 vira 9 de nível 1 (custa 8)
        assertArrayEquals(new long[] {0, 11, 0, 0}, CompactingMath.convert(new long[] {18, 0, 1, 0}, NINE, 1, 0));
    }

    @Test
    void naoEstouraOLong() {
        long[] counts = {0, 0, 0, Long.MAX_VALUE / 2};
        long[] out = CompactingMath.convert(counts, NINE, 0, Long.MAX_VALUE);
        // nada pode ficar negativo e o total em itens não pode diminuir para a descompactação
        for (long c : out) {
            org.junit.jupiter.api.Assertions.assertTrue(c >= 0);
        }
    }

    @Test
    void alvoNoNivelAtualNaoMexe() {
        assertArrayEquals(new long[] {3, 5, 0, 0}, CompactingMath.convert(new long[] {3, 5, 0, 0}, NINE, 1, 0));
    }

    @Test
    void equivalenteContaACadeiaNaUnidadeDaForma() {
        // 1 lingote + 11 blocos = 100 lingotes; as 10 pepitas valem mais 1 lingote (a sobra de 1 pepita não conta)
        org.junit.jupiter.api.Assertions.assertEquals(101, CompactingMath.equivalent(new long[] {10, 1, 11}, new int[] {1, 9, 9}, 1));
        org.junit.jupiter.api.Assertions.assertEquals(Long.MAX_VALUE,
                CompactingMath.equivalent(new long[] {0, 0, 0, Long.MAX_VALUE}, NINE, 0));
    }
}
