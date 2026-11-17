package io.github.matheusanbs.wirelessautomate.client.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A fila dos efeitos do quadro: quem tem efeito à vista anota aqui durante o quadro, o {@link FxStages} desenha e
 * esvazia. Só na thread de render. A lista é uma só, reaproveitada.
 */
public final class FxQueue {
    private static final List<FxJob> JOBS = new ArrayList<>();
    /** Quem diz se é a passada de sombra; o teste troca por um fornecedor seu (sem GL nem Iris). */
    private static BooleanSupplier shadowPass = ShaderPackCheck::shadowPass;

    private FxQueue() {
    }

    /** Anota o job, a não ser na passada de sombra: nada de efeito nela (senão seria desenhado de novo no mundo). */
    public static void submit(FxJob job) {
        if (inShadowPass()) {
            return;
        }
        JOBS.add(job);
    }

    /** Só o teste: troca o detector da passada de sombra; {@code null} volta ao do Iris. */
    static void setShadowDetector(BooleanSupplier detector) {
        shadowPass = detector == null ? ShaderPackCheck::shadowPass : detector;
    }

    /** Se é a passada de sombra, pelo mesmo detector de {@link #submit}; o {@link FxLights} também o consulta. */
    static boolean inShadowPass() {
        return shadowPass.getAsBoolean();
    }

    public static boolean isEmpty() {
        return JOBS.isEmpty();
    }

    public static void clear() {
        JOBS.clear();
    }

    static int size() {
        return JOBS.size();
    }

    static FxJob get(int index) {
        return JOBS.get(index);
    }
}
