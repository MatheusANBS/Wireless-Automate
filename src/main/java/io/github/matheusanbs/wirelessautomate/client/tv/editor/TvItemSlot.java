package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.TvIcons;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O espaço do Ícone: uma caixa com o item, o fluido ou o químico escolhido e, ao lado, o nome dele (ou a dica de largar um ingrediente do
 * JEI ali, que é a única forma de escolher). O clique tira o item. É o alvo do arrasto do JEI
 * ({@link TvPropsPanel#ghostBounds}). O {@code ItemStack} e os textos são refeitos só quando o item muda.
 */
final class TvItemSlot extends TvControl {
    private static final int SLOT = 22;

    private final Font font;
    private final Supplier<TvPartStyle> selected;
    private final Runnable clear;
    private final GuiText.Fitted drop;
    private final GuiText.Fitted clickToClear;
    private @Nullable TvPartStyle shown;
    private boolean has;
    private @Nullable GuiText.Fitted name;

    TvItemSlot(Font font, String id, int x, int y, Supplier<TvPartStyle> selected, Runnable clear) {
        super(id, x, y, TvForm.W, SLOT);
        this.font = font;
        this.selected = selected;
        this.clear = clear;
        int textW = TvForm.W - SLOT - 6;
        this.drop = GuiText.fit(font, TvPropsCtx.key("data.icon.drop"), textW);
        this.clickToClear = GuiText.fit(font, TvPropsCtx.key("data.icon.clear"), textW);
        refresh();
    }

    @Override
    void refresh() {
        TvPartStyle now = selected.get();
        if (now.equals(shown) && (!has || name != null)) {
            return;
        }
        shown = now;
        has = now.item().isPresent() && TvIcons.present(now);
        name = has ? GuiText.fit(font, TvIcons.name(now), TvForm.W - SLOT - 6) : null;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        GuiPaint.inset(g, ax, ay, SLOT, SLOT);
        if (contains(mouseX, mouseY) && has) {
            g.fill(ax + 1, ay + 1, ax + SLOT - 1, ay + SLOT - 1, GuiPaint.ROW_HOVER);
        }
        if (!has) {
            GuiText.draw(g, font, drop, ax + SLOT + 4, ay + (SLOT - 8) / 2, GuiPaint.MUTED);
            return;
        }
        ItemStack stack = TvIcons.stack(shown);
        if (stack.isEmpty()) {
            TvIcons.drawFlat(g, font, shown, ax + 3, ay + 3);
        } else {
            g.renderItem(stack, ax + 3, ay + 3);
        }
        GuiText.draw(g, font, name, ax + SLOT + 4, ay + 3, GuiPaint.FG);
        GuiText.draw(g, font, clickToClear, ax + SLOT + 4, ay + 13, GuiPaint.MUTED);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (has) {
            clear.run();
        }
        return true;
    }

    @Override
    @Nullable Component tooltip(double mouseX, double mouseY) {
        return has ? TvIcons.name(shown) : null;
    }
}
