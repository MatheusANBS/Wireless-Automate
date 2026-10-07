package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Monta, mantém e desmonta a cena de um cenário numa área afastada ({@link #ORIGIN}), com os chunks
 * forçados. Cada nó é uma máquina com um roteador em cima (facing UP), configurado pela face
 * {@link Direction#UP} da máquina. Os roteadores nascem sem rede (linha de base) e entram numa rede
 * nova em {@link #attach}.
 */
final class BenchScene {
    static final BlockPos ORIGIN = new BlockPos(20_000, 200, 20_000);
    static final UUID OWNER = UUID.nameUUIDFromBytes("wirelessautomate:bench".getBytes(StandardCharsets.UTF_8));
    /** Slots livres no fim do destino quase cheio ({@link BenchScenario#BIG_FULL}). */
    static final int FREE_TAIL_SLOTS = 4;
    /** Estoque da lista branca do destino e quanto ele já tem ({@link BenchScenario#STOCK}). */
    static final int STOCK_LIMIT = 1_000;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    enum Machine { SMALL, BIG, FLUID_SOURCE, FLUID_SINK, ENERGY_SOURCE, ENERGY_SINK, STACK_SOURCE, ITEM_SINK }

    /** Um nó: máquina, roteador em cima, tipo e papel; {@code index} varia o padrão de itens. */
    record Node(int index, BlockPos machine, BlockPos router, ResourceType type, boolean source, Machine kind) {
    }

    final ServerLevel level;
    final BenchScenario scenario;
    final BenchStorage storage;
    final List<Node> nodes = new ArrayList<>();
    private final List<BlockPos> extraBlocks = new ArrayList<>();
    /** Blocos de redstone que ligam e desligam ({@link BenchScenario#REDSTONE}), um acima de cada roteador. */
    private final List<BlockPos> clocks = new ArrayList<>();
    private boolean clockOn;
    private @Nullable TabletMenu tablet;
    private final LongSet forcedChunks = new LongOpenHashSet();
    private final List<Item> sourceItems;
    private final List<Item> junkItems;
    private @Nullable UUID network;
    private int touched;
    private BlockPos min = ORIGIN;
    private BlockPos max = ORIGIN;

    BenchScene(ServerLevel level, BenchScenario scenario, BenchStorage storage, int count) {
        this.level = level;
        this.scenario = scenario;
        this.storage = storage;
        List<Item> items = stackableItems();
        sourceItems = new ArrayList<>();
        junkItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            (i % 2 == 0 ? sourceItems : junkItems).add(items.get(i));
        }
        plan(count);
    }

    /** Itens do minecraft que empilham até 64 e cabem em qualquer inventário, em ordem de id. */
    private static List<Item> stackableItems() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (!key.getNamespace().equals("minecraft") || item == Items.AIR
                    || item.getDefaultMaxStackSize() != 64
                    || (item instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock)) {
                continue;
            }
            items.add(item);
        }
        items.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        return items;
    }

    private void plan(int count) {
        int width = scenario.bigInventories() && storage.bigIsDoubleChest() ? 2 : 1;
        int side = (int) Math.ceil(Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            BlockPos machine = ORIGIN.offset((i % side) * width, 0, i / side);
            boolean source = i % 2 == 0;
            ResourceType type = ResourceType.ITEM;
            Machine kind = scenario.bigInventories() ? Machine.BIG : Machine.SMALL;
            if (scenario == BenchScenario.MIXED) {
                // Pares vizinhos do mesmo tipo: itens, fluidos, energia, itens...
                int group = (i / 2) % 3;
                if (group == 1) {
                    type = ResourceType.FLUID;
                    kind = source ? Machine.FLUID_SOURCE : Machine.FLUID_SINK;
                } else if (group == 2) {
                    type = ResourceType.ENERGY;
                    kind = source ? Machine.ENERGY_SOURCE : Machine.ENERGY_SINK;
                }
            }
            if (scenario == BenchScenario.BIG_STACK) {
                kind = source ? Machine.STACK_SOURCE : Machine.ITEM_SINK;
            }
            nodes.add(new Node(i, machine, machine.above(), type, source, kind));
        }
        max = ORIGIN.offset(side * width, scenario == BenchScenario.REDSTONE ? 2 : 1, (count + side - 1) / side);
    }

    /** Põe máquinas e roteadores, enche as máquinas e configura as faces. Os roteadores ficam sem rede. */
    void build() {
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                forcedChunks.add(ChunkPos.asLong(cx, cz));
            }
        }
        BlockState router = ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, scenario.tier);
        for (Node node : nodes) {
            placeMachine(node);
            fill(node);
            level.setBlock(node.router(), router, PLACE_FLAGS);
            if (level.getBlockEntity(node.router()) instanceof RouterBlockEntity entity) {
                configure(node, entity);
            }
        }
    }

    private void placeMachine(Node node) {
        switch (node.kind()) {
            case SMALL -> level.setBlock(node.machine(), storage.small().defaultBlockState(), PLACE_FLAGS);
            case BIG -> {
                if (storage.bigIsDoubleChest()) {
                    // Facing norte: a metade LEFT se liga à de leste (RIGHT).
                    BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
                    BlockPos right = node.machine().east();
                    level.setBlock(node.machine(), chest.setValue(ChestBlock.TYPE, ChestType.LEFT), PLACE_FLAGS);
                    level.setBlock(right, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT), PLACE_FLAGS);
                    extraBlocks.add(right);
                } else {
                    level.setBlock(node.machine(), storage.big().defaultBlockState(), PLACE_FLAGS);
                }
            }
            case FLUID_SOURCE -> level.setBlock(node.machine(), BenchCapabilities.FLUID_SOURCE.defaultBlockState(), PLACE_FLAGS);
            case FLUID_SINK -> level.setBlock(node.machine(), BenchCapabilities.FLUID_SINK.defaultBlockState(), PLACE_FLAGS);
            case ENERGY_SOURCE -> level.setBlock(node.machine(), BenchCapabilities.ENERGY_SOURCE.defaultBlockState(), PLACE_FLAGS);
            case ENERGY_SINK -> level.setBlock(node.machine(), BenchCapabilities.ENERGY_SINK.defaultBlockState(), PLACE_FLAGS);
            case STACK_SOURCE -> level.setBlock(node.machine(), BenchCapabilities.STACK_SOURCE.defaultBlockState(), PLACE_FLAGS);
            case ITEM_SINK -> level.setBlock(node.machine(), BenchCapabilities.ITEM_SINK.defaultBlockState(), PLACE_FLAGS);
        }
    }

    private void configure(Node node, RouterBlockEntity router) {
        router.setMode(node.type(), Direction.UP, node.source() ? PortMode.EXTRACT : PortMode.INSERT);
        if (scenario == BenchScenario.TYPES) {
            router.setFilter(ResourceType.ITEM, Direction.UP, node.source() ? sourceFilter() : destinationFilter());
        } else if (scenario == BenchScenario.STOCK && !node.source()) {
            router.setFilter(ResourceType.ITEM, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                    List.of(new FilterEntry.ItemEntry(new ItemStack(Items.COBBLESTONE), STOCK_LIMIT))));
        }
    }

    /** Lista negra com os itens de "lixo" (nenhum está na origem): tudo passa, depois de consultar. */
    private Filter sourceFilter() {
        List<FilterEntry> entries = new ArrayList<>();
        for (Item item : junkItems) {
            entries.add(new FilterEntry.ItemEntry(new ItemStack(item), 0));
        }
        return new Filter(Filter.ListMode.BLACKLIST, false, entries);
    }

    /** Lista branca com todos os itens e algumas tags: milhares de entradas. */
    private Filter destinationFilter() {
        List<FilterEntry> entries = new ArrayList<>();
        for (String tag : new String[] {"minecraft:logs", "minecraft:planks", "minecraft:wool", "c:ingots", "c:gems", "c:ores"}) {
            entries.add(new FilterEntry.TagEntry(ResourceLocation.parse(tag), 0));
        }
        for (Item item : junkItems) {
            entries.add(new FilterEntry.ItemEntry(new ItemStack(item), 0));
        }
        for (Item item : sourceItems) {
            entries.add(new FilterEntry.ItemEntry(new ItemStack(item), 0));
        }
        return new Filter(Filter.ListMode.WHITELIST, false, entries);
    }

    /** Põe os roteadores numa rede nova. */
    void attach() {
        network = NetworkSavedData.get(level.getServer())
                .create(OWNER, "bench-" + scenario.id + "-" + level.getGameTime()).id();
        for (Node node : nodes) {
            RouterBlockEntity router = router(node);
            if (router != null) {
                router.setNetworkId(network);
            }
        }
    }

    /**
     * Abre o Tablet para um jogador falso dono da rede da cena ({@link BenchScenario#TABLET}): ele vê
     * todos os nós dela. O menu é criado direto (sem pacote de abertura) e sincronizado por
     * {@link #syncTablet}, como o {@code ServerPlayer} faz a cada tick com a tela aberta.
     */
    void openTablet() {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(OWNER, "[wa-bench]"));
        player.setPos(ORIGIN.getX() + 0.5, ORIGIN.getY() + 2, ORIGIN.getZ() + 0.5);
        tablet = new TabletMenu(1, player.getInventory(), player);
    }

    /** Sincroniza o Tablet aberto, se houver; devolve quanto levou, em ns. */
    long syncTablet() {
        TabletMenu menu = tablet;
        if (menu == null) {
            return 0;
        }
        // O que o broadcastChanges do menu faz no servidor, menos o envio: o jogador falso não tem
        // canal de rede (o hasChannel dá NullPointerException) e o menu não tem slots.
        long start = System.nanoTime();
        menu.pollSnapshot();
        return System.nanoTime() - start;
    }

    /** Liga ou desliga os blocos de redstone em cima dos roteadores, avisando os vizinhos. */
    void toggleClocks() {
        if (scenario != BenchScenario.REDSTONE) {
            return;
        }
        clockOn = !clockOn;
        BlockState state = (clockOn ? Blocks.REDSTONE_BLOCK : Blocks.AIR).defaultBlockState();
        if (clocks.isEmpty()) {
            for (Node node : nodes) {
                clocks.add(node.router().above());
            }
        }
        for (BlockPos pos : clocks) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    @Nullable UUID network() {
        return network;
    }

    /** Todos os roteadores já se registraram no gerenciador (o {@code onLoad} roda no tick seguinte). */
    boolean registered() {
        NetworkManager manager = NetworkManager.get();
        for (Node node : nodes) {
            RouterBlockEntity router = router(node);
            if (router == null || !manager.contains(router)) {
                return false;
            }
        }
        return true;
    }

    @Nullable RouterBlockEntity router(Node node) {
        return level.getBlockEntity(node.router()) instanceof RouterBlockEntity router ? router : null;
    }

    /** Total movido como origem pelos roteadores da cena, por tipo. */
    long moved(ResourceType type) {
        long total = 0;
        for (Node node : nodes) {
            RouterBlockEntity router = node.source() ? router(node) : null;
            if (router != null) {
                total += router.moved(type);
            }
        }
        return total;
    }

    /** Quanto cada origem moveu até agora (na ordem de {@link #nodes}; destinos ficam com 0). */
    long[] movedBySource() {
        long[] result = new long[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            RouterBlockEntity router = node.source() ? router(node) : null;
            if (router != null) {
                result[i] = router.moved(node.type());
            }
        }
        return result;
    }

    int sourceCount() {
        int count = 0;
        for (Node node : nodes) {
            if (node.source()) {
                count++;
            }
        }
        return count;
    }

    /** Slots do inventário de itens das origens (para o relatório); 0 sem inventário. */
    int slotsPerMachine() {
        for (Node node : nodes) {
            IItemHandler handler = items(node);
            if (handler != null) {
                return handler.getSlots();
            }
        }
        return 0;
    }

    /** Reenche as origens e esvazia os destinos (ou só a cauda livre, no quase cheio). */
    void recycle() {
        for (Node node : nodes) {
            if (node.type() != ResourceType.ITEM || scenario == BenchScenario.IDLE) {
                continue;
            }
            IItemHandler handler = items(node);
            if (handler == null) {
                continue;
            }
            if (node.source()) {
                if (activeSource(node)) {
                    refill(node, handler);
                }
            } else if (scenario == BenchScenario.BIG_FULL) {
                clear(handler, Math.max(0, handler.getSlots() - FREE_TAIL_SLOTS));
            } else if (scenario != BenchScenario.FULL && scenario != BenchScenario.STOCK) {
                clear(handler, 0);
            }
        }
    }

    /** Muda a prioridade de uma origem, o que suja a rede (cenário de remontagem). */
    void touch() {
        List<Node> sources = nodes.stream().filter(Node::source).toList();
        if (sources.isEmpty()) {
            return;
        }
        Node node = sources.get(touched++ % sources.size());
        RouterBlockEntity router = router(node);
        if (router != null) {
            int priority = router.face(node.type(), Direction.UP).priority();
            router.setPriority(node.type(), Direction.UP, priority == 0 ? 1 : 0);
        }
    }

    /** Origem que recebe itens: todas, menos no ocioso e, no esparso, só a primeira. */
    private boolean activeSource(Node node) {
        return scenario != BenchScenario.IDLE && (scenario != BenchScenario.SPARSE || node.index() == 0);
    }

    private void fill(Node node) {
        IItemHandler handler = items(node);
        if (handler == null) {
            return;
        }
        if (node.source()) {
            if (activeSource(node)) {
                refill(node, handler);
            }
        } else if (scenario == BenchScenario.STOCK) {
            int left = STOCK_LIMIT;
            for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                int count = Math.min(left, Items.COBBLESTONE.getDefaultMaxStackSize());
                left -= count - handler.insertItem(slot, new ItemStack(Items.COBBLESTONE, count), false).getCount();
            }
        } else if (scenario == BenchScenario.FULL) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                top(handler, slot, Items.STONE);
            }
        } else if (scenario == BenchScenario.BIG_FULL) {
            int filled = handler.getSlots() - FREE_TAIL_SLOTS;
            for (int slot = 0; slot < filled; slot++) {
                top(handler, slot, junkItems.get((slot + node.index()) % junkItems.size()));
            }
        }
    }

    /** Item que a origem guarda em cada slot: pedregulho, ou um diferente por slot nos inventários grandes. */
    private Item pattern(Node node, int slot) {
        if (scenario == BenchScenario.RAW || scenario == BenchScenario.STOCK || !scenario.bigInventories()) {
            return Items.COBBLESTONE;
        }
        return sourceItems.get((slot + node.index() * 7) % sourceItems.size());
    }

    private void refill(Node node, IItemHandler handler) {
        for (int slot = 0, n = handler.getSlots(); slot < n; slot++) {
            top(handler, slot, pattern(node, slot));
        }
    }

    /** Completa o slot com o item até 64 (ou o limite do slot). Slot com outro item fica como está. */
    private static void top(IItemHandler handler, int slot, Item item) {
        ItemStack inSlot = handler.getStackInSlot(slot);
        if (!inSlot.isEmpty() && !inSlot.is(item)) {
            return;
        }
        int want = Math.min(handler.getSlotLimit(slot), item.getDefaultMaxStackSize()) - inSlot.getCount();
        if (want > 0) {
            handler.insertItem(slot, new ItemStack(item, want), false);
        }
    }

    private static void clear(IItemHandler handler, int from) {
        for (int slot = from, n = handler.getSlots(); slot < n; slot++) {
            for (int guard = 0; guard < 64 && !handler.getStackInSlot(slot).isEmpty(); guard++) {
                if (handler.extractItem(slot, Integer.MAX_VALUE, false).isEmpty()) {
                    break;
                }
            }
        }
    }

    private @Nullable IItemHandler items(Node node) {
        if (node.kind() != Machine.SMALL && node.kind() != Machine.BIG) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, node.machine(), Direction.UP);
    }

    /** Tira a rede, esvazia e remove tudo, solta os chunks. */
    void teardown() {
        tablet = null;
        for (BlockPos pos : clocks) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        }
        clocks.clear();
        if (network != null) {
            NetworkSavedData.get(level.getServer()).remove(network);
            network = null;
        }
        for (Node node : nodes) {
            IItemHandler handler = items(node);
            if (handler != null) {
                clear(handler, 0);
            }
            level.setBlock(node.router(), Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        }
        for (Node node : nodes) {
            level.setBlock(node.machine(), Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        }
        for (BlockPos pos : extraBlocks) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        }
        AABB box = new AABB(min.getX() - 2, min.getY() - 2, min.getZ() - 2, max.getX() + 3, max.getY() + 3, max.getZ() + 3);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            item.discard();
        }
        for (long chunk : forcedChunks) {
            level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
        }
        forcedChunks.clear();
    }
}
