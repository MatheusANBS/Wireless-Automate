package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eUi;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * O mouse das cenas de tela. O jogo não captura o cursor: aqui ele é um ponto (coordenadas da GUI) que anda até o
 * alvo, leva o mouse do jogo junto (para o realce e as dicas) e que o {@link Gravador} anota em cada quadro, para o
 * {@code scripts/curseforge/gif.py} desenhar a seta e o realce do clique por cima.
 */
final class Cursor {
    private static double x = -1;
    private static double y = -1;
    private static long clique = -1;

    private Cursor() {
    }

    /** O cursor de uma tela recém-aberta: no centro dela, como o jogo o solta. */
    static Step centralizar() {
        return Passos.agir("cursor no centro", () -> {
            Screen tela = Minecraft.getInstance().screen;
            x = tela.width / 2.0;
            y = tela.height / 2.0;
            E2eUi.moveMouse((int) x, (int) y);
        });
    }

    /** Anda até o ponto {@code alvo} (lido ao começar, a tela já arrumada) em {@code ms}. */
    static Step ir(Supplier<int[]> alvo, long ms) {
        double[] de = new double[2];
        int[][] para = new int[1][];
        long[] inicio = new long[1];
        return new Step("cursor anda", 60_000 + ms, () -> {
            de[0] = x;
            de[1] = y;
            para[0] = alvo.get();
            inicio[0] = System.nanoTime();
        }, () -> {
            double t = Passos.suave(Math.min(1, (System.nanoTime() - inicio[0]) / 1e6 / ms));
            x = de[0] + (para[0][0] - de[0]) * t;
            y = de[1] + (para[0][1] - de[1]) * t;
            E2eUi.moveMouse((int) Math.round(x), (int) Math.round(y));
            return t >= 1;
        }, () -> "cursor");
    }

    /** Clica (botão esquerdo) onde o cursor está, pelo mesmo caminho do mouse. */
    static Step clicar() {
        return clicar(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** Clica com o {@code botao} do mouse onde o cursor está. */
    static Step clicar(int botao) {
        return Passos.agir("clique", () -> {
            clique = System.nanoTime();
            Screen tela = Minecraft.getInstance().screen;
            int cx = (int) Math.round(x);
            int cy = (int) Math.round(y);
            E2eUi.moveMouse(cx, cy);
            tela.mouseClicked(cx, cy, botao);
            tela.mouseReleased(cx, cy, botao);
        });
    }

    /**
     * Arrasta com o botão esquerdo: aperta em {@code de} (lido ao começar), leva o cursor até {@code para} em {@code ms}
     * com o mouse apertado e solta lá.
     */
    static Step arrastar(Supplier<int[]> de, Supplier<int[]> para, long ms) {
        int[][] pontos = new int[2][];
        long[] inicio = new long[1];
        return new Step("cursor arrasta", 60_000 + ms, () -> {
            pontos[0] = de.get();
            pontos[1] = para.get();
            x = pontos[0][0];
            y = pontos[0][1];
            clique = System.nanoTime();
            E2eUi.moveMouse(pontos[0][0], pontos[0][1]);
            Minecraft.getInstance().screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            inicio[0] = System.nanoTime();
        }, () -> {
            double t = Passos.suave(Math.min(1, (System.nanoTime() - inicio[0]) / 1e6 / ms));
            double nx = pontos[0][0] + (pontos[1][0] - pontos[0][0]) * t;
            double ny = pontos[0][1] + (pontos[1][1] - pontos[0][1]) * t;
            E2eUi.moveMouse((int) Math.round(nx), (int) Math.round(ny));
            Screen tela = Minecraft.getInstance().screen;
            tela.mouseDragged(nx, ny, GLFW.GLFW_MOUSE_BUTTON_LEFT, nx - x, ny - y);
            x = nx;
            y = ny;
            if (t >= 1) {
                tela.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
            return t >= 1;
        }, () -> "arrasto");
    }

    /** Anda até o alvo, clica e espera {@code depoisMs}. */
    static void tocar(List<Step> lista, Supplier<int[]> alvo, long irMs, long depoisMs) {
        tocar(lista, alvo, GLFW.GLFW_MOUSE_BUTTON_LEFT, irMs, depoisMs);
    }

    /** Anda até o alvo, clica com o {@code botao} e espera {@code depoisMs}. */
    static void tocar(List<Step> lista, Supplier<int[]> alvo, int botao, long irMs, long depoisMs) {
        lista.add(ir(alvo, irMs));
        lista.add(Passos.esperar(120));
        lista.add(clicar(botao));
        lista.add(Passos.esperar(depoisMs));
    }

    /** Posição para o gravador: pixels do quadro, ou {@code null} sem tela aberta. */
    static double[] quadro() {
        Minecraft jogo = Minecraft.getInstance();
        if (jogo.screen == null || x < 0) {
            return null;
        }
        double escala = jogo.getWindow().getGuiScale();
        double idade = clique < 0 ? 9999 : (System.nanoTime() - clique) / 1e6;
        return new double[] {x * escala, y * escala, idade};
    }
}
