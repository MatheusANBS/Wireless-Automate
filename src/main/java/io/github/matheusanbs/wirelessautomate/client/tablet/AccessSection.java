package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.MIN_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.RX;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * O acesso a uma rede privada, na coluna da direita da aba Redes, logo abaixo do segmento Pública/Privada e da cor:
 * os convidados, com o campo de convidar jogador, e os times do FTB Teams (só com ele instalado), com o campo de
 * adicionar time. Os dois campos têm a lista de sugestões ({@link SuggestField}). Tirar um convidado ou um time pede
 * dois cliques. As linhas são montadas quando o acesso da rede muda, não por quadro; a lista de sugestões é
 * desenhada por cima, depois dos widgets ({@link #renderOverlay}). Cresce com a altura da tela: as linhas a mais vão
 * para as listas.
 */
public final class AccessSection {
    private static final int ROW = 13;
    /** Onde a seção começa, em relação ao corpo da aba: abaixo do segmento e da cor. */
    static final int LABEL_Y = BODY_Y + 62;
    private static final int PLUS_W = 14;
    private static final Component MORE = Component.literal("▼");
    private static final Component PLUS = Component.literal("+");
    private static final Component REMOVE = tr("access.remove");
    private static final Component REMOVE_ARMED = tr("access.remove.armed");

    /** Uma linha de convidado ou de time: o id, o nome, o detalhe à direita e o estado. */
    private record Row(UUID id, Component name, @Nullable Component detail, int detailW, boolean online, boolean gone) {
    }

    private final TabletLayout layout;
    private final Supplier<@Nullable NetworkView> network;
    private final TabletActions actions;
    private final SuggestField guestField;
    private final SuggestField teamField;
    private final ArmedClick removeArm = new ArmedClick();
    private @Nullable UUID armedId;
    /** A seção está à vista: aba Redes, rede privada que o jogador gerencia. */
    private boolean active;
    private int guestScroll;
    private int teamScroll;
    // montadas quando o acesso da rede muda
    private @Nullable AccessView built;
    private List<Row> guests = List.of();
    private List<Row> teams = List.of();

    AccessSection(TabletLayout layout, TabletMenu menu, TabletActions actions,
            Supplier<@Nullable NetworkView> network) {
        this.layout = layout;
        this.network = network;
        this.actions = actions;
        Supplier<@Nullable UUID> id = () -> {
            NetworkView view = network.get();
            return view == null ? null : view.id();
        };
        this.guestField = new SuggestField(NetworkSuggestPayload.Kind.PLAYER, menu, actions, id, tr("access.guest.add"));
        this.teamField = new SuggestField(NetworkSuggestPayload.Kind.TEAM, menu, actions, id, tr("access.team.add"));
    }

    /** Larga os campos, as rolagens e o que estava armado (outra rede, outra aba, tela refeita). */
    void reset() {
        guestField.clear();
        teamField.clear();
        guestScroll = 0;
        teamScroll = 0;
        armedId = null;
        removeArm.disarm();
    }

    private boolean withTeams() {
        return TeamAccess.LOADED;
    }

    /** As linhas que a altura da tela dá a mais que o mínimo. */
    private int extraRows() {
        return Math.max(0, (layout.h - MIN_H) / ROW);
    }

    private int guestRowsY() {
        return LABEL_Y + 11;
    }

    private int guestRows() {
        return withTeams() ? 2 + (extraRows() + 1) / 2 : 5 + extraRows();
    }

    private int guestBoxY() {
        return guestRowsY() + guestRows() * ROW + 1;
    }

    private int teamLabelY() {
        return guestBoxY() + ROW_H + 3;
    }

    private int teamRowsY() {
        return teamLabelY() + 11;
    }

    private int teamRows() {
        return withTeams() ? 1 + extraRows() / 2 : 0;
    }

    private int teamBoxY() {
        return teamRowsY() + teamRows() * ROW + 1;
    }

    // ------------------------------------------------------------------ widgets

    /** Os dois campos, refeitos a cada {@code init}; a tela os põe entre os widgets dela. */
    List<EditBox> createBoxes(Font font) {
        int x = layout.left + RX + 4;
        int width = layout.rw() - 8 - PLUS_W;
        EditBox guest = guestField.createBox(font, x, layout.top + guestBoxY() + 3, width);
        EditBox team = teamField.createBox(font, x, layout.top + teamBoxY() + 3, width);
        return List.of(guest, team);
    }

    /** A cor do texto e da dica dos campos segue o tema. */
    void ink() {
        if (guestField.box() != null) {
            GuiPaint.ink(guestField.box(), guestField.hint());
            GuiPaint.ink(teamField.box(), teamField.hint());
        }
    }

    /** {@code shown}: a aba Redes aberta com uma rede privada que o jogador gerencia escolhida. */
    void refresh(boolean shown) {
        if (active && !shown) {
            reset();
        }
        active = shown;
        if (guestField.box() != null) {
            guestField.box().visible = shown;
            teamField.box().visible = shown && withTeams();
        }
    }

    boolean active() {
        return active;
    }

    // ------------------------------------------------------------------ linhas

    private void rebuild(AccessView view) {
        if (view == built) {
            return;
        }
        built = view;
        Font font = TabletList.font();
        List<Row> g = new ArrayList<>(view.guests().size());
        for (var guest : view.guests()) {
            g.add(new Row(guest.id(), Component.literal(guest.name()), null, 0, guest.online(), false));
        }
        guests = g;
        List<Row> t = new ArrayList<>(view.teams().size());
        for (TeamAccess.TeamInfo team : view.teams()) {
            Component detail = team.gone() ? tr("access.gone") : plural("access.members", team.members());
            t.add(new Row(team.id(), Component.literal(team.name()), detail, font.width(detail), false, team.gone()));
        }
        teams = t;
    }

    // ------------------------------------------------------------------ entrada

    private int rowAt(double mouseX, double mouseY, int firstY, int rows, int scroll, int count) {
        int x = layout.left + RX;
        int y = layout.top + firstY;
        if (mouseX < x || mouseX >= x + layout.rw() || mouseY < y || mouseY >= y + rows * ROW) {
            return -1;
        }
        int index = (int) ((mouseY - y) / ROW) + scroll;
        return index < count ? index : -1;
    }

    private boolean overRemove(double mouseX) {
        return mouseX >= layout.left + RX + layout.rw() - 14 && mouseX < layout.left + RX + layout.rw();
    }

    private boolean overPlus(double mouseX, double mouseY, int boxY) {
        int x = layout.left + RX + layout.rw() - PLUS_W;
        int y = layout.top + boxY;
        return mouseX >= x && mouseX < x + PLUS_W && mouseY >= y && mouseY < y + ROW_H;
    }

    private boolean overField(double mouseX, double mouseY, int boxY) {
        int x = layout.left + RX;
        int y = layout.top + boxY;
        return mouseX >= x && mouseX < x + layout.rw() - PLUS_W && mouseY >= y && mouseY < y + ROW_H;
    }

    /** Cliques da seção: a lista de sugestões aberta, o ✕ das linhas e o +. Devolve se tratou. */
    boolean mouseClicked(double mouseX, double mouseY) {
        if (!active) {
            return false;
        }
        if (guestField.mouseClicked(mouseX, mouseY) || teamField.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        NetworkView view = network.get();
        if (view == null) {
            return false;
        }
        UUID net = view.id();
        int guest = rowAt(mouseX, mouseY, guestRowsY(), guestRows(), guestScroll, guests.size());
        int team = withTeams() ? rowAt(mouseX, mouseY, teamRowsY(), teamRows(), teamScroll, teams.size()) : -1;
        if (!overRemove(mouseX) || guest < 0 && team < 0) {
            armedId = null;
            removeArm.disarm();
        }
        if (guest >= 0 && overRemove(mouseX)) {
            return removeRow(guests.get(guest).id(), () -> actions.member(net, guests.get(guest).id(), "", false));
        }
        if (team >= 0 && overRemove(mouseX)) {
            return removeRow(teams.get(team).id(), () -> actions.team(net, teams.get(team).id(), "", false));
        }
        if (overPlus(mouseX, mouseY, guestBoxY())) {
            guestField.submit();
            return true;
        }
        if (withTeams() && overPlus(mouseX, mouseY, teamBoxY())) {
            teamField.submit();
            return true;
        }
        if (overField(mouseX, mouseY, guestBoxY())) {
            guestField.clicked();
        } else if (withTeams() && overField(mouseX, mouseY, teamBoxY())) {
            teamField.clicked();
        }
        return false;
    }

    /** O ✕ pede dois cliques na mesma linha: o primeiro arma, o segundo tira. */
    private boolean removeRow(UUID id, Runnable remove) {
        long now = System.currentTimeMillis();
        if (!id.equals(armedId)) {
            armedId = id;
            removeArm.arm(now);
            return true;
        }
        if (removeArm.click(now)) {
            armedId = null;
            remove.run();
        }
        return true;
    }

    void mouseScrolled(double mouseY, int step) {
        if (!active) {
            return;
        }
        if (mouseY < layout.top + teamLabelY() - 2 || !withTeams()) {
            guestScroll = Mth.clamp(guestScroll + step, 0, Math.max(0, guests.size() - guestRows()));
        } else {
            teamScroll = Mth.clamp(teamScroll + step, 0, Math.max(0, teams.size() - teamRows()));
        }
    }

    /** Teclas de um dos campos com o cursor: setas, Tab, Enter e Esc da lista de sugestões. */
    boolean keyPressed(@Nullable EditBox focused, int keyCode) {
        if (!active || focused == null) {
            return false;
        }
        if (focused == guestField.box()) {
            return guestField.keyPressed(keyCode);
        }
        return focused == teamField.box() && teamField.keyPressed(keyCode);
    }

    @Nullable Component tooltip(int mouseX, int mouseY) {
        if (!active) {
            return null;
        }
        int guest = rowAt(mouseX, mouseY, guestRowsY(), guestRows(), guestScroll, guests.size());
        int team = withTeams() ? rowAt(mouseX, mouseY, teamRowsY(), teamRows(), teamScroll, teams.size()) : -1;
        if ((guest >= 0 || team >= 0) && overRemove(mouseX)) {
            return tr("access.remove.tooltip");
        }
        if (overPlus(mouseX, mouseY, guestBoxY()) || withTeams() && overPlus(mouseX, mouseY, teamBoxY())) {
            return tr("access.add.tooltip");
        }
        return null;
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        NetworkView view = network.get();
        if (!active || view == null) {
            return;
        }
        rebuild(view.access());
        guestField.update();
        teamField.update();
        guestField.hover(mouseX, mouseY);
        teamField.hover(mouseX, mouseY);
        int x = layout.left + RX;
        int y = layout.top;
        int rw = layout.rw();
        GuiPaint.text(g, font, tr("access.guests"), x, y + LABEL_Y, GuiPaint.FG);
        if (guests.isEmpty()) {
            GuiText.draw(g, font, tr("access.guests.empty"), x, y + guestRowsY() + 2, rw, GuiPaint.MUTED);
        }
        renderRows(g, font, mouseX, mouseY, guests, guestRowsY(), guestRows(), guestScroll);
        renderField(g, guestField, guestBoxY(), mouseX, mouseY);
        if (withTeams()) {
            GuiPaint.text(g, font, tr("access.teams"), x, y + teamLabelY(), GuiPaint.FG);
            if (teams.isEmpty()) {
                GuiText.draw(g, font, tr("access.teams.empty"), x, y + teamRowsY() + 2, rw, GuiPaint.MUTED);
            }
            renderRows(g, font, mouseX, mouseY, teams, teamRowsY(), teamRows(), teamScroll);
            renderField(g, teamField, teamBoxY(), mouseX, mouseY);
        }
    }

    private void renderRows(GuiGraphics g, Font font, int mouseX, int mouseY, List<Row> rows, int firstY, int visible,
            int scroll) {
        int x = layout.left + RX;
        int rw = layout.rw();
        int hovered = rowAt(mouseX, mouseY, firstY, visible, scroll, rows.size());
        for (int i = 0; i < visible && i + scroll < rows.size(); i++) {
            Row row = rows.get(i + scroll);
            int ry = layout.top + firstY + i * ROW;
            if (i + scroll == hovered) {
                g.fill(x, ry, x + rw, ry + ROW, GuiPaint.ROW_SELECTED);
            }
            boolean armed = row.id().equals(armedId) && removeArm.armed(System.currentTimeMillis());
            int right = x + rw - 14;
            if (row.detail() != null) {
                GuiPaint.textRight(g, font, row.detail(), right - 2, ry + 3, row.gone() ? GuiPaint.WARN : GuiPaint.MUTED);
                right -= row.detailW() + 6;
            } else {
                g.fill(x + 2, ry + 4, x + 7, ry + 9, row.online() ? GuiPaint.OK : GuiPaint.DISABLED);
            }
            GuiText.draw(g, font, row.name(), x + (row.detail() != null ? 3 : 11), ry + 3,
                    right - x - (row.detail() != null ? 3 : 11), row.gone() ? GuiPaint.DISABLED : GuiPaint.FG);
            GuiPaint.textRight(g, font, armed ? REMOVE_ARMED : REMOVE, x + rw - 3, ry + 3,
                    armed ? GuiPaint.DANGER : GuiPaint.MUTED);
        }
        if (scroll + visible < rows.size()) {
            GuiPaint.text(g, font, MORE, x + rw - 24, layout.top + firstY + visible * ROW - 8, GuiPaint.MUTED);
        }
    }

    private void renderField(GuiGraphics g, SuggestField field, int boxY, int mouseX, int mouseY) {
        int x = layout.left + RX;
        int y = layout.top + boxY;
        int rw = layout.rw();
        GuiPaint.box(g, x, y, rw - PLUS_W - 2, ROW_H, GuiPaint.INSET,
                field.box().isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);
        boolean hovered = overPlus(mouseX, mouseY, boxY);
        GuiPaint.box(g, x + rw - PLUS_W, y, PLUS_W, ROW_H, GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiPaint.text(g, TabletList.font(), PLUS, x + rw - PLUS_W + 4, y + 3, GuiPaint.FG);
    }

    /** As listas de sugestões, por cima de tudo (a tela chama depois de desenhar os widgets). */
    void renderOverlay(GuiGraphics g, Font font) {
        if (!active) {
            return;
        }
        int x = layout.left + RX;
        int width = layout.rw() - PLUS_W - 2;
        int bottom = layout.top + layout.h - 8;
        guestField.renderList(g, font, x, layout.top + guestBoxY(), width, ROW_H, bottom);
        if (withTeams()) {
            teamField.renderList(g, font, x, layout.top + teamBoxY(), width, ROW_H, bottom);
        }
    }

    // ------------------------------------------------------------------ ganchos do e2e e das capturas

    /** Centro do campo de convidar, ou do de time ({@code team}). */
    public int[] fieldCenter(boolean team) {
        int boxY = team ? teamBoxY() : guestBoxY();
        return new int[] {layout.left + RX + (layout.rw() - PLUS_W) / 2, layout.top + boxY + ROW_H / 2};
    }

    /** Centro do ✕ da linha {@code index} dos convidados ou dos times. */
    public int[] removeCenter(boolean team, int index) {
        int firstY = team ? teamRowsY() : guestRowsY();
        return new int[] {layout.left + RX + layout.rw() - 7, layout.top + firstY + index * ROW + ROW / 2};
    }

    /** Quantas linhas de convidado e de time estão montadas. */
    public int[] counts() {
        return new int[] {guests.size(), teams.size()};
    }

    /** A seção está à vista. */
    public boolean shown() {
        return active;
    }

    /** Quantas sugestões a lista do campo de convidar, ou do de time, tem agora. */
    public int suggestionCount(boolean team) {
        return (team ? teamField : guestField).count();
    }
}
