package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;

/**
 * Filtro de entrada de um Baú: o que pode entrar nele, por qualquer caminho. Mesma tela de filtro
 * do roteador; o "estoque" de uma entrada vira "guardar até N deste item". Voltar reabre a tela do
 * Baú.
 */
public record StorageChestFilterTarget(StorageChestBlockEntity chest) implements FilterTarget {
    /** Abre a tela de filtro do Baú para o jogador. */
    public void open(ServerPlayer player) {
        FilterView view = view(player);
        Component title = Component.translatable("container.wirelessautomate.filter.chest",
                chest.getBlockState().getBlock().getName());
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new FilterMenu(containerId, inventory, this, view), title),
                buf -> FilterView.STREAM_CODEC.encode(buf, view));
    }

    @Override
    public ResourceType type() {
        return ResourceType.ITEM;
    }

    @Override
    public Filter filter() {
        return chest.filter();
    }

    @Override
    public void setFilter(Filter filter) {
        chest.setFilter(filter);
    }

    @Override
    public boolean stillValid(Player player) {
        return StorageChestMenu.stillValid(chest, player);
    }

    @Override
    public int version() {
        return chest.filterVersion();
    }

    /** Com a posição e sem face: a tela mostra "Baú Wireless" e o botão Voltar. */
    @Override
    public FilterView view(Player player) {
        return new FilterView(ResourceType.ITEM, Optional.of(chest.getBlockPos()), Optional.empty(), filter(),
                FilterCardItem.isCard(player.getMainHandItem()));
    }
}
