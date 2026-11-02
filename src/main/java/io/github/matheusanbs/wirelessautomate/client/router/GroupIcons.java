package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * O ícone de cada grupo da lista das máquinas e da pílula do alvo: o item do bloco do grupo, criado uma vez por
 * snapshot e não a cada quadro. Um snapshot novo (outra {@link RangeView}) começa do zero.
 */
final class GroupIcons {
    private static RangeView view = RangeView.EMPTY;
    private static final Map<ResourceLocation, ItemStack> ICONS = new HashMap<>();

    private GroupIcons() {
    }

    /** O item do bloco do grupo ({@link ItemStack#EMPTY} se o bloco não tem item). Não altere a pilha devolvida. */
    static ItemStack of(RangeView range, RangeView.GroupView group) {
        if (range != view) {
            view = range;
            ICONS.clear();
        }
        return ICONS.computeIfAbsent(group.block(), GroupIcons::create);
    }

    private static ItemStack create(ResourceLocation block) {
        return new ItemStack(BuiltInRegistries.BLOCK.get(block).asItem());
    }
}
