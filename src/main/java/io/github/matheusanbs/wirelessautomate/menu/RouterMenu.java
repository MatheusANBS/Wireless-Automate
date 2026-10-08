package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.RouterNetworksPayload;
import io.github.matheusanbs.wirelessautomate.packet.RouterSnapshotPayload;
import io.github.matheusanbs.wirelessautomate.packet.RouterThroughputPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela do roteador. No cliente guarda o último {@link RouterSnapshot} e a vazão atual; no
 * servidor conhece o roteador e envia atualizações enquanto a tela estiver aberta.
 *
 * <p>Slots: os {@link RouterBlockEntity#CARD_SLOTS} de Cartão de Filtro da face e do tipo
 * selecionados na tela, seguidos do inventário do jogador. A seleção é da tela: ela avisa o
 * servidor ({@code SelectFacePayload} → {@link #select}) e os slots de cartão passam a mostrar os
 * daquela face, por um {@link Container} que lê e grava direto no roteador. A sincronização vanilla
 * de slots leva o resultado ao cliente. Energia e químicos não têm cartões: os slots ficam inativos.
 * Depois do inventário vem o slot do Upgrade de chunk loading ({@link #UPGRADE_SLOT}), que lê e grava
 * direto no roteador; o dono do upgrade é o jogador da tela.
 *
 * <p>Sincronização (servidor): o vanilla só chama {@link #broadcastChanges()} no menu aberto de
 * cada jogador, então nada roda para roteadores sem tela aberta. A cada chamada o menu compara a
 * {@link RouterBlockEntity#changeVersion()} do roteador e a {@link NetworkSavedData#version()} das
 * redes com as que já enviou; se alguma mudou, remonta o snapshot e o compara com o último enviado.
 * O corpo (uns 100 bytes) e as redes do seletor (que trazem as públicas de todos os donos e mudam
 * com qualquer rede do servidor) vão em pacotes separados, cada um só se ficou diferente. A vazão é amostrada a cada {@link #SAMPLE_TICKS} ticks
 * pelos totais que o motor soma no roteador e só é enviada quando muda.
 */
public class RouterMenu extends AbstractContainerMenu {
    /** Faixa da prioridade aceita da tela; valores fora dela são limitados. */
    public static final int MIN_PRIORITY = -999;
    public static final int MAX_PRIORITY = 999;
    /** Distância máxima (em blocos, do olho ao centro do roteador) para a tela continuar aberta. */
    public static final double MAX_DISTANCE = 8.0;
    /** Janela da amostra de vazão, em ticks. */
    public static final int SAMPLE_TICKS = 20;
    /** Posições na tela (canto do item): o primeiro slot de cartão e o primeiro da mochila. */
    public static final int CARD_X = 130;
    public static final int CARD_Y = 132;
    public static final int INVENTORY_X = 130;
    public static final int INVENTORY_Y = 157;
    public static final int CARD_SLOT_COUNT = RouterBlockEntity.CARD_SLOTS;
    /** Primeiro slot do inventário do jogador (público para o e2e conferir o layout). */
    public static final int INVENTORY_START = CARD_SLOT_COUNT;
    private static final int HOTBAR_START = INVENTORY_START + 27;
    private static final int SLOTS_END = HOTBAR_START + 9;
    /** Índice do slot do Upgrade de chunk loading, depois do inventário (os índices de antes não mudam). */
    public static final int UPGRADE_SLOT = SLOTS_END;
    /** Canto do item do slot de upgrade: no canto direito do cabeçalho, à direita do tier. */
    public static final int UPGRADE_X = 274;
    public static final int UPGRADE_Y = 6;

    private final @Nullable RouterBlockEntity router;
    private RouterSnapshot snapshot;
    private long[] throughput = new long[ResourceType.values().length];
    private int version;
    /** Face (absoluta) e tipo cujos cartões os slots mostram. */
    private ResourceType selectedType = ResourceType.ITEM;
    private Direction selectedFace;

    // Só no servidor.
    private final @Nullable ServerPlayer viewer;
    private int sentChangeVersion;
    private int sentDataVersion;
    private long sampleTick;
    private final long[] sampleTotals = new long[ResourceType.values().length];
    private final long[] sentThroughput = new long[ResourceType.values().length];
    /** Partes do último snapshot novo de {@link #pollSnapshot()} que mudaram, para o envio. */
    private boolean bodyChanged;
    private boolean networksChanged;

    /** Servidor. */
    public RouterMenu(int containerId, Inventory inventory, RouterBlockEntity router, RouterSnapshot snapshot) {
        super(ModMenus.ROUTER.get(), containerId);
        this.router = router;
        this.snapshot = snapshot;
        this.viewer = inventory.player instanceof ServerPlayer player ? player : null;
        this.sentChangeVersion = router.changeVersion();
        if (viewer != null) {
            this.sentDataVersion = NetworkSavedData.get(viewer.server).version();
            this.sampleTick = viewer.server.getTickCount();
        }
        for (ResourceType type : ResourceType.values()) {
            sampleTotals[type.ordinal()] = router.moved(type);
        }
        this.selectedFace = router.facing();
        addSlots(new RouterCards(), new RouterUpgrade(inventory.player.getUUID()), inventory);
    }

    /** Cliente: o snapshot inicial vem no buffer de abertura. */
    public RouterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, RouterSnapshot.STREAM_CODEC.decode(buf));
    }

    /** Cliente (e a captura de tela de desenvolvimento): sem roteador, só o snapshot. */
    public RouterMenu(int containerId, Inventory inventory, RouterSnapshot snapshot) {
        super(ModMenus.ROUTER.get(), containerId);
        this.router = null;
        this.snapshot = snapshot;
        this.viewer = null;
        this.selectedFace = snapshot.facing();
        addSlots(new SimpleContainer(CARD_SLOT_COUNT) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        }, new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        }, inventory);
    }

    private void addSlots(Container cards, Container upgrade, Inventory inventory) {
        for (int i = 0; i < CARD_SLOT_COUNT; i++) {
            addSlot(new CardSlot(cards, i, CARD_X + i * 18, CARD_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        addSlot(new UpgradeSlot(upgrade, UPGRADE_X, UPGRADE_Y));
    }

    /**
     * Põe os slots no layout da tela (só no cliente, depois de redimensionar): cartões e inventário a
     * partir de {@code rightX}, inventário com o topo em {@code inventoryY}, upgrade em {@code upgradeX}.
     * A posição do {@link Slot} é final: cada slot é trocado por um igual, no mesmo índice.
     */
    public void placeSlots(int rightX, int upgradeX, int inventoryY) {
        for (int i = 0; i < slots.size(); i++) {
            Slot old = slots.get(i);
            Slot moved;
            if (old instanceof CardSlot) {
                moved = new CardSlot(old.container, old.getContainerSlot(), rightX + old.getContainerSlot() * 18, CARD_Y);
            } else if (old instanceof UpgradeSlot) {
                moved = new UpgradeSlot(old.container, upgradeX, UPGRADE_Y);
            } else {
                int c = old.getContainerSlot();
                int sx = rightX + (c % 9) * 18;
                int sy = c < 9 ? inventoryY + 58 : inventoryY + (c / 9 - 1) * 18;
                if (old.x == sx && old.y == sy) {
                    continue;
                }
                moved = new Slot(old.container, c, sx, sy);
            }
            moved.index = old.index;
            slots.set(i, moved);
        }
    }

    /**
     * Abre a tela do roteador para o jogador. O mesmo snapshot vai no buffer de abertura e no menu
     * do servidor. O título é o nome do nó ou, sem nome, o nome do bloco.
     */
    public static void open(ServerPlayer player, RouterBlockEntity router) {
        RouterSnapshot snapshot = RouterSnapshot.capture(router, player);
        Component title = router.name().isEmpty()
                ? router.getBlockState().getBlock().getName()
                : Component.literal(router.name());
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new RouterMenu(containerId, inventory, router, snapshot), title),
                buf -> RouterSnapshot.STREAM_CODEC.encode(buf, snapshot));
    }

    /** O roteador; só existe no servidor. */
    public @Nullable RouterBlockEntity router() {
        return router;
    }

    public RouterSnapshot snapshot() {
        return snapshot;
    }

    /** Vazão atual por {@link ResourceType#ordinal()}, por segundo (energia em FE/t). */
    public long[] throughput() {
        return throughput;
    }

    /** Muda a cada snapshot ou vazão recebidos: a tela compara para saber quando refazer os widgets. */
    public int version() {
        return version;
    }

    public void applySnapshot(RouterSnapshot snapshot) {
        this.snapshot = snapshot;
        version++;
    }

    /** Cliente: corpo novo do servidor ({@code RouterSnapshotPayload}); as redes do seletor ficam. */
    public void applySnapshotBody(RouterSnapshot body) {
        applySnapshot(body.withNetworks(snapshot.networks()));
    }

    /** Cliente: redes novas do seletor ({@code RouterNetworksPayload}). */
    public void applyNetworks(List<RouterSnapshot.NetworkEntry> networks) {
        applySnapshot(snapshot.withNetworks(List.copyOf(networks)));
    }

    public void applyThroughput(long[] throughput) {
        this.throughput = throughput;
        version++;
    }

    public ResourceType selectedType() {
        return selectedType;
    }

    public Direction selectedFace() {
        return selectedFace;
    }

    /**
     * Troca a face (absoluta) e o tipo cujos cartões os slots mostram. No servidor vem do pacote
     * da tela; no cliente a tela chama junto com o envio, para a previsão dos cliques bater.
     */
    public void select(ResourceType type, Direction face) {
        this.selectedType = type;
        this.selectedFace = face;
    }

    /** Os slots de cartão valem para o tipo selecionado (itens e fluidos). */
    public boolean cardSlotsActive() {
        return RouterBlockEntity.hasCardSlots(selectedType);
    }

    /**
     * Shift + clique: um cartão do tipo da aba vai para o primeiro slot de cartão livre (um por
     * clique) e o Upgrade de chunk loading vai para o slot de upgrade; os slots de cartão e de upgrade
     * devolvem ao inventário; o resto troca entre mochila e barra.
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
        boolean toCards = false;
        if (index < INVENTORY_START || index == UPGRADE_SLOT) {
            if (!moveItemStackTo(stack, INVENTORY_START, SLOTS_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (RouterBlockEntity.acceptsUpgrade(stack) && !slots.get(UPGRADE_SLOT).hasItem()
                && moveItemStackTo(stack, UPGRADE_SLOT, UPGRADE_SLOT + 1, false)) {
            toCards = true;
        } else if (cardSlotsActive() && RouterBlockEntity.acceptsCard(selectedType, stack)
                && moveItemStackTo(stack, 0, CARD_SLOT_COUNT, false)) {
            toCards = true;
        } else if (index < HOTBAR_START) {
            if (!moveItemStackTo(stack, HOTBAR_START, SLOTS_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false)) {
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
        // Um cartão por clique: o vanilla repete o Shift + clique enquanto devolvemos algo.
        return toCards ? ItemStack.EMPTY : original;
    }

    /** Clique duplo num cartão do inventário junta os iguais, mas não tira os que estão nos slots de cartão. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof CardSlot) && !(slot instanceof UpgradeSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    /** Slot do Upgrade de chunk loading: só o upgrade, um só. */
    private static final class UpgradeSlot extends Slot {
        UpgradeSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return RouterBlockEntity.acceptsUpgrade(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    /**
     * O slot de upgrade, lido e gravado direto no roteador (servidor). Quem põe o upgrade vira o
     * dono dele para o limite de chunks forçados.
     */
    private final class RouterUpgrade implements Container {
        private final UUID owner;

        RouterUpgrade(UUID owner) {
            this.owner = owner;
        }

        private boolean usable() {
            return router != null && !router.isRemoved();
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getItem(0).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return usable() && slot == 0 ? router.upgrade() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = stack.split(amount);
            router.setUpgrade(stack.isEmpty() ? ItemStack.EMPTY : stack, router.upgradeOwner());
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty()) {
                router.setUpgrade(ItemStack.EMPTY, null);
            }
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (usable() && slot == 0) {
                router.setUpgrade(stack, owner);
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return RouterBlockEntity.acceptsUpgrade(stack);
        }

        @Override
        public void setChanged() {
            if (usable()) {
                router.setChanged();
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return RouterMenu.this.stillValid(player);
        }

        @Override
        public void clearContent() {
            removeItemNoUpdate(0);
        }
    }

    /** Slot de cartão: só Cartão de Filtro do tipo selecionado, um por slot, e só em itens e fluidos. */
    private final class CardSlot extends Slot {
        CardSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return RouterBlockEntity.acceptsCard(selectedType, stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }

        @Override
        public boolean isActive() {
            return cardSlotsActive();
        }
    }

    /**
     * Os cartões da face e do tipo selecionados, lidos e gravados direto no roteador (servidor).
     * Cada mudança passa pelo roteador, que salva e avisa o motor para remontar as rotas.
     */
    private final class RouterCards implements Container {
        private RelativeSide side() {
            return RelativeSide.fromAbsolute(router.facing(), selectedFace);
        }

        private boolean usable() {
            return router != null && !router.isRemoved() && cardSlotsActive();
        }

        @Override
        public int getContainerSize() {
            return CARD_SLOT_COUNT;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < CARD_SLOT_COUNT; i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return usable() ? router.card(selectedType, side(), slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = stack.split(amount);
            if (stack.isEmpty()) {
                router.setCard(selectedType, side(), slot, ItemStack.EMPTY);
            } else {
                router.cardsChanged();
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty()) {
                router.setCard(selectedType, side(), slot, ItemStack.EMPTY);
            }
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (usable()) {
                router.setCard(selectedType, side(), slot, stack);
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return RouterBlockEntity.acceptsCard(selectedType, stack);
        }

        @Override
        public void setChanged() {
            if (usable()) {
                router.cardsChanged();
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return RouterMenu.this.stillValid(player);
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < CARD_SLOT_COUNT; i++) {
                removeItemNoUpdate(i);
            }
        }
    }

    /** No servidor: o roteador continua no mundo, na mesma dimensão e a até {@link #MAX_DISTANCE} blocos. */
    @Override
    public boolean stillValid(Player player) {
        if (router == null) {
            return true;
        }
        if (router.isRemoved() || router.getLevel() != player.level()
                || player.level().getBlockEntity(router.getBlockPos()) != router) {
            return false;
        }
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(router.getBlockPos()))
                <= MAX_DISTANCE * MAX_DISTANCE;
    }

    /** Envia ao jogador da tela o snapshot, se o roteador ou as redes mudaram, e a vazão, se mudou. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (router == null || viewer == null) {
            return;
        }
        RouterSnapshot changed = pollSnapshot();
        if (changed != null) {
            // As redes antes do corpo: o corpo pode apontar para uma rede nova do seletor.
            if (networksChanged) {
                send(new RouterNetworksPayload(containerId, changed.networks()));
            }
            if (bodyChanged) {
                send(new RouterSnapshotPayload(containerId, changed));
            }
        }
        long[] rates = pollThroughput(viewer.server.getTickCount());
        if (rates != null) {
            send(new RouterThroughputPayload(containerId, rates));
        }
    }

    private void send(CustomPacketPayload payload) {
        // Jogadores falsos (GameTests, mods de automação) não negociam os canais do mod.
        if (viewer != null && viewer.connection != null && viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    /**
     * Snapshot novo se a versão do roteador ou das redes mudou e ele ficou diferente do último
     * enviado, ou {@code null}. Marca o novo como enviado. Só no servidor; público para os GameTests.
     */
    public @Nullable RouterSnapshot pollSnapshot() {
        if (router == null || viewer == null || router.isRemoved()) {
            return null;
        }
        int changeVersion = router.changeVersion();
        int dataVersion = NetworkSavedData.get(viewer.server).version();
        if (changeVersion == sentChangeVersion && dataVersion == sentDataVersion) {
            return null;
        }
        sentChangeVersion = changeVersion;
        sentDataVersion = dataVersion;
        RouterSnapshot captured = RouterSnapshot.capture(router, viewer);
        bodyChanged = !captured.sameBody(snapshot);
        networksChanged = !captured.networks().equals(snapshot.networks());
        if (!bodyChanged && !networksChanged) {
            return null;
        }
        snapshot = captured;
        return captured;
    }

    /**
     * Vazão da última janela se já passaram {@link #SAMPLE_TICKS} ticks desde a amostra anterior e
     * ela mudou em relação à última enviada, ou {@code null}. Itens e fluidos por segundo, energia
     * por tick. Só no servidor; público para os GameTests.
     */
    public long @Nullable [] pollThroughput(long now) {
        if (router == null) {
            return null;
        }
        long elapsed = now - sampleTick;
        if (elapsed < SAMPLE_TICKS) {
            return null;
        }
        boolean changed = false;
        for (ResourceType type : ResourceType.values()) {
            int i = type.ordinal();
            long total = router.moved(type);
            long delta = total - sampleTotals[i];
            sampleTotals[i] = total;
            long rate = type.ratePerTick()
                    ? (delta + elapsed / 2) / elapsed
                    : (delta * 20 + elapsed / 2) / elapsed;
            if (rate != sentThroughput[i]) {
                sentThroughput[i] = rate;
                changed = true;
            }
        }
        sampleTick = now;
        return changed ? Arrays.copyOf(sentThroughput, sentThroughput.length) : null;
    }
}
