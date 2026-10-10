package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.IPayloadContext;
import io.github.matheusanbs.wirelessautomate.net.PacketDistributor;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.ServerMenus;
import io.github.matheusanbs.wirelessautomate.packet.ScalarStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.storage.ScalarStorageBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela de um valor só (Bateria e Tanque de Source): sem slots, só o estado (quantidade e capacidade), mandado pelo
 * {@link ScalarStatePayload} quando muda, no máximo a cada {@link StorageListMenu#SYNC_INTERVAL}
 * ticks, e só com a tela aberta.
 */
public class StorageScalarMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final StorageKind kind;
    private final @Nullable ScalarStorageBlockEntity storage;
    private final @Nullable ServerPlayer viewer;
    private long sentStored = -1;
    private long sentCapacity = -1;
    private long lastSync;

    // Cliente: os dois últimos estados, para a variação por tick.
    private long stored;
    private long capacity;
    private long tick;
    private long rate;
    private boolean received;

    /** Servidor. */
    public StorageScalarMenu(int containerId, Inventory inventory, ScalarStorageBlockEntity storage) {
        super(ModMenus.STORAGE_SCALAR.get(), containerId);
        this.pos = storage.getBlockPos();
        this.kind = storage.kind();
        this.storage = storage;
        this.viewer = inventory.player instanceof ServerPlayer player ? player : null;
    }

    /** Cliente. */
    public StorageScalarMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, GameCodecs.BLOCK_POS.decode(buf), buf.readEnum(StorageKind.class));
    }

    /** Cliente (e a captura de tela de desenvolvimento). */
    public StorageScalarMenu(int containerId, BlockPos pos, StorageKind kind) {
        super(ModMenus.STORAGE_SCALAR.get(), containerId);
        this.pos = pos;
        this.kind = kind;
        this.storage = null;
        this.viewer = null;
    }

    public static void open(ServerPlayer player, ScalarStorageBlockEntity storage) {
        ServerMenus.openMenu(player, new SimpleMenuProvider(
                        (containerId, inventory, p) -> new StorageScalarMenu(containerId, inventory, storage),
                        storage.getBlockState().getBlock().getName()),
                buf -> {
                    GameCodecs.BLOCK_POS.encode(buf, storage.getBlockPos());
                    buf.writeEnum(storage.kind());
                });
    }

    public BlockPos pos() {
        return pos;
    }

    public StorageKind kind() {
        return kind;
    }

    public long stored() {
        return stored;
    }

    public long capacity() {
        return capacity;
    }

    /** Variação por tick entre os dois últimos estados (positiva carregando, negativa descarregando). */
    public long rate() {
        return rate;
    }

    public boolean received() {
        return received;
    }

    /** Cliente: aplica um estado; também usado pela captura de desenvolvimento. */
    public void apply(long stored, long capacity, long tick) {
        if (received && tick > this.tick) {
            rate = (stored - this.stored) / (tick - this.tick);
        }
        this.stored = stored;
        this.capacity = capacity;
        this.tick = tick;
        received = true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return storage == null || StorageListMenu.stillValid(storage, player);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (storage == null || viewer == null) {
            return;
        }
        long now = viewer.server.getTickCount();
        long value = storage.store().stored();
        long cap = storage.capacity();
        // O primeiro vai na hora; depois, quando muda (no máximo a cada SYNC_INTERVAL ticks) e a cada
        // 20 ticks sem mudança, para a variação voltar a 0 na tela.
        boolean first = sentStored < 0;
        boolean changed = value != sentStored || cap != sentCapacity;
        if (!first && (!changed && now - lastSync < 20 || changed && now - lastSync < StorageListMenu.SYNC_INTERVAL)) {
            return;
        }
        sentStored = value;
        sentCapacity = cap;
        lastSync = now;
        ScalarStatePayload payload = new ScalarStatePayload(containerId, value, cap, now);
        if (viewer.connection != null && PacketDistributor.hasChannel(viewer.connection, payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    /** Cliente: um pacote do servidor. */
    public static void onState(ScalarStatePayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof StorageScalarMenu menu && menu.containerId == payload.containerId()) {
            menu.apply(payload.stored(), payload.capacity(), payload.tick());
        }
    }
}
