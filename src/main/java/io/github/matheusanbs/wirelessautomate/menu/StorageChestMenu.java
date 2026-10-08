package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.packet.StorageActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.StorageEntriesPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela do Baú. Os únicos slots são os do inventário do jogador; a lista de tipos não é de
 * slots (são ilimitados): a tela desenha a {@link StorageChestView} e manda os cliques como
 * {@link StorageActionPayload}, que o servidor valida e aplica ({@link #handle}).
 *
 * <p>Sincronização (servidor): o vanilla só chama {@link #broadcastChanges()} no menu aberto, então
 * nada roda com a tela fechada. Quando a {@link ItemStorage#version()} muda, o menu compara cada
 * tipo com o que já mandou e envia só as diferenças, no máximo a cada {@link #SYNC_INTERVAL} ticks
 * (a primeira vai na hora). Um Baú com milhões de itens chegando não inunda o cliente.
 */
public class StorageChestMenu extends AbstractContainerMenu {
    public static final double MAX_DISTANCE = 8.0;
    /** Ticks mínimos entre dois envios de diferenças. */
    public static final int SYNC_INTERVAL = 5;
    /** Posição do inventário do jogador na tela (canto do primeiro slot da mochila). */
    public static final int INVENTORY_X = 14;
    public static final int INVENTORY_Y = 152;

    private final StorageChestView view = new StorageChestView();
    private final BlockPos pos;

    // Só no servidor.
    private final @Nullable StorageChestBlockEntity chest;
    private final @Nullable ServerPlayer viewer;
    private final Object2LongOpenCustomHashMap<ItemStack> sent =
            new Object2LongOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
    private int sentVersion = -1;
    private StorageChestView.Header sentHeader = StorageChestView.Header.EMPTY;
    private long lastSync = Long.MIN_VALUE;

    /** Servidor. */
    public StorageChestMenu(int containerId, Inventory inventory, StorageChestBlockEntity chest) {
        super(ModMenus.STORAGE_CHEST.get(), containerId);
        this.chest = chest;
        this.pos = chest.getBlockPos();
        this.viewer = inventory.player instanceof ServerPlayer player ? player : null;
        addInventory(inventory);
    }

    /** Cliente: o buffer de abertura traz a posição; os tipos chegam pelos {@link StorageEntriesPayload}. */
    public StorageChestMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, BlockPos.STREAM_CODEC.decode(buf));
    }

    /** Cliente (e a captura de tela de desenvolvimento). */
    public StorageChestMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.STORAGE_CHEST.get(), containerId);
        this.chest = null;
        this.pos = pos;
        this.viewer = null;
        addInventory(inventory);
    }

    public static void open(ServerPlayer player, StorageChestBlockEntity chest) {
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new StorageChestMenu(containerId, inventory, chest),
                        chest.getBlockState().getBlock().getName()),
                buf -> BlockPos.STREAM_CODEC.encode(buf, chest.getBlockPos()));
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
     * Cliente: muda o inventário do jogador de lugar na tela (a tela do Baú é redimensionável). A
     * posição de um slot só serve para desenhar e clicar, e o {@link Slot} a guarda como
     * {@code final}; então os 36 slots são trocados por iguais na posição nova, com o mesmo índice
     * no menu e no inventário. O servidor não usa a posição, e nada muda para ele.
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

    public StorageChestView view() {
        return view;
    }

    public BlockPos pos() {
        return pos;
    }

    /** O Baú; só existe no servidor. */
    public @Nullable StorageChestBlockEntity chest() {
        return chest;
    }

    @Override
    public boolean stillValid(Player player) {
        return chest == null || stillValid(chest, player);
    }

    static boolean stillValid(StorageChestBlockEntity chest, Player player) {
        if (chest.isRemoved() || chest.getLevel() != player.level()
                || player.level().getBlockEntity(chest.getBlockPos()) != chest) {
            return false;
        }
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(chest.getBlockPos())) <= MAX_DISTANCE * MAX_DISTANCE;
    }

    /** Shift + clique no inventário: guarda a pilha no Baú (o que o filtro e a capacidade deixarem). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (chest == null || index < 0 || index >= slots.size() || !stillValid(player)) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long accepted = chest.storage().insert(stack, stack.getCount(), false);
        if (accepted > 0) {
            slot.remove((int) accepted);
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ sincronização

    /** Um envio: o primeiro de uma sincronização inteira leva {@code reset}. */
    public record Sync(boolean reset, StorageChestView.Header header, List<StorageChestView.Entry> changes) {
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        Sync sync = poll();
        if (sync != null) {
            send(sync);
        }
    }

    /**
     * O que mandar agora, ou {@code null}: nada mudou, ou o último envio foi há menos de
     * {@link #SYNC_INTERVAL} ticks (a mudança espera o próximo). Marca como enviado. Só no servidor;
     * público para os GameTests.
     */
    public @Nullable Sync poll() {
        if (chest == null || viewer == null) {
            return null;
        }
        ItemStorage storage = chest.storage();
        StorageChestView.Header header = new StorageChestView.Header(storage.total(), chest.capacity(),
                !chest.filter().isEmpty());
        boolean contentsChanged = storage.version() != sentVersion;
        if (!contentsChanged && header.equals(sentHeader)) {
            return null;
        }
        long now = viewer.server.getTickCount();
        if (sentVersion != -1 && now - lastSync < SYNC_INTERVAL) {
            return null;
        }
        boolean reset = sentVersion == -1;
        List<StorageChestView.Entry> changes = contentsChanged ? diff(storage) : List.of();
        sentVersion = storage.version();
        sentHeader = header;
        lastSync = now;
        return new Sync(reset, header, changes);
    }

    /** Tipos com quantidade diferente da última enviada, e os que saíram (com 0). Marca como enviados. */
    private List<StorageChestView.Entry> diff(ItemStorage storage) {
        List<StorageChestView.Entry> changes = new ArrayList<>();
        for (int i = 0, n = storage.types(); i < n; i++) {
            ItemStack key = storage.key(i);
            long count = storage.count(i);
            if (!sent.containsKey(key)) {
                ItemStack copy = key.copy();
                sent.put(copy, count);
                changes.add(new StorageChestView.Entry(copy, count));
            } else if (sent.getLong(key) != count) {
                sent.put(key, count);
                changes.add(new StorageChestView.Entry(key.copy(), count));
            }
        }
        if (sent.size() > storage.types()) {
            ObjectIterator<Object2LongMap.Entry<ItemStack>> it = sent.object2LongEntrySet().fastIterator();
            while (it.hasNext()) {
                ItemStack key = it.next().getKey();
                if (storage.count(key) <= 0) {
                    changes.add(new StorageChestView.Entry(key, 0));
                    it.remove();
                }
            }
        }
        return changes;
    }

    /** Manda em pacotes de até {@link StorageEntriesPayload#MAX_ENTRIES} tipos; só o primeiro leva o {@code reset}. */
    private void send(Sync sync) {
        List<StorageChestView.Entry> changes = sync.changes();
        int max = StorageEntriesPayload.MAX_ENTRIES;
        int from = 0;
        do {
            int to = Math.min(changes.size(), from + max);
            StorageEntriesPayload payload = new StorageEntriesPayload(containerId, sync.reset() && from == 0,
                    sync.header(), List.copyOf(changes.subList(from, to)));
            // Jogadores falsos (GameTests, mods de automação) não negociam os canais do mod.
            if (viewer.connection != null && viewer.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(viewer, payload);
            }
            from = to;
        } while (from < changes.size());
    }

    /** Cliente: um pacote do servidor. */
    public static void onEntries(StorageEntriesPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof StorageChestMenu menu && menu.containerId == payload.containerId()) {
            menu.view.apply(payload.reset(), payload.header(), payload.entries());
        }
    }

    // ------------------------------------------------------------------ ações

    /** Um clique na lista. Devolve se a ação foi aceita. */
    public static boolean handle(@Nullable ServerPlayer player, StorageActionPayload payload) {
        StorageChestMenu menu = player != null && player.containerMenu instanceof StorageChestMenu m
                && m.containerId == payload.containerId() ? m : null;
        if (menu == null || menu.chest == null || !menu.stillValid(player)) {
            return false;
        }
        StorageChestBlockEntity chest = menu.chest;
        ItemStorage storage = chest.storage();
        ItemStack key = payload.key();
        ItemStack carried = menu.getCarried();
        switch (payload.action()) {
            case TAKE_STACK, TAKE_HALF -> {
                long present = key.isEmpty() ? 0 : storage.count(key);
                if (present <= 0 || !carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, key)) {
                    return false;
                }
                int max = key.getMaxStackSize();
                int want = payload.action() == StorageActionPayload.Action.TAKE_HALF ? (max + 1) / 2 : max;
                want = (int) Math.min(want, present);
                want = Math.min(want, max - carried.getCount());
                if (want <= 0) {
                    return false;
                }
                long taken = storage.extract(key, want, false);
                if (carried.isEmpty()) {
                    menu.setCarried(key.copyWithCount((int) taken));
                } else {
                    carried.grow((int) taken);
                }
            }
            case TAKE_TO_INVENTORY -> {
                long present = key.isEmpty() ? 0 : storage.count(key);
                if (present <= 0) {
                    return false;
                }
                ItemStack moving = key.copyWithCount((int) Math.min(key.getMaxStackSize(), present));
                int offered = moving.getCount();
                player.getInventory().add(moving);
                int added = offered - moving.getCount();
                if (added <= 0) {
                    return false;
                }
                storage.extract(key, added, false);
            }
            case INSERT_CARRIED, INSERT_CARRIED_ONE -> {
                if (carried.isEmpty()) {
                    return false;
                }
                int want = payload.action() == StorageActionPayload.Action.INSERT_CARRIED_ONE ? 1 : carried.getCount();
                long accepted = storage.insert(carried, want, false);
                if (accepted <= 0) {
                    return false;
                }
                carried.shrink((int) accepted);
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            case OPEN_FILTER -> new StorageChestFilterTarget(chest).open(player);
        }
        return true;
    }

    /** O tier do Baú da tela, pelo bloco no mundo do cliente (o mesmo que o servidor vê). */
    public static int tierOrdinal(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(RouterBlock.TIER) ? state.getValue(RouterBlock.TIER).ordinal() : 0;
    }
}
