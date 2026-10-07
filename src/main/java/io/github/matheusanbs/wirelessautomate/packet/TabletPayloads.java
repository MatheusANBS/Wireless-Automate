package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * Pacotes do Tablet de rede e os handlers, registrados por {@link ModPayloads#register}. Código
 * comum: o handler do cliente só mexe no {@link TabletMenu}. Os do servidor só agem com o Tablet
 * daquele {@code containerId} aberto e ainda válido; a validação de cada ação fica no menu.
 */
public final class TabletPayloads {
    private TabletPayloads() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TabletSnapshotPayload.TYPE, TabletSnapshotPayload.STREAM_CODEC,
                TabletPayloads::onSnapshot);
        registrar.playToServer(TabletQueryPayload.TYPE, TabletQueryPayload.STREAM_CODEC,
                (payload, context) -> handleQuery(serverPlayer(context), payload));
        registrar.playToServer(TabletMoveNodesPayload.TYPE, TabletMoveNodesPayload.STREAM_CODEC,
                (payload, context) -> handleMoveNodes(serverPlayer(context), payload));
        registrar.playToServer(TabletOpenNodePayload.TYPE, TabletOpenNodePayload.STREAM_CODEC,
                (payload, context) -> handleOpenNode(serverPlayer(context), payload));
        registrar.playToServer(TabletActionPayload.TYPE, TabletActionPayload.STREAM_CODEC,
                (payload, context) -> handleAction(serverPlayer(context), payload));
        registrar.playToServer(OpenTabletPayload.TYPE, OpenTabletPayload.STREAM_CODEC,
                (payload, context) -> handleOpenTablet(serverPlayer(context)));
    }

    private static @Nullable ServerPlayer serverPlayer(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }

    private static void onSnapshot(TabletSnapshotPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player != null && player.containerMenu instanceof TabletMenu menu && menu.containerId == payload.containerId()) {
            menu.applySnapshot(payload.snapshot());
        }
    }

    /** O Tablet aberto com esse id, se ainda vale. */
    private static @Nullable TabletMenu menu(@Nullable ServerPlayer player, int containerId) {
        return player != null && player.containerMenu instanceof TabletMenu menu && menu.containerId == containerId
                && menu.stillValid(player) ? menu : null;
    }

    // Servidor. Públicos para os GameTests.

    public static boolean handleQuery(@Nullable ServerPlayer player, TabletQueryPayload payload) {
        TabletMenu menu = menu(player, payload.containerId());
        if (menu == null) {
            return false;
        }
        menu.setQuery(payload.query());
        return true;
    }

    /** Devolve quantos nós mudaram de rede, ou -1 se recusou. */
    public static int handleMoveNodes(@Nullable ServerPlayer player, TabletMoveNodesPayload payload) {
        TabletMenu menu = menu(player, payload.containerId());
        return menu == null ? -1 : menu.moveNodes(payload.nodes(), payload.resource(), payload.network());
    }

    public static boolean handleOpenNode(@Nullable ServerPlayer player, TabletOpenNodePayload payload) {
        TabletMenu menu = menu(player, payload.containerId());
        return menu != null && menu.openNode(payload.node());
    }

    public static boolean handleAction(@Nullable ServerPlayer player, TabletActionPayload payload) {
        TabletMenu menu = menu(player, payload.containerId());
        return menu != null && menu.apply(payload.action(), payload.target(), payload.other(), payload.text(),
                payload.value());
    }

    /** Tecla de atalho: abre o Tablet se o jogador tem um no inventário e não está com outra tela aberta. */
    public static boolean handleOpenTablet(@Nullable ServerPlayer player) {
        if (player == null || player.containerMenu != player.inventoryMenu || !TabletMenu.hasTablet(player)) {
            return false;
        }
        TabletMenu.open(player);
        return true;
    }
}
