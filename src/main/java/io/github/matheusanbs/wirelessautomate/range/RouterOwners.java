package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * O {@link OwnerIndex} dos roteadores carregados por posição de máquina vinculada (todas, ativas ou não, com ou sem o
 * Upgrade de Alcance) e a consulta {@link #otherRouter}, que decide se uma máquina já é de outro roteador. Um por
 * servidor, descartado na parada. Mantido pelo roteador quando as remotas mudam, na carga e na saída. Só no clique.
 */
public final class RouterOwners {
    private static final Direction[] SIDES = Direction.values();
    private static @Nullable OwnerIndex<RouterBlockEntity> instance;

    private RouterOwners() {
    }

    public static OwnerIndex<RouterBlockEntity> get() {
        if (instance == null) {
            instance = new OwnerIndex<>();
        }
        return instance;
    }

    /** Descarta o estado ao parar o servidor. */
    public static void reset() {
        instance = null;
    }

    /**
     * Outro roteador carregado (não {@code chosen}) que tem a máquina em {@code pos}: vinculada (ativa ou não, pelo
     * índice, conferida com {@code remoteMachines().index(pos) >= 0}) ou presa (um roteador encostado com
     * {@code machinePos()} em {@code pos}, nos 6 vizinhos carregados). {@code null} se nenhum. Só no clique: aloca.
     */
    public static @Nullable RouterBlockEntity otherRouter(Level level, RouterBlockEntity chosen, BlockPos pos) {
        for (RouterBlockEntity other : get().at(level, pos)) {
            if (other != chosen && !other.isRemoved() && other.remoteMachines().index(pos) >= 0) {
                return other;
            }
        }
        BlockPos.MutableBlockPos around = new BlockPos.MutableBlockPos();
        for (Direction side : SIDES) {
            around.setWithOffset(pos, side);
            if (!level.isLoaded(around)) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(around);
            if (entity instanceof RouterBlockEntity other && other != chosen && !other.isRemoved()
                    && pos.equals(other.machinePos())) {
                return other;
            }
        }
        return null;
    }
}
