package io.github.matheusanbs.wirelessautomate.net;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Contexto de um pacote recebido. Imita o {@code net.neoforged.neoforge.network.handling.IPayloadContext}
 * (só o que o mod usa) por cima do {@link NetworkEvent.Context} do Forge, para os handlers do {@code main}
 * mudarem só os imports. Crie com {@link #of}.
 */
public interface IPayloadContext {
    /**
     * Quem recebeu ou mandou: no servidor, o jogador que mandou; no cliente, o jogador local (pelo
     * {@link ForgePayloadContext#setClientPlayer}, ligado pelo código de cliente). Nulo se não houver.
     */
    @Nullable Player player();

    /** Roda na thread principal do lado que recebeu. */
    CompletableFuture<Void> enqueueWork(Runnable task);

    /** {@link PacketFlow#SERVERBOUND} se o servidor recebeu, {@link PacketFlow#CLIENTBOUND} se o cliente. */
    PacketFlow flow();

    static IPayloadContext of(Supplier<NetworkEvent.Context> context) {
        return new ForgePayloadContext(context.get());
    }
}
