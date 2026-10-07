package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * Registro dos pacotes do mod e os handlers. Código comum: os handlers do cliente só mexem no
 * {@link RouterMenu}, sem classes de tela.
 *
 * <p>Os handlers rodam na thread principal (o padrão do {@link PayloadRegistrar} é
 * {@code HandlerThread.MAIN}). Os do servidor só agem se o jogador está com a tela daquele roteador
 * aberta ({@code containerId} igual e {@link RouterMenu#stillValid}) e validam os valores; depois de
 * aplicar, o {@link RouterMenu#broadcastChanges()} do próprio menu manda o snapshot novo.
 */
public final class ModPayloads {
    /** Versão do protocolo; mude quando um payload mudar de formato. */
    public static final String VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(RouterSnapshotPayload.TYPE, RouterSnapshotPayload.STREAM_CODEC,
                ModPayloads::onSnapshot);
        registrar.playToClient(RouterThroughputPayload.TYPE, RouterThroughputPayload.STREAM_CODEC,
                ModPayloads::onThroughput);
        registrar.playToServer(SetFacePayload.TYPE, SetFacePayload.STREAM_CODEC,
                (payload, context) -> handleSetFace(serverPlayer(context), payload));
        registrar.playToServer(SetNetworkPayload.TYPE, SetNetworkPayload.STREAM_CODEC,
                (payload, context) -> handleSetNetwork(serverPlayer(context), payload));
        registrar.playToServer(RenameRouterPayload.TYPE, RenameRouterPayload.STREAM_CODEC,
                (payload, context) -> handleRename(serverPlayer(context), payload));
    }

    private static @Nullable ServerPlayer serverPlayer(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }

    // Cliente.

    private static void onSnapshot(RouterSnapshotPayload payload, IPayloadContext context) {
        RouterMenu menu = openMenu(context.player(), payload.containerId());
        if (menu != null) {
            menu.applySnapshot(payload.snapshot());
        }
    }

    private static void onThroughput(RouterThroughputPayload payload, IPayloadContext context) {
        RouterMenu menu = openMenu(context.player(), payload.containerId());
        if (menu != null && payload.perType().length == ResourceType.values().length) {
            menu.applyThroughput(payload.perType());
        }
    }

    private static @Nullable RouterMenu openMenu(@Nullable Player player, int containerId) {
        return player != null && player.containerMenu instanceof RouterMenu menu && menu.containerId == containerId
                ? menu
                : null;
    }

    // Servidor. Públicos para os GameTests; devolvem se aplicaram.

    /** Configura uma face. Recusa químicos (sem Mekanism) e limita a prioridade. */
    public static boolean handleSetFace(@Nullable ServerPlayer player, SetFacePayload payload) {
        RouterBlockEntity router = router(player, payload.containerId());
        if (router == null || payload.resource() == ResourceType.CHEMICAL) {
            return false;
        }
        int priority = Mth.clamp(payload.priority(), RouterMenu.MIN_PRIORITY, RouterMenu.MAX_PRIORITY);
        router.configureFace(payload.resource(), payload.face(), payload.mode(), priority, payload.redstone());
        return true;
    }

    /**
     * Muda a rede do roteador. Só aceita rede que existe e que o jogador pode usar (dono ou
     * operador nível 2, a mesma regra do Configurador), ou vazio para tirar da rede.
     */
    public static boolean handleSetNetwork(@Nullable ServerPlayer player, SetNetworkPayload payload) {
        RouterBlockEntity router = router(player, payload.containerId());
        if (router == null) {
            return false;
        }
        Optional<UUID> id = payload.network();
        if (id.isEmpty()) {
            router.setNetworkId(null);
            return true;
        }
        WaNetwork network = NetworkSavedData.get(player.server).network(id.get());
        if (network == null || !canUse(player, network)) {
            return false;
        }
        router.setNetworkId(network.id());
        return true;
    }

    /** Dá nome ao nó. Recusa nomes maiores que {@link RenameRouterPayload#MAX_LENGTH}. */
    public static boolean handleRename(@Nullable ServerPlayer player, RenameRouterPayload payload) {
        RouterBlockEntity router = router(player, payload.containerId());
        if (router == null || payload.name().strip().length() > RenameRouterPayload.MAX_LENGTH) {
            return false;
        }
        router.setName(payload.name());
        return true;
    }

    public static boolean canUse(ServerPlayer player, WaNetwork network) {
        return network.owner().equals(player.getUUID()) || player.hasPermissions(2);
    }

    /** O roteador da tela aberta, se é ela que o pacote cita e ela ainda vale. */
    private static @Nullable RouterBlockEntity router(@Nullable ServerPlayer player, int containerId) {
        RouterMenu menu = openMenu(player, containerId);
        if (menu == null || menu.router() == null || !menu.stillValid(player)) {
            return null;
        }
        return menu.router();
    }

    private ModPayloads() {
    }
}
