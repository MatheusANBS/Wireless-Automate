package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.resources.ResourceLocation;

/**
 * O mapa dos códigos de câmara ({@code ChamberView.chambers}: 0 vazia, 1 Sinal, 2 Lapidado, 3 Opaco do Sinal, 4 Opaco
 * do Lapidado, 5 Rachado do Sinal, 6 Rachado do Lapidado) para a textura, o filamento e a estrela. Lógica pura.
 * O Lapidado Opaco (4) usa a textura do Opaco: a spec só lista cinco texturas de cristal.
 */
final class CrystalLook {
    private static final byte DULL = 3;
    private static final byte CRACKED = 5;
    private static final double CRACKED_FILAMENT = 0.35;

    /** As cinco texturas de cristal. */
    enum Tex {
        SINAL("sinal"), LAPIDADO("lapidado"), FAIXA("faixa"), OPACO("opaco"), RACHADO("rachado");

        private final ResourceLocation texture;

        Tex(String nome) {
            this.texture = WirelessAutomate.id("textures/armilar/cristal_" + nome + ".png");
        }

        ResourceLocation texture() {
            return texture;
        }
    }

    private CrystalLook() {
    }

    static boolean empty(byte code) {
        return code <= 0;
    }

    /** Opaco ou rachado: os dois sem carga. */
    static boolean dull(byte code) {
        return code >= DULL;
    }

    static boolean cracked(byte code) {
        return code >= CRACKED;
    }

    /** Lapidado (os pares). */
    static boolean cut(byte code) {
        return code > 0 && code % 2 == 0;
    }

    /** A textura do cristal aceso: Lapidado para os pares, Sinal para os ímpares; {@code null} na câmara vazia. */
    static Tex liveTex(byte code) {
        return empty(code) ? null : cut(code) ? Tex.LAPIDADO : Tex.SINAL;
    }

    /** A textura do estado: aceso, Opaco ou Rachado; {@code null} na câmara vazia. */
    static Tex bodyTex(byte code) {
        if (empty(code)) {
            return null;
        }
        return cracked(code) ? Tex.RACHADO : dull(code) ? Tex.OPACO : liveTex(code);
    }

    /** O quanto o filamento do cristal acende: 1 vivo, 0 Opaco, 0,35 rachado. */
    static double filament(byte code) {
        return empty(code) ? 0 : cracked(code) ? CRACKED_FILAMENT : dull(code) ? 0 : 1;
    }

    /** A estrela só no cristal vivo. */
    static boolean star(byte code) {
        return !empty(code) && !dull(code);
    }
}
