package io.github.matheusanbs.wirelessautomate.compat.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.parts.IPartHost;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import io.github.matheusanbs.wirelessautomate.dock.DockReturnNetwork;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A Doca para o AE2: a capability {@code ae2:crafting_machine} em cada face, uma {@link Ae2DockMachine} por face,
 * criada na primeira consulta e guardada no block entity. Só carrega com o AE2 (pela ponte {@code dock/DockAe2}).
 */
public final class Ae2Dock {
    private static final Direction[] DIRECTIONS = Direction.values();

    private Ae2Dock() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.CRAFTING_MACHINE, ModBlockEntities.PATTERN_DOCK.get(),
                (dock, face) -> face == null ? null : machine(dock, face.ordinal()));
    }

    /** A máquina da face {@code side}, criada na primeira vez (o AE2 consulta a capability a cada pedido). */
    static Ae2DockMachine machine(PatternDockBlockEntity dock, int side) {
        if (dock.ae2Face(side) instanceof Ae2DockMachine machine) {
            return machine;
        }
        Ae2DockMachine machine = new Ae2DockMachine(dock, side);
        dock.setAe2Face(side, machine);
        return machine;
    }

    /**
     * O vizinho do lado {@code side} parece um Pattern Provider: expõe a {@code GENERIC_INTERNAL_INV} (o retorno) e não
     * expõe a {@code ME_STORAGE} (a Interface expõe as duas). Não carrega o chunk do vizinho.
     */
    public static boolean providerNextTo(PatternDockBlockEntity dock, int side) {
        Level level = dock.getLevel();
        if (level == null) {
            return false;
        }
        Direction direction = DIRECTIONS[side];
        BlockPos neighbor = dock.getBlockPos().relative(direction);
        if (!level.isLoaded(neighbor)) {
            return false;
        }
        @Nullable Direction face = direction.getOpposite();
        return level.getCapability(AECapabilities.GENERIC_INTERNAL_INV, neighbor, face) != null
                && level.getCapability(AECapabilities.ME_STORAGE, neighbor, face) == null;
    }

    /**
     * O destino da volta para o vizinho em {@code pos}, encostado na Doca pela face {@code face} dele: a rede ME
     * ({@link Ae2DockNetwork}) se ele é um nó do AE2 (expõe o {@code IN_WORLD_GRID_NODE_HOST}), senão {@code null}.
     * Basta a capability: o nó pode ainda não existir no primeiro tick depois da carga, e o destino o acha na inserção.
     * Só na troca do vizinho (a volta guarda o destino por lado).
     */
    public static @Nullable DockReturnNetwork target(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null) == null ? null
                : new Ae2DockNetwork(level, pos, face);
    }

    /**
     * O provedor encostado no lado {@code side} (em bloco ou em parte virada para a Doca) está na trava "travar até o
     * resultado", que com a Doca nunca destrava: o resultado vai direto para a rede, sem passar pelo retorno dele. Lê
     * a configuração pela API ({@link IConfigurableObject}); um bloco sem essa configuração não trava. Não carrega o
     * chunk do vizinho. Só para a tela, nunca por tick.
     */
    public static boolean lockedUntilResult(PatternDockBlockEntity dock, int side) {
        Level level = dock.getLevel();
        if (level == null) {
            return false;
        }
        Direction direction = DIRECTIONS[side];
        BlockPos neighbor = dock.getBlockPos().relative(direction);
        if (!level.isLoaded(neighbor)) {
            return false;
        }
        BlockEntity entity = level.getBlockEntity(neighbor);
        Object configurable = entity instanceof IPartHost host ? host.getPart(direction.getOpposite()) : entity;
        if (!(configurable instanceof IConfigurableObject object)) {
            return false;
        }
        IConfigManager config = object.getConfigManager();
        return config.hasSetting(Settings.LOCK_CRAFTING_MODE)
                && config.getSetting(Settings.LOCK_CRAFTING_MODE) == LockCraftingMode.LOCK_UNTIL_RESULT;
    }
}
