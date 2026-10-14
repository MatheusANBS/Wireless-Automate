package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.COND_ROW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.FIELD_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.IW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.LEVEL_W;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.SUGGEST_MORE_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.SUGGEST_ROW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.SUGGEST_ROWS;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen.Tab;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * O encantamento da aba Regra: o campo do nome com a lista de sugestões por cima das linhas de baixo
 * ({@link EnchantSearch}), e o campo do nível mínimo (1 a 255, a roda soma). O que vale fica no rascunho
 * da {@link RuleTab}.
 */
final class EnchantPicker {
    private static final ItemStack BOOK = new ItemStack(Items.ENCHANTED_BOOK);

    private final FilterScreen screen;
    private final FilterFrame frame;
    private final RuleTab rule;

    private @Nullable List<Holder<Enchantment>> enchantments;
    private EditBox enchantBox;
    private EditBox levelBox;
    /** Texto do campo de nível (sobrevive ao {@code init} de um redimensionamento, mesmo inválido). */
    private String levelText = "1";
    /** Lista de sugestões aberta por cima das linhas de baixo. */
    private boolean suggesting;
    private List<Holder<Enchantment>> suggestions = List.of();
    private int suggestSel;
    private int suggestTop;
    /** Texto posto pela própria tela no campo do encantamento: não abre a lista. */
    private boolean syncingFields;

    EnchantPicker(FilterScreen screen, RuleTab rule) {
        this.screen = screen;
        this.frame = screen.frame();
        this.rule = rule;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private ItemRule draft() {
        return rule.draft();
    }

    void addWidgets() {
        suggesting = false;
        enchantBox = screen.box(tr("rule.enchantment"), 256, enchantDisplay(), value -> {
            if (!syncingFields) {
                openSuggestions();
            }
        });
        levelBox = screen.box(tr("rule.level"), 3, levelText, this::typedLevel);
        levelBox.setFilter(value -> value.chars().allMatch(c -> c >= '0' && c <= '9'));
    }

    EditBox enchantBox() {
        return enchantBox;
    }

    EditBox levelBox() {
        return levelBox;
    }

    /** Posições dos dois campos, na linha abaixo da caixa do encantamento; devolve o {@code ry} da linha seguinte. */
    int refresh(int px, int ry, boolean enchant) {
        enchantBox.visible = levelBox.visible = enchant;
        if (!enchant) {
            suggesting = false;
        }
        if (enchant) {
            ry += COND_ROW + 1;
            enchantBox.setPosition(px + 4, ry + 2);
            enchantBox.setWidth(enchantFieldW() - 8);
            levelBox.setPosition(px + IW - LEVEL_W + 4, ry + 2);
            levelBox.setWidth(LEVEL_W - 8);
            ry += FIELD_H - COND_ROW;
        }
        return ry;
    }

    /** Os campos de encantamento e de nível mostram o que está no rascunho (fecha a lista). */
    void syncEnchantFields() {
        if (enchantBox == null || levelBox == null) {
            return;
        }
        suggesting = false;
        setEnchantText(enchantDisplay());
        int level = draft().enchantment().map(ItemRule.Enchant::minLevel).orElse(1);
        if (EnchantSearch.parseLevel(levelBox.getValue()).orElse(-1) != level) {
            levelBox.setValue(Integer.toString(level));
        }
    }

    private void setEnchantText(String text) {
        syncingFields = true;
        try {
            enchantBox.setValue(text);
        } finally {
            syncingFields = false;
        }
    }

    List<Holder<Enchantment>> enchantments() {
        if (enchantments == null) {
            List<Holder<Enchantment>> list = new ArrayList<>();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                minecraft.level.registryAccess().registry(Registries.ENCHANTMENT)
                        .ifPresent(registry -> registry.holders().forEach(list::add));
            }
            list.sort(Comparator.comparing(h -> h.value().description().getString()));
            enchantments = list;
        }
        return enchantments;
    }

    private @Nullable Holder<Enchantment> draftEnchantment() {
        ResourceLocation id = draft().enchantment().map(ItemRule.Enchant::id).orElse(null);
        if (id == null) {
            return null;
        }
        for (Holder<Enchantment> holder : enchantments()) {
            if (holder.unwrapKey().map(k -> k.location().equals(id)).orElse(false)) {
                return holder;
            }
        }
        return null;
    }

    static String enchantName(Holder<Enchantment> holder) {
        return holder.value().description().getString();
    }

    private static String enchantId(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(k -> k.location().toString()).orElse("");
    }

    /** O nome do encantamento escolhido (ou o id, se ele não está no registro); vazio sem encantamento. */
    private String enchantDisplay() {
        if (draft().enchantment().isEmpty()) {
            return "";
        }
        Holder<Enchantment> holder = draftEnchantment();
        return holder != null ? enchantName(holder) : draft().enchantment().get().id().toString();
    }

    /** O texto do campo é o do encantamento escolhido. */
    boolean enchantFieldValid() {
        return draft().enchantment().isEmpty() || enchantBox == null || enchantBox.getValue().equals(enchantDisplay());
    }

    /** O nível digitado vale (1 a 255). */
    boolean levelFieldValid() {
        return draft().enchantment().isEmpty() || levelBox == null || EnchantSearch.parseLevel(levelBox.getValue()).isPresent();
    }

    /** Largura da moldura do campo do encantamento: o painel menos o "≥" e o campo do nível. */
    int enchantFieldW() {
        return IW - LEVEL_W - font().width("≥") - 6;
    }

    /** Abre (ou refaz) a lista pelo texto do campo; igual ao escolhido (ou vazio) lista todos. */
    private void openSuggestions() {
        if (draft().enchantment().isEmpty() || enchantBox == null) {
            suggesting = false;
            return;
        }
        String text = enchantBox.getValue();
        String query = text.equals(enchantDisplay()) ? "" : text;
        suggestions = EnchantSearch.search(enchantments(), EnchantPicker::enchantName, EnchantPicker::enchantId, query);
        suggestSel = 0;
        suggestTop = 0;
        suggesting = !suggestions.isEmpty();
    }

    /** Fecha a lista sem escolher: o texto volta ao encantamento escolhido. */
    private void closeSuggestions() {
        suggesting = false;
        if (enchantBox != null && !enchantBox.getValue().equals(enchantDisplay())) {
            setEnchantText(enchantDisplay());
        }
    }

    private void pickSuggestion(Holder<Enchantment> holder) {
        int level = draft().enchantment().map(ItemRule.Enchant::minLevel).orElse(1);
        ResourceLocation id = holder.unwrapKey().orElseThrow().location();
        rule.setDraft(draft().withEnchantment(Optional.of(new ItemRule.Enchant(id, level))));
        suggesting = false;
        setEnchantText(enchantDisplay());
    }

    private void moveSuggestion(int direction) {
        suggestSel = Math.max(0, Math.min(suggestions.size() - 1, suggestSel + direction));
        if (suggestSel < suggestTop) {
            suggestTop = suggestSel;
        } else if (suggestSel >= suggestTop + SUGGEST_ROWS) {
            suggestTop = suggestSel - SUGGEST_ROWS + 1;
        }
    }

    /** Nível digitado: só um valor de 1 a 255 vai para o rascunho. */
    private void typedLevel(String value) {
        levelText = value;
        EnchantSearch.parseLevel(value).ifPresent(level -> draft().enchantment().ifPresent(e -> {
            if (e.minLevel() != level) {
                rule.setDraft(draft().withEnchantment(Optional.of(new ItemRule.Enchant(e.id(), level))));
            }
        }));
    }

    /** Roda do mouse no nível: soma {@code delta} ao valor do campo (ou ao do rascunho, se o campo não vale). */
    private void stepLevel(int delta) {
        draft().enchantment().ifPresent(e -> {
            int current = EnchantSearch.parseLevel(levelBox.getValue()).orElse(e.minLevel());
            levelBox.setValue(Integer.toString(EnchantSearch.stepLevel(current, delta)));
        });
    }

    private static void selectAll(EditBox box) {
        box.moveCursorToEnd(false);
        box.setHighlightPos(0);
    }

    /** O foco da tela mudou: sair do nome fecha a lista; entrar num campo seleciona o texto (no nome, abre a lista). */
    void focusChanged(@Nullable GuiEventListener before, @Nullable GuiEventListener listener) {
        if (before != null && before == enchantBox) {
            closeSuggestions();
        }
        if (listener != null && listener == enchantBox) {
            selectAll(enchantBox);
            openSuggestions();
        } else if (listener != null && listener == levelBox) {
            selectAll(levelBox);
        }
    }

    // lista de sugestões (por cima das linhas de baixo)

    private int suggestX() {
        return frame.left() + frame.ix();
    }

    private int suggestY() {
        return enchantBox.getY() - 2 + FIELD_H - 1;
    }

    private int suggestVisible() {
        return Math.min(SUGGEST_ROWS, suggestions.size());
    }

    private int suggestH() {
        return suggestVisible() * SUGGEST_ROW + 2 + (suggestions.size() > SUGGEST_ROWS ? SUGGEST_MORE_H : 0);
    }

    boolean suggestionsShown() {
        return suggesting && screen.tab() == Tab.RULE && enchantBox.visible && !suggestions.isEmpty();
    }

    private boolean overSuggestions(double mouseX, double mouseY) {
        return suggestionsShown() && mouseX >= suggestX() && mouseX < suggestX() + IW
                && mouseY >= suggestY() && mouseY < suggestY() + suggestH();
    }

    /** Índice (na lista inteira) da sugestão sob o mouse, ou −1. */
    private int suggestionAt(double mouseX, double mouseY) {
        if (!overSuggestions(mouseX, mouseY)) {
            return -1;
        }
        int row = (int) Math.floor((mouseY - suggestY() - 1) / SUGGEST_ROW);
        return row >= 0 && row < suggestVisible() ? suggestTop + row : -1;
    }

    /** Moldura do campo do encantamento ou do nível sob o mouse (para a roda e as dicas). */
    private boolean overEnchantField(double mouseX, double mouseY) {
        int x = frame.left() + frame.ix();
        int y = enchantBox.getY() - 2;
        return enchantBox.visible && mouseX >= x && mouseX < x + enchantFieldW() && mouseY >= y && mouseY < y + FIELD_H;
    }

    private boolean overLevelField(double mouseX, double mouseY) {
        int x = frame.left() + frame.ix() + IW - LEVEL_W;
        int y = levelBox.getY() - 2;
        return levelBox.visible && mouseX >= x && mouseX < x + LEVEL_W && mouseY >= y && mouseY < y + FIELD_H;
    }

    // eventos

    /** Lista aberta: o clique nela escolhe e não vaza para os widgets de baixo; fora dela (e do campo), fecha. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (suggestionsShown()) {
            if (overSuggestions(mouseX, mouseY)) {
                int index = suggestionAt(mouseX, mouseY);
                if (index >= 0 && button == 0) {
                    pickSuggestion(suggestions.get(index));
                }
                return true;
            }
            if (!enchantBox.isMouseOver(mouseX, mouseY)) {
                closeSuggestions();
            }
        }
        return false;
    }

    /** Clicar no campo do encantamento já focado reabre a lista. */
    void reopenOnClick(double mouseX, double mouseY) {
        if (screen.getFocused() == enchantBox && enchantBox.isMouseOver(mouseX, mouseY) && !suggesting) {
            openSuggestions();
        }
    }

    /** Setas, Enter, Tab e Esc no campo do encantamento; devolve se a tecla foi usada aqui. */
    boolean keyPressed(EditBox box, int keyCode, boolean enter) {
        boolean arrow = keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN;
        if (box == enchantBox && suggestionsShown()) {
            if (arrow) {
                moveSuggestion(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
                return true;
            }
            if (enter || keyCode == GLFW.GLFW_KEY_TAB) {
                pickSuggestion(suggestions.get(suggestSel));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeSuggestions();
                return true;
            }
        } else if (box == enchantBox && arrow) {
            openSuggestions();
            return true;
        }
        return false;
    }

    boolean scrollSuggestions(double mouseX, double mouseY, int step) {
        if (overSuggestions(mouseX, mouseY)) {
            suggestTop = Math.max(0, Math.min(suggestions.size() - SUGGEST_ROWS, suggestTop + step));
            return true;
        }
        return false;
    }

    /** A roda no campo do nível (Shift soma 10). */
    boolean scrollLevel(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!overLevelField(mouseX, mouseY)) {
            return false;
        }
        // Shift + roda vira roda horizontal em alguns sistemas
        double amount = scrollY != 0 ? scrollY : scrollX;
        if (amount != 0) {
            stepLevel((int) Math.signum(amount) * (Screen.hasShiftDown() ? 10 : 1));
        }
        return true;
    }

    /** As dicas da lista de sugestões e dos dois campos; devolve se o mouse estava num deles. */
    boolean tooltip(int mouseX, int mouseY) {
        if (overSuggestions(mouseX, mouseY)) {
            int index = suggestionAt(mouseX, mouseY);
            if (index >= 0) {
                Holder<Enchantment> holder = suggestions.get(index);
                screen.setTooltipForNextRenderPass(List.of(Component.literal(enchantName(holder)).getVisualOrderText(),
                        Component.literal(enchantId(holder)).withColor(GuiPaint.DISABLED).getVisualOrderText()));
            }
            return true;
        }
        if (screen.tab() == Tab.RULE && !suggestionsShown()) {
            if (overEnchantField(mouseX, mouseY)) {
                screen.setTooltipForNextRenderPass(font().split(tr("rule.enchantment.field.tooltip"), 220));
                return true;
            }
            if (overLevelField(mouseX, mouseY)) {
                screen.setTooltipForNextRenderPass(font().split(tr("rule.level.tooltip"), 220));
                return true;
            }
        }
        return false;
    }

    // desenho

    /**
     * A lista de sugestões do encantamento, por cima de tudo (depois dos widgets): livro, nome (o
     * trecho digitado em destaque) e o id em cinza; "+N" quando sobram.
     */
    void renderSuggestions(GuiGraphics g, int mouseX, int mouseY) {
        if (!suggestionsShown()) {
            return;
        }
        int x = suggestX();
        int y = suggestY();
        int h = suggestH();
        String query = enchantBox.getValue().equals(enchantDisplay()) ? "" : EnchantSearch.fold(enchantBox.getValue().strip());
        int hover = suggestionAt(mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x + 1, y + h, x + IW + 1, y + h + 2, GuiPaint.BEVEL_DARK);
        GuiPaint.box(g, x, y, IW, h, GuiPaint.INSET, GuiPaint.ACCENT);
        for (int i = 0; i < suggestVisible(); i++) {
            int index = suggestTop + i;
            Holder<Enchantment> holder = suggestions.get(index);
            int ry = y + 1 + i * SUGGEST_ROW;
            if (index == suggestSel) {
                g.fill(x + 1, ry, x + IW - 1, ry + SUGGEST_ROW, GuiPaint.mix(GuiPaint.INSET, GuiPaint.ACCENT, 0.22f));
            } else if (index == hover) {
                g.fill(x + 1, ry, x + IW - 1, ry + SUGGEST_ROW, GuiPaint.ROW_HOVER);
            }
            g.pose().pushPose();
            g.pose().translate(x + 3, ry + 1, 0);
            g.pose().scale(0.625f, 0.625f, 1);
            g.renderFakeItem(BOOK, 0, 0);
            g.pose().popPose();
            renderSuggestionText(g, holder, query, x + 16, ry + 2, x + IW - 4);
        }
        int more = suggestions.size() - suggestVisible();
        if (more > 0) {
            GuiText.draw(g, font(), tr("rule.enchantment.more", more), x + 4, y + 1 + suggestVisible() * SUGGEST_ROW + 1,
                    IW - 8, GuiPaint.DISABLED);
        }
        g.pose().popPose();
    }

    /**
     * Nome e id de uma sugestão entre {@code left} e {@code right}: o nome tem a vez, e o id só aparece
     * quando cabe inteiro ao lado dele (id pela metade não ajuda; a dica da linha mostra os dois).
     */
    private void renderSuggestionText(GuiGraphics g, Holder<Enchantment> holder, String query, int left, int ty, int right) {
        Font font = font();
        String name = enchantName(holder);
        ResourceLocation key = holder.unwrapKey().map(k -> k.location()).orElse(null);
        // os do jogo sem o "minecraft:", que não diz nada; os dos mods com o id inteiro
        String id = key == null ? "" : key.getNamespace().equals("minecraft") ? key.getPath() : key.toString();
        int avail = right - left;
        int nameW = font.width(name);
        int idW = font.width(id);
        int gap = 6;
        int nameSpace = Math.min(nameW, avail);
        String folded = EnchantSearch.fold(name);
        int at = query.isEmpty() || folded.length() != name.length() ? -1 : folded.indexOf(query);
        if (at >= 0 && nameW <= nameSpace) {
            int end = at + query.length();
            int cx = left;
            GuiPaint.text(g, font, Component.literal(name.substring(0, at)), cx, ty, GuiPaint.FG);
            cx += font.width(name.substring(0, at));
            GuiPaint.text(g, font, Component.literal(name.substring(at, end)), cx, ty, GuiPaint.ACCENT);
            cx += font.width(name.substring(at, end));
            GuiPaint.text(g, font, Component.literal(name.substring(end)), cx, ty, GuiPaint.FG);
        } else {
            GuiText.draw(g, font, Component.literal(name), left, ty, nameSpace, GuiPaint.FG);
        }
        int idSpace = avail - nameSpace - gap;
        if (!id.isEmpty() && idW <= idSpace) {
            GuiPaint.text(g, font, Component.literal(id), right - idW, ty, GuiPaint.DISABLED);
        }
    }

    /** Nomes das sugestões da lista aberta (todas, não só as cinco à vista); vazia com a lista fechada. */
    List<String> suggestionLabels() {
        return suggestionsShown() ? suggestions.stream().map(EnchantPicker::enchantName).toList() : List.of();
    }
}
