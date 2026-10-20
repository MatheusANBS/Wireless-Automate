package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows.Block;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows.Machine;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows.More;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows.RouterRef;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows.Row;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.IndexRemotes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TabletMachinesPayload;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * A visão por máquinas da Lista de um Tablet aberto (servidor, etapa 3b): a visão (roteadores ou máquinas), os blocos
 * abertos e recolhidos à mão e os com "Mostrar todas", mudados pelo {@link TabletMachinesPayload}; e a montagem da
 * página nessa visão ({@link TabletMachineRows}), numa passada pela ordem por bloco compartilhada ({@link MachineOrder})
 * com a consulta do jogador. Um bloco fora dos dois conjuntos segue a regra padrão: aberto se a visão inteira cabe em
 * {@link TabletSnapshot#MAX_NODES} linhas, recolhido senão.
 *
 * <p>Teto: {@link #MAX_BLOCKS} blocos em cada conjunto; acima disso, a ação é recusada. A montagem guarda as linhas
 * e só refaz a passada quando o índice, a consulta, os conjuntos ou os roteadores que passam nos filtros rápidos
 * mudam; nas outras amostras, só o estado e a vazão das linhas à vista.
 */
public final class TabletMachines {
    public static final int MAX_BLOCKS = 256;
    /** Máquinas por bloco antes do "Mostrar todas". */
    static final int FEW = 8;
    private static final ResourceType[] TYPES = ResourceType.values();

    /** Cópia imutável do estado, para o Tablet lembrar ao reabrir ({@link TabletMenu.State}). */
    public record Saved(boolean machines, List<ResourceLocation> opened, List<ResourceLocation> closed,
            List<ResourceLocation> showAll) {
        public static final Saved EMPTY = new Saved(false, List.of(), List.of(), List.of());
    }

    private final MinecraftServer server;
    private boolean active;
    private final Set<ResourceLocation> opened = new ObjectLinkedOpenHashSet<>();
    private final Set<ResourceLocation> closed = new ObjectLinkedOpenHashSet<>();
    private final Set<ResourceLocation> showAll = new ObjectLinkedOpenHashSet<>();
    /** Muda a cada ação que vale, para a montagem saber que os conjuntos mudaram. */
    private int setsVersion;

    /** Os roteadores (pela posição na ordem da Lista) visíveis e nos filtros rápidos, marcados na passada do índice. */
    private final BitSet marks = new BitSet();
    /** As máquinas da ordem por bloco que passam na consulta, da última passada. */
    private final BitSet pass = new BitSet();
    private int[] count = new int[0];
    private int[] routers = new int[0];
    private int[] hidden = new int[0];
    private int[] first = new int[0];
    private int[] last = new int[0];

    // A última passada: o que ela leu e o que montou (as linhas sem o estado e a vazão).
    private @Nullable MachineOrder builtOrder;
    private int builtIndexVersion;
    private @Nullable Query builtQuery;
    /** A consulta do Tablet da última página, para refazer a passada no bloco marcado. */
    private @Nullable TabletQuery builtFrom;
    private int builtSets = -1;
    private final BitSet builtMarks = new BitSet();
    private List<Row> builtRows = List.of();
    private List<RouterRef> builtRouters = List.of();
    /** Por linha de máquina, na ordem: a posição dela na ordem por bloco, para refazer o estado e a vazão. */
    private int[] builtKeys = new int[0];
    private int builtTotalRows;
    private int builtMachines;
    private int builtBlocks;

    TabletMachines(MinecraftServer server) {
        this.server = server;
    }

    /** A Lista está na visão por máquinas. */
    public boolean active() {
        return active;
    }

    /**
     * Uma ação da tela, validada: as de bloco pedem a visão por máquinas e um bloco, e abrir, recolher e "Mostrar
     * todas" pedem espaço no conjunto. Devolve se valeu.
     */
    boolean apply(TabletMachinesPayload.Action action, Optional<ResourceLocation> block) {
        boolean done = switch (action) {
            case ROUTERS -> {
                setActive(false, false);
                yield true;
            }
            case MACHINES -> {
                setActive(true, false);
                yield true;
            }
            default -> active && block.isPresent() && applyBlock(action, block.get());
        };
        if (done) {
            setsVersion++;
        }
        return done;
    }

    private boolean applyBlock(TabletMachinesPayload.Action action, ResourceLocation block) {
        return switch (action) {
            case OPEN_BLOCK -> {
                closed.remove(block);
                yield add(opened, block);
            }
            case CLOSE_BLOCK -> {
                opened.remove(block);
                yield add(closed, block);
            }
            case SHOW_ALL -> add(showAll, block);
            case SHOW_FEW -> {
                showAll.remove(block);
                yield true;
            }
            case ROUTERS, MACHINES -> false;
        };
    }

    private static boolean add(Set<ResourceLocation> set, ResourceLocation block) {
        return set.contains(block) || set.size() < MAX_BLOCKS && set.add(block);
    }

    private void setActive(boolean machines, boolean closing) {
        if (machines == active) {
            return;
        }
        active = machines;
        TabletSample sample = TabletSample.get(server);
        if (machines) {
            sample.watchMachines(this);
        } else {
            sample.unwatchMachines(this, closing);
            builtOrder = null;
        }
    }

    /** O Tablet fechou: sai da visão por máquinas (a ordem por bloco sai na hora se ninguém mais olha). */
    void stop() {
        if (active) {
            TabletSample.get(server).unwatchMachines(this, true);
        }
    }

    Saved save() {
        if (!active && opened.isEmpty() && closed.isEmpty() && showAll.isEmpty()) {
            return Saved.EMPTY;
        }
        return new Saved(active, List.copyOf(opened), List.copyOf(closed), List.copyOf(showAll));
    }

    /** Põe o estado guardado, nos tetos. Só no servidor, antes da primeira montagem. */
    void restore(Saved saved) {
        restore(opened, saved.opened());
        restore(closed, saved.closed());
        restore(showAll, saved.showAll());
        setActive(saved.machines(), false);
    }

    private static void restore(Set<ResourceLocation> set, List<ResourceLocation> blocks) {
        for (ResourceLocation block : blocks) {
            if (set.size() < MAX_BLOCKS) {
                set.add(block);
            }
        }
    }

    // ------------------------------------------------------------------ montagem

    /** Uma passada nova pelo índice vai marcar os roteadores ({@link #markRouter}). */
    void beginRouters() {
        marks.clear();
    }

    /** O roteador na posição {@code rank} da ordem da Lista é visível e passa nos filtros rápidos. */
    void markRouter(int rank) {
        marks.set(rank);
    }

    /**
     * A página da visão por máquinas: refaz as linhas só se o índice, a consulta, os conjuntos ou os roteadores
     * marcados mudaram desde a última; o estado e a vazão das máquinas à vista, sempre, da amostra compartilhada.
     */
    TabletMachineRows page(TabletSample shared, NodeIndex index, TabletQuery query, long now) {
        MachineOrder order = shared.machineOrder(index);
        builtFrom = query;
        if (order != builtOrder || index.version() != builtIndexVersion || !query.query().equals(builtQuery)
                || setsVersion != builtSets || !marks.equals(builtMarks)) {
            remake(order, index, query);
        }
        List<MachineLive> lives = new ArrayList<>(builtKeys.length);
        NetworkSavedData data = NetworkSavedData.get(server);
        for (int k : builtKeys) {
            lives.add(live(order, k, shared, data, now));
        }
        return new TabletMachineRows(true, builtRows, builtRouters, List.copyOf(lives), builtTotalRows, builtMachines,
                builtBlocks);
    }

    private void remake(MachineOrder order, NodeIndex index, TabletQuery query) {
        rebuild(order, query);
        builtOrder = order;
        builtIndexVersion = index.version();
        builtQuery = query.query();
        builtSets = setsVersion;
        builtMarks.clear();
        builtMarks.or(marks);
    }

    private void rebuild(MachineOrder order, TabletQuery query) {
        int blocks = order.blockCount();
        grow(blocks);
        Arrays.fill(count, 0, blocks, 0);
        Arrays.fill(routers, 0, blocks, 0);
        Arrays.fill(hidden, 0, blocks, 0);
        int machines = countPassing(order, query);
        int allOpen = 0;
        int blockCount = 0;
        for (int b = 0; b < blocks; b++) {
            if (count[b] > 0) {
                blockCount++;
                int limit = limit(order.block(b), count[b]);
                allOpen += 1 + limit + (count[b] > limit ? 1 : 0);
            }
        }
        boolean defaultOpen = allOpen <= TabletSnapshot.MAX_NODES;
        List<Row> rows = new ArrayList<>();
        IntArrayList keys = new IntArrayList();
        Object2IntOpenHashMap<NodeKey> palette = new Object2IntOpenHashMap<>();
        palette.defaultReturnValue(-1);
        List<RouterRef> refs = new ArrayList<>();
        int totalRows = 0;
        for (int b = 0; b < blocks; b++) {
            if (count[b] == 0) {
                continue;
            }
            ResourceLocation block = order.block(b);
            boolean open = opened.contains(block) || !closed.contains(block) && defaultOpen;
            int limit = limit(block, count[b]);
            int more = count[b] - limit;
            totalRows += 1 + (open ? limit + (more > 0 ? 1 : 0) : 0);
            if (rows.size() >= TabletSnapshot.MAX_NODES) {
                continue;
            }
            rows.add(new Block(block, count[b], routers[b], !open, showAll.contains(block), hidden[b]));
            if (!open) {
                continue;
            }
            int shown = 0;
            for (int k = pass.nextSetBit(first[b]); k >= 0 && k <= last[b] && shown < limit
                    && rows.size() < TabletSnapshot.MAX_NODES; k = pass.nextSetBit(k + 1)) {
                rows.add(machineRow(order, k, palette, refs));
                keys.add(k);
                shown++;
            }
            if (more > 0 && rows.size() < TabletSnapshot.MAX_NODES) {
                rows.add(new More(block, more));
            }
        }
        builtRows = List.copyOf(rows);
        builtRouters = List.copyOf(refs);
        builtKeys = keys.toIntArray();
        builtTotalRows = totalRows;
        builtMachines = machines;
        builtBlocks = blockCount;
    }

    /**
     * Marca em {@link #pass} as máquinas que passam (roteador marcado e a regra por máquina do {@link TabletQuery}) e
     * conta, por bloco, as máquinas, os roteadores e as acima do teto do índice. Devolve quantas passaram.
     */
    private int countPassing(MachineOrder order, TabletQuery query) {
        pass.clear();
        query.beginMachines();
        int machines = 0;
        int previousBlock = -1;
        int previousRouter = -1;
        for (int k = 0, n = order.size(); k < n; k++) {
            int r = order.routerRank(k);
            if (!marks.get(r)) {
                continue;
            }
            NodeIndex.Entry entry = order.router(k);
            int m = order.machine(k);
            int b = order.blockRank(k);
            ResourceLocation block = order.block(b);
            IndexRemotes remotes = entry.remotes();
            boolean attached = m == MachineOrder.ATTACHED;
            int roles = attached ? entry.attachedRoles() : remotes.machineRoles(m);
            long nets = attached ? entry.attachedNets() : remotes.machineNets(m);
            if (!query.matchesMachine(entry, block, roles, nets, attached ? "" : remotes.label(m))) {
                continue;
            }
            pass.set(k);
            if (count[b]++ == 0) {
                first[b] = k;
            }
            last[b] = k;
            if (b != previousBlock || r != previousRouter) {
                routers[b]++;
                hidden[b] += hiddenIn(remotes, block);
                previousBlock = b;
                previousRouter = r;
            }
            machines++;
        }
        return machines;
    }

    /** Uma máquina da última página: o roteador e o índice dela ({@link MachineOrder#ATTACHED} na presa). */
    interface MachineSink {
        void accept(NodeIndex.Entry router, int machine);
    }

    /**
     * As máquinas do bloco que passaram na consulta da última página montada, até {@code max}: o bloco marcado no
     * Selecionar, que o cliente não conhece inteiro (um bloco recolhido não manda as linhas). Nenhuma fora da visão
     * por máquinas. Se o índice mudou desde a página (vincular ou desvincular troca as remotas no lugar), refaz a
     * passada antes, com a mesma consulta; e pula a máquina cujo índice saiu das remotas ou cujo grupo não é do
     * bloco, para nunca colar noutra nem estourar com um pacote velho. Devolve quantas.
     */
    int forEachInBlock(ResourceLocation block, int max, MachineSink sink) {
        if (!active || builtOrder == null || builtFrom == null) {
            return 0;
        }
        NodeIndex index = NodeIndex.get(server);
        MachineOrder fresh = TabletSample.get(server).machineOrder(index);
        if (fresh != builtOrder || index.version() != builtIndexVersion) {
            remake(fresh, index, builtFrom);
        }
        MachineOrder order = builtOrder;
        for (int b = 0, n = Math.min(order.blockCount(), count.length); b < n; b++) {
            if (count[b] == 0 || !order.block(b).equals(block)) {
                continue;
            }
            int sent = 0;
            for (int k = pass.nextSetBit(first[b]); k >= 0 && k <= last[b] && sent < max; k = pass.nextSetBit(k + 1)) {
                NodeIndex.Entry router = order.router(k);
                int m = order.machine(k);
                if (m != MachineOrder.ATTACHED && !inBlock(router.remotes(), m, block)) {
                    continue;
                }
                sink.accept(router, m);
                sent++;
            }
            return sent;
        }
        return 0;
    }

    /** A máquina {@code m} ainda está nas remotas guardadas e o grupo dela é do bloco. */
    private static boolean inBlock(IndexRemotes remotes, int m, ResourceLocation block) {
        if (m < 0 || m >= remotes.size()) {
            return false;
        }
        int g = remotes.groupIndex(remotes.group(m));
        return g >= 0 && remotes.groupBlock(g).equals(block);
    }

    private int limit(ResourceLocation block, int machines) {
        return showAll.contains(block) ? machines : Math.min(machines, FEW);
    }

    /** As máquinas do bloco acima do teto do índice num roteador; 0 sem nenhuma acima (o caso comum, sem laço). */
    private static int hiddenIn(IndexRemotes remotes, ResourceLocation block) {
        if (remotes.total() <= remotes.size()) {
            return 0;
        }
        int hiddenHere = 0;
        for (int g = 0, groups = remotes.groupCount(); g < groups; g++) {
            if (remotes.groupBlock(g).equals(block)) {
                int stored = 0;
                for (int i = 0, n = remotes.size(); i < n; i++) {
                    if (remotes.group(i) == remotes.groupId(g)) {
                        stored++;
                    }
                }
                hiddenHere += remotes.groupMachines(g) - stored;
            }
        }
        return hiddenHere;
    }

    private static Machine machineRow(MachineOrder order, int k, Object2IntOpenHashMap<NodeKey> palette,
            List<RouterRef> refs) {
        NodeIndex.Entry entry = order.router(k);
        NodeKey key = entry.key();
        int ref = palette.getInt(key);
        if (ref < 0) {
            ref = refs.size();
            palette.put(key, ref);
            refs.add(new RouterRef(key, clip(entry.name())));
        }
        int m = order.machine(k);
        if (m == MachineOrder.ATTACHED) {
            RouterBlockEntity router = entry.router();
            long pos = router != null ? router.machinePos().asLong() : key.pos().asLong();
            return new Machine(ref, true, -1, 0, pos, "", false);
        }
        IndexRemotes remotes = entry.remotes();
        return new Machine(ref, false, m, numberInGroup(remotes, m), remotes.pos(m), clip(remotes.label(m)),
                remotes.hasException(m));
    }

    /**
     * O "#N" da máquina {@code m}: a posição dela entre as do mesmo grupo do roteador, a partir de 1, a mesma conta
     * da árvore e da tela do roteador. Só ao montar a página, para as linhas dela.
     */
    private static int numberInGroup(IndexRemotes remotes, int m) {
        int group = remotes.group(m);
        int number = 0;
        for (int i = 0; i <= m; i++) {
            if (remotes.group(i) == group) {
                number++;
            }
        }
        return number;
    }

    /**
     * O estado e a vazão da máquina {@code k}: a vinculada pelo {@link TabletMachineSample}; a presa pelo estado e a
     * vazão do nó, com a vazão do nó na metade do que ela deu ({@link MachineLive#given}), e o "sem rede" e o
     * "pausado" do nó como a Lista os mostra.
     */
    private static MachineLive live(MachineOrder order, int k, TabletSample shared, NetworkSavedData data, long now) {
        NodeIndex.Entry entry = order.router(k);
        NodeKey key = entry.key();
        RouterBlockEntity router = entry.router();
        int m = order.machine(k);
        if (m != MachineOrder.ATTACHED) {
            return shared.machines().live(router, key, m, entry.remotes().pos(m), now);
        }
        if (router == null) {
            return MachineLive.still(MachineStatus.UNLOADED);
        }
        switch (TabletNodes.status(entry, true, 0, false, data)) {
            case NO_NETWORK -> {
                return MachineLive.still(MachineStatus.NO_NETWORK);
            }
            case PAUSED -> {
                return MachineLive.still(MachineStatus.PAUSED);
            }
            default -> {
            }
        }
        MachineStatus status = shared.active(key) ? MachineStatus.MOVING
                : shared.full(key) ? MachineStatus.FULL : MachineStatus.SLEEPING;
        long[] rates = shared.rates(key);
        if (rates == null) {
            return MachineLive.still(status);
        }
        long[] both = new long[2 * TYPES.length];
        System.arraycopy(rates, 0, both, TYPES.length, TYPES.length);
        return new MachineLive(status, both);
    }

    private static String clip(String text) {
        return text.length() > RouterSnapshot.NAME_LENGTH ? text.substring(0, RouterSnapshot.NAME_LENGTH) : text;
    }

    private void grow(int blocks) {
        if (count.length < blocks) {
            count = new int[blocks];
            routers = new int[blocks];
            hidden = new int[blocks];
            first = new int[blocks];
            last = new int[blocks];
        }
    }

    // ------------------------------------------------------------------ só para os GameTests

    /** Os blocos abertos, recolhidos e com "Mostrar todas" somados. */
    public int blockCountForTests() {
        return opened.size() + closed.size() + showAll.size();
    }

    /** A ordem por bloco compartilhada existe agora. */
    public boolean orderExistsForTests() {
        return TabletSample.get(server).hasMachineOrder();
    }
}
