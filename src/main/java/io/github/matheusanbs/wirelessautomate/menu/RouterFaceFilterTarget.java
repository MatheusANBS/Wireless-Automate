package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Filtro de uma face de um roteador, para um tipo, no alvo de edição da tela ({@code target}): a face
 * absoluta da máquina presa, ou a face canônica do perfil de um grupo ou de uma máquina remota. Grava
 * pelo {@link RouterBlockEntity#setFilter(EditTarget, ResourceType, Direction, Filter)}, que salva e
 * avisa o motor. Um alvo remoto que deixou de existir fecha a tela ({@link #stillValid}) e não grava.
 */
public record RouterFaceFilterTarget(RouterBlockEntity router, ResourceType type, Direction face,
        EditTargetRef target) implements FilterTarget {
    public RouterFaceFilterTarget {
        if (!hasFilter(type)) {
            throw new IllegalArgumentException("Filtro de " + type);
        }
    }

    /** Filtro de uma face (absoluta) da máquina presa. */
    public RouterFaceFilterTarget(RouterBlockEntity router, ResourceType type, Direction face) {
        this(router, type, face, EditTargetRef.ATTACHED);
    }

    /** O alvo conferido no roteador, ou {@code null} se ele deixou de existir. */
    private @Nullable EditTarget current() {
        return target.current(router);
    }

    /** O tipo tem filtro (pelo registro) e existe nesta instância. */
    public static boolean hasFilter(ResourceType type) {
        return type.filtered() && LoadedTypes.contains(type);
    }

    /** Abre a tela de filtro desta face para o jogador. */
    public void open(ServerPlayer player) {
        FilterView view = view(player);
        Component side = target.target().attached()
                ? Component.translatable("gui.wirelessautomate.router.face." + face.getName())
                : Component.translatable("gui.wirelessautomate.router.side."
                        + RemoteSide.fromCanonical(face.ordinal()).key());
        Component title = Component.translatable("container.wirelessautomate.filter.face",
                FilterCardItem.typeName(type), side);
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new FilterMenu(containerId, inventory, this, view), title),
                buf -> FilterView.STREAM_CODEC.encode(buf, view));
    }

    @Override
    public Filter filter() {
        EditTarget current = current();
        return current == null ? Filter.EMPTY : router.face(current, type, face).filter();
    }

    @Override
    public void setFilter(Filter filter) {
        EditTarget current = current();
        if (current != null) {
            router.setFilter(current, type, face, filter);
        }
    }

    /** A mesma regra do {@link RouterMenu}: roteador no mundo, mesma dimensão e até 8 blocos. */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(router, player) && current() != null;
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
