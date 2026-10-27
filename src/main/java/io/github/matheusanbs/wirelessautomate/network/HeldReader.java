package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import io.github.matheusanbs.wirelessautomate.storage.BulkFluids;
import io.github.matheusanbs.wirelessautomate.storage.BulkItems;
import io.github.matheusanbs.wirelessautomate.storage.BulkSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Lê o que a máquina de uma porta guarda e quanto cabe, pelos caches de capability que o motor já usa na face dela
 * (nenhuma busca nova). O mod primeiro, em {@code long} (Baú, Tanque, Bateria, Tanque de Source), e depois a API
 * comum de cada tipo; químicos e Source de outros mods passam pelas pontes {@link Chemicals} e {@link Sources}.
 * Uma capacidade {@code <= 0} (sem limite) não soma à capacidade. Não aloca.
 */
final class HeldReader {
    /** Quantos itens cabem num slot vazio, para a capacidade de um inventário comum. */
    private static final int EMPTY_SLOT = 64;

    private HeldReader() {
    }

    /**
     * Soma em {@code out[0]} o guardado e em {@code out[1]} a capacidade da máquina da porta. Devolve
     * {@code false}, sem somar nada, se a máquina não tem a capability do tipo (ou o chunk dela está descarregado).
     */
    static boolean read(Port port, long[] out) {
        MachineCaches machine = port.machine;
        Direction face = port.face;
        switch (port.type) {
            case ITEM -> {
                BulkItems bulk = machine.bulkItems(face);
                if (bulk != null) {
                    add(out, bulk.total(), bulk.capacity());
                    return true;
                }
                IItemHandler items = machine.items(face);
                if (items == null) {
                    return false;
                }
                items(items, out);
                return true;
            }
            case FLUID -> {
                BulkFluids bulk = machine.bulkFluids(face);
                if (bulk != null) {
                    add(out, bulk.total(), bulk.capacity());
                    return true;
                }
                IFluidHandler fluids = machine.fluids(face);
                if (fluids == null) {
                    return false;
                }
                for (int tank = 0, n = fluids.getTanks(); tank < n; tank++) {
                    add(out, fluids.getFluidInTank(tank).getAmount(), fluids.getTankCapacity(tank));
                }
                return true;
            }
            case ENERGY -> {
                BulkEnergy bulk = machine.bulkEnergy(face);
                if (bulk != null) {
                    add(out, bulk.stored(), bulk.capacity());
                    return true;
                }
                IEnergyStorage energy = machine.energy(face);
                if (energy == null) {
                    return false;
                }
                add(out, energy.getEnergyStored(), energy.getMaxEnergyStored());
                return true;
            }
            case CHEMICAL -> {
                Object chemicals = Chemicals.LOADED ? machine.chemicals(face) : null;
                if (chemicals == null) {
                    return false;
                }
                Chemicals.held(chemicals, out);
                return true;
            }
            case SOURCE -> {
                BulkSource bulk = machine.bulkSource(face);
                if (bulk != null) {
                    add(out, bulk.stored(), bulk.capacity());
                    return true;
                }
                Object source = Sources.LOADED ? machine.arsSource(face) : null;
                if (source == null) {
                    return false;
                }
                Sources.held(source, out);
                return true;
            }
        }
        return false;
    }

    private static void add(long[] out, long stored, long capacity) {
        out[0] += stored;
        if (capacity > 0) {
            out[1] += capacity;
        }
    }

    private static void items(IItemHandler items, long[] out) {
        for (int slot = 0, n = items.getSlots(); slot < n; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            int limit = items.getSlotLimit(slot);
            out[0] += stack.getCount();
            out[1] += stack.isEmpty() ? Math.min(limit, EMPTY_SLOT) : Math.min(limit, stack.getMaxStackSize());
        }
    }
}
