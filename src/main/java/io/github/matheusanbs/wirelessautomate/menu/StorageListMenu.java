package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.IPayloadContext;
import io.github.matheusanbs.wirelessautomate.net.PacketDistributor;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.ServerMenus;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.packet.StorageActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.StorageEntriesPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.storage.FluidStorage;
import io.github.matheusanbs.wirelessautomate.storage.KeyedStorage;
import io.github.matheusanbs.wirelessautomate.storage.KeyedStorageBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChemicalTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela em lista dos armazenamentos por tipo (Baú, Tanque, Tanque Químico). Os únicos slots
 * são os do inventário do jogador; a lista não é de slots (os tipos são ilimitados): a tela desenha
 * a {@link StorageListView} e manda os cliques como {@link StorageActionPayload}, que o servidor
 * valida e aplica ({@link #handle}).
 *
 * <p>Sincronização (servidor): o vanilla só chama {@link #broadcastChanges()} no menu aberto, então
 * nada roda com a tela fechada. Quando a versão do conteúdo muda, o menu compara cada tipo com o que
 * já mandou e envia só as diferenças, no máximo a cada {@link #SYNC_INTERVAL} ticks (a primeira vai
 * na hora). Um armazenamento com milhões chegando não inunda o cliente.
 */
public class StorageListMenu<K> extends AbstractContainerMenu {
    public static final double MAX_DISTANCE = 8.0;
    /** Ticks mínimos entre dois envios de diferenças. */
    public static final int SYNC_INTERVAL = 5;
    /** Posição inicial do inventário do jogador (a tela o move ao redimensionar, {@link #placeInventory}). */
    public static final int INVENTORY_X = 14;
    public static final int INVENTORY_Y = 152;

    private final ListKind<K> kind;
    private final StorageListView<K> view;
    private final BlockPos pos;

    // Só no servidor.
    private final @Nullable KeyedStorageBlockEntity<K> storage;
    private final @Nullable ServerPlayer viewer;
    private final Object2LongOpenCustomHashMap<K> sent;
    private int sentVersion = -1;
    private StorageListView.Header sentHeader = StorageListView.Header.EMPTY;
    private long lastSync = Long.MIN_VALUE;

    /** Servidor. */
    public StorageListMenu(int containerId, Inventory inventory, KeyedStorageBlockEntity<K> storage) {
        this(containerId, inventory, ListKind.of(storage.kind()), storage.getBlockPos(), storage);
    }

    /** Cliente: o buffer de abertura traz o tipo e a posição; os tipos chegam pelos {@link StorageEntriesPayload}. */
    public static StorageListMenu<?> fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        StorageKind kind = buf.readEnum(StorageKind.class);
        return new StorageListMenu<>(containerId, inventory, ListKind.of(kind), GameCodecs.BLOCK_POS.decode(buf), null);
    }

    /** Cliente (e a captura de tela de desenvolvimento). */
    public StorageListMenu(int containerId, Inventory inventory, ListKind<K> kind, BlockPos pos) {
        this(containerId, inventory, kind, pos, null);
    }

    private StorageListMenu(int containerId, Inventory inventory, ListKind<K> kind, BlockPos pos,
            @Nullable KeyedStorageBlockEntity<K> storage) {
        super(ModMenus.STORAGE_LIST.get(), containerId);
        this.kind = kind;
        this.view = new StorageListView<>(kind);
        this.pos = pos;
        this.storage = storage;
        this.viewer = storage != null && inventory.player instanceof ServerPlayer player ? player : null;
        this.sent = new Object2LongOpenCustomHashMap<>(kind.strategy);
        addInventory(inventory);
    }

    public static void open(ServerPlayer player, KeyedStorageBlockEntity<?> storage) {
        ServerMenus.openMenu(player, new SimpleMenuProvider(
                        (containerId, inventory, p) -> new StorageListMenu<>(containerId, inventory, storage),
                        storage.getBlockState().getBlock().getName()),
                buf -> {
                    buf.writeEnum(storage.kind());
                    GameCodecs.BLOCK_POS.encode(buf, storage.getBlockPos());
                });
    }

    private void addInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    /**
     * Cliente: muda o inventário do jogador de lugar na tela (a tela é redimensionável). A posição
     * de um slot só serve para desenhar e clicar, e o {@link Slot} a guarda como {@code final}; então
     * os 36 slots são trocados por iguais na posição nova, com o mesmo índice no menu e no
     * inventário. O servidor não usa a posição, e nada muda para ele.
     */
    public void placeInventory(int x, int y) {
        for (int i = 0; i < slots.size(); i++) {
            Slot old = slots.get(i);
            int containerSlot = old.getContainerSlot();
            int sx = x + (containerSlot % 9) * 18;
            int sy = containerSlot < 9 ? y + 58 : y + (containerSlot / 9 - 1) * 18;
            if (old.x == sx && old.y == sy) {
                continue;
            }
            Slot moved = new Slot(old.container, containerSlot, sx, sy);
            moved.index = old.index;
            slots.set(i, moved);
        }
    }

    public ListKind<K> kind() {
        return kind;
    }

    public StorageListView<K> view() {
        return view;
    }

    public BlockPos pos() {
        return pos;
    }

    /** O armazenamento; só existe no servidor. */
    public @Nullable KeyedStorageBlockEntity<K> storage() {
        return storage;
    }

    @Override
    public boolean stillValid(Player player) {
        return storage == null || stillValid(storage, player);
    }

    static boolean stillValid(BlockEntity block, Player player) {
        if (block.isRemoved() || block.getLevel() != player.level()
                || player.level().getBlockEntity(block.getBlockPos()) != block) {
            return false;
        }
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(block.getBlockPos())) <= MAX_DISTANCE * MAX_DISTANCE;
    }

    /**
     * Shift + clique no inventário: no Baú guarda a pilha; nos Tanques esvazia os recipientes da
     * pilha (baldes, tanques de outros mods), um por um, e devolve os vazios ao inventário.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (storage == null || index < 0 || index >= slots.size() || !stillValid(player)) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (kind == ListKind.ITEMS) {
            @SuppressWarnings("unchecked")
            KeyedStorage<ItemStack> items = (KeyedStorage<ItemStack>) storage.keyed();
            long accepted = items.insert(stack, stack.getCount(), false);
            if (accepted > 0) {
                slot.remove((int) accepted);
                slot.setChanged();
            }
            return ItemStack.EMPTY;
        }
        while (!slot.getItem().isEmpty()) {
            ItemStack container = slot.getItem().copyWithCount(1);
            ItemStack emptied = emptyContainer(player, container);
            if (emptied == null) {
                break;
            }
            slot.remove(1);
            slot.setChanged();
            give(player, emptied, false);
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ sincronização

    /** Um envio: o primeiro de uma sincronização inteira leva {@code reset}. */
    public record Sync<K>(boolean reset, StorageListView.Header header, List<StorageListView.Entry<K>> changes) {
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        Sync<K> sync = poll();
        if (sync != null) {
            send(sync);
        }
    }

    /**
     * O que mandar agora, ou {@code null}: nada mudou, ou o último envio foi há menos de
     * {@link #SYNC_INTERVAL} ticks (a mudança espera o próximo). Marca como enviado. Só no servidor;
     * público para os GameTests.
     */
    public @Nullable Sync<K> poll() {
        if (storage == null || viewer == null) {
            return null;
        }
        KeyedStorage<K> keyed = storage.keyed();
        StorageListView.Header header = new StorageListView.Header(keyed.total(), storage.capacity(),
                !storage.filter().isEmpty());
        boolean contentsChanged = keyed.version() != sentVersion;
        if (!contentsChanged && header.equals(sentHeader)) {
            return null;
        }
        long now = viewer.server.getTickCount();
        if (sentVersion != -1 && now - lastSync < SYNC_INTERVAL) {
            return null;
        }
        boolean reset = sentVersion == -1;
        List<StorageListView.Entry<K>> changes = contentsChanged ? diff(keyed) : List.of();
        sentVersion = keyed.version();
        sentHeader = header;
        lastSync = now;
        return new Sync<>(reset, header, changes);
    }

    /** Tipos com quantidade diferente da última enviada, e os que saíram (com 0). Marca como enviados. */
    private List<StorageListView.Entry<K>> diff(KeyedStorage<K> keyed) {
        List<StorageListView.Entry<K>> changes = new ArrayList<>();
        for (int i = 0, n = keyed.types(); i < n; i++) {
            K key = keyed.key(i);
            long count = keyed.count(i);
            if (!sent.containsKey(key)) {
                K copy = kind.copy(key);
                sent.put(copy, count);
                changes.add(new StorageListView.Entry<>(copy, count));
            } else if (sent.getLong(key) != count) {
                sent.put(key, count);
                changes.add(new StorageListView.Entry<>(kind.copy(key), count));
            }
        }
        if (sent.size() > keyed.types()) {
            ObjectIterator<Object2LongMap.Entry<K>> it = sent.object2LongEntrySet().fastIterator();
            while (it.hasNext()) {
                K key = it.next().getKey();
                if (keyed.count(key) <= 0) {
                    changes.add(new StorageListView.Entry<>(key, 0));
                    it.remove();
                }
            }
        }
        return changes;
    }

    /** Manda em pacotes de até {@link StorageEntriesPayload#MAX_ENTRIES} tipos; só o primeiro leva o {@code reset}. */
    @SuppressWarnings("unchecked")
    private void send(Sync<K> sync) {
        List<StorageListView.Entry<K>> changes = sync.changes();
        int max = StorageEntriesPayload.MAX_ENTRIES;
        int from = 0;
        do {
            int to = Math.min(changes.size(), from + max);
            List<StorageListView.Entry<Object>> part = new ArrayList<>(to - from);
            for (StorageListView.Entry<K> entry : changes.subList(from, to)) {
                part.add((StorageListView.Entry<Object>) (StorageListView.Entry<?>) entry);
            }
            StorageEntriesPayload payload = new StorageEntriesPayload(containerId, kind.storage,
                    sync.reset() && from == 0, sync.header(), part);
            // Jogadores falsos (GameTests, mods de automação) não negociam os canais do mod.
            if (viewer.connection != null && PacketDistributor.hasChannel(viewer.connection, payload)) {
                PacketDistributor.sendToPlayer(viewer, payload);
            }
            from = to;
        } while (from < changes.size());
    }

    /** Cliente: um pacote do servidor. */
    @SuppressWarnings("unchecked")
    public static void onEntries(StorageEntriesPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof StorageListMenu<?> menu && menu.containerId == payload.containerId()
                && menu.kind.storage == payload.kind()) {
            StorageListMenu<Object> typed = (StorageListMenu<Object>) menu;
            typed.view.apply(payload.reset(), payload.header(), payload.entries());
        }
    }

    // ------------------------------------------------------------------ ações

    /** Um clique na lista. Devolve se a ação foi aceita. */
    @SuppressWarnings("unchecked")
    public static boolean handle(@Nullable ServerPlayer player, StorageActionPayload payload) {
        StorageListMenu<Object> menu = player != null && player.containerMenu instanceof StorageListMenu<?> m
                && m.containerId == payload.containerId() && m.kind.storage == payload.kind()
                ? (StorageListMenu<Object>) m : null;
        if (menu == null || menu.storage == null || !menu.stillValid(player)) {
            return false;
        }
        if (payload.action() == StorageActionPayload.Action.OPEN_FILTER) {
            new StorageFilterTarget(menu.storage).open(player);
            return true;
        }
        Optional<Object> key = payload.key();
        return switch (menu.kind.storage) {
            case CHEST -> menu.handleItems(player, payload.action(), key.map(ItemStack.class::cast).orElse(ItemStack.EMPTY),
                    payload.slot());
            case TANK, CHEMICAL_TANK -> menu.handleContainers(player, payload.action(), key.orElse(null));
            case BATTERY, SOURCE_TANK -> false;
        };
    }

    /**
     * Baú: pilha para o cursor, meia pilha, para o inventário, guardar o cursor, a rodinha (um item
     * por vez, sobre um tipo ou sobre um slot do inventário) e o Shift + duplo clique com item no cursor (o máximo que couber; o cursor fica).
     */
    @SuppressWarnings("unchecked")
    private boolean handleItems(ServerPlayer player, StorageActionPayload.Action action, ItemStack key, int slotIndex) {
        KeyedStorage<ItemStack> items = (KeyedStorage<ItemStack>) (KeyedStorage<?>) storage.keyed();
        ItemStack carried = getCarried();
        switch (action) {
            case TAKE_STACK, TAKE_HALF -> {
                long present = key.isEmpty() ? 0 : items.count(key);
                if (present <= 0 || !carried.isEmpty() && !ItemStack.isSameItemSameTags(carried, key)) {
                    return false;
                }
                int max = key.getMaxStackSize();
                int want = action == StorageActionPayload.Action.TAKE_HALF ? (max + 1) / 2 : max;
                want = (int) Math.min(want, present);
                want = Math.min(want, max - carried.getCount());
                if (want <= 0) {
                    return false;
                }
                long taken = items.extract(key, want, false);
                if (carried.isEmpty()) {
                    setCarried(key.copyWithCount((int) taken));
                } else {
                    carried.grow((int) taken);
                }
            }
            case TAKE_TO_INVENTORY -> {
                long present = key.isEmpty() ? 0 : items.count(key);
                if (present <= 0) {
                    return false;
                }
                ItemStack moving = key.copyWithCount((int) Math.min(key.getMaxStackSize(), present));
                int added = addToInventory(player.getInventory(), moving);
                if (added <= 0) {
                    return false;
                }
                items.extract(key, added, false);
            }
            case INSERT_CARRIED, INSERT_CARRIED_ONE -> {
                if (carried.isEmpty()) {
                    return false;
                }
                int want = action == StorageActionPayload.Action.INSERT_CARRIED_ONE ? 1 : carried.getCount();
                long accepted = items.insert(carried, want, false);
                if (accepted <= 0) {
                    return false;
                }
                carried.shrink((int) accepted);
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            case TAKE_ONE_TO_INVENTORY -> {
                if (key.isEmpty() || items.count(key) <= 0) {
                    return false;
                }
                if (addToInventory(player.getInventory(), key.copyWithCount(1)) <= 0) {
                    return false;
                }
                items.extract(key, 1, false);
            }
            case INSERT_ONE_FROM_INVENTORY -> {
                Slot from = key.isEmpty() ? null : slots.stream()
                        .filter(s -> s.mayPickup(player) && ItemStack.isSameItemSameTags(s.getItem(), key))
                        .findFirst().orElse(null);
                if (from == null || items.insert(from.getItem(), 1, false) <= 0) {
                    return false;
                }
                from.remove(1);
                from.setChanged();
            }
            case INSERT_ONE_FROM_SLOT -> {
                Slot from = slotIndex >= 0 && slotIndex < slots.size() ? slots.get(slotIndex) : null;
                if (from == null || from.getItem().isEmpty() || !from.mayPickup(player)
                        || items.insert(from.getItem(), 1, false) <= 0) {
                    return false;
                }
                from.remove(1);
                from.setChanged();
            }
            case TAKE_ONE_TO_SLOT -> {
                Slot to = slotIndex >= 0 && slotIndex < slots.size() ? slots.get(slotIndex) : null;
                ItemStack there = to == null ? ItemStack.EMPTY : to.getItem();
                if (there.isEmpty() || there.getCount() >= to.getMaxStackSize(there) || !to.mayPlace(there)
                        || items.count(there) <= 0) {
                    return false;
                }
                items.extract(there, 1, false);
                there.grow(1);
                to.setChanged();
            }
            case TAKE_ALL_TO_INVENTORY -> {
                if (key.isEmpty()) {
                    return false;
                }
                // Enche o inventário pilha por pilha e tira do Baú uma vez só no fim: cada retirada
                // salva o chunk e avisa os vizinhos.
                long left = items.count(key);
                long moved = 0;
                while (left > 0) {
                    ItemStack moving = key.copyWithCount((int) Math.min(key.getMaxStackSize(), left));
                    int added = addToInventory(player.getInventory(), moving);
                    if (added <= 0) {
                        break;
                    }
                    left -= added;
                    moved += added;
                }
                if (moved <= 0) {
                    return false;
                }
                items.extract(key, moved, false);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Tanques: com um recipiente no cursor, clicar num tipo enche um recipiente com ele, e clicar
     * na lista com um recipiente cheio esvazia um (ou todos, no clique esquerdo). O recipiente usado
     * volta ao cursor, ou ao inventário quando o cursor tem mais de um.
     */
    private boolean handleContainers(ServerPlayer player, StorageActionPayload.Action action, @Nullable Object key) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return false;
        }
        switch (action) {
            case TAKE_STACK, TAKE_HALF -> {
                if (key == null) {
                    return false;
                }
                // Enche com o tipo clicado; se o recipiente já está cheio (não enche), esvazia um.
                ItemStack filled = fillContainer(player, carried.copyWithCount(1), key);
                if (filled == null) {
                    filled = emptyContainer(player, carried.copyWithCount(1));
                }
                if (filled == null) {
                    return false;
                }
                carried.shrink(1);
                give(player, filled, true);
            }
            case INSERT_CARRIED, INSERT_CARRIED_ONE -> {
                int times = action == StorageActionPayload.Action.INSERT_CARRIED ? carried.getCount() : 1;
                boolean any = false;
                for (int i = 0; i < times && !getCarried().isEmpty(); i++) {
                    ItemStack emptied = emptyContainer(player, getCarried().copyWithCount(1));
                    if (emptied == null) {
                        break;
                    }
                    getCarried().shrink(1);
                    give(player, emptied, true);
                    any = true;
                }
                return any;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Enche um recipiente (pilha de 1) com o tipo {@code key}; o recipiente cheio, ou {@code null}. */
    private @Nullable ItemStack fillContainer(Player player, ItemStack container, Object key) {
        if (storage instanceof StorageTankBlockEntity tank && key instanceof FluidStack fluid) {
            FluidActionResult result = FluidUtil.tryFillContainer(container, new OnlyFluid(tank.storage(), fluid),
                    Integer.MAX_VALUE, player, true);
            return result.isSuccess() ? result.getResult() : null;
        }
        if (storage instanceof StorageChemicalTankBlockEntity tank && key instanceof ResourceLocation id) {
            return Chemicals.fillContainer(container, tank.storage(), id) > 0 ? container : null;
        }
        return null;
    }

    /** Esvazia um recipiente (pilha de 1) no tanque; o recipiente vazio, ou {@code null} se nada passou. */
    private @Nullable ItemStack emptyContainer(Player player, ItemStack container) {
        if (storage instanceof StorageTankBlockEntity tank) {
            FluidActionResult result = FluidUtil.tryEmptyContainer(container, tank.handler(), Integer.MAX_VALUE, player, true);
            return result.isSuccess() ? result.getResult() : null;
        }
        if (storage instanceof StorageChemicalTankBlockEntity tank) {
            return Chemicals.emptyContainer(container, tank.storage()) > 0 ? container : null;
        }
        return null;
    }

    /** O recipiente depois da troca: no cursor se ele ficou vazio, senão no inventário (ou no chão). */
    private void give(Player player, ItemStack stack, boolean toCursor) {
        if (stack.isEmpty()) {
            return;
        }
        if (toCursor && getCarried().isEmpty()) {
            setCarried(stack);
        } else {
            addToInventory(player.getInventory(), stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    /**
     * Põe no inventário o que couber da pilha (que diminui) e devolve quanto entrou. Não usa o
     * {@code Inventory.add}: no criativo ele apaga o que não cabe e diz que guardou tudo, e o resto
     * sairia do Baú e sumiria.
     */
    private static int addToInventory(Inventory inventory, ItemStack stack) {
        int added = 0;
        while (!stack.isEmpty()) {
            int slot = inventory.getSlotWithRemainingSpace(stack);
            if (slot < 0) {
                slot = inventory.getFreeSlot();
            }
            if (slot < 0) {
                break;
            }
            ItemStack there = inventory.getItem(slot);
            int max = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
            int moving = Math.min(stack.getCount(), there.isEmpty() ? max : max - there.getCount());
            if (moving <= 0) {
                break;
            }
            if (there.isEmpty()) {
                inventory.setItem(slot, stack.copyWithCount(moving));
            } else {
                there.grow(moving);
            }
            stack.shrink(moving);
            added += moving;
        }
        if (added > 0) {
            inventory.setChanged();
        }
        return added;
    }

    /** O Tanque visto como uma fonte de um fluido só, para encher um recipiente com o fluido clicado. */
    private record OnlyFluid(FluidStorage storage, FluidStack key) implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return new FluidStack(key, (int) Math.min(storage.count(key), Integer.MAX_VALUE));
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return resource.isFluidEqual(key) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            long taken = storage.extract(key, maxDrain, action.simulate());
            return taken <= 0 ? FluidStack.EMPTY : new FluidStack(key, (int) taken);
        }
    }

    /** O tier do armazenamento da tela, pelo bloco no mundo do cliente (o mesmo que o servidor vê). */
    public static int tierOrdinal(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(RouterBlock.TIER) ? state.getValue(RouterBlock.TIER).ordinal() : 0;
    }
}
