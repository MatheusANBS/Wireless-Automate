package io.github.matheusanbs.wirelessautomate.storage;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * O Tanque visto como um handler de fluido comum: um tanque por fluido guardado, mais um vazio no
 * fim que aceita qualquer fluido. Diferente dos itens, a API de fluido já passa até
 * {@link Integer#MAX_VALUE} mB numa chamada, então o roteador (e qualquer cano) move muito por vez
 * por aqui mesmo; a quantidade de cada tanque aparece cortada nesse teto.
 */
public final class FluidStorageHandler implements IFluidHandler {
    private final FluidStorage storage;

    public FluidStorageHandler(FluidStorage storage) {
        this.storage = storage;
    }

    @Override
    public int getTanks() {
        return storage.types() + 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank < 0 || tank >= storage.types()) {
            return FluidStack.EMPTY;
        }
        return storage.key(tank).copyWithAmount((int) Math.min(storage.count(tank), Integer.MAX_VALUE));
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    /** Um tanque de fluido só aceita o próprio fluido; o vazio do fim aceita qualquer um. */
    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        if (tank < 0 || tank > storage.types()) {
            return false;
        }
        return tank == storage.types() || FluidStack.isSameFluidSameComponents(storage.key(tank), stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        return (int) storage.insert(resource, resource.getAmount(), action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        long taken = storage.extract(resource, resource.getAmount(), action.simulate());
        return taken <= 0 ? FluidStack.EMPTY : resource.copyWithAmount((int) taken);
    }

    /** Drena do primeiro fluido guardado, como um tanque comum de um fluido só. */
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0 || storage.isEmpty()) {
            return FluidStack.EMPTY;
        }
        FluidStack key = storage.key(0);
        long taken = storage.extract(key, maxDrain, action.simulate());
        return taken <= 0 ? FluidStack.EMPTY : key.copyWithAmount((int) taken);
    }
}
