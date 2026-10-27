package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: os números de uma Tela de Sinal, um por ligação distinta do layout (na ordem de
 * {@code TvLayout.bindings()}), {@code NaN} onde a rede some ou o dono não a pode usar. Só sai para quem está perto
 * e quando os números mudam; {@code revision} é a do layout que o servidor leu, para o cliente descartar números de
 * um layout velho. {@code stale} marca (um bit por ligação) o último número lido de um armazenamento num chunk
 * descarregado, que o cliente pinta em Apagado; vazio sem nenhum.
 */
public record TvValuesPayload(BlockPos pos, int revision, double[] values, long[] stale)
        implements CustomPacketPayload {
    /** O máximo de palavras de bits (uma por 64 ligações; a config dá até 256 ligações). */
    private static final int MAX_STALE_WORDS = 4;

    public static final Type<TvValuesPayload> TYPE = new Type<>(WirelessAutomate.id("tv_values"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TvValuesPayload> STREAM_CODEC =
            StreamCodec.of(TvValuesPayload::encode, TvValuesPayload::decode);

    public TvValuesPayload(BlockPos pos, int revision, double[] values) {
        this(pos, revision, values, new long[0]);
    }

    private static void encode(RegistryFriendlyByteBuf buf, TvValuesPayload payload) {
        BlockPos.STREAM_CODEC.encode(buf, payload.pos);
        ByteBufCodecs.VAR_INT.encode(buf, payload.revision);
        ByteBufCodecs.VAR_INT.encode(buf, payload.values.length);
        for (double value : payload.values) {
            buf.writeDouble(value);
        }
        ByteBufCodecs.VAR_INT.encode(buf, payload.stale.length);
        for (long word : payload.stale) {
            buf.writeLong(word);
        }
    }

    private static TvValuesPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
        int revision = ByteBufCodecs.VAR_INT.decode(buf);
        int size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size < 0 || size > buf.readableBytes() / Double.BYTES) {
            throw new IllegalArgumentException("tamanho inválido dos valores da Tela: " + size);
        }
        double[] values = new double[size];
        for (int i = 0; i < size; i++) {
            values[i] = buf.readDouble();
        }
        int words = ByteBufCodecs.VAR_INT.decode(buf);
        if (words < 0 || words > MAX_STALE_WORDS) {
            throw new IllegalArgumentException("palavras de dado antigo inválidas: " + words);
        }
        long[] stale = new long[words];
        for (int i = 0; i < words; i++) {
            stale[i] = buf.readLong();
        }
        return new TvValuesPayload(pos, revision, values, stale);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TvValuesPayload p && pos.equals(p.pos) && revision == p.revision
                && Arrays.equals(values, p.values) && Arrays.equals(stale, p.stale);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * pos.hashCode() + revision) + Arrays.hashCode(values)) + Arrays.hashCode(stale);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
