package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * O que a tela de filtro mostra. Filtro de uma face de roteador ({@code router} e {@code face}
 * presentes) ou de um Cartão de Filtro (os dois vazios).
 *
 * @param type     ITEM ou FLUID (energia não usa filtro)
 * @param hasCard  o jogador tem um Cartão de Filtro na mão principal (habilita importar/exportar)
 */
public record FilterView(ResourceType type, Optional<BlockPos> router, Optional<Direction> face, Filter filter,
        boolean hasCard) {
    public boolean isCard() {
        return router.isEmpty();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterView> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), FilterView::type,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), FilterView::router,
            ByteBufCodecs.optional(Direction.STREAM_CODEC), FilterView::face,
            Filter.STREAM_CODEC, FilterView::filter,
            ByteBufCodecs.BOOL, FilterView::hasCard,
            FilterView::new);
}
