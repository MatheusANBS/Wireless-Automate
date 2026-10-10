package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.NeoForgeStreamCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * O que a tela de filtro mostra. Filtro de uma face de roteador ({@code router} e {@code face}
 * presentes), o de entrada de um Baú ({@code router} com a posição do Baú, sem {@code face}) ou de
 * um Cartão de Filtro (os dois vazios).
 *
 * @param type     ITEM ou FLUID (energia não usa filtro)
 * @param hasCard  o jogador tem um Cartão de Filtro na mão principal (habilita importar/exportar)
 */
public record FilterView(ResourceType type, Optional<BlockPos> router, Optional<Direction> face, Filter filter,
        boolean hasCard) {
    public boolean isCard() {
        return router.isEmpty();
    }

    /**
     * A mesma visão sem as entradas do filtro: vai na abertura da tela quando a completa passa do teto do Forge
     * ({@code ServerMenus}); as entradas chegam logo depois num {@code FilterViewPayload}.
     */
    public FilterView reduced() {
        return new FilterView(type, router, face, filter.cleared(), hasCard);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterView> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), FilterView::type,
            ByteBufCodecs.optional(GameCodecs.BLOCK_POS), FilterView::router,
            ByteBufCodecs.optional(GameCodecs.DIRECTION), FilterView::face,
            Filter.STREAM_CODEC, FilterView::filter,
            ByteBufCodecs.BOOL, FilterView::hasCard,
            FilterView::new);
}
