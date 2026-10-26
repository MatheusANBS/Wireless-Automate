package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.packet.RepairerStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerCosts;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela do Reparador de Cristais: os 17 slots do bloco na ordem do {@link RepairerSlots} (8 rachados, o estoque
 * de cristais cheios e 8 saídas) e o inventário do jogador. O resto vai pelo {@link RepairerStatePayload}, montado no
 * servidor e mandado só com a tela aberta, quando muda, no máximo a cada {@link StorageListMenu#SYNC_INTERVAL} ticks
 * (o primeiro na hora).
 *
 * <p>A entrada de uma linha ocupada (andando ou travada) não sai nem troca: o {@code RepairerContainer} ignora a escrita
 * e recusa a retirada em silêncio, então o slot também recusa ({@code mayPickup} e {@code mayPlace} falsos), para o
 * cursor não perder nem duplicar o cristal. No cliente a regra vem do último estado recebido.
 */
public class RepairerMenu extends AbstractContainerMenu {
    /** Os slots do jogador vêm logo depois dos 17 do Reparador. */
    public static final int INVENTORY_START = RepairerSlots.TOTAL;
    private static final int HOTBAR_START = INVENTORY_START + 27;
    private static final int INVENTORY_END = HOTBAR_START + 9;
    /** O canto dos slots do mockup, 1 px para dentro do fundo de cada um. */
    public static final int INPUT_X = 209;
    public static final int INPUT_Y = 39;
    public static final int COLUMN_STEP = 34;
    public static final int ROW_STEP = 20;
    public static final int FEED_X = 209;
    public static final int FEED_Y = 92;
    public static final int OUTPUT_X = 209;
    public static final int OUTPUT_Y = 124;
    public static final int INVENTORY_X = 96;
    public static final int INVENTORY_Y = 191;
    public static final int HOTBAR_Y = 251;

    private final BlockPos pos;
    private final Container container;
    private final @Nullable RepairerBlockEntity repairer;
    private final @Nullable ServerPlayer viewer;
    private @Nullable RepairerStatePayload sent;
    private long lastSent;
    // Cliente: o último estado recebido.
    private @Nullable RepairerStatePayload state;

    /** Servidor. */
    public RepairerMenu(int containerId, Inventory inventory, RepairerBlockEntity repairer) {
        this(containerId, inventory, repairer.getBlockPos(), repairer.container(), repairer);
    }

    /** Cliente. */
    public RepairerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, BlockPos.STREAM_CODEC.decode(buf));
    }

    /** Cliente (e a captura de tela de desenvolvimento). */
    public RepairerMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, pos, new SimpleContainer(RepairerSlots.TOTAL), null);
    }

    private RepairerMenu(int containerId, Inventory inventory, BlockPos pos, Container container,
            @Nullable RepairerBlockEntity repairer) {
        super(ModMenus.CRYSTAL_REPAIRER.get(), containerId);
        this.pos = pos;
        this.container = container;
        this.repairer = repairer;
        this.viewer = repairer != null && inventory.player instanceof ServerPlayer player ? player : null;
        for (int i = 0; i < RepairerSlots.INPUTS; i++) {
            addSlot(new InputSlot(container, i, INPUT_X + COLUMN_STEP * (i % 4), INPUT_Y + ROW_STEP * (i / 4)));
        }
        addSlot(new FeedSlot(container, RepairerSlots.FEED, FEED_X, FEED_Y));
        for (int i = 0; i < RepairerSlots.OUTPUTS; i++) {
            addSlot(new OutputSlot(container, RepairerSlots.OUTPUT_FIRST + i, OUTPUT_X + COLUMN_STEP * (i % 4),
                    OUTPUT_Y + ROW_STEP * (i / 4)));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + 18 * col, INVENTORY_Y + 18 * row));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + 18 * col, HOTBAR_Y));
        }
    }

    public static void open(ServerPlayer player, RepairerBlockEntity repairer) {
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new RepairerMenu(containerId, inventory, repairer),
                        Component.translatable("container.wirelessautomate.repairer")),
                buf -> BlockPos.STREAM_CODEC.encode(buf, repairer.getBlockPos()));
    }

    public BlockPos pos() {
        return pos;
    }

    /** Servidor: o Reparador da tela; nulo no cliente. */
    public @Nullable RepairerBlockEntity repairer() {
        return repairer;
    }

    /** Cliente: o último estado recebido, ou {@code null} antes do primeiro. */
    public @Nullable RepairerStatePayload state() {
        return state;
    }

    /** Cliente: aplica um estado; também usado pela captura de desenvolvimento. */
    public void apply(RepairerStatePayload payload) {
        state = payload;
    }

    /**
     * A linha {@code line} está ocupada (andando ou travada): no servidor pelo bloco, no cliente pelo último estado
     * (sem estado, livre: o servidor corrige o clique).
     */
    public boolean lineBusy(int line) {
        if (repairer != null) {
            return repairer.lineEnd(line) > 0;
        }
        return state != null && state.ends()[line] > 0;
    }

    /** Cliente: quanto a linha andou, de 0 a 1000, pelo relógio; 0 parada ou sem estado, 1000 travada. */
    public int progress(int line, long gameTime) {
        if (state == null || state.ends()[line] <= 0) {
            return 0;
        }
        if (state.blocked()[line]) {
            return 1000;
        }
        return RepairMath.progressPermille(gameTime, state.ends()[line], state.duration());
    }

    /**
     * Servidor: o estado a mandar agora, ou {@code null}. O primeiro vai na hora; depois, só se mudou e se já passaram
     * {@link StorageListMenu#SYNC_INTERVAL} ticks desde o último mandado (a mudança espera o próximo). Compara campo a
     * campo antes de montar, para não alocar sem mudança. Marca como mandado. Público para os GameTests.
     */
    public @Nullable RepairerStatePayload pollState(long now) {
        if (repairer == null || sent != null && now - lastSent < StorageListMenu.SYNC_INTERVAL) {
            return null;
        }
        long cost0 = firstWaitingCost(repairer);
        if (sent != null && same(sent, repairer, cost0)) {
            return null;
        }
        long[] ends = new long[RepairMath.LINES];
        boolean[] blocked = new boolean[RepairMath.LINES];
        for (int i = 0; i < RepairMath.LINES; i++) {
            ends[i] = repairer.lineEnd(i);
            blocked[i] = repairer.lineBlocked(i);
        }
        sent = new RepairerStatePayload(containerId, repairer.energy().stored(), repairer.energy().capacity(), ends,
                blocked, duration(), Config.recastCrystals(), cost0);
        lastSent = now;
        return sent;
    }

    private static int duration() {
        return (int) Math.min(Integer.MAX_VALUE, Config.repairTicks());
    }

    private static boolean same(RepairerStatePayload p, RepairerBlockEntity repairer, long cost0) {
        if (p.energy() != repairer.energy().stored() || p.capacity() != repairer.energy().capacity()
                || p.cost0() != cost0 || p.duration() != duration() || p.needed() != Config.recastCrystals()) {
            return false;
        }
        for (int i = 0; i < RepairMath.LINES; i++) {
            if (p.ends()[i] != repairer.lineEnd(i) || p.blocked()[i] != repairer.lineBlocked(i)) {
                return false;
            }
        }
        return true;
    }

    /** O custo do primeiro rachado parado (linha sem fim marcado), ou 0 sem nenhum. */
    private static long firstWaitingCost(RepairerBlockEntity repairer) {
        for (int i = 0; i < RepairMath.LINES; i++) {
            ItemStack input = repairer.items().get(i);
            if (repairer.lineEnd(i) == 0 && RepairerSlots.acceptsCracked(input)) {
                return RepairerCosts.of(input);
            }
        }
        return 0;
    }

    /**
     * Shift + clique: de um slot do Reparador para o inventário (a barra primeiro), menos a entrada de uma linha
     * ocupada; do inventário, o rachado vai para as entradas livres, um por slot, e o cristal de Sinal cheio para o
     * estoque; o resto troca entre a mochila e a barra. Nada entra numa saída.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INVENTORY_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
            slot.onTake(player, original.copyWithCount(original.getCount() - stack.getCount()));
            return original;
        }
        if (RepairerSlots.acceptsCracked(stack) || RepairerSlots.acceptsFeed(stack)) {
            boolean moved = RepairerSlots.acceptsCracked(stack) ? fillInputs(stack)
                    : moveItemStackTo(stack, RepairerSlots.FEED, RepairerSlots.FEED + 1, false);
            if (!moved) {
                return ItemStack.EMPTY;
            }
            // O que sobrou com o Reparador cheio fica onde está: o vanilla repete o Shift + clique enquanto devolvemos
            // algo, e a repetição levaria a sobra para a barra.
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return ItemStack.EMPTY;
        }
        if (index < HOTBAR_START ? !moveItemStackTo(stack, HOTBAR_START, INVENTORY_END, false)
                : !moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /** Um rachado em cada entrada livre: o {@code moveItemStackTo} para no primeiro slot vazio que preenche. */
    private boolean fillInputs(ItemStack stack) {
        boolean moved = false;
        while (!stack.isEmpty() && moveItemStackTo(stack, 0, RepairerSlots.INPUTS, false)) {
            moved = true;
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return repairer == null || container.stillValid(player);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (viewer == null) {
            return;
        }
        RepairerStatePayload payload = pollState(viewer.server.getTickCount());
        if (payload != null && viewer.connection != null && viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    /** Cliente: um pacote do servidor. */
    public static void onState(RepairerStatePayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof RepairerMenu menu && menu.containerId == payload.containerId()) {
            menu.apply(payload);
        }
    }

    /** A entrada de uma linha: um rachado por vez, e nada sai nem troca com a linha ocupada. */
    private final class InputSlot extends Slot {
        InputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return RepairerSlots.acceptsCracked(stack) && !lineBusy(getContainerSlot());
        }

        @Override
        public boolean mayPickup(Player player) {
            return !lineBusy(getContainerSlot());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** O estoque de cristais de Sinal cheios, até uma pilha. */
    private static final class FeedSlot extends Slot {
        FeedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return RepairerSlots.acceptsFeed(stack);
        }
    }

    /** Uma saída: o jogador só tira. */
    private static final class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
