package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.dock.DockColors;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.packet.LinkerReachPayload;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * O cubo do alcance do roteador no cliente de quem agiu ({@link LinkerReachPayload} com {@code kind} roteador): um
 * pacote ao escolher o roteador no Vinculador e depois de cada vínculo, desvínculo ou Ctrl + clique
 * ({@link RangeLinks}, {@link SameBlockLinks}), com o estado novo, e quando o cliente pede ({@link #request}). Só para
 * aquele jogador; nada por tick.
 */
public final class RouterReachSync {
    /** Há seis cores de grupo (as da {@link DockColors#ORDER}); o grupo de índice maior repete. */
    private static final int GROUP_COLORS = DockColors.ORDER.length;

    private RouterReachSync() {
    }

    /**
     * O pacote do roteador (prazo 0: enquanto o Vinculador com ele está na mão): o raio de máquinas do tier, ou
     * {@link LinkerReachPayload#NO_CUBE} sem o Upgrade de Alcance valendo; as remotas na ordem de vínculo, até
     * {@link LinkerReachPayload#MAX_MACHINES} (a presa não entra), cada uma na cor do grupo dela se está ativa
     * ({@code DockColors.ORDER} pela posição do grupo na lista do roteador, só com os grupos de remotas) e em
     * {@link LinkerReachPayload#INACTIVE} se está fora do raio, além do limite ou sem o cubo.
     */
    public static LinkerReachPayload payload(RouterBlockEntity router) {
        RemoteMachines remotes = router.remoteMachines();
        List<RemoteMachine> machines = remotes.machines();
        int count = Math.min(machines.size(), LinkerReachPayload.MAX_MACHINES);
        long[] positions = new long[count];
        byte[] colors = new byte[count];
        int radius = RangeLinks.upgradeRefusal(router) == LinkRefusal.NONE ? Config.machineRadius(router.tier())
                : LinkerReachPayload.NO_CUBE;
        Int2IntOpenHashMap groupIndex = groupIndex(remotes);
        RangeRules.Reach[] reach = router.machineReach();
        for (int i = 0; i < count; i++) {
            RemoteMachine machine = machines.get(i);
            positions[i] = machine.pos().asLong();
            colors[i] = radius != LinkerReachPayload.NO_CUBE && reach[i] == RangeRules.Reach.ACTIVE
                    ? (byte) DockColors.ORDER[groupIndex.get(machine.group()) % GROUP_COLORS]
                    : LinkerReachPayload.INACTIVE;
        }
        return new LinkerReachPayload(LinkerReachPayload.ROUTER, router.getBlockPos(), radius,
                router.tier().ordinal(), positions, colors, 0);
    }

    /**
     * A posição de cada grupo com remotas na lista de grupos do roteador (a presa sozinha no grupo dela não ocupa
     * posição: o primeiro grupo de remotas é o 0).
     */
    private static Int2IntOpenHashMap groupIndex(RemoteMachines remotes) {
        IntOpenHashSet used = new IntOpenHashSet();
        for (RemoteMachine machine : remotes.machines()) {
            used.add(machine.group());
        }
        Int2IntOpenHashMap index = new Int2IntOpenHashMap();
        for (MachineGroup group : remotes.groups()) {
            if (used.contains(group.id())) {
                index.put(group.id(), index.size());
            }
        }
        return index;
    }

    /** Manda o cubo do roteador a {@code player}; jogadores falsos (GameTests, outro mod) não negociam os canais. */
    public static void send(ServerPlayer player, RouterBlockEntity router) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        LinkerReachPayload payload = payload(router);
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * O pedido do cliente que segura o Vinculador com o roteador em {@code pos} sem ter o cubo dele: responde só se
     * {@link #answers} aceita. Senão, nada (sem mensagem).
     */
    public static void request(@Nullable ServerPlayer player, BlockPos pos) {
        if (player != null && answers(player, pos) && player.serverLevel()
                .getBlockEntity(pos) instanceof RouterBlockEntity router) {
            send(player, router);
        }
    }

    /**
     * O pedido por {@code pos} vale: o Vinculador de uma das mãos tem esse roteador escolhido, nesta dimensão, e ele
     * está carregado, inteiro e o jogador pode interagir com ele.
     */
    public static boolean answers(ServerPlayer player, BlockPos pos) {
        if (!chose(player, player.getMainHandItem(), pos) && !chose(player, player.getOffhandItem(), pos)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof RouterBlockEntity router
                && !router.isRemoved() && level.mayInteract(player, pos);
    }

    private static boolean chose(ServerPlayer player, ItemStack stack, BlockPos pos) {
        GlobalPos choice = stack.getItem() instanceof LinkerItem ? LinkerItem.router(stack) : null;
        return choice != null && choice.pos().equals(pos) && choice.dimension().equals(player.level().dimension());
    }
}
