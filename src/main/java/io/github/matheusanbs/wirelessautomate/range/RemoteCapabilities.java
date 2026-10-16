package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * As capabilities de uma máquina remota por consulta direta ({@code level.getCapability}), sem criar
 * {@code BlockCapabilityCache} nem listener: para a tela (as faces da remota mostrada) e para a máquina
 * trocada, nunca para o motor, que usa os caches. Só com o chunk carregado (quem chama conferiu) e só nos
 * tipos carregados.
 */
public final class RemoteCapabilities {
    private RemoteCapabilities() {
    }

    /**
     * Slots (itens), tanques (fluidos e químicos) ou 1 (energia e Source) da face {@code face} ({@code null}
     * = o lado nulo); {@code -1} sem a capability ou com o tipo não carregado.
     */
    public static int slots(Level level, BlockPos pos, ResourceType type, @Nullable Direction face) {
        if (!LoadedTypes.contains(type)) {
            return -1;
        }
        return switch (type) {
            case ITEM -> {
                IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
                yield items == null ? -1 : items.getSlots();
            }
            case FLUID -> {
                IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
                yield fluids == null ? -1 : fluids.getTanks();
            }
            case ENERGY -> level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face) == null ? -1 : 1;
            case CHEMICAL -> {
                BlockCapability<?, @Nullable Direction> capability = Chemicals.capability();
                Object chemicals = capability == null ? null : level.getCapability(capability, pos, face);
                yield chemicals == null ? -1 : Chemicals.tanks(chemicals);
            }
            case SOURCE -> {
                BlockCapability<?, @Nullable Direction> capability = Sources.capability();
                yield capability == null || level.getCapability(capability, pos, face) == null ? -1 : 1;
            }
        };
    }

    /** O bloco em {@code pos} expõe a capability de algum tipo carregado, no lado nulo ou num dos seis. */
    public static boolean any(Level level, BlockPos pos) {
        for (ResourceType type : LoadedTypes.LIST) {
            if (slots(level, pos, type, null) >= 0) {
                return true;
            }
            for (Direction face : Direction.values()) {
                if (slots(level, pos, type, face) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
