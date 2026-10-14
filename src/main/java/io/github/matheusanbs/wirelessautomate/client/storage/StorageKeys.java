package io.github.matheusanbs.wirelessautomate.client.storage;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * O que muda de um armazenamento para outro num tipo da lista (item, fluido ou químico, pela
 * {@link io.github.matheusanbs.wirelessautomate.menu.ListKind}): o nome, o mod, o texto da busca por
 * {@code #} e o desenho na célula.
 */
final class StorageKeys {
    private StorageKeys() {
    }

    /** Nome do tipo para mostrar: o do item, o do fluido ou o do químico. */
    static Component displayName(Object key) {
        return switch (key) {
            case ItemStack stack -> stack.getHoverName();
            case FluidStack fluid -> fluid.getHoverName();
            case ResourceLocation id -> Chemicals.name(id);
            default -> Component.literal(String.valueOf(key));
        };
    }

    /** Mod do tipo, para a busca {@code @mod} e a ordem por mod. */
    static String namespace(Object key) {
        return switch (key) {
            case ItemStack stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
            case FluidStack fluid -> BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getNamespace();
            case ResourceLocation id -> id.getNamespace();
            default -> "";
        };
    }

    /** As tags do tipo ({@code c:ingots/iron}) e as linhas do tooltip, em minúsculas, uma por linha. */
    static String searchText(Object key) {
        Minecraft minecraft = Minecraft.getInstance();
        StringBuilder text = new StringBuilder();
        Stream<? extends TagKey<?>> tags = switch (key) {
            case ItemStack stack -> stack.getTags();
            case FluidStack fluid -> fluid.getFluidHolder().tags();
            default -> Stream.empty();
        };
        tags.forEach(tag -> text.append(tag.location()).append('\n'));
        if (key instanceof ItemStack stack && minecraft != null) {
            try {
                for (Component line : stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player,
                        TooltipFlag.Default.NORMAL)) {
                    text.append(line.getString()).append('\n');
                }
            } catch (RuntimeException e) {
                // tooltip de outro mod que falha fora do lugar de costume: fica só com as tags
            }
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    /** O tipo na célula: o item, ou a textura do fluido ou do químico tingida. */
    static void renderKey(GuiGraphics g, Font font, Object key, int x, int y, int trim) {
        switch (key) {
            case ItemStack stack -> g.renderItem(stack, x, y);
            case FluidStack fluid -> GuiPaint.fluid(g, fluid, x, y);
            case ResourceLocation id -> GuiPaint.chemical(g, font, id, x, y, trim);
            default -> {
            }
        }
    }
}
