package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Energia para o {@link ScalarTransfer}. A Bateria do mod ({@link BulkEnergy}) vem primeiro: o lado
 * dela é em {@code long}, sem o teto de {@link Integer#MAX_VALUE} FE por chamada do
 * {@link IEnergyStorage}. Entre duas Baterias, bilhões de FE passam por tick; com máquinas e cabos de
 * outros mods, o lado deles continua em {@code int}.
 */
final class EnergyAccess implements ScalarAccess {
    static final EnergyAccess INSTANCE = new EnergyAccess();

    @Override
    public @Nullable Object handler(RouterBlockEntity node, Direction machineFace) {
        BulkEnergy bulk = node.bulkEnergy(machineFace);
        return bulk != null ? bulk : node.energy(machineFace);
    }

    @Override
    public boolean canExtract(Object handler) {
        return handler instanceof BulkEnergy || ((IEnergyStorage) handler).canExtract();
    }

    @Override
    public boolean canReceive(Object handler) {
        return handler instanceof BulkEnergy || ((IEnergyStorage) handler).canReceive();
    }

    @Override
    public long extract(Object handler, long amount, boolean simulate) {
        return handler instanceof BulkEnergy bulk ? bulk.extract(amount, simulate)
                : ((IEnergyStorage) handler).extractEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate);
    }

    @Override
    public long insert(Object handler, long amount, boolean simulate) {
        return handler instanceof BulkEnergy bulk ? bulk.insert(amount, simulate)
                : ((IEnergyStorage) handler).receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate);
    }

    private EnergyAccess() {
    }
}
