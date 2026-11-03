package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowRow.Kind;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.Net;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.NetLine;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.Part;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.Totals;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.TypeLine;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TabletFlowsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletFlowsPayload.NetworkFlows;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Fluxos do Tablet: o tempo do mod por tick com a barra do orçamento, o seletor Por tipo | Por rede e as
 * linhas do que entra e sai, média dos últimos 5 s ({@link TabletFlowsPayload}, que o servidor só manda com esta
 * aba aberta). Por tipo: uma linha por tipo em uso com o total das redes não pausadas; a seta abre as redes do
 * tipo; as pausadas vêm no fim, apagadas. Por rede: o cabeçalho de cada rede e os tipos dela. O detalhe fica
 * na dica; o clique numa linha abre a Lista filtrada pelo tipo e pela rede dela. A vista e os tipos abertos
 * ficam lembrados na sessão. As linhas são desenhadas e clicadas por geometria; a lógica pura fica no
 * {@link FlowLines} e os textos no {@link FlowsText}. Linhas e textos só são refeitos quando os Fluxos, as
 * redes do cabeçalho, a vista, os tipos abertos ou o tema mudam; o quadro só desenha.
 */
public final class FlowsTab {
    private static final int MODE_Y = BODY_Y + 20;
    private static final int MODE_H = 13;
    private static final int LIST_Y = BODY_Y + 39;
    /** Onde começa o "entra → sai" numa linha, a partir da borda esquerda da lista. */
    private static final int MID_X = 92;
    /** A seta de abrir um tipo: os primeiros pixels da linha. */
    private static final int CHEVRON_W = 10;
    private static final Component MORE_BELOW = Component.literal("▼");

    /** Por rede (e não Por tipo), lembrado na sessão. */
    private static boolean byNetwork;
    /** Os tipos abertos Por tipo, lembrados na sessão. */
    private static final Set<ResourceType> OPEN = EnumSet.noneOf(ResourceType.class);
    /** Muda a cada tipo aberto ou fechado, para refazer as linhas. */
    private static int openVersion;

    private final TabletLayout layout;
    private final TabletMenu menu;
    /** Troca para a Lista com o filtro do tipo e da rede. */
    private final BiConsumer<Optional<ResourceType>, Optional<UUID>> openList;
    /** Os Fluxos que já estavam no menu quando a aba abriu: velhos até chegar um novo. */
    private @Nullable TabletFlowsPayload stale;
    private int scroll;
    /** As linhas do último quadro, na ordem; a primeira à vista é a {@link #scroll}. */
    private final List<FlowRow> rows = new ArrayList<>();
    // De onde as linhas saíram: refeitas só quando algum muda.
    private @Nullable TabletFlowsPayload rowsFlows;
    private @Nullable TabletSnapshot rowsSnapshot;
    private boolean rowsByNetwork;
    private int rowsOpen = -1;
    private int rowsTheme = -1;
    /** O texto da lista vazia, refeito com as linhas. */
    private Component empty = Component.empty();
    private final Component caption = tr("flows.caption");
    private final Component timeLabel = tr("flows.time");
    /** O tempo do mod dos Fluxos {@link #timeOf}; refeito quando chegam outros. */
    private Component time = FlowsText.NONE_YET;
    private @Nullable TabletFlowsPayload timeOf;
    /** A largura do seletor Por tipo | Por rede, medida ao pôr os botões. */
    private int modeWidth;

    FlowsTab(TabletLayout layout, TabletMenu menu, BiConsumer<Optional<ResourceType>, Optional<UUID>> openList) {
        this.layout = layout;
        this.menu = menu;
        this.openList = openList;
    }

    /** O seletor Por tipo | Por rede, no canto direito. */
    void addButtons(TabletScreen.Buttons buttons, Font font) {
        List<Component> labels = FlowsText.modeLabels();
        modeWidth = 0;
        for (Component label : labels) {
            modeWidth += font.width(label) + 12;
        }
        int right = layout.left + layout.x1();
        int y = layout.top + MODE_Y;
        for (int i = labels.size() - 1; i >= 0; i--) {
            Component label = labels.get(i);
            boolean network = i == 1;
            int w = font.width(label) + 10;
            right -= w;
            buttons.add(TabletScreen.Tab.FLOWS, new FlatButton(right, y, w, MODE_H, label,
                    (g, b, hovered) -> TabletButtons.paintSegment(g, b, hovered, label, byNetwork == network),
                    () -> setMode(network)));
            right -= 2;
        }
    }

    /** A aba acabou de abrir: os Fluxos que o menu já tinha são de antes e esperam os novos. */
    void shown() {
        stale = menu.flows();
    }

    private void setMode(boolean network) {
        if (byNetwork != network) {
            byNetwork = network;
            scroll = 0;
        }
    }

    private @Nullable TabletFlowsPayload current() {
        TabletFlowsPayload flows = menu.flows();
        return flows == stale ? null : flows;
    }

    // ------------------------------------------------------------------ linhas

    /** As redes dos Fluxos com o nome e a cor do cabeçalho; uma rede que o cabeçalho não tem fica de fora. */
    private static List<Net> nets(TabletFlowsPayload flows, TabletSnapshot snapshot) {
        List<Net> nets = new ArrayList<>(flows.networks().size());
        for (NetworkFlows n : flows.networks()) {
            Optional<NetworkView> view = snapshot.network(n.id());
            view.ifPresent(v -> nets.add(new Net(n.id(), v.name(), 0xFF000000 | v.color(), n.paused(), n.nanos(),
                    n.types())));
        }
        return nets;
    }

    /** Refaz as linhas e os textos se os Fluxos, as redes do cabeçalho, a vista, os tipos abertos ou o tema mudaram. */
    private void refreshRows(@Nullable TabletFlowsPayload flows) {
        TabletSnapshot snapshot = menu.snapshot();
        if (flows == rowsFlows && snapshot == rowsSnapshot && byNetwork == rowsByNetwork && openVersion == rowsOpen
                && GuiThemes.version() == rowsTheme) {
            return;
        }
        rowsFlows = flows;
        rowsSnapshot = snapshot;
        rowsByNetwork = byNetwork;
        rowsOpen = openVersion;
        rowsTheme = GuiThemes.version();
        rows.clear();
        if (flows != null) {
            buildRows(flows, snapshot);
        }
        empty = rows.isEmpty() ? FlowsText.empty(snapshot.networks().isEmpty(), flows == null) : Component.empty();
    }

    private void buildRows(TabletFlowsPayload flows, TabletSnapshot snapshot) {
        List<Net> nets = nets(flows, snapshot);
        if (!byNetwork) {
            for (TypeLine line : FlowLines.byType(nets, LoadedTypes.LIST, flows.withoutNetwork())) {
                rows.add(FlowRow.of(Kind.TYPE, line.type(), null, line.totals(), line.withoutNetwork(),
                        !line.networks().isEmpty()));
                if (OPEN.contains(line.type())) {
                    for (Part part : line.networks()) {
                        rows.add(FlowRow.of(Kind.TYPE_NET, part.type(), part.net(), part.totals(), 0, false));
                    }
                }
            }
            for (Net net : FlowLines.paused(nets)) {
                rows.add(FlowRow.of(Kind.PAUSED, null, net, Totals.NONE, 0, false));
            }
            return;
        }
        for (NetLine line : FlowLines.byNetwork(nets, LoadedTypes.LIST)) {
            rows.add(FlowRow.of(Kind.NET, null, line.net(), line.totals(), 0, false));
            if (!line.net().paused()) {
                for (Part part : line.types()) {
                    rows.add(FlowRow.of(Kind.NET_TYPE, part.type(), part.net(), part.totals(), 0, false));
                }
            }
        }
    }

    // ------------------------------------------------------------------ geometria

    private int visibleRows() {
        return Math.max(0, (layout.h - 8 - LIST_Y) / ROW_H);
    }

    /** A linha sob o mouse no último quadro (índice em {@link #rows}), ou -1. */
    private int rowAt(double mouseX, double mouseY) {
        int x = layout.left + X0;
        int y = layout.top + LIST_Y;
        if (mouseX < x || mouseX >= layout.left + layout.x1() || mouseY < y) {
            return -1;
        }
        int i = (int) ((mouseY - y) / ROW_H);
        int index = i + scroll;
        return i < visibleRows() && index < rows.size() ? index : -1;
    }

    private boolean overChevron(FlowRow row, double mouseX) {
        return row.expandable() && mouseX < layout.left + X0 + CHEVRON_W;
    }

    // ------------------------------------------------------------------ entrada

    boolean mouseClicked(double mouseX, double mouseY) {
        int index = rowAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        FlowRow row = rows.get(index);
        if (overChevron(row, mouseX)) {
            if (!OPEN.remove(row.type())) {
                OPEN.add(row.type());
            }
            openVersion++;
            return true;
        }
        openList.accept(Optional.ofNullable(row.type()), Optional.ofNullable(row.net()).map(Net::id));
        return true;
    }

    void mouseScrolled(int step) {
        scroll = Mth.clamp(scroll + step, 0, Math.max(0, rows.size() - visibleRows()));
    }

    /**
     * A dica da linha sob o mouse, com o texto abreviado inteiro na frente (como na Lista), ou {@code null}.
     */
    @Nullable Component tooltip(int mouseX, int mouseY) {
        int index = rowAt(mouseX, mouseY);
        Component area = index < 0 ? null : rowTip(rows.get(index), mouseX);
        Component clipped = GuiText.clipAt(mouseX, mouseY);
        if (clipped == null || (area != null && area.getString().contains(clipped.getString()))) {
            return area;
        }
        return area == null ? clipped : clipped.copy().append("\n").append(area);
    }

    private Component rowTip(FlowRow row, double mouseX) {
        return switch (row.kind()) {
            case TYPE -> overChevron(row, mouseX) ? tr("flows.tip.open")
                    : FlowsText.typeTip(row.type(), row.totals(), row.withoutNetwork(), row.expandable());
            case TYPE_NET, NET_TYPE -> FlowsText.partTip(row.net(), row.type(), row.totals());
            case PAUSED -> FlowsText.pausedTip(row.net());
            case NET -> FlowsText.netTip(row.net(), row.totals());
        };
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        TabletFlowsPayload flows = current();
        int x = layout.left + X0;
        int y = layout.top;
        int x1 = layout.left + layout.x1();
        renderTime(g, font, flows);
        GuiText.draw(g, font, caption, x, y + MODE_Y + 3, x1 - x - modeWidth - 6, GuiPaint.MUTED);
        g.fill(x, y + LIST_Y - 3, x1, y + LIST_Y - 2, GuiPaint.LINE);
        refreshRows(flows);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visibleRows()));
        if (rows.isEmpty()) {
            GuiText.wrapCentered(g, font, empty, layout.left + layout.w / 2, y + LIST_Y + 12, x1 - x, 2,
                    GuiPaint.MUTED);
            return;
        }
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < visibleRows() && i + scroll < rows.size(); i++) {
            int index = i + scroll;
            renderRow(g, font, rows.get(index), y + LIST_Y + i * ROW_H, index == hovered, i > 0);
        }
        if (scroll + visibleRows() < rows.size()) {
            GuiPaint.text(g, font, MORE_BELOW, x1 - 8, y + layout.h - 12, GuiPaint.MUTED);
        }
    }

    /** O tempo do mod por tick com a barra do orçamento. */
    private void renderTime(GuiGraphics g, Font font, @Nullable TabletFlowsPayload flows) {
        int x = layout.left + X0;
        int y = layout.top + BODY_Y;
        int x1 = layout.left + layout.x1();
        if (flows != timeOf) {
            timeOf = flows;
            time = flows == null ? FlowsText.NONE_YET : FlowsText.time(flows.modNanos(), flows.budgetNanos());
        }
        int timeW = Math.min(font.width(time), (x1 - x) / 2);
        GuiText.draw(g, font, timeLabel, x, y, x1 - x - timeW - 6, GuiPaint.FG);
        GuiText.draw(g, font, time, x1 - timeW, y, timeW, GuiPaint.MUTED);
        float used = flows == null || flows.budgetNanos() <= 0 ? 0
                : Math.min(1f, flows.modNanos() / (float) flows.budgetNanos());
        int barColor = used < 0.6f ? GuiPaint.OK : used < 0.9f ? GuiPaint.WARN : GuiPaint.DANGER;
        g.fill(x, y + 11, x1, y + 15, GuiPaint.INSET);
        g.fill(x, y + 11, x + Math.max(1, Math.round((x1 - x) * used)), y + 15, barColor);
    }

    private void renderRow(GuiGraphics g, Font font, FlowRow row, int ry, boolean hovered, boolean notFirst) {
        int x = layout.left + X0;
        int x1 = layout.left + layout.x1();
        if (row.kind() == Kind.NET && notFirst) {
            g.fill(x, ry, x1, ry + 1, GuiPaint.LINE);
        }
        if (hovered) {
            g.fill(x, ry + 1, x1, ry + ROW_H - 1, GuiPaint.ROW_HOVER);
        }
        int ty = ry + 3;
        switch (row.kind()) {
            case TYPE -> {
                if (row.expandable()) {
                    int color = hovered ? GuiPaint.FG : GuiPaint.MUTED;
                    if (OPEN.contains(row.type())) {
                        GuiPaint.arrowDown(g, x + 1, ty + 2, color);
                    } else {
                        GuiPaint.arrowRight(g, x + 2, ty + 1, color);
                    }
                }
                ResourceStyle.drawIcon(g, row.type(), x + 10, ty - 1);
                columns(g, font, row, x + 22, ty, GuiPaint.FG);
            }
            case TYPE_NET -> {
                GuiPaint.eye(g, x + 12, ty + 1, row.net().color());
                columns(g, font, row, x + 22, ty, GuiPaint.FG);
            }
            case PAUSED -> {
                GuiPaint.eye(g, x + 12, ty + 1, GuiPaint.mix(row.net().color(), GuiPaint.INSET, 0.6f));
                columns(g, font, row, x + 22, ty, GuiPaint.DISABLED);
            }
            case NET -> {
                boolean paused = row.net().paused();
                GuiPaint.eye(g, x + 2, ty + 1, paused ? GuiPaint.mix(row.net().color(), GuiPaint.INSET, 0.6f)
                        : row.net().color());
                Component right = row.right();
                int rightW = Math.min(font.width(right), (x1 - x) / 2);
                GuiText.draw(g, font, right, x1 - 2 - rightW, ty, rightW, GuiPaint.MUTED);
                GuiText.draw(g, font, row.name(), x + 12, ty, x1 - x - 18 - rightW,
                        paused ? GuiPaint.DISABLED : GuiPaint.FG);
            }
            case NET_TYPE -> {
                ResourceStyle.drawIcon(g, row.type(), x + 12, ty - 1);
                columns(g, font, row, x + 24, ty, GuiPaint.FG);
            }
        }
    }

    /**
     * Nome até o meio, "entra → sai" no meio (na cor do tipo) e o lado direito encostado na borda. Sem espaço
     * para os dois, o "entra → sai" fica inteiro e o lado direito é que abrevia (até 40% da largura).
     */
    private void columns(GuiGraphics g, Font font, FlowRow row, int nameX, int ty, int nameColor) {
        Component name = row.name();
        Component mid = row.mid();
        Component right = row.right();
        int x = layout.left + X0;
        int x1 = layout.left + layout.x1();
        int midX = x + MID_X;
        GuiText.draw(g, font, name, nameX, ty, midX - 4 - nameX, nameColor);
        int room = x1 - 2 - midX;
        int rightW = Math.min(font.width(right), Math.max(room - font.width(mid) - 6, room * 40 / 100));
        if (rightW > 0) {
            GuiText.draw(g, font, right, x1 - 2 - rightW, ty, rightW, GuiPaint.MUTED);
        }
        int midColor = row.totals().measuring() ? GuiPaint.MUTED : row.type() == null ? GuiPaint.DISABLED
                : ResourceStyle.color(row.type());
        GuiText.draw(g, font, mid, midX, ty, x1 - 2 - rightW - 6 - midX, midColor);
    }

    // ------------------------------------------------------------------ ganchos do e2e e das capturas

    /** Por rede está à vista. */
    public boolean byNetwork() {
        return byNetwork;
    }

    /** O tipo está aberto Por tipo. */
    public boolean isOpen(ResourceType type) {
        return OPEN.contains(type);
    }

    /** Captura: abre ou fecha o tipo Por tipo. */
    public void previewOpen(ResourceType type, boolean open) {
        if (open) {
            OPEN.add(type);
        } else {
            OPEN.remove(type);
        }
        openVersion++;
    }

    /** Captura: Por tipo ou Por rede. */
    public void previewMode(boolean network) {
        setMode(network);
    }

    /**
     * A linha à vista no último quadro: do tipo ({@code network} nulo), da rede ({@code type} nulo) ou do tipo na
     * rede; ou {@code null}.
     */
    private @Nullable FlowRow shownRow(@Nullable ResourceType type, @Nullable UUID network) {
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows()); i++) {
            FlowRow row = rows.get(i);
            boolean sameNet = network == null ? row.net() == null : row.net() != null && row.net().id().equals(network);
            if (row.type() == type && sameNet) {
                return row;
            }
        }
        return null;
    }

    /** O centro da linha (ver {@link #shownRow}) no último quadro, ou {@code null} se ela não está à vista. */
    public int @Nullable [] rowCenter(@Nullable ResourceType type, @Nullable UUID network) {
        FlowRow row = shownRow(type, network);
        if (row == null) {
            return null;
        }
        int i = rows.indexOf(row) - scroll;
        return new int[] {layout.left + X0 + MID_X, layout.top + LIST_Y + i * ROW_H + ROW_H / 2};
    }

    /** O centro da seta do tipo Por tipo, ou {@code null} se ele não tem redes ou não está à vista. */
    public int @Nullable [] chevronCenter(ResourceType type) {
        FlowRow row = shownRow(type, null);
        if (row == null || !row.expandable()) {
            return null;
        }
        int i = rows.indexOf(row) - scroll;
        return new int[] {layout.left + X0 + 4, layout.top + LIST_Y + i * ROW_H + ROW_H / 2};
    }

    /** A linha está à vista com a janela cheia e algo movido nela. */
    public boolean measured(@Nullable ResourceType type, @Nullable UUID network) {
        FlowRow row = shownRow(type, network);
        if (row == null || !row.totals().filled()) {
            return false;
        }
        Totals t = row.totals();
        return !FlowLines.zero(t.produced() + t.consumed() + t.stored() + t.released());
    }

    /** O texto da linha como o jogador lê (nome, "entra → sai" e o lado direito), ou {@code null}. */
    public @Nullable String rowText(@Nullable ResourceType type, @Nullable UUID network) {
        FlowRow row = shownRow(type, network);
        if (row == null) {
            return null;
        }
        if (row.type() == null) {
            return row.net().name();
        }
        boolean unit = row.kind() != Kind.TYPE_NET;
        return FlowsText.pair(row.type(), row.totals(), unit).getString() + " | "
                + FlowsText.right(row.type(), row.totals(), row.withoutNetwork(), unit).getString();
    }
}
