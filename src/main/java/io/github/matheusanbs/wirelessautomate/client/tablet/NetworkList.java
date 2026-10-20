package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.SIDE_W;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.tablet.NetworkSections.Row;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * A lista da esquerda da aba Redes, com os grupos de redes como seções ({@link NetworkSections}): o cabeçalho de
 * cada grupo (▾/▸ que recolhe, o nome e, à direita, Pausar ou "pausado") e as redes dele; as sem grupo em "Sem
 * grupo", por último. Os textos são montados quando as redes, os grupos, as seções recolhidas ou o tema mudam; por
 * quadro, só o desenho. Clicar numa rede ou num cabeçalho escolhe (o detalhe é do {@link NetworksTab}).
 */
final class NetworkList {
    /** O que o clique pegou. */
    enum Pick { NONE, NETWORK, GROUP }

    /**
     * Uma linha pronta.
     *
     * @param id    a rede, ou o grupo num cabeçalho ({@code null} no "Sem grupo")
     * @param mark  à direita: a marca da rede (ativa, pública) ou, no cabeçalho, Pausar ou "pausado"
     * @param pause o cabeçalho tem o botão de pausar e retomar (o grupo é do jogador e tem redes)
     * @param tip   a dica da linha: "também em …" numa rede em outros grupos
     */
    private record Line(Row row, @Nullable UUID id, Component name, int color, @Nullable Component mark, int markW,
            int markColor, boolean pause, boolean paused, @Nullable Component tip) {
    }

    private static final int CHEVRON_W = 10;

    private final Supplier<TabletSnapshot> snapshot;
    private final SideList side;
    private final TabletActions actions;
    private final Set<UUID> collapsed = new HashSet<>();
    private List<Line> lines = List.of();
    private @Nullable List<NetworkView> builtNetworks;
    private @Nullable List<GroupView> builtGroups;
    private @Nullable Optional<UUID> builtActive;
    private int builtTheme = -1;
    private boolean dirty = true;
    private @Nullable UUID picked;

    NetworkList(Supplier<TabletSnapshot> snapshot, SideList side, TabletActions actions) {
        this.snapshot = snapshot;
        this.side = side;
        this.actions = actions;
    }

    /** As linhas, montadas de novo só se as redes, os grupos, as recolhidas ou o tema mudaram. */
    private List<Line> lines(Font font) {
        TabletSnapshot s = snapshot.get();
        if (!dirty && s.networks() == builtNetworks && s.groups() == builtGroups
                && s.activeNetwork().equals(builtActive) && builtTheme == GuiThemes.version()) {
            return lines;
        }
        dirty = false;
        builtNetworks = s.networks();
        builtGroups = s.groups();
        builtActive = s.activeNetwork();
        builtTheme = GuiThemes.version();
        List<UUID> networkIds = new ArrayList<>(s.networks().size());
        for (NetworkView n : s.networks()) {
            networkIds.add(n.id());
        }
        List<NetworkSections.Group> groups = new ArrayList<>(s.groups().size());
        for (GroupView g : s.groups()) {
            groups.add(new NetworkSections.Group(g.id(), g.networks()));
        }
        List<Line> built = new ArrayList<>();
        for (Row row : NetworkSections.build(networkIds, groups, collapsed)) {
            built.add(row.header() ? header(font, s, row) : network(font, s, row));
        }
        lines = built;
        return lines;
    }

    private Line header(Font font, TabletSnapshot s, Row row) {
        if (row.group() < 0) {
            return new Line(row, null, tr("networks.no_group"), GuiPaint.MUTED, null, 0, 0, false, false, null);
        }
        GroupView group = s.groups().get(row.group());
        boolean pause = group.manageable() && !group.networks().isEmpty();
        Component mark = pause && !group.paused() ? tr("networks.pause") : group.paused() ? tr("networks.paused") : null;
        int markColor = group.paused() ? GuiPaint.PAUSED : GuiPaint.FG;
        return new Line(row, group.id(), Component.literal(group.name()), GuiPaint.FG, mark,
                mark == null ? 0 : font.width(mark), markColor, pause, group.paused(), null);
    }

    private Line network(Font font, TabletSnapshot s, Row row) {
        NetworkView n = s.networks().get(row.network());
        boolean active = s.activeNetwork().equals(Optional.of(n.id()));
        Component mark = active ? tr("mark.active") : n.isPublic() ? tr("mark.public") : null;
        Component tip = null;
        if (!row.others().isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < row.others().size(); i++) {
                names.append(i == 0 ? "" : ", ").append(s.groups().get(row.others().get(i)).name());
            }
            tip = tr("networks.also_in", names);
        }
        return new Line(row, n.id(), Component.literal(n.name()), 0xFF000000 | n.color(), mark,
                mark == null ? 0 : font.width(mark), GuiPaint.MUTED, false, false, tip);
    }

    int count(Font font) {
        return lines(font).size();
    }

    /** Uma seção recolhida ou aberta: as linhas são montadas de novo. */
    private void toggle(@Nullable UUID group) {
        UUID key = group == null ? NetworkSections.NO_GROUP : group;
        if (!collapsed.remove(key)) {
            collapsed.add(key);
        }
        dirty = true;
    }

    // ------------------------------------------------------------------ geometria

    /** A esquerda do botão de pausar do cabeçalho, ou -1 sem ele. */
    private int pauseX(Line line) {
        return line.pause && line.mark != null ? side.x() + SIDE_W - 3 - pauseW(line) : -1;
    }

    private static int pauseW(Line line) {
        return line.markW + 6;
    }

    private boolean overPause(Line line, int index, double mouseX, double mouseY) {
        int px = pauseX(line);
        int y = side.rowY(index);
        return px >= 0 && mouseX >= px && mouseX < px + pauseW(line) && mouseY >= y + 1 && mouseY < y + ROW_H - 1;
    }

    // ------------------------------------------------------------------ entrada

    /** O que o clique escolheu ({@link #pickedId}); recolher e pausar são feitos aqui. */
    Pick mouseClicked(Font font, double mouseX, double mouseY) {
        List<Line> all = lines(font);
        int index = side.rowAt(mouseX, mouseY, all.size());
        if (index < 0) {
            return Pick.NONE;
        }
        Line line = all.get(index);
        picked = line.id;
        if (!line.row.header()) {
            return Pick.NETWORK;
        }
        if (line.id == null || mouseX < side.x() + CHEVRON_W) {
            toggle(line.id);
            return Pick.NONE;
        }
        if (overPause(line, index, mouseX, mouseY)) {
            actions.groupPause(line.id, line.paused);
            return Pick.NONE;
        }
        return Pick.GROUP;
    }

    /** A rede ou o grupo do último clique que escolheu. */
    @Nullable UUID pickedId() {
        return picked;
    }

    void mouseScrolled(Font font, int step) {
        side.mouseScrolled(step, count(font));
    }

    @Nullable Component tooltip(Font font, int mouseX, int mouseY) {
        List<Line> all = lines(font);
        int index = side.rowAt(mouseX, mouseY, all.size());
        if (index < 0) {
            return null;
        }
        Line line = all.get(index);
        if (!line.row.header()) {
            return line.tip;
        }
        if (line.id == null || mouseX < side.x() + CHEVRON_W) {
            return tr(line.row.collapsed() ? "list.group.open" : "list.group.close");
        }
        if (overPause(line, index, mouseX, mouseY)) {
            return tr(line.paused ? "group.resume" : "group.pause");
        }
        return tr("networks.group.tooltip");
    }

    /** Centro do cabeçalho do grupo, ou {@code null} fora da vista. */
    int @Nullable [] groupCenter(Font font, UUID group) {
        List<Line> all = lines(font);
        for (int i = 0; i < all.size(); i++) {
            Line line = all.get(i);
            if (line.row.header() && group.equals(line.id) && side.shown(i)) {
                return new int[] {side.x() + SIDE_W / 2, side.rowY(i) + ROW_H / 2};
            }
        }
        return null;
    }

    /** Põe na vista a primeira linha da rede ou do cabeçalho do grupo. */
    void reveal(Font font, UUID id) {
        List<Line> all = lines(font);
        for (int i = 0; i < all.size(); i++) {
            if (id.equals(all.get(i).id)) {
                side.reveal(i, all.size());
                return;
            }
        }
    }

    // ------------------------------------------------------------------ desenho

    /** A lista; {@code network} e {@code group}: o escolhido, em destaque (a rede em todas as seções em que está). */
    void render(GuiGraphics g, Font font, int mouseX, int mouseY, @Nullable UUID network, @Nullable UUID group) {
        List<Line> all = lines(font);
        side.renderFrame(g, font, all.size(), tr("networks.empty"));
        int hovered = side.rowAt(mouseX, mouseY, all.size());
        boolean sections = !snapshot.get().groups().isEmpty();
        int x = side.x();
        for (int i = side.scroll(); i < all.size() && side.shown(i); i++) {
            Line line = all.get(i);
            int y = side.rowY(i);
            boolean chosen = line.id != null && line.id.equals(line.row.header() ? group : network);
            if (chosen) {
                g.fill(x + 1, y + 1, x + SIDE_W - 1, y + ROW_H, GuiPaint.ROW_SELECTED);
                g.fill(x + 1, y + 1, x + 2, y + ROW_H, GuiPaint.ACCENT);
            } else if (i == hovered) {
                g.fill(x + 1, y + 1, x + SIDE_W - 1, y + ROW_H, GuiPaint.ROW_HOVER);
            }
            int right = x + SIDE_W - 4;
            if (line.row.header()) {
                renderHeader(g, font, line, i, x, y, right, mouseX, mouseY);
                continue;
            }
            int eyeX = x + (sections ? 9 : 5);
            GuiPaint.eye(g, eyeX, y + 5, line.color);
            if (line.mark != null) {
                GuiPaint.textRight(g, font, line.mark, right, y + 4, line.markColor);
                right -= line.markW + 3;
            }
            GuiText.draw(g, font, line.name, eyeX + 9, y + 4, right - eyeX - 9, GuiPaint.FG);
        }
        side.renderMore(g, font, all.size());
    }

    private void renderHeader(GuiGraphics g, Font font, Line line, int index, int x, int y, int right, int mouseX,
            int mouseY) {
        g.fill(x + 1, y + ROW_H - 1, x + SIDE_W - 1, y + ROW_H, GuiPaint.LINE);
        boolean overChevron = mouseX >= x && mouseX < x + CHEVRON_W && side.rowAt(mouseX, mouseY, index + 1) == index;
        int chevron = overChevron ? GuiPaint.FG : GuiPaint.MUTED;
        if (line.row.collapsed()) {
            GuiPaint.arrowRight(g, x + 4, y + 4, chevron);
        } else {
            GuiPaint.arrowDown(g, x + 3, y + 5, chevron);
        }
        int px = pauseX(line);
        if (px >= 0) {
            boolean over = overPause(line, index, mouseX, mouseY);
            GuiPaint.box(g, px, y + 2, pauseW(line), ROW_H - 3, over ? GuiPaint.BUTTON : GuiPaint.PANEL,
                    line.paused ? GuiPaint.PAUSED : over ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            GuiPaint.text(g, font, line.mark, px + 3, y + 4, line.markColor);
            right = px - 3;
        } else if (line.mark != null) {
            GuiPaint.textRight(g, font, line.mark, right, y + 4, line.markColor);
            right -= line.markW + 3;
        }
        GuiText.draw(g, font, line.name, x + CHEVRON_W + 1, y + 4, right - x - CHEVRON_W - 1, line.color);
    }
}
