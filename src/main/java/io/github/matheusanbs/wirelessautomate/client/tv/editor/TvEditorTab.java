package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvEditorImage;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Editar da tela da Tela de Sinal: a anfitriã das partes do editor, dispostas pelo {@link TvEditorLayout}: na
 * coluna da esquerda, as abas {@link TvLeftTabs} com {@link TvPalettePanel} (Peças), {@link TvComponentsPanel}
 * (Componentes) e {@link TvLayersPanel} (Camadas); no centro, {@link TvBaseLine}, {@link TvEditorCanvas} (com o
 * {@link TvCanvasMenu} do clique direito) e {@link TvEditorBar}; na direita, as propriedades ({@link TvPropsPanel}).
 * Repassa o desenho, o mouse, a roda e o teclado ({@link TvEditorKeys}, com o campo de texto focado tendo a vez) e faz
 * o salvar pelo {@link TvEditorActions}; o rascunho só vai ao servidor no Salvar. Numa janela abaixo do mínimo, só a
 * mensagem pedindo uma escala menor. As janelas soltas (o seletor de cor e a lista de fontes, {@link TvPopups}) ficam por cima de tudo e têm a vez
 * do mouse e do teclado. Os ganchos do e2e ficam no {@link TvEditorProbe}.
 */
public final class TvEditorTab {
    /** O Shift do e2e (a janela de teste não tem teclado de verdade). */
    public static boolean e2eShift;
    /** O Ctrl do e2e, para o zoom da roda. */
    public static boolean e2eCtrl;

    private final TvEditorDoc doc;
    private final TvEditorActions actions;
    private final TvLeftTabs leftTabs = new TvLeftTabs();
    private final TvCanvasMenu menu;
    private final TvPalettePanel palette;
    private final TvComponentsPanel components;
    private final TvEditorCanvas canvas;
    private final TvBaseLine base;
    private final TvEditorBar bar;
    private final TvLayersPanel layers;
    private final TvPropsPanel props;
    private final TvEditorKeys keys;
    private final TvPopups popups = new TvPopups();
    private final TvEditorProbe probe;
    private boolean fits;
    private boolean libraryRequested;
    private int x;
    private int y;
    private int w;
    private int h;
    private List<FormattedCharSequence> tooSmall = List.of();
    private int[] tooSmallWidths = new int[0];
    private Font font;
    private int tooBigVersion = -1;

    public TvEditorTab(BlockPos pos, TvArea area, List<TvMenu.NetworkChoice> networks, TvEditorActions actions) {
        this.doc = new TvEditorDoc(pos, area);
        this.actions = actions;
        this.menu = new TvCanvasMenu(doc);
        this.components = new TvComponentsPanel(doc, actions);
        this.canvas = new TvEditorCanvas(doc);
        this.palette = new TvPalettePanel(doc, canvas);
        this.base = new TvBaseLine(doc);
        this.bar = new TvEditorBar(doc);
        this.layers = new TvLayersPanel(doc);
        this.props = new TvPropsPanel(doc, networks, actions, popups);
        this.keys = new TvEditorKeys(doc, this::save);
        this.probe = new TvEditorProbe(doc, leftTabs, palette, components, canvas, bar, layers, props, menu,
                popups);
    }

    /** Dispõe as partes na janela ({@code x}, {@code y}, {@code w} × {@code h}), já com o cabeçalho incluso. */
    public void layout(Font font, int x, int y, int w, int h) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        fits = TvEditorLayout.fits(w, h);
        if (!fits) {
            tooSmall = font.split(TvEditorText.key("too_small"), Math.max(40, w - 24));
            tooSmallWidths = new int[tooSmall.size()];
            for (int i = 0; i < tooSmallWidths.length; i++) {
                tooSmallWidths[i] = font.width(tooSmall.get(i));
            }
            return;
        }
        TvEditorLayout layout = TvEditorLayout.of(x, y, w, h);
        popups.close();
        popups.bounds(new TvRect(x, y, w, h));
        leftTabs.layout(font, layout.leftTabs());
        menu.layout(font, new TvRect(x, y, w, h));
        palette.layout(font, layout);
        components.layout(font, layout);
        canvas.layout(font, layout.canvas());
        base.layout(font, layout.base());
        bar.layout(font, layout.buttons());
        layers.layout(font, layout.layers());
        props.layout(font, layout.props());
    }

    /** Uma vez por tick: a Tela no cliente e o aviso de tamanho, que some com a próxima mudança. */
    public void tick() {
        doc.tick();
        if (tooBigVersion >= 0 && tooBigVersion != doc.model().version()) {
            tooBigVersion = -1;
            base.showTooBig(false);
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!fits) {
            int lineY = y + (h - tooSmall.size() * 10) / 2;
            for (int i = 0; i < tooSmall.size(); i++) {
                GuiPaint.text(g, font, tooSmall.get(i), x + (w - tooSmallWidths[i]) / 2, lineY + i * 10,
                        GuiPaint.MUTED);
            }
            return;
        }
        leftTabs.render(g, mouseX, mouseY);
        switch (leftTabs.tab()) {
            case PIECES -> palette.render(g, mouseX, mouseY);
            case COMPONENTS -> components.render(g, mouseX, mouseY);
            case LAYERS -> layers.render(g, mouseX, mouseY);
        }
        base.render(g, mouseX, mouseY);
        canvas.render(g, mouseX, mouseY);
        bar.render(g, mouseX, mouseY);
        props.render(g, mouseX, mouseY);
        menu.render(g, mouseX, mouseY);
        palette.renderGhost(g, mouseX, mouseY);
        TvPopup popup = popups.current();
        if (popup != null) {
            popup.render(g, mouseX, mouseY);
        }
    }

    /**
     * O clique: o menu aberto tem a vez (o de fora só o fecha); o botão direito na miniatura escolhe a peça e abre o
     * menu; o do meio move a vista; o esquerdo vai à coluna de propriedades, à da esquerda (só a aba aberta), à
     * barra e à miniatura.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!fits) {
            return false;
        }
        if (popups.isOpen()) {
            return popupClicked(mouseX, mouseY, button);
        }
        if (menu.isOpen()) {
            TvCanvasMenu.Item item = menu.itemAt(mouseX, mouseY);
            boolean inside = menu.contains(mouseX, mouseY);
            menu.close();
            if (item != null) {
                runMenu(item);
            }
            return inside || button == 0;
        }
        if (button == 1) {
            if (canvas.contextPick(mouseX, mouseY) >= 0) {
                menu.open(mouseX, mouseY);
                return true;
            }
            return false;
        }
        if (button == 2) {
            return canvas.mouseClicked(mouseX, mouseY, button);
        }
        if (props.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        TvLeftTabs.Tab clickedTab = leftTabs.at(mouseX, mouseY);
        if (clickedTab != null) {
            components.cancel();
            leftTabs.select(clickedTab);
            return true;
        }
        if (leftTabs.tab() == TvLeftTabs.Tab.COMPONENTS && components.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        components.cancel();
        if (bar.contains(mouseX, mouseY)) {
            TvEditorBar.Action action = bar.clicked(mouseX, mouseY);
            if (action != null) {
                run(action);
            }
            return true;
        }
        return base.mouseClicked(mouseX, mouseY) || tabBodyClicked(mouseX, mouseY)
                || canvas.mouseClicked(mouseX, mouseY, button);
    }

    /** Com uma janela solta aberta: o clique dentro é dela; o conta-gotas pega a cor da miniatura; fora, fecha. */
    private boolean popupClicked(double mouseX, double mouseY, int button) {
        TvPopup popup = popups.current();
        if (popup.contains(mouseX, mouseY)) {
            popup.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (popup instanceof TvColorPopup color && color.picking()) {
            Integer argb = canvas.pixelAt(mouseX, mouseY);
            if (argb != null) {
                color.picked(argb);
                return true;
            }
        }
        popups.close();
        return button == 0;
    }

    private boolean tabBodyClicked(double mouseX, double mouseY) {
        return switch (leftTabs.tab()) {
            case PIECES -> palette.mouseClicked(mouseX, mouseY);
            case LAYERS -> layers.mouseClicked(mouseX, mouseY);
            case COMPONENTS -> false;
        };
    }

    private void runMenu(TvCanvasMenu.Item item) {
        switch (item) {
            case FRONT -> doc.toFront();
            case UP -> doc.raise();
            case DOWN -> doc.lower();
            case BACK -> doc.toBack();
            case HIDE -> doc.hideSelection();
            case GROUP -> doc.group();
            case UNGROUP -> doc.ungroup();
            case DUPLICATE -> doc.duplicate();
            case REMOVE -> doc.remove();
        }
    }

    private void run(TvEditorBar.Action action) {
        switch (action) {
            case UNDO -> doc.undo();
            case REDO -> doc.redo();
            case GROUP -> doc.group();
            case UNGROUP -> doc.ungroup();
            case SAVE_COMPONENT -> {
                leftTabs.select(TvLeftTabs.Tab.COMPONENTS);
                components.beginSave();
            }
            case DUPLICATE -> doc.duplicate();
            case REMOVE -> doc.remove();
            case SAVE -> save();
        }
    }

    private void save() {
        switch (actions.save(doc.draft())) {
            case SENT -> doc.sent();
            case LOCAL -> doc.savedLocally();
            case TOO_BIG -> {
                tooBigVersion = doc.model().version();
                base.showTooBig(true);
            }
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!fits) {
            return false;
        }
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.mouseDragged(mouseX, mouseY);
        }
        return props.mouseDragged(mouseX, mouseY) || palette.mouseDragged(mouseX, mouseY)
                || canvas.mouseDragged(mouseX, mouseY) || layers.mouseDragged(mouseX, mouseY);
    }

    public boolean mouseReleased(double mouseX, double mouseY) {
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.mouseReleased(mouseX, mouseY);
        }
        boolean propsDrag = props.mouseReleased(mouseX, mouseY);
        boolean paletteDrag = palette.mouseReleased(mouseX, mouseY);
        boolean canvasDrag = canvas.mouseReleased();
        boolean layersDrag = layers.mouseReleased();
        return propsDrag || paletteDrag || canvasDrag || layersDrag;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!fits) {
            return false;
        }
        menu.close();
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.mouseScrolled(mouseX, mouseY, delta);
        }
        if (canvas.mouseScrolled(mouseX, mouseY, delta, Screen.hasControlDown() || e2eCtrl)) {
            return true;
        }
        return props.mouseScrolled(mouseX, mouseY, delta)
                || leftTabs.tab() == TvLeftTabs.Tab.COMPONENTS && components.mouseScrolled(mouseX, mouseY, delta)
                || leftTabs.tab() == TvLeftTabs.Tab.LAYERS && layers.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Uma tecla: o campo de texto com o foco primeiro, depois os atalhos; {@code true} se foi usada. */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!fits) {
            return false;
        }
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.keyPressed(keyCode, scanCode, modifiers);
        }
        return components.keyPressed(keyCode) || props.keyPressed(keyCode, scanCode, modifiers)
                || keys.keyPressed(keyCode, modifiers);
    }

    /** Uma letra: vai ao campo de texto com o foco; sem campo, {@code [} e {@code ]} mudam a camada. */
    public boolean charTyped(char c, int modifiers) {
        if (!fits) {
            return false;
        }
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.charTyped(c, modifiers);
        }
        return components.charTyped(c, modifiers) || props.charTyped(c, modifiers)
                || (!textFocused() && keys.charTyped(c));
    }

    /** Um campo de texto (da coluna de propriedades ou o nome de um componente) está com o teclado. */
    public boolean textFocused() {
        TvPopup popup = popups.current();
        return popup != null && popup.wantsKeys() || props.textFocused() || components.textFocused();
    }

    public @Nullable Component tooltip(double mouseX, double mouseY) {
        if (!fits) {
            return null;
        }
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.tooltip(mouseX, mouseY);
        }
        Component propsTip = props.tooltip(mouseX, mouseY);
        if (propsTip != null) {
            return propsTip;
        }
        Component barTip = bar.tooltip(mouseX, mouseY);
        if (barTip != null) {
            return barTip;
        }
        Component tabTip = leftTabs.tooltip(mouseX, mouseY);
        if (tabTip != null) {
            return tabTip;
        }
        return leftTabs.tab() == TvLeftTabs.Tab.COMPONENTS ? components.tooltip(mouseX, mouseY) : null;
    }

    /** A aba Editar abriu: pede a biblioteca do jogador, uma vez por tela. */
    public void opened() {
        if (!libraryRequested) {
            libraryRequested = true;
            actions.requestLibrary();
        }
    }

    /** O Esc fecha o menu do clique direito; senão, com algo escolhido, solta a escolha. Devolve se usou a tecla. */
    public boolean escape() {
        if (popups.isOpen()) {
            popups.close();
            return true;
        }
        if (menu.isOpen()) {
            menu.close();
            return true;
        }
        if (doc.hasSelection()) {
            doc.select(TvSelection.EMPTY, 0);
            return true;
        }
        return false;
    }

    /** Há mudança não salva (fechar pede confirmação). */
    public boolean dirty() {
        return doc.model().dirty();
    }

    /** Ao fechar a tela: solta a textura da miniatura e a biblioteca. */
    public void release() {
        TvEditorImage.release();
        TvLibraryClient.clear();
    }

    public TvLayout draft() {
        return doc.draft();
    }

    public TvSelection selection() {
        return doc.selection();
    }

    public boolean changedOnServer() {
        return doc.changedOnServer();
    }

    public boolean fits() {
        return fits;
    }

    /** Os ganchos do e2e e das capturas. */
    public TvEditorProbe probe() {
        return probe;
    }
}
