package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * O desvincular pendente do Vinculador, por jogador: o primeiro clique numa máquina vinculada só pergunta, e o
 * segundo na mesma máquina e no mesmo roteador, em até {@link #WINDOW} ticks, desvincula. Só na memória do servidor,
 * nunca no item nem no NBT do mundo: some no clique seguinte, quando o jogador sai ou quando o servidor para.
 */
public final class UnlinkConfirm {
    /** O prazo do segundo clique: 3 s. */
    public static final int WINDOW = 60;

    private record Pending(GlobalPos router, BlockPos machine, long until) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private UnlinkConfirm() {
    }

    /**
     * Esquece o pendente do jogador e diz se ele era esta máquina deste roteador, ainda no prazo. Todo clique do modo
     * Vincular passa por aqui: clicar em outra coisa no meio desfaz a pergunta.
     */
    public static boolean take(ServerPlayer player, @Nullable RouterBlockEntity router, BlockPos machine) {
        Pending pending = PENDING.remove(player.getUUID());
        return pending != null && router != null
                && pending.router().equals(GlobalPos.of(player.level().dimension(), router.getBlockPos()))
                && pending.machine().equals(machine)
                && player.serverLevel().getGameTime() <= pending.until();
    }

    /** O primeiro clique em {@code machine}, vinculada a {@code router}: espera o segundo. */
    public static void ask(ServerPlayer player, RouterBlockEntity router, BlockPos machine) {
        PENDING.put(player.getUUID(), new Pending(GlobalPos.of(player.level().dimension(), router.getBlockPos()),
                machine.immutable(), player.serverLevel().getGameTime() + WINDOW));
    }

    /** Esquece o pendente do jogador (ele saiu do servidor). */
    public static void forget(UUID player) {
        PENDING.remove(player);
    }

    /** Esquece todos (o servidor parou). */
    public static void reset() {
        PENDING.clear();
    }
}
