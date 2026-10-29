package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvArtThumbs;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A grade das pixel arts para escolher a arte da Imagem (seção Peça) e a do fundo em mosaico (base), no molde da
 * {@link TvItemGrid}: as miniaturas das artes da biblioteca do jogador ({@link TvLibraryClient}) e, depois do rótulo
 * "Da Tela", as que só existem no layout (copiadas de outro jogador ou vindas de código: as do rascunho e as do layout
 * salvo, para dar para voltar a uma delas antes de salvar). Seis por linha, três linhas à vista, a roda rola; a arte
 * em uso fica marcada e o clique escolhe; a dica tem o nome e o tamanho. As miniaturas são as texturas do
 * {@link TvArtThumbs}; a lista, as posições e as dicas são refeitas só quando a biblioteca ou as artes do layout
 * mudam (comparação de referência no desenho), nunca por quadro.
 */
final class TvArtGrid extends TvControl {
    static final int CELL = 18;
    static final int COLUMNS = 6;
    static final int ROWS = 3;
    private static final int THUMB = 16;
    private static final int HEAD_H = 11;
    private static final int VIEW_H = ROWS * CELL + 2;
    static final int HEIGHT = VIEW_H;

    private final Font font;
    private final TvEditorDoc doc;
    private final Supplier<UUID> selected;
    private final Consumer<TvPixelArt> choose;
    /** O recuo das células para a grade de {@link #COLUMNS} ficar no meio da largura. */
    private final int inset;
    private TvPixelArt[] arts = new TvPixelArt[0];
    private boolean[] onScreen = new boolean[0];
    /** O canto de cada célula no conteúdo que rola, em px (o {@code x} já com o recuo). */
    private int[] cellX = new int[0];
    private int[] cellY = new int[0];
    private Component[] tips = new Component[0];
    /** O topo do rótulo "Da Tela" no conteúdo, ou -1 sem artes só da Tela. */
    private int headY = -1;
    private int contentH;
    private int builtLibrary = -1;
    private @Nullable List<TvPixelArt> builtDraft;
    private @Nullable List<TvPixelArt> builtSaved;
    private final GuiText.Fitted head;
    private final Component empty = TvPropsCtx.key("image.arts.empty");
    private @Nullable UUID current;
    private int scroll;

    TvArtGrid(Font font, String id, int x, int y, TvEditorDoc doc, Supplier<UUID> selected,
            Consumer<TvPixelArt> choose) {
        super(id, x, y, TvForm.W, HEIGHT);
        this.font = font;
        this.doc = doc;
        this.selected = selected;
        this.choose = choose;
        this.inset = (w - 2 - COLUMNS * CELL) / 2;
        this.head = GuiText.fit(font, TvPropsCtx.key("image.arts.screen"), w - 2 * inset - 4);
        refresh();
    }

    @Override
    void refresh() {
        current = selected.get();
        rebuild();
    }

    /** Refaz a lista se a biblioteca ou as artes do rascunho ou do layout salvo mudaram; senão só compara. */
    private void rebuild() {
        List<TvPixelArt> draft = doc.draft().images();
        List<TvPixelArt> saved = doc.model().saved().images();
        if (builtLibrary == TvLibraryClient.version() && draft == builtDraft && saved == builtSaved) {
            return;
        }
        builtLibrary = TvLibraryClient.version();
        builtDraft = draft;
        builtSaved = saved;
        List<TvPixelArt> library = TvLibraryClient.arts();
        Set<UUID> seen = new HashSet<>();
        List<TvPixelArt> list = new ArrayList<>(library);
        for (TvPixelArt art : library) {
            seen.add(art.id());
        }
        addScreenOnly(list, seen, draft);
        addScreenOnly(list, seen, saved);
        arts = list.toArray(new TvPixelArt[0]);
        onScreen = new boolean[arts.length];
        cellX = new int[arts.length];
        cellY = new int[arts.length];
        tips = new Component[arts.length];
        int libraryRows = (library.size() + COLUMNS - 1) / COLUMNS;
        headY = arts.length > library.size() ? libraryRows * CELL : -1;
        for (int i = 0; i < arts.length; i++) {
            boolean screen = i >= library.size();
            int k = screen ? i - library.size() : i;
            onScreen[i] = screen;
            cellX[i] = inset + k % COLUMNS * CELL;
            cellY[i] = (screen ? headY + HEAD_H : 0) + k / COLUMNS * CELL;
            TvPixelArt art = arts[i];
            tips[i] = TvPropsCtx.key(screen ? "image.arts.tip.screen" : "image.art.line", art.name(), art.width(),
                    art.height());
        }
        contentH = arts.length == 0 ? 0 : cellY[arts.length - 1] + CELL;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private static void addScreenOnly(List<TvPixelArt> out, Set<UUID> seen, List<TvPixelArt> images) {
        for (TvPixelArt art : images) {
            if (seen.add(art.id())) {
                out.add(art);
            }
        }
    }

    private int maxScroll() {
        return Math.max(0, contentH - (VIEW_H - 2));
    }

    private boolean inView(double mouseX, double mouseY) {
        return mouseX >= ax + 1 && mouseX < ax + w - 1 && mouseY >= ay + 1 && mouseY < ay + VIEW_H - 1;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        rebuild();
        GuiPaint.inset(g, ax, ay, w, VIEW_H);
        if (arts.length == 0) {
            GuiText.wrapCentered(g, font, empty, ax + w / 2, ay + 8, w - 8, 4, GuiPaint.MUTED);
            return;
        }
        int top = ay + 1 - scroll;
        g.enableScissor(ax + 1, ay + 1, ax + w - 1, ay + VIEW_H - 1);
        if (headY >= 0) {
            int y = top + headY;
            GuiText.draw(g, font, head, ax + 1 + inset + 2, y + 2, GuiPaint.MUTED);
            int lineX = ax + 1 + inset + 6 + head.width();
            if (ax + w - 1 - inset - lineX >= 8) {
                g.fill(lineX, y + 5, ax + w - 1 - inset, y + 6, GuiPaint.BUTTON_BORDER);
            }
        }
        boolean hoverable = inView(mouseX, mouseY);
        TvArtThumbs.begin(g);
        for (int i = 0; i < arts.length; i++) {
            int cy = top + cellY[i];
            if (cy + CELL <= ay + 1 || cy >= ay + VIEW_H - 1) {
                continue;
            }
            int cx = ax + 1 + cellX[i];
            if (arts[i].id().equals(current)) {
                g.fill(cx, cy, cx + CELL, cy + CELL, GuiPaint.ROW_SELECTED);
                GuiPaint.outline(g, cx, cy, CELL, CELL, GuiPaint.ACCENT);
            } else if (hoverable && mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                g.fill(cx, cy, cx + CELL, cy + CELL, GuiPaint.ROW_HOVER);
            }
            TvArtThumbs.drawFitted(g, arts[i], cx + 1, cy + 1, THUMB);
        }
        TvArtThumbs.end(g);
        g.disableScissor();
        if (maxScroll() > 0) {
            int track = VIEW_H - 2;
            int thumb = Math.max(6, track * track / contentH);
            int thumbY = ay + 1 + (track - thumb) * scroll / maxScroll();
            g.fill(ax + w - 2, thumbY, ax + w - 1, thumbY + thumb, GuiPaint.BUTTON_BORDER);
        }
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!inView(mouseX, mouseY)) {
            return -1;
        }
        int contentY = (int) mouseY - ay - 1 + scroll;
        for (int i = 0; i < arts.length; i++) {
            int cx = ax + 1 + cellX[i];
            if (contentY >= cellY[i] && contentY < cellY[i] + CELL && mouseX >= cx && mouseX < cx + CELL) {
                return i;
            }
        }
        return -1;
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int index = indexAt(mouseX, mouseY);
        if (index >= 0 && !arts[index].id().equals(current)) {
            choose.accept(arts[index]);
        }
        return true;
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY) || maxScroll() == 0) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * CELL, 0, maxScroll());
        return true;
    }

    @Override
    @Nullable Component tooltip(double mouseX, double mouseY) {
        int index = indexAt(mouseX, mouseY);
        return index < 0 ? null : tips[index];
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** O centro da célula da arte {@code id}, rolada até aparecer, ou {@code null} se a grade não a tem. */
    int @Nullable [] centerOf(UUID id) {
        rebuild();
        for (int i = 0; i < arts.length; i++) {
            if (arts[i].id().equals(id)) {
                int view = VIEW_H - 2;
                if (cellY[i] < scroll || cellY[i] + CELL > scroll + view) {
                    scroll = Mth.clamp(cellY[i] - (onScreen[i] ? HEAD_H : 0), 0, maxScroll());
                }
                return new int[] {ax + 1 + cellX[i] + CELL / 2, ay + 1 + cellY[i] - scroll + CELL / 2};
            }
        }
        return null;
    }

    /** Se a arte {@code id} está na grade entre as "Da Tela" (só no layout, fora da biblioteca). */
    boolean onScreenOnly(UUID id) {
        rebuild();
        for (int i = 0; i < arts.length; i++) {
            if (arts[i].id().equals(id)) {
                return onScreen[i];
            }
        }
        return false;
    }
}
