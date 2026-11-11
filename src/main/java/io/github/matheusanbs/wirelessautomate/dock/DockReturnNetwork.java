package io.github.matheusanbs.wirelessautomate.dock;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * O destino da volta num lado encostado numa rede (a ME do AE2, {@code compat/ae2/Ae2DockNetwork}, ou a do RS2,
 * {@code compat/refinedstorage/Rs2DockNetwork}): um inventário e um tanque de um espaço, sempre vazios, que só aceitam
 * e inserem direto no armazenamento da rede. A volta ({@link DockReturn}) o usa no lugar do
 * {@code IItemHandler}/{@code IFluidHandler} do vizinho, sem conhecer os tipos do outro mod.
 *
 * <p>Químicos ({@code ChemicalStack} do Mekanism como {@code Object}, pela ponte {@code network.Chemicals}): por padrão
 * a rede não os aceita ({@link #acceptsChemicals} falso), e a volta deixa o químico na máquina, sem marcar o provedor
 * cheio. A rede que guarda químicos (com o addon do Mekanism dela) responde {@code true} e insere pelo
 * {@link #insertChemical}.
 */
public interface DockReturnNetwork extends IItemHandler, IFluidHandler {
    /** O vizinho mudou: esquece a rede guardada, achada de novo na próxima inserção. */
    void reset();

    /**
     * A simulação da inserção não vale para esta rede (no RS2, as tarefas só pegam o resultado que esperam na execução):
     * a volta insere de verdade o que a máquina oferece e depois tira dela o que entrou ({@link DockReturn}).
     */
    default boolean insertsFirst() {
        return false;
    }

    /**
     * Insere {@code amount} do item de {@code item} (a quantidade dele não conta, e ele não muda) no armazenamento da
     * rede; devolve quanto entrou. A volta juntada ({@link DockReturnItems}) chama uma vez por item por visita, com a
     * soma de todos os slots e máquinas: a rede que guarda em {@code long} sobrescreve sem criar pilha. Por padrão,
     * pelo {@link #insertItem} com uma cópia do tamanho pedido (até {@code Integer.MAX_VALUE}).
     */
    default long insertItems(ItemStack item, long amount, boolean simulate) {
        int count = (int) Math.min(amount, Integer.MAX_VALUE);
        if (count <= 0 || item.isEmpty()) {
            return 0;
        }
        return count - insertItem(0, item.copyWithCount(count), simulate).getCount();
    }

    /**
     * Tira da rede {@code stack} (o que entrou a mais quando a máquina deu menos do que ofereceu, com
     * {@link #insertsFirst}); devolve quanto saiu.
     */
    default long reclaim(ItemStack stack) {
        return 0;
    }

    /** O mesmo que {@link #reclaim(ItemStack)}, para fluido (mB). */
    default long reclaim(FluidStack stack) {
        return 0;
    }

    /** A rede guarda químicos: só então a volta chama {@link #insertChemical} e {@link #reclaimChemical}. */
    default boolean acceptsChemicals() {
        return false;
    }

    /**
     * Insere o químico {@code stack} ({@code ChemicalStack}) no armazenamento da rede, sem mudá-lo; devolve quanto
     * entrou (mB). Por padrão, nada.
     */
    default long insertChemical(Object stack, boolean simulate) {
        return 0;
    }

    /** O mesmo que {@link #reclaim(ItemStack)}, para químico ({@code ChemicalStack}, mB). */
    default long reclaimChemical(Object stack) {
        return 0;
    }
}
