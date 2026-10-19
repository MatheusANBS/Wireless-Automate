package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.Attached;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.Group;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.Machine;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.More;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.Row;
import io.github.matheusanbs.wirelessautomate.network.IndexRemotes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.TabletTreePayload;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A árvore da Lista de um Tablet aberto (servidor): os roteadores abertos, os grupos recolhidos e os grupos com
 * "Mostrar todas", mudados pelo {@link TabletTreePayload} e lidos a cada montagem da página. A presa e os grupos de
 * um roteador aberto começam abertos. As linhas de um nó aberto saem do índice de nós ({@link IndexRemotes}), na
 * ordem do {@link TreeOrder}, e valem com o roteador descarregado.
 *
 * <p>Teto: {@link #MAX_OPEN} roteadores abertos e {@link #MAX_GROUPS} grupos em cada conjunto; acima disso, a
 * ação é recusada. Recolher e "Mostrar todas" só valem num roteador aberto; fechar um roteador esquece os grupos
 * dele, e um nó que saiu do índice ou deixou de ser visível sai dos abertos, com os grupos, na montagem seguinte.
 */
public final class TabletTree {
    public static final int MAX_OPEN = 64;
    public static final int MAX_GROUPS = 256;
    private static final ResourceLocation AIR = ResourceLocation.withDefaultNamespace("air");

    /** Um grupo de um roteador. */
    public record GroupKey(NodeKey node, int group) {
    }

    private final ServerPlayer viewer;
    private final Set<NodeKey> open = new ObjectLinkedOpenHashSet<>();
    private final Set<GroupKey> collapsed = new ObjectOpenHashSet<>();
    private final Set<GroupKey> showAll = new ObjectOpenHashSet<>();
    /** As linhas do nó que está sendo montado, reaproveitado. */
    private final Emitter emitter = new Emitter();

    TabletTree(ServerPlayer viewer) {
        this.viewer = viewer;
    }

    /**
     * Uma ação da tela, validada: abrir, recolher e "Mostrar todas" pedem um nó que o jogador vê (e, nos grupos, um
     * nó aberto e um grupo que o índice tem) e espaço no conjunto; fechar, expandir e "Mostrar menos" só tiram.
     * Devolve se valeu.
     */
    boolean apply(TabletTreePayload.Action action, NodeKey node, int group) {
        GroupKey groupKey = new GroupKey(node, group);
        return switch (action) {
            case OPEN -> open.contains(node) || visible(node) != null && open.size() < MAX_OPEN && open.add(node);
            case CLOSE -> {
                open.remove(node);
                forgetGroups(node);
                yield true;
            }
            case COLLAPSE_GROUP -> addGroup(collapsed, groupKey);
            case EXPAND_GROUP -> {
                collapsed.remove(groupKey);
                yield true;
            }
            case SHOW_ALL -> addGroup(showAll, groupKey);
            case SHOW_FEW -> {
                showAll.remove(groupKey);
                yield true;
            }
        };
    }

    private boolean addGroup(Set<GroupKey> set, GroupKey key) {
        if (set.contains(key)) {
            return true;
        }
        if (!open.contains(key.node())) {
            return false;
        }
        NodeIndex.Entry entry = visible(key.node());
        return entry != null && entry.remotes().groupIndex(key.group()) >= 0 && set.size() < MAX_GROUPS && set.add(key);
    }

    /** A entrada do nó, se o jogador o vê; senão {@code null}. */
    private NodeIndex.@Nullable Entry visible(NodeKey node) {
        MinecraftServer server = viewer.server;
        NodeIndex.Entry entry = NodeIndex.get(server).entry(node);
        return entry != null && TabletNodes.visible(entry, NetworkSavedData.get(server), viewer.hasPermissions(2),
                viewer.getUUID()) ? entry : null;
    }

    private void forgetGroups(NodeKey node) {
        collapsed.removeIf(key -> key.node().equals(node));
        showAll.removeIf(key -> key.node().equals(node));
    }

    boolean isOpen(NodeKey node) {
        return open.contains(node);
    }

    /**
     * Tira dos abertos os nós que saíram do índice ou que o jogador deixou de ver, e dos grupos os de nó que não está
     * aberto. No máximo {@link #MAX_OPEN} buscas e, só quando algum nó saiu, uma passada pelos grupos.
     */
    void prune(NodeIndex index, NetworkSavedData data, boolean operator, UUID me) {
        boolean removed = false;
        Iterator<NodeKey> keys = open.iterator();
        while (keys.hasNext()) {
            NodeKey key = keys.next();
            NodeIndex.Entry entry = index.entry(key);
            if (entry == null || !TabletNodes.visible(entry, data, operator, me)) {
                keys.remove();
                removed = true;
            }
        }
        // Um grupo só entra com o nó aberto e o fechar o esquece: só sobra grupo de nó fechado quando um nó sai aqui.
        if (removed) {
            collapsed.removeIf(key -> !open.contains(key.node()));
            showAll.removeIf(key -> !open.contains(key.node()));
        }
    }

    /**
     * Põe em {@code rows} as linhas do nó aberto {@code entry}, que está na posição {@code parent} da página, no
     * máximo {@code budget}, e em {@code lives} o estado e a vazão de cada linha de máquina, da amostra
     * compartilhada. Devolve quantas linhas pôs.
     */
    int appendRows(NodeIndex.Entry entry, int parent, int budget, List<Row> rows, List<MachineLive> lives,
            TabletMachineSample sample, long now) {
        NodeKey key = entry.key();
        emitter.start(entry, parent, rows, lives, sample, now);
        try {
            return TreeOrder.plan(!entry.machine().equals(AIR), emitter,
                    id -> collapsed.contains(new GroupKey(key, id)), id -> showAll.contains(new GroupKey(key, id)),
                    budget, emitter);
        } finally {
            emitter.clear();
        }
    }

    // ------------------------------------------------------------------ só para os GameTests

    /** Abre {@code node} sem validar, para encher o conjunto. */
    public void openForTests(NodeKey node) {
        open.add(node);
    }

    /** Recolhe o grupo sem validar, para encher o conjunto. */
    public void collapseForTests(GroupKey key) {
        collapsed.add(key);
    }

    /** Quantos roteadores estão abertos e quantos grupos estão recolhidos ou com "Mostrar todas". */
    public int openCountForTests() {
        return open.size();
    }

    public int groupCountForTests() {
        return collapsed.size() + showAll.size();
    }

    /** As máquinas e os grupos do índice para o {@link TreeOrder}, e as linhas que ele manda montadas na página. */
    private final class Emitter implements TreeOrder.Machines, TreeOrder.Rows {
        private @Nullable NodeIndex.Entry entry;
        private IndexRemotes remotes = IndexRemotes.EMPTY;
        private @Nullable RouterBlockEntity router;
        private int parent;
        private @Nullable List<Row> rows;
        private @Nullable List<MachineLive> lives;
        private @Nullable TabletMachineSample sample;
        private long now;
        /** As exceções de cada grupo, por posição do grupo no índice, contadas no {@link #start}. */
        private int[] exceptions = new int[0];

        void start(NodeIndex.Entry entry, int parent, List<Row> rows, List<MachineLive> lives, TabletMachineSample sample,
                long now) {
            this.entry = entry;
            this.remotes = entry.remotes();
            this.router = entry.router();
            this.parent = parent;
            this.rows = rows;
            this.lives = lives;
            this.sample = sample;
            this.now = now;
            int groups = remotes.groupCount();
            if (exceptions.length < groups) {
                exceptions = new int[groups];
            } else {
                Arrays.fill(exceptions, 0, groups, 0);
            }
            for (int k = 0, n = remotes.exceptionCount(); k < n; k++) {
                int g = remotes.groupIndex(remotes.group(remotes.exception(k)));
                if (g >= 0) {
                    exceptions[g]++;
                }
            }
        }

        void clear() {
            entry = null;
            remotes = IndexRemotes.EMPTY;
            router = null;
            rows = null;
            lives = null;
            sample = null;
        }

        @Override
        public int size() {
            return remotes.size();
        }

        @Override
        public int machineGroup(int i) {
            return remotes.group(i);
        }

        @Override
        public int groupCount() {
            return remotes.groupCount();
        }

        @Override
        public int groupId(int g) {
            return remotes.groupId(g);
        }

        @Override
        public int groupTotal(int g) {
            return remotes.groupMachines(g);
        }

        @Override
        public void attached() {
            rows.add(new Attached(parent, router == null ? Optional.empty() : Optional.of(router.facing().getOpposite())));
        }

        @Override
        public void group(int g) {
            int groupId = remotes.groupId(g);
            GroupKey key = new GroupKey(entry.key(), groupId);
            rows.add(new Group(parent, groupId, remotes.groupBlock(g), clip(remotes.groupName(g)), remotes.groupMachines(g),
                    exceptions[g], collapsed.contains(key), showAll.contains(key)));
        }

        @Override
        public void machine(int i) {
            long pos = remotes.pos(i);
            rows.add(new Machine(parent, i, pos, clip(remotes.label(i)), remotes.hasException(i)));
            lives.add(sample.live(router, entry.key(), i, pos, now));
        }

        @Override
        public void more(int g, int hidden) {
            rows.add(new More(parent, remotes.groupId(g), hidden));
        }

        private static String clip(String text) {
            return text.length() > RouterSnapshot.NAME_LENGTH ? text.substring(0, RouterSnapshot.NAME_LENGTH) : text;
        }
    }
}
