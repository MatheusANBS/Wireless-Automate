package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;

import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * A vista do editor da Tela de Sinal no e2e, na Tela 4 × 2 com o Valor e o Texto agrupados e salvos: puxa a alça do
 * canto para encolher o painel, dá zoom com Ctrl + roda (e move a vista pelo botão do meio), volta com o "Caber", abre
 * a aba Camadas pelo clique e o menu do clique direito numa peça, duplica por ele e desfaz. Tudo pelo mouse da janela
 * ({@link E2eUi}); capturas {@code tela-editor-redimensionado}, {@code -zoom}, {@code -camadas} e {@code -menu}.
 */
final class E2eTvEditorViewSteps {
    private static final int[] BEFORE = new int[2];

    private E2eTvEditorViewSteps() {
    }

    static void viewSteps(List<Step> list) {
        list.add(new Step("Vista: puxar a alça do canto encolhe o painel", STEP_TIMEOUT_MS, () -> {
            int[] size = screen().panelSize();
            BEFORE[0] = size[0];
            BEFORE[1] = size[1];
            int[] grip = screen().gripCenter();
            drag(screen(), grip[0], grip[1], grip[0] - 30, grip[1] - 20);
        }, () -> {
            int[] size = screen().panelSize();
            return size[0] < BEFORE[0] && size[1] < BEFORE[1] && editor().fits()
                    && editor().draft().pieces().size() == 2;
        }, () -> "painel " + Arrays.toString(screen().panelSize()) + " de " + Arrays.toString(BEFORE)));
        E2eTvSteps.shot(list, "tela-editor-redimensionado");
        list.add(new Step("Vista: Ctrl + roda dá zoom e o botão do meio move", STEP_TIMEOUT_MS, () -> {
            int[] at = editor().canvasCenter();
            TvEditorTab.e2eCtrl = true;
            try {
                for (int i = 0; i < 3; i++) {
                    screen().mouseScrolled(at[0], at[1], 0, 1);
                }
            } finally {
                TvEditorTab.e2eCtrl = false;
            }
            screen().mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            screen().mouseDragged(at[0] + 12, at[1] + 6, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 12, 6);
            screen().mouseReleased(at[0] + 12, at[1] + 6, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        }, () -> editor().zoomFactor() == 4 && editor().fitCenter() != null, () -> "zoom " + editor().zoomFactor()));
        E2eTvSteps.shot(list, "tela-editor-zoom");
        list.add(new Step("Vista: o botão Caber volta", STEP_TIMEOUT_MS, () -> click(screen(), editor().fitCenter()[0],
                editor().fitCenter()[1]), () -> editor().zoomFactor() == 1, () -> "zoom " + editor().zoomFactor()));
        list.add(new Step("Vista: a aba Camadas pelo clique", STEP_TIMEOUT_MS, () -> {
            int[] tab = editor().leftTabCenter("LAYERS");
            click(screen(), tab[0], tab[1]);
        }, () -> "LAYERS".equals(editor().leftTab()), () -> "aba " + editor().leftTab()));
        E2eTvSteps.shot(list, "tela-editor-camadas");
        list.add(new Step("Vista: clique direito numa peça abre o menu", STEP_TIMEOUT_MS, () -> {
            int[] at = editor().pieceCenter(1);
            moveMouse(at[0], at[1]);
            screen().mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            screen().mouseReleased(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        }, () -> editor().menuOpen() && !editor().selection().isEmpty(), () -> "menu " + editor().menuOpen()));
        E2eTvSteps.shot(list, "tela-editor-menu");
        list.add(new Step("Vista: Duplicar pelo menu e Ctrl+Z desfaz", STEP_TIMEOUT_MS, () -> {
            int[] at = editor().menuCenter("DUPLICATE");
            click(screen(), at[0], at[1]);
        }, () -> !editor().menuOpen() && editor().draft().pieces().size() > 2, () -> "peças "
                + editor().draft().pieces().size()));
        list.add(new Step("Vista: Ctrl+Z tira a cópia", STEP_TIMEOUT_MS,
                () -> screen().keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL),
                () -> editor().draft().pieces().size() == 2 && !editor().dirty(),
                () -> "peças " + editor().draft().pieces().size()));
        list.add(new Step("Vista: solta a escolha", STEP_TIMEOUT_MS, () -> {
            if (!editor().selection().isEmpty()) {
                screen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
        }, () -> editor().selection().isEmpty() && Minecraft.getInstance().screen instanceof TvScreen,
                () -> "escolha " + editor().selection()));
    }

    private static TvScreen screen() throws StepFailure {
        return E2eTvSteps.screen();
    }

    private static TvEditorTab editor() throws StepFailure {
        return screen().editor();
    }
}
