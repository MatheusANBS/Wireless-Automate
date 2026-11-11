package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockAe2;
import io.github.matheusanbs.wirelessautomate.dock.DockMachine;
import io.github.matheusanbs.wirelessautomate.dock.DockRs2;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.packet.DockStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela da Doca de Padrões: sem slots, só o estado dos seis lados pelo {@link DockStatePayload}, montado no
 * servidor e mandado só com a tela aberta: o primeiro na hora, depois só com mudança, conferida no máximo uma vez por
 * segundo ({@link #SYNC_INTERVAL}), e só para quem abriu.
 */
public class DockMenu extends AbstractContainerMenu {
    /** Ticks entre duas conferências do estado (o "tem provedor" consulta o vizinho de cada lado). */
    public static final int SYNC_INTERVAL = 20;
    private static final int SIDES = PatternDockBlockEntity.SIDES;
    /** Os números de um lado, na ordem: vinculadas, trabalhando, descarregadas, fora do alcance e o estado. */
    private static final int FACTS = 5;
    private static final Direction[] DIRECTIONS = Direction.values();

    private final BlockPos pos;
    private final @Nullable PatternDockBlockEntity dock;
    private final @Nullable ServerPlayer viewer;
    // Servidor: o último estado mandado, campo a campo, e o rascunho da conferência (reaproveitados, sem alocar).
    private @Nullable DockStatePayload sent;
    private final int[] sentFacts = new int[SIDES * FACTS];
    private final int[] facts = new int[SIDES * FACTS];
    private final @Nullable ResourceLocation[] sentBlocks = new ResourceLocation[SIDES];
    private final @Nullable Block[] sentProviders = new Block[SIDES];
    private final @Nullable Block[] providers = new Block[SIDES];
    private int sentTotal = -1;
    private int sentMax = -1;
    private int sentRadius = -1;
    private long lastCheck;
    private boolean closed;
    // Cliente: o último estado recebido.
    private @Nullable DockStatePayload state;

    /** Servidor. */
    public DockMenu(int containerId, Inventory inventory, PatternDockBlockEntity dock) {
        this(containerId, dock.getBlockPos(), dock, inventory.player instanceof ServerPlayer player ? player : null);
    }

    /** Cliente. */
    public DockMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, BlockPos.STREAM_CODEC.decode(buf), null, null);
    }

    /** Cliente (e a captura de desenvolvimento). */
    public DockMenu(int containerId, BlockPos pos) {
        this(containerId, pos, null, null);
    }

    private DockMenu(int containerId, BlockPos pos, @Nullable PatternDockBlockEntity dock,
            @Nullable ServerPlayer viewer) {
        super(ModMenus.PATTERN_DOCK.get(), containerId);
        this.pos = pos;
        this.dock = dock;
        this.viewer = viewer;
    }

    public static void open(ServerPlayer player, PatternDockBlockEntity dock) {
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new DockMenu(containerId, inventory, dock),
                        Component.translatable("container.wirelessautomate.pattern_dock")),
                buf -> BlockPos.STREAM_CODEC.encode(buf, dock.getBlockPos()));
    }

    public BlockPos pos() {
        return pos;
    }

    /** Cliente: o último estado recebido, ou {@code null} antes do primeiro. */
    public @Nullable DockStatePayload state() {
        return state;
    }

    /** Cliente: aplica um estado; também usado pela captura de desenvolvimento. */
    public void apply(DockStatePayload payload) {
        state = payload;
    }

    /**
     * Servidor: o estado a mandar agora, ou {@code null}. O primeiro vai na hora; depois, a Doca é conferida no máximo a
     * cada {@link #SYNC_INTERVAL} ticks e o estado só sai se algo mudou. Compara campo a campo antes de montar, para
     * não alocar sem mudança. Com o menu fechado, nada. Marca como mandado. Público para os GameTests.
     */
    public @Nullable DockStatePayload pollState(long now) {
        if (dock == null || closed || sent != null && now - lastCheck < SYNC_INTERVAL) {
            return null;
        }
        lastCheck = now;
        Level level = dock.getLevel();
        if (level == null) {
            return null;
        }
        RouterTier tier = dock.getBlockState().getValue(RouterBlock.TIER);
        int total = dock.machineCount();
        int max = Config.dockMaxMachines(tier);
        int radius = Config.dockRadius(tier);
        measure(level, now);
        if (sent != null && total == sentTotal && max == sentMax && radius == sentRadius
                && Arrays.equals(facts, sentFacts) && sameBlocks()) {
            return null;
        }
        List<DockStatePayload.Line> lines = new ArrayList<>(SIDES);
        for (int i = 0; i < SIDES; i++) {
            DockSide side = dock.side(i);
            ResourceLocation block = side == null ? null : side.block();
            sentBlocks[i] = block;
            sentProviders[i] = providers[i];
            int at = i * FACTS;
            lines.add(new DockStatePayload.Line(block, block == null ? Component.empty() : machineName(block),
                    providers[i] == null ? null : RouterBlock.machineName(providers[i]), facts[at], facts[at + 1],
                    facts[at + 2], facts[at + 3], facts[at + 4]));
        }
        System.arraycopy(facts, 0, sentFacts, 0, facts.length);
        sentTotal = total;
        sentMax = max;
        sentRadius = radius;
        sent = new DockStatePayload(containerId, total, max, radius, List.copyOf(lines));
        return sent;
    }

    /** Os fatos de cada lado em {@link #facts} e o bloco do provedor em {@link #providers}. */
    private void measure(Level level, long now) {
        for (int i = 0; i < SIDES; i++) {
            DockSide side = dock.side(i);
            boolean provider = DockAe2.hasProvider(dock, i) || DockRs2.hasProvider(dock, i);
            providers[i] = provider ? providerBlock(level, i) : null;
            int linked = 0;
            int working = 0;
            int unloaded = 0;
            int outOfReach = 0;
            int refusal = DockStatus.NO_REFUSAL;
            boolean full = false;
            boolean pending = false;
            if (side != null) {
                for (DockMachine machine : side.machines()) {
                    linked++;
                    if (machine.lastActive() > 0 && now - machine.lastActive() < DockStatus.WORKING_TICKS) {
                        working++;
                    }
                    if (!machine.active()) {
                        outOfReach++;
                    } else if (!level.isLoaded(machine.pos())) {
                        unloaded++;
                    }
                }
                refusal = side.lastRefusal();
                full = side.providerFull();
                pending = side.hasPending();
            }
            int at = i * FACTS;
            facts[at] = linked;
            facts[at + 1] = working;
            facts[at + 2] = unloaded;
            facts[at + 3] = outOfReach;
            // a trava só se lê com provedor e máquinas: é o único caso em que ela decide o estado
            boolean lock = provider && linked > 0 && DockAe2.lockedUntilResult(dock, i);
            facts[at + 4] = DockStatus.of(provider, linked, lock, refusal, full, refusal == DockStatus.ALL_FULL, pending,
                    working).ordinal();
        }
    }

    private boolean sameBlocks() {
        for (int i = 0; i < SIDES; i++) {
            DockSide side = dock.side(i);
            if (!Objects.equals(side == null ? null : side.block(), sentBlocks[i]) || providers[i] != sentProviders[i]) {
                return false;
            }
        }
        return true;
    }

    /** O bloco encostado no lado {@code side}, para o nome do provedor; nulo com o vizinho descarregado ou ar. */
    private @Nullable Block providerBlock(Level level, int side) {
        BlockPos neighbor = pos.relative(DIRECTIONS[side]);
        if (!level.isLoaded(neighbor)) {
            return null;
        }
        Block block = level.getBlockState(neighbor).getBlock();
        return block == Blocks.AIR ? null : block;
    }

    private static Component machineName(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block == Blocks.AIR ? Component.literal(id.toString()) : RouterBlock.machineName(block);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return dock == null || StorageListMenu.stillValid(dock, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        closed = true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (viewer == null) {
            return;
        }
        DockStatePayload payload = pollState(viewer.server.getTickCount());
        if (payload != null && viewer.connection != null && viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    /** Cliente: um pacote do servidor. */
    public static void onState(DockStatePayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof DockMenu menu && menu.containerId == payload.containerId()) {
            menu.apply(payload);
        }
    }
}
