package io.github.matheusanbs.wirelessautomate.dock;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * O destino da volta num lado encostado numa rede (a ME do AE2, {@code compat/ae2/Ae2DockNetwork}, ou a do RS2,
 * {@code compat/refinedstorage/Rs2DockNetwork}): um inventário e um tanque de um espaço, sempre vazios, que só aceitam
 * e inserem direto no armazenamento da rede. A volta ({@link DockReturn}) o usa no lugar do
 * {@code IItemHandler}/{@code IFluidHandler} do vizinho, sem conhecer os tipos do outro mod.
 */
public interface DockReturnNetwork extends IItemHandler, IFluidHandler {
    /** O vizinho mudou: esquece a rede guardada, achada de novo na próxima inserção. */
    void reset();
}
