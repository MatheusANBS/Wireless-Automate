package io.github.matheusanbs.wirelessautomate.compat.refinedstorage;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.support.network.AbstractNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import io.github.matheusanbs.wirelessautomate.dock.DockRs2;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * O destino da volta num lado com Autocrafter: a rede do RS2 dele, vista pela volta da Doca como um inventário e um
 * tanque de um espaço, sempre vazios, que só aceitam ({@code insertItem} e {@code fill} inserem na rede pelo
 * {@link StorageNetworkComponent}; nada sai). O resultado conta para a tarefa do RS2 ao entrar na rede por qualquer
 * caminho, então não precisa passar pelo Autocrafter, que não tem inventário.
 *
 * <p>A rede é achada pelo block entity do Autocrafter ({@link AbstractNetworkNodeContainerBlockEntity}: o nó de um
 * contêiner, a rede dele, o componente de armazenamento) e guardada com o nó. A cada inserção, só confere que o nó
 * continua na mesma rede (uma leitura): a rede troca quando o Controlador sai ou volta, e a velha não pode receber
 * nada. A busca só se repete quando a rede trocou ou quando o vizinho mudou ({@link #reset}, pelo cache de capability
 * do lado). Sem rede, aceita zero: a volta marca "provedor cheio" e dorme. Sem energia, o armazenamento da rede some
 * e a inserção também dá zero.
 */
final class Rs2DockNetwork implements DockRs2.Target {
    /** Quem insere, para o RS2 (o histórico do armazenamento). */
    private static final Actor ACTOR = () -> "Pattern Dock";

    private final ServerLevel level;
    private final BlockPos pos;
    private @Nullable NetworkNode node;
    private @Nullable Network network;
    private @Nullable StorageNetworkComponent storage;

    Rs2DockNetwork(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    @Override
    public void reset() {
        node = null;
        network = null;
        storage = null;
    }

    /** O armazenamento da rede do Autocrafter; {@code null} sem rede (ou com o chunk dele descarregado). */
    private @Nullable StorageNetworkComponent storage() {
        if (node != null && storage != null && node.getNetwork() == network) {
            return storage;
        }
        reset();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof AbstractNetworkNodeContainerBlockEntity<?> entity)
                || entity.isRemoved()) {
            return null;
        }
        for (InWorldNetworkNodeContainer container : entity.getContainerProvider().getContainers()) {
            Network found = container.getNode().getNetwork();
            if (found != null) {
                node = container.getNode();
                network = found;
                storage = found.getComponent(StorageNetworkComponent.class);
                return storage;
            }
        }
        return null;
    }

    private long insert(ResourceKey resource, long amount, boolean simulate) {
        StorageNetworkComponent target = storage();
        return target == null ? 0 : target.insert(resource, amount, simulate ? Action.SIMULATE : Action.EXECUTE, ACTOR);
    }

    // ------------------------------------------------------------------ itens

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return stack;
        }
        long inserted = insert(ItemResource.ofItemStack(stack), stack.getCount(), simulate);
        if (inserted <= 0) {
            return stack;
        }
        return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) inserted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    // ------------------------------------------------------------------ fluidos (mB, a unidade do RS2 no NeoForge)

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        long inserted = insert(new FluidResource(resource.getFluid(), resource.getComponentsPatch()),
                resource.getAmount(), action.simulate());
        return (int) Math.min(inserted, resource.getAmount());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
