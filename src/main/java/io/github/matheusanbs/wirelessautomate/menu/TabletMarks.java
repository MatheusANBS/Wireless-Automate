package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.menu.TabletTree.GroupKey;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * O que o Selecionar do Tablet marcou além dos roteadores (etapa 3b), para o Colar nos marcados e o Mover: máquinas
 * uma a uma, grupos de alcance inteiros e blocos inteiros da visão por máquinas. O bloco vai como bloco e o servidor
 * reavalia a consulta do jogador, porque o cliente não conhece as máquinas de um bloco recolhido.
 *
 * @param machines no máximo {@link TabletMenu#MAX_MOVE}
 * @param groups   no máximo {@link #MAX_GROUPS}
 * @param blocks   no máximo {@link #MAX_BLOCKS}
 */
public record TabletMarks(List<MachineKey> machines, List<GroupKey> groups, List<ResourceLocation> blocks) {
    public static final int MAX_GROUPS = 256;
    public static final int MAX_BLOCKS = 256;
    public static final TabletMarks EMPTY = new TabletMarks(List.of(), List.of(), List.of());

    public TabletMarks {
        machines = List.copyOf(machines);
        groups = List.copyOf(groups);
        blocks = List.copyOf(blocks);
    }

    /**
     * Uma máquina marcada: o roteador e a posição dela ({@code BlockPos.asLong}); {@link #ATTACHED} é a presa do
     * roteador, que o cliente nem sempre conhece pela posição.
     */
    public record MachineKey(NodeKey node, long pos) {
        /** A presa: uma posição fora do mundo (x = −2²⁵). */
        public static final long ATTACHED = Long.MIN_VALUE;

        public static MachineKey attached(NodeKey node) {
            return new MachineKey(node, ATTACHED);
        }

        public boolean isAttached() {
            return pos == ATTACHED;
        }
    }

    public boolean isEmpty() {
        return machines.isEmpty() && groups.isEmpty() && blocks.isEmpty();
    }

    /**
     * As máquinas vão juntas por roteador (a chave uma vez e as posições, ~8 B por máquina); os grupos com a chave e
     * o id; os blocos pelo id. Os tetos são conferidos na leitura.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletMarks> STREAM_CODEC = StreamCodec.of(
            TabletMarks::encode, TabletMarks::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TabletMarks marks) {
        Map<NodeKey, LongArrayList> byNode = new LinkedHashMap<>();
        for (MachineKey machine : marks.machines) {
            byNode.computeIfAbsent(machine.node, node -> new LongArrayList()).add(machine.pos);
        }
        buf.writeVarInt(byNode.size());
        for (Map.Entry<NodeKey, LongArrayList> entry : byNode.entrySet()) {
            NodeKey.STREAM_CODEC.encode(buf, entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (int i = 0; i < entry.getValue().size(); i++) {
                buf.writeLong(entry.getValue().getLong(i));
            }
        }
        buf.writeVarInt(marks.groups.size());
        for (GroupKey group : marks.groups) {
            NodeKey.STREAM_CODEC.encode(buf, group.node());
            buf.writeVarInt(group.group());
        }
        buf.writeVarInt(marks.blocks.size());
        for (ResourceLocation block : marks.blocks) {
            ResourceLocation.STREAM_CODEC.encode(buf, block);
        }
    }

    private static TabletMarks decode(RegistryFriendlyByteBuf buf) {
        int nodes = count(buf, TabletMenu.MAX_MOVE);
        List<MachineKey> machines = new ArrayList<>();
        for (int i = 0; i < nodes; i++) {
            NodeKey node = NodeKey.STREAM_CODEC.decode(buf);
            int n = count(buf, TabletMenu.MAX_MOVE - machines.size());
            for (int m = 0; m < n; m++) {
                machines.add(new MachineKey(node, buf.readLong()));
            }
        }
        int groupCount = count(buf, MAX_GROUPS);
        List<GroupKey> groups = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            groups.add(new GroupKey(NodeKey.STREAM_CODEC.decode(buf), buf.readVarInt()));
        }
        int blockCount = count(buf, MAX_BLOCKS);
        List<ResourceLocation> blocks = new ArrayList<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            blocks.add(ResourceLocation.STREAM_CODEC.decode(buf));
        }
        return new TabletMarks(machines, groups, blocks);
    }

    private static int count(RegistryFriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) {
            throw new DecoderException("marcados: " + n + " de no máximo " + max);
        }
        return n;
    }
}
