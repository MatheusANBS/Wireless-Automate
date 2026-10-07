package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Filtro de uma face (absoluta) de um roteador, para um tipo. Grava pelo
 * {@link RouterBlockEntity#setFilter}, que salva e avisa o motor.
 */
public record RouterFaceFilterTarget(RouterBlockEntity router, ResourceType type, Direction face)
        implements FilterTarget {
    public RouterFaceFilterTarget {
        if (type != ResourceType.ITEM && type != ResourceType.FLUID) {
            throw new IllegalArgumentException("Filtro de " + type);
        }
    }

    /** Abre a tela de filtro desta face para o jogador. */
    public void open(ServerPlayer player) {
        FilterView view = view(player);
        Component title = Component.translatable("container.wirelessautomate.filter.face",
                FilterCardItem.typeName(type),
                Component.translatable("gui.wirelessautomate.router.face." + face.getName()));
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new FilterMenu(containerId, inventory, this, view), title),
                buf -> FilterView.STREAM_CODEC.encode(buf, view));
    }

    @Override
    public Filter filter() {
        return router.face(type, face).filter();
    }

    @Override
    public void setFilter(Filter filter) {
        router.setFilter(type, face, filter);
    }

    /** A mesma regra do {@link RouterMenu}: roteador no mundo, mesma dimensão e até 8 blocos. */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(router, player);
    }

    static boolean stillValid(RouterBlockEntity router, Player player) {
        if (router.isRemoved() || router.getLevel() != player.level()
                || player.level().getBlockEntity(router.getBlockPos()) != router) {
            return false;
        }
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(router.getBlockPos()))
                <= RouterMenu.MAX_DISTANCE * RouterMenu.MAX_DISTANCE;
    }

    @Override
    public int version() {
        return router.changeVersion();
    }

    @Override
    public FilterView view(Player player) {
        return new FilterView(type, Optional.of(router.getBlockPos()), Optional.of(face), filter(),
                FilterCardItem.isCard(player.getMainHandItem()));
    }
}
