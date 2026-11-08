package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * O ícone de cada grupo da lista das máquinas e da pílula do alvo: o item do bloco do grupo, criado uma vez por
 * snapshot e não a cada quadro. Um snapshot novo (outra {@link RangeView}) começa do zero; sair do mundo esquece a
 * última visão e os ícones (o mapa é estático).
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class GroupIcons {
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

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        view = RangeView.EMPTY;
        ICONS.clear();
    }
}
