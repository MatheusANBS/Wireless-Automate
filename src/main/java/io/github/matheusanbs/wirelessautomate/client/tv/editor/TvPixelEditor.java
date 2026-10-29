package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelCanvas;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * O editor de pixel art (decisões 1 a 1h da spec da etapa 4): um modo da aba Editar que ocupa a janela inteira do
 * editor da Tela, por cima dele, e volta para ele ao salvar ou cancelar. Assim o rascunho da Tela, o desfazer dela e o
 * menu aberto no servidor (que as ações da biblioteca exigem) continuam como estavam. Esta classe é a anfitriã: guarda
 * a {@link TvPixelSession} da arte aberta e repassa o desenho, o mouse e o teclado às partes ({@link TvPixelHeader},
 * {@link TvPixelToolbar}, {@link TvPixelBoard}, {@link TvPixelFooter}, {@link TvPixelSide} e os atalhos do
 * {@link TvPixelKeys}), com a janela solta do seletor de cor por cima de tudo. Salvar manda a arte pelo
 * {@link TvEditorActions} e fecha só quando a biblioteca volta com ela: a recusa do servidor (biblioteca cheia, grande
 * demais, o limite de ações) deixa o desenho aberto, com o aviso. O Esc tira primeiro a seleção e, sem ela, é o
 * Cancelar (dois cliques com mudança).
 */
public final class TvPixelEditor {
    private final TvEditorActions actions;
    private final TvArtsPanel arts;
    private final TvPixelTexture texture = new TvPixelTexture();
    private final TvPopups popups = new TvPopups();
    private final String[] name = {""};
    private final TvTooSmall tooSmall = new TvTooSmall();
    private @Nullable TvPixelSession session;
    private UUID artId = new UUID(0, 0);
    private TvPixelHeader header;
    private TvPixelToolbar toolbar;
    private TvPixelBoard board;
    private TvPixelFooter footer;
    private TvPixelSide side;
    private TvPixelKeys keys;
    private @Nullable Font font;
    private TvRect window = TvRect.NONE;
    private boolean fits;
    private boolean spaceDown;
    private boolean painting;
    /** A arte mandada a salvar, até a biblioteca voltar com ela ou o prazo passar. */
    private final TvPendingSave<TvPixelArt> saving = new TvPendingSave<>();
    private int savingLibrary;

    TvPixelEditor(TvEditorActions actions, TvArtDrag drag) {
        this.actions = actions;
        this.arts = new TvArtsPanel(actions, this, drag);
    }

    /** A aba Pixel art da coluna da esquerda do editor da Tela, que abre este editor. */
    TvArtsPanel arts() {
        return arts;
    }

    public boolean isOpen() {
        return session != null;
    }

    /** Abre a arte para editar, ou uma nova de 16 × 16 com a paleta padrão ({@code art} nulo). */
    void open(@Nullable TvPixelArt art) {
        TvPixelCanvas canvas = art == null ? TvPixelCanvas.blank(TvPixelCanvas.NEW_SIDE, TvPixelCanvas.NEW_SIDE)
                : new TvPixelCanvas(art.width(), art.height(), art.palette(), art.pixels());
        TvPixelSession opened = new TvPixelSession(canvas);
        session = opened;
        artId = art == null ? UUID.randomUUID() : art.id();
        name[0] = art == null ? TvEditorText.key("pixel.new_name").getString() : art.name();
        header = new TvPixelHeader(opened, name);
        toolbar = new TvPixelToolbar(opened);
        board = new TvPixelBoard(opened, texture);
        footer = new TvPixelFooter(opened);
        side = new TvPixelSide(opened, texture);
        keys = new TvPixelKeys(opened, board, toolbar, this::save);
        spaceDown = false;
        painting = false;
        layoutParts();
    }

    /** A janela inteira do editor ({@code x}, {@code y}, {@code w} × {@code h}), já com o cabeçalho. */
    void layout(Font font, int x, int y, int w, int h) {
        this.font = font;
        this.window = new TvRect(x, y, w, h);
        this.fits = TvEditorLayout.fits(w, h);
        tooSmall.layout(font, window);
        layoutParts();
    }

    private void layoutParts() {
        popups.close();
        if (session == null || font == null || !fits) {
            return;
        }
        TvPixelLayout layout = TvPixelLayout.of(window.x(), window.y(), window.w(), window.h());
        popups.bounds(window);
        header.layout(font, layout.header());
        toolbar.layout(layout.tools());
        board.layout(layout.board());
        footer.layout(font, layout.footer(), layout.status());
        side.layout(font, layout.side(), layout.clear());
    }

    /** Fecha sem salvar e solta as texturas do quadro. */
    void close() {
        popups.close();
        saving.release();
        session = null;
        texture.release();
    }

    /**
     * Uma vez por tick, só com um salvar esperando: a biblioteca voltou com a arte (fecha, se nada mudou desde o
     * Salvar) ou o prazo passou sem ela (fica aberto e avisa; a recusa do servidor já disse o motivo na barra de ação).
     */
    void tick() {
        TvPixelArt sent = saving.sent();
        if (sent == null) {
            return;
        }
        if (TvLibraryClient.version() != savingLibrary) {
            savingLibrary = TvLibraryClient.version();
            if (TvLibraryClient.arts().contains(sent)) {
                saving.release();
                if (session != null && !session.dirty()) {
                    close();
                }
                return;
            }
        }
        if (saving.tick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(TvEditorText.key("pixel.save_failed"), true);
            }
        }
    }

    /** O Salvar espera a resposta da biblioteca. */
    public boolean saving() {
        return saving.pending();
    }

    private void save() {
        TvPixelSession current = session;
        if (current == null) {
            return;
        }
        header.blur();
        current.prepareSave();
        TvPixelCanvas canvas = current.canvas();
        String chosen = name[0].strip();
        if (chosen.isEmpty()) {
            chosen = TvEditorText.key("pixel.new_name").getString();
        }
        TvPixelArt art = new TvPixelArt(artId, chosen, canvas.width(), canvas.height(), canvas.palette(),
                canvas.pixels());
        actions.saveArt(art);
        if (actions.preview()) {
            close();
            return;
        }
        current.markClean();
        savingLibrary = TvLibraryClient.version();
        saving.begin(art);
    }

    private void cancel() {
        if (side.cancel()) {
            close();
        }
    }

    // ------------------------------------------------------------------ desenho

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (session == null || font == null) {
            return;
        }
        if (!fits) {
            tooSmall.render(g);
            return;
        }
        header.render(g, mouseX, mouseY);
        toolbar.render(g, mouseX, mouseY);
        board.render(g, mouseX, mouseY);
        boolean over = board.box().contains(mouseX, mouseY);
        footer.render(g, mouseX, mouseY, over ? board.cellX(mouseX) : -1, over ? board.cellY(mouseY) : -1);
        side.render(g, mouseX, mouseY);
        TvPopup popup = popups.current();
        if (popup != null) {
            popup.render(g, mouseX, mouseY);
        }
    }

    public @Nullable Component tooltip(double mouseX, double mouseY) {
        if (session == null || !fits) {
            return null;
        }
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.tooltip(mouseX, mouseY);
        }
        Component tip = toolbar.tooltip(mouseX, mouseY);
        if (tip == null) {
            tip = footer.tooltip(mouseX, mouseY);
        }
        return tip != null ? tip : side.tooltip(mouseX, mouseY);
    }

    // ------------------------------------------------------------------ mouse

    private static boolean shift() {
        return Screen.hasShiftDown() || TvEditorTab.e2eShift;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (session == null || !fits) {
            return session != null;
        }
        TvPopup popup = popups.current();
        if (popup != null) {
            return popupClicked(popup, mouseX, mouseY, button);
        }
        if (button == 0 && header.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        header.blur();
        if (board.box().contains(mouseX, mouseY)) {
            if (button == 2 || Screen.hasAltDown() || spaceDown) {
                board.beginPan(mouseX, mouseY);
            } else if (button == 0 || button == 1) {
                session.press(board.cellX(mouseX), board.cellY(mouseY), button == 1, shift(),
                        Screen.hasControlDown() || TvEditorTab.e2eCtrl);
                painting = true;
            }
            return true;
        }
        if (button == 0 && !toolbar.mouseClicked(mouseX, mouseY)) {
            TvPixelFooter.Button pressed = footer.clicked(mouseX, mouseY);
            if (pressed != null) {
                run(pressed);
            } else {
                sideClicked(mouseX, mouseY);
            }
        }
        return true;
    }

    /** Com o seletor aberto: o clique dentro é dele; o conta-gotas pega a cor de um pixel do quadro; fora, fecha. */
    private boolean popupClicked(TvPopup popup, double mouseX, double mouseY, int button) {
        if (popup.contains(mouseX, mouseY)) {
            popup.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        int cx = board.cellX(mouseX);
        int cy = board.cellY(mouseY);
        TvPixelCanvas canvas = session.canvas();
        if (popup instanceof TvColorPopup color && color.picking() && board.box().contains(mouseX, mouseY)
                && canvas.inside(cx, cy) && canvas.get(cx, cy) != 0) {
            color.picked(canvas.color(canvas.get(cx, cy)));
            return true;
        }
        popups.close();
        return true;
    }

    private void run(TvPixelFooter.Button button) {
        switch (button) {
            case UNDO -> session.undo();
            case REDO -> session.redo();
            case SMALLER -> session.brushBy(-1);
            case LARGER -> session.brushBy(1);
            case SHAPE -> session.toggleRound();
            case GRID -> session.toggleGrid();
            case FIT -> board.fit();
        }
    }

    private void sideClicked(double mouseX, double mouseY) {
        TvPixelSide.Button pressed = side.mouseClicked(mouseX, mouseY, board);
        if (pressed == null) {
            return;
        }
        switch (pressed) {
            case EDIT_COLOR -> openColor();
            case RAMP -> session.makeRamp();
            case CLEAR -> {
                if (side.clear()) {
                    session.clearAll();
                }
            }
            case CANCEL -> cancel();
            case SAVE -> save();
        }
    }

    private void openColor() {
        int index = session.current();
        if (index == 0) {
            return;
        }
        TvPixelSession editing = session;
        editing.beginColorEdit();
        popups.open(new TvColorPopup(font, side.paletteBox(), popups.bounds(), editing.canvas().color(index), false,
                true, null, argb -> editing.setColor(index, argb)));
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (session == null || !fits) {
            return false;
        }
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.mouseDragged(mouseX, mouseY);
        }
        if (header.mouseDragged(mouseX, mouseY) || board.dragPan(mouseX, mouseY)) {
            return true;
        }
        if (painting) {
            session.drag(board.cellX(mouseX), board.cellY(mouseY), shift());
        }
        return painting;
    }

    public boolean mouseReleased(double mouseX, double mouseY) {
        if (session == null) {
            return false;
        }
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.mouseReleased(mouseX, mouseY);
        }
        boolean used = header.mouseReleased(mouseX, mouseY) | board.endPan() | painting;
        if (painting) {
            painting = false;
            session.release();
        }
        return used;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (session == null || !fits) {
            return false;
        }
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.mouseScrolled(mouseX, mouseY, delta);
        }
        return header.mouseScrolled(mouseX, mouseY, delta) || board.scrolled(mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------------ teclado

    /** Uma tecla com o editor aberto: o seletor, depois o campo com o foco, o Esc, o Espaço e os atalhos. */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (session == null) {
            return false;
        }
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!session.escape()) {
                cancel();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            spaceDown = true;
            return true;
        }
        return fits && keys.keyPressed(keyCode, modifiers);
    }

    public boolean keyReleased(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_SPACE && spaceDown) {
            spaceDown = false;
            return true;
        }
        return false;
    }

    public boolean charTyped(char c, int modifiers) {
        if (session == null) {
            return false;
        }
        TvPopup popup = popups.current();
        if (popup != null) {
            return popup.charTyped(c, modifiers);
        }
        if (header.textFocused()) {
            return header.charTyped(c, modifiers);
        }
        return fits && c != ' ' && keys.charTyped(c);
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** A arte aberta (ou {@code null}), para o e2e conferir os pixels, a vista e a seleção. */
    public @Nullable TvPixelSession session() {
        return session;
    }

    public String name() {
        return name[0];
    }

    /** O centro do botão de nome {@code button} da coluna de ferramentas, do rodapé ou da coluna da direita. */
    public int[] buttonCenter(String button) {
        String upper = button.toUpperCase(Locale.ROOT);
        for (TvPixelToolbar.Button each : TvPixelToolbar.Button.ALL) {
            if (each.name().equals(upper)) {
                return toolbar.center(each);
            }
        }
        for (TvPixelFooter.Button each : TvPixelFooter.Button.values()) {
            if (each.name().equals(upper)) {
                return footer.center(each);
            }
        }
        return side.center(TvPixelSide.Button.valueOf(upper));
    }

    public int[] cellCenter(int x, int y) {
        return board.cellCenter(x, y);
    }

    public int[] swatchCenter(int index) {
        return side.swatchCenter(index);
    }

    public int[] previewCenter(int which) {
        return side.previewCenter(which);
    }

    public int[] boardCenter() {
        return board.box().center();
    }

    /** Se o ponto da janela cai no quadro. */
    public boolean onBoard(int[] point) {
        return board.box().contains(point[0], point[1]);
    }

    /** Dá o foco ao campo do nome (o e2e digita depois). */
    public void focusName() {
        header.focusName();
    }

    /** Dá o foco ao campo da largura ({@code width}) ou da altura (o e2e digita depois). */
    public void focusSize(boolean width) {
        header.focusSize(width);
    }

    /** O centro de um botão da aba Pixel art ({@code NEW}, {@code EDIT}...); a aba precisa estar aberta. */
    public int[] artsButtonCenter(String button) {
        return arts.buttonCenter(TvArtsPanel.Button.valueOf(button));
    }

    /** O centro da linha da arte de nome {@code name} na aba Pixel art, ou {@code null}. */
    public int @Nullable [] artRowCenter(String name) {
        return arts.rowCenter(name);
    }

    /** O centro da linha da arte de id {@code id} na aba Pixel art, ou {@code null}. */
    public int @Nullable [] artRowCenter(UUID id) {
        return arts.rowCenter(id);
    }

    public boolean colorOpen() {
        return popups.current() instanceof TvColorPopup;
    }
}
