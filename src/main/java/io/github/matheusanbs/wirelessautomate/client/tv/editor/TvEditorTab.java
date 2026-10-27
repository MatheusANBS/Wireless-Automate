package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvEditorImage;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Editar da tela da Tela de Sinal: a anfitriã das partes do editor ({@link TvPalettePanel},
 * {@link TvEditorCanvas}, {@link TvBaseLine}, {@link TvEditorBar}, {@link TvLayersPanel} e {@link TvSelectionInfo}),
 * dispostas pelo {@link TvEditorLayout}. Repassa o desenho, o mouse e a roda a cada uma e faz o salvar pelo
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
    private final TvSelectionInfo info;
    private boolean fits;
    private int x;
    private int y;
    private int w;
    private int h;
    private List<FormattedCharSequence> tooSmall = List.of();
    private int[] tooSmallWidths = new int[0];
    private Font font;
    private int tooBigVersion = -1;

    public TvEditorTab(BlockPos pos, TvArea area, TvEditorActions actions) {
        this.doc = new TvEditorDoc(pos, area);
        this.actions = actions;
        this.palette = new TvPalettePanel(doc);
        this.canvas = new TvEditorCanvas(doc);
        this.base = new TvBaseLine(doc);
        this.bar = new TvEditorBar(doc);
        this.layers = new TvLayersPanel(doc);
        this.info = new TvSelectionInfo(doc);
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
        info.layout(font, layout.props());
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
        info.render(g);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!fits) {
            return false;
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
        return fits && layers.mouseScrolled(mouseX, mouseY, delta);
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
}
