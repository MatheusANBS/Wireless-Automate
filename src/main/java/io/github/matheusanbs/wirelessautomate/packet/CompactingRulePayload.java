package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/**
 * Cliente → servidor: o painel da Compactação do Baú aberto. {@code WATCH}/{@code UNWATCH} abrem e fecham o painel
 * (o servidor só manda o estado com ele aberto); {@code MODE} troca o modo global ({@code mode} é o ordinal do
 * {@code CompactingMode}); {@code TARGET} dá à cadeia do {@code item} esse item como alvo e {@code CLEAR} tira o alvo
 * próprio da cadeia (qualquer forma dela); {@code PREFER} faz da cadeia cuja forma base é o {@code item} a variante
 * preferida do grupo dela. O alvo vai pelo item, não pelo nível nem pelo id da cadeia: depois de um
 * {@code /reload} o nível e o id podem apontar para outra coisa, o item não. O servidor confere o menu aberto, o
 * upgrade e a cadeia (e, no {@code PREFER}, o grupo).
 */
public record CompactingRulePayload(int containerId, Kind kind, int mode, Optional<Item> item)
        implements CustomPacketPayload {
    public enum Kind {
        WATCH,
        UNWATCH,
        MODE,
        TARGET,
        CLEAR,
        PREFER;

        static final Kind[] ALL = values();
    }

    public static final Type<CompactingRulePayload> TYPE = new Type<>(WirelessAutomate.id("compacting_rule"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompactingRulePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CompactingRulePayload::containerId,
            ByteBufCodecs.idMapper(index -> Kind.ALL[index], Kind::ordinal), CompactingRulePayload::kind,
            ByteBufCodecs.VAR_INT, CompactingRulePayload::mode,
            ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.ITEM)), CompactingRulePayload::item,
            CompactingRulePayload::new);

    public static CompactingRulePayload of(int containerId, Kind kind) {
        return new CompactingRulePayload(containerId, kind, 0, Optional.empty());
    }

    public static CompactingRulePayload mode(int containerId, int mode) {
        return new CompactingRulePayload(containerId, Kind.MODE, mode, Optional.empty());
    }

    public static CompactingRulePayload target(int containerId, Kind kind, Item item) {
        return new CompactingRulePayload(containerId, kind, 0, Optional.of(item));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
