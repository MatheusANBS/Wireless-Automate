package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: na página do Tablet aberto só mudaram o estado e a vazão dos nós e das linhas de máquina (o caso
 * de quase toda amostra): vão só eles, na ordem da página, e o cliente os põe na página que já tem.
 */
public record TabletLivePayload(int containerId, List<TabletSnapshot.Live> lives, List<TabletRows.MachineLive> machines)
        implements CustomPacketPayload {
    public static final Type<TabletLivePayload> TYPE = new Type<>(WirelessAutomate.id("tablet_live"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletLivePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletLivePayload::containerId,
            TabletSnapshot.LIVE_CODEC, TabletLivePayload::lives,
            TabletRows.LIVE_CODEC, TabletLivePayload::machines,
            TabletLivePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
