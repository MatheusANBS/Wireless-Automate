package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockEntity;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;

/**
 * Filtro de entrada de um armazenamento por tipo (Baú, Tanque, Tanque Químico): o que pode entrar
 * nele, por qualquer caminho. Mesma tela de filtro do roteador, do tipo do recurso guardado; o
 * "estoque" de uma entrada vira "guardar até N". Voltar reabre a tela do armazenamento.
 */
public record StorageFilterTarget(StorageBlockEntity storage) implements FilterTarget {
    /** Abre a tela de filtro do armazenamento para o jogador. */
    public void open(ServerPlayer player) {
        FilterView view = view(player);
        Component title = Component.translatable("container.wirelessautomate.filter.chest",
                storage.getBlockState().getBlock().getName());
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new FilterMenu(containerId, inventory, this, view), title),
                buf -> FilterView.STREAM_CODEC.encode(buf, view));
    }

    @Override
    public ResourceType type() {
        return storage.kind().resource;
    }

    @Override
    public Filter filter() {
        return storage.filter();
    }

    @Override
    public void setFilter(Filter filter) {
        storage.setFilter(filter);
    }

    @Override
    public boolean stillValid(Player player) {
        return StorageListMenu.stillValid(storage, player);
    }

    @Override
    public int version() {
        return storage.filterVersion();
    }

    /** Com a posição e sem face: a tela mostra o nome do bloco e o botão Voltar. */
    @Override
    public FilterView view(Player player) {
        return new FilterView(type(), Optional.of(storage.getBlockPos()), Optional.empty(), filter(),
                FilterCardItem.isCard(player.getMainHandItem()));
    }
}
