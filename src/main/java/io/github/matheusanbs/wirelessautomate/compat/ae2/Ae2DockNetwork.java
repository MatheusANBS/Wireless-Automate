package io.github.matheusanbs.wirelessautomate.compat.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import io.github.matheusanbs.wirelessautomate.dock.DockReturnNetwork;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * O destino da volta num lado encostado num nó do AE2 (o Pattern Provider, em bloco ou em parte): o armazenamento da
 * rede ME dele, visto pela volta da Doca como um inventário e um tanque de um espaço, sempre vazios, que só aceitam.
 * O {@code CraftingServiceStorage} do AE2 fica montado na rede com prioridade máxima e é o preferido para tudo, então o
 * que entra pelo {@code getInventory().insert} passa antes pelas CPUs que esperam aquele item: o pedido conta o
 * resultado inserido direto, sem o retorno de 9 espaços do provedor (que limitava a volta) e sem o provedor no meio.
 *
 * <p>O nó é o da face do vizinho encostada na Doca ({@code getGridNode(face)}, ou o principal, {@link #find}), e só serve ativo (com energia, a rede
 * pronta e o canal). Guarda o nó, a grade, o armazenamento e a fonte (uma máquina, o próprio nó); a cada inserção só
 * confere que o nó continua ativo e na mesma grade (três leituras de campo): a grade troca quando a rede se divide ou
 * se junta, e a velha não pode receber nada. A busca (uma consulta de capability) só se repete então ou no
 * {@link #reset}, pelo cache de capability do lado. Sem nó ativo ou sem espaço na rede, aceita zero: a volta marca
 * "provedor cheio" e dorme.
 *
 * <p>Com o Applied Mekanistics ({@link Ae2Chemicals#LOADED}), a rede guarda químicos: o {@code ChemicalStack} entra
 * como {@code MekanismKey} pelo mesmo {@code insert}, e a CPU que espera o químico o conta como conta um item.
 */
final class Ae2DockNetwork implements DockReturnNetwork {
    private final ServerLevel level;
    private final BlockPos pos;
    private final Direction face;
    private @Nullable IGridNode node;
    private @Nullable IGrid grid;
    private @Nullable MEStorage storage;
    private @Nullable IActionSource source;
    /**
     * A chave da última pilha inserida, reaproveitada enquanto o item (ou o fluido) e os componentes são os mesmos: a
     * volta simula e executa com a mesma pilha, e a saída de uma máquina costuma se repetir.
     */
    private @Nullable AEItemKey itemKey;
    private @Nullable AEFluidKey fluidKey;
    private @Nullable AEKey chemicalKey;

    Ae2DockNetwork(ServerLevel level, BlockPos pos, Direction face) {
        this.level = level;
        this.pos = pos.immutable();
        this.face = face;
    }

    @Override
    public void reset() {
        node = null;
        grid = null;
        storage = null;
        source = null;
    }

    /** O armazenamento da rede do nó; {@code null} sem nó ativo (ou com o chunk do vizinho descarregado). */
    private @Nullable MEStorage storage() {
        // isActive antes do getGrid: o getGrid de um nó destruído lança exceção, e o isActive dele é falso.
        if (node != null && node.isActive() && node.getGrid() == grid) {
            return storage;
        }
        reset();
        if (!level.isLoaded(pos)) {
            return null;
        }
        IGridNode found = find(level, pos, face);
        if (found == null || !found.isActive()) {
            return null;
        }
        node = found;
        grid = found.getGrid();
        storage = grid.getStorageService().getInventory();
        source = IActionSource.ofMachine(() -> found);
        return storage;
    }

    /**
     * O nó do vizinho em {@code pos}: o da face encostada na Doca; senão o principal do bloco (o Pattern Provider
     * direcional que empurra para a Doca não expõe o nó nessa face) ou o da parte nessa face (uma parte sem cabo).
     */
    static @Nullable IGridNode find(ServerLevel level, BlockPos pos, Direction face) {
        IInWorldGridNodeHost host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
        if (host == null) {
            return null;
        }
        IGridNode node = host.getGridNode(face);
        if (node != null) {
            return node;
        }
        if (host instanceof IActionHost action) {
            return action.getActionableNode();
        }
        IPart part = host instanceof IPartHost parts ? parts.getPart(face) : null;
        return part == null ? null : part.getGridNode();
    }

    private long insert(@Nullable AEKey key, long amount, boolean simulate) {
        MEStorage target = key == null ? null : storage();
        return target == null ? 0 : target.insert(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                source);
    }

    // ------------------------------------------------------------------ químicos (mB, com o Applied Mekanistics)

    @Override
    public boolean acceptsChemicals() {
        return Ae2Chemicals.LOADED;
    }

    @Override
    public long insertChemical(Object stack, boolean simulate) {
        chemicalKey = Ae2Chemicals.key(stack, chemicalKey);
        return insert(chemicalKey, Chemicals.amount(stack), simulate);
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
        if (itemKey == null || !itemKey.matches(stack)) {
            itemKey = AEItemKey.of(stack);
        }
        long inserted = insert(itemKey, stack.getCount(), simulate);
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

    // ------------------------------------------------------------------ fluidos (mB, a unidade do AE2 no NeoForge)

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
        if (fluidKey == null || !fluidKey.matches(resource)) {
            fluidKey = AEFluidKey.of(resource);
        }
        long inserted = insert(fluidKey, resource.getAmount(), action.simulate());
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
