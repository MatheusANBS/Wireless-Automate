package io.github.matheusanbs.wirelessautomate.net;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * O {@link IPayloadContext} sobre o {@link NetworkEvent.Context} do Forge. O jogador do lado do cliente vem
 * de um {@link Supplier} que o código de cliente liga no setup ({@link #setClientPlayer}): o código comum não
 * pode tocar no {@code Minecraft} (o servidor dedicado quebra ao carregar a classe).
 */
public final class ForgePayloadContext implements IPayloadContext {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile @Nullable Supplier<@Nullable Player> clientPlayer;
    private static volatile boolean warned;

    private final NetworkEvent.Context context;

    ForgePayloadContext(NetworkEvent.Context context) {
        this.context = context;
    }

    /** Chamado pelo setup do cliente com {@code () -> Minecraft.getInstance().player}. */
    public static void setClientPlayer(Supplier<@Nullable Player> supplier) {
        clientPlayer = supplier;
    }

    public NetworkEvent.Context forgeContext() {
        return context;
    }

    @Override
    public @Nullable Player player() {
        if (flow() == PacketFlow.SERVERBOUND) {
            return context.getSender();
        }
        Supplier<@Nullable Player> supplier = clientPlayer;
        if (supplier == null) {
            if (!warned) {
                warned = true;
                LOGGER.warn("ForgePayloadContext.setClientPlayer não foi chamado no setup do cliente: "
                        + "os pacotes do servidor para o cliente serão ignorados");
            }
            return null;
        }
        return supplier.get();
    }

    @Override
    public CompletableFuture<Void> enqueueWork(Runnable task) {
        return context.enqueueWork(task);
    }

    @Override
    public PacketFlow flow() {
        return context.getDirection().getReceptionSide().isServer() ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND;
    }
}
