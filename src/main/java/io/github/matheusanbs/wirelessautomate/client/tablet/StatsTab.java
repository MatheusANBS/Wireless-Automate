package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.appendPart;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.decimal;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.ms;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.rate;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.statusColor;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.CardGrid;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.RateMath;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Estatísticas do Tablet: o tempo do mod por tick com a barra do orçamento, um cartão por tipo (clicar
 * filtra a Lista pelo tipo), os avisos somados e um cartão por rede, com rolagem.
 */
final class StatsTab {
    private static final int STAT_CARD = 34;
    /** 3 cartões por linha no tamanho mínimo, 5 numa linha a partir de 464 px (com os 5 tipos carregados). */
    private static final int CARD_MIN_W = 86;
    private static final int CARD_GAP = 4;
    /** Nome, vazão e até duas linhas de detalhe (26 + 2 × 10 + 1). */
    private static final int CARD_H = 47;
    /** Onde começam os cartões por tipo das Estatísticas (relativo ao topo). */
    private static final int CARDS_Y = BODY_Y + 20;

    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private int statsScroll;
    /** Retângulos dos cartões no último frame, para o clique e o tooltip. */
    private final Map<ResourceType, int[]> cardRects = new EnumMap<>(ResourceType.class);

    StatsTab(TabletLayout layout, Supplier<TabletSnapshot> snapshot) {
        this.layout = layout;
        this.snapshot = snapshot;
    }

    private TabletSnapshot snapshot() {
        return snapshot.get();
    }

    /** Os cartões voltam no próximo quadro (outra aba, ou a nova grade depois de redimensionar). */
    void clearCards() {
        cardRects.clear();
    }

    // ------------------------------------------------------------------ geometria

    /** Colunas da grade de cartões por tipo na largura atual. */
    private int cardColumns() {
        return CardGrid.columns(LoadedTypes.LIST.size(), layout.x1() - X0, CARD_MIN_W, CARD_GAP);
    }

    /** Fim da grade de cartões (relativo ao topo): a linha dos avisos começa 2 px abaixo. */
    private int cardsEnd() {
        int cols = cardColumns();
        int rows = (LoadedTypes.LIST.size() + cols - 1) / cols;
        return CARDS_Y + rows * (CARD_H + CARD_GAP);
    }

    /** Onde começa a lista de redes das Estatísticas, depois dos cartões e dos avisos (relativo ao topo). */
    private int statsListY() {
        return cardsEnd() + 26;
    }

    /** Cartões de rede que cabem embaixo dos cartões por tipo. */
    private int statsRows() {
        return Math.max(0, (layout.h - 8 - statsListY()) / STAT_CARD);
    }

    /** O cartão por tipo sob o mouse no último frame, ou {@code null}. */
    @Nullable ResourceType cardAt(double mouseX, double mouseY) {
        for (Map.Entry<ResourceType, int[]> e : cardRects.entrySet()) {
            int[] r = e.getValue();
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Centro do cartão do tipo (último frame), ou {@code null} se ele não aparece. */
    int @Nullable [] cardCenter(ResourceType type) {
        int[] r = cardRects.get(type);
        return r == null ? null : new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    // ------------------------------------------------------------------ entrada

    void mouseScrolled(int step) {
        statsScroll = Mth.clamp(statsScroll + step, 0, Math.max(0, snapshot().networks().size() - statsRows()));
    }

    @Nullable Component tooltip(int mouseX, int mouseY) {
        ResourceType card = cardAt(mouseX, mouseY);
        if (card != null) {
            return tr("stats.card.tooltip", ResourceStyle.name(card));
        }
        return null;
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font) {
        TabletSnapshot s = snapshot();
        int x = layout.left;
        int y = layout.top;
        int x1 = layout.x1();
        // tempo do mod por tick, com a barra do orçamento
        Component time = tr("stats.time.value", decimal(s.modNanos() / 1e6), decimal(s.budgetNanos() / 1e6));
        int timeW = Math.min(font.width(time), (x1 - X0) / 2);
        GuiText.draw(g, font, tr("stats.time"), x + X0, y + BODY_Y, x1 - X0 - timeW - 6, GuiPaint.FG);
        GuiText.draw(g, font, time, x + x1 - timeW, y + BODY_Y, timeW, GuiPaint.MUTED);
        float used = s.budgetNanos() <= 0 ? 0 : Math.min(1f, s.modNanos() / (float) s.budgetNanos());
        int barColor = used < 0.6f ? GuiPaint.OK : used < 0.9f ? GuiPaint.WARN : GuiPaint.DANGER;
        g.fill(x + X0, y + BODY_Y + 11, x + x1, y + BODY_Y + 15, GuiPaint.INSET);
        g.fill(x + X0, y + BODY_Y + 11, x + X0 + Math.max(1, Math.round((x1 - X0) * used)), y + BODY_Y + 15, barColor);

        // um cartão por tipo, somando as redes visíveis
        int after = renderTypeCards(g, font, y + CARDS_Y);
        int full = 0;
        int unloaded = 0;
        int paused = 0;
        for (NetworkView n : s.networks()) {
            full += n.full();
            unloaded += n.unloaded();
            paused += n.paused() ? 1 : 0;
        }
        MutableComponent warnings = Component.empty();
        appendWarning(warnings, full, "stats.full", statusColor(NodeStatus.FULL));
        appendWarning(warnings, unloaded, "stats.unloaded", statusColor(NodeStatus.UNLOADED));
        appendWarning(warnings, paused, "stats.paused", statusColor(NodeStatus.PAUSED));
        boolean ok = warnings.getString().isEmpty();
        GuiText.wrap(g, font, ok ? tr("stats.ok") : warnings, x + X0, after + 2, x1 - X0, 2,
                ok ? statusColor(NodeStatus.ACTIVE) : GuiPaint.FG);
        int listY = statsListY();
        g.fill(x + X0, y + listY - 3, x + x1, y + listY - 2, GuiPaint.LINE);
        renderNetworks(g, font, listY);
    }

    /** Nome, vazões e avisos de cada rede, dentro do cartão (a sombra da última linha não toca a borda). */
    private void renderNetworks(GuiGraphics g, Font font, int listY) {
        int x = layout.left;
        int y = layout.top;
        int x1 = layout.x1();
        List<NetworkView> networks = snapshot().networks();
        if (networks.isEmpty()) {
            GuiPaint.textCentered(g, font, tr("networks.empty"), x + layout.w / 2, y + listY + 12, GuiPaint.MUTED);
        }
        int rows = statsRows();
        for (int i = 0; i < rows && i + statsScroll < networks.size(); i++) {
            NetworkView n = networks.get(i + statsScroll);
            int cy = y + listY + i * STAT_CARD;
            GuiPaint.box(g, x + X0, cy, x1 - X0, STAT_CARD - 3, GuiPaint.BUTTON, GuiPaint.LINE);
            GuiPaint.eye(g, x + X0 + 5, cy + 4, 0xFF000000 | n.color());
            Component right = tr("stats.network", plural("meta.nodes", n.nodes()), ms(n.averageNanos()), n.opsPerSecond());
            int rightW = Math.min(font.width(right), (x1 - X0) / 2);
            GuiText.draw(g, font, right, x + x1 - 5 - rightW, cy + 3, rightW, GuiPaint.MUTED);
            GuiText.draw(g, font, Component.literal(n.name()), x + X0 + 14, cy + 3, x1 - X0 - 24 - rightW,
                    GuiPaint.FG);
            MutableComponent rates = Component.empty();
            for (ResourceType t : LoadedTypes.LIST) {
                if (!rates.getSiblings().isEmpty()) {
                    rates.append(" · ");
                }
                rates.append(rate(t, n.type(t).rate()));
            }
            GuiText.draw(g, font, rates, x + X0 + 5, cy + 12, x1 - X0 - 10, GuiPaint.MUTED);
            GuiText.draw(g, font, networkLine(n), x + X0 + 5, cy + 21, x1 - X0 - 10, GuiPaint.FG);
        }
        if (statsScroll + rows < networks.size()) {
            GuiPaint.text(g, font, Component.literal("▼"), x + x1 - 8, y + layout.h - 12, GuiPaint.MUTED);
        }
    }

    /** Os avisos de uma rede: pausada, cheios, dormindo, descarregados; ou "tudo bem". */
    private static Component networkLine(NetworkView n) {
        MutableComponent line = Component.empty();
        if (n.paused()) {
            appendPart(line, tr("status.paused").copy().withColor(statusColor(NodeStatus.PAUSED)));
        }
        if (n.full() > 0) {
            appendPart(line, plural("stats.full", n.full()).copy().withColor(statusColor(NodeStatus.FULL)));
        }
        if (n.sleeping() > 0) {
            appendPart(line, tr("stats.sleeping", n.sleeping()).copy().withColor(GuiPaint.MUTED));
        }
        if (n.unloaded() > 0) {
            appendPart(line, plural("stats.unloaded", n.unloaded()).copy()
                    .withColor(statusColor(NodeStatus.UNLOADED)));
        }
        if (line.getSiblings().isEmpty()) {
            line.append(tr("stats.network.ok").copy().withColor(GuiPaint.DISABLED));
        }
        return line;
    }

    /**
     * Um cartão por tipo carregado: ícone e nome, vazão na cor do tipo e origens, destinos e
     * dormindo. A grade quebra conforme a largura ({@link CardGrid}). Devolve onde a grade termina.
     */
    private int renderTypeCards(GuiGraphics g, Font font, int top) {
        cardRects.clear();
        List<ResourceType> shown = LoadedTypes.LIST;
        int avail = layout.x1() - X0;
        int cols = cardColumns();
        int cardW = (avail - CARD_GAP * (cols - 1)) / cols;
        for (int i = 0; i < shown.size(); i++) {
            ResourceType t = shown.get(i);
            long rate = 0;
            int sources = 0;
            int destinations = 0;
            int sleeping = 0;
            for (NetworkView n : snapshot().networks()) {
                TabletSnapshot.TypeStats s = n.type(t);
                rate = RateMath.add(rate, s.rate());
                sources += s.sources();
                destinations += s.destinations();
                sleeping += s.sleeping();
            }
            int cx = layout.left + X0 + (i % cols) * (cardW + CARD_GAP);
            int cy = top + (i / cols) * (CARD_H + CARD_GAP);
            GuiPaint.box(g, cx, cy, cardW, CARD_H, GuiPaint.INSET, GuiPaint.LINE);
            g.fill(cx, cy, cx + cardW, cy + 1, ResourceStyle.color(t));
            ResourceStyle.drawIcon(g, t, cx + 4, cy + 3);
            GuiText.draw(g, font, ResourceStyle.name(t), cx + 16, cy + 4, cardW - 20, GuiPaint.FG);
            GuiText.draw(g, font, ResourceStyle.rate(t, rate), cx + 4, cy + 15, cardW - 8, ResourceStyle.color(t));
            Component detail = sleeping > 0 ? tr("stats.card.detail.sleeping", sources, destinations, sleeping)
                    : tr("stats.card.detail", sources, destinations);
            GuiText.wrap(g, font, detail, cx + 4, cy + 26, cardW - 8, 2, GuiPaint.MUTED);
            cardRects.put(t, new int[] {cx, cy, cardW, CARD_H});
        }
        int rows = (shown.size() + cols - 1) / cols;
        return top + rows * (CARD_H + CARD_GAP);
    }

    private static void appendWarning(MutableComponent line, int count, String key, int color) {
        if (count > 0) {
            appendPart(line, plural(key, count).copy().withColor(color));
        }
    }
}
