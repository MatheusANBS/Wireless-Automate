package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * O último colar do perfil de uma máquina de cada jogador ({@link MachineCopyPaste}), para o Desfazer do aviso: o
 * menu em que ele colou, o roteador e o que mudou ({@link PasteUndo}: o perfil ou a exceção, antes e depois). Só na
 * memória do servidor: some quando o jogador cola de novo mudando algo, desfaz, fecha o menu ({@link #closed}), sai do servidor
 * ou o servidor para.
 */
public final class MachinePasteUndo {
    private record Entry(RouterEditHost host, int containerId, ResourceKey<Level> dimension, BlockPos pos,
            PasteUndo change) {
    }

    private static final Map<UUID, Entry> LAST = new HashMap<>();

    private MachinePasteUndo() {
    }

    /** Guarda o colar de agora do jogador no lugar do anterior. */
    static void remember(ServerPlayer player, RouterEditHost host, PasteUndo change) {
        RouterBlockEntity router = Objects.requireNonNull(host.editedRouter());
        LAST.put(player.getUUID(), new Entry(host, host.containerId(), router.getLevel().dimension(),
                router.getBlockPos(), change));
    }

    /**
     * O que o último colar mudou, se o jogador ainda está no mesmo menu e no mesmo roteador, e esquece o colar: o
     * Desfazer vale uma vez só, mesmo quando quem chama não consegue desfazer nada. Noutro menu ou roteador, devolve
     * {@code null} e o colar continua guardado (ele some quando o menu dele fecha).
     */
    static @Nullable PasteUndo take(ServerPlayer player, RouterEditHost host) {
        Entry entry = LAST.get(player.getUUID());
        RouterBlockEntity router = host.editedRouter();
        if (entry == null || entry.host() != host || entry.containerId() != host.containerId() || router == null
                || !router.getLevel().dimension().equals(entry.dimension()) || !router.getBlockPos().equals(entry.pos())) {
            return null;
        }
        LAST.remove(player.getUUID());
        return entry.change();
    }

    /** O Desfazer guardado do jogador é de um colar no menu {@code host}. */
    static boolean heldIn(UUID player, RouterEditHost host) {
        Entry entry = LAST.get(player);
        return entry != null && entry.host() == host;
    }

    /** Há um Desfazer guardado para o jogador. */
    public static boolean has(UUID player) {
        return LAST.containsKey(player);
    }

    /** O menu {@code host} fechou: o Desfazer feito nele não vale mais. Só no servidor. */
    public static void closed(Player player, RouterEditHost host) {
        if (player instanceof ServerPlayer) {
            Entry entry = LAST.get(player.getUUID());
            if (entry != null && entry.host() == host) {
                LAST.remove(player.getUUID());
            }
        }
    }

    /** Esquece o jogador (colou de novo ou saiu do servidor). */
    public static void forget(UUID player) {
        LAST.remove(player);
    }

    /** Esquece todos (o servidor parou). */
    public static void reset() {
        LAST.clear();
    }
}
