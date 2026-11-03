package io.github.matheusanbs.wirelessautomate.client.tablet.machine;

import static io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachineText.tr;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletSearch;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletSearch.Candidate;
import io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachineListRows.Entry;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Facet;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Tone;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A coluna esquerda da aba Máquina (mockup {@code lista_maquinas}): o campo "Buscar máquina" e, embaixo, uma linha de 24 px
 * por máquina com o ícone do bloco, o nome e o estado (cor do problema) ou a vazão; a escolhida leva a faixa {@code FG}.
 * Rola pela roda e por uma barra fina, sempre com a linha escolhida à mão ({@link #reveal}). As linhas, os textos e as
 * larguras são montados quando chega a página ou muda o tema ({@link #rebuild}); o desenho só pinta as que estão à vista.
 * A busca vai ao servidor um pouco depois da última letra, pela consulta de sempre (só o texto, sem etiquetas).
 */
final class MachineList {
    /** Onde a divisória da coluna fica, relativo ao painel. */
    static final int RULE = TabletLayout.COLUMN_RULE;
    static final int ROW_TOP = 52;
    static final int ROW_H = 24;
    private static final int TEXT_X = 28;
    private static final int TEXT_W = RULE - TEXT_X - 8;
    private static final int DEBOUNCE = 6;

    /** Uma linha pronta: o ícone, o nome inteiro (o cabeçalho usa) e os dois textos já cortados na largura. */
    record Line(Entry entry, ItemStack icon, String name, GuiText.Fitted nameText, GuiText.Fitted stateText) {
    }

    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private final Consumer<Query> onQuery;
    private final Map<ResourceLocation, String> blockNames = new HashMap<>();
    private final Map<ResourceLocation, ItemStack> blockIcons = new HashMap<>();
    private final Component empty = tr("list.empty");
    private final Component noMatch = tr("list.no_match");
    private Line[] lines = new Line[0];
    private int scroll;
    private int chosen = -1;
    private int cursor = -1;
    private @Nullable EditBox box;
    private String text;
    private int ticks = -1;
    private int inkTheme = -1;

    MachineList(TabletLayout layout, Supplier<TabletSnapshot> snapshot, Consumer<Query> onQuery) {
        this.layout = layout;
        this.snapshot = snapshot;
        this.onQuery = onQuery;
        this.text = snapshot.get().query().text();
    }

    // ------------------------------------------------------------------ montagem

    /** O campo de busca, refeito a cada {@code init} (a tela o põe entre os widgets); sem borda, sobre o campo da lista. */
    EditBox createBox(Font font) {
        EditBox created = new EditBox(font, layout.left + 13, layout.top + 34, MachineList.RULE - 25, 9,
                tr("search"));
        created.setBordered(false);
        created.setTextShadow(false);
        created.setMaxLength(TabletSnapshot.MAX_TEXT);
        created.setValue(text);
        created.setResponder(value -> {
            if (!value.equals(text)) {
                text = value;
                ticks = DEBOUNCE;
                cursor = -1;
            }
        });
        box = created;
        inkTheme = -1;
        return created;
    }

    @Nullable EditBox box() {
        return box;
    }

    /** A tinta e a dica do campo seguem o tema; refeitas só quando ele muda. */
    void ink(int themeVersion) {
        if (box != null && inkTheme != themeVersion) {
            inkTheme = themeVersion;
            GuiPaint.ink(box, tr("search"));
        }
    }

    /** O nome do bloco, no idioma do jogador, guardado. */
    String blockName(ResourceLocation id) {
        return blockNames.computeIfAbsent(id, key -> RouterBlock.machineName(BuiltInRegistries.BLOCK.get(key)).getString());
    }

    /** O ícone do bloco, criado uma vez (vazio para o bloco sem item). */
    ItemStack blockIcon(ResourceLocation id) {
        return blockIcons.computeIfAbsent(id, key -> new ItemStack(BuiltInRegistries.BLOCK.get(key).asItem()));
    }

    /** Monta as linhas: nomes, estados e larguras. */
    void rebuild(Font font, List<Entry> entries) {
        Line[] built = new Line[entries.size()];
        for (int i = 0; i < built.length; i++) {
            Entry entry = entries.get(i);
            String block = blockName(entry.block());
            String name;
            Component second;
            if (entry.machine()) {
                name = entry.name(block);
                second = stateText(entry.state());
            } else {
                name = tr("more", entry.hidden()).getString();
                second = Component.literal(block);
            }
            built[i] = new Line(entry, blockIcon(entry.block()), name,
                    GuiText.fit(font, Component.literal(name), TEXT_W), GuiText.fit(font, second, TEXT_W));
        }
        lines = built;
        scroll = Math.min(scroll, maxScroll());
        chosen = Math.min(chosen, lines.length - 1);
        cursor = Math.min(cursor, lines.length - 1);
    }

    private static Component stateText(MachineState.State state) {
        return state.rateType() != null ? ResourceStyle.rate(state.rateType(), state.rate())
                : tr("state." + state.key());
    }

    int size() {
        return lines.length;
    }

    @Nullable Line line(int index) {
        return index >= 0 && index < lines.length ? lines[index] : null;
    }

    // ------------------------------------------------------------------ escolha e rolagem

    /** A linha escolhida ({@code -1}: nenhuma). */
    void choose(int index) {
        chosen = index;
    }

    int chosen() {
        return chosen;
    }

    private int viewH() {
        return layout.h - 2 - ROW_TOP;
    }

    private int maxScroll() {
        return Math.max(0, lines.length * ROW_H - viewH());
    }

    /** Põe a linha à vista, rolando só o que falta. */
    void reveal(int index) {
        if (index < 0 || index >= lines.length) {
            return;
        }
        int top = index * ROW_H;
        if (top < scroll) {
            scroll = top;
        } else if (top + ROW_H > scroll + viewH()) {
            scroll = top + ROW_H - viewH();
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    /** As setas: anda o cursor a partir dele ou da escolhida. */
    void moveCursor(int direction) {
        if (lines.length == 0) {
            return;
        }
        int from = cursor >= 0 ? cursor : chosen;
        cursor = from < 0 ? (direction > 0 ? 0 : lines.length - 1)
                : Math.max(0, Math.min(lines.length - 1, from + direction));
        reveal(cursor);
    }

    @Nullable Entry cursorEntry() {
        Line line = line(cursor);
        return line == null ? null : line.entry();
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < layout.left || mx >= layout.left + RULE || my < layout.top + 24) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * ROW_H));
        return true;
    }

    /** A linha sob o mouse ({@code -1}: nenhuma). */
    int indexAt(double mx, double my) {
        if (mx < layout.left + 2 || mx >= layout.left + RULE || my < layout.top + ROW_TOP
                || my >= layout.top + layout.h - 2) {
            return -1;
        }
        int index = (int) (my - layout.top - ROW_TOP + scroll) / ROW_H;
        return index < lines.length ? index : -1;
    }

    @Nullable Entry entryAt(double mx, double my) {
        Line line = line(indexAt(mx, my));
        return line == null ? null : line.entry();
    }

    /** Centro da linha na tela, rolando até ela se preciso (gancho do e2e). */
    int @Nullable [] centerOf(int index) {
        if (index < 0 || index >= lines.length) {
            return null;
        }
        reveal(index);
        return new int[] {layout.left + RULE / 2, layout.top + ROW_TOP + index * ROW_H - scroll + ROW_H / 2 - 1};
    }

    // ------------------------------------------------------------------ busca

    /** A cada tique da tela: a consulta sai um pouco depois da última letra. */
    void tick() {
        if (ticks > 0 && --ticks == 0) {
            sendQuery();
        }
    }

    /** O texto da busca saiu (e as etiquetas e filtros que a consulta de antes trazia, sem lugar nesta aba, também). */
    void sendQuery() {
        ticks = -1;
        scroll = 0;
        onQuery.accept(Query.of(text.strip()).withTextMachines(textMachines()));
    }

    /** As máquinas cujo nome, no idioma do jogador, contém o texto: o servidor só conhece os ids. */
    private List<ResourceLocation> textMachines() {
        List<Candidate<ResourceLocation>> candidates = new ArrayList<>();
        for (Facet<ResourceLocation> facet : snapshot.get().machineFacets()) {
            candidates.add(new Candidate<>(facet.id(), blockName(facet.id()), facet.count()));
        }
        List<ResourceLocation> ids = TabletSearch.matchingIds(text, candidates);
        return ids.size() > TabletSnapshot.MAX_TEXT_MACHINES ? ids.subList(0, TabletSnapshot.MAX_TEXT_MACHINES) : ids;
    }

    /** Há texto na busca. */
    boolean searching() {
        return !text.isBlank();
    }

    /** Esvazia a busca e consulta de novo. */
    void clearSearch() {
        text = "";
        if (box != null) {
            box.setValue("");
        }
        sendQuery();
    }

    // ------------------------------------------------------------------ dica

    /** A dica do "… mais N" (o nome cortado das outras linhas sai pelo {@link GuiText#clipAt}). */
    @Nullable Component tooltip(int mx, int my) {
        Line line = line(indexAt(mx, my));
        return line != null && !line.entry().machine()
                ? tr("more.tooltip", blockName(line.entry().block())) : null;
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, int mx, int my) {
        int left = layout.left;
        int top = layout.top;
        g.fill(left + RULE, top + 24, left + RULE + 1, top + layout.h - 2, GuiPaint.INSET);
        PanelPaint.field(g, left + 8, top + 30, RULE - 15, 16, box != null && box.isFocused());
        if (lines.length == 0) {
            GuiText.wrapCentered(g, font, searching() ? noMatch : empty, left + RULE / 2, top + ROW_TOP + 8,
                    RULE - 20, 3, GuiPaint.MUTED);
            return;
        }
        int hovered = indexAt(mx, my);
        g.enableScissor(left + 2, top + ROW_TOP, left + RULE, top + layout.h - 2);
        for (int i = 0; i < lines.length; i++) {
            int y = top + ROW_TOP + i * ROW_H - scroll;
            if (y + ROW_H <= top + ROW_TOP) {
                continue;
            }
            if (y >= top + layout.h - 2) {
                break;
            }
            if (i == chosen) {
                PanelPaint.chosenRow(g, left + 2, y, RULE - 2, ROW_H - 2);
            } else if (i == hovered || i == cursor) {
                g.fill(left + 2, y, left + RULE, y + ROW_H - 2, GuiPaint.ROW_HOVER);
            }
            Line line = lines[i];
            g.renderItem(line.icon(), left + 8, y + 3);
            GuiText.draw(g, font, line.nameText(), left + TEXT_X, y + 3, GuiPaint.FG);
            GuiText.draw(g, font, line.stateText(), left + TEXT_X, y + 12, color(line.entry().state().tone()));
        }
        g.disableScissor();
        renderScrollbar(g, left, top);
    }

    private void renderScrollbar(GuiGraphics g, int left, int top) {
        int max = maxScroll();
        if (max == 0) {
            return;
        }
        int view = viewH();
        int thumb = Math.max(8, view * view / (lines.length * ROW_H));
        int y = top + ROW_TOP + (view - thumb) * scroll / max;
        g.fill(left + RULE - 3, y, left + RULE - 1, y + thumb, GuiPaint.LINE);
    }

    private static int color(Tone tone) {
        return switch (tone) {
            case BAD -> GuiPaint.DANGER;
            case WARN -> GuiPaint.WARN;
            case OK -> GuiPaint.OK;
            case MUTED -> GuiPaint.MUTED;
        };
    }
}
