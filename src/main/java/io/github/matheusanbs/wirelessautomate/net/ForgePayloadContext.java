package io.github.matheusanbs.wirelessautomate.net;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * O {@link IPayloadContext} sobre o {@link NetworkEvent.Context} do Forge. O jogador do lado do cliente vem
 * de um {@link Supplier} que o código de cliente liga no setup ({@link #setClientPlayer}): o código comum não
 * pode tocar no {@code Minecraft} (o servidor dedicado quebra ao carregar a classe).
 */
public final class ForgePayloadContext implements IPayloadContext {
    private static volatile Supplier<@Nullable Player> clientPlayer = () -> null;

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
        return flow() == PacketFlow.SERVERBOUND ? context.getSender() : clientPlayer.get();
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
