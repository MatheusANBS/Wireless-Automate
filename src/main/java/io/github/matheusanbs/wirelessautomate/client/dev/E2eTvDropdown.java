package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;

import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorProbe;
import java.util.List;

/**
 * As listas do editor da Tela de Sinal no e2e, pelo mouse: abrir pelo controle e escolher pela linha. Os passos que
 * trocam um valor de lista passam por aqui; o {@code TvEditorProbe} só diz onde clicar.
 */
final class E2eTvDropdown {
    private E2eTvDropdown() {
    }

    private static TvEditorProbe probe() throws StepFailure {
        return E2eTvSteps.screen().editor().probe();
    }

    private static void clickAt(int[] point, String what) throws StepFailure {
        if (point == null) {
            throw new StepFailure(what + " não está à vista");
        }
        click(E2eTvSteps.screen(), point[0], point[1]);
    }

    /** Abre a lista do controle {@code id} com um clique nele. */
    static void open(String id) throws StepFailure {
        clickAt(probe().propCenter(id), "o controle " + id);
        if (!id.equals(probe().dropdownOwner())) {
            throw new StepFailure("a lista de " + id + " não abriu (aberta: " + probe().dropdownOwner() + ")");
        }
    }

    /** Abre a lista do controle {@code id} e clica na linha {@code index}. */
    static void pick(String id, int index) throws StepFailure {
        open(id);
        clickAt(probe().dropdownRowCenter(index), "a linha " + index + " da lista de " + id);
    }

    /** Abre a lista do controle {@code id} e clica na linha com o texto {@code text}. */
    static void pick(String id, String text) throws StepFailure {
        open(id);
        clickAt(probe().dropdownRowCenter(text), "a linha \"" + text + "\" da lista de " + id);
    }

    /** Um passo que abre a lista do controle {@code id} (para a foto dela aberta). */
    static Step openStep(String name, String id) {
        return new Step(name, STEP_TIMEOUT_MS, () -> open(id), () -> id.equals(probe().dropdownOwner())
                && stepTicks >= 2, () -> "lista aberta " + probe().dropdownOwner());
    }

    /**
     * Abre a lista do controle {@code id} com a coluna rolada para ele ficar o mais embaixo que der e confere que ela
     * ficou inteira dentro da janela sem cobrir o controle (para cima quando embaixo não cabe); o Esc fecha sem mudar.
     */
    static void edgeSteps(List<Step> list, String id) {
        int[] anchorY = new int[1];
        list.add(new Step("Lista perto da borda: abrir " + id, STEP_TIMEOUT_MS, () -> {
            int[] at = probe().propCenterLow(id);
            if (at == null) {
                throw new StepFailure("o controle " + id + " não está à vista");
            }
            anchorY[0] = at[1];
            open(id);
        }, () -> fits(anchorY[0]), () -> "lista " + describe(probe().dropdownGeometry()) + ", controle em y "
                + anchorY[0]));
        list.add(new Step("Lista perto da borda: Esc fecha sem mudar", STEP_TIMEOUT_MS,
                () -> E2eTvSteps.screen().editor().escape(), () -> probe().dropdownOwner() == null,
                () -> "lista aberta " + probe().dropdownOwner()));
    }

    /** A lista aberta cabe inteira na janela e não cobre o centro do controle. */
    private static boolean fits(int anchorY) throws StepFailure {
        int[] g = probe().dropdownGeometry();
        if (g == null) {
            return false;
        }
        boolean inside = g[0] >= g[6] && g[0] + g[2] <= g[6] + g[8] && g[1] >= g[7] && g[1] + g[3] <= g[7] + g[9];
        boolean clear = g[5] == 1 ? g[1] + g[3] <= anchorY : g[1] > anchorY;
        return inside && clear;
    }

    static String describe(int[] g) {
        return g == null ? "fechada" : "x " + g[0] + " y " + g[1] + " " + g[2] + "×" + g[3] + ", " + g[4]
                + " linhas, " + (g[5] == 1 ? "para cima" : "para baixo") + ", janela y " + g[7] + " a " + (g[7] + g[9]);
    }
}
