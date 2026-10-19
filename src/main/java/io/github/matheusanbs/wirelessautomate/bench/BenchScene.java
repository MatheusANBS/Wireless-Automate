package io.github.matheusanbs.wirelessautomate.bench;

import com.mojang.authlib.GameProfile;
import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TabletHeaderPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletLivePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPagePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
 *
 * <p>No benchmark comparativo ({@link BenchTransport#logisticsNetwork()}), a máquina recebe um nó do
 * Logistics Network no lugar do roteador ({@link LogisticsNetworkBench}), com o canal 0 pelo lado de
 * cima e a vazão do tier do cenário ({@link LnChannelPlan}). A contagem neutra ({@link #delivered},
 * {@link #extractedBySource}) olha só os inventários: o que chegou aos destinos e o que saiu de cada
 * origem, sem perguntar a nenhum dos dois mods.
 */
final class BenchScene {
    static final BlockPos ORIGIN = new BlockPos(20_000, 200, 20_000);
    static final UUID OWNER = UUID.nameUUIDFromBytes("wirelessautomate:bench".getBytes(StandardCharsets.UTF_8));
    /** Slots livres no fim do destino quase cheio ({@link BenchScenario#BIG_FULL}). */
    static final int FREE_TAIL_SLOTS = 4;
    /** Estoque da lista branca do destino e quanto ele já tem ({@link BenchScenario#STOCK}). */
    static final int STOCK_LIMIT = 1_000;
    /** Tipos de papel (um nome por tipo) no Baú de destino do {@link BenchScenario#CHEST_TYPES}. */
    static final int CHEST_TYPES = 2_000;
    /** Estoque de pedregulho da lista branca do {@link BenchScenario#CHEST_TYPES}: nunca atingido na medição. */
    static final int CHEST_TYPES_STOCK = 1_000_000;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** {@code RESONATOR} é a Porta de Energia de um Ressonador e {@code BATTERY} a Bateria dele ({@link BenchResonators}). */
    enum Machine { SMALL, BIG, CHEST, FLUID_SOURCE, FLUID_SINK, ENERGY_SOURCE, ENERGY_SINK, STACK_SOURCE, ITEM_SINK, RESONATOR, BATTERY }

    /** Um nó: máquina, roteador em cima, tipo e papel; {@code index} varia o padrão de itens. */
    record Node(int index, BlockPos machine, BlockPos router, ResourceType type, boolean source, Machine kind) {
    }

    final ServerLevel level;
    final BenchScenario scenario;
    final BenchStorage storage;
    final BenchTransport transport;
    final List<Node> nodes = new ArrayList<>();
    /** Nós do Logistics Network, na ordem de {@link #nodes} (vazia com o Wireless Automate). */
    private final List<Entity> lnNodes = new ArrayList<>();
    private final List<UUID> lnNetworks = new ArrayList<>();
    /** Redes do Wireless Automate da cena ({@link #network} é a primeira). */
    private final List<UUID> networks = new ArrayList<>();
    /** Itens postos em cada origem pelo benchmark (enchimento e reenchimentos), na ordem de {@link #nodes}. */
    private long[] refilled = new long[0];
    /** Itens tirados dos destinos pelo reenchimento (esvaziar), somados. */
    private long destinationCleared;
    private final List<BlockPos> extraBlocks = new ArrayList<>();
    /** Blocos de redstone que ligam e desligam ({@link BenchScenario#clocked}), um acima de cada roteador. */
    private final List<BlockPos> clocks = new ArrayList<>();
    private boolean clockOn;
    private @Nullable TabletMenu tablet;
    private long tabletBytes;
    /** Os Ressonadores dos cenários {@code resonator-*}; {@code null} nos outros. */
    private @Nullable BenchResonators resonators;
    private final LongSet forcedChunks = new LongOpenHashSet();
    private final List<Item> sourceItems;
    private final List<Item> junkItems;
    private @Nullable UUID network;
    private int touched;
    private BlockPos min = ORIGIN;
    private BlockPos max = ORIGIN;

    BenchScene(ServerLevel level, BenchScenario scenario, BenchStorage storage, BenchTransport transport, int count) {
        this.level = level;
        this.scenario = scenario;
        this.storage = storage;
        this.transport = transport;
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
        if (scenario.resonator()) {
            planResonators(count);
            return;
        }
        int width = scenario.bigInventories() && storage.bigIsDoubleChest() ? 2 : 1;
        int side = (int) Math.ceil(Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            BlockPos machine = ORIGIN.offset((i % side) * width, 0, i / side);
            // No Baú central, só o nó 1 é destino; nos outros, origens e destinos se alternam.
            boolean source = scenario == BenchScenario.HUB ? i != 1 : i % 2 == 0;
            ResourceType type = ResourceType.ITEM;
            Machine kind = scenario.bigInventories() ? Machine.BIG : Machine.SMALL;
            if (scenario.modChestDestination() && !source) {
                kind = Machine.CHEST;
            }
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
            if (scenario == BenchScenario.BIG_STACK || scenario == BenchScenario.INFINITE) {
                kind = source ? Machine.STACK_SOURCE : Machine.ITEM_SINK;
            }
            nodes.add(new Node(i, machine, machine.above(), type, source, kind));
        }
        max = ORIGIN.offset(side * width, scenario.clocked() ? 2 : 1, (count + side - 1) / side);
        refilled = new long[count];
    }

    /**
     * {@code count} Ressonadores; no cenário que gera, cada um com dois nós: a Porta de Energia (origem) e a Bateria
     * (destino), vizinhos em {@link #nodes} para o {@link #attach} pôr cada par numa rede.
     */
    private void planResonators(int count) {
        BenchResonators planned = new BenchResonators(level, ORIGIN, count, scenario == BenchScenario.RESONATOR_GEN);
        resonators = planned;
        if (scenario == BenchScenario.RESONATOR_GEN) {
            for (BenchResonators.Resonator resonator : planned.list) {
                BlockPos battery = Objects.requireNonNull(resonator.battery());
                nodes.add(new Node(nodes.size(), resonator.energyPort(), resonator.energyPort().above(),
                        ResourceType.ENERGY, true, Machine.RESONATOR));
                nodes.add(new Node(nodes.size(), battery, battery.above(), ResourceType.ENERGY, false, Machine.BATTERY));
            }
        }
        max = planned.max();
        refilled = new long[nodes.size()];
    }

    @Nullable BenchResonators resonators() {
        return resonators;
    }

    /** Põe máquinas e roteadores, enche as máquinas e configura as faces. Os roteadores ficam sem rede. */
    void build() {
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                forcedChunks.add(ChunkPos.asLong(cx, cz));
            }
        }
        if (transport.logisticsNetwork()) {
            buildLogisticsNetwork();
            return;
        }
        if (resonators != null) {
            resonators.build();
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

    /** As máquinas e um nó do Logistics Network em cada, sem rede, na vazão do tier do cenário. */
    private void buildLogisticsNetwork() {
        LogisticsNetworkBench ln = LogisticsNetworkBench.get();
        LnChannelPlan plan = lnPlan(scenario);
        for (Node node : nodes) {
            placeMachine(node);
            fill(node);
        }
        for (Node node : nodes) {
            String type;
            int batch;
            if (node.type() == ResourceType.FLUID) {
                type = "fluid";
                batch = plan.fluid();
            } else if (node.type() == ResourceType.ENERGY) {
                type = "energy";
                batch = plan.energy();
            } else {
                type = "item";
                batch = plan.items();
            }
            lnNodes.add(ln.place(level, node.machine(), OWNER,
                    new LogisticsNetworkBench.Channel(type, node.source(), batch, transport.lnDistribution()),
                    plan.upgrade()));
        }
    }

    /** A configuração do canal do Logistics Network com a vazão do tier do cenário (config do servidor). */
    static LnChannelPlan lnPlan(BenchScenario scenario) {
        Config.TierValues tier = Config.TIERS.get(scenario.tier);
        return LnChannelPlan.forRates(tier.rate(ResourceType.ITEM), tier.rate(ResourceType.FLUID),
                tier.rate(ResourceType.ENERGY));
    }

    private void placeMachine(Node node) {
        switch (node.kind()) {
            case SMALL -> level.setBlock(node.machine(), storage.small(), PLACE_FLAGS);
            case CHEST -> level.setBlock(node.machine(), BenchStorage.chest(), PLACE_FLAGS);
            case BIG -> {
                if (storage.bigIsDoubleChest()) {
                    // Facing norte: a metade LEFT se liga à de leste (RIGHT).
                    BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
                    BlockPos right = node.machine().east();
                    level.setBlock(node.machine(), chest.setValue(ChestBlock.TYPE, ChestType.LEFT), PLACE_FLAGS);
                    level.setBlock(right, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT), PLACE_FLAGS);
                    extraBlocks.add(right);
                } else {
                    level.setBlock(node.machine(), storage.big(), PLACE_FLAGS);
                }
            }
            case FLUID_SOURCE -> level.setBlock(node.machine(), BenchCapabilities.FLUID_SOURCE.defaultBlockState(), PLACE_FLAGS);
            case FLUID_SINK -> level.setBlock(node.machine(), BenchCapabilities.FLUID_SINK.defaultBlockState(), PLACE_FLAGS);
            case ENERGY_SOURCE -> level.setBlock(node.machine(), BenchCapabilities.ENERGY_SOURCE.defaultBlockState(), PLACE_FLAGS);
            case ENERGY_SINK -> level.setBlock(node.machine(), BenchCapabilities.ENERGY_SINK.defaultBlockState(), PLACE_FLAGS);
            case STACK_SOURCE -> level.setBlock(node.machine(), BenchCapabilities.STACK_SOURCE.defaultBlockState(), PLACE_FLAGS);
            case ITEM_SINK -> level.setBlock(node.machine(), BenchCapabilities.ITEM_SINK.defaultBlockState(), PLACE_FLAGS);
            case RESONATOR, BATTERY -> {
                // Postas pelo BenchResonators.build.
            }
        }
    }

    private void configure(Node node, RouterBlockEntity router) {
        router.setMode(node.type(), Direction.UP, node.source() ? PortMode.EXTRACT : PortMode.INSERT);
        if (scenario == BenchScenario.REDSTONE_FACE && node.source()) {
            router.setRedstone(node.type(), Direction.UP, RedstoneMode.HIGH);
        }
        if (scenario == BenchScenario.TYPES) {
            router.setFilter(ResourceType.ITEM, Direction.UP, node.source() ? sourceFilter() : destinationFilter());
        } else if (scenario == BenchScenario.STOCK && !node.source()) {
            router.setFilter(ResourceType.ITEM, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                    List.of(new FilterEntry.ItemEntry(new ItemStack(Items.COBBLESTONE), STOCK_LIMIT))));
        } else if (scenario == BenchScenario.CHEST_TYPES && !node.source()) {
            router.setFilter(ResourceType.ITEM, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                    List.of(new FilterEntry.ItemEntry(new ItemStack(Items.COBBLESTONE), CHEST_TYPES_STOCK))));
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

    /**
     * Põe os roteadores (ou os nós do Logistics Network) em redes novas: uma só com todos, ou uma por par
     * de origem e destino vizinhos ({@link BenchScenario#PAIRS} e cada Ressonador com a sua Bateria). Nos cenários do
     * Ressonador, põe também os Núcleos, que montam nos ticks seguintes.
     */
    void attach() {
        int group = scenario == BenchScenario.PAIRS || scenario == BenchScenario.RESONATOR_GEN ? 2 : nodes.size();
        for (int start = 0; start < nodes.size(); start += group) {
            int end = Math.min(nodes.size(), start + group);
            String name = "bench-" + scenario.id + "-" + level.getGameTime() + "-" + start / group;
            if (transport.logisticsNetwork()) {
                lnNetworks.add(LogisticsNetworkBench.get().attach(level, name, OWNER, lnNodes.subList(start, end)));
                continue;
            }
            UUID id = NetworkSavedData.get(level.getServer()).create(OWNER, name).id();
            networks.add(id);
            for (Node node : nodes.subList(start, end)) {
                RouterBlockEntity router = router(node);
                if (router != null) {
                    router.setNetworkId(id);
                }
            }
        }
        network = networks.isEmpty() ? null : networks.get(0);
        if (resonators != null) {
            resonators.form();
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

    /**
     * Sincroniza o Tablet aberto, se houver; devolve quanto levou, em ns. Os pacotes que sairiam são codificados
     * depois, fora do tempo medido, para somar os bytes ({@link #tabletBytes}).
     */
    long syncTablet() {
        TabletMenu menu = tablet;
        if (menu == null) {
            return 0;
        }
        // O que o broadcastChanges do menu faz no servidor, menos o envio: o jogador falso não tem
        // canal de rede (o hasChannel dá NullPointerException) e o menu não tem slots.
        long start = System.nanoTime();
        List<CustomPacketPayload> payloads = menu.pollPayloads();
        long nanos = System.nanoTime() - start;
        for (CustomPacketPayload payload : payloads) {
            tabletBytes += encodedSize(payload);
        }
        return nanos;
    }

    /** Os bytes de um pacote do Tablet pelo codec dele (cabeçalho, página ou o estado e a vazão). */
    private int encodedSize(CustomPacketPayload payload) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        if (payload instanceof TabletHeaderPayload header) {
            TabletHeaderPayload.STREAM_CODEC.encode(buf, header);
        } else if (payload instanceof TabletPagePayload page) {
            TabletPagePayload.STREAM_CODEC.encode(buf, page);
        } else if (payload instanceof TabletLivePayload live) {
            TabletLivePayload.STREAM_CODEC.encode(buf, live);
        }
        int size = buf.writerIndex();
        buf.release();
        return size;
    }

    /** Bytes que o Tablet aberto mandaria, somados desde que a cena começou. */
    long tabletBytes() {
        return tabletBytes;
    }

    /** O que a cena faz no fim de cada tick depois de pôr a rede: os relógios de redstone e a sintonia dos Ressonadores. */
    void step() {
        toggleClocks();
        if (resonators != null) {
            resonators.tune();
        }
    }

    /** Liga ou desliga os blocos de redstone em cima dos roteadores, avisando os vizinhos. */
    private void toggleClocks() {
        if (!scenario.clocked()) {
            return;
        }
        clockOn = !clockOn;
        BlockState state = (clockOn ? Blocks.REDSTONE_BLOCK : Blocks.AIR).defaultBlockState();
        if (clocks.isEmpty()) {
            // Encostado na peça que transporta: em cima do roteador, ou em cima da máquina com o nó.
            for (Node node : nodes) {
                clocks.add(transport.logisticsNetwork() ? node.machine().above() : node.router().above());
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
        if (transport.logisticsNetwork()) {
            return lnNodes.size() == nodes.size() && lnNodes.stream().allMatch(Entity::isAlive);
        }
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

    /**
     * Contagem neutra: unidades que chegaram aos destinos até agora (itens nos destinos mais os já
     * tirados pelo reenchimento, mais o que os ralos de teste receberam; fluido e energia só nos ralos).
     * Use a diferença entre duas leituras.
     */
    long delivered(ResourceType type) {
        if (type == ResourceType.FLUID) {
            return BenchCapabilities.fluidReceived();
        }
        if (type == ResourceType.ENERGY) {
            return BenchCapabilities.energyReceived();
        }
        if (type != ResourceType.ITEM) {
            return 0;
        }
        long total = destinationCleared + BenchCapabilities.itemsReceived();
        for (Node node : nodes) {
            IItemHandler handler = node.source() ? null : items(node);
            if (handler != null) {
                total += count(handler);
            }
        }
        return total;
    }

    /**
     * Contagem neutra: itens que saíram de cada origem com inventário até agora (o que o benchmark pôs
     * menos o que sobrou), na ordem de {@link #nodes}; {@code -1} para destinos e origens sem inventário
     * (fonte infinita, fluido, energia).
     */
    long[] extractedBySource() {
        long[] result = new long[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            IItemHandler handler = node.source() ? items(node) : null;
            result[i] = handler == null ? -1 : refilled[i] - count(handler);
        }
        return result;
    }

    private static long count(IItemHandler handler) {
        long total = 0;
        for (int slot = 0, n = handler.getSlots(); slot < n; slot++) {
            total += handler.getStackInSlot(slot).getCount();
        }
        return total;
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
        if (storage == BenchStorage.WA) {
            return BenchStorage.virtualSlots(scenario.bigInventories());
        }
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
            } else if (scenario == BenchScenario.CHEST_TYPES) {
                ItemStorage chest = modChest(node);
                if (chest != null) {
                    // Só o pedregulho que chegou: os 2.000 tipos de papel ficam.
                    destinationCleared += chest.extract(new ItemStack(Items.COBBLESTONE), Long.MAX_VALUE, false);
                }
            } else if (scenario == BenchScenario.BIG_FULL) {
                destinationCleared += clear(handler, Math.max(0, handler.getSlots() - FREE_TAIL_SLOTS));
            } else if (scenario != BenchScenario.FULL && scenario != BenchScenario.STOCK) {
                destinationCleared += clear(node, handler);
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
        ItemStorage chest = modChest(node);
        if (chest != null && !node.source()) {
            fillChest(chest);
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

    /** O destino Baú do mod: cheio de pedra no {@code full}, o estoque no {@code stock} e os tipos de papel no {@code chesttypes}. */
    private void fillChest(ItemStorage chest) {
        if (scenario == BenchScenario.FULL) {
            chest.insert(new ItemStack(Items.STONE), chest.capacity(), false);
        } else if (scenario == BenchScenario.STOCK) {
            chest.insert(new ItemStack(Items.COBBLESTONE), STOCK_LIMIT, false);
        } else if (scenario == BenchScenario.CHEST_TYPES) {
            for (int i = 0; i < CHEST_TYPES; i++) {
                ItemStack paper = new ItemStack(Items.PAPER);
                paper.set(DataComponents.CUSTOM_NAME, Component.literal("bench " + i));
                chest.insert(paper, 1, false);
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
        ItemStorage chest = modChest(node);
        long added = 0;
        if (chest != null) {
            // Sem slots: completa cada item até as pilhas de 64 que os slots virtuais pedem.
            Map<Item, Long> want = new LinkedHashMap<>();
            for (int slot = 0, n = BenchStorage.virtualSlots(scenario.bigInventories()); slot < n; slot++) {
                want.merge(pattern(node, slot), (long) Items.COBBLESTONE.getDefaultMaxStackSize(), Long::sum);
            }
            for (Map.Entry<Item, Long> entry : want.entrySet()) {
                ItemStack key = new ItemStack(entry.getKey());
                long missing = entry.getValue() - chest.count(key);
                if (missing > 0) {
                    added += chest.insert(key, missing, false);
                }
            }
        } else {
            for (int slot = 0, n = handler.getSlots(); slot < n; slot++) {
                added += top(handler, slot, pattern(node, slot));
            }
        }
        refilled[node.index()] += added;
    }

    /**
     * Completa o slot com o item até 64 (ou o limite do slot). Slot com outro item fica como está.
     * Devolve quantos entraram.
     */
    private static int top(IItemHandler handler, int slot, Item item) {
        ItemStack inSlot = handler.getStackInSlot(slot);
        if (!inSlot.isEmpty() && !inSlot.is(item)) {
            return 0;
        }
        int want = Math.min(handler.getSlotLimit(slot), item.getDefaultMaxStackSize()) - inSlot.getCount();
        if (want <= 0) {
            return 0;
        }
        return want - handler.insertItem(slot, new ItemStack(item, want), false).getCount();
    }

    /** Esvazia a máquina inteira (o Baú do mod pelo conteúdo); devolve quantos itens saíram. */
    private long clear(Node node, IItemHandler handler) {
        ItemStorage chest = modChest(node);
        if (chest == null) {
            return clear(handler, 0);
        }
        long removed = 0;
        while (chest.types() > 0) {
            int last = chest.types() - 1;
            removed += chest.extract(chest.key(last), chest.count(last), false);
        }
        return removed;
    }

    /** Esvazia os slots a partir de {@code from}; devolve quantos itens saíram. */
    private static long clear(IItemHandler handler, int from) {
        long removed = 0;
        for (int slot = from, n = handler.getSlots(); slot < n; slot++) {
            for (int guard = 0; guard < 64 && !handler.getStackInSlot(slot).isEmpty(); guard++) {
                ItemStack out = handler.extractItem(slot, Integer.MAX_VALUE, false);
                if (out.isEmpty()) {
                    break;
                }
                removed += out.getCount();
            }
        }
        return removed;
    }

    /** O conteúdo da máquina, se ela é um Baú do mod. */
    private @Nullable ItemStorage modChest(Node node) {
        return level.getBlockEntity(node.machine()) instanceof StorageChestBlockEntity chest ? chest.storage() : null;
    }

    private @Nullable IItemHandler items(Node node) {
        if (node.kind() != Machine.SMALL && node.kind() != Machine.BIG && node.kind() != Machine.CHEST) {
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
        if (transport.logisticsNetwork()) {
            LogisticsNetworkBench.get().teardown(level, lnNodes, lnNetworks);
            lnNodes.clear();
            lnNetworks.clear();
        }
        for (UUID id : networks) {
            NetworkSavedData.get(level.getServer()).remove(id);
        }
        networks.clear();
        network = null;
        if (resonators != null) {
            resonators.teardown();
        }
        for (Node node : nodes) {
            IItemHandler handler = items(node);
            if (handler != null) {
                clear(node, handler);
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
