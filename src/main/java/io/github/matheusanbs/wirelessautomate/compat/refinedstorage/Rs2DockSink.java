package io.github.matheusanbs.wirelessautomate.compat.refinedstorage;

import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.autocrafting.PlatformPatternProviderExternalPatternSink;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockRs2;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * O destino de um Autocrafter na Doca: o lado é a face da Doca encostada nele ({@code direction}, que o Autocrafter já
 * passa invertida). Criado uma vez, antes de saber se há uma Doca ali; a cada pedido confere a posição (sem Doca,
 * {@code SKIPPED}, e os outros destinos do RS2 respondem).
 *
 * <p>O RS2 simula e depois executa, no mesmo tick: a simulação passa à {@link DockDelivery} com {@code simulate}, que
 * escolhe a máquina e a guarda, e a execução entrega nela sem recusar. Toda recusa é {@code REJECTED}, também a do
 * pendente: o {@code LOCKED} não é seguro aqui, porque o RS2 junta o resultado dos destinos da mesma posição e um
 * {@code LOCKED} seguido de um {@code SKIPPED} (o destino de inventário, que não acha nada na Doca) vira
 * {@code ACCEPTED}, e a execução que viesse depois sumiria com os ingredientes.
 *
 * <p>Os recursos viram {@code ItemStack} partidos no tamanho máximo do item (o contrato da entrega), {@code FluidStack}
 * (o RS2 conta fluido em mB no NeoForge) e, com a integração do RS2 com o Mekanism, o químico do
 * {@code ChemicalResource} ({@code ChemicalStack} como {@code Object}, pela ponte {@link Rs2Chemicals}), juntos no
 * mesmo pedido; outro tipo recusa com {@link DockStatus#MIXED}. O RS2 entrega o pedido inteiro a todos os destinos da
 * posição: o de químico da integração procura a capability de químico da Doca, que não existe, e responde
 * {@code SKIPPED}, então é este que trata o químico. As listas são deste destino e reaproveitadas: o RS2 chama na
 * thread do servidor, um pedido por vez.
 */
final class Rs2DockSink implements PlatformPatternProviderExternalPatternSink {
    private final ServerLevel level;
    private final BlockPos pos;
    private final int side;
    private final List<ItemStack> items = new ArrayList<>();
    private final List<FluidStack> fluids = new ArrayList<>();
    private final List<Object> chemicals = new ArrayList<>();

    Rs2DockSink(ServerLevel level, BlockPos pos, Direction direction) {
        this.level = level;
        this.pos = pos.immutable();
        this.side = direction.ordinal();
    }

    @Override
    public ExternalPatternSink.Result accept(Collection<ResourceAmount> resources, Action action) {
        PatternDockBlockEntity dock = dock();
        if (dock == null) {
            return ExternalPatternSink.Result.SKIPPED;
        }
        DockRs2.offered(dock, side);
        try {
            if (!convert(resources)) {
                DockDelivery.refuse(dock, side, DockStatus.MIXED);
                return ExternalPatternSink.Result.REJECTED;
            }
            return DockDelivery.offer(dock, side, items, fluids, chemicals, action == Action.SIMULATE)
                    == DockDelivery.Result.ACCEPTED ? ExternalPatternSink.Result.ACCEPTED
                    : ExternalPatternSink.Result.REJECTED;
        } finally {
            items.clear();
            fluids.clear();
            chemicals.clear();
        }
    }

    /**
     * Ordena os destinos do RS2 por tipo: este leva itens, fluidos e, com a integração, químicos. O RS2 só chama os
     * destinos que se aplicam a algum recurso do pedido, então sem o químico aqui um pedido só de químico nunca chegaria.
     */
    @Override
    public boolean applies(ResourceKey resource) {
        return resource instanceof ItemResource || resource instanceof FluidResource
                || Rs2Chemicals.isChemical(resource);
    }

    /** A trava "até a máquina esvaziar" do Autocrafter; sem Doca, vazio. */
    @Override
    public boolean isEmpty() {
        PatternDockBlockEntity dock = dock();
        return dock == null || DockRs2.idle(dock, side);
    }

    /** A Doca na posição, sem carregar o chunk; {@code null} se não há uma. */
    private @Nullable PatternDockBlockEntity dock() {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PatternDockBlockEntity dock
                && !dock.isRemoved() ? dock : null;
    }

    /** Converte os recursos nas listas deste destino; {@code false} com um que a Doca não conhece. */
    private boolean convert(Collection<ResourceAmount> resources) {
        for (ResourceAmount resourceAmount : resources) {
            long amount = resourceAmount.amount();
            if (resourceAmount.resource() instanceof ItemResource item) {
                ItemStack first = new ItemStack(BuiltInRegistries.ITEM.wrapAsHolder(item.item()), 1, item.components());
                int max = Math.max(1, first.getMaxStackSize());
                for (; amount > 0; amount -= max) {
                    items.add(first.copyWithCount((int) Math.min(amount, max)));
                }
            } else if (resourceAmount.resource() instanceof FluidResource fluid) {
                for (; amount > 0; amount -= Integer.MAX_VALUE) {
                    fluids.add(new FluidStack(BuiltInRegistries.FLUID.wrapAsHolder(fluid.fluid()),
                            (int) Math.min(amount, Integer.MAX_VALUE), fluid.components()));
                }
            } else {
                Object chemical = Rs2Chemicals.chemical(resourceAmount.resource(), amount);
                if (chemical == null) {
                    return false;
                }
                chemicals.add(chemical);
            }
        }
        return true;
    }
}
