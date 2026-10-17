package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.range.IdleNetworkFace;
import java.util.HashMap;
import java.util.List;
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
 * O último "Tirar a rede das faces sem modo" de cada jogador, para o Desfazer do aviso: o menu em que ele
 * limpou, o roteador, o alvo e as faces que perderam a rede. Só na memória do servidor: some quando o
 * jogador limpa de novo, desfaz, fecha o menu ({@link #closed}), sai do servidor ou o servidor para.
 */
public final class IdleNetworkUndo {
    private record Entry(RouterEditHost host, int containerId, ResourceKey<Level> dimension, BlockPos pos,
            EditTargetRef target, List<IdleNetworkFace> faces) {
    }

    private static final Map<UUID, Entry> LAST = new HashMap<>();

    private IdleNetworkUndo() {
    }

    /** Guarda o limpar de agora do jogador no lugar do anterior. */
    public static void remember(ServerPlayer player, RouterEditHost host, List<IdleNetworkFace> faces) {
        RouterBlockEntity router = Objects.requireNonNull(host.editedRouter());
        LAST.put(player.getUUID(), new Entry(host, host.containerId(), router.getLevel().dimension(),
                router.getBlockPos(), host.targetRef(), List.copyOf(faces)));
    }

    /**
     * As faces do último limpar, se o jogador ainda está no mesmo menu, no mesmo roteador e no mesmo alvo
     * (uma máquina pela posição, um grupo pelo bloco); senão {@code null}. O Desfazer vale uma vez só.
     */
    public static @Nullable List<IdleNetworkFace> take(ServerPlayer player, RouterEditHost host) {
        Entry entry = LAST.get(player.getUUID());
        RouterBlockEntity router = host.editedRouter();
        if (entry == null || entry.host() != host || entry.containerId() != host.containerId() || router == null
                || !router.getLevel().dimension().equals(entry.dimension()) || !router.getBlockPos().equals(entry.pos())
                || !sameTarget(entry.target(), host.targetRef())) {
            return null;
        }
        LAST.remove(player.getUUID());
        return entry.faces();
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

    /** Esquece o jogador (limpou de novo ou saiu do servidor). */
    public static void forget(UUID player) {
        LAST.remove(player);
    }

    /** Esquece todos (o servidor parou). */
    public static void reset() {
        LAST.clear();
    }

    /** O mesmo alvo: o mesmo tipo e a mesma máquina (posição) ou o mesmo grupo (bloco), mesmo com outro índice. */
    private static boolean sameTarget(EditTargetRef a, EditTargetRef b) {
        return a.target().kind() == b.target().kind() && Objects.equals(a.pos(), b.pos())
                && Objects.equals(a.block(), b.block());
    }
}
