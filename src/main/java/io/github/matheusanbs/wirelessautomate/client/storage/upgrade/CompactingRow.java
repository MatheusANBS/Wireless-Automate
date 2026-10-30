package io.github.matheusanbs.wirelessautomate.client.storage.upgrade;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.packet.CompactingStatePayload;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Uma linha do painel da Compactação: uma cadeia com os nomes já medidos, o alvo mostrado e o desenho da linha (o
 * ícone da base, o nome da cadeia, ‹ a forma alvo ›, o nome da forma e a etiqueta Global ou Própria). Com variantes
 * (o mesmo material de mods diferentes), o nome sobe e embaixo dele fica o botão com o nome curto do mod da preferida,
 * que passa para a próxima variante. As posições são relativas ao canto interno do painel.
 */
final class CompactingRow {
    static final int HEIGHT = 20;
    static final int PAD = 4;
    static final int NAME_X = 23;
    static final int NAME_W = 62;
    static final int PREV_X = NAME_X + NAME_W + 2;
    static final int SLOT_X = PREV_X + 10;
    static final int NEXT_X = SLOT_X + 18;
    static final int FORM_X = NEXT_X + 12;
    static final int FORM_W = 36;
    static final int TAG_X = FORM_X + FORM_W + 4;
    static final int TAG_W = 42;
    /** Onde a linha acaba e começa a barra de rolagem. */
    static final int END_X = TAG_X + TAG_W + 2;
    /** O botão do mod, embaixo do nome, nas linhas com variantes. */
    static final int MOD_Y = 8;
    static final int MOD_H = 11;

    /** Os textos iguais em todas as linhas (a parada e as etiquetas), medidos no {@code layout} do painel. */
    record Labels(GuiText.Fitted stopped, GuiText.Fitted global, int globalX, GuiText.Fitted own, int ownX) {
        static Labels measure(Font font) {
            GuiText.Fitted global = GuiText.fit(font, UpgradePanels.tr("compacting.tag.global"), TAG_W - 4);
            GuiText.Fitted own = GuiText.fit(font, UpgradePanels.tr("compacting.tag.own"), TAG_W - 4);
            return new Labels(GuiText.fit(font, UpgradePanels.tr("compacting.stopped"), FORM_W), global,
                    (TAG_W - font.width(global.shown())) / 2, own, (TAG_W - font.width(own.shown())) / 2);
        }
    }

    final Item[] forms;
    final ItemStack[] stacks;
    final @Nullable String[] corners;
    private final GuiText.Fitted[] formNames;
    private final GuiText.Fitted name;
    /** As bases das variantes; com menos de duas, a linha não tem o botão do mod. */
    final Item[] variants;
    private final GuiText.Fitted[] modLabels;
    private final int[] modWidths;
    private final Component[] modTooltips;
    int target;
    boolean own;
    int chosen;

    private CompactingRow(Item[] forms, ItemStack[] stacks, @Nullable String[] corners, GuiText.Fitted[] formNames,
            GuiText.Fitted name, int target, boolean own, Variants variants) {
        this.forms = forms;
        this.stacks = stacks;
        this.corners = corners;
        this.formNames = formNames;
        this.name = name;
        this.target = target;
        this.own = own;
        this.variants = variants.bases;
        this.modLabels = variants.labels;
        this.modWidths = variants.widths;
        this.modTooltips = variants.tooltips;
        this.chosen = variants.chosen;
    }

    /** O botão do mod de cada variante, medido quando o estado chega. */
    private record Variants(Item[] bases, GuiText.Fitted[] labels, int[] widths, Component[] tooltips, int chosen) {
        static final Variants NONE = new Variants(new Item[0], new GuiText.Fitted[0], new int[0], new Component[0], 0);

        static Variants of(CompactingStatePayload.Row row, Font font) {
            int count = row.variants().size();
            if (count < 2) {
                return NONE;
            }
            Item[] bases = row.variants().toArray(new Item[0]);
            GuiText.Fitted[] labels = new GuiText.Fitted[count];
            int[] widths = new int[count];
            Component[] tooltips = new Component[count];
            for (int i = 0; i < count; i++) {
                String namespace = BuiltInRegistries.ITEM.getKey(bases[i]).getNamespace();
                String mod = ModList.get().getModContainerById(namespace)
                        .map(container -> container.getModInfo().getDisplayName()).orElse(namespace);
                labels[i] = GuiText.fit(font, Component.literal(ChainNames.modShort(mod, font::width, NAME_W - 8)),
                        NAME_W - 8);
                widths[i] = font.width(labels[i].shown()) + 8;
                tooltips[i] = Component.translatable("gui.wirelessautomate.upgrade.compacting.variant", mod);
            }
            return new Variants(bases, labels, widths, tooltips, Math.max(0, Math.min(row.chosen(), count - 1)));
        }
    }

    /**
     * Monta a linha de uma cadeia do estado: o nome da cadeia e o de cada forma ({@link ChainNames}). A forma com
     * rótulo de canto se chama pelo rótulo ("1x"); a base de uma escada de compressão, ou a que não tem nada além do
     * nome da cadeia, se chama "base"; {@code null} sem formas.
     */
    static @Nullable CompactingRow of(CompactingStatePayload.Row row, Font font) {
        int count = row.forms().size();
        if (count == 0) {
            return null;
        }
        Item[] forms = row.forms().toArray(new Item[0]);
        ItemStack[] stacks = new ItemStack[count];
        String[] corners = new String[count];
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stacks[i] = new ItemStack(forms[i]);
            corners[i] = ChainNames.corner(BuiltInRegistries.ITEM.getKey(forms[i]).getPath());
            names.add(stacks[i].getHoverName().getString());
        }
        boolean ladder = ChainNames.ladder(corners);
        String[] shorts = ChainNames.shortNames(names);
        Component base = UpgradePanels.tr("compacting.base");
        GuiText.Fitted[] formNames = new GuiText.Fitted[count];
        for (int i = 0; i < count; i++) {
            Component label;
            if (corners[i] != null) {
                label = Component.literal(corners[i]);
            } else if (i == 0 && (ladder || shorts[0].isEmpty())) {
                label = base;
            } else {
                label = shorts[i].isEmpty() ? stacks[i].getHoverName() : Component.literal(shorts[i]);
            }
            formNames[i] = GuiText.fit(font, label, FORM_W);
        }
        GuiText.Fitted name = GuiText.fit(font, Component.literal(ChainNames.chainName(names)), NAME_W);
        return new CompactingRow(forms, stacks, corners, formNames, name, Math.min(row.target(), count - 1), row.own(),
                Variants.of(row, font));
    }

    boolean hasVariants() {
        return variants.length > 1;
    }

    /** O mouse está no botão do mod da linha que começa em {@code ry}. */
    boolean onVariant(double mouseX, double mouseY, int x, int ry) {
        return hasVariants() && within(mouseX, x + NAME_X, modWidths[chosen]) && within(mouseY, ry + MOD_Y, MOD_H);
    }

    /** Passa o botão para a próxima variante e devolve a base dela, para o pedido ao servidor. */
    Item nextVariant() {
        chosen = (chosen + 1) % variants.length;
        return variants[chosen];
    }

    /** O alvo que o modo global dá a esta cadeia. */
    int globalLevel(CompactingMode mode) {
        return switch (mode) {
            case OFF -> CompactingStatePayload.Row.STOPPED;
            case COMPRESS -> forms.length - 1;
            case DECOMPRESS -> 0;
        };
    }

    /** O alvo depois de andar uma forma (‹, › ou a rodinha); da parada, um passo para cima vai à base. −1: não anda. */
    int stepped(int delta) {
        int next = target < 0 ? (delta > 0 ? 0 : -1) : Math.max(0, Math.min(forms.length - 1, target + delta));
        return next == target ? -1 : next;
    }

    static boolean within(double mouse, int from, int size) {
        return mouse >= from && mouse < from + size;
    }

    /** O mouse está na faixa dos botões da linha que começa em {@code ry}, na coluna {@code from}. */
    static boolean on(double mouseX, double mouseY, int x, int ry, int from, int size) {
        return within(mouseX, x + from, size) && within(mouseY, ry + 1, 18);
    }

    void render(GuiGraphics g, Font font, Labels labels, int x, int ry, boolean hovered, boolean picking, int mouseX,
            int mouseY) {
        if (hovered) {
            g.fill(x + 2, ry, x + END_X - 1, ry + HEIGHT, GuiPaint.ROW_HOVER);
        }
        g.renderItem(stacks[0], x + PAD, ry + 2);
        if (hasVariants()) {
            GuiText.draw(g, font, name, x + NAME_X, ry, GuiPaint.FG);
            boolean modHovered = onVariant(mouseX, mouseY, x, ry);
            GuiPaint.pill(g, x + NAME_X, ry + MOD_Y, modWidths[chosen], MOD_H, GuiPaint.PANEL,
                    modHovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.ACCENT);
            GuiText.draw(g, font, modLabels[chosen], x + NAME_X + 4, ry + MOD_Y + 2, GuiPaint.ACCENT);
        } else {
            GuiText.draw(g, font, name, x + NAME_X, ry + 6, GuiPaint.FG);
        }
        stepButton(g, x + PREV_X, ry + 1, target > 0, false, on(mouseX, mouseY, x, ry, PREV_X, 10));
        stepButton(g, x + NEXT_X, ry + 1, target < forms.length - 1, true, on(mouseX, mouseY, x, ry, NEXT_X, 10));
        GuiPaint.slot(g, x + SLOT_X, ry + 1);
        if (target >= 0) {
            g.renderItem(stacks[target], x + SLOT_X + 1, ry + 2);
            g.renderItemDecorations(font, stacks[target], x + SLOT_X + 1, ry + 2, corners[target]);
        }
        if (picking || on(mouseX, mouseY, x, ry, SLOT_X, 18)) {
            GuiPaint.outline(g, x + SLOT_X, ry + 1, 18, 18, GuiPaint.ACCENT);
        }
        GuiText.draw(g, font, target >= 0 ? formNames[target] : labels.stopped(), x + FORM_X, ry + 6,
                target >= 0 ? GuiPaint.FG : GuiPaint.MUTED);
        int tagColor = own ? GuiPaint.ACCENT : GuiPaint.MUTED;
        boolean tagHovered = own && within(mouseX, x + TAG_X, TAG_W) && within(mouseY, ry + 4, 12);
        GuiPaint.pill(g, x + TAG_X, ry + 4, TAG_W, 12, GuiPaint.PANEL, tagHovered ? GuiPaint.BUTTON_HOVER_BORDER : tagColor);
        GuiText.draw(g, font, own ? labels.own() : labels.global(), x + TAG_X + (own ? labels.ownX() : labels.globalX()),
                ry + 6, tagColor);
    }

    /**
     * A dica sob o mouse na linha que começa em {@code ry} (forma alvo, ícone da base, botão do mod, etiqueta), ou
     * {@code null}.
     */
    @Nullable List<Component> tooltip(double mouseX, double mouseY, int x, int ry) {
        if (on(mouseX, mouseY, x, ry, SLOT_X, 18)) {
            Component shown = target >= 0 ? stacks[target].getHoverName() : UpgradePanels.tr("compacting.stopped");
            return List.of(shown, UpgradePanels.tr("compacting.target.hint").withStyle(ChatFormatting.GRAY));
        }
        if (on(mouseX, mouseY, x, ry, PAD, 16)) {
            return List.of(stacks[0].getHoverName());
        }
        if (onVariant(mouseX, mouseY, x, ry)) {
            return List.of(modTooltips[chosen]);
        }
        if (on(mouseX, mouseY, x, ry, TAG_X, TAG_W)) {
            return List.of(UpgradePanels.tr(own ? "compacting.tag.own.tooltip" : "compacting.tag.global.tooltip"));
        }
        return null;
    }

    /** ‹ ou ›: caixa de 10 × 18 com a seta; apagada quando não há forma para aquele lado. */
    private static void stepButton(GuiGraphics g, int bx, int by, boolean enabled, boolean right, boolean hovered) {
        GuiPaint.box(g, bx, by, 10, 18, GuiPaint.BUTTON,
                enabled && hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        int color = enabled ? GuiPaint.FG : GuiPaint.DISABLED;
        if (right) {
            GuiPaint.arrowRight(g, bx + 4, by + 6, color);
        } else {
            g.fill(bx + 5, by + 6, bx + 6, by + 11, color);
            g.fill(bx + 4, by + 7, bx + 5, by + 10, color);
            g.fill(bx + 3, by + 8, bx + 4, by + 9, color);
        }
    }
}
