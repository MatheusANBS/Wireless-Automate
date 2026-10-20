package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.resonator.CoreInventory;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import io.github.matheusanbs.wirelessautomate.resonator.Tuning;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
 * Menu da tela do Núcleo de Ressonância: um slot por câmara do tier (um cristal cada), o da redstone e o inventário
 * do jogador. Os slots viajam pelo próprio menu; o resto vai pelo {@link ResonatorStatePayload}, montado no servidor
 * e mandado só com a tela aberta, quando muda, no máximo a cada {@link StorageListMenu#SYNC_INTERVAL} ticks (o
 * primeiro na hora). A estação nunca sai do servidor.
 *
 * <p>As câmaras ficam fixas na abertura: se o tier muda com a tela aberta (Cartão de Upgrade de outro jogador), o
 * {@link #stillValid} deixa de valer, porque o slot da redstone do {@code CoreInventory} muda de índice. Com o jogador
 * ainda ao alcance e o Núcleo montado, o {@link #broadcastChanges} reabre a tela com as câmaras novas; senão o vanilla
 * a fecha.
 */
public class ResonatorMenu extends AbstractContainerMenu {
    public static final int CHAMBER_X = 191;
    public static final int CHAMBER_Y = 39;
    public static final int REDSTONE_X = 191;
    public static final int REDSTONE_Y = 61;
    public static final int INVENTORY_X = 11;
    public static final int INVENTORY_Y = 159;
    public static final int HOTBAR_Y = 217;

    private final BlockPos pos;
    private final RouterTier tier;
    private final int chambers;
    private final Container container;
    private final @Nullable ResonanceCoreBlockEntity core;
    private final @Nullable ServerPlayer viewer;
    private @Nullable ResonatorStatePayload sent;
    private long lastSent;
    // Cliente: o último estado recebido.
    private @Nullable ResonatorStatePayload state;

    /** Servidor. */
    public ResonatorMenu(int containerId, Inventory inventory, ResonanceCoreBlockEntity core) {
        this(containerId, inventory, core.getBlockPos(), core.tier(), core.chambers(), core.inventory(), core);
    }

    /** Cliente. */
    public ResonatorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, BlockPos.STREAM_CODEC.decode(buf), tierOf(buf.readVarInt()), buf.readVarInt());
    }

    /** Cliente (e a captura de tela de desenvolvimento). */
    public ResonatorMenu(int containerId, Inventory inventory, BlockPos pos, RouterTier tier, int chambers) {
        this(containerId, inventory, pos, tier, chambers, new SimpleContainer(chambers + 1), null);
    }

    private ResonatorMenu(int containerId, Inventory inventory, BlockPos pos, RouterTier tier, int chambers,
            Container container, @Nullable ResonanceCoreBlockEntity core) {
        super(ModMenus.RESONATOR.get(), containerId);
        this.pos = pos;
        this.tier = tier;
        this.chambers = Math.max(1, Math.min(ResonanceCoreBlockEntity.MAX_CHAMBERS, chambers));
        this.container = container;
        this.core = core;
        this.viewer = core != null && inventory.player instanceof ServerPlayer player ? player : null;
        for (int i = 0; i < this.chambers; i++) {
            addSlot(new ChamberSlot(container, i, CHAMBER_X + 18 * i, CHAMBER_Y));
        }
        addSlot(new RedstoneSlot(container, this.chambers, REDSTONE_X, REDSTONE_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + 18 * col, INVENTORY_Y + 18 * row));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + 18 * col, HOTBAR_Y));
        }
    }

    private static RouterTier tierOf(int ordinal) {
        RouterTier[] all = RouterTier.values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    public static void open(ServerPlayer player, ResonanceCoreBlockEntity core) {
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new ResonatorMenu(containerId, inventory, core),
                        core.getBlockState().getBlock().getName()),
                buf -> {
                    BlockPos.STREAM_CODEC.encode(buf, core.getBlockPos());
                    buf.writeVarInt(core.tier().ordinal());
                    buf.writeVarInt(core.chambers());
                });
    }

    public BlockPos pos() {
        return pos;
    }

    public RouterTier tier() {
        return tier;
    }

    public int chambers() {
        return chambers;
    }

    public int redstoneSlot() {
        return chambers;
    }

    public int inventoryStart() {
        return chambers + 1;
    }

    /** Servidor: o Núcleo da tela; nulo no cliente. */
    public @Nullable ResonanceCoreBlockEntity core() {
        return core;
    }

    /** Cliente: o último estado recebido, ou {@code null} antes do primeiro. */
    public @Nullable ResonatorStatePayload state() {
        return state;
    }

    public boolean received() {
        return state != null;
    }

    /** Cliente: aplica um estado; também usado pela captura de desenvolvimento. */
    public void apply(ResonatorStatePayload payload) {
        state = payload;
    }

    /**
     * Servidor: o estado a mandar agora, ou {@code null}. O primeiro vai na hora; depois, só se mudou e se já passaram
     * {@link StorageListMenu#SYNC_INTERVAL} ticks desde o último mandado (a mudança espera o próximo). Marca como
     * mandado. Público para os GameTests.
     */
    public @Nullable ResonatorStatePayload pollState(long now) {
        if (core == null || sent != null && now - lastSent < StorageListMenu.SYNC_INTERVAL) {
            return null;
        }
        ResonatorStatePayload current = capture(core);
        if (current.equals(sent)) {
            return null;
        }
        sent = current;
        lastSent = now;
        return current;
    }

    private ResonatorStatePayload capture(ResonanceCoreBlockEntity core) {
        long fePerCrystal = Config.resonatorFePerCrystal(core.tier());
        List<ItemStack> crystals = core.crystals();
        int yieldSum = 0;
        boolean dull = false;
        for (int i = 0; i < core.chambers(); i++) {
            ItemStack stack = crystals.get(i);
            if (stack.getItem() instanceof SignalCrystalItem item) {
                yieldSum += item.yield();
                dull |= SignalCrystalItem.isDull(stack);
            }
        }
        int recharge = dull && core.redstone() > 0 ? core.rechargeProgress() : 0;
        int layout = core.layout() == null ? 0 : core.layout().ordinal() + 1;
        return new ResonatorStatePayload(containerId, core.energy(), core.capacity(), core.generationPerTick(),
                CoreMath.perTick(fePerCrystal, yieldSum * 4, 1.0), core.dial(),
                (int) Math.round(core.efficiency() * 1000), (int) Math.round(Tuning.ceiling(core.tier().ordinal()) * 1000),
                core.auto(), core.hasTuner(), TuneSignal.steps(core.station(), core.dial()),
                TuneSignal.tuned(core.station(), core.dial()), recharge,
                (int) Math.min(Integer.MAX_VALUE, Config.rechargeTicks()), core.energyPorts(), core.crystalPorts(),
                layout);
    }

    /**
     * Shift + clique: de um slot do Núcleo para o inventário (barra primeiro); do inventário, cristal para as
     * câmaras livres e redstone para o slot dela; o resto troca entre mochila e barra. Os slots do Núcleo dão uma
     * cópia (o {@code CoreInventory} não deixa sair pilha dele): a sobra volta pelo {@code setByPlayer}, e a redstone
     * entra pelo {@code safeInsert}, nunca pela soma no lugar do {@code moveItemStackTo}.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int start = inventoryStart();
        int hotbar = start + 27;
        int end = hotbar + 9;
        if (index < start) {
            if (!moveItemStackTo(stack, start, end, true)) {
                return ItemStack.EMPTY;
            }
            slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
            slot.onTake(player, original.copyWithCount(original.getCount() - stack.getCount()));
            return original;
        }
        if (stack.getItem() instanceof SignalCrystalItem && fillChambers(stack)
                || CoreInventory.isPlainRedstone(stack) && slots.get(chambers).safeInsert(stack).getCount()
                        < original.getCount()) {
            // O que sobrou com o Núcleo cheio fica onde está: o vanilla repete o Shift + clique enquanto devolvemos
            // algo, e a repetição levaria a sobra para a barra.
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return ItemStack.EMPTY;
        }
        if (index < hotbar ? !moveItemStackTo(stack, hotbar, end, false) : !moveItemStackTo(stack, start, hotbar, false)) {
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

    /** Um cristal em cada câmara livre: o {@code moveItemStackTo} para no primeiro slot vazio que preenche. */
    private boolean fillChambers(ItemStack stack) {
        boolean moved = false;
        while (!stack.isEmpty() && moveItemStackTo(stack, 0, chambers, false)) {
            moved = true;
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return core == null || core.chambers() == chambers && container.stillValid(player);
    }

    @Override
    public void broadcastChanges() {
        if (viewer != null && core != null && core.chambers() != chambers) {
            reopen(viewer, core);
            return;
        }
        super.broadcastChanges();
        if (viewer == null) {
            return;
        }
        ResonatorStatePayload payload = pollState(viewer.server.getTickCount());
        if (payload != null && viewer.connection != null && viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    /**
     * O tier mudou com a tela aberta: abre outra com as câmaras novas, se o jogador ainda alcança o Núcleo montado e
     * esta ainda é a tela dele. Abrir fecha esta antes (o item no cursor volta ao inventário, pelo {@code removed} do
     * vanilla), e o tick do jogador confere em seguida o {@link #stillValid} da nova. Fora disso, nada: o tick fecha
     * esta pelo {@link #stillValid}.
     */
    private void reopen(ServerPlayer player, ResonanceCoreBlockEntity core) {
        if (player.containerMenu == this && !core.isRemoved() && core.formed() && container.stillValid(player)) {
            open(player, core);
        }
    }

    /** Cliente: um pacote do servidor. */
    public static void onState(ResonatorStatePayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof ResonatorMenu menu && menu.containerId == payload.containerId()) {
            menu.apply(payload);
        }
    }

    /** Uma câmara: só Cristal de Sinal ou Lapidado, um por vez. */
    private static final class ChamberSlot extends Slot {
        ChamberSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof SignalCrystalItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** A redstone da recarga: só pó de redstone comum (sem componentes), até {@link ResonanceCoreBlockEntity#MAX_REDSTONE}. */
    private static final class RedstoneSlot extends Slot {
        RedstoneSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return CoreInventory.isPlainRedstone(stack);
        }

        @Override
        public int getMaxStackSize() {
            return ResonanceCoreBlockEntity.MAX_REDSTONE;
        }
    }
}
