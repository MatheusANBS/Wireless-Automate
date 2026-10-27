package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * O espaço do item do Ícone: uma caixa com o item escolhido e, ao lado, o nome dele (ou a dica de largar um item do
 * JEI ali, que é a única forma de escolher). O clique tira o item. É o alvo do arrasto do JEI
 * ({@link TvPropsPanel#ghostBounds}). O {@code ItemStack} e os textos são refeitos só quando o item muda.
 */
final class TvItemSlot extends TvControl {
    private static final int SLOT = 22;

    private final Font font;
    private final Supplier<Optional<ResourceLocation>> selected;
    private final Runnable clear;
    private final GuiText.Fitted drop;
    private final GuiText.Fitted clickToClear;
    private @Nullable ResourceLocation shown;
    private ItemStack stack = ItemStack.EMPTY;
    private @Nullable GuiText.Fitted name;

    TvItemSlot(Font font, String id, int x, int y, Supplier<Optional<ResourceLocation>> selected, Runnable clear) {
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
        ResourceLocation now = selected.get().orElse(null);
        if (java.util.Objects.equals(now, shown) && (now == null || name != null)) {
            return;
        }
        shown = now;
        Item item = now == null ? Items.AIR : BuiltInRegistries.ITEM.get(now);
        stack = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        name = stack.isEmpty() ? null : GuiText.fit(font, stack.getHoverName(), TvForm.W - SLOT - 6);
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        GuiPaint.inset(g, ax, ay, SLOT, SLOT);
        if (contains(mouseX, mouseY) && !stack.isEmpty()) {
            g.fill(ax + 1, ay + 1, ax + SLOT - 1, ay + SLOT - 1, GuiPaint.ROW_HOVER);
        }
        if (stack.isEmpty()) {
            GuiText.draw(g, font, drop, ax + SLOT + 4, ay + (SLOT - 8) / 2, GuiPaint.MUTED);
            return;
        }
        g.renderItem(stack, ax + 3, ay + 3);
        GuiText.draw(g, font, name, ax + SLOT + 4, ay + 3, GuiPaint.FG);
        GuiText.draw(g, font, clickToClear, ax + SLOT + 4, ay + 13, GuiPaint.MUTED);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (!stack.isEmpty()) {
            clear.run();
        }
        return true;
    }

    @Override
    @Nullable Component tooltip(double mouseX, double mouseY) {
        return stack.isEmpty() ? null : stack.getHoverName();
    }
}
