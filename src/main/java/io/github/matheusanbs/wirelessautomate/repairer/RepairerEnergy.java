package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.storage.ScalarStore;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** O buffer do Reparador como {@link IEnergyStorage}: só recebe, nunca devolve. O guardado e a capacidade aparecem cortados em {@link Integer#MAX_VALUE}. */
public final class RepairerEnergy implements IEnergyStorage {
    private final RepairerBlockEntity repairer;
    private final ScalarStore store;

    RepairerEnergy(RepairerBlockEntity repairer, ScalarStore store) {
        this.repairer = repairer;
        this.store = store;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (toReceive <= 0) {
            return 0;
        }
        int taken = (int) store.insert(toReceive, simulate);
        if (taken > 0 && !simulate) {
            repairer.wake();
        }
        return taken;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(store.stored(), Integer.MAX_VALUE);
    }

    @Override
    public int getMaxEnergyStored() {
        long capacity = store.capacity();
        return capacity <= 0 ? Integer.MAX_VALUE : (int) Math.min(capacity, Integer.MAX_VALUE);
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
