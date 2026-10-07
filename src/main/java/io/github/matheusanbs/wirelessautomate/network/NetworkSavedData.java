package io.github.matheusanbs.wirelessautomate.network;

import java.util.Collection;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Redes do mundo e a rede ativa de cada jogador. Fica no data storage do overworld.
 * TODO(contrato): estender SavedData e implementar.
 */
public final class NetworkSavedData {
    public static NetworkSavedData get(MinecraftServer server) {
        throw new UnsupportedOperationException("TODO");
    }

    public WaNetwork create(UUID owner, String name) {
        throw new UnsupportedOperationException("TODO");
    }

    public @Nullable WaNetwork network(UUID id) {
        throw new UnsupportedOperationException("TODO");
    }

    public Collection<WaNetwork> networks() {
        throw new UnsupportedOperationException("TODO");
    }

    public boolean remove(UUID id) {
        throw new UnsupportedOperationException("TODO");
    }

    public @Nullable UUID activeNetwork(UUID player) {
        throw new UnsupportedOperationException("TODO");
    }

    public void setActiveNetwork(UUID player, @Nullable UUID network) {
        throw new UnsupportedOperationException("TODO");
    }

    /** A rede ativa do jogador; se ele não tiver nenhuma, cria uma com nome padrão e a ativa. */
    public WaNetwork activeOrCreate(ServerPlayer player) {
        throw new UnsupportedOperationException("TODO");
    }
}
