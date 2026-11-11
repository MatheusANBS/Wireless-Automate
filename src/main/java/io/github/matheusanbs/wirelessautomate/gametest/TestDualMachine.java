package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Uma máquina de teste com duas entradas de tipos diferentes, para os pedidos da Doca com item e fluido: um slot de
 * item só pela face de cima e um tanque só pelas faces laterais (como uma máquina do Mekanism que pede item e fluido).
 * O tanque pode mentir ({@link Dual#lying}): aceita na simulação e nada de verdade, o caso da simulação que não soma.
 * Presa a um bloco vanilla sem capability, com o estado guardado por posição. Só existe com
 * {@code -Dwirelessautomate.gameTests=true} ({@link TestMachines#enabled}).
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class TestDualMachine {
    public static final Block BLOCK = Blocks.MELON;

    private static final Map<BlockPos, Dual> MACHINES = new ConcurrentHashMap<>();

    private TestDualMachine() {
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        if (!TestMachines.enabled()) {
            return;
        }
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) ->
                side == Direction.UP ? at(pos).items : null, BLOCK);
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) ->
                side != null && side.getAxis().isHorizontal() ? at(pos).tank : null, BLOCK);
    }

    /** A máquina da posição absoluta {@code pos} (criada vazia na primeira consulta). */
    static Dual at(BlockPos pos) {
        return MACHINES.computeIfAbsent(pos.immutable(), key -> new Dual());
    }

    /**
     * O perfil da máquina virada para o norte num lado da Doca: item Insere em Cima, fluido Insere em Trás. Cada tipo tem
     * também uma face Insere sem o recurso dele (fluido em Cima, item em Trás), que a entrega pula.
     */
    static void profile(DockSide side) {
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BACK).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.FLUID, RemoteSide.BACK).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.FLUID, RemoteSide.TOP).setMode(PortMode.INSERT);
    }

    /** Esquece o estado da posição (o mundo dos testes é reaproveitado entre lotes). */
    static void reset(BlockPos pos) {
        MACHINES.remove(pos.immutable());
    }

    static final class Dual {
        final ItemStackHandler items = new ItemStackHandler(1);
        final FluidTank tank = new FluidTank(4_000) {
            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return lying && action.execute() ? 0 : super.fill(resource, action);
            }
        };
        /** O tanque aceita na simulação e não enche na execução. */
        volatile boolean lying;
    }
}
