package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * As máquinas remotas de um roteador e os grupos delas. A ordem das máquinas é a ordem de vínculo e
 * decide quem passa do limite do tier: as primeiras {@code maxMachines} ficam ativas.
 *
 * <p>Só o modelo: quem salva e avisa o motor é o {@code RouterBlockEntity}. As regras de raio e de
 * limite ficam fora daqui (o Vinculador as aplica antes de vincular).
 *
 * <p><b>Vincule e desvincule só pelos métodos do {@code RouterBlockEntity}</b> ({@code linkMachine},
 * {@code unlinkMachine}): {@link #link}, {@link #unlink} e {@link #load} são públicos só porque o
 * roteador está noutro pacote. Chamados direto, deixam velhos o índice do mixin ({@link RemoteIndex}),
 * os caches de capability e as rotas.
 */
public final class RemoteMachines {
    private final List<RemoteMachine> machines = new ArrayList<>();
    private final List<MachineGroup> groups = new ArrayList<>();
    private int nextGroupId;
    /** A máquina presa como membro de um grupo; {@code null} até a primeira escrita nela. */
    private @Nullable AttachedMember attached;

    /**
     * Frente horizontal do bloco ({@code HORIZONTAL_FACING} ou {@code FACING} horizontal) como índice
     * de {@link Direction#ordinal()}, ou −1 se o bloco não tem frente horizontal.
     */
    public static int frontOf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).ordinal();
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            return facing.getAxis().isHorizontal() ? facing.ordinal() : -1;
        }
        return -1;
    }

    /**
     * Vincula a máquina em {@code pos}: entra no primeiro grupo do bloco {@code block}, que não muda (ela segue o
     * perfil), ou num grupo novo com o perfil vazio e as seis faces de todos os tipos carregados na rede
     * {@code network} ({@code null} = sem rede, o que o Vinculador sempre passa). Devolve {@code false} se a
     * posição já estava vinculada. Só pelo {@code RouterBlockEntity.linkMachine} (ver a classe).
     */
    public boolean link(BlockPos pos, ResourceLocation block, int front, @Nullable UUID network) {
        if (contains(pos)) {
            return false;
        }
        MachineGroup group = firstGroupOf(block);
        if (group == null) {
            MachineConfig profile = new MachineConfig();
            if (network != null) {
                for (ResourceType type : LoadedTypes.LIST) {
                    for (RemoteSide side : RemoteSide.values()) {
                        profile.face(type, side).setNetwork(network);
                    }
                }
            }
            group = newGroup(block, profile);
        }
        machines.add(new RemoteMachine(pos, group.id(), null, front));
        return true;
    }

    /** Cria um grupo do bloco {@code block}, sem nome e sem cartões, com o perfil {@code profile} (guardado assim). */
    public MachineGroup newGroup(ResourceLocation block, MachineConfig profile) {
        MachineGroup group = new MachineGroup(nextGroupId++, block, "", profile);
        groups.add(group);
        return group;
    }

    // ------------------------------------------------------------------ a presa

    /** O membro presa, ou {@code null} se ninguém configurou a máquina presa ainda. */
    public @Nullable AttachedMember attached() {
        return attached;
    }

    /** O grupo da presa, ou {@code null} sem membro. */
    public @Nullable MachineGroup attachedGroup() {
        return attached == null ? null : group(attached.group());
    }

    /** A configuração que vale para a presa (a exceção, ou o perfil do grupo), ou {@code null} sem membro. */
    public @Nullable MachineConfig effectiveAttached() {
        if (attached == null) {
            return null;
        }
        MachineConfig exception = attached.exception();
        if (exception != null) {
            return exception;
        }
        MachineGroup group = group(attached.group());
        return group == null ? null : group.profile();
    }

    /** O grupo {@code groupId} tem a presa. */
    public boolean groupHasAttached(int groupId) {
        return attached != null && attached.group() == groupId;
    }

    /** Quantas máquinas o grupo tem: as remotas e a presa. */
    public int memberCount(int groupId) {
        int count = groupHasAttached(groupId) ? 1 : 0;
        for (RemoteMachine machine : machines) {
            if (machine.group() == groupId) {
                count++;
            }
        }
        return count;
    }

    /**
     * Põe a presa no bloco {@code block} com a frente {@code front}. Se ela já está num grupo desse bloco, só a
     * frente muda e devolve {@code false}. Senão entra no primeiro grupo do bloco, ou num grupo novo com o perfil
     * vazio, sem exceção (uma exceção só vale para o bloco em que foi feita), e devolve {@code true}; o grupo que
     * ela deixou segue a regra de {@link #attachTo}.
     */
    public boolean attach(ResourceLocation block, int front) {
        MachineGroup now = attachedGroup();
        if (now != null && now.block().equals(block)) {
            attached.setFront(front);
            return false;
        }
        MachineGroup group = firstGroupOf(block);
        if (group == null) {
            group = newGroup(block, new MachineConfig());
        }
        attachTo(group.id(), null, front);
        return true;
    }

    /**
     * Põe a presa no grupo {@code groupId}, com a exceção e a frente dadas. O grupo que ela deixa fica, mesmo
     * sem máquina, a não ser que esteja vazio de verdade (perfil no padrão e sem cartões): aí ele sai.
     */
    public void attachTo(int groupId, @Nullable MachineConfig exception, int front) {
        int before = attached == null ? -1 : attached.group();
        if (attached == null) {
            attached = new AttachedMember(groupId, exception, front);
        } else {
            attached.setGroup(groupId);
            attached.setException(exception);
            attached.setFront(front);
        }
        if (before >= 0 && before != groupId) {
            MachineGroup left = group(before);
            if (left != null && !hasMachines(before) && !left.hasCards() && left.profile().isDefault()) {
                groups.remove(left);
            }
        }
    }

    /**
     * O resultado de {@link #regroup}: os cartões dos grupos que esvaziaram (para soltar no mundo) e o grupo
     * de destino de cada máquina movida, por índice, na ordem do lote.
     */
    public record Regroup(List<ItemStack> cards, Map<Integer, Integer> destinations) {
        public static final Regroup EMPTY = new Regroup(List.of(), Map.of());

        public boolean isEmpty() {
            return destinations.isEmpty();
        }
    }

    /**
     * Passa cada máquina de {@code moves} (índice → id do bloco novo) para o grupo do bloco novo, na ordem:
     * <ol>
     * <li>um grupo com todas as máquinas no lote, todas para o mesmo bloco, sem grupo desse bloco, passa a
     * ser do bloco novo (mesmo id, nome, perfil e cartões);</li>
     * <li>as outras entram no primeiro grupo do bloco novo, ou num grupo novo criado uma vez por bloco, com
     * uma cópia do perfil do grupo antigo, sem nome e sem cartões;</li>
     * <li>os grupos que esvaziaram saem, e os cartões deles voltam no resultado.</li>
     * </ol>
     * A exceção de cada máquina continua. Índice inválido ou bloco igual ao do grupo é ignorado. Quem
     * confere que a máquina está mesmo trocada é o roteador: só pelo {@code RouterBlockEntity.updateReplaced}
     * (ver a classe).
     */
    public Regroup regroup(Map<Integer, ResourceLocation> moves) {
        Map<Integer, ResourceLocation> pending = new LinkedHashMap<>();
        for (Map.Entry<Integer, ResourceLocation> move : moves.entrySet()) {
            RemoteMachine machine = machine(move.getKey());
            if (machine != null && !groupOf(machine).block().equals(move.getValue())) {
                pending.put(move.getKey(), move.getValue());
            }
        }
        if (pending.isEmpty()) {
            return Regroup.EMPTY;
        }
        Set<Integer> before = new HashSet<>();
        for (RemoteMachine machine : machines) {
            before.add(machine.group());
        }
        Map<Integer, Integer> destinations = new LinkedHashMap<>();
        for (MachineGroup group : groups) {
            ResourceLocation block = wholeGroupMove(group, pending);
            if (block != null && firstGroupOf(block) == null) {
                group.setBlock(block);
                for (int i = 0; i < machines.size(); i++) {
                    if (machines.get(i).group() == group.id()) {
                        destinations.put(i, group.id());
                    }
                }
            }
        }
        for (Map.Entry<Integer, ResourceLocation> move : pending.entrySet()) {
            if (destinations.containsKey(move.getKey())) {
                continue;
            }
            RemoteMachine machine = machines.get(move.getKey());
            MachineGroup target = firstGroupOf(move.getValue());
            if (target == null) {
                target = new MachineGroup(nextGroupId++, move.getValue(), "", groupOf(machine).profile().copy());
                groups.add(target);
            }
            machine.setGroup(target.id());
            destinations.put(move.getKey(), target.id());
        }
        List<ItemStack> cards = new ArrayList<>();
        for (MachineGroup group : List.copyOf(groups)) {
            if (before.contains(group.id()) && !hasMachines(group.id()) && !groupHasAttached(group.id())) {
                groups.remove(group);
                cards.addAll(group.removeAllCards());
            }
        }
        Map<Integer, Integer> ordered = new LinkedHashMap<>();
        for (Integer index : pending.keySet()) {
            ordered.put(index, destinations.get(index));
        }
        return new Regroup(List.copyOf(cards), Collections.unmodifiableMap(ordered));
    }

    /** O bloco novo se todas as máquinas de {@code group} estão em {@code pending} e vão para ele; senão {@code null}. */
    private @Nullable ResourceLocation wholeGroupMove(MachineGroup group, Map<Integer, ResourceLocation> pending) {
        if (groupHasAttached(group.id())) {
            return null;
        }
        ResourceLocation block = null;
        for (int i = 0; i < machines.size(); i++) {
            if (machines.get(i).group() != group.id()) {
                continue;
            }
            ResourceLocation to = pending.get(i);
            if (to == null || (block != null && !block.equals(to))) {
                return null;
            }
            block = to;
        }
        return block;
    }

    private boolean hasMachines(int groupId) {
        for (RemoteMachine machine : machines) {
            if (machine.group() == groupId) {
                return true;
            }
        }
        return false;
    }

    /** O primeiro grupo do bloco {@code block}, na ordem de criação, ou {@code null}. */
    public @Nullable MachineGroup firstGroupOf(ResourceLocation block) {
        for (MachineGroup group : groups) {
            if (group.block().equals(block)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Desvincula a máquina em {@code pos}. Se o grupo dela esvazia (e não tem a presa), ele sai e os cartões dele
     * voltam na lista, para quem chamou soltar no mundo. Sem a máquina, devolve uma lista vazia. Só pelo
     * {@code RouterBlockEntity.unlinkMachine} (ver a classe).
     */
    public List<ItemStack> unlink(BlockPos pos) {
        return unlink(index(pos));
    }

    /** Como {@link #unlink(BlockPos)}, pelo índice na lista; índice inválido devolve uma lista vazia. */
    public List<ItemStack> unlink(int index) {
        if (index < 0 || index >= machines.size()) {
            return List.of();
        }
        RemoteMachine removed = machines.remove(index);
        int groupId = removed.group();
        if (groupHasAttached(groupId)) {
            return List.of();
        }
        for (RemoteMachine machine : machines) {
            if (machine.group() == groupId) {
                return List.of();
            }
        }
        MachineGroup group = group(groupId);
        if (group == null) {
            return List.of();
        }
        groups.remove(group);
        return group.removeAllCards();
    }

    public boolean contains(BlockPos pos) {
        return index(pos) >= 0;
    }

    /** Índice da máquina em {@code pos} na lista, ou −1. */
    public int index(BlockPos pos) {
        for (int i = 0; i < machines.size(); i++) {
            if (machines.get(i).pos().equals(pos)) {
                return i;
            }
        }
        return -1;
    }

    /** A máquina no índice, ou {@code null} se o índice não vale. */
    public @Nullable RemoteMachine machine(int index) {
        return index >= 0 && index < machines.size() ? machines.get(index) : null;
    }

    /**
     * O estado de cada máquina no motor, na ordem de {@link #machines()}, para um roteador em
     * {@code router} com o raio e o limite dados ({@link RangeRules#reach}). Não olha o upgrade.
     */
    public RangeRules.Reach[] reach(BlockPos router, int radius, int max) {
        int[] distances = new int[machines.size()];
        for (int i = 0; i < distances.length; i++) {
            BlockPos pos = machines.get(i).pos();
            distances[i] = RangeRules.chebyshev(router.getX(), router.getY(), router.getZ(), pos.getX(), pos.getY(), pos.getZ());
        }
        return RangeRules.reach(distances, radius, max);
    }

    /** Quantas máquinas há; com {@link #machine(int)}, percorre sem a lista de {@link #machines()}. */
    public int size() {
        return machines.size();
    }

    /** Quantos grupos há; com {@link #groupAt(int)}, percorre sem a lista de {@link #groups()}. */
    public int groupCount() {
        return groups.size();
    }

    /** O grupo na posição {@code index}, na ordem de criação (não é o id). */
    public MachineGroup groupAt(int index) {
        return groups.get(index);
    }

    /** As máquinas, na ordem de vínculo. Só leitura. */
    public List<RemoteMachine> machines() {
        return Collections.unmodifiableList(machines);
    }

    /** Os grupos, na ordem de criação. Só leitura. */
    public List<MachineGroup> groups() {
        return Collections.unmodifiableList(groups);
    }

    public @Nullable MachineGroup group(int id) {
        for (MachineGroup group : groups) {
            if (group.id() == id) {
                return group;
            }
        }
        return null;
    }

    /** O grupo da máquina. Nunca nulo para uma máquina desta lista (a carga descarta as órfãs). */
    public MachineGroup groupOf(RemoteMachine machine) {
        MachineGroup group = group(machine.group());
        if (group == null) {
            throw new IllegalStateException("Máquina " + machine.pos() + " sem o grupo " + machine.group());
        }
        return group;
    }

    /** A configuração que vale para a máquina: a exceção, ou o perfil do grupo. */
    public MachineConfig effective(RemoteMachine machine) {
        MachineConfig exception = machine.exception();
        return exception != null ? exception : groupOf(machine).profile();
    }

    public boolean isEmpty() {
        return machines.isEmpty() && groups.isEmpty() && attached == null;
    }

    /** Tira os cartões de todos os grupos (para soltar quando o roteador sai do mundo). */
    public List<ItemStack> removeAllCards() {
        List<ItemStack> removed = new ArrayList<>();
        for (MachineGroup group : groups) {
            removed.addAll(group.removeAllCards());
        }
        return removed;
    }

    // ------------------------------------------------------------------ NBT

    public ListTag saveMachines(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (RemoteMachine machine : machines) {
            list.add(machine.save(registries));
        }
        return list;
    }

    public ListTag saveGroups(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MachineGroup group : groups) {
            list.add(group.save(registries));
        }
        return list;
    }

    /** O membro presa no NBT (chave {@code attached}), ou {@code null} sem membro. */
    public @Nullable CompoundTag saveAttached(HolderLookup.Provider registries) {
        return attached == null ? null : attached.save(registries);
    }

    /**
     * Troca o conteúdo pelo das listas {@code machines} e {@code groups} e pelo membro presa {@code attachedTag}
     * (as tags de {@link #saveMachines}, {@link #saveGroups} e {@link #saveAttached}; {@code null} sem membro).
     * Descarta grupos repetidos ou ilegíveis, máquinas repetidas, máquinas sem grupo e a presa sem grupo. Um grupo
     * sem máquinas fica. O próximo id de grupo é o maior lido + 1.
     */
    public void load(ListTag machinesTag, ListTag groupsTag, @Nullable CompoundTag attachedTag,
            HolderLookup.Provider registries) {
        machines.clear();
        groups.clear();
        attached = null;
        nextGroupId = 0;
        for (int i = 0; i < groupsTag.size(); i++) {
            MachineGroup group = MachineGroup.load(groupsTag.getCompound(i), registries);
            if (group == null || group(group.id()) != null) {
                WirelessAutomate.LOGGER.warn("Grupo de máquinas remotas inválido descartado: {}", groupsTag.getCompound(i));
                continue;
            }
            groups.add(group);
            nextGroupId = Math.max(nextGroupId, group.id() + 1);
        }
        for (int i = 0; i < machinesTag.size(); i++) {
            CompoundTag tag = machinesTag.getCompound(i);
            RemoteMachine machine = RemoteMachine.load(tag, registries);
            if (group(machine.group()) == null || contains(machine.pos())) {
                WirelessAutomate.LOGGER.warn("Máquina remota inválida descartada: {}", tag);
                continue;
            }
            machines.add(machine);
        }
        if (attachedTag != null) {
            AttachedMember member = AttachedMember.load(attachedTag, registries);
            if (group(member.group()) == null) {
                WirelessAutomate.LOGGER.warn("Máquina presa sem grupo descartada: {}", attachedTag);
            } else {
                attached = member;
            }
        }
    }
}
