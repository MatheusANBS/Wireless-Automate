package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Cliente → servidor: o "+" do JEI na Mesa de Trabalho do Baú. {@code slots} tem uma lista de alternativas por slot
 * da grade 3×3 (de cima para baixo, da esquerda para a direita; lista vazia é slot vazio), até {@link #MAX_ALTERNATIVES}
 * pilhas de 1 em cada; {@code maxTransfer} é o Shift do "+" (encher cada slot em vez de pôr 1). O servidor confere o
 * menu aberto e a Mesa, e decide as quantidades sozinho.
 */
public record ChestCraftFillPayload(int containerId, List<List<ItemStack>> slots, boolean maxTransfer)
        implements CustomPacketPayload {
    /** Alternativas por slot que o pacote leva. */
    public static final int MAX_ALTERNATIVES = 16;
    /** Slots da grade. */
    public static final int SLOTS = 9;

    public static final Type<ChestCraftFillPayload> TYPE = new Type<>(WirelessAutomate.id("chest_craft_fill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChestCraftFillPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChestCraftFillPayload::containerId,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ALTERNATIVES)).apply(ByteBufCodecs.list(SLOTS)),
            ChestCraftFillPayload::slots,
            ByteBufCodecs.BOOL, ChestCraftFillPayload::maxTransfer,
            ChestCraftFillPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
