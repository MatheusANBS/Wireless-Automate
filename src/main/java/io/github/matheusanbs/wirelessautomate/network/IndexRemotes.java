package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * As máquinas vinculadas de um nó do {@link NodeIndex}, compactas e imutáveis: por máquina, a posição, o id do
 * grupo, o rótulo e se tem exceção; por grupo, o id, o bloco, o nome e quantas máquinas tem. A ordem das
 * máquinas é a de vínculo ({@link RemoteMachines#machines()}), a mesma do "#N" da tela.
 *
 * <p>Guarda no máximo {@link #CAP} máquinas, as primeiras; {@link #total()} dá a contagem de verdade e as
 * contagens dos grupos contam todas. Lida do roteador só no aviso de mudança ({@link #matches} antes, sem
 * alocar, para só montar outra quando algo mudou).
 *
 * <p>NBT, só com máquinas ou grupos: {@code remote_pos} ({@code long[]}), {@code remote_group} ({@code int[]}),
 * {@code remote_exception} (os índices das exceções), {@code remote_labels} (índice → rótulo, só os com rótulo),
 * {@code remote_groups} ({@code id}, {@code block}, {@code name}) e, só acima do teto, {@code remote_total} e o
 * {@code count} de cada grupo (abaixo dele, as contagens saem das máquinas).
 */
public final class IndexRemotes {
    /** O teto de máquinas por roteador no índice. */
    public static final int CAP = 512;
    public static final IndexRemotes EMPTY = new IndexRemotes(new long[0], new int[0], new int[0], null, 0,
            new int[0], new ResourceLocation[0], new String[0], new int[0]);

    private final long[] pos;
    private final int[] group;
    /** Os índices das máquinas com exceção, em ordem crescente. */
    private final int[] exceptions;
    /** O rótulo de cada máquina ({@code null} = sem rótulo), ou {@code null} se nenhuma tem. */
    private final String @Nullable [] labels;
    private final int total;
    private final int[] groupIds;
    private final ResourceLocation[] groupBlocks;
    private final String[] groupNames;
    private final int[] groupCounts;

    private IndexRemotes(long[] pos, int[] group, int[] exceptions, String @Nullable [] labels, int total,
            int[] groupIds, ResourceLocation[] groupBlocks, String[] groupNames, int[] groupCounts) {
        this.pos = pos;
        this.group = group;
        this.exceptions = exceptions;
        this.labels = labels;
        this.total = total;
        this.groupIds = groupIds;
        this.groupBlocks = groupBlocks;
        this.groupNames = groupNames;
        this.groupCounts = groupCounts;
    }

    // ------------------------------------------------------------------ leitura

    /** Quantas máquinas estão guardadas (até {@link #CAP}). */
    public int size() {
        return pos.length;
    }

    /** Quantas máquinas o roteador tem, inclusive as acima do teto. */
    public int total() {
        return total;
    }

    public boolean isEmpty() {
        return total == 0 && groupIds.length == 0;
    }

    /** A posição da máquina {@code i} ({@code BlockPos.asLong}). */
    public long pos(int i) {
        return pos[i];
    }

    /** O id do grupo da máquina {@code i}. */
    public int group(int i) {
        return group[i];
    }

    /** Quantas máquinas guardadas têm exceção. */
    public int exceptionCount() {
        return exceptions.length;
    }

    /** O índice da {@code k}-ésima máquina com exceção, em ordem crescente. */
    public int exception(int k) {
        return exceptions[k];
    }

    public boolean hasException(int i) {
        return Arrays.binarySearch(exceptions, i) >= 0;
    }

    /** O rótulo da máquina {@code i}; vazio sem rótulo. */
    public String label(int i) {
        String label = labels == null ? null : labels[i];
        return label == null ? "" : label;
    }

    public int groupCount() {
        return groupIds.length;
    }

    /** O id do grupo na posição {@code g} (ordem de criação). */
    public int groupId(int g) {
        return groupIds[g];
    }

    public ResourceLocation groupBlock(int g) {
        return groupBlocks[g];
    }

    /** O nome do grupo na posição {@code g}; vazio = o nome do bloco. */
    public String groupName(int g) {
        return groupNames[g];
    }

    /** Quantas máquinas o grupo na posição {@code g} tem, contando as acima do teto. */
    public int groupMachines(int g) {
        return groupCounts[g];
    }

    /** A posição do grupo de id {@code id} em {@link #groupId}, ou −1. */
    public int groupIndex(int id) {
        for (int g = 0; g < groupIds.length; g++) {
            if (groupIds[g] == id) {
                return g;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ do roteador

    /** Estas são as máquinas e os grupos de {@code remotes}. Sem alocar. */
    boolean matches(RemoteMachines remotes) {
        if (remotes.size() != total || remotes.groupCount() != groupIds.length) {
            return false;
        }
        int exception = 0;
        for (int i = 0; i < pos.length; i++) {
            RemoteMachine machine = remotes.machine(i);
            if (machine.pos().asLong() != pos[i] || machine.group() != group[i] || !machine.label().equals(label(i))) {
                return false;
            }
            boolean stored = exception < exceptions.length && exceptions[exception] == i;
            if (machine.hasException() != stored) {
                return false;
            }
            if (stored) {
                exception++;
            }
        }
        for (int g = 0; g < groupIds.length; g++) {
            MachineGroup now = remotes.groupAt(g);
            if (now.id() != groupIds[g] || !now.block().equals(groupBlocks[g]) || !now.name().equals(groupNames[g])) {
                return false;
            }
        }
        return total <= CAP || countsMatch(remotes);
    }

    /**
     * Acima do teto, as máquinas de fora das guardadas só aparecem nas contagens dos grupos. Uma contagem por
     * grupo, que para na primeira diferença; só roda acima do teto.
     */
    private boolean countsMatch(RemoteMachines remotes) {
        int machines = remotes.size();
        for (int g = 0; g < groupIds.length; g++) {
            int id = groupIds[g];
            int count = 0;
            for (int i = 0; i < machines; i++) {
                if (remotes.machine(i).group() == id) {
                    count++;
                }
            }
            if (count != groupCounts[g]) {
                return false;
            }
        }
        return true;
    }

    static IndexRemotes of(RemoteMachines remotes) {
        int machines = remotes.size();
        int groups = remotes.groupCount();
        if (machines == 0 && groups == 0) {
            return EMPTY;
        }
        int size = Math.min(machines, CAP);
        long[] pos = new long[size];
        int[] group = new int[size];
        int[] exceptions = new int[size];
        int exceptionCount = 0;
        String[] labels = null;
        for (int i = 0; i < size; i++) {
            RemoteMachine machine = remotes.machine(i);
            pos[i] = machine.pos().asLong();
            group[i] = machine.group();
            if (machine.hasException()) {
                exceptions[exceptionCount++] = i;
            }
            if (!machine.label().isEmpty()) {
                if (labels == null) {
                    labels = new String[size];
                }
                labels[i] = machine.label();
            }
        }
        int[] groupIds = new int[groups];
        ResourceLocation[] groupBlocks = new ResourceLocation[groups];
        String[] groupNames = new String[groups];
        for (int g = 0; g < groups; g++) {
            MachineGroup now = remotes.groupAt(g);
            groupIds[g] = now.id();
            groupBlocks[g] = now.block();
            groupNames[g] = now.name();
        }
        int[] groupCounts = new int[groupIds.length];
        for (int i = 0; i < machines; i++) {
            int g = indexOf(groupIds, remotes.machine(i).group());
            if (g >= 0) {
                groupCounts[g]++;
            }
        }
        return new IndexRemotes(pos, group, Arrays.copyOf(exceptions, exceptionCount), labels, machines,
                groupIds, groupBlocks, groupNames, groupCounts);
    }

    private static int indexOf(int[] ids, int id) {
        for (int g = 0; g < ids.length; g++) {
            if (ids[g] == id) {
                return g;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ NBT

    void save(CompoundTag tag) {
        if (isEmpty()) {
            return;
        }
        // As tags de array guardam os arrays deste objeto, sem cópia: a tag fica no cache da entrada
        // (Entry.saved) e ninguém a altera, como os arrays daqui, que não mudam depois de prontos.
        tag.put("remote_pos", new LongArrayTag(pos));
        tag.put("remote_group", new IntArrayTag(group));
        if (exceptions.length > 0) {
            tag.put("remote_exception", new IntArrayTag(exceptions));
        }
        if (labels != null) {
            CompoundTag labelsTag = new CompoundTag();
            for (int i = 0; i < labels.length; i++) {
                if (labels[i] != null) {
                    labelsTag.putString(Integer.toString(i), labels[i]);
                }
            }
            tag.put("remote_labels", labelsTag);
        }
        boolean capped = total > pos.length;
        ListTag groupsTag = new ListTag();
        for (int g = 0; g < groupIds.length; g++) {
            CompoundTag groupTag = new CompoundTag();
            groupTag.putInt("id", groupIds[g]);
            groupTag.putString("block", groupBlocks[g].toString());
            if (!groupNames[g].isEmpty()) {
                groupTag.putString("name", groupNames[g]);
            }
            if (capped) {
                groupTag.putInt("count", groupCounts[g]);
            }
            groupsTag.add(groupTag);
        }
        tag.put("remote_groups", groupsTag);
        if (capped) {
            tag.putInt("remote_total", total);
        }
    }

    /** Lê o formato de {@link #save}; sem as chaves (o formato antigo), sem máquinas. */
    static IndexRemotes load(CompoundTag tag) {
        if (!tag.contains("remote_groups", Tag.TAG_LIST)) {
            return EMPTY;
        }
        long[] pos = tag.getLongArray("remote_pos");
        int[] readGroup = tag.getIntArray("remote_group");
        int size = Math.min(Math.min(pos.length, readGroup.length), CAP);
        pos = Arrays.copyOf(pos, size);
        int[] group = Arrays.copyOf(readGroup, size);
        int[] exceptions = Arrays.stream(tag.getIntArray("remote_exception"))
                .filter(i -> i >= 0 && i < size).sorted().distinct().toArray();
        String[] labels = null;
        CompoundTag labelsTag = tag.getCompound("remote_labels");
        for (String key : labelsTag.getAllKeys()) {
            int i;
            try {
                i = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            String label = RenameRouterPayload.clean(labelsTag.getString(key));
            if (i >= 0 && i < size && !label.isEmpty()) {
                if (labels == null) {
                    labels = new String[size];
                }
                labels[i] = label;
            }
        }
        int total = Math.max(size, tag.getInt("remote_total"));
        boolean capped = total > size;
        ListTag groupsTag = tag.getList("remote_groups", Tag.TAG_COMPOUND);
        int[] groupIds = new int[groupsTag.size()];
        ResourceLocation[] groupBlocks = new ResourceLocation[groupsTag.size()];
        String[] groupNames = new String[groupsTag.size()];
        int[] groupCounts = new int[groupsTag.size()];
        int groups = 0;
        for (int g = 0; g < groupsTag.size(); g++) {
            CompoundTag groupTag = groupsTag.getCompound(g);
            ResourceLocation block = ResourceLocation.tryParse(groupTag.getString("block"));
            if (block == null) {
                continue;
            }
            groupIds[groups] = groupTag.getInt("id");
            groupBlocks[groups] = block;
            groupNames[groups] = RenameRouterPayload.clean(groupTag.getString("name"));
            groupCounts[groups] = capped ? groupTag.getInt("count") : 0;
            groups++;
        }
        groupIds = Arrays.copyOf(groupIds, groups);
        groupBlocks = Arrays.copyOf(groupBlocks, groups);
        groupNames = Arrays.copyOf(groupNames, groups);
        groupCounts = Arrays.copyOf(groupCounts, groups);
        if (!capped) {
            for (int id : group) {
                int g = indexOf(groupIds, id);
                if (g >= 0) {
                    groupCounts[g]++;
                }
            }
        }
        return new IndexRemotes(pos, group, exceptions, labels, total, groupIds, groupBlocks, groupNames, groupCounts);
    }

    // ------------------------------------------------------------------ só para os GameTests

    /** {@link #of} para os GameTests. */
    public static IndexRemotes ofForTests(RemoteMachines remotes) {
        return of(remotes);
    }

    /** {@link #matches} para os GameTests. */
    public boolean matchesForTests(RemoteMachines remotes) {
        return matches(remotes);
    }

    /** {@link #save} numa tag nova e {@link #load} de volta, para os GameTests. */
    public static IndexRemotes roundTripForTests(IndexRemotes remotes, CompoundTag into) {
        remotes.save(into);
        return load(into);
    }

    /** {@link #load} para os GameTests. */
    public static IndexRemotes loadForTests(CompoundTag tag) {
        return load(tag);
    }
}
