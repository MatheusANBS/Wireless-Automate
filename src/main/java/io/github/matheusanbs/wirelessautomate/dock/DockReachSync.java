package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.packet.DockReachPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * O cubo do alcance da Doca no cliente de quem agiu ({@link DockReachPayload}): um pacote ao colocar a Doca (por
 * {@link #PLACED_TICKS}), ao escolhê-la no Vinculador e depois de cada vínculo, desvínculo ou Ctrl + clique, com o
 * estado novo, e quando o cliente pede ({@link #request}). Só para aquele jogador; nada por tick.
 */
public final class DockReachSync {
    /** Quanto o cubo fica à mostra depois de colocar a Doca: 5 s. */
    public static final int PLACED_TICKS = 100;

    private DockReachSync() {
    }

    /**
     * O pacote da Doca {@code dock} com o prazo {@code ticks} (0: enquanto o Vinculador com ela está na mão): o raio do
     * tier, as máquinas dos lados na ordem dos lados e de vínculo, até {@link DockReachPayload#MAX_MACHINES}.
     */
    public static DockReachPayload payload(PatternDockBlockEntity dock, int ticks) {
        int count = Math.min(dock.machineCount(), DockReachPayload.MAX_MACHINES);
        long[] machines = new long[count];
        byte[] sides = new byte[count];
        int n = 0;
        for (int side = 0; side < 6 && n < count; side++) {
            DockSide dockSide = dock.side(side);
            if (dockSide == null) {
                continue;
            }
            for (DockMachine machine : dockSide.machines()) {
                if (n == count) {
                    break;
                }
                machines[n] = machine.pos().asLong();
                sides[n] = (byte) side;
                n++;
            }
        }
        return new DockReachPayload(dock.getBlockPos(), Config.dockRadius(dock.tier()), dock.tier().ordinal(),
                machines, sides, ticks);
    }

    /** Manda o cubo da Doca a {@code player}; jogadores falsos (GameTests, outro mod) não negociam os canais. */
    public static void send(ServerPlayer player, PatternDockBlockEntity dock, int ticks) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        DockReachPayload payload = payload(dock, ticks);
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * O pedido do cliente que segura o Vinculador com a Doca em {@code pos} sem ter o cubo dela: responde só se o
     * Vinculador de uma das mãos tem essa Doca escolhida, nesta dimensão, e ela está carregada, inteira e o jogador pode
     * interagir com ela. Senão, nada (sem mensagem).
     */
    public static void request(@Nullable ServerPlayer player, BlockPos pos) {
        if (player == null || !chose(player, player.getMainHandItem(), pos) && !chose(player, player.getOffhandItem(),
                pos)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PatternDockBlockEntity dock
                && !dock.isRemoved() && level.mayInteract(player, pos)) {
            send(player, dock, 0);
        }
    }

    private static boolean chose(ServerPlayer player, ItemStack stack, BlockPos pos) {
        DockChoice choice = stack.getItem() instanceof LinkerItem ? LinkerItem.dock(stack) : null;
        return choice != null && choice.pos().pos().equals(pos)
                && choice.pos().dimension().equals(player.level().dimension());
    }
}
