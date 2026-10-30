package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/**
 * Servidor → cliente: o estado do painel da Compactação do Baú aberto, só com o painel aberto e só quando as regras
 * ou as cadeias presentes mudam. Uma linha por cadeia com algum item no Baú, na ordem do índice: as formas (da menor
 * para a maior, ids da registry), o nível de destino ({@link Row#STOPPED} é parada, sem conversão) e se o alvo é
 * próprio da cadeia ou vem do modo global.
 */
public record CompactingStatePayload(int containerId, CompactingMode mode, List<Row> rows) implements CustomPacketPayload {
    /**
     * O teto de linhas: as primeiras na ordem do índice, o resto não aparece. Protege o pacote e o desenho (uma linha
     * leva ~2 bytes por forma mais 2; 512 linhas de até 4 formas ficam abaixo de 6 KB) e o cliente que decodifica.
     */
    public static final int MAX_ROWS = 512;
    /** Formas por linha que o decode aceita (o índice real tem poucas; o teto vale contra pacote forjado). */
    private static final int MAX_FORMS = 16;

    public static final Type<CompactingStatePayload> TYPE = new Type<>(WirelessAutomate.id("compacting_state"));

    /** @param target o nível de destino na cadeia; {@link #STOPPED} é parada */
    public record Row(List<Item> forms, int target, boolean own) {
        public static final int STOPPED = -1;

        /** O alvo vai somado a 1: o −1 da parada seria 5 bytes num VAR_INT. */
        static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list(MAX_FORMS)), Row::forms,
                ByteBufCodecs.VAR_INT.map(shifted -> shifted - 1, level -> level + 1), Row::target,
                ByteBufCodecs.BOOL, Row::own,
                Row::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CompactingStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CompactingStatePayload::containerId,
            ByteBufCodecs.idMapper(index -> CompactingMode.ALL[index], CompactingMode::ordinal), CompactingStatePayload::mode,
            Row.CODEC.apply(ByteBufCodecs.list(MAX_ROWS)), CompactingStatePayload::rows,
            CompactingStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
