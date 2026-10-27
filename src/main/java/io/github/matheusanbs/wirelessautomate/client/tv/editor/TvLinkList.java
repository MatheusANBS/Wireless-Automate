package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A lista dos armazenamentos ligados à Tela, na aba Dado: uma linha por armazenamento com o nome do tipo e
 * a distância (ou a dimensão, se é outra) e, embaixo, as coordenadas. O ligado ao dado da peça fica marcado; o clique escolhe. Os
 * textos são medidos uma vez, na montagem da aba; a distância é calculada na montagem e quando a aba se refaz.
 */
final class TvLinkList extends TvControl {
    static final int ROW_H = 20;
    private static final int MAX_ROWS = 3;

    private final Font font;
    private final List<TvMenu.LinkChoice> links;
    private final Supplier<GlobalPos> selected;
    private final Consumer<GlobalPos> choose;
    private final GuiText.Fitted[] names;
    private final GuiText.Fitted[] places;
    private final GuiText.Fitted[] distances;
    private final int[] distanceWidths;
    private final int rows;
    private int scroll;
    private @Nullable GlobalPos current;

    TvLinkList(Font font, String id, int x, int y, List<TvMenu.LinkChoice> links, TvDataState state,
            Supplier<GlobalPos> selected, Consumer<GlobalPos> choose) {
        super(id, x, y, TvForm.W, Math.min(MAX_ROWS, Math.max(1, links.size())) * ROW_H + 2);
        this.font = font;
        this.links = links;
        this.selected = selected;
        this.choose = choose;
        this.rows = Math.min(MAX_ROWS, links.size());
        names = new GuiText.Fitted[links.size()];
        places = new GuiText.Fitted[links.size()];
        distances = new GuiText.Fitted[links.size()];
        distanceWidths = new int[links.size()];
        for (int i = 0; i < names.length; i++) {
            TvMenu.LinkChoice link = links.get(i);
            places[i] = GuiText.fit(font, Component.literal(coords(link.pos())), TvForm.W - 8);
            distances[i] = GuiText.fit(font, Component.literal(distance(link.pos())), TvForm.W / 2 - 4);
            distanceWidths[i] = font.width(distances[i].shown());
            names[i] = GuiText.fit(font, name(state.kindOf(link.pos())), TvForm.W - distanceWidths[i] - 14);
        }
        current = selected.get();
    }

    /** O nome do tipo do armazenamento (o bloco do mod), ou "Armazenamento" se o servidor não o conhecia. */
    static Component name(@Nullable StorageKind kind) {
        return kind == null ? TvPropsCtx.key("data.storage.unknown")
                : TvPropsCtx.key("data.storage.kind." + kind.getSerializedName());
    }

    private static String coords(GlobalPos pos) {
        BlockPos p = pos.pos();
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    /** A distância em blocos, ou a dimensão se é outra. */
    private static String distance(GlobalPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return "";
        }
        if (!minecraft.level.dimension().equals(pos.dimension())) {
            return pos.dimension().location().getPath();
        }
        return (int) Math.round(Math.sqrt(minecraft.player.blockPosition().distSqr(pos.pos()))) + " m";
    }

    private int maxScroll() {
        return Math.max(0, links.size() - rows);
    }

    @Override
    void refresh() {
        current = selected.get();
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        GuiPaint.inset(g, ax, ay, w, h);
        int end = Math.min(links.size(), scroll + rows);
        for (int i = scroll; i < end; i++) {
            int rowY = ay + 1 + (i - scroll) * ROW_H;
            if (links.get(i).pos().equals(current)) {
                g.fill(ax + 1, rowY, ax + w - 1, rowY + ROW_H, GuiPaint.ROW_SELECTED);
            } else if (mouseX >= ax && mouseX < ax + w && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(ax + 1, rowY, ax + w - 1, rowY + ROW_H, GuiPaint.ROW_HOVER);
            }
            GuiText.draw(g, font, names[i], ax + 4, rowY + 2, GuiPaint.FG);
            GuiText.draw(g, font, distances[i], ax + w - 4 - distanceWidths[i], rowY + 2, GuiPaint.MUTED);
            GuiText.draw(g, font, places[i], ax + 4, rowY + 11, GuiPaint.MUTED);
        }
        if (maxScroll() > 0) {
            int track = h - 2;
            int thumb = Math.max(6, track * rows / links.size());
            int thumbY = ay + 1 + (track - thumb) * scroll / maxScroll();
            g.fill(ax + w - 3, thumbY, ax + w - 1, thumbY + thumb, GuiPaint.BUTTON_BORDER);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int row = scroll + (int) (mouseY - ay - 1) / ROW_H;
        if (row >= scroll && row < Math.min(links.size(), scroll + rows)) {
            choose.accept(links.get(row).pos());
        }
        return true;
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY) || maxScroll() == 0) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    /** O centro da linha de um armazenamento, se está à vista (gancho do e2e). */
    int @Nullable [] centerOf(GlobalPos pos) {
        for (int i = scroll; i < Math.min(links.size(), scroll + rows); i++) {
            if (links.get(i).pos().equals(pos)) {
                return new int[] {ax + w / 2, ay + 1 + (i - scroll) * ROW_H + ROW_H / 2};
            }
        }
        return null;
    }
}
