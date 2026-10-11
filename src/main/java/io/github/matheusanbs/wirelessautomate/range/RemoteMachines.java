package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
     * Vincula a máquina em {@code pos}: entra no primeiro grupo do bloco {@code block}, ou num grupo
     * novo com o perfil vazio. Devolve {@code false} se a posição já estava vinculada. Só pelo
     * {@code RouterBlockEntity.linkMachine} (ver a classe).
     */
    public boolean link(BlockPos pos, ResourceLocation block, int front) {
        if (contains(pos)) {
            return false;
        }
        MachineGroup group = firstGroupOf(block);
        if (group == null) {
            group = new MachineGroup(nextGroupId++, block, "", new MachineConfig());
            groups.add(group);
        }
        machines.add(new RemoteMachine(pos, group.id(), null, front));
        return true;
    }

    private @Nullable MachineGroup firstGroupOf(ResourceLocation block) {
        for (MachineGroup group : groups) {
            if (group.block().equals(block)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Desvincula a máquina em {@code pos}. Se o grupo dela esvazia, ele sai e os cartões dele voltam
     * na lista, para quem chamou soltar no mundo. Sem a máquina, devolve uma lista vazia. Só pelo
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
        return machines.isEmpty() && groups.isEmpty();
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

    /**
     * Troca o conteúdo pelo das listas {@code machines} e {@code groups} (as tags de
     * {@link #saveMachines} e {@link #saveGroups}). Descarta grupos repetidos ou ilegíveis, máquinas
     * repetidas e máquinas sem grupo. O próximo id de grupo é o maior lido + 1.
     */
    public void load(ListTag machinesTag, ListTag groupsTag, HolderLookup.Provider registries) {
        machines.clear();
        groups.clear();
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
    }
}
