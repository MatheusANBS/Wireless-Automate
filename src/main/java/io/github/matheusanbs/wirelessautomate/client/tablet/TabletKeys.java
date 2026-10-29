package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.access.AccessPanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen.Tab;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * O teclado do Tablet: o que digitar na Lista procura, as teclas da Lista, e os campos de texto (renomear e
 * nova rede ou grupo, e os do acesso à rede) que seguram as teclas enquanto o cursor está neles. Devolve {@code true} quando tratou a
 * tecla; {@code false} deixa o resto para a tela. Também dá aos campos a cor do tema (o {@link EditBox} guarda a cor).
 */
final class TabletKeys {
    private final Screen screen;
    private final TabletList list;
    private final NameFields names;
    private final AccessPanel access;
    private final Supplier<Tab> tab;
    private @Nullable EditBox searchBox;
    /** Versão do tema com que os campos foram pintados ({@link GuiThemes#version()}). */
    private int inkVersion = -1;

    TabletKeys(Screen screen, TabletList list, NameFields names, AccessPanel access, Supplier<Tab> tab) {
        this.screen = screen;
        this.list = list;
        this.names = names;
        this.access = access;
        this.tab = tab;
    }

    /** A caixa de busca nova (a tela refaz os widgets); os campos serão pintados de novo. */
    void searchBox(EditBox box) {
        searchBox = box;
        inkVersion = -1;
    }

    /** A busca com o cursor: digitar na Lista procura. */
    void focusSearch() {
        if (searchBox != null) {
            searchBox.visible = true;
            screen.setFocused(searchBox);
        }
    }

    /** Cor do texto e da dica dos campos: segue o tema, só quando ele muda. */
    void ink() {
        if (inkVersion == GuiThemes.version()) {
            return;
        }
        inkVersion = GuiThemes.version();
        // a dica da busca é desenhada pela lista, que a corta e a mostra também com o cursor na caixa
        GuiPaint.ink(searchBox);
        GuiPaint.ink(names.newBox());
        GuiPaint.ink(names.renameBox());
        access.ink();
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab.get() == Tab.LIST) {
            if (list.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // sem busca nem filtro, Esc fecha o Tablet
                return false;
            }
            if (screen.getFocused() != searchBox) {
                focusSearch();
            }
        }
        if (screen.getFocused() instanceof EditBox box && box.isFocused()) {
            boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
            if (box == names.renameBox() && (enter || keyCode == GLFW.GLFW_KEY_ESCAPE)) {
                names.finishRename(enter);
            } else if (box == names.newBox() && enter) {
                names.create();
            } else if (access.keyPressed(box, keyCode)) {
                // tratada pela lista de sugestões do acesso
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                screen.setFocused(null);
            } else {
                box.keyPressed(keyCode, scanCode, modifiers);
            }
            // nenhuma tecla fecha a tela enquanto se digita (nem a do inventário)
            return true;
        }
        return false;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (tab.get() == Tab.LIST && list.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (tab.get() == Tab.LIST && screen.getFocused() != searchBox) {
            focusSearch();
        }
        return screen.getFocused() instanceof EditBox box && box.isFocused() && box.charTyped(codePoint, modifiers);
    }
}
