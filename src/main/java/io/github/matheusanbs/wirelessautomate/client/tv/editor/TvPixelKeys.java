package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelTool;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelTransform;
import org.lwjgl.glfw.GLFW;

/**
 * Os atalhos do editor de pixel art (decisões 1a a 1g da spec da etapa 4). As combinações com Ctrl, as setas, Delete,
 * Enter e o teclado numérico vêm pela tecla: Ctrl+Z desfaz, Ctrl+Y (e Ctrl+Shift+Z) refaz, Ctrl+A e Ctrl+D selecionam
 * tudo e nada, Ctrl+C, X e V copiam, recortam e colam, Ctrl+G liga a grade, Ctrl+S salva; as setas movem o pedaço
 * (Shift, 8 px) ou, sem seleção, a vista. As letras vêm pelo caractere digitado, que acompanha o teclado de quem usa:
 * a tecla de cada ferramenta, M e Shift+M os espelhos, Shift+H e Shift+V viram, P o pixel perfect, X a cor anterior,
 * {@code [} e {@code ]} o pincel, 0 enquadra e {@code +} e {@code −} dão zoom. Nada vale com um campo de texto focado
 * (quem chama confere antes).
 */
final class TvPixelKeys {
    private static final int VIEW_STEP = 32;
    private static final int BIG_STEP = 8;

    private final TvPixelSession session;
    private final TvPixelBoard board;
    private final TvPixelToolbar toolbar;
    private final Runnable save;

    TvPixelKeys(TvPixelSession session, TvPixelBoard board, TvPixelToolbar toolbar, Runnable save) {
        this.session = session;
        this.board = board;
        this.toolbar = toolbar;
        this.save = save;
    }

    boolean keyPressed(int keyCode, int modifiers) {
        boolean shift = TvEditorKeys.shift(modifiers);
        if (TvEditorKeys.ctrl(modifiers)) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_Z -> {
                    if (shift) {
                        session.redo();
                    } else {
                        session.undo();
                    }
                }
                case GLFW.GLFW_KEY_Y -> session.redo();
                case GLFW.GLFW_KEY_A -> session.selectAll();
                case GLFW.GLFW_KEY_D -> session.deselect();
                case GLFW.GLFW_KEY_C -> session.copy();
                case GLFW.GLFW_KEY_X -> session.cut();
                case GLFW.GLFW_KEY_V -> session.paste(board.viewCornerX(), board.viewCornerY());
                case GLFW.GLFW_KEY_G -> session.toggleGrid();
                case GLFW.GLFW_KEY_S -> save.run();
                default -> {
                    return false;
                }
            }
            return true;
        }
        int dx = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
        int dy = keyCode == GLFW.GLFW_KEY_UP ? -1 : keyCode == GLFW.GLFW_KEY_DOWN ? 1 : 0;
        if (dx != 0 || dy != 0) {
            if (session.selection().active()) {
                int step = shift ? BIG_STEP : 1;
                session.nudge(dx * step, dy * step);
            } else {
                session.view().panBy(-dx * VIEW_STEP, -dy * VIEW_STEP);
            }
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> session.delete();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> session.settle();
            case GLFW.GLFW_KEY_KP_ADD -> board.zoomCenter(1);
            case GLFW.GLFW_KEY_KP_SUBTRACT -> board.zoomCenter(-1);
            default -> {
                return false;
            }
        }
        return true;
    }

    boolean charTyped(char c) {
        TvPixelTool tool = TvPixelTool.forKey(c);
        if (tool != null) {
            toolbar.press(TvPixelToolbar.Button.ALL[tool.ordinal()]);
            return true;
        }
        switch (c) {
            case 'm' -> session.toggleMirrorH();
            case 'M' -> session.toggleMirrorV();
            case 'H' -> session.transform(TvPixelTransform.FLIP_H);
            case 'V' -> session.transform(TvPixelTransform.FLIP_V);
            case 'p' -> session.togglePerfect();
            case 'x' -> session.swapColors();
            case '[' -> session.brushBy(-1);
            case ']' -> session.brushBy(1);
            case '0' -> board.fit();
            case '+', '=' -> board.zoomCenter(1);
            case '-' -> board.zoomCenter(-1);
            default -> {
                return false;
            }
        }
        return true;
    }
}
