package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.linker.LinkerOpen;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: vários roteadores chegam na máquina que o Vinculador (modo Abrir) clicou; o cliente
 * mostra a lista e a escolha volta no {@link LinkerOpenPickPayload}.
 *
 * @param machine a máquina clicada
 * @param choices os roteadores, no máximo {@link LinkerOpen#MAX_CHOICES}
 */
public record LinkerOpenListPayload(BlockPos machine, List<LinkerOpen.Choice> choices) implements CustomPacketPayload {
    public static final Type<LinkerOpenListPayload> TYPE = new Type<>(WirelessAutomate.id("linker_open_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerOpenListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, LinkerOpenListPayload::machine,
                    LinkerOpen.Choice.STREAM_CODEC.apply(ByteBufCodecs.list(LinkerOpen.MAX_CHOICES)),
                    LinkerOpenListPayload::choices,
                    LinkerOpenListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
