package io.github.matheusanbs.wirelessautomate.client.access;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Uma lista de acesso para o dono editar, comum à aba Redes do Tablet (o acesso a uma rede privada) e à aba Acesso da
 * Tela de Sinal: os convidados, com o campo de convidar jogador, e os times do FTB Teams (só com ele instalado), com o
 * campo de adicionar time. Os dois campos têm a lista de sugestões ({@link SuggestField}). Tirar um convidado ou um
 * time pede dois cliques. As linhas são montadas quando a lista muda, não por quadro; a lista de sugestões é desenhada
 * por cima, depois dos widgets ({@link #renderOverlay}). Onde fica e quantas linhas cada lista tem vem do
 * {@link Frame} da anfitriã, lido a cada uso (a tela pode mudar de tamanho); as ações saem pelo {@link AccessHost}.
 */
public final class AccessPanel {
    private static final int ROW = 13;
    /** A altura do campo de texto com o +. */
    private static final int FIELD_H = 14;
    private static final int PLUS_W = 14;
    private static final Component MORE = Component.literal("▼");
    private static final Component PLUS = Component.literal("+");
    private static final Component REMOVE = tr("access.remove");
    private static final Component REMOVE_ARMED = tr("access.remove.armed");

    /**
     * Onde a lista fica na tela, em coordenadas da tela: {@code x} e {@code labelY} do rótulo dos convidados, a
     * largura, as linhas visíveis de cada lista ({@code teamRows} só vale com o FTB Teams) e o limite de baixo da lista
     * de sugestões (sem espaço até ele, ela abre para cima).
     */
    public interface Frame {
        int x();

        int labelY();

        int width();

        int guestRows();

        int teamRows();

        int bottom();
    }

    /** Uma linha de convidado ou de time: o id, o nome, o detalhe à direita e o estado. */
    private record Row(UUID id, Component name, @Nullable Component detail, int detailW, boolean online, boolean gone) {
    }

    private final Frame frame;
    private final AccessHost host;
    private final SuggestField guestField;
    private final SuggestField teamField;
    private final ArmedClick removeArm = new ArmedClick();
    private @Nullable UUID armedId;
    /** O painel está à vista (a anfitriã decide quando). */
    private boolean active;
    private int guestScroll;
    private int teamScroll;
    // montadas quando a lista muda
    private @Nullable AccessView built;
    private List<Row> guests = List.of();
    private List<Row> teams = List.of();

    public AccessPanel(Frame frame, AccessHost host) {
        this.frame = frame;
        this.host = host;
        this.guestField = new SuggestField(NetworkSuggestPayload.Kind.PLAYER, host, tr("access.guest.add"));
        this.teamField = new SuggestField(NetworkSuggestPayload.Kind.TEAM, host, tr("access.team.add"));
    }

    /** Os textos do acesso: as chaves {@code gui.wirelessautomate.tablet.access.*}, comuns às duas telas. */
    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.tablet." + key, args);
    }

    /** {@code key} com o número, ou {@code key.one} quando é 1. */
    static Component plural(String key, long count) {
        return tr(count == 1 ? key + ".one" : key, count);
    }

    /** Larga os campos, as rolagens e o que estava armado (outra lista, outra aba, tela refeita). */
    public void reset() {
        guestField.clear();
        teamField.clear();
        guestScroll = 0;
        teamScroll = 0;
        armedId = null;
        removeArm.disarm();
    }

    /** A seção de times existe: o FTB Teams está instalado. */
    public static boolean withTeams() {
        return TeamAccess.LOADED;
    }

    private int guestRowsY() {
        return frame.labelY() + 11;
    }

    private int guestBoxY() {
        return guestRowsY() + frame.guestRows() * ROW + 1;
    }

    private int teamLabelY() {
        return guestBoxY() + FIELD_H + 3;
    }

    private int teamRowsY() {
        return teamLabelY() + 11;
    }

    private int teamRows() {
        return withTeams() ? frame.teamRows() : 0;
    }

    private int teamBoxY() {
        return teamRowsY() + teamRows() * ROW + 1;
    }

    /** A altura que o painel ocupa do rótulo dos convidados até o fim do último campo, com estas linhas. */
    public static int height(int guestRows, int teamRows) {
        int guests = 11 + guestRows * ROW + 1 + FIELD_H;
        return withTeams() ? guests + 3 + 11 + teamRows * ROW + 1 + FIELD_H : guests;
    }

    // ------------------------------------------------------------------ widgets

    /** Os dois campos, refeitos a cada {@code init}; a tela os põe entre os widgets dela. */
    public List<EditBox> createBoxes(Font font) {
        int x = frame.x() + 4;
        int width = frame.width() - 8 - PLUS_W;
        EditBox guest = guestField.createBox(font, x, guestBoxY() + 3, width);
        EditBox team = teamField.createBox(font, x, teamBoxY() + 3, width);
        return List.of(guest, team);
    }

    /** A cor do texto e da dica dos campos segue o tema. */
    public void ink() {
        if (guestField.box() != null) {
            GuiPaint.ink(guestField.box(), guestField.hint());
            GuiPaint.ink(teamField.box(), teamField.hint());
        }
    }

    /** {@code shown}: o painel está à vista; ao sumir, larga os campos. */
    public void refresh(boolean shown) {
        if (active && !shown) {
            reset();
        }
        active = shown;
        if (guestField.box() != null) {
            guestField.box().visible = shown;
            teamField.box().visible = shown && withTeams();
        }
    }

    public boolean active() {
        return active;
    }

    // ------------------------------------------------------------------ linhas

    private void rebuild(AccessView view) {
        if (view == built) {
            return;
        }
        built = view;
        Font font = Minecraft.getInstance().font;
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
        int x = frame.x();
        if (mouseX < x || mouseX >= x + frame.width() || mouseY < firstY || mouseY >= firstY + rows * ROW) {
            return -1;
        }
        int index = (int) ((mouseY - firstY) / ROW) + scroll;
        return index < count ? index : -1;
    }

    private boolean overRemove(double mouseX) {
        return mouseX >= frame.x() + frame.width() - 14 && mouseX < frame.x() + frame.width();
    }

    private boolean overPlus(double mouseX, double mouseY, int boxY) {
        int x = frame.x() + frame.width() - PLUS_W;
        return mouseX >= x && mouseX < x + PLUS_W && mouseY >= boxY && mouseY < boxY + FIELD_H;
    }

    private boolean overField(double mouseX, double mouseY, int boxY) {
        int x = frame.x();
        return mouseX >= x && mouseX < x + frame.width() - PLUS_W && mouseY >= boxY && mouseY < boxY + FIELD_H;
    }

    /** Cliques do painel: a lista de sugestões aberta, o ✕ das linhas e o +. Devolve se tratou. */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!active) {
            return false;
        }
        if (guestField.mouseClicked(mouseX, mouseY) || teamField.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        if (host.access() == null) {
            return false;
        }
        int guest = rowAt(mouseX, mouseY, guestRowsY(), frame.guestRows(), guestScroll, guests.size());
        int team = withTeams() ? rowAt(mouseX, mouseY, teamRowsY(), teamRows(), teamScroll, teams.size()) : -1;
        if (!overRemove(mouseX) || guest < 0 && team < 0) {
            armedId = null;
            removeArm.disarm();
        }
        if (guest >= 0 && overRemove(mouseX)) {
            return removeRow(guests.get(guest).id(), () -> host.member(guests.get(guest).id(), "", false));
        }
        if (team >= 0 && overRemove(mouseX)) {
            return removeRow(teams.get(team).id(), () -> host.team(teams.get(team).id(), "", false));
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

    public void mouseScrolled(double mouseY, int step) {
        if (!active) {
            return;
        }
        if (mouseY < teamLabelY() - 2 || !withTeams()) {
            guestScroll = Mth.clamp(guestScroll + step, 0, Math.max(0, guests.size() - frame.guestRows()));
        } else {
            teamScroll = Mth.clamp(teamScroll + step, 0, Math.max(0, teams.size() - teamRows()));
        }
    }

    /** Teclas de um dos campos com o cursor: setas, Tab, Enter e Esc da lista de sugestões. */
    public boolean keyPressed(@Nullable EditBox focused, int keyCode) {
        if (!active || focused == null) {
            return false;
        }
        if (focused == guestField.box()) {
            return guestField.keyPressed(keyCode);
        }
        return focused == teamField.box() && teamField.keyPressed(keyCode);
    }

    public @Nullable Component tooltip(int mouseX, int mouseY) {
        if (!active) {
            return null;
        }
        int guest = rowAt(mouseX, mouseY, guestRowsY(), frame.guestRows(), guestScroll, guests.size());
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

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        AccessView view = host.access();
        if (!active || view == null) {
            return;
        }
        rebuild(view);
        guestField.update();
        teamField.update();
        guestField.hover(mouseX, mouseY);
        teamField.hover(mouseX, mouseY);
        int x = frame.x();
        int rw = frame.width();
        GuiPaint.text(g, font, tr("access.guests"), x, frame.labelY(), GuiPaint.FG);
        if (guests.isEmpty()) {
            GuiText.draw(g, font, tr("access.guests.empty"), x, guestRowsY() + 2, rw, GuiPaint.MUTED);
        }
        renderRows(g, font, mouseX, mouseY, guests, guestRowsY(), frame.guestRows(), guestScroll);
        renderField(g, guestField, guestBoxY(), mouseX, mouseY);
        if (withTeams()) {
            GuiPaint.text(g, font, tr("access.teams"), x, teamLabelY(), GuiPaint.FG);
            if (teams.isEmpty()) {
                GuiText.draw(g, font, tr("access.teams.empty"), x, teamRowsY() + 2, rw, GuiPaint.MUTED);
            }
            renderRows(g, font, mouseX, mouseY, teams, teamRowsY(), teamRows(), teamScroll);
            renderField(g, teamField, teamBoxY(), mouseX, mouseY);
        }
    }

    private void renderRows(GuiGraphics g, Font font, int mouseX, int mouseY, List<Row> rows, int firstY, int visible,
            int scroll) {
        int x = frame.x();
        int rw = frame.width();
        int hovered = rowAt(mouseX, mouseY, firstY, visible, scroll, rows.size());
        for (int i = 0; i < visible && i + scroll < rows.size(); i++) {
            Row row = rows.get(i + scroll);
            int ry = firstY + i * ROW;
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
            GuiPaint.text(g, font, MORE, x + rw - 24, firstY + visible * ROW - 8, GuiPaint.MUTED);
        }
    }

    private void renderField(GuiGraphics g, SuggestField field, int boxY, int mouseX, int mouseY) {
        int x = frame.x();
        int rw = frame.width();
        GuiPaint.box(g, x, boxY, rw - PLUS_W - 2, FIELD_H, GuiPaint.INSET,
                field.box().isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);
        boolean hovered = overPlus(mouseX, mouseY, boxY);
        GuiPaint.box(g, x + rw - PLUS_W, boxY, PLUS_W, FIELD_H, GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiPaint.text(g, Minecraft.getInstance().font, PLUS, x + rw - PLUS_W + 4, boxY + 3, GuiPaint.FG);
        EditBox box = field.box();
        if (box.isFocused() && box.getValue().isEmpty()) {
            // O campo do jogo esconde a dica com o foco; aqui ela fica, depois do cursor, até a primeira letra.
            GuiPaint.text(g, Minecraft.getInstance().font, field.hint(), box.getX() + 6, box.getY(), GuiPaint.DISABLED);
        }
    }

    /** As listas de sugestões, por cima de tudo (a tela chama depois de desenhar os widgets). */
    public void renderOverlay(GuiGraphics g, Font font) {
        if (!active) {
            return;
        }
        int x = frame.x();
        int width = frame.width() - PLUS_W - 2;
        guestField.renderList(g, font, x, guestBoxY(), width, FIELD_H, frame.bottom());
        if (withTeams()) {
            teamField.renderList(g, font, x, teamBoxY(), width, FIELD_H, frame.bottom());
        }
    }

    // ------------------------------------------------------------------ ganchos do e2e e das capturas

    /** Centro do campo de convidar, ou do de time ({@code team}). */
    public int[] fieldCenter(boolean team) {
        int boxY = team ? teamBoxY() : guestBoxY();
        return new int[] {frame.x() + (frame.width() - PLUS_W) / 2, boxY + FIELD_H / 2};
    }

    /** Centro do ✕ da linha {@code index} dos convidados ou dos times. */
    public int[] removeCenter(boolean team, int index) {
        int firstY = team ? teamRowsY() : guestRowsY();
        return new int[] {frame.x() + frame.width() - 7, firstY + index * ROW + ROW / 2};
    }

    /** Quantas linhas de convidado e de time estão montadas. */
    public int[] counts() {
        return new int[] {guests.size(), teams.size()};
    }

    /** O painel está à vista. */
    public boolean shown() {
        return active;
    }

    /** Quantas sugestões a lista do campo de convidar, ou do de time, tem agora. */
    public int suggestionCount(boolean team) {
        return (team ? teamField : guestField).count();
    }
}
