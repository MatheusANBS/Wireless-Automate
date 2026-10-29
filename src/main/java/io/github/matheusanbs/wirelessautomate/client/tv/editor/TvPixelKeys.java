package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelTool;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelTransform;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * O teclado do editor de pixel art (decisões 1a a 1g da spec da etapa 4 e o mockup da animação). A tecla vai primeiro à
 * janela solta (o seletor de cor, a lista do modo), depois ao campo com o foco (o nome, o tamanho, a velocidade), ao
 * Esc (tira a seleção ou é o Cancelar), ao Espaço (segurado, o arrastar move a vista) e aos atalhos.
 * <p>
 * As combinações com Ctrl, com Alt, as setas, Delete, Enter e o teclado numérico vêm pela tecla: Ctrl+Z desfaz, Ctrl+Y
 * (e Ctrl+Shift+Z) refaz, Ctrl+A e Ctrl+D selecionam tudo e nada, Ctrl+C, X e V copiam, recortam e colam (a área de
 * cópia vale entre quadros), Ctrl+G liga a grade, Ctrl+S salva; Alt+N põe um quadro novo vazio depois do escolhido,
 * Alt+D o duplica, Alt+O liga o papel vegetal e Alt+P toca e pausa a prévia; as setas movem o pedaço (Shift, 8 px) ou,
 * sem seleção, a vista. As letras vêm pelo caractere digitado, que acompanha o teclado de quem usa: a tecla de cada
 * ferramenta, M e Shift+M os espelhos, Shift+H e Shift+V viram, P o pixel perfect, X a cor anterior, {@code [} e
 * {@code ]} o pincel, {@code ,} e {@code .} o quadro anterior e o seguinte, 0 enquadra e {@code +} e {@code −} dão
 * zoom. Com Alt (sem Ctrl, que junto dele é o AltGr), o caractere não vale: o Alt+D não escolhe também o clarear.
 */
final class TvPixelKeys {
    private static final int VIEW_STEP = 32;
    private static final int BIG_STEP = 8;

    private final TvPixelSession session;
    private final TvPixelBoard board;
    private final TvPixelToolbar toolbar;
    private final TvPixelStrip strip;
    private final TvPixelHeader header;
    private final TvPopups popups;
    private final Runnable save;
    private final Runnable cancel;
    private boolean spaceDown;

    TvPixelKeys(TvPixelSession session, TvPixelBoard board, TvPixelToolbar toolbar, TvPixelStrip strip,
            TvPixelHeader header, TvPopups popups, Runnable save, Runnable cancel) {
        this.session = session;
        this.board = board;
        this.toolbar = toolbar;
        this.strip = strip;
        this.header = header;
        this.popups = popups;
        this.save = save;
        this.cancel = cancel;
    }

    /** O Espaço está seguro: o arrastar no quadro move a vista. */
    boolean spaceDown() {
        return spaceDown;
    }

    /** Uma tecla com o editor aberto: a janela solta, o campo com o foco, o Esc, o Espaço e os atalhos. */
    boolean key(int keyCode, int scanCode, int modifiers, boolean fits) {
        TvPopup popup = popups.current();
        if (popup != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE && !popup.wantsKeys()) {
                popups.close();
                return true;
            }
            popup.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (header.textFocused()) {
            header.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (strip.wantsKeys()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                strip.blur();
            } else {
                strip.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!session.escape()) {
                cancel.run();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            spaceDown = true;
            return true;
        }
        return fits && shortcut(keyCode, modifiers);
    }

    boolean released(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_SPACE && spaceDown) {
            spaceDown = false;
            return true;
        }
        return false;
    }

    boolean typed(char c, int modifiers, boolean fits) {
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.charTyped(c, modifiers);
        }
        if (header.textFocused()) {
            return header.charTyped(c, modifiers);
        }
        if (strip.wantsKeys()) {
            return strip.charTyped(c, modifiers);
        }
        if (Screen.hasAltDown() && !Screen.hasControlDown()) {
            return true;
        }
        return fits && c != ' ' && letter(c);
    }

    private boolean shortcut(int keyCode, int modifiers) {
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
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_N -> strip.press(TvPixelStrip.Button.ADD);
                case GLFW.GLFW_KEY_D -> strip.press(TvPixelStrip.Button.DUPLICATE);
                case GLFW.GLFW_KEY_O -> strip.press(TvPixelStrip.Button.ONION);
                case GLFW.GLFW_KEY_P -> strip.press(TvPixelStrip.Button.PLAY);
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

    private boolean letter(char c) {
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
            case ',' -> session.frames().step(-1);
            case '.' -> session.frames().step(1);
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
