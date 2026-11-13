package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.client.dock.DockReachPaint;
import io.github.matheusanbs.wirelessautomate.dock.DockReachSync;
import io.github.matheusanbs.wirelessautomate.menu.DockMenu;
import io.github.matheusanbs.wirelessautomate.range.RouterReachSync;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * Os pacotes da Doca de Padrões: o estado da tela ({@link DockStatePayload}, handler no {@link DockMenu}) e o cubo do
 * alcance no mundo, do roteador e da Doca ({@link LinkerReachPayload}, que o cliente só guarda e desenha), com o
 * pedido do cliente ({@link LinkerReachRequestPayload}, respondido pelo {@link RouterReachSync} ou pelo
 * {@link DockReachSync}, pelo {@code kind}). O {@code DockReachPaint} do cliente só é
 * citado dentro da lambda do handler, que o servidor dedicado nunca roda.
 */
public final class DockPayloads {
    private DockPayloads() {
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(DockStatePayload.TYPE, DockStatePayload.STREAM_CODEC, DockMenu::onState);
        registrar.playToClient(LinkerReachPayload.TYPE, LinkerReachPayload.STREAM_CODEC,
                (payload, context) -> DockReachPaint.receive(payload));
        registrar.playToServer(LinkerReachRequestPayload.TYPE, LinkerReachRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (payload.kind() == LinkerReachPayload.ROUTER) {
                        RouterReachSync.request(serverPlayer(context), payload.pos());
                    } else if (payload.kind() == LinkerReachPayload.DOCK) {
                        DockReachSync.request(serverPlayer(context), payload.pos());
                    }
                });
    }

    private static @Nullable ServerPlayer serverPlayer(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }
}
