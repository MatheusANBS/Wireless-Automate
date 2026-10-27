package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvEditorImage;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * A miniatura do editor: a imagem do rascunho ({@link TvEditorImage}, pintada pelo mesmo desenho da parede) na maior
 * escala que cabe (inteira quando passa de 1), e por cima, na GUI, a grade de {@link TvSnap#GRID} px, as linhas de
 * bloco, a peça sob o mouse, as caixas da escolha e a alça. O clique escolhe pela regra do mockup, arrastar move a
 * escolha e a alça redimensiona (uma peça, ou o grupo em proporção), tudo pelo {@link TvEditOps} a partir das peças de
 * quando o arrasto começou, com o encaixe da grade (1 px com o Alt). Por quadro: um pedido à imagem (comparações), os
 * retângulos e uma busca da peça sob o mouse, sem alocar.
 */
final class TvEditorCanvas {
    private static final TvEditOps.Access<TvPiece> A = TvPieceAccess.INSTANCE;
    private static final long TWICE_MS = 350;
    private static final int HANDLE = 5;
    /** O vidro apagado atrás da imagem, como o do bloco; o layout vazio da etapa 1 só mostra ele, como na parede. */
    private static final int GLASS = 0xFF0B0C10;

    private enum Drag { NONE, MOVE, RESIZE }

    private final TvEditorDoc doc;
    private TvRect rect = TvRect.NONE;
    /** A imagem na tela: canto, tamanho e escala (pixels da GUI por pixel de desenho). */
    private int imageX;
    private int imageY;
    private int imageW;
    private int imageH;
    private float scale = 1;
    private TvArea fittedArea;
    private Drag drag = Drag.NONE;
    private List<TvPiece> dragFrom = List.of();
    private double dragX;
    private double dragY;
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

    void layout(TvRect rect) {
        this.rect = rect;
        fittedArea = null;
    }

    /** A escala e o lugar da imagem, refeitos só quando a área da Tela ou o retângulo mudam. */
    private void fit() {
        TvArea area = doc.area();
        if (area.equals(fittedArea)) {
            return;
        }
        fittedArea = area;
        int innerW = Math.max(1, rect.w() - 4);
        int innerH = Math.max(1, rect.h() - 4);
        float fit = Math.min(innerW / (float) area.pixelWidth(), innerH / (float) area.pixelHeight());
        scale = fit >= 1 ? (float) Math.floor(fit) : fit;
        imageW = Math.max(1, Math.round(area.pixelWidth() * scale));
        imageH = Math.max(1, Math.round(area.pixelHeight() * scale));
        imageX = rect.x() + (rect.w() - imageW) / 2;
        imageY = rect.y() + (rect.h() - imageH) / 2;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        fit();
        GuiPaint.inset(g, rect.x(), rect.y(), rect.w(), rect.h());
        g.fill(imageX, imageY, imageX + imageW, imageY + imageH, GLASS);
        if (!doc.draft().blank()) {
            requestImage();
            TvEditorImage.blit(g, imageX, imageY, imageW, imageH);
        }
        grid(g);
        List<TvPiece> pieces = doc.pieces();
        boxes(pieces);
        int hover = drag == Drag.NONE && inImage(mouseX, mouseY)
                ? TvEditOps.hit(A, pieces, toX(mouseX), toY(mouseY)) : -1;
        if (hover >= 0 && !doc.selection().contains(hover)) {
            outline(g, pieces.get(hover), GuiPaint.alpha(GuiPaint.FG, 0x90));
        }
        selection(g, pieces);
        if (resizable) {
            GuiPaint.box(g, handleX(), handleY(), HANDLE, HANDLE, GuiPaint.ACCENT, GuiPaint.ACCENT_DEEP);
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

    /** O tamanho da textura em pixels da janela: o da imagem vezes a escala da GUI, até o teto da placa. */
    private void requestImage() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        double max = RenderSystem.maxSupportedTextureSize();
        double k = Math.min(gui, Math.min(max / imageW, max / imageH));
        TvArea area = doc.area();
        int texW = (int) Math.round(imageW * k);
        int texH = (int) Math.round(texW * area.pixelHeight() / (double) area.pixelWidth());
        TvEditorImage.request(doc.draft(), doc.model().saved(), area, doc.pos().asLong(), doc.revision(), texW,
                texH);
    }

    /** A grade só onde as linhas ficam a 3 px ou mais umas das outras; senão só as de bloco, ou nenhuma. */
    private void grid(GuiGraphics g) {
        if (TvArea.PIXELS_PER_BLOCK * scale < 3) {
            return;
        }
        TvArea area = doc.area();
        int fine = GuiPaint.alpha(GuiPaint.MUTED, 0x38);
        int block = GuiPaint.alpha(GuiPaint.ACCENT, 0x70);
        int stepPx = TvSnap.GRID * scale >= 3 ? TvSnap.GRID : TvArea.PIXELS_PER_BLOCK;
        for (int px = stepPx; px < area.pixelWidth(); px += stepPx) {
            int x = imageX + Math.round(px * scale);
            g.fill(x, imageY, x + 1, imageY + imageH, px % TvArea.PIXELS_PER_BLOCK == 0 ? block : fine);
        }
        for (int py = stepPx; py < area.pixelHeight(); py += stepPx) {
            int y = imageY + Math.round(py * scale);
            g.fill(imageX, y, imageX + imageW, y + 1, py % TvArea.PIXELS_PER_BLOCK == 0 ? block : fine);
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
        GuiPaint.outline(g, screenX(p.x()), screenY(p.y()), Math.max(2, Math.round(p.width() * scale)),
                Math.max(2, Math.round(p.height() * scale)), color);
    }

    private void box(GuiGraphics g, int[] b, int color) {
        GuiPaint.outline(g, screenX(b[0]) - 1, screenY(b[1]) - 1, Math.round((b[2] - b[0]) * scale) + 2,
                Math.round((b[3] - b[1]) * scale) + 2, color);
    }

    /** O canto da alça, centrada no canto de baixo à direita da escolha. */
    private int handleX() {
        return screenX(selectionBox[2]) - HANDLE / 2 - 1;
    }

    private int handleY() {
        return screenY(selectionBox[3]) - HANDLE / 2 - 1;
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!rect.contains(mouseX, mouseY)) {
            return false;
        }
        fit();
        List<TvPiece> pieces = doc.pieces();
        boxes(pieces);
        if (resizable && mouseX >= handleX() - 1 && mouseX < handleX() + HANDLE + 1 && mouseY >= handleY() - 1
                && mouseY < handleY() + HANDLE + 1) {
            start(Drag.RESIZE, mouseX, mouseY);
            return true;
        }
        int hit = inImage(mouseX, mouseY) ? TvEditOps.hit(A, pieces, toX(mouseX), toY(mouseY)) : -1;
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

    private void start(Drag mode, double mouseX, double mouseY) {
        drag = mode;
        dragFrom = doc.pieces();
        dragX = mouseX;
        dragY = mouseY;
        lastDx = 0;
        lastDy = 0;
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (drag == Drag.NONE) {
            return false;
        }
        boolean fine = Screen.hasAltDown();
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
        drag = Drag.NONE;
        dragFrom = List.of();
        return true;
    }

    private boolean inImage(double mouseX, double mouseY) {
        return mouseX >= imageX && mouseX < imageX + imageW && mouseY >= imageY && mouseY < imageY + imageH;
    }

    private double toX(double mouseX) {
        return (mouseX - imageX) / scale;
    }

    private double toY(double mouseY) {
        return (mouseY - imageY) / scale;
    }

    private int screenX(int px) {
        return imageX + Math.round(px * scale);
    }

    private int screenY(int py) {
        return imageY + Math.round(py * scale);
    }

    /** O centro de uma peça na tela (gancho do e2e). */
    int[] pieceCenter(int index) {
        fit();
        TvPiece p = doc.pieces().get(index);
        return new int[] {screenX(p.x()) + Math.round(p.width() * scale / 2), screenY(p.y())
                + Math.round(p.height() * scale / 2)};
    }

    /** O centro da alça (gancho do e2e). */
    int[] handleCenter() {
        fit();
        boxes(doc.pieces());
        return new int[] {handleX() + HANDLE / 2, handleY() + HANDLE / 2};
    }

    /** Pixels da GUI por pixel de desenho (gancho do e2e, para arrastar uma medida certa). */
    float scale() {
        fit();
        return scale;
    }
}
