package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.network.IndexRemotes;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Arrays;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Todas as máquinas do índice de nós na ordem da visão por máquinas do Tablet (D6 da etapa 3b): por bloco (pelo id),
 * depois pelo roteador (a ordem da Lista, {@link TabletSample#sorted}), depois a presa e as vinculadas por #N. Uma
 * só para todos os Tablets, dona do {@link TabletSample}, que só a monta enquanto alguém está em Máquinas e a refaz só
 * quando o índice muda.
 *
 * <p>Sem objeto por máquina: cada uma é um {@code long} (o bloco, o roteador e o índice da máquina mais um, 0 na
 * presa), ordenado de uma vez. Guarda as vinculadas que o índice guarda ({@link IndexRemotes#CAP} por roteador) e
 * a presa de cada roteador; ~8 B por máquina.
 */
final class MachineOrder {
    /** O índice de máquina da presa. */
    static final int ATTACHED = -1;
    private static final ResourceLocation AIR = ResourceLocation.withDefaultNamespace("air");
    private static final int MACHINE_BITS = 10;
    private static final int ROUTER_BITS = 24;
    private static final long MACHINE_MASK = (1L << MACHINE_BITS) - 1;
    private static final long ROUTER_MASK = (1L << ROUTER_BITS) - 1;

    static {
        if (IndexRemotes.CAP + 1 > MACHINE_MASK) {
            throw new IllegalStateException("O teto de máquinas do índice não cabe na ordem por bloco");
        }
    }

    private final long[] keys;
    private final NodeIndex.Entry[] routers;
    /** Os blocos na ordem dos ids; a posição é o que vai na chave. */
    private final ResourceLocation[] blocks;
    /** A {@link NodeIndex#dataVersion()} lida na montagem. */
    final int version;

    private MachineOrder(long[] keys, NodeIndex.Entry[] routers, ResourceLocation[] blocks, int version) {
        this.keys = keys;
        this.routers = routers;
        this.blocks = blocks;
        this.version = version;
    }

    /** Monta a ordem de {@code sorted} (os nós na ordem da Lista). O(máquinas · log), só quando o índice muda. */
    static MachineOrder build(List<NodeIndex.Entry> sorted, int version) {
        int count = Math.min(sorted.size(), (int) ROUTER_MASK);
        NodeIndex.Entry[] routers = sorted.subList(0, count).toArray(NodeIndex.Entry[]::new);
        Object2IntOpenHashMap<ResourceLocation> ranks = new Object2IntOpenHashMap<>();
        int machines = 0;
        for (NodeIndex.Entry entry : routers) {
            ranks.putIfAbsent(entry.machine(), 0);
            IndexRemotes remotes = entry.remotes();
            for (int g = 0, n = remotes.groupCount(); g < n; g++) {
                ranks.putIfAbsent(remotes.groupBlock(g), 0);
            }
            machines += 1 + remotes.size();
        }
        ranks.putIfAbsent(AIR, 0);
        ResourceLocation[] blocks = ranks.keySet().toArray(ResourceLocation[]::new);
        Arrays.sort(blocks);
        for (int b = 0; b < blocks.length; b++) {
            ranks.put(blocks[b], b);
        }
        long[] keys = new long[machines];
        int k = 0;
        for (int r = 0; r < routers.length; r++) {
            NodeIndex.Entry entry = routers[r];
            keys[k++] = key(ranks.getInt(entry.machine()), r, ATTACHED);
            IndexRemotes remotes = entry.remotes();
            for (int i = 0, n = remotes.size(); i < n; i++) {
                int g = remotes.groupIndex(remotes.group(i));
                keys[k++] = key(ranks.getInt(g < 0 ? AIR : remotes.groupBlock(g)), r, i);
            }
        }
        Arrays.sort(keys);
        return new MachineOrder(keys, routers, blocks, version);
    }

    private static long key(int block, int router, int machine) {
        return ((long) block << (ROUTER_BITS + MACHINE_BITS)) | ((long) router << MACHINE_BITS) | (machine + 1);
    }

    /** Quantas máquinas. */
    int size() {
        return keys.length;
    }

    /** Quantos blocos na paleta (as posições de {@link #blockRank}). */
    int blockCount() {
        return blocks.length;
    }

    /** A posição do bloco da máquina {@code k} na paleta; a ordem dos blocos é a das posições. */
    int blockRank(int k) {
        return (int) (keys[k] >>> (ROUTER_BITS + MACHINE_BITS));
    }

    ResourceLocation block(int rank) {
        return blocks[rank];
    }

    /** A posição do roteador da máquina {@code k} na ordem da Lista. */
    int routerRank(int k) {
        return (int) ((keys[k] >>> MACHINE_BITS) & ROUTER_MASK);
    }

    NodeIndex.Entry router(int k) {
        return routers[routerRank(k)];
    }

    /** O índice da máquina {@code k} no roteador, ou {@link #ATTACHED} na presa. */
    int machine(int k) {
        return (int) (keys[k] & MACHINE_MASK) - 1;
    }

    /** Quantos roteadores (as posições de {@link #routerRank}). */
    int routerCount() {
        return routers.length;
    }
}
