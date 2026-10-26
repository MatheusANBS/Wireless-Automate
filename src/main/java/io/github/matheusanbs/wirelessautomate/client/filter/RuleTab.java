package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.BOX_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.BTN_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.COND_ROW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.COND_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.FIELD_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.IW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.LEVEL_W;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.TRI_W;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintCheck;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintPrimary;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintTextButton;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintTri;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen.Tab;
import io.github.matheusanbs.wirelessautomate.filter.ComponentQuery;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.RuleEntry;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule.Property;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Regra (só itens): monta uma regra por propriedade ({@link ItemRule}) com — / Sim / Não por
 * propriedade, encantamento com nível ({@link EnchantPicker}), durabilidade e "só em"; o inventário acende
 * no que ela pega antes de adicionar. Também edita uma regra que já está no filtro.
 */
final class RuleTab {
    private final FilterScreen screen;
    private final FilterFrame frame;
    private final FilterActions actions;
    private final EnchantPicker picker;
    private final RuleSuggestions suggestions = new RuleSuggestions();
    private final TextSuggest scopeSuggest;
    private final TextSuggest componentSuggest;

    /** Quantas condições de componente cabem como linhas (cada uma com o ✕); o campo de baixo é a próxima. */
    private static final int MAX_CHIPS = 4;
    private static final int CHIP_H = COND_ROW + 1;

    private ItemRule draft = ItemRule.EMPTY;
    /** As condições de componente já escritas (as linhas); o campo guarda a que está sendo digitada. */
    private final List<String> conditions = new ArrayList<>();
    private final FlatButton[] chipRemove = new FlatButton[MAX_CHIPS];
    private int editing = -1;
    private @Nullable ItemRule compiledFor;
    private Predicate<ItemStack> compiled = stack -> false;

    private final FlatButton[][] triButtons = new FlatButton[Property.values().length][3];
    private FlatButton enchantToggle;
    private final FlatButton[] durabilityButtons = new FlatButton[3];
    private FlatButton durabilityPercent;
    private EditBox scopeBox;
    private EditBox componentBox;
    private FlatButton ruleResetButton;
    private FlatButton ruleAddButton;

    RuleTab(FilterScreen screen, FilterActions actions) {
        this.screen = screen;
        this.frame = screen.frame();
        this.actions = actions;
        this.picker = new EnchantPicker(screen, this);
        this.scopeSuggest = new TextSuggest(screen, () -> scopeBox, suggestions::scope);
        this.componentSuggest = new TextSuggest(screen, () -> componentBox, suggestions::component);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    EnchantPicker picker() {
        return picker;
    }

    ItemRule draft() {
        return draft;
    }

    EditBox scopeBox() {
        return scopeBox;
    }

    boolean suggestionsShown() {
        return picker.suggestionsShown() || scopeSuggest.shown() || componentSuggest.shown();
    }

    void focusChanged(@Nullable GuiEventListener before, @Nullable GuiEventListener listener) {
        scopeSuggest.focusChanged(before, listener);
        componentSuggest.focusChanged(before, listener);
    }

    boolean suggestionClicked(double mx, double my, int button) {
        return scopeSuggest.mouseClicked(mx, my, button) || componentSuggest.mouseClicked(mx, my, button);
    }

    void reopenOnClick(double mx, double my) {
        scopeSuggest.reopenOnClick(mx, my);
        componentSuggest.reopenOnClick(mx, my);
    }

    boolean suggestionKey(EditBox box, int keyCode, boolean enter) {
        return scopeSuggest.keyPressed(box, keyCode, enter) || componentSuggest.keyPressed(box, keyCode, enter)
                || enter && box == componentBox && commitPending();
    }

    // ------------------------------------------------------------------ condições de componente

    /** As condições escritas mais a que está no campo (se há), juntas por {@code ;}: o que a regra guarda. */
    private String composedComponent() {
        String pending = componentBox == null ? "" : componentBox.getValue().strip();
        List<String> all = new ArrayList<>(conditions);
        if (!pending.isEmpty()) {
            all.add(pending);
        }
        return String.join("; ", all);
    }

    /** Enter com uma condição inteira no campo: ela vira uma linha e o campo fica livre para a próxima. */
    private boolean commitPending() {
        String pending = componentBox.getValue().strip();
        if (conditions.size() >= MAX_CHIPS || ComponentQuery.parse(pending) == null) {
            return false;
        }
        conditions.add(pending);
        componentBox.setValue("");
        return true;
    }

    private void removeCondition(int index) {
        if (index < conditions.size()) {
            conditions.remove(index);
            setDraft(draft.withComponent(composedComponent()));
        }
    }

    /** Põe as condições de {@code component} nas linhas e deixa o campo vazio (a regra já tem o mesmo texto). */
    private void loadComponent(String component) {
        conditions.clear();
        for (String piece : component.split(";")) {
            if (!piece.isBlank() && conditions.size() < MAX_CHIPS) {
                conditions.add(piece.strip());
            }
        }
        componentBox.setValue("");
    }

    private static void paintCross(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 5; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            g.fill(x + 4 - i, y + i, x + 5 - i, y + i + 1, color);
        }
    }

    /** Captura: amostras de exemplo para as sugestões de {@code id}. */
    void previewSamples(String id, List<Object> trees) {
        suggestions.previewSamples(id, trees, true);
    }

    /** Captura: uma condição já escrita e outra sendo digitada, com a lista de sugestões aberta. */
    void previewComponent(List<String> written, String typing) {
        screen.openTab(Tab.RULE);
        conditions.clear();
        conditions.addAll(written);
        scopeSuggest.close();
        componentBox.setValue(typing);
        screen.setFocused(componentBox);
        componentBox.moveCursorToEnd(false);
        componentSuggest.typed();
    }

    boolean scrollSuggestions(double mx, double my, int step) {
        return scopeSuggest.scroll(mx, my, step) || componentSuggest.scroll(mx, my, step);
    }

    boolean suggestionTooltip(int mx, int my) {
        return scopeSuggest.tooltip(mx, my) || componentSuggest.tooltip(mx, my);
    }

    void renderSuggestions(GuiGraphics g, int mx, int my) {
        scopeSuggest.render(g, mx, my);
        componentSuggest.render(g, mx, my);
    }

    EditBox componentBox() {
        return componentBox;
    }

    void addWidgets() {
        Property[] properties = Property.values();
        for (Property property : properties) {
            for (int v = 0; v < 3; v++) {
                Boolean value = v == 0 ? null : v == 1;
                Component label = v == 0 ? Component.literal("—") : tr(v == 1 ? "rule.yes" : "rule.no");
                triButtons[property.ordinal()][v] = screen.add(new FlatButton(0, 0, TRI_W, COND_ROW - 1, label,
                        (g, b, hovered) -> paintTri(g, b, hovered, label, sameFlag(draft.flag(property), value),
                                Boolean.FALSE.equals(value), value == null),
                        () -> setDraft(draft.withFlag(property, value)))
                        .tooltip(() -> tr("rule.prop." + property.getSerializedName() + ".tooltip")));
            }
        }
        enchantToggle = screen.add(new FlatButton(0, 0, 70, COND_ROW, tr("rule.enchantment"),
                (g, b, hovered) -> paintCheck(g, b, hovered, tr("rule.enchantment"), draft.enchantment().isPresent()),
                this::toggleEnchantment).tooltip(() -> tr("rule.enchantment.tooltip")));
        picker.addWidgets();
        for (int v = 0; v < 3; v++) {
            int mode = v;
            Component label = Component.literal(v == 0 ? "-" : v == 1 ? "≥" : "<");
            durabilityButtons[v] = screen.add(new FlatButton(0, 0, 14, COND_ROW - 1, label,
                    (g, b, hovered) -> paintTri(g, b, hovered, label, durabilityMode() == mode, false, false),
                    () -> setDurabilityMode(mode)).tooltip(() -> tr("rule.durability.tooltip." + mode)));
        }
        durabilityPercent = screen.add(new FlatButton(0, 0, 28, COND_ROW - 1, tr("rule.percent"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal(
                        draft.durability().map(d -> d.percent() + "%").orElse("")), false), () -> stepPercent(10))
                .tooltip(() -> tr("rule.percent.tooltip")));
        scopeBox = screen.box(tr("rule.scope"), ItemRule.MAX_SCOPE, draft.scope(), value -> {
            setDraft(draft.withScope(value));
            scopeSuggest.typed();
        });
        componentBox = screen.box(tr("rule.component"), ItemRule.MAX_COMPONENT, "",
                value -> {
                    setDraft(draft.withComponent(composedComponent()));
                    componentSuggest.typed();
                });
        for (int i = 0; i < MAX_CHIPS; i++) {
            int index = i;
            chipRemove[i] = screen.add(new FlatButton(0, 0, 10, CHIP_H - 3, Component.literal("✕"),
                    (g, b, hovered) -> paintCross(g, b.getX() + 2, b.getY() + 2, hovered ? GuiPaint.ACCENT : GuiPaint.MUTED),
                    () -> removeCondition(index)).tooltip(() -> tr("rule.component.remove")));
        }
        componentBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("rule.component.tooltip")));
        ruleResetButton = screen.add(new FlatButton(0, 0, 40, BTN_H, Component.empty(),
                (g, b, hovered) -> paintTextButton(g, b, hovered, editing >= 0 ? tr("rule.cancel") : tr("rule.reset"), false),
                this::resetRule).tooltip(() -> editing >= 0 ? tr("rule.cancel.tooltip") : tr("rule.reset.tooltip")));
        ruleAddButton = screen.add(new FlatButton(0, 0, 40, BTN_H, tr("rule.add"),
                (g, b, hovered) -> paintPrimary(g, b, hovered, editing >= 0 ? tr("rule.save") : tr("rule.add")),
                this::submitRule).tooltip(() -> !draft.validScope() ? tr("rule.scope.invalid")
                        : !draft.validComponent() ? tr("rule.component.invalid")
                        : !picker.enchantFieldValid() ? tr("rule.enchantment.invalid")
                        : !picker.levelFieldValid() ? tr("rule.level.invalid")
                        : draft.isEmpty() ? tr("rule.empty") : null));
    }

    void refresh(int px, int y, int bottom, int half) {
        boolean rule = screen.tab() == Tab.RULE;
        Property[] properties = Property.values();
        for (Property property : properties) {
            for (int v = 0; v < 3; v++) {
                FlatButton b = triButtons[property.ordinal()][v];
                b.visible = rule;
                b.setPosition(px + IW - 3 * TRI_W + v * TRI_W, y + COND_Y + property.ordinal() * COND_ROW);
            }
        }
        int ry = y + COND_Y + properties.length * COND_ROW + 3;
        enchantToggle.visible = rule;
        enchantToggle.setPosition(px, ry);
        enchantToggle.setWidth(IW);
        ry = picker.refresh(px, ry, rule && draft.enchantment().isPresent());
        ry += COND_ROW + 3;
        for (int v = 0; v < 3; v++) {
            durabilityButtons[v].visible = rule;
            durabilityButtons[v].setPosition(px + IW - 28 - 4 - 3 * 14 + v * 14, ry);
        }
        durabilityPercent.visible = rule && draft.durability().isPresent();
        durabilityPercent.setPosition(px + IW - 28, ry);
        ry += COND_ROW + 3;
        scopeBox.visible = rule;
        int scopeLabel = font().width(tr("rule.scope")) + 5;
        scopeBox.setPosition(px + scopeLabel + 4, ry + 2);
        scopeBox.setWidth(IW - scopeLabel - 8);
        ry += FIELD_H + 3;
        for (int i = 0; i < MAX_CHIPS; i++) {
            chipRemove[i].visible = rule && i < conditions.size();
            chipRemove[i].setPosition(px + IW - 12, ry + i * CHIP_H + 1);
        }
        ry += conditions.size() * CHIP_H;
        componentBox.visible = rule;
        int componentLabel = font().width(tr("rule.component")) + 5;
        componentBox.setPosition(px + componentLabel + 4, ry + 2);
        componentBox.setWidth(IW - componentLabel - 8);
        ruleResetButton.visible = ruleAddButton.visible = rule;
        ruleResetButton.setPosition(px, bottom - BTN_H);
        ruleResetButton.setWidth(half);
        ruleAddButton.setPosition(px + IW - half, bottom - BTN_H);
        ruleAddButton.setWidth(half);
        ruleAddButton.active = draft.isValid() && picker.enchantFieldValid() && picker.levelFieldValid();
    }


    /** A visão mudou: a regra em edição sumiu do filtro (ou mudou de lugar para outra coisa) e não é mais editada. */
    void dropStaleEditing(Filter f) {
        if (editing >= 0 && (editing >= f.entries().size() || !(f.entries().get(editing) instanceof RuleEntry))) {
            editing = -1;
        }
    }

    private static boolean sameFlag(@Nullable Boolean a, @Nullable Boolean b) {
        return a == null ? b == null : a.equals(b);
    }

    void setDraft(ItemRule rule) {
        boolean enchantChanged = !draft.enchantment().equals(rule.enchantment());
        draft = rule;
        if (enchantChanged) {
            picker.syncEnchantFields();
        }
    }

    Predicate<ItemStack> draftPredicate() {
        if (!draft.equals(compiledFor)) {
            compiledFor = draft;
            compiled = draft.isValid() ? draft.compile() : stack -> false;
        }
        return compiled;
    }

    private void toggleEnchantment() {
        if (draft.enchantment().isPresent()) {
            setDraft(draft.withEnchantment(Optional.empty()));
            return;
        }
        List<Holder<Enchantment>> list = picker.enchantments();
        if (list.isEmpty()) {
            return;
        }
        Holder<Enchantment> first = list.stream()
                .filter(h -> h.unwrapKey().map(k -> k.location().getPath().equals("fortune")).orElse(false))
                .findFirst().orElse(list.getFirst());
        setDraft(draft.withEnchantment(Optional.of(new ItemRule.Enchant(first.unwrapKey().orElseThrow().location(), 1))));
    }

    /** 0 = tanto faz, 1 = pelo menos, 2 = abaixo de. */
    private int durabilityMode() {
        return draft.durability().map(d -> d.atLeast() ? 1 : 2).orElse(0);
    }

    private void setDurabilityMode(int mode) {
        int percent = draft.durability().map(ItemRule.Durability::percent).orElse(mode == 2 ? 25 : 50);
        setDraft(draft.withDurability(mode == 0 ? Optional.empty() : Optional.of(new ItemRule.Durability(mode == 1, percent))));
    }

    private void stepPercent(int delta) {
        draft.durability().ifPresent(d -> {
            int step = Screen.hasShiftDown() ? Integer.signum(delta) : delta;
            int percent = d.percent() + step;
            percent = percent > 100 ? 1 : percent < 1 ? 100 : percent;
            setDraft(draft.withDurability(Optional.of(new ItemRule.Durability(d.atLeast(), percent))));
        });
    }

    private void resetRule() {
        editing = -1;
        setDraft(ItemRule.EMPTY);
        picker.syncEnchantFields();
        scopeBox.setValue("");
        conditions.clear();
        componentBox.setValue("");
    }

    void submitRule() {
        if (!draft.isValid() || !picker.enchantFieldValid() || !picker.levelFieldValid()) {
            return;
        }
        RuleEntry entry = new RuleEntry(draft, 0);
        screen.selection().pendingReveal(entry);
        actions.sendEntries(editing, List.of(entry));
        editing = -1;
        setDraft(ItemRule.EMPTY);
        picker.syncEnchantFields();
        scopeBox.setValue("");
        conditions.clear();
        componentBox.setValue("");
        screen.setTab(Tab.ENTRY);
        screen.unfocus();
    }

    /** Regra do filtro (na posição {@code index}) vai para o montador, para editar. */
    void edit(ItemRule rule, int index) {
        editing = index;
        setDraft(rule);
        picker.syncEnchantFields();
        scopeBox.setValue(rule.scope());
        loadComponent(rule.component());
        screen.openTab(Tab.RULE);
    }

    /** Captura: abre a aba com a regra no montador. */
    void preview(ItemRule rule) {
        screen.openTab(Tab.RULE);
        setDraft(rule);
        picker.syncEnchantFields();
        scopeBox.setValue(rule.scope());
        loadComponent(rule.component());
    }

    /** Botão direito na porcentagem volta 10. */
    boolean rightClickPercent(double mouseX, double mouseY) {
        if (durabilityPercent.visible && durabilityPercent.isMouseOver(mouseX, mouseY)) {
            stepPercent(-10);
            return true;
        }
        return false;
    }

    /** A roda no campo do nível ou na porcentagem. */
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int step) {
        if (screen.tab() == Tab.RULE && picker.scrollLevel(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (durabilityPercent.visible && durabilityPercent.isMouseOver(mouseX, mouseY)) {
            stepPercent(-step * 5);
            return true;
        }
        return false;
    }

    void render(GuiGraphics g) {
        Font font = font();
        int x = frame.left() + frame.ix();
        int y = frame.top();
        GuiPaint.text(g, font, editing >= 0 ? tr("rule.editing") : tr("rule.new"), x, y + BOX_Y + 6, GuiPaint.FG);
        for (Property property : Property.values()) {
            int ry = y + COND_Y + property.ordinal() * COND_ROW;
            boolean set = draft.flag(property) != null;
            GuiText.draw(g, font, tr("rule.prop." + property.getSerializedName()), x, ry + 1, IW - 3 * TRI_W - 4,
                    set ? GuiPaint.FG : GuiPaint.MUTED);
        }
        int ry = y + COND_Y + Property.values().length * COND_ROW + 3;
        if (draft.enchantment().isPresent()) {
            ry += COND_ROW + 1;
            boolean nameOk = picker.enchantFieldValid() || picker.suggestionsShown();
            int nameBorder = !nameOk ? GuiPaint.DANGER : picker.enchantBox().isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE;
            GuiPaint.box(g, x, ry, picker.enchantFieldW(), FIELD_H, GuiPaint.PANEL, nameBorder);
            int levelX = x + IW - LEVEL_W;
            GuiPaint.text(g, font, Component.literal("≥"), levelX - 3 - font.width("≥"), ry + 2, GuiPaint.MUTED);
            int levelBorder = !picker.levelFieldValid() ? GuiPaint.DANGER
                    : picker.levelBox().isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE;
            GuiPaint.box(g, levelX, ry, LEVEL_W, FIELD_H, GuiPaint.PANEL, levelBorder);
            ry += FIELD_H - COND_ROW;
        } else {
            GuiPaint.textRight(g, font, tr("rule.any"), x + IW, ry + 2, GuiPaint.DISABLED);
        }
        ry += COND_ROW + 3;
        GuiText.draw(g, font, tr("rule.durability"), x, ry + 1, IW - 28 - 4 - 3 * 14 - 2,
                draft.durability().isPresent() ? GuiPaint.FG : GuiPaint.MUTED);
        ry += COND_ROW + 3;
        GuiPaint.text(g, font, tr("rule.scope"), x, ry + 3, draft.scope().isEmpty() ? GuiPaint.MUTED : GuiPaint.FG);
        int scopeX = x + font.width(tr("rule.scope")) + 5;
        int border = !draft.validScope() ? GuiPaint.DANGER : scopeBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE;
        GuiPaint.box(g, scopeX, ry, x + IW - scopeX, FIELD_H, GuiPaint.PANEL, border);
        ry += FIELD_H + 3;
        for (int i = 0; i < conditions.size(); i++) {
            int cy = ry + i * CHIP_H;
            ComponentQuery query = ComponentQuery.parse(conditions.get(i));
            GuiPaint.box(g, x, cy, IW, CHIP_H - 1, GuiPaint.BUTTON, query == null ? GuiPaint.DANGER : GuiPaint.BUTTON_BORDER);
            GuiText.draw(g, font, Component.literal(query == null ? conditions.get(i) : query.summary()), x + 4, cy + 2,
                    IW - 18, query == null ? GuiPaint.DANGER : GuiPaint.FG);
        }
        ry += conditions.size() * CHIP_H;
        GuiPaint.text(g, font, tr("rule.component"), x, ry + 3, draft.component().isEmpty() ? GuiPaint.MUTED : GuiPaint.FG);
        int componentX = x + font.width(tr("rule.component")) + 5;
        int componentBorder = !draft.validComponent() ? GuiPaint.DANGER
                : componentBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE;
        GuiPaint.box(g, componentX, ry, x + IW - componentX, FIELD_H, GuiPaint.PANEL, componentBorder);
        ry += FIELD_H + 5;
        if (suggestionsShown()) {
            // a lista de sugestões cobre o resumo: sem pedaços de texto saindo por baixo dela
            return;
        }
        renderSummary(g, x, ry, y + frame.innerBottom() - BTN_H - 3);
    }

    /** O resumo passa pelo GuiText: o que não cabe é abreviado e a dica mostra o texto inteiro. */
    private void renderSummary(GuiGraphics g, int x, int ry, int limit) {
        Font font = font();
        if (draft.isEmpty()) {
            GuiText.wrap(g, font, tr("rule.empty"), x, ry, IW, (limit - ry) / 10, GuiPaint.DISABLED);
        } else if (!draft.validScope()) {
            GuiText.wrap(g, font, tr("rule.scope.invalid"), x, ry, IW, (limit - ry) / 10, GuiPaint.DANGER);
        } else if (!draft.validComponent()) {
            GuiText.wrap(g, font, tr("rule.component.invalid"), x, ry, IW, (limit - ry) / 10, GuiPaint.DANGER);
        } else if (!picker.enchantFieldValid()) {
            GuiText.wrap(g, font, tr("rule.enchantment.invalid"), x, ry, IW, (limit - ry) / 10, GuiPaint.DANGER);
        } else if (!picker.levelFieldValid()) {
            GuiText.wrap(g, font, tr("rule.level.invalid"), x, ry, IW, (limit - ry) / 10, GuiPaint.DANGER);
        } else {
            int used = GuiText.wrap(g, font, FilterText.ruleText(draft), x, ry, IW, Math.max(1, (limit - ry) / 10 - 1),
                    GuiPaint.RULE_TEXT);
            int n = InventoryArea.matches(screen, draftPredicate());
            // quebra nas linhas que sobram até os botões; o que ainda não couber vai para a dica
            GuiText.wrap(g, font, tr("rule.inventory", n), x, ry + used, IW, (limit - ry - used) / 10, GuiPaint.MUTED);
        }
    }
}
