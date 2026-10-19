package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/**
 * Os nomes que o jogador lê de uma máquina vinculada e do grupo dela, montados no servidor (mensagens do
 * Vinculador e do Tablet). A mesma regra da tela do roteador: o rótulo, ou "bloco #N" com N a posição entre
 * as máquinas do mesmo grupo, a partir de 1, na ordem de vínculo; o grupo, o nome dado ou o do bloco.
 */
public final class MachineNames {
    private MachineNames() {
    }

    /** O nome do grupo: o dado pelo jogador ou o do bloco (pelo item, que preenche as lacunas do nome do bloco). */
    public static Component group(MachineGroup group) {
        if (!group.name().isEmpty()) {
            return Component.literal(group.name());
        }
        return RouterBlock.machineName(BuiltInRegistries.BLOCK.get(group.block()));
    }

    /** O nome da máquina em {@code pos}; "?" se ela não está na lista. */
    public static Component machine(RemoteMachines remotes, BlockPos pos) {
        int index = remotes.index(pos);
        RemoteMachine machine = index < 0 ? null : remotes.machine(index);
        if (machine == null) {
            return Component.literal("?");
        }
        if (!machine.label().isEmpty()) {
            return Component.literal(machine.label());
        }
        int number = 0;
        List<RemoteMachine> all = remotes.machines();
        for (int i = 0; i <= index; i++) {
            if (all.get(i).group() == machine.group()) {
                number++;
            }
        }
        MachineGroup group = remotes.group(machine.group());
        Component block = group == null ? Component.literal("?")
                : RouterBlock.machineName(BuiltInRegistries.BLOCK.get(group.block()));
        return Component.translatable("gui.wirelessautomate.router.range.machine.name", block, number);
    }

    /** O nome do grupo da máquina em {@code pos}; "?" se ela não está na lista. */
    public static Component groupOf(RemoteMachines remotes, BlockPos pos) {
        int index = remotes.index(pos);
        RemoteMachine machine = index < 0 ? null : remotes.machine(index);
        MachineGroup group = machine == null ? null : remotes.group(machine.group());
        return group == null ? Component.literal("?") : group(group);
    }
}
