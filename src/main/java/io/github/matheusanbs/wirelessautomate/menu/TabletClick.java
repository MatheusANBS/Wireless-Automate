package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * O clique com o Tablet numa máquina: abre o nó dela na Lista. O roteador preso na máquina vem primeiro; depois
 * os que a têm como remota (índice do alcance). Fica com o primeiro que o jogador vê. Só no clique: uma
 * consulta ao índice e aos seis vizinhos, nada por tick.
 */
public final class TabletClick {
    private TabletClick() {
    }

    /** Os roteadores presos na máquina em {@code pos}: o vizinho que é roteador e está virado para ela. */
    public static List<RouterBlockEntity> attachedRouters(ServerLevel level, BlockPos pos) {
        List<RouterBlockEntity> routers = new ArrayList<>(1);
        for (Direction d : Direction.values()) {
            BlockPos neighbor = pos.relative(d);
            if (!level.isLoaded(neighbor)) {
                continue;
            }
            BlockState state = level.getBlockState(neighbor);
            if (state.getBlock() instanceof RouterBlock && state.getValue(RouterBlock.FACING) == d
                    && RouterBlock.attachedPos(state, neighbor).equals(pos)
                    && level.getBlockEntity(neighbor) instanceof RouterBlockEntity router) {
                routers.add(router);
            }
        }
        return routers;
    }

    /**
     * Abre o Tablet no nó da máquina em {@code pos}, se algum roteador dela o jogador vê. Sem nenhum, o
     * aviso fica para o {@link #notifyNotLinked}. Devolve se abriu o nó.
     */
    public static boolean open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<RouterBlockEntity> routers = attachedRouters(level, pos);
        int attached = routers.size();
        for (RouterBlockEntity router : RemoteIndex.get().routersAt(level, pos)) {
            if (!routers.contains(router)) {
                routers.add(router);
            }
        }
        NodeIndex index = NodeIndex.get(level.getServer());
        NetworkSavedData data = NetworkSavedData.get(level.getServer());
        boolean operator = player.hasPermissions(2);
        for (int i = 0; i < routers.size(); i++) {
            RouterBlockEntity router = routers.get(i);
            NodeKey key = NodeKey.of(level, router.getBlockPos());
            NodeIndex.Entry entry = index.entry(key);
            if (entry == null || !TabletNodes.visible(entry, data, operator, player.getUUID())) {
                continue;
            }
            EditTargetRef target = EditTargetRef.ATTACHED;
            if (i >= attached) {
                int machine = router.remoteMachines().index(pos);
                target = machine < 0 ? null : EditTargetRef.resolve(router, EditTarget.machine(machine), pos, null);
                if (target == null) {
                    continue;
                }
            }
            TabletMenu.open(player, key, target);
            return true;
        }
        return false;
    }

    /** Avisa na barra de ação, se a máquina tem item, fluido ou energia, que ela não está num roteador do jogador. */
    public static void notifyNotLinked(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (level.isLoaded(pos) && hasTransfer(level, pos)) {
            player.displayClientMessage(Component.translatable("item.wirelessautomate.network_tablet.not_linked"), true);
        }
    }

    private static boolean hasTransfer(ServerLevel level, BlockPos pos) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null
                || level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null
                || level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null) != null;
    }
}
