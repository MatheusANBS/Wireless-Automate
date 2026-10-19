package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.PanelAccess;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: o painel do nó escolhido no Tablet aberto. {@code node} é o nó a que ele se refere
 * (vazio quando o servidor largou a escolha, porque o nó sumiu ou deixou de ser visível); o
 * {@code snapshot} é o mesmo da tela do roteador, ou vazio se o nó descarregado nunca foi lido.
 *
 * @param access   o que o jogador pode fazer no painel
 * @param distance blocos entre o jogador e o nó, ou -1 se estão em dimensões diferentes (para a mensagem de alcance).
 *                 No fio vai só a dimensão (0 ou -1): a tela calcula a distância pela posição do jogador
 *                 ({@code client/tablet/ListPainter}), e andar não reenvia o painel
 * @param range    alcance do tier do nó em blocos (0 = a dimensão inteira)
 */
public record TabletPanelPayload(int containerId, Optional<NodeKey> node, Optional<RouterSnapshot> snapshot,
        PanelAccess access, int distance, int range) implements CustomPacketPayload {
    public static final Type<TabletPanelPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_panel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletPanelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletPanelPayload::containerId,
            ByteBufCodecs.optional(NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()), TabletPanelPayload::node,
            ByteBufCodecs.optional(RouterSnapshot.STREAM_CODEC), TabletPanelPayload::snapshot,
            PanelAccess.STREAM_CODEC, TabletPanelPayload::access,
            ByteBufCodecs.BOOL.map(same -> same ? 0 : -1, distance -> distance >= 0), TabletPanelPayload::distance,
            ByteBufCodecs.INT, TabletPanelPayload::range,
            TabletPanelPayload::new);

    /** O mesmo painel como o cliente o recebe: a distância vira só a dimensão (0 ou -1). */
    public TabletPanelPayload asSent() {
        return distance <= 0 ? this : new TabletPanelPayload(containerId, node, snapshot, access, 0, range);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
