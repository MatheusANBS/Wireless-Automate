package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

/**
 * O estado inteiro de uma edição no editor de pixel art, sem tela: a arte, o desfazer, a seleção, a vista, a
 * ferramenta e as ajudas (pincel, espelhos, pixel perfect, grade), as cores atual e anterior e a área de cópia (que
 * vale enquanto o editor está aberto). A tela traduz o mouse em pixels da arte e as teclas em comandos e chama os
 * métodos daqui; tudo o que muda a arte guarda um passo do desfazer antes (decisões 1a a 1g da spec da etapa 4).
 * Antes de um passo novo, um pedaço solto assenta, para o Esc só desfazer o mover que ainda está no ar. Lógica pura,
 * com JUnit.
 * <p>
 * Os quadros da animação: escolher um quadro não é um passo (o pedaço solto assenta no quadro de antes, e a área de
 * cópia vale entre quadros); adicionar, duplicar, remover, mudar a ordem, a velocidade e o modo são. Virar, girar e
 * mudar o tamanho sem seleção valem para todos os quadros (como o "Sprite > Flip/Rotate Canvas" do Aseprite: os
 * quadros têm sempre o mesmo tamanho), a paleta é uma só, e o Limpar e as ferramentas de pintar ficam no quadro
 * escolhido ({@link TvPixelFrames}).
 */
public final class TvPixelSession {
    private enum Drag { NONE, STROKE, MARK, MOVE }

    private final TvPixelCanvas canvas;
    private final TvPixelHistory history = new TvPixelHistory();
    private final TvPixelSelection selection = new TvPixelSelection();
    private final TvPixelView view = new TvPixelView();
    private final TvPixelTools tools;
    private final TvPixelStroke stroke;
    private TvPixelTool tool = TvPixelTool.PEN;
    private boolean rectFilled;
    private boolean ellipseFilled;
    private boolean mirrorH;
    private boolean mirrorV;
    private boolean perfect;
    private boolean grid = true;
    private int brush = TvPixelBrush.MIN;
    private boolean round;
    private int current = 6;
    private int previous = 1;
    private TvPixelSelection.Clip clipboard;
    private boolean colorEditing;
    private int cleanVersion;
    private Drag drag = Drag.NONE;
    private int dragX;
    private int dragY;
    private int pieceX;
    private int pieceY;
    private final TvPixelFrames frames = new TvPixelFrames(this);

    public TvPixelSession(TvPixelCanvas canvas) {
        this.canvas = canvas;
        this.tools = new TvPixelTools(canvas);
        this.stroke = new TvPixelStroke(canvas, tools);
        this.cleanVersion = canvas.version();
    }

    public TvPixelCanvas canvas() {
        return canvas;
    }

    public TvPixelSelection selection() {
        return selection;
    }

    public TvPixelView view() {
        return view;
    }

    public TvPixelHistory history() {
        return history;
    }

    /** Os quadros da animação e o que a prévia mostra. */
    public TvPixelFrames frames() {
        return frames;
    }

    // ------------------------------------------------------------------ o mouse no quadro

    /**
     * O botão apertado no pixel ({@code x}, {@code y}) da arte. Ctrl (ou o conta-gotas) pega a cor; a seleção marca
     * ou, de dentro, levanta e move; o resto pinta, com o direito apagando (no clarear, escurecendo).
     */
    public void press(int x, int y, boolean right, boolean shift, boolean ctrl) {
        if (!right && (tool == TvPixelTool.PICK || ctrl)) {
            if (canvas.inside(x, y)) {
                choose(canvas.get(x, y));
            }
            return;
        }
        if (tool == TvPixelTool.SELECT) {
            if (selection.contains(x, y)) {
                liftIfMarked();
                drag = Drag.MOVE;
                pieceX = selection.x();
                pieceY = selection.y();
            } else {
                selection.commit(canvas);
                selection.clear();
                drag = Drag.MARK;
            }
            dragX = x;
            dragY = y;
            return;
        }
        if (!canvas.inside(x, y)) {
            return;
        }
        TvPixelTool used = right && tool != TvPixelTool.SHADE
                ? tool.erasesWithSelf() ? tool : TvPixelTool.ERASE : tool;
        int index = right || used == TvPixelTool.ERASE ? 0 : current;
        beginStep();
        applyHelpers();
        if (used == TvPixelTool.FILL) {
            tools.fill(x, y, index, shift);
            history.dropIfUnchanged(canvas);
            return;
        }
        boolean filled = used == TvPixelTool.RECT ? rectFilled : ellipseFilled;
        stroke.begin(used, index, x, y, right ? -1 : 1, perfect && brush == 1 && used == TvPixelTool.PEN, filled);
        drag = Drag.STROKE;
    }

    /** O mouse andou com o botão apertado, agora sobre o pixel ({@code x}, {@code y}). */
    public void drag(int x, int y, boolean shift) {
        switch (drag) {
            case STROKE -> stroke.moveTo(x, y, shift);
            case MOVE -> selection.moveTo(pieceX + x - dragX, pieceY + y - dragY);
            case MARK -> {
                int cx = clamp(x, canvas.width());
                int cy = clamp(y, canvas.height());
                int sx = clamp(dragX, canvas.width());
                int sy = clamp(dragY, canvas.height());
                selection.select(canvas, Math.min(sx, cx), Math.min(sy, cy), Math.abs(cx - sx) + 1,
                        Math.abs(cy - sy) + 1);
            }
            default -> {
            }
        }
    }

    /** O botão foi solto: o traço que não mudou nada sai do desfazer. */
    public void release() {
        if (drag == Drag.STROKE) {
            history.dropIfUnchanged(canvas);
        }
        drag = Drag.NONE;
    }

    public boolean dragging() {
        return drag != Drag.NONE;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    /** Um passo novo do desfazer: o pedaço solto assenta antes, e a arte de agora fica guardada. */
    void beginStep() {
        selection.commit(canvas);
        history.push(canvas.snapshot());
    }

    private void applyHelpers() {
        tools.mirrors(mirrorH, mirrorV);
        tools.brush(brush, round);
        if (selection.active() && !selection.floating()) {
            tools.mask(selection.x(), selection.y(), selection.width(), selection.height());
        } else {
            tools.mask(0, 0, 0, 0);
        }
    }

    /** Levanta o retângulo marcado num passo do desfazer (cada mover de pedaço é um passo). */
    private void liftIfMarked() {
        if (selection.active() && !selection.floating()) {
            history.push(canvas.snapshot());
            selection.lift(canvas);
        }
    }

    // ------------------------------------------------------------------ ferramenta, pincel, cor

    public TvPixelTool tool() {
        return tool;
    }

    /** Escolhe a ferramenta; o retângulo e a elipse escolhidos de novo alternam contorno e cheio. */
    public void pickTool(TvPixelTool chosen) {
        if (chosen == tool && chosen == TvPixelTool.RECT) {
            rectFilled = !rectFilled;
        } else if (chosen == tool && chosen == TvPixelTool.ELLIPSE) {
            ellipseFilled = !ellipseFilled;
        }
        if (chosen != TvPixelTool.SELECT) {
            selection.commit(canvas);
        }
        tool = chosen;
    }

    public boolean rectFilled() {
        return rectFilled;
    }

    public boolean ellipseFilled() {
        return ellipseFilled;
    }

    public int brush() {
        return brush;
    }

    public boolean round() {
        return round;
    }

    public void brushBy(int delta) {
        brush = Math.max(TvPixelBrush.MIN, Math.min(TvPixelBrush.MAX, brush + delta));
    }

    public void toggleRound() {
        round = !round;
    }

    public boolean mirrorH() {
        return mirrorH;
    }

    public boolean mirrorV() {
        return mirrorV;
    }

    public void toggleMirrorH() {
        mirrorH = !mirrorH;
    }

    public void toggleMirrorV() {
        mirrorV = !mirrorV;
    }

    /** O pixel perfect vale só no lápis de 1 px; ligado com outro pincel, espera o pincel voltar a 1. */
    public boolean perfect() {
        return perfect;
    }

    public void togglePerfect() {
        perfect = !perfect;
    }

    public boolean grid() {
        return grid;
    }

    public void toggleGrid() {
        grid = !grid;
    }

    public int current() {
        return current;
    }

    /** Escolhe a cor; a de antes vira a anterior (o X volta para ela). */
    public void choose(int index) {
        if (index != current) {
            previous = current;
            current = index;
        }
    }

    public void swapColors() {
        int old = current;
        current = previous;
        previous = old;
    }

    /** Começa a editar uma cor da paleta (o seletor abriu): a primeira mudança guarda um passo. */
    public void beginColorEdit() {
        colorEditing = false;
    }

    /** Troca a cor {@code index} da paleta; dentro de uma mesma edição do seletor, um passo só. */
    public void setColor(int index, int argb) {
        if (canvas.color(index) == argb) {
            return;
        }
        if (!colorEditing) {
            beginStep();
            colorEditing = true;
        }
        canvas.setColor(index, argb);
    }

    /** "Criar rampa": a linha da cor atual vira a rampa dela, num passo do desfazer. */
    public void makeRamp() {
        if (current == 0) {
            return;
        }
        int[] palette = canvas.palette().clone();
        TvPixelRamp.make(palette, current);
        beginStep();
        for (int i = 0; i < palette.length; i++) {
            canvas.setColor(i, palette[i]);
        }
        history.dropIfUnchanged(canvas);
    }

    // ------------------------------------------------------------------ a arte inteira e o desfazer

    /**
     * O pedaço solto assenta antes (ele já saiu da arte ao ser levantado): sem isso, o estado que vai para o refazer
     * seria a arte com o buraco e sem o pedaço.
     */
    public boolean undo() {
        selection.commit(canvas);
        selection.clear();
        return history.undo(canvas);
    }

    public boolean redo() {
        selection.commit(canvas);
        selection.clear();
        return history.redo(canvas);
    }

    /**
     * Virar ou girar: só o pedaço no quadro escolhido, se há seleção (levantado antes); senão a arte inteira, todos os
     * quadros, num passo.
     */
    public void transform(TvPixelTransform kind) {
        if (selection.active()) {
            liftIfMarked();
            selection.transform(kind);
            return;
        }
        beginStep();
        canvas.transformAll(kind);
    }

    /** Limpar tudo: o quadro escolhido inteiro transparente, num passo. */
    public void clearAll() {
        selection.clear();
        beginStep();
        canvas.clear();
        history.dropIfUnchanged(canvas);
    }

    /**
     * Muda o tamanho de todos os quadros mantendo o canto de cima à esquerda, num passo; a seleção sai. Nada muda se
     * os quadros nesse tamanho passam do teto ({@link TvPixelFrames#maxPixels}).
     */
    public void resize(int width, int height) {
        if (width == canvas.width() && height == canvas.height()
                || (long) width * height * canvas.frames() > frames.maxPixels()) {
            return;
        }
        beginStep();
        selection.clear();
        canvas.resize(width, height);
        history.dropIfUnchanged(canvas);
    }

    // ------------------------------------------------------------------ seleção e área de cópia

    public void selectAll() {
        selection.commit(canvas);
        selection.select(canvas, 0, 0, canvas.width(), canvas.height());
        tool = TvPixelTool.SELECT;
    }

    public void deselect() {
        selection.commit(canvas);
        selection.clear();
    }

    /** Enter: assenta o pedaço solto. */
    public void settle() {
        selection.commit(canvas);
    }

    public void copy() {
        TvPixelSelection.Clip clip = selection.grab(canvas);
        if (clip != null) {
            clipboard = clip;
        }
    }

    public void cut() {
        copy();
        delete();
        selection.clear();
    }

    /** Delete: o pedaço solto some (o levantar já foi um passo); no retângulo marcado, apaga os pixels num passo. */
    public void delete() {
        if (selection.floating()) {
            selection.clear();
        } else if (selection.active()) {
            history.push(canvas.snapshot());
            selection.erase(canvas);
            history.dropIfUnchanged(canvas);
        }
    }

    public boolean hasClipboard() {
        return clipboard != null;
    }

    /**
     * Cola a área de cópia como pedaço solto, no canto da seleção ou, sem ela, no pixel ({@code viewX}, {@code
     * viewY}) (o canto de cima à esquerda da vista, nunca antes do canto da arte), e escolhe a seleção.
     */
    public void paste(int viewX, int viewY) {
        if (clipboard == null) {
            return;
        }
        int x = selection.active() ? selection.x() : Math.max(0, viewX);
        int y = selection.active() ? selection.y() : Math.max(0, viewY);
        beginStep();
        selection.paste(clipboard, x, y);
        tool = TvPixelTool.SELECT;
    }

    /** As setas com seleção: o pedaço anda (levantado antes, num passo). */
    public void nudge(int dx, int dy) {
        if (!selection.active()) {
            return;
        }
        liftIfMarked();
        selection.moveTo(selection.x() + dx, selection.y() + dy);
    }

    /**
     * Esc: com um pedaço solto, desfaz o mover (a arte volta ao que era antes de levantar) e tira a seleção; com um
     * retângulo marcado, só o tira. Devolve se havia seleção (sem ela, o Esc é da tela).
     */
    public boolean escape() {
        if (selection.floating()) {
            selection.clear();
            history.cancel(canvas);
            return true;
        }
        if (selection.active()) {
            selection.clear();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ salvar

    /** Assenta o pedaço solto antes de salvar. */
    public void prepareSave() {
        selection.commit(canvas);
    }

    /** A arte mudou desde que abriu ou desde o último {@link #markClean}. */
    public boolean dirty() {
        return canvas.version() != cleanVersion || selection.floating();
    }

    public void markClean() {
        cleanVersion = canvas.version();
    }
}
