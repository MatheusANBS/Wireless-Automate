package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Os atalhos do editor (decisão 11 da spec geral): Ctrl+Z desfaz, Ctrl+Y e Ctrl+Shift+Z refazem, Ctrl+C, X e V copiam,
 * recortam e colam, Ctrl+D duplica, Ctrl+A escolhe tudo, Ctrl+G agrupa e Ctrl+Shift+G desagrupa, Delete e Backspace
 * removem, as setas movem 4 px (Shift, um bloco de 32 px; Alt, 1 px), {@code [} e {@code ]} descem e sobem uma camada
 * (pela letra digitada, que acompanha o teclado de quem usa) e Ctrl+S salva. Nada age com um campo de texto
 * focado (o painel de propriedades fica com o teclado) nem com a escolha vazia onde ela é preciso. O Esc é da tela.
 * Os modificadores vêm da tecla e do estado do teclado, para o e2e mandar a combinação sem apertar nada.
 */
final class TvEditorKeys {
    private final TvEditorDoc doc;
    private final Runnable save;

    TvEditorKeys(TvEditorDoc doc, Runnable save) {
        this.doc = doc;
        this.save = save;
    }

    static boolean ctrl(int modifiers) {
        return Screen.hasControlDown() || (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    static boolean shift(int modifiers) {
        return Screen.hasShiftDown() || (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 || TvEditorTab.e2eShift;
    }

    static boolean alt(int modifiers) {
        return Screen.hasAltDown() || (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }

    /** Uma tecla apertada; {@code true} se virou um atalho. */
    boolean keyPressed(int keyCode, int modifiers) {
        boolean ctrl = ctrl(modifiers);
        boolean shift = shift(modifiers);
        if (ctrl) {
            return switch (keyCode) {
                case GLFW.GLFW_KEY_Z -> shift ? run(doc::redo) : run(doc::undo);
                case GLFW.GLFW_KEY_Y -> run(doc::redo);
                case GLFW.GLFW_KEY_C -> run(doc::copy);
                case GLFW.GLFW_KEY_X -> run(doc::cut);
                case GLFW.GLFW_KEY_V -> run(doc::paste);
                case GLFW.GLFW_KEY_D -> run(() -> {
                    if (doc.hasSelection()) {
                        doc.duplicate();
                    }
                });
                case GLFW.GLFW_KEY_A -> run(doc::selectAll);
                case GLFW.GLFW_KEY_G -> run(() -> {
                    if (shift) {
                        if (doc.canUngroup()) {
                            doc.ungroup();
                        }
                    } else if (doc.canGroup()) {
                        doc.group();
                    }
                });
                case GLFW.GLFW_KEY_S -> run(() -> {
                    if (doc.model().dirty()) {
                        save.run();
                    }
                });
                default -> false;
            };
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                return run(() -> {
                    if (doc.hasSelection()) {
                        doc.remove();
                    }
                });
            }
            case GLFW.GLFW_KEY_LEFT -> {
                return arrow(-1, 0, shift, modifiers);
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                return arrow(1, 0, shift, modifiers);
            }
            case GLFW.GLFW_KEY_UP -> {
                return arrow(0, -1, shift, modifiers);
            }
            case GLFW.GLFW_KEY_DOWN -> {
                return arrow(0, 1, shift, modifiers);
            }
            default -> {
                return false;
            }
        }
    }

    /** Uma letra digitada sem campo focado: {@code [} desce e {@code ]} sobe uma camada. */
    boolean charTyped(char c) {
        if (c == '[') {
            return run(doc::lower);
        }
        if (c == ']') {
            return run(doc::raise);
        }
        return false;
    }

    private boolean arrow(int dx, int dy, boolean shift, int modifiers) {
        int step = alt(modifiers) ? 1 : shift ? TvArea.PIXELS_PER_BLOCK : TvSnap.GRID;
        return run(() -> doc.nudge(dx * step, dy * step));
    }

    private static boolean run(Runnable action) {
        action.run();
        return true;
    }
}
