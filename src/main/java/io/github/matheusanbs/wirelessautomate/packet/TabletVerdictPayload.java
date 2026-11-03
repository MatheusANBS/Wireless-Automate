package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.Verdict;
import io.github.matheusanbs.wirelessautomate.network.VerdictCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: o veredito da máquina escolhida na aba Máquina do Tablet. Só sai com a aba aberta, uma vez por
 * segundo no máximo e só quando mudou. O cliente só o usa se o nó e a máquina batem com a escolha dele.
 *
 * @param node    o nó a que o veredito se refere; vazio = nenhum nó escolhido
 * @param machine a máquina remota; vazio = a presa
 * @param verdict o veredito; vazio quando não há máquina escolhida
 */
public record TabletVerdictPayload(int containerId, Optional<NodeKey> node, Optional<BlockPos> machine,
        Optional<Verdict> verdict) implements CustomPacketPayload {
    public static final Type<TabletVerdictPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_verdict"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletVerdictPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletVerdictPayload::containerId,
            ByteBufCodecs.optional(NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()), TabletVerdictPayload::node,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), TabletVerdictPayload::machine,
            ByteBufCodecs.optional(VerdictCodec.STREAM_CODEC), TabletVerdictPayload::verdict,
            TabletVerdictPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
