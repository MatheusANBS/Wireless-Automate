package io.github.matheusanbs.wirelessautomate.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.dock.DockAe2;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockProbe;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Uma face da Doca para o AE2: o Pattern Provider encostado nela vê uma máquina de crafting. O provedor pergunta
 * {@link #acceptsPlans} e chama {@link #pushPattern} com uma execução do padrão; a face passa o pedido à
 * {@link DockDelivery} (sem simular: o AE2 entrega numa chamada só) e responde aceito ou recusado, sem meio-termo.
 *
 * <p>Recusa o padrão que não vai a inventário comum (bancada, cortador, forja: {@link DockStatus#CRAFTING_PATTERN}) e
 * a chave que a Doca não conhece ({@link DockStatus#MIXED}). As chaves viram {@code ItemStack} partidos no tamanho
 * máximo do item (o contrato da entrega), {@code FluidStack} e, com o Applied Mekanistics, o químico da
 * {@code MekanismKey} ({@code ChemicalStack} como {@code Object}, pela ponte {@link Ae2Chemicals}), juntos no mesmo
 * pedido; as listas são desta face e reaproveitadas, porque o AE2 chama na thread do servidor, um pedido por vez.
 */
final class Ae2DockMachine implements ICraftingMachine {
    private final PatternDockBlockEntity dock;
    private final int side;
    private final List<ItemStack> items = new ArrayList<>();
    private final List<FluidStack> fluids = new ArrayList<>();
    private final List<Object> chemicals = new ArrayList<>();

    Ae2DockMachine(PatternDockBlockEntity dock, int side) {
        this.dock = dock;
        this.side = side;
    }

    /** No Pattern Access Terminal: o ícone e o nome das máquinas do lado ("Fornalha × 12"), ou os da própria Doca. */
    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        DockSide dockSide = dock.side(side);
        ResourceLocation id = dockSide == null ? null : dockSide.block();
        if (id == null) {
            Block block = dock.getBlockState().getBlock();
            return new PatternContainerGroup(AEItemKey.of(block), block.getName(), List.of());
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        ItemStack icon = new ItemStack(block);
        Component name = Component.translatable("gui.wirelessautomate.pattern_dock.ae2_group",
                RouterBlock.machineName(block), dockSide.machines().size());
        return new PatternContainerGroup(icon.isEmpty() ? null : AEItemKey.of(icon), name, List.of());
    }

    @Override
    public boolean acceptsPlans() {
        DockAe2.offered(dock, side);
        boolean accepts = DockDelivery.acceptsOrders(dock, side);
        DockProbe.Listener probe = DockProbe.listener;
        if (probe != null) {
            probe.asked(accepts);
        }
        return accepts;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        DockProbe.Listener probe = DockProbe.listener;
        if (probe == null) {
            return push(patternDetails, inputs);
        }
        long started = System.nanoTime();
        boolean accepted = push(patternDetails, inputs);
        long nanos = System.nanoTime() - started;
        DockSide dockSide = dock.side(side);
        probe.offered(accepted, dockSide == null ? DockStatus.NO_REFUSAL : dockSide.lastRefusal(), nanos);
        return accepted;
    }

    private boolean push(IPatternDetails patternDetails, KeyCounter[] inputs) {
        DockAe2.offered(dock, side);
        if (!patternDetails.supportsPushInputsToExternalInventory()) {
            DockDelivery.refuse(dock, side, DockStatus.CRAFTING_PATTERN);
            return false;
        }
        try {
            if (!convert(inputs)) {
                DockDelivery.refuse(dock, side, DockStatus.MIXED);
                return false;
            }
            return DockDelivery.offer(dock, side, items, fluids, chemicals, false) == DockDelivery.Result.ACCEPTED;
        } finally {
            items.clear();
            fluids.clear();
            chemicals.clear();
        }
    }

    /** Converte as entradas nas listas desta face; {@code false} com uma chave que a Doca não conhece. */
    private boolean convert(KeyCounter[] inputs) {
        for (KeyCounter counter : inputs) {
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                AEKey key = entry.getKey();
                if (key instanceof AEItemKey item) {
                    int max = Math.max(1, item.getReadOnlyStack().getMaxStackSize());
                    for (; amount > 0; amount -= max) {
                        items.add(item.toStack((int) Math.min(amount, max)));
                    }
                } else if (key instanceof AEFluidKey fluid) {
                    for (; amount > 0; amount -= Integer.MAX_VALUE) {
                        fluids.add(fluid.toStack((int) Math.min(amount, Integer.MAX_VALUE)));
                    }
                } else {
                    Object chemical = Ae2Chemicals.chemical(key, amount);
                    if (chemical == null) {
                        return false;
                    }
                    chemicals.add(chemical);
                }
            }
        }
        return true;
    }
}
