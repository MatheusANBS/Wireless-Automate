package io.github.matheusanbs.wirelessautomate.packet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvCodecs;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: os componentes e os temas do jogador, só com o editor da Tela de Sinal aberto (na abertura e
 * depois de cada ação). Vai como NBT em bytes; o teto fica abaixo do limite de 1 MiB de um payload para o cliente.
 */
public record TvLibraryPayload(int containerId, List<TvComponent> components, List<TvTheme> themes)
        implements CustomPacketPayload {
    public static final int MAX_BYTES = 900 * 1024;
    public static final Type<TvLibraryPayload> TYPE = new Type<>(WirelessAutomate.id("tv_library"));
    private static final StreamCodec<ByteBuf, byte[]> BYTES = ByteBufCodecs.byteArray(MAX_BYTES);
    private static final Codec<TvLibraryPayload> BODY = RecordCodecBuilder.create(i -> i.group(
            TvComponent.CODEC.listOf().fieldOf("components").forGetter(TvLibraryPayload::components),
            TvCodecs.THEME.listOf().fieldOf("themes").forGetter(TvLibraryPayload::themes))
            .apply(i, (components, themes) -> new TvLibraryPayload(0, components, themes)));

    public static final StreamCodec<RegistryFriendlyByteBuf, TvLibraryPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.containerId);
                BYTES.encode(buf, TvNbtBytes.encode(BODY, payload));
            },
            buf -> {
                int containerId = ByteBufCodecs.VAR_INT.decode(buf);
                TvLibraryPayload body = TvNbtBytes.decode(BODY, BYTES.decode(buf), MAX_BYTES);
                return new TvLibraryPayload(containerId, body.components, body.themes);
            });

    public TvLibraryPayload {
        components = List.copyOf(components);
        themes = List.copyOf(themes);
    }

    /** Quantos bytes o payload ocupa; o servidor confere antes de mandar. */
    public int encodedSize() {
        return TvNbtBytes.encode(BODY, this).length;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
