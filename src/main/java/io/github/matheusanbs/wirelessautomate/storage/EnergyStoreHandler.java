package io.github.matheusanbs.wirelessautomate.storage;

import net.minecraftforge.energy.IEnergyStorage;

/**
 * A Bateria vista como um {@link IEnergyStorage} comum. A API é em {@code int}: cada chamada passa
 * até {@link Integer#MAX_VALUE} FE (o roteador do mod move isso por tick), e o guardado e a
 * capacidade aparecem cortados nesse teto para quem lê (medidores de outros mods).
 */
public final class EnergyStoreHandler implements IEnergyStorage {
    private final ScalarStore store;

    public EnergyStoreHandler(ScalarStore store) {
        this.store = store;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return toReceive <= 0 ? 0 : (int) store.insert(toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return toExtract <= 0 ? 0 : (int) store.extract(toExtract, simulate);
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
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
