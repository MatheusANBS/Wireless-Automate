package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.packet.BatteryStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.storage.ScalarStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela da Bateria: sem slots, só o estado (energia e capacidade), mandado pelo
 * {@link BatteryStatePayload} quando muda, no máximo a cada {@link StorageListMenu#SYNC_INTERVAL}
 * ticks, e só com a tela aberta.
 */
public class StorageBatteryMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final @Nullable ScalarStorageBlockEntity battery;
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
    public StorageBatteryMenu(int containerId, Inventory inventory, ScalarStorageBlockEntity battery) {
        super(ModMenus.STORAGE_BATTERY.get(), containerId);
        this.pos = battery.getBlockPos();
        this.battery = battery;
        this.viewer = inventory.player instanceof ServerPlayer player ? player : null;
    }

    /** Cliente. */
    public StorageBatteryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, BlockPos.STREAM_CODEC.decode(buf));
    }

    /** Cliente (e a captura de tela de desenvolvimento). */
    public StorageBatteryMenu(int containerId, BlockPos pos) {
        super(ModMenus.STORAGE_BATTERY.get(), containerId);
        this.pos = pos;
        this.battery = null;
        this.viewer = null;
    }

    public static void open(ServerPlayer player, ScalarStorageBlockEntity battery) {
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new StorageBatteryMenu(containerId, inventory, battery),
                        battery.getBlockState().getBlock().getName()),
                buf -> BlockPos.STREAM_CODEC.encode(buf, battery.getBlockPos()));
    }

    public BlockPos pos() {
        return pos;
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
        return battery == null || StorageListMenu.stillValid(battery, player);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (battery == null || viewer == null) {
            return;
        }
        long now = viewer.server.getTickCount();
        long value = battery.store().stored();
        long cap = battery.capacity();
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
        BatteryStatePayload payload = new BatteryStatePayload(containerId, value, cap, now);
        if (viewer.connection != null && viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    /** Cliente: um pacote do servidor. */
    public static void onState(BatteryStatePayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof StorageBatteryMenu menu && menu.containerId == payload.containerId()) {
            menu.apply(payload.stored(), payload.capacity(), payload.tick());
        }
    }
}
