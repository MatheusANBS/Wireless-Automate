package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: uma edição no filtro da tela aberta. Os campos usados dependem de {@link #op}:
 * <ul>
 *   <li>{@code ADD_TAG} / {@code ADD_MOD}: {@code text} sem o prefixo ({@code c:ingots}, {@code mekanism})</li>
 *   <li>{@code REMOVE}: {@code index} da entrada</li>
 *   <li>{@code SET_STOCK}: {@code index} e {@code value} (0 = sem estoque)</li>
 *   <li>{@code SET_LIST_MODE}: {@code value} 0 = lista branca, 1 = negra</li>
 *   <li>{@code SET_COMPONENTS}: {@code value} 0 = ignorar, 1 = exigir iguais</li>
 *   <li>{@code CLEAR}, {@code IMPORT_CARD}, {@code EXPORT_CARD}, {@code BACK}: nenhum
 *       ({@code BACK} volta para a tela do roteador)</li>
 * </ul>
 * Adicionar do inventário não usa este payload: é o Shift + clique do {@code FilterMenu}. Do JEI
 * (sem ter o item) é o {@link AddFilterEntryPayload}.
 */
public record EditFilterPayload(int containerId, Op op, int index, long value, String text)
        implements CustomPacketPayload {
    public static final int MAX_TEXT = 128;

    public enum Op {
        ADD_TAG,
        ADD_MOD,
        REMOVE,
        SET_STOCK,
        SET_LIST_MODE,
        SET_COMPONENTS,
        CLEAR,
        IMPORT_CARD,
        EXPORT_CARD,
        BACK
    }

    public static EditFilterPayload of(int containerId, Op op) {
        return new EditFilterPayload(containerId, op, 0, 0, "");
    }

    public static final Type<EditFilterPayload> TYPE = new Type<>(WirelessAutomate.id("edit_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditFilterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EditFilterPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(Op.class), EditFilterPayload::op,
            ByteBufCodecs.VAR_INT, EditFilterPayload::index,
            ByteBufCodecs.VAR_LONG, EditFilterPayload::value,
            ByteBufCodecs.stringUtf8(MAX_TEXT), EditFilterPayload::text,
            EditFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
