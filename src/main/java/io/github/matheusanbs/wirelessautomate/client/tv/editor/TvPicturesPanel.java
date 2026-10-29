package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageUploader;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvArtThumbs;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvPictureThumbs;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A parte Imagens da aba de artes da coluna da esquerda: as imagens enviadas da biblioteca do jogador
 * ({@link TvLibraryClient#pictures}) numa lista que rola, com a miniatura (a textura da própria imagem, que no GIF
 * toca), o nome e o tamanho ("GIF · n quadros" na animada), e o uso {@code n / 64} em cima. Um clique escolhe, dois
 * renomeiam e arrastar uma linha até a miniatura cria a peça Imagem ({@link TvArtDrag}); embaixo, Enviar arquivo (a
 * caixa do sistema, {@link TvPictureFiles}), Renomear e Remover (dois cliques: sai da biblioteca, e a imagem só some
 * do mundo quando nada mais a usa), Copiar código e Importar (o código {@code WA-IMAGEM1}, {@link TvPictureSend}). Com
 * as imagens desligadas no servidor, a lista diz isso e os botões de enviar ficam apagados. As linhas e os textos são
 * refeitos quando a biblioteca ou a janela mudam, nunca por quadro; só as linhas à vista são desenhadas.
 */
final class TvPicturesPanel {
    /** Um botão embaixo da lista. */
    enum Button { SEND, RENAME, REMOVE, COPY, IMPORT }

    private static final int ROW_H = 18;
    private static final int THUMB = 16;
    private static final int BUTTON_H = 14;
    private static final int BUTTON_ROWS = 3;
    private static final int HEAD_H = 12;
    private static final long DOUBLE_CLICK_MS = 400;

    /** Uma linha: a imagem da biblioteca, o nome medido e o tamanho. */
    private record Row(TvLibraryPicture entry, GuiText.Fitted name, Component size) {
    }

    private final TvEditorActions actions;
    private final TvArtDrag drag;
    private final ArmedClick removeClick = new ArmedClick();
    private TvEditorPaint.Label removeConfirm;
    private final TvRect[] buttons = new TvRect[Button.values().length];
    private final TvEditorPaint.Label[] labels = new TvEditorPaint.Label[Button.values().length];
    private final Component[] tips = new Component[Button.values().length];
    private Font font;
    private TvRect box = TvRect.NONE;
    private TvRect list = TvRect.NONE;
    private GuiText.Fitted title;
    private Component empty = Component.empty();
    private List<Row> rows = List.of();
    private int builtLibrary = -1;
    private Component count = Component.empty();
    private int countWidth;
    private int scroll;
    private @Nullable String chosen;
    private long lastClickAt;
    private @Nullable TvTextField edit;
    private String pending = "";
    private @Nullable String editing;

    TvPicturesPanel(TvEditorActions actions, TvArtDrag drag) {
        this.actions = actions;
        this.drag = drag;
    }

    void layout(Font font, TvRect box) {
        this.font = font;
        this.box = box;
        cancelRename();
        int half = (box.w() - 2) / 2;
        int buttonsTop = box.bottom() - BUTTON_ROWS * (BUTTON_H + 2) + 2;
        for (Button button : Button.values()) {
            int i = button.ordinal();
            int row = button == Button.SEND ? 0 : (i + 1) / 2;
            boolean full = button == Button.SEND;
            boolean left = full || i % 2 == 1;
            int w = full ? box.w() : half;
            buttons[i] = new TvRect(left ? box.x() : box.right() - half, buttonsTop + row * (BUTTON_H + 2), w,
                    BUTTON_H);
            String key = "pictures." + button.name().toLowerCase(Locale.ROOT);
            labels[i] = TvEditorPaint.label(font, TvEditorText.key(key), w - 4);
        }
        var rules = actions.images();
        tips[Button.SEND.ordinal()] = TvEditorText.key("pictures.send.tip", rules.maxFileBytes() / (1024 * 1024),
                rules.maxSize(), rules.maxFrames());
        tips[Button.IMPORT.ordinal()] = TvEditorText.key("pictures.import.tip");
        tips[Button.COPY.ordinal()] = TvEditorText.key("pictures.copy.tip");
        tips[Button.RENAME.ordinal()] = TvEditorText.key("pictures.rename.tip");
        removeConfirm = TvEditorPaint.label(font, TvEditorText.key("arts.remove.confirm"), half - 4);
        list = new TvRect(box.x(), box.y() + HEAD_H, box.w(), Math.max(0, buttonsTop - 3 - box.y() - HEAD_H));
        empty = TvEditorText.key(!rules.enabled() ? "pictures.off" : rules.canUpload() ? "pictures.empty"
                : "pictures.empty.no_upload");
        builtLibrary = -1;
    }

    private void rebuild() {
        if (builtLibrary == TvLibraryClient.version()) {
            return;
        }
        builtLibrary = TvLibraryClient.version();
        List<TvLibraryPicture> pictures = TvLibraryClient.pictures();
        List<Row> out = new ArrayList<>(pictures.size());
        for (TvLibraryPicture entry : pictures) {
            out.add(new Row(entry, GuiText.fit(font, Component.literal(entry.name()), list.w() - THUMB - 14),
                    TvEditorText.pictureSize(entry.picture())));
        }
        rows = out;
        count = TvEditorText.key("arts.count", pictures.size(), TvLibrary.MAX_PICTURES);
        countWidth = font.width(count);
        title = GuiText.fit(font, TvEditorText.key("pictures.title"), box.w() - countWidth - 6);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        if (chosen != null && out.stream().noneMatch(r -> r.entry.picture().hash().equals(chosen))) {
            chosen = null;
        }
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * ROW_H - (list.h() - 4));
    }

    /** A imagem escolhida na lista, ou {@code null}: a Imagem que a paleta de Peças cria nasce com ela. */
    @Nullable TvLibraryPicture chosen() {
        if (box != TvRect.NONE) {
            rebuild();
        }
        for (Row row : rows) {
            if (row.entry.picture().hash().equals(chosen)) {
                return row.entry;
            }
        }
        return null;
    }

    private boolean enabled(Button button) {
        var rules = actions.images();
        return switch (button) {
            case SEND -> rules.canUpload() && !TvPictureFiles.opening() && !TvImageUploader.busy();
            case IMPORT -> rules.canUpload() && !TvImageUploader.busy();
            case RENAME, REMOVE, COPY -> chosen() != null;
        };
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        rebuild();
        if (edit != null) {
            edit.render(g, mouseX, mouseY);
        } else {
            GuiText.draw(g, font, title, box.x() + 1, box.y() + 1, GuiPaint.MUTED);
            GuiPaint.text(g, font, count, box.right() - countWidth - 1, box.y() + 1, GuiPaint.MUTED);
        }
        GuiPaint.inset(g, list.x(), list.y(), list.w(), list.h());
        int top = list.y() + 2;
        int bottom = list.bottom() - 2;
        g.enableScissor(list.x() + 1, top, list.right() - 1, bottom);
        int w = list.w() - 2 - (maxScroll() > 0 ? 3 : 0);
        TvArtThumbs.begin(g);
        for (int i = 0; i < rows.size(); i++) {
            int y = top + i * ROW_H - scroll;
            if (y + ROW_H <= top || y >= bottom) {
                continue;
            }
            Row row = rows.get(i);
            int x = list.x() + 1;
            if (row.entry.picture().hash().equals(chosen)) {
                g.fill(x, y, x + w, y + ROW_H, GuiPaint.alpha(GuiPaint.ACCENT, 0x40));
                g.fill(x, y, x + 2, y + ROW_H, GuiPaint.ACCENT);
            } else if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H && mouseY >= top
                    && mouseY < bottom) {
                g.fill(x, y, x + w, y + ROW_H, GuiPaint.alpha(GuiPaint.ACCENT, 0x20));
            }
            thumb(g, row.entry.picture(), x + 4, y + 1);
            GuiText.draw(g, font, row.name, x + THUMB + 8, y + 1, GuiPaint.FG);
            GuiPaint.text(g, font, row.size, x + THUMB + 8, y + 10, GuiPaint.MUTED);
        }
        TvArtThumbs.end(g);
        g.disableScissor();
        if (rows.isEmpty()) {
            GuiText.wrapCentered(g, font, empty, list.centerX(), list.y() + 8, list.w() - 8, 6, GuiPaint.MUTED);
        }
        for (Button button : Button.values()) {
            TvRect r = buttons[button.ordinal()];
            if (button == Button.REMOVE && removeClick.armed(System.currentTimeMillis()) && enabled(button)) {
                TvEditorPaint.danger(g, font, r, removeConfirm);
            } else {
                TvEditorPaint.button(g, font, r, labels[button.ordinal()], r.contains(mouseX, mouseY),
                        enabled(button));
            }
        }
    }

    /** A miniatura no maior tamanho que cabe em {@link #THUMB} px, com a marca de play no GIF. */
    private static void thumb(GuiGraphics g, TvPicture picture, int x, int y) {
        GuiPaint.outline(g, x - 1, y - 1, THUMB + 2, THUMB + 2, GuiPaint.BUTTON_BORDER);
        TvPictureThumbs.drawFitted(g, picture, x, y, THUMB);
        if (picture.animated()) {
            TvArtThumbs.end(g);
            TvEditorPaint.playMark(g, x + THUMB - 4, y + THUMB - 5);
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!list.contains(mouseX, mouseY) || mouseY < list.y() + 2 || mouseY >= list.bottom() - 2) {
            return -1;
        }
        int i = (int) (mouseY - list.y() - 2 + scroll) / ROW_H;
        return i >= 0 && i < rows.size() ? i : -1;
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!box.contains(mouseX, mouseY)) {
            if (edit != null) {
                commitRename();
            }
            return false;
        }
        if (edit != null) {
            if (edit.mouseClicked(mouseX, mouseY, 0)) {
                return true;
            }
            commitRename();
        }
        rebuild();
        int i = rowAt(mouseX, mouseY);
        if (i >= 0) {
            TvLibraryPicture entry = rows.get(i).entry;
            String hash = entry.picture().hash();
            long now = System.currentTimeMillis();
            boolean twice = hash.equals(chosen) && now - lastClickAt < DOUBLE_CLICK_MS;
            if (!hash.equals(chosen)) {
                removeClick.disarm();
            }
            chosen = hash;
            lastClickAt = now;
            if (twice) {
                beginRename(entry);
            } else {
                drag.press(TvImageSource.of(entry.picture()), mouseX, mouseY);
            }
            return true;
        }
        for (Button button : Button.values()) {
            if (buttons[button.ordinal()].contains(mouseX, mouseY) && enabled(button)) {
                run(button);
            }
        }
        return true;
    }

    private void run(Button button) {
        TvLibraryPicture entry = chosen();
        if (button != Button.REMOVE) {
            removeClick.disarm();
        }
        switch (button) {
            case SEND -> TvPictureFiles.open(actions);
            case RENAME -> beginRename(entry);
            case REMOVE -> {
                if (removeClick.click(System.currentTimeMillis())) {
                    actions.removePicture(entry.picture().hash());
                }
            }
            case COPY -> TvPictureSend.copy(actions, entry);
            case IMPORT -> {
                String text = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (!TvPictureSend.pasteIfImage(actions, text)) {
                    TvCodeNotice.show(TvCodeNotice.Kind.INVALID, TvEditorText.key("code.image.not_image"));
                }
            }
        }
    }

    // ------------------------------------------------------------------ renomear

    private void beginRename(TvLibraryPicture entry) {
        editing = entry.picture().hash();
        pending = entry.name();
        edit = new TvTextField(font, "pictures.name", box.x(), box.y(), box.w(), TvLibraryPicture.MAX_NAME, null,
                TvEditorText.key("pictures.name"), () -> pending, value -> pending = value);
        edit.place(0, 0);
        edit.focus();
    }

    private void commitRename() {
        String name = pending.strip();
        if (editing != null && !name.isEmpty()) {
            for (Row row : rows) {
                if (row.entry.picture().hash().equals(editing) && !row.entry.name().equals(name)) {
                    actions.renamePicture(editing, name);
                }
            }
        }
        cancelRename();
    }

    private void cancelRename() {
        edit = null;
        editing = null;
        pending = "";
    }

    boolean keyPressed(int keyCode) {
        if (edit == null) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitRename();
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelRename();
        } else {
            edit.keyPressed(keyCode, 0, 0);
        }
        return true;
    }

    boolean charTyped(char c, int modifiers) {
        return edit != null && edit.charTyped(c, modifiers);
    }

    boolean textFocused() {
        return edit != null;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!list.contains(mouseX, mouseY) || maxScroll() == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * ROW_H));
        return true;
    }

    /**
     * As dicas: a da linha, com o nome inteiro e os quadros do GIF; e as dos botões, com os tetos do servidor no Enviar
     * arquivo.
     */
    @Nullable Component tooltip(double mouseX, double mouseY) {
        int row = edit == null ? rowAt(mouseX, mouseY) : -1;
        if (row >= 0) {
            TvLibraryPicture entry = rows.get(row).entry;
            return TvEditorText.key("image.picture.line", entry.name(),
                    TvEditorText.pictureSizeLong(entry.picture()));
        }
        for (Button button : Button.values()) {
            if (buttons[button.ordinal()].contains(mouseX, mouseY)) {
                if ((button == Button.SEND || button == Button.IMPORT) && !actions.images().canUpload()) {
                    return TvEditorText.key(actions.images().enabled() ? "pictures.no_upload" : "pictures.off");
                }
                return tips[button.ordinal()];
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ ganchos do e2e

    int[] buttonCenter(Button button) {
        return buttons[button.ordinal()].center();
    }

    /** O centro da linha da imagem de nome {@code name} (rolada até aparecer), ou {@code null}. */
    int @Nullable [] rowCenter(String name) {
        rebuild();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).entry.name().equals(name)) {
                int y = i * ROW_H;
                int view = list.h() - 4;
                if (y < scroll || y + ROW_H > scroll + view) {
                    scroll = Math.max(0, Math.min(maxScroll(), y - 2));
                }
                return new int[] {list.centerX(), list.y() + 2 + y - scroll + ROW_H / 2};
            }
        }
        return null;
    }
}
