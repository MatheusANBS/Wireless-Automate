package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.FilterViewPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela de filtro. Os únicos slots são os do inventário do jogador (aba Inventário):
 * Shift + clique num deles chega ao servidor como {@link #quickMoveStack} e acrescenta o item
 * (ou o fluido que ele contém) ao filtro, sem mover nada. No cliente guarda a última
 * {@link FilterView}; no servidor conhece o {@link FilterTarget}.
 *
 * <p>Sincronização (servidor): como no {@link RouterMenu}, o vanilla só chama
 * {@link #broadcastChanges()} no menu aberto, então nada roda com a tela fechada. A visão é
 * reenviada quando a {@link FilterTarget#version()} do alvo ou o "cartão na mão principal" mudam,
 * e só se ela de fato ficou diferente da última enviada.
 */
public class FilterMenu extends AbstractContainerMenu {
    /** Posição do inventário do jogador na tela (canto do primeiro slot da mochila). */
    public static final int INVENTORY_X = 48;
    public static final int INVENTORY_Y = 148;
    public static final int SLOT_COUNT = 36;

    private final @Nullable FilterTarget target;
    private FilterView view;
    private int version;

    // Só no servidor.
    private final @Nullable ServerPlayer viewer;
    private int sentTargetVersion;

    /** Servidor. {@code view} é a visão mandada no buffer de abertura. */
    public FilterMenu(int containerId, Inventory inventory, FilterTarget target, FilterView view) {
        super(ModMenus.FILTER.get(), containerId);
        this.target = target;
        this.view = view;
        this.viewer = inventory.player instanceof ServerPlayer player ? player : null;
        this.sentTargetVersion = target.version();
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
        this.viewer = null;
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

    /**
     * Shift + clique num slot do inventário: no servidor, acrescenta o item (aba de itens) ou o
     * fluido que ele contém (aba de fluidos, ex.: balde de água) ao filtro. Não move nada; no
     * cliente não faz nada. Duplicados e o teto são ignorados pelo {@link Filter#withEntry}.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (target == null || index < 0 || index >= slots.size() || !target.stillValid(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slots.get(index).getItem();
        entryFor(target.type(), stack).ifPresent(entry -> target.setFilter(target.filter().withEntry(entry)));
        return ItemStack.EMPTY;
    }

    /** A entrada que Shift + clique em {@code stack} acrescenta a um filtro de {@code type}, se houver. */
    public static Optional<FilterEntry> entryFor(ResourceType type, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return switch (type) {
            case ITEM -> Optional.of(new FilterEntry.ItemEntry(stack, 0));
            case FLUID -> FluidUtil.getFluidContained(stack)
                    .filter(fluid -> !fluid.isEmpty())
                    .map(fluid -> new FilterEntry.FluidEntry(fluid, 0));
            default -> Optional.empty();
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return target == null || target.stillValid(player);
    }

    /** Envia ao jogador da tela a visão nova, se o filtro ou o cartão na mão mudaram. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        FilterView changed = pollView();
        if (changed != null && viewer != null) {
            FilterViewPayload payload = new FilterViewPayload(containerId, changed);
            // Jogadores falsos (GameTests, mods de automação) não negociam os canais do mod.
            if (viewer.connection != null && viewer.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(viewer, payload);
            }
        }
    }

    /**
     * Visão nova se a versão do alvo ou o cartão na mão principal mudaram e ela difere da última
     * enviada, ou {@code null}. Marca a nova como enviada. Só no servidor; público para os GameTests.
     */
    public @Nullable FilterView pollView() {
        if (target == null || viewer == null) {
            return null;
        }
        int targetVersion = target.version();
        boolean hasCard = FilterCardItem.isCard(viewer.getMainHandItem());
        if (targetVersion == sentTargetVersion && hasCard == view.hasCard()) {
            return null;
        }
        sentTargetVersion = targetVersion;
        FilterView next = target.view(viewer);
        if (next.equals(view)) {
            return null;
        }
        view = next;
        return next;
    }
}
