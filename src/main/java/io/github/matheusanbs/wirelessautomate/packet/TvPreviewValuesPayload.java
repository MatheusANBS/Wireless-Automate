package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: os números das ligações que o editor pediu no {@link TvPreviewPayload} de mesma
 * {@code version}, na ordem dela, {@code NaN} onde quem edita não pode usar a rede. Só para quem edita, só com o
 * editor aberto e só quando os números mudam.
 */
public record TvPreviewValuesPayload(int containerId, int version, double[] values) implements CustomPacketPayload {
    public static final Type<TvPreviewValuesPayload> TYPE = new Type<>(WirelessAutomate.id("tv_preview_values"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TvPreviewValuesPayload> STREAM_CODEC =
            StreamCodec.of(TvPreviewValuesPayload::encode, TvPreviewValuesPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TvPreviewValuesPayload payload) {
        ByteBufCodecs.VAR_INT.encode(buf, payload.containerId);
        ByteBufCodecs.VAR_INT.encode(buf, payload.version);
        ByteBufCodecs.VAR_INT.encode(buf, payload.values.length);
        for (double value : payload.values) {
            buf.writeDouble(value);
        }
    }

    private static TvPreviewValuesPayload decode(RegistryFriendlyByteBuf buf) {
        int containerId = ByteBufCodecs.VAR_INT.decode(buf);
        int version = ByteBufCodecs.VAR_INT.decode(buf);
        int size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size < 0 || size > TvPreviewPayload.MAX_BINDINGS || size > buf.readableBytes() / Double.BYTES) {
            throw new IllegalArgumentException("tamanho inválido da prévia da Tela: " + size);
        }
        double[] values = new double[size];
        for (int i = 0; i < size; i++) {
            values[i] = buf.readDouble();
        }
        return new TvPreviewValuesPayload(containerId, version, values);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TvPreviewValuesPayload p && containerId == p.containerId && version == p.version
                && Arrays.equals(values, p.values);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * containerId + version) + Arrays.hashCode(values);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
