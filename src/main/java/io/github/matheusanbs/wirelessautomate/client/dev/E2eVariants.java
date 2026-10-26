package io.github.matheusanbs.wirelessautomate.client.dev;

import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import java.util.List;

/**
 * As repetições do e2e em português e no tema escuro: o bloco só entra no roteiro quando a variante está ligada
 * ({@code WA_E2E_VARIANTS=pt,escuro} ou {@code todas}). O bloco é autocontido: entra e devolve o idioma e o tema.
 */
final class E2eVariants {
    private static final String MODE = DevEndToEnd.envOr("WA_E2E_VARIANTS", DevEndToEnd.PROFILE.variants());

    private E2eVariants() {
    }

    static boolean ptOn() {
        return E2eSelection.variantOn(MODE, "pt");
    }

    static boolean darkOn() {
        return E2eSelection.variantOn(MODE, "escuro");
    }

    /** Acrescenta os passos de {@code body} só com a variante {@code pt} ligada. */
    static void pt(List<Step> list, Runnable body) {
        if (ptOn()) {
            body.run();
        }
    }

    /** Acrescenta os passos de {@code body} só com a variante {@code escuro} ligada. */
    static void escuro(List<Step> list, Runnable body) {
        if (darkOn()) {
            body.run();
        }
    }
}
