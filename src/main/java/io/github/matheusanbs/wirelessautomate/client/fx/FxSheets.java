package io.github.matheusanbs.wirelessautomate.client.fx;

import io.github.matheusanbs.wirelessautomate.client.fx.FxSheet.Layer;
import java.util.List;

/**
 * Todas as folhas de efeito, com os mesmos nomes e tamanhos do {@code fx_atlas.py} do filme (os quadros animados
 * empilhados). As de tinta vêm primeiro em {@link #all()}, a ordem em que o {@code FxBatch} desenha: tinta, depois luz.
 * As tiras ({@code repeatU}) repetem pelo comprimento. O {@code fluxo1}, o {@code aro_fino} e o risco de 8 × 2 do
 * filme não são usados pela Armilar e não existem aqui.
 */
public final class FxSheets {
    public static final FxSheet FUMACA = new FxSheet("fumaca", 16, 16, 4, Layer.INK, false);
    public static final FxSheet TRILHOS = new FxSheet("trilhos", 64, 8, 1, Layer.INK, true);

    public static final FxSheet BRILHO = new FxSheet("brilho", 16, 16, 1, Layer.LIGHT, false);
    public static final FxSheet BRILHO_G = new FxSheet("brilho_g", 32, 32, 1, Layer.LIGHT, false);
    public static final FxSheet SOL = new FxSheet("sol", 32, 32, 6, Layer.LIGHT, false);
    public static final FxSheet CHAMA = new FxSheet("chama", 16, 24, 6, Layer.LIGHT, false);
    public static final FxSheet ESTRELA = new FxSheet("estrela", 16, 16, 4, Layer.LIGHT, false);
    public static final FxSheet ARO_TRACEJADO = new FxSheet("aro_tracejado", 32, 32, 1, Layer.LIGHT, false);
    public static final FxSheet BRASA0 = new FxSheet("brasa0", 3, 3, 1, Layer.LIGHT, false);
    public static final FxSheet BRASA1 = new FxSheet("brasa1", 4, 4, 1, Layer.LIGHT, false);
    public static final FxSheet BRASA2 = new FxSheet("brasa2", 6, 6, 1, Layer.LIGHT, false);
    public static final FxSheet RISCO_L = new FxSheet("risco_l", 12, 2, 1, Layer.LIGHT, false);
    public static final FxSheet CHOQUE = new FxSheet("choque", 32, 32, 6, Layer.LIGHT, false);
    public static final FxSheet CLARAO = new FxSheet("clarao", 32, 32, 4, Layer.LIGHT, false);
    public static final FxSheet TRILHOS_RIM = new FxSheet("trilhos_rim", 64, 8, 1, Layer.LIGHT, true);
    public static final FxSheet MIOLO = new FxSheet("miolo", 64, 8, 1, Layer.LIGHT, true);
    public static final FxSheet FLUXO = new FxSheet("fluxo", 64, 8, 1, Layer.LIGHT, true);
    public static final FxSheet RAIO = new FxSheet("raio", 64, 8, 8, Layer.LIGHT, true);
    public static final FxSheet SIGILO = new FxSheet("sigilo", 64, 64, 4, Layer.LIGHT, false);

    private static final List<FxSheet> ALL = List.of(FUMACA, TRILHOS, BRILHO, BRILHO_G, SOL, CHAMA, ESTRELA,
            ARO_TRACEJADO, BRASA0, BRASA1, BRASA2, RISCO_L, CHOQUE, CLARAO, TRILHOS_RIM, MIOLO, FLUXO, RAIO, SIGILO);

    private FxSheets() {
    }

    /** Todas as folhas, imutável, as de tinta primeiro. */
    public static List<FxSheet> all() {
        return ALL;
    }
}
