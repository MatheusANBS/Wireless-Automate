package io.github.matheusanbs.wirelessautomate.gametest;

import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.autocrafting.PlatformPatternProviderExternalPatternSink;
import com.refinedmods.refinedstorage.common.api.support.network.AbstractNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.autocrafting.CraftingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.autocrafter.LockMode;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Os tipos do RS2 dos GameTests da Doca ({@link Rs2DockGameTests}), que não podem aparecer nas assinaturas da classe
 * de teste: o destino vai e volta como {@code Object}. A rede é a do bloco do RS2 em {@code pos} (o Autocrafter), achada
 * pelo nó dele; o padrão de processamento é montado como a Pattern Grid monta e posto no Autocrafter pelo contêiner
 * dele, que o RS2 não publica (reflexão). Só carrega na run do RS2.
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

    /** O Autocrafter virado para {@code facing} (a máquina para que aponta fica desse lado). */
    @SuppressWarnings("unchecked")
    static BlockState facing(Block autocrafter, Direction facing) {
        BlockState state = autocrafter.defaultBlockState();
        Property<?> property = state.getBlock().getStateDefinition().getProperty("direction");
        return state.setValue((EnumProperty<Direction>) property, facing);
    }

    /** Um padrão de processamento: duas entradas e uma saída, como a Pattern Grid grava. */
    static ItemStack processingPattern(Item output, int outCount, Item first, int firstCount, Item second,
            int secondCount) {
        ItemStack pattern = PatternGridBlockEntity.createPatternStack(PatternType.PROCESSING);
        List<Optional<ProcessingPatternState.ProcessingIngredient>> ingredients = List.of(
                Optional.of(new ProcessingPatternState.ProcessingIngredient(
                        new ResourceAmount(new ItemResource(first), firstCount), List.of())),
                Optional.of(new ProcessingPatternState.ProcessingIngredient(
                        new ResourceAmount(new ItemResource(second), secondCount), List.of())));
        List<Optional<ResourceAmount>> outputs = List.of(Optional.of(new ResourceAmount(new ItemResource(output),
                outCount)));
        pattern.set(DataComponents.INSTANCE.getProcessingPatternState(), new ProcessingPatternState(ingredients,
                outputs));
        return pattern;
    }

    /** Um padrão de bancada com uma entrada só (num espaço de 1×1), como a Pattern Grid grava. */
    static ItemStack craftingPattern(Item input) {
        ItemStack pattern = PatternGridBlockEntity.createPatternStack(PatternType.CRAFTING);
        pattern.set(DataComponents.INSTANCE.getCraftingPatternState(), new CraftingPatternState(false,
                CraftingInput.ofPositioned(1, 1, List.of(new ItemStack(input)))));
        return pattern;
    }

    /** Põe {@code pattern} no primeiro espaço de padrões do Autocrafter em {@code pos}. */
    static boolean installPattern(ServerLevel level, BlockPos pos, ItemStack pattern) {
        return installPattern(level, pos, pattern, 0);
    }

    /** Põe {@code pattern} no espaço {@code slot} dos padrões do Autocrafter em {@code pos}. */
    static boolean installPattern(ServerLevel level, BlockPos pos, ItemStack pattern, int slot) {
        try {
            Object entity = level.getBlockEntity(pos);
            Method getter = entity.getClass().getDeclaredMethod("getPatternContainer");
            getter.setAccessible(true);
            Container container = (Container) getter.invoke(entity);
            container.setItem(slot, pattern);
            return pattern.equals(container.getItem(slot));
        } catch (ReflectiveOperationException | ClassCastException e) {
            return false;
        }
    }

    /** Liga a trava "até receber todos os resultados" no Autocrafter em {@code pos} (o RS2 não publica o setter). */
    static boolean lockUntilAllOutputs(ServerLevel level, BlockPos pos) {
        try {
            Object entity = level.getBlockEntity(pos);
            Method setter = entity.getClass().getDeclaredMethod("setLockMode", LockMode.class);
            setter.setAccessible(true);
            setter.invoke(entity, LockMode.LOCK_UNTIL_ALL_OUTPUTS_ARE_RECEIVED);
            return true;
        } catch (ReflectiveOperationException | ClassCastException e) {
            return false;
        }
    }

    /** O Autocrafter em {@code pos} está travado; {@code false} se não deu para ler. */
    static boolean locked(ServerLevel level, BlockPos pos) {
        try {
            Object entity = level.getBlockEntity(pos);
            Method getter = entity.getClass().getDeclaredMethod("isLocked");
            getter.setAccessible(true);
            return (boolean) getter.invoke(entity);
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
            return false;
        }
    }

    private static @Nullable Network network(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbstractNetworkNodeContainerBlockEntity<?> entity)) {
            return null;
        }
        for (InWorldNetworkNodeContainer container : entity.getContainerProvider().getContainers()) {
            Network network = container.getNode().getNetwork();
            if (network != null) {
                return network;
            }
        }
        return null;
    }

    /** A rede do bloco em {@code pos} guarda itens (simula inserir um). */
    static boolean stores(ServerLevel level, BlockPos pos) {
        Network network = network(level, pos);
        return network != null && network.getComponent(StorageNetworkComponent.class)
                .insert(new ItemResource(Items.COBBLESTONE), 1, Action.SIMULATE, Actor.EMPTY) == 1;
    }

    /** A rede do bloco em {@code pos} conhece um padrão que dá {@code output}. */
    static boolean knows(ServerLevel level, BlockPos pos, Item output) {
        Network network = network(level, pos);
        return network != null && network.getComponent(AutocraftingNetworkComponent.class).getOutputs()
                .contains(new ItemResource(output));
    }

    static long stored(ServerLevel level, BlockPos pos, Item item) {
        Network network = network(level, pos);
        return network == null ? 0 : network.getComponent(StorageNetworkComponent.class).get(new ItemResource(item));
    }

    /** Insere {@code count} de {@code item} na rede; devolve quanto entrou. */
    static long stock(ServerLevel level, BlockPos pos, Item item, int count) {
        Network network = network(level, pos);
        return network == null ? 0 : network.getComponent(StorageNetworkComponent.class)
                .insert(new ItemResource(item), count, Action.EXECUTE, Actor.EMPTY);
    }

    /** Tira {@code count} de {@code item} da rede; devolve quanto saiu. */
    static long take(ServerLevel level, BlockPos pos, Item item, int count) {
        Network network = network(level, pos);
        return network == null ? 0 : network.getComponent(StorageNetworkComponent.class)
                .extract(new ItemResource(item), count, Action.EXECUTE, Actor.EMPTY);
    }

    /** Pede {@code count} de {@code item} à rede; devolve o nome do resultado ({@code TASK_CREATED} se começou). */
    static String request(ServerLevel level, BlockPos pos, Item item, int count) {
        Network network = network(level, pos);
        if (network == null) {
            return "sem rede";
        }
        return network.getComponent(AutocraftingNetworkComponent.class).ensureTask(new ItemResource(item), count,
                Actor.EMPTY, CancellationToken.NONE).name();
    }

    /** Tarefas de autocrafting em curso na rede. */
    static int tasks(ServerLevel level, BlockPos pos) {
        Network network = network(level, pos);
        return network == null ? -1 : network.getComponent(AutocraftingNetworkComponent.class).getStatuses().size();
    }
}
