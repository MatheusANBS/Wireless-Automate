package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: a lista de acesso da Tela de Sinal depois de uma ação do dono ({@link TvAccessActionPayload}),
 * aceita ou recusada, com o aviso para a aba Acesso. Só a quem mandou a ação, com o menu aberto.
 *
 * @param notice o fim da chave do aviso ({@code gui.wirelessautomate.tv.access.notice.<notice>}); vazio, nenhum
 * @param arg    o nome que o aviso mostra (o jogador ou o time); vazio se não há
 */
public record TvAccessPayload(int containerId, AccessView access, String notice, String arg)
        implements CustomPacketPayload {
    public static final Type<TvAccessPayload> TYPE = new Type<>(WirelessAutomate.id("tv_access"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TvAccessPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.containerId);
                payload.access.write(buf);
                buf.writeUtf(payload.notice, 32);
                buf.writeUtf(payload.arg, 64);
            },
            buf -> new TvAccessPayload(buf.readVarInt(), AccessView.read(buf), buf.readUtf(32), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
