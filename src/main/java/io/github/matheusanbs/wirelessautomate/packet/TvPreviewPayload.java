package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor, com o editor da Tela de Sinal aberto: as ligações do rascunho, para o servidor mandar os
 * números delas antes de salvar ({@link TvPreviewValuesPayload}). {@code version} sobe a cada lista nova e volta na
 * resposta; a lista vazia para a prévia. Só sai quando as ligações do rascunho mudam.
 */
public record TvPreviewPayload(int containerId, int version, List<TvBinding> bindings)
        implements CustomPacketPayload {
    public static final int MAX_BINDINGS = 256;
    public static final Type<TvPreviewPayload> TYPE = new Type<>(WirelessAutomate.id("tv_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TvPreviewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TvPreviewPayload::containerId,
            ByteBufCodecs.VAR_INT, TvPreviewPayload::version,
            ByteBufCodecs.fromCodec(TvBinding.CODEC).apply(ByteBufCodecs.list(MAX_BINDINGS)),
            TvPreviewPayload::bindings,
            TvPreviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
