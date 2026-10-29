package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvArtThumbs;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
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
 * A grade para escolher o que a Imagem (seção Peça) e o fundo em mosaico (base) mostram, no molde da
 * {@link TvItemGrid}, em até quatro grupos separados por um rótulo: as pixel arts da biblioteca do jogador
 * ({@link TvLibraryClient}); "Da Tela", as artes que só existem no layout (copiadas de outro jogador ou vindas de
 * código: as do rascunho e as do layout salvo, para dar para voltar a uma delas antes de salvar); "Imagens", as
 * imagens enviadas da biblioteca; e "Imagens da Tela", as que só o layout aponta. O GIF animado tem a marca de
 * play no canto. Seis por linha, três linhas à vista, a roda rola; a escolhida fica marcada e o clique escolhe; a dica
 * tem o nome e o tamanho. As miniaturas são as texturas do {@link TvArtThumbs} e do {@code TvPictureThumbs}; a lista,
 * as posições e as dicas são refeitas só quando a biblioteca ou as artes e imagens do layout mudam (comparação de
 * referência no desenho), nunca por quadro.
 */
final class TvArtGrid extends TvControl {
    static final int CELL = 18;
    static final int COLUMNS = 6;
    static final int ROWS = 3;
    private static final int THUMB = 16;
    private static final int HEAD_H = 11;
    private static final int VIEW_H = ROWS * CELL + 2;
    static final int HEIGHT = VIEW_H;
    /** Os rótulos dos grupos 1 a 3 (o das artes da biblioteca não tem). */
    private static final String[] HEADS = {"", "image.arts.screen", "image.pictures", "image.pictures.screen"};

    private final Font font;
    private final TvEditorDoc doc;
    private final Supplier<UUID> selected;
    private final Consumer<TvImageSource> choose;
    /** O recuo das células para a grade de {@link #COLUMNS} ficar no meio da largura. */
    private final int inset;
    private TvImageSource[] items = new TvImageSource[0];
    /** Se cada item é dos que só a Tela tem (fora da biblioteca). */
    private boolean[] onScreen = new boolean[0];
    /** O canto de cada célula no conteúdo que rola, em px (o {@code x} já com o recuo). */
    private int[] cellX = new int[0];
    private int[] cellY = new int[0];
    private Component[] tips = new Component[0];
    /** O topo do rótulo de cada grupo no conteúdo, ou -1 sem o rótulo. */
    private final int[] headY = {-1, -1, -1, -1};
    private final GuiText.Fitted[] heads = new GuiText.Fitted[HEADS.length];
    private int contentH;
    private int builtLibrary = -1;
    private @Nullable List<TvPixelArt> builtDraft;
    private @Nullable List<TvPixelArt> builtSaved;
    private @Nullable List<TvPicture> builtDraftPictures;
    private @Nullable List<TvPicture> builtSavedPictures;
    private final Component empty = TvPropsCtx.key("image.arts.empty");
    private @Nullable UUID current;
    private int scroll;

    TvArtGrid(Font font, String id, int x, int y, TvEditorDoc doc, Supplier<UUID> selected,
            Consumer<TvImageSource> choose) {
        super(id, x, y, TvForm.W, HEIGHT);
        this.font = font;
        this.doc = doc;
        this.selected = selected;
        this.choose = choose;
        this.inset = (w - 2 - COLUMNS * CELL) / 2;
        for (int i = 1; i < HEADS.length; i++) {
            heads[i] = GuiText.fit(font, TvPropsCtx.key(HEADS[i]), w - 2 * inset - 4);
        }
        refresh();
    }

    @Override
    void refresh() {
        current = selected.get();
        rebuild();
    }

    /** Refaz a lista se a biblioteca ou as artes e imagens do rascunho ou do layout salvo mudaram; senão só compara. */
    private void rebuild() {
        List<TvPixelArt> draft = doc.draft().images();
        List<TvPixelArt> saved = doc.model().saved().images();
        List<TvPicture> draftPictures = doc.draft().pictures();
        List<TvPicture> savedPictures = doc.model().saved().pictures();
        if (builtLibrary == TvLibraryClient.version() && draft == builtDraft && saved == builtSaved
                && draftPictures == builtDraftPictures && savedPictures == builtSavedPictures) {
            return;
        }
        builtLibrary = TvLibraryClient.version();
        builtDraft = draft;
        builtSaved = saved;
        builtDraftPictures = draftPictures;
        builtSavedPictures = savedPictures;
        List<List<TvImageSource>> groups = groups(draft, saved, draftPictures, savedPictures);
        List<TvImageSource> all = new ArrayList<>();
        List<Component> tipList = new ArrayList<>();
        List<Boolean> screenList = new ArrayList<>();
        List<int[]> cells = new ArrayList<>();
        int y = 0;
        for (int g = 0; g < groups.size(); g++) {
            List<TvImageSource> group = groups.get(g);
            headY[g] = -1;
            if (group.isEmpty()) {
                continue;
            }
            if (g > 0) {
                headY[g] = y;
                y += HEAD_H;
            }
            for (int k = 0; k < group.size(); k++) {
                TvImageSource item = group.get(k);
                all.add(item);
                screenList.add(g == 1 || g == 3);
                cells.add(new int[] {inset + k % COLUMNS * CELL, y + k / COLUMNS * CELL});
                tipList.add(tip(item, g));
            }
            y += (group.size() + COLUMNS - 1) / COLUMNS * CELL;
        }
        items = all.toArray(new TvImageSource[0]);
        onScreen = new boolean[items.length];
        cellX = new int[items.length];
        cellY = new int[items.length];
        tips = tipList.toArray(new Component[0]);
        for (int i = 0; i < items.length; i++) {
            onScreen[i] = screenList.get(i);
            cellX[i] = cells.get(i)[0];
            cellY[i] = cells.get(i)[1];
        }
        contentH = y;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /** Os quatro grupos: artes da biblioteca, artes só da Tela, imagens da biblioteca e imagens só da Tela. */
    private static List<List<TvImageSource>> groups(List<TvPixelArt> draft, List<TvPixelArt> saved,
            List<TvPicture> draftPictures, List<TvPicture> savedPictures) {
        List<TvImageSource> arts = new ArrayList<>();
        List<TvImageSource> screenArts = new ArrayList<>();
        List<TvImageSource> pictures = new ArrayList<>();
        List<TvImageSource> screenPictures = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (TvPixelArt art : TvLibraryClient.arts()) {
            seen.add(art.id());
            arts.add(TvImageSource.of(art));
        }
        for (List<TvPixelArt> list : List.of(draft, saved)) {
            for (TvPixelArt art : list) {
                if (seen.add(art.id())) {
                    screenArts.add(TvImageSource.of(art));
                }
            }
        }
        for (TvLibraryPicture entry : TvLibraryClient.pictures()) {
            seen.add(entry.picture().id());
            pictures.add(TvImageSource.of(entry.picture()));
        }
        for (List<TvPicture> list : List.of(draftPictures, savedPictures)) {
            for (TvPicture picture : list) {
                if (seen.add(picture.id())) {
                    screenPictures.add(TvImageSource.of(picture));
                }
            }
        }
        return List.of(arts, screenArts, pictures, screenPictures);
    }

    /** A dica de um item: nome e tamanho (a imagem só da Tela não tem nome), "só nesta Tela" e "animada". */
    private static Component tip(TvImageSource item, int group) {
        if (item.art() != null) {
            TvPixelArt art = item.art();
            return TvPropsCtx.key(group == 1 ? "image.arts.tip.screen" : "image.art.line", art.name(), art.width(),
                    art.height());
        }
        TvPicture picture = item.picture();
        Component size = TvEditorText.pictureSizeLong(picture);
        return group == 3 ? TvPropsCtx.key("image.pictures.tip.screen", size)
                : TvPropsCtx.key("image.picture.line", TvEditorText.pictureName(picture), size);
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
        if (items.length == 0) {
            GuiText.wrapCentered(g, font, empty, ax + w / 2, ay + 8, w - 8, 4, GuiPaint.MUTED);
            return;
        }
        int top = ay + 1 - scroll;
        g.enableScissor(ax + 1, ay + 1, ax + w - 1, ay + VIEW_H - 1);
        for (int k = 1; k < HEADS.length; k++) {
            if (headY[k] >= 0) {
                head(g, heads[k], top + headY[k]);
            }
        }
        boolean hoverable = inView(mouseX, mouseY);
        TvArtThumbs.begin(g);
        for (int i = 0; i < items.length; i++) {
            int cy = top + cellY[i];
            if (cy + CELL <= ay + 1 || cy >= ay + VIEW_H - 1) {
                continue;
            }
            int cx = ax + 1 + cellX[i];
            if (items[i].id().equals(current)) {
                g.fill(cx, cy, cx + CELL, cy + CELL, GuiPaint.ROW_SELECTED);
                GuiPaint.outline(g, cx, cy, CELL, CELL, GuiPaint.ACCENT);
            } else if (hoverable && mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                g.fill(cx, cy, cx + CELL, cy + CELL, GuiPaint.ROW_HOVER);
            }
            items[i].drawFitted(g, cx + 1, cy + 1, THUMB);
            if (items[i].picture() != null && items[i].picture().animated()) {
                TvArtThumbs.end(g);
                TvEditorPaint.playMark(g, cx + CELL - 5, cy + CELL - 6);
            }
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

    /** O rótulo de um grupo com a linha fina até a borda. */
    private void head(GuiGraphics g, GuiText.Fitted label, int y) {
        GuiText.draw(g, font, label, ax + 1 + inset + 2, y + 2, GuiPaint.MUTED);
        int lineX = ax + 1 + inset + 6 + label.width();
        if (ax + w - 1 - inset - lineX >= 8) {
            g.fill(lineX, y + 5, ax + w - 1 - inset, y + 6, GuiPaint.BUTTON_BORDER);
        }
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!inView(mouseX, mouseY)) {
            return -1;
        }
        int contentY = (int) mouseY - ay - 1 + scroll;
        for (int i = 0; i < items.length; i++) {
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
        if (index >= 0 && !items[index].id().equals(current)) {
            choose.accept(items[index]);
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

    /** O centro da célula de {@code id} (arte ou imagem), rolada até aparecer, ou {@code null} se a grade não a tem. */
    int @Nullable [] centerOf(UUID id) {
        rebuild();
        for (int i = 0; i < items.length; i++) {
            if (items[i].id().equals(id)) {
                int view = VIEW_H - 2;
                if (cellY[i] < scroll || cellY[i] + CELL > scroll + view) {
                    scroll = Mth.clamp(cellY[i] - HEAD_H, 0, maxScroll());
                }
                return new int[] {ax + 1 + cellX[i] + CELL / 2, ay + 1 + cellY[i] - scroll + CELL / 2};
            }
        }
        return null;
    }

    /** Se {@code id} está na grade entre as "Da Tela" ou as "Imagens da Tela" (só no layout, fora da biblioteca). */
    boolean onScreenOnly(UUID id) {
        rebuild();
        for (int i = 0; i < items.length; i++) {
            if (items[i].id().equals(id)) {
                return onScreen[i];
            }
        }
        return false;
    }
}
