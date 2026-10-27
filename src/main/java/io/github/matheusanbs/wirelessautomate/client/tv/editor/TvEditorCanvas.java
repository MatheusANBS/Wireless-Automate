package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvEditorImage;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A miniatura do editor: a imagem do rascunho ({@link TvEditorImage}, pintada pelo mesmo desenho da parede) na maior
 * escala que cabe (inteira quando passa de 1), com zoom de Ctrl + roda até 8 vezes e a vista movida pelo botão do meio
 * ou por Espaço + arrasto ({@link TvCanvasView}; o botão "Caber" volta), e por cima, na GUI, a grade de
 * {@link TvSnap#GRID} px, as linhas de bloco, a peça sob o mouse, as caixas da escolha e a alça. O clique escolhe pela
 * regra do mockup (Alt + clique, a peça de baixo na mesma posição, pelo {@link TvHitCycle}), arrastar move a escolha e
 * a alça redimensiona (uma peça, ou o grupo em proporção), tudo pelo {@link TvEditOps} a partir das peças de quando o
 * arrasto começou, com o encaixe da grade (1 px com o Alt). O clique direito escolhe a peça e quem usa abre o
 * {@link TvCanvasMenu}. A textura da imagem é pedida no tamanho de "caber" e só refeita quando o rascunho muda, nunca
 * por zoom. Por quadro: um pedido à imagem (comparações), os retângulos e uma busca da peça sob o mouse, sem alocar.
 */
final class TvEditorCanvas {
    private static final TvEditOps.Access<TvPiece> A = TvPieceAccess.INSTANCE;
    private static final long TWICE_MS = 350;
    private static final int HANDLE = 5;
    /** O vidro apagado atrás da imagem, como o do bloco; o layout vazio da etapa 1 só mostra ele, como na parede. */
    private static final int GLASS = 0xFF0B0C10;

    private enum Drag { NONE, MOVE, RESIZE, PAN }

    private final TvEditorDoc doc;
    private final TvCanvasView view = new TvCanvasView();
    private TvRect rect = TvRect.NONE;
    private Font font;
    private TvRect fitButton = TvRect.NONE;
    private TvEditorPaint.Label fitLabel;
    private int fitLabelFactor;
    private Drag drag = Drag.NONE;
    private List<TvPiece> dragFrom = List.of();
    private double dragX;
    private double dragY;
    private double panX;
    private double panY;
    private int lastDx;
    private int lastDy;
    private int lastHit = -1;
    private long lastClick;
    /** As caixas da escolha e do grupo em que se entrou, refeitas só quando o rascunho ou a escolha mudam. */
    private int[] selectionBox = new int[4];
    private int[] focusBox = new int[4];
    private boolean resizable;
    private int boxVersion = -1;
    private TvSelection boxSelection;
    private int boxFocus;

    TvEditorCanvas(TvEditorDoc doc) {
        this.doc = doc;
    }

    void layout(Font font, TvRect rect) {
        this.font = font;
        this.rect = rect;
        view.layout(rect);
        fitLabelFactor = 0;
    }

    /** A escala e o lugar da imagem, refeitos só quando a área da Tela ou o retângulo mudam. */
    private void fit() {
        view.fit(doc.area());
    }

    /** O botão "Caber" no canto de cima da miniatura, com o fator do zoom; refeito só quando o fator muda. */
    private void fitButton() {
        if (fitLabelFactor == view.factor()) {
            return;
        }
        fitLabelFactor = view.factor();
        fitLabel = TvEditorPaint.label(font, TvEditorText.key("zoom_fit", fitLabelFactor), rect.w() - 12);
        int w = fitLabel.width() + 10;
        fitButton = new TvRect(rect.right() - w - 3, rect.y() + 3, w, 11);
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        fit();
        GuiPaint.inset(g, rect.x(), rect.y(), rect.w(), rect.h());
        g.enableScissor(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1);
        g.fill(view.x(), view.y(), view.x() + view.width(), view.y() + view.height(), GLASS);
        if (!doc.draft().blank()) {
            requestImage();
            TvEditorImage.blit(g, view.x(), view.y(), view.width(), view.height());
        }
        if (doc.gridVisible()) {
            grid(g);
        }
        List<TvPiece> pieces = doc.pieces();
        boxes(pieces);
        int hover = drag == Drag.NONE && view.contains(mouseX, mouseY)
                ? TvEditOps.hit(A, pieces, view.toX(mouseX), view.toY(mouseY)) : -1;
        if (hover >= 0 && !doc.selection().contains(hover)) {
            outline(g, pieces.get(hover), GuiPaint.alpha(GuiPaint.FG, 0x90));
        }
        selection(g, pieces);
        if (resizable) {
            GuiPaint.box(g, handleX(), handleY(), HANDLE, HANDLE, GuiPaint.ACCENT, GuiPaint.ACCENT_DEEP);
        }
        g.disableScissor();
        if (view.zoomed()) {
            fitButton();
            TvEditorPaint.button(g, font, fitButton, fitLabel, fitButton.contains(mouseX, mouseY), true);
        }
    }

    private void boxes(List<TvPiece> pieces) {
        TvSelection selection = doc.selection();
        int focus = doc.model().focusGroup();
        if (boxVersion == doc.model().version() && selection == boxSelection && focus == boxFocus) {
            return;
        }
        boxVersion = doc.model().version();
        boxSelection = selection;
        boxFocus = focus;
        boolean valid = !selection.isEmpty() && selection.last() < pieces.size();
        selectionBox = valid ? TvEditOps.bounds(A, pieces, selection) : new int[4];
        focusBox = focus != 0 ? TvEditOps.bounds(A, pieces, TvEditOps.members(A, pieces, focus)) : new int[4];
        resizable = valid && TvEditOps.resizable(A, pieces, selection);
    }

    /** O tamanho da textura em pixels da janela: o de "caber" vezes a escala da GUI, até o teto da placa. */
    private void requestImage() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        double max = RenderSystem.maxSupportedTextureSize();
        int fitW = view.fitWidth();
        int fitH = view.fitHeight();
        double k = Math.min(gui, Math.min(max / fitW, max / fitH));
        TvArea area = doc.area();
        int texW = (int) Math.round(fitW * k);
        int texH = (int) Math.round(texW * area.pixelHeight() / (double) area.pixelWidth());
        TvEditorImage.request(doc.draft(), doc.model().saved(), area, doc.pos().asLong(), doc.revision(), texW,
                texH);
    }

    /** A grade só onde as linhas ficam a 3 px ou mais umas das outras; senão só as de bloco, ou nenhuma. */
    private void grid(GuiGraphics g) {
        float scale = view.scale();
        if (TvArea.PIXELS_PER_BLOCK * scale < 3) {
            return;
        }
        TvArea area = doc.area();
        int fine = GuiPaint.alpha(GuiPaint.MUTED, 0x38);
        int block = GuiPaint.alpha(GuiPaint.ACCENT, 0x70);
        int stepPx = TvSnap.GRID * scale >= 3 ? TvSnap.GRID : TvArea.PIXELS_PER_BLOCK;
        int left = view.x();
        int right = left + view.width();
        int top = view.y();
        int bottom = top + view.height();
        for (int px = stepPx; px < area.pixelWidth(); px += stepPx) {
            int x = view.screenX(px);
            g.fill(x, top, x + 1, bottom, px % TvArea.PIXELS_PER_BLOCK == 0 ? block : fine);
        }
        for (int py = stepPx; py < area.pixelHeight(); py += stepPx) {
            int y = view.screenY(py);
            g.fill(left, y, right, y + 1, py % TvArea.PIXELS_PER_BLOCK == 0 ? block : fine);
        }
    }

    private void selection(GuiGraphics g, List<TvPiece> pieces) {
        TvSelection selection = doc.selection();
        if (selection.isEmpty() || selection.last() >= pieces.size()) {
            return;
        }
        if (doc.model().focusGroup() != 0) {
            box(g, focusBox, GuiPaint.alpha(GuiPaint.ACCENT, 0x80));
        }
        if (selection.size() > 1) {
            for (int k = 0; k < selection.size(); k++) {
                outline(g, pieces.get(selection.get(k)), GuiPaint.alpha(GuiPaint.ACCENT, 0x90));
            }
        }
        box(g, selectionBox, GuiPaint.ACCENT);
    }

    private void outline(GuiGraphics g, TvPiece p, int color) {
        float scale = view.scale();
        GuiPaint.outline(g, view.screenX(p.x()), view.screenY(p.y()), Math.max(2, Math.round(p.width() * scale)),
                Math.max(2, Math.round(p.height() * scale)), color);
    }

    private void box(GuiGraphics g, int[] b, int color) {
        float scale = view.scale();
        GuiPaint.outline(g, view.screenX(b[0]) - 1, view.screenY(b[1]) - 1, Math.round((b[2] - b[0]) * scale) + 2,
                Math.round((b[3] - b[1]) * scale) + 2, color);
    }

    /** O canto da alça, centrada no canto de baixo à direita da escolha. */
    private int handleX() {
        return view.screenX(selectionBox[2]) - HANDLE / 2 - 1;
    }

    private int handleY() {
        return view.screenY(selectionBox[3]) - HANDLE / 2 - 1;
    }

    /**
     * O clique na miniatura: o "Caber", o arrasto da vista (botão do meio, ou Espaço com o botão esquerdo, já com
     * zoom), a alça e a escolha da peça. Os outros botões não fazem nada aqui.
     */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!rect.contains(mouseX, mouseY)) {
            return false;
        }
        fit();
        if (view.zoomed()) {
            fitButton();
            if (button == 0 && fitButton.contains(mouseX, mouseY)) {
                view.reset();
                return true;
            }
            if (button == 2 || button == 0 && spaceDown()) {
                start(Drag.PAN, mouseX, mouseY);
                return true;
            }
        }
        if (button != 0) {
            return true;
        }
        List<TvPiece> pieces = doc.pieces();
        boxes(pieces);
        if (resizable && mouseX >= handleX() - 1 && mouseX < handleX() + HANDLE + 1 && mouseY >= handleY() - 1
                && mouseY < handleY() + HANDLE + 1) {
            start(Drag.RESIZE, mouseX, mouseY);
            return true;
        }
        boolean inside = view.contains(mouseX, mouseY);
        if (inside && Screen.hasAltDown()) {
            int below = TvHitCycle.next(A, pieces, view.toX(mouseX), view.toY(mouseY), doc.selection());
            if (below >= 0) {
                doc.select(TvSelection.of(below), pieces.get(below).group());
                start(Drag.MOVE, mouseX, mouseY);
                return true;
            }
        }
        int hit = inside ? TvEditOps.hit(A, pieces, view.toX(mouseX), view.toY(mouseY)) : -1;
        long now = System.currentTimeMillis();
        boolean twice = hit >= 0 && hit == lastHit && now - lastClick < TWICE_MS;
        lastHit = hit;
        lastClick = now;
        boolean shift = Screen.hasShiftDown() || TvEditorTab.e2eShift;
        TvEditOps.Pick pick = TvEditOps.click(A, pieces, doc.selection(), doc.model().focusGroup(), hit, shift, twice);
        doc.select(pick.selection(), pick.focusGroup());
        if (hit >= 0 && !shift && pick.selection().contains(hit)) {
            start(Drag.MOVE, mouseX, mouseY);
        }
        return true;
    }

    /**
     * O clique direito: escolhe a peça sob o mouse (se ela já está na escolha, a escolha fica) e devolve o índice
     * dela, ou -1 sem peça ali; quem chama abre o menu.
     */
    int contextPick(double mouseX, double mouseY) {
        if (!rect.contains(mouseX, mouseY)) {
            return -1;
        }
        fit();
        List<TvPiece> pieces = doc.pieces();
        int hit = view.contains(mouseX, mouseY) ? TvEditOps.hit(A, pieces, view.toX(mouseX), view.toY(mouseY)) : -1;
        if (hit >= 0 && !doc.selection().contains(hit)) {
            TvEditOps.Pick pick = TvEditOps.click(A, pieces, doc.selection(), doc.model().focusGroup(), hit, false,
                    false);
            doc.select(pick.selection(), pick.focusGroup());
        }
        return hit;
    }

    /** Ctrl + roda sobre a miniatura: um passo de zoom centrado no mouse; sem Ctrl a roda não é daqui. */
    boolean mouseScrolled(double mouseX, double mouseY, double delta, boolean ctrl) {
        if (!ctrl || !rect.contains(mouseX, mouseY)) {
            return false;
        }
        fit();
        view.zoom(delta > 0 ? 1 : -1, mouseX, mouseY);
        return true;
    }

    private static boolean spaceDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_SPACE);
    }

    private void start(Drag mode, double mouseX, double mouseY) {
        drag = mode;
        if (mode != Drag.PAN) {
            doc.beginGesture();
        }
        dragFrom = doc.pieces();
        dragX = mouseX;
        dragY = mouseY;
        panX = mouseX;
        panY = mouseY;
        lastDx = 0;
        lastDy = 0;
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (drag == Drag.NONE) {
            return false;
        }
        if (drag == Drag.PAN) {
            view.pan((int) Math.round(mouseX - panX), (int) Math.round(mouseY - panY));
            panX = mouseX;
            panY = mouseY;
            return true;
        }
        boolean fine = Screen.hasAltDown();
        float scale = view.scale();
        int dx = TvSnap.delta((mouseX - dragX) / scale, fine);
        int dy = TvSnap.delta((mouseY - dragY) / scale, fine);
        if (dx == lastDx && dy == lastDy) {
            return true;
        }
        lastDx = dx;
        lastDy = dy;
        TvArea area = doc.area();
        TvSelection selection = doc.selection();
        List<TvPiece> moved = drag == Drag.MOVE
                ? TvEditOps.move(A, dragFrom, selection, dx, dy, area.pixelWidth(), area.pixelHeight())
                : TvEditOps.resize(A, dragFrom, selection, dx, dy, area.pixelWidth(), area.pixelHeight());
        doc.apply(moved, selection);
        return true;
    }

    boolean mouseReleased() {
        if (drag == Drag.NONE) {
            return false;
        }
        if (drag != Drag.PAN) {
            doc.endGesture();
        }
        drag = Drag.NONE;
        dragFrom = List.of();
        return true;
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** O centro de uma peça na tela. */
    int[] pieceCenter(int index) {
        fit();
        TvPiece p = doc.pieces().get(index);
        float scale = view.scale();
        return new int[] {view.screenX(p.x()) + Math.round(p.width() * scale / 2), view.screenY(p.y())
                + Math.round(p.height() * scale / 2)};
    }

    /** O centro da alça. */
    int[] handleCenter() {
        fit();
        boxes(doc.pieces());
        return new int[] {handleX() + HANDLE / 2, handleY() + HANDLE / 2};
    }

    /** Pixels da GUI por pixel de desenho (para arrastar uma medida certa). */
    float scale() {
        fit();
        return view.scale();
    }

    /** O fator do zoom, 1 em "caber". */
    int zoomFactor() {
        fit();
        return view.factor();
    }

    /** O centro do botão "Caber", ou {@code null} sem zoom. */
    int @Nullable [] fitCenter() {
        if (!view.zoomed()) {
            return null;
        }
        fitButton();
        return fitButton.center();
    }

    /** O centro da miniatura. */
    int[] center() {
        return rect.center();
    }
}
