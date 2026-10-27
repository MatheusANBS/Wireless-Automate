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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Editar da tela da Tela de Sinal: a anfitriã das partes do editor ({@link TvPalettePanel},
 * {@link TvEditorCanvas}, {@link TvBaseLine}, {@link TvEditorBar}, {@link TvLayersPanel} e {@link TvSelectionInfo}),
 * dispostas pelo {@link TvEditorLayout}, e da coluna de propriedades ({@link TvPropsPanel}). Repassa o desenho, o
 * mouse, a roda e o teclado ({@link TvEditorKeys}, com o campo de texto focado tendo a vez) e faz o salvar pelo
 * {@link TvEditorActions}; o rascunho só vai ao servidor no Salvar. Numa janela abaixo do mínimo, só a mensagem
 * pedindo uma escala menor. Os ganchos do e2e ficam aqui.
 */
public final class TvEditorTab {
    /** O Shift do e2e (a janela de teste não tem teclado de verdade). */
    public static boolean e2eShift;

    private final TvEditorDoc doc;
    private final TvEditorActions actions;
    private final TvPalettePanel palette;
    private final TvEditorCanvas canvas;
    private final TvBaseLine base;
    private final TvEditorBar bar;
    private final TvLayersPanel layers;
    private final TvPropsPanel props;
    private final TvEditorKeys keys;
    private boolean fits;
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
        this.palette = new TvPalettePanel(doc);
        this.canvas = new TvEditorCanvas(doc);
        this.base = new TvBaseLine(doc);
        this.bar = new TvEditorBar(doc);
        this.layers = new TvLayersPanel(doc);
        this.props = new TvPropsPanel(doc, networks);
        this.keys = new TvEditorKeys(doc, this::save);
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
        palette.layout(font, layout);
        canvas.layout(layout.canvas());
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
        palette.render(g, mouseX, mouseY);
        base.render(g, mouseX, mouseY);
        canvas.render(g, mouseX, mouseY);
        bar.render(g, mouseX, mouseY);
        layers.render(g, mouseX, mouseY);
        props.render(g, mouseX, mouseY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!fits) {
            return false;
        }
        if (props.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (bar.contains(mouseX, mouseY)) {
            TvEditorBar.Action action = bar.clicked(mouseX, mouseY);
            if (action != null) {
                run(action);
            }
            return true;
        }
        return base.mouseClicked(mouseX, mouseY) || palette.mouseClicked(mouseX, mouseY)
                || layers.mouseClicked(mouseX, mouseY) || canvas.mouseClicked(mouseX, mouseY);
    }

    private void run(TvEditorBar.Action action) {
        switch (action) {
            case UNDO -> doc.undo();
            case REDO -> doc.redo();
            case GROUP -> doc.group();
            case UNGROUP -> doc.ungroup();
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
        return fits && (canvas.mouseDragged(mouseX, mouseY) || layers.mouseDragged(mouseX, mouseY));
    }

    public boolean mouseReleased() {
        boolean canvasDrag = canvas.mouseReleased();
        boolean layersDrag = layers.mouseReleased();
        return canvasDrag || layersDrag;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return fits && (props.mouseScrolled(mouseX, mouseY, delta) || layers.mouseScrolled(mouseX, mouseY, delta));
    }

    /** Uma tecla: o campo de texto com o foco primeiro, depois os atalhos; {@code true} se foi usada. */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!fits) {
            return false;
        }
        return props.keyPressed(keyCode, scanCode, modifiers) || keys.keyPressed(keyCode, modifiers);
    }

    /** Uma letra: vai ao campo de texto com o foco; sem campo, {@code [} e {@code ]} mudam a camada. */
    public boolean charTyped(char c, int modifiers) {
        if (!fits) {
            return false;
        }
        return props.charTyped(c, modifiers) || (!props.textFocused() && keys.charTyped(c));
    }

    /** Um campo de texto da coluna de propriedades está com o teclado. */
    public boolean textFocused() {
        return props.textFocused();
    }

    public @Nullable Component tooltip(double mouseX, double mouseY) {
        return fits ? bar.tooltip(mouseX, mouseY) : null;
    }

    /** O Esc com algo escolhido solta a escolha; devolve se usou a tecla. */
    public boolean escape() {
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

    /** Ao fechar a tela: solta a textura da miniatura. */
    public void release() {
        TvEditorImage.release();
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

    public int[] paletteCenter(TvPieceType type) {
        return palette.center(type);
    }

    public int[] pieceCenter(int index) {
        return canvas.pieceCenter(index);
    }

    public int[] handleCenter() {
        return canvas.handleCenter();
    }

    public float canvasScale() {
        return canvas.scale();
    }

    public int[] barCenter(String action) {
        return bar.center(TvEditorBar.Action.valueOf(action));
    }

    public int @Nullable [] layerCenter(int index) {
        return layers.rowCenter(index);
    }

    public boolean fits() {
        return fits;
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** As abas da coluna de propriedades pelo nome: {@code DATA}, {@code FORMAT}, {@code STYLE}, {@code RULES}. */
    public int[] propsTabCenter(String tab) {
        return props.tabCenter(TvPropsPanel.Tab.valueOf(tab));
    }

    public String propsTab() {
        return props.tab().name();
    }

    /** O centro do controle de {@code id} (rolado até aparecer), ou {@code null}. */
    public int @Nullable [] propCenter(String id) {
        return props.center(id);
    }

    public int @Nullable [] propNetworkCenter(String id, UUID network) {
        return props.networkCenter(id, network);
    }

    public int @Nullable [] propSwatchCenter(String id, int value) {
        return props.swatchCenter(id, value);
    }

    public int @Nullable [] propArrowCenter(String id, boolean right) {
        return props.arrowCenter(id, right);
    }

    public int @Nullable [] propChipCenter(String id, int index) {
        return props.chipCenter(id, index);
    }

    /** Dá o foco a um campo de texto (como um clique nele). */
    public boolean focusProp(String id) {
        return props.focusField(id);
    }

    public int undoSize() {
        return doc.model().history().undoSize();
    }

    public boolean canRedo() {
        return doc.model().history().canRedo();
    }
}
