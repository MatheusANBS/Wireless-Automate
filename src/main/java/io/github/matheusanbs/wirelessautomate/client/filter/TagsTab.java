package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.BTN_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.CAND_ROW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.CAND_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.FIELD_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.INSPECT_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.IW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.PREVIEW_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.TAG_SEARCH_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.thumbHeight;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintPrimary;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.wrapped;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.RateFormat;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen.Tab;
import io.github.matheusanbs.wirelessautomate.client.filter.TagCandidates.Candidate;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ChemicalEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Tags: o inspetor (um item ou fluido posto no slot mostra as tags dele para marcar), a busca em todas
 * as tags do jogo ({@code @texto} busca mods), as linhas com caixa de marcar e a prévia dos membros da linha
 * sob o mouse. Nos químicos, que não têm tags, a aba vira "Adicionar" (id do químico ou mod).
 */
final class TagsTab {
    private final FilterScreen screen;
    private final FilterFrame frame;
    private final FilterMembers members;
    private final FilterActions actions;
    private final TagCandidates source;

    private ItemStack inspected = ItemStack.EMPTY;
    private FluidStack inspectedFluid = FluidStack.EMPTY;
    private final Set<FilterEntry> checked = new LinkedHashSet<>();
    private List<Candidate> candidates = List.of();
    private String candidatesKey = "";
    private int candScroll;
    private @Nullable Candidate hoveredCandidate;
    private EditBox tagSearchBox;
    private String tagSearchDraft = "";
    private FlatButton addTagsButton;

    TagsTab(FilterScreen screen, FilterMembers members, FilterActions actions) {
        this.screen = screen;
        this.frame = screen.frame();
        this.members = members;
        this.actions = actions;
        this.source = new TagCandidates(screen, members);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    void addWidgets() {
        tagSearchBox = screen.box(tr("tags.search"), 128, tagSearchDraft, value -> {
            tagSearchDraft = value;
            candScroll = 0;
        });
        addTagsButton = screen.add(new FlatButton(0, 0, IW, BTN_H, tr("tags.add.action"),
                (g, b, hovered) -> paintPrimary(g, b, hovered, addLabel()), this::addChecked)
                .tooltip(() -> uncheckedCount() == 0 ? tr("tags.add.empty") : null));
    }

    EditBox searchBox() {
        return tagSearchBox;
    }

    void refresh(int px, int y, int bottom) {
        boolean tags = screen.tab() == Tab.TAGS;
        tagSearchBox.visible = addTagsButton.visible = tags;
        tagSearchBox.setPosition(px + 4, y + TAG_SEARCH_Y + 2);
        tagSearchBox.setWidth(IW - 8);
        addTagsButton.setPosition(px, bottom - BTN_H);
        addTagsButton.active = uncheckedCount() > 0;
    }

    // inspetor

    /** Põe o item no inspetor (cópia de 1); num filtro de fluidos, inspeciona o fluido que ele contém. */
    void inspect(ItemStack stack) {
        if (screen.isFluid()) {
            FluidUtil.getFluidContained(stack).filter(f -> !f.isEmpty()).ifPresent(this::inspectFluid);
            return;
        }
        if (!screen.isItem() || stack.isEmpty()) {
            return;
        }
        inspected = stack.copyWithCount(1);
        afterInspect();
    }

    void inspectFluid(FluidStack stack) {
        if (!screen.isFluid() || stack.isEmpty()) {
            return;
        }
        inspectedFluid = stack.copyWithAmount(1000);
        afterInspect();
    }

    private void afterInspect() {
        checked.clear();
        tagSearchBox.setValue("");
        candScroll = 0;
        candidatesKey = "";
    }

    private void clearInspect() {
        inspected = ItemStack.EMPTY;
        inspectedFluid = FluidStack.EMPTY;
        checked.clear();
        candidatesKey = "";
    }

    private boolean hasInspected() {
        return !inspected.isEmpty() || !inspectedFluid.isEmpty();
    }

    // linhas marcadas

    private boolean inFilter(FilterEntry entry) {
        return screen.filter().indexOf(entry) >= 0;
    }

    private int uncheckedCount() {
        int n = 0;
        for (FilterEntry entry : checked) {
            if (!inFilter(entry)) {
                n++;
            }
        }
        return n;
    }

    private Component addLabel() {
        int n = uncheckedCount();
        return n == 0 ? tr("tags.add.none") : tr(n == 1 ? "tags.add.one" : "tags.add", n);
    }

    private void toggleCandidate(Candidate candidate) {
        if (inFilter(candidate.entry())) {
            screen.selection().pendingReveal(candidate.entry());
            screen.selection().revealPending();
            return;
        }
        if (!checked.remove(candidate.entry())) {
            checked.add(candidate.entry());
        }
    }

    private void addChecked() {
        List<FilterEntry> entries = new ArrayList<>();
        for (FilterEntry entry : checked) {
            if (!inFilter(entry)) {
                entries.add(entry);
            }
        }
        checked.clear();
        actions.sendEntries(-1, entries);
    }

    /** Enter na busca: marca e adiciona o que foi digitado ({@code #tag}, {@code @mod} ou um químico). */
    void addTyped() {
        FilterEntry typed = source.typedEntry(tagSearchDraft);
        if (typed == null) {
            if (candidates.size() == 1) {
                typed = candidates.getFirst().entry();
            } else {
                return;
            }
        }
        if (inFilter(typed)) {
            screen.selection().pendingReveal(typed);
            screen.selection().revealPending();
            return;
        }
        screen.selection().pendingReveal(typed);
        actions.sendEntries(-1, List.of(typed));
        tagSearchBox.setValue("");
    }

    /** Refaz as linhas da aba Tags se a busca, o inspetor ou o tipo mudaram. */
    void rebuildCandidates() {
        String search = tagSearchDraft.strip().toLowerCase(Locale.ROOT);
        String key = search + "|" + System.identityHashCode(inspected) + "|" + System.identityHashCode(inspectedFluid);
        if (key.equals(candidatesKey)) {
            return;
        }
        candidatesKey = key;
        candidates = source.build(search, inspected, inspectedFluid);
        candScroll = Math.max(0, Math.min(candScroll, maxCandScroll()));
    }

    // geometria

    private int candRows() {
        int bottom = frame.innerBottom() - BTN_H - 3 - PREVIEW_H;
        return Math.max(1, (bottom - CAND_Y) / CAND_ROW);
    }

    private int maxCandScroll() {
        return Math.max(0, candidates.size() - candRows());
    }

    /** A linha da aba Tags sob o mouse, ou {@code null}. */
    private @Nullable Candidate candidateAt(double mouseX, double mouseY) {
        if (screen.tab() != Tab.TAGS) {
            return null;
        }
        double rx = mouseX - (frame.left() + frame.ix() - 2);
        double ry = mouseY - (frame.top() + CAND_Y);
        if (rx < 0 || rx >= IW + 4 || ry < 0 || ry >= candRows() * CAND_ROW) {
            return null;
        }
        int index = candScroll + (int) ry / CAND_ROW;
        return index < candidates.size() ? candidates.get(index) : null;
    }

    private boolean overCandidates(double mouseX, double mouseY) {
        int ix = frame.left() + frame.ix();
        int top = frame.top();
        return screen.tab() == Tab.TAGS && mouseX >= ix - 2 && mouseX < ix + IW + 2
                && mouseY >= top + CAND_Y && mouseY < top + CAND_Y + candRows() * CAND_ROW;
    }

    private boolean overInspector(double mouseX, double mouseY) {
        int ix = frame.left() + frame.ix();
        int top = frame.top();
        return screen.tab() == Tab.TAGS && !screen.isChemical() && mouseX >= ix - 1 && mouseX < ix + 17
                && mouseY >= top + INSPECT_Y - 1 && mouseY < top + INSPECT_Y + 17;
    }

    private boolean overInspectorClear(double mouseX, double mouseY) {
        return overInspectorRow(mouseX, mouseY) && hasInspected() && mouseX >= frame.left() + frame.ix() + IW - 9;
    }

    private boolean overInspectorRow(double mouseX, double mouseY) {
        int ix = frame.left() + frame.ix();
        int top = frame.top();
        return screen.tab() == Tab.TAGS && mouseY >= top + INSPECT_Y - 1 && mouseY < top + INSPECT_Y + 17
                && mouseX >= ix - 1 && mouseX < ix + IW;
    }

    // eventos

    /** Guarda a linha sob o mouse para o quadro (destaque, prévia e dica). */
    void hover(int mouseX, int mouseY) {
        hoveredCandidate = candidateAt(mouseX, mouseY);
    }

    /** O × do inspetor, o slot do inspetor (o item na mão entra; o botão direito o tira) ou uma linha. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overInspectorClear(mouseX, mouseY)) {
            clearInspect();
            return true;
        }
        if (overInspector(mouseX, mouseY)) {
            ItemStack carried = screen.getMenu().getCarried();
            if (!carried.isEmpty()) {
                inspect(carried);
            } else if (button == 1) {
                clearInspect();
            }
            return true;
        }
        Candidate candidate = candidateAt(mouseX, mouseY);
        if (candidate != null) {
            toggleCandidate(candidate);
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, int step) {
        if (overCandidates(mouseX, mouseY)) {
            candScroll = Math.max(0, Math.min(maxCandScroll(), candScroll + step));
            return true;
        }
        return false;
    }

    /** As dicas da aba: a linha sob o mouse, o inspetor e o ×; devolve se mostrou alguma. */
    boolean tooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (hoveredCandidate != null) {
            screen.setTooltipForNextRenderPass(candidateTooltip(hoveredCandidate));
            return true;
        }
        if (overInspector(mouseX, mouseY)) {
            if (!inspected.isEmpty()) {
                g.renderTooltip(font(), screen.itemTooltip(inspected), inspected.getTooltipImage(), inspected, mouseX, mouseY);
            } else {
                screen.setTooltipForNextRenderPass(font().split(tr(screen.isFluid() ? "inspect.tooltip.fluid" : "inspect.tooltip"), 200));
            }
            return true;
        }
        if (overInspectorClear(mouseX, mouseY)) {
            screen.setTooltipForNextRenderPass(tr("inspect.clear"));
            return true;
        }
        return false;
    }

    private List<FormattedCharSequence> candidateTooltip(Candidate c) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(c.label()).getVisualOrderText());
        if (c.entry() instanceof TagEntry e) {
            Component name = Component.translatableWithFallback(Tags.getTagTranslationKey(screen.isFluid()
                    ? TagKey.create(Registries.FLUID, e.tag()) : TagKey.create(Registries.ITEM, e.tag())), "");
            if (!name.getString().isEmpty()) {
                lines.add(name.copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
        } else if (c.entry() instanceof ModEntry e) {
            ModList.get().getModContainerById(e.modId()).ifPresent(mod -> lines.add(Component.literal(
                    mod.getModInfo().getDisplayName()).withStyle(ChatFormatting.GRAY).getVisualOrderText()));
        }
        if (c.count() >= 0) {
            lines.add(tr(screen.isFluid() ? "tags.count.fluid" : "tags.count", c.count()).copy().withStyle(ChatFormatting.GRAY)
                    .getVisualOrderText());
        }
        lines.add((inFilter(c.entry()) ? tr("tags.in_filter") : checked.contains(c.entry()) ? tr("tags.uncheck") : tr("tags.check"))
                .copy().withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        return lines;
    }

    // desenho

    void render(GuiGraphics g, int mouseX, int mouseY) {
        Font font = font();
        int x = frame.left() + frame.ix();
        int y = frame.top();
        if (screen.isChemical()) {
            wrapped(g, tr("tags.chemical.hint"), x, y + INSPECT_Y, IW, 2, GuiPaint.MUTED);
        } else {
            renderInspector(g, x, y, mouseX, mouseY);
        }
        GuiPaint.box(g, x, y + TAG_SEARCH_Y, IW, FIELD_H, GuiPaint.PANEL, tagSearchBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);

        // linhas
        int rows = candRows();
        if (candidates.isEmpty()) {
            Component empty = !tagSearchDraft.isBlank() ? tr("tags.none")
                    : screen.isChemical() ? tr("tags.chemical.empty") : tr(screen.isFluid() ? "tags.empty.fluid" : "tags.empty");
            wrapped(g, empty, x, y + CAND_Y + 2, IW, rows, GuiPaint.DISABLED);
        }
        for (int r = 0; r < rows && candScroll + r < candidates.size(); r++) {
            Candidate c = candidates.get(candScroll + r);
            int ry = y + CAND_Y + r * CAND_ROW;
            boolean had = inFilter(c.entry());
            boolean on = had || checked.contains(c.entry());
            if (c == hoveredCandidate) {
                g.fill(x - 2, ry, x + IW + 2, ry + CAND_ROW, GuiPaint.ROW_HOVER);
            }
            GuiPaint.checkbox(g, x, ry + 1, on, had ? GuiPaint.DISABLED : GuiPaint.ACCENT);
            String count = c.count() < 0 ? "" : RateFormat.abbreviate(c.count());
            int countW = count.isEmpty() ? 0 : font.width(count) + 4;
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, Component.literal(c.label()), IW - 12 - countW), x + 12, ry + 2,
                    had ? GuiPaint.DISABLED : c.entry() instanceof ModEntry ? GuiPaint.BRASS : GuiPaint.FG);
            if (!count.isEmpty()) {
                GuiPaint.textRight(g, font, Component.literal(count), x + IW, ry + 2, GuiPaint.DISABLED);
            }
        }
        int max = maxCandScroll();
        if (max > 0) {
            int bx = x + IW + 1;
            int hh = rows * CAND_ROW;
            int thumb = thumbHeight(hh, rows, candidates.size());
            int ty = y + CAND_Y + Math.round((hh - thumb) * (candScroll / (float) max));
            g.fill(bx, y + CAND_Y, bx + 2, y + CAND_Y + hh, GuiPaint.PANEL);
            g.fill(bx, ty, bx + 2, ty + thumb, GuiPaint.BEVEL_DARK);
        }
        renderPreview(g, x, y);
    }

    private void renderInspector(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        Font font = font();
        GuiPaint.slot(g, x - 1, y + INSPECT_Y - 1);
        if (overInspector(mouseX, mouseY)) {
            GuiPaint.outline(g, x - 1, y + INSPECT_Y - 1, 18, 18, GuiPaint.ACCENT);
        }
        Component name;
        Component sub;
        if (!inspected.isEmpty()) {
            g.renderItem(inspected, x, y + INSPECT_Y);
            name = inspected.getHoverName();
            sub = tr("inspect.tags", inspected.getTags().count());
        } else if (!inspectedFluid.isEmpty()) {
            GuiPaint.fluid(g, inspectedFluid, x, y + INSPECT_Y);
            name = inspectedFluid.getHoverName();
            sub = tr("inspect.tags", FilterMembers.fluidTags(inspectedFluid.getFluid()).count());
        } else {
            name = tr("inspect");
            sub = tr("inspect.hint");
        }
        int textW = IW - 21 - (hasInspected() ? 10 : 0);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, name, textW), x + 21, y + INSPECT_Y, GuiPaint.FG);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, sub, textW), x + 21, y + INSPECT_Y + 9, GuiPaint.MUTED);
        if (hasInspected()) {
            GuiPaint.text(g, font, Component.literal("×"), x + IW - 7, y + INSPECT_Y,
                    overInspectorClear(mouseX, mouseY) ? GuiPaint.DANGER : GuiPaint.MUTED);
        }
    }

    /** Prévia: a linha sob o mouse ou a última marcada. */
    private void renderPreview(GuiGraphics g, int x, int y) {
        int py = y + frame.innerBottom() - BTN_H - 3 - PREVIEW_H;
        g.fill(x - 2, py, x + IW + 2, py + 1, GuiPaint.LINE);
        Candidate pv = hoveredCandidate;
        if (pv == null && !checked.isEmpty()) {
            FilterEntry last = null;
            for (FilterEntry entry : checked) {
                last = entry;
            }
            for (Candidate c : candidates) {
                if (c.entry().equals(last)) {
                    pv = c;
                }
            }
        }
        if (pv == null || pv.entry() instanceof ChemicalEntry) {
            wrapped(g, tr("tags.preview.hint"), x, py + 4, IW, 2, GuiPaint.DISABLED);
            return;
        }
        GuiPaint.text(g, font(), GuiPaint.ellipsize(font(), Component.literal(pv.label()), IW), x, py + 3, GuiPaint.FG);
        int perRow = IW / 17;
        if (screen.isFluid()) {
            List<FluidStack> fluids = members.fluidMembers(pv.entry());
            for (int i = 0; i < Math.min(perRow, fluids.size()); i++) {
                GuiPaint.fluid(g, fluids.get(i), x + i * 17, py + 13);
            }
        } else if (!screen.isChemical()) {
            List<ItemStack> items = members.itemMembers(pv.entry());
            for (int i = 0; i < Math.min(perRow, items.size()); i++) {
                g.renderItem(items.get(i), x + i * 17, py + 13);
            }
        }
    }

    // ingredientes fantasmas (JEI)

    /** O slot do inspetor, se a aba Tags está aberta e o filtro tem tags (itens ou fluidos); senão, nulo. */
    @Nullable Rect2i inspectArea() {
        return screen.tab() == Tab.TAGS && !screen.isChemical()
                ? new Rect2i(frame.left() + frame.ix() - 1, frame.top() + INSPECT_Y - 1, 18, 18) : null;
    }

    /** O ingrediente serve para o inspetor (item num filtro de itens; fluido ou balde num de fluidos)? */
    boolean canInspect(Object ingredient) {
        return switch (ingredient) {
            case ItemStack stack when screen.isItem() -> !stack.isEmpty();
            case ItemStack stack when screen.isFluid() -> FluidUtil.getFluidContained(stack).filter(f -> !f.isEmpty()).isPresent();
            case FluidStack stack when screen.isFluid() -> !stack.isEmpty();
            default -> false;
        };
    }

    /** Põe um ingrediente de fora no inspetor (só a tela; nada vai ao servidor). */
    void inspectGhost(Object ingredient) {
        switch (ingredient) {
            case ItemStack stack -> inspect(stack);
            case FluidStack stack -> inspectFluid(stack);
            default -> {
            }
        }
    }

    // captura e teste de ponta a ponta

    void previewCheck(int candidate) {
        rebuildCandidates();
        if (candidate >= 0 && candidate < candidates.size()) {
            toggleCandidate(candidates.get(candidate));
        }
    }

    List<String> candidateLabels() {
        rebuildCandidates();
        return candidates.stream().map(Candidate::label).toList();
    }

    List<String> checkedLabels() {
        List<String> labels = new ArrayList<>();
        for (FilterEntry entry : checked) {
            labels.add(FilterText.entryId(entry));
        }
        return labels;
    }

    int[] candidateCenter(int index) {
        return new int[] {frame.left() + frame.ix() + 30, frame.top() + CAND_Y + (index - candScroll) * CAND_ROW + CAND_ROW / 2};
    }

    int[] inspectorCenter() {
        return new int[] {frame.left() + frame.ix() + 8, frame.top() + INSPECT_Y + 8};
    }
}
