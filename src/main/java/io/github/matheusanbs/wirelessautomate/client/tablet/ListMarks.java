package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.menu.TabletMarks;
import io.github.matheusanbs.wirelessautomate.menu.TabletMarks.MachineKey;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletTree.GroupKey;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * O que o Selecionar da Lista do Tablet marcou (etapa 3b): roteadores, máquinas uma a uma (a presa inclusive), grupos
 * de alcance inteiros (na árvore) e blocos inteiros (na visão por máquinas). Marcar um grupo ou um bloco vale pelas
 * máquinas dele; desmarcar uma máquina de um grupo ou bloco marcado deixa marcadas as outras que estão à vista. As
 * contagens das caixas pela metade são mantidas a cada clique, para o desenho só ler.
 */
final class ListMarks {
    /** A caixa de uma linha. */
    enum Box { NONE, PART, ALL }

    /** Onde a máquina foi marcada: o grupo dela (na árvore) ou o bloco (na visão por máquinas). */
    private record Where(@Nullable GroupKey group, @Nullable ResourceLocation block) {
    }

    private final Set<NodeKey> nodes = new LinkedHashSet<>();
    private final Set<NodeKey> nodesView = Collections.unmodifiableSet(nodes);
    /** Muda a cada mudança: quem guarda um texto dos marcados sabe quando refazer. */
    private int version;
    private final Map<MachineKey, Where> machines = new LinkedHashMap<>();
    /** Os grupos e blocos marcados, com quantas máquinas cada um tinha ao marcar. */
    private final Object2IntMap<GroupKey> groups = new Object2IntLinkedOpenHashMap<>();
    private final Object2IntMap<ResourceLocation> blocks = new Object2IntLinkedOpenHashMap<>();
    /** Máquinas e grupos marcados por roteador, máquinas marcadas por grupo e por bloco: as caixas pela metade. */
    private final Object2IntMap<NodeKey> perNode = new Object2IntOpenHashMap<>();
    private final Object2IntMap<GroupKey> perGroup = new Object2IntOpenHashMap<>();
    private final Object2IntMap<ResourceLocation> perBlock = new Object2IntOpenHashMap<>();

    int version() {
        return version;
    }

    /** Os roteadores marcados (só para ler). */
    Set<NodeKey> nodes() {
        return nodesView;
    }

    boolean isEmpty() {
        return nodes.isEmpty() && machines.isEmpty() && groups.isEmpty() && blocks.isEmpty();
    }

    void clear() {
        version++;
        nodes.clear();
        machines.clear();
        groups.clear();
        blocks.clear();
        perNode.clear();
        perGroup.clear();
        perBlock.clear();
    }

    // ------------------------------------------------------------------ caixas

    Box node(NodeKey key) {
        return nodes.contains(key) ? Box.ALL : perNode.getInt(key) > 0 ? Box.PART : Box.NONE;
    }

    Box group(GroupKey key) {
        return groups.containsKey(key) ? Box.ALL : perGroup.getInt(key) > 0 ? Box.PART : Box.NONE;
    }

    Box block(ResourceLocation block) {
        return blocks.containsKey(block) ? Box.ALL : perBlock.getInt(block) > 0 ? Box.PART : Box.NONE;
    }

    /** A máquina está marcada: ela mesma, o grupo dela ({@code group}) ou o bloco dela ({@code block}). */
    boolean machine(MachineKey key, @Nullable GroupKey group, @Nullable ResourceLocation block) {
        return machines.containsKey(key) || group != null && groups.containsKey(group)
                || block != null && blocks.containsKey(block);
    }

    // ------------------------------------------------------------------ marcar

    void toggleNode(NodeKey key) {
        version++;
        if (!nodes.remove(key)) {
            nodes.add(key);
        }
    }

    void addNodes(Collection<NodeKey> keys) {
        version++;
        nodes.addAll(keys);
    }

    void removeNodes(Collection<NodeKey> keys) {
        version++;
        keys.forEach(nodes::remove);
    }

    /** Só os roteadores que ainda estão na lista. */
    void retainNodes(Collection<NodeKey> keys) {
        if (nodes.retainAll(keys)) {
            version++;
        }
    }

    /**
     * Marca ou desmarca a máquina {@code key}, do grupo {@code group} ou do bloco {@code block}. Com o grupo ou o
     * bloco marcado, ele sai e as outras máquinas dele à vista ({@code siblings}) ficam marcadas uma a uma.
     */
    void toggleMachine(MachineKey key, @Nullable GroupKey group, @Nullable ResourceLocation block,
            List<MachineKey> siblings) {
        version++;
        if (machines.containsKey(key)) {
            remove(key);
            return;
        }
        boolean split = group != null && groups.containsKey(group) || block != null && blocks.containsKey(block);
        if (split) {
            if (group != null && groups.containsKey(group)) {
                groups.removeInt(group);
                count(perNode, group.node(), -1);
            } else {
                blocks.removeInt(block);
            }
            for (MachineKey sibling : siblings) {
                if (!sibling.equals(key) && !machines.containsKey(sibling)) {
                    add(sibling, new Where(group, block));
                }
            }
            return;
        }
        add(key, new Where(group, block));
    }

    /** Marca ou desmarca o grupo inteiro; as máquinas dele marcadas uma a uma ({@code shown}) saem. */
    void toggleGroup(GroupKey key, int total, List<MachineKey> shown) {
        version++;
        dropAll(shown);
        if (groups.containsKey(key)) {
            groups.removeInt(key);
            count(perNode, key.node(), -1);
        } else {
            groups.put(key, total);
            count(perNode, key.node(), 1);
        }
    }

    /** Marca ou desmarca o bloco inteiro; as máquinas dele marcadas uma a uma ({@code shown}) saem. */
    void toggleBlock(ResourceLocation block, int total, List<MachineKey> shown) {
        version++;
        dropAll(shown);
        if (blocks.containsKey(block)) {
            blocks.removeInt(block);
        } else {
            blocks.put(block, total);
        }
    }

    /** Todos na visão por máquinas: marca os blocos da página; se já estavam todos, desmarca. */
    void toggleBlocks(Map<ResourceLocation, Integer> pageBlocks) {
        version++;
        if (blocks.keySet().containsAll(pageBlocks.keySet())) {
            pageBlocks.keySet().forEach(blocks::removeInt);
            return;
        }
        pageBlocks.forEach((block, total) -> blocks.put(block, total.intValue()));
    }

    private void dropAll(List<MachineKey> keys) {
        for (MachineKey key : keys) {
            if (machines.containsKey(key)) {
                remove(key);
            }
        }
    }

    private void add(MachineKey key, Where where) {
        machines.put(key, where);
        count(perNode, key.node(), 1);
        if (where.group() != null) {
            count(perGroup, where.group(), 1);
        }
        if (where.block() != null) {
            count(perBlock, where.block(), 1);
        }
    }

    private void remove(MachineKey key) {
        Where where = machines.remove(key);
        count(perNode, key.node(), -1);
        if (where.group() != null) {
            count(perGroup, where.group(), -1);
        }
        if (where.block() != null) {
            count(perBlock, where.block(), -1);
        }
    }

    private static <K> void count(Object2IntMap<K> map, K key, int delta) {
        int next = map.getInt(key) + delta;
        if (next <= 0) {
            map.removeInt(key);
        } else {
            map.put(key, next);
        }
    }

    // ------------------------------------------------------------------ o que vai ao servidor

    int routers() {
        return nodes.size();
    }

    /** Quantas máquinas o Colar atinge: as marcadas uma a uma e as dos grupos e blocos quando foram marcados. */
    int machineCount() {
        int count = machines.size();
        for (int total : groups.values()) {
            count += total;
        }
        for (int total : blocks.values()) {
            count += total;
        }
        return count;
    }

    /** Os nós marcados, até {@code max}. */
    List<NodeKey> nodeList(int max) {
        List<NodeKey> list = new ArrayList<>(nodes);
        return List.copyOf(list.size() > max ? list.subList(0, max) : list);
    }

    /** As máquinas, os grupos e os blocos, nos tetos do pacote. */
    TabletMarks payload() {
        List<MachineKey> machineList = new ArrayList<>(machines.keySet());
        List<GroupKey> groupList = new ArrayList<>(groups.keySet());
        List<ResourceLocation> blockList = new ArrayList<>(blocks.keySet());
        return new TabletMarks(cap(machineList, TabletMenu.MAX_MOVE), cap(groupList, TabletMarks.MAX_GROUPS),
                cap(blockList, TabletMarks.MAX_BLOCKS));
    }

    private static <T> List<T> cap(List<T> list, int max) {
        return list.size() > max ? list.subList(0, max) : list;
    }
}
