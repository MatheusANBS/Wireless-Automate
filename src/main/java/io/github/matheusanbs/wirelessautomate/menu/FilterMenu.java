package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela de filtro. Os únicos slots são os do inventário do jogador (aba Inventário):
 * Shift + clique num deles chega ao servidor como {@link #quickMoveStack} e acrescenta o item
 * (ou o fluido que ele contém) ao filtro, sem mover nada. No cliente guarda a última
 * {@link FilterView}; no servidor conhece o {@link FilterTarget}.
 */
public class FilterMenu extends AbstractContainerMenu {
    /** Posição do inventário do jogador na tela (canto do primeiro slot da mochila). */
    public static final int INVENTORY_X = 48;
    public static final int INVENTORY_Y = 148;
    public static final int SLOT_COUNT = 36;

    private final @Nullable FilterTarget target;
    private FilterView view;
    private int version;

    /** Servidor. */
    public FilterMenu(int containerId, Inventory inventory, FilterTarget target, FilterView view) {
        super(ModMenus.FILTER.get(), containerId);
        this.target = target;
        this.view = view;
        addInventory(inventory);
    }

    /** Cliente: a visão inicial vem no buffer de abertura. */
    public FilterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, FilterView.STREAM_CODEC.decode(buf));
    }

    /** Cliente (e a captura de tela de desenvolvimento). */
    public FilterMenu(int containerId, Inventory inventory, FilterView view) {
        super(ModMenus.FILTER.get(), containerId);
        this.target = null;
        this.view = view;
        addInventory(inventory);
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

    public @Nullable FilterTarget target() {
        return target;
    }

    public FilterView view() {
        return view;
    }

    /** Muda a cada visão recebida: a tela compara para saber quando se atualizar. */
    public int version() {
        return version;
    }

    public void applyView(FilterView view) {
        this.view = view;
        version++;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // TODO(contrato): no servidor, acrescentar o item (ou fluido contido) do slot ao filtro (agente do servidor).
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return target == null || target.stillValid(player);
    }

    // TODO(contrato): broadcastChanges() reenvia a visão quando o alvo muda (agente do servidor).
}
