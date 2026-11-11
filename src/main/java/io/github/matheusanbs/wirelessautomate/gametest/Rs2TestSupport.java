package io.github.matheusanbs.wirelessautomate.gametest;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.autocrafting.PlatformPatternProviderExternalPatternSink;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Os tipos do RS2 dos GameTests da Doca ({@link Rs2DockGameTests}), que não podem aparecer nas assinaturas da classe
 * de teste: o destino vai e volta como {@code Object}. Só carrega na run do RS2.
 */
final class Rs2TestSupport {
    /** Um recurso que não é item nem fluido (como um químico de um addon). */
    private static final ResourceKey FOREIGN = new ResourceKey() {
    };

    private Rs2TestSupport() {
    }

    /**
     * O destino que um Autocrafter encostado na face {@code face} da Doca em {@code dock} recebe: o da fábrica
     * registrada no RS2, que junta os destinos de todas as fábricas (o da Doca e os de inventário do próprio RS2).
     */
    static Object sink(ServerLevel level, BlockPos dock, Direction face) {
        return RefinedStorageApi.INSTANCE.getPatternProviderExternalPatternSinkFactory().create(level, dock, face);
    }

    /**
     * Oferece os itens e fluidos ao destino, com um recurso de outro tipo no fim se {@code foreign}; devolve o nome do
     * resultado ({@code ACCEPTED}, {@code REJECTED}, {@code SKIPPED} ou {@code LOCKED}).
     */
    static String accept(Object sink, List<ItemStack> items, List<FluidStack> fluids, boolean foreign,
            boolean simulate) {
        List<ResourceAmount> resources = new ArrayList<>();
        for (ItemStack stack : items) {
            resources.add(new ResourceAmount(ItemResource.ofItemStack(stack), stack.getCount()));
        }
        for (FluidStack stack : fluids) {
            resources.add(new ResourceAmount(new FluidResource(stack.getFluid(), stack.getComponentsPatch()),
                    stack.getAmount()));
        }
        if (foreign) {
            resources.add(new ResourceAmount(FOREIGN, 1));
        }
        return ((PlatformPatternProviderExternalPatternSink) sink)
                .accept(resources, simulate ? Action.SIMULATE : Action.EXECUTE).name();
    }

    static boolean isEmpty(Object sink) {
        return ((PlatformPatternProviderExternalPatternSink) sink).isEmpty();
    }
}
