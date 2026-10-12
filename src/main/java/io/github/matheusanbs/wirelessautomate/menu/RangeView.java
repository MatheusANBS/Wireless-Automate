package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O que a tela do roteador mostra das máquinas remotas, dentro do {@link RouterSnapshot}. Só existe
 * com o Upgrade de Alcance valendo ({@code rangeActive()}); sem ele é {@link #EMPTY} e o snapshot é o
 * de sempre. Os estados são lidos só na captura, com a tela aberta.
 *
 * @param radius   raio do tier, em blocos (Chebyshev)
 * @param max      máquinas que o tier move
 * @param groups   os grupos, na ordem do roteador
 * @param machines as máquinas, na ordem de vínculo (o índice é o do {@link EditTarget#machine})
 * @param target   o alvo de edição atual da tela
 * @param face     a face selecionada no menu do servidor (absoluta na presa, canônica num alvo remoto).
 *                 Trocar de alvo a põe numa face padrão do quadro novo (Frente = NORTH num remoto, a face
 *                 de antes na presa); o cliente a adota quando o alvo do snapshot muda
 */
public record RangeView(int radius, int max, List<GroupView> groups, List<MachineView> machines, EditTarget target,
        Direction face) {
    public static final RangeView EMPTY = new RangeView(0, 0, List.of(), List.of(), EditTarget.ATTACHED_TARGET,
            Direction.NORTH);

    /** Estado de uma máquina remota na tela. */
    public enum MachineState {
        /** Ativa e com o chunk carregado. */
        LOADED,
        /** Ativa, mas o chunk está descarregado: volta a mover quando ele carregar. */
        UNLOADED,
        /** O bloco não é mais o do grupo ou perdeu toda capability de um tipo carregado. */
        MISSING,
        /** Fora do raio do tier. */
        OUT_OF_RANGE,
        /** Dentro do raio, mas além do limite de máquinas do tier. */
        OVER_LIMIT
    }

    /**
     * Um grupo.
     *
     * @param name       nome dado pelo jogador; vazio = o nome do bloco
     * @param count      máquinas vinculadas no grupo
     * @param exceptions dessas, quantas têm exceção
     */
    public record GroupView(int id, ResourceLocation block, String name, int count, int exceptions) {
        /** O item do bloco do grupo, para o ícone ({@link ItemStack#EMPTY} se o bloco não tem item). */
        public ItemStack icon() {
            return new ItemStack(BuiltInRegistries.BLOCK.get(block).asItem());
        }
    }

    /**
     * Uma máquina remota.
     *
     * @param group     id do grupo
     * @param distance  distância de Chebyshev ao roteador
     * @param exception tem exceção (não segue o perfil)
     */
    public record MachineView(BlockPos pos, int group, int distance, boolean exception, MachineState state) {
    }

    /** Há upgrade valendo (a tela mostra a lista). */
    public boolean present() {
        return radius > 0;
    }

    public @Nullable GroupView group(int id) {
        for (GroupView group : groups) {
            if (group.id == id) {
                return group;
            }
        }
        return null;
    }

    public @Nullable MachineView machine(int index) {
        return index >= 0 && index < machines.size() ? machines.get(index) : null;
    }

    /** Máquinas que gastam vaga (dentro do raio e até o limite): o "7" de {@code 7/32}. */
    public int used() {
        int used = 0;
        for (MachineView machine : machines) {
            if (machine.state != MachineState.OUT_OF_RANGE && machine.state != MachineState.OVER_LIMIT) {
                used++;
            }
        }
        return used;
    }

    static void encode(RegistryFriendlyByteBuf buf, RangeView view) {
        buf.writeBoolean(view.present());
        if (!view.present()) {
            return;
        }
        buf.writeVarInt(view.radius);
        buf.writeVarInt(view.max);
        buf.writeEnum(view.target.kind());
        buf.writeVarInt(view.target.id());
        buf.writeEnum(view.face);
        buf.writeVarInt(view.groups.size());
        for (GroupView group : view.groups) {
            buf.writeVarInt(group.id);
            buf.writeResourceLocation(group.block);
            buf.writeUtf(group.name, RouterSnapshot.NAME_LENGTH);
            buf.writeVarInt(group.count);
            buf.writeVarInt(group.exceptions);
        }
        buf.writeVarInt(view.machines.size());
        for (MachineView machine : view.machines) {
            buf.writeBlockPos(machine.pos);
            buf.writeVarInt(machine.group);
            buf.writeVarInt(machine.distance);
            buf.writeBoolean(machine.exception);
            buf.writeEnum(machine.state);
        }
    }

    static RangeView decode(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return EMPTY;
        }
        int radius = buf.readVarInt();
        int max = buf.readVarInt();
        EditTarget target = new EditTarget(buf.readEnum(EditTarget.Kind.class), buf.readVarInt());
        Direction face = buf.readEnum(Direction.class);
        int groupCount = buf.readVarInt();
        List<GroupView> groups = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            groups.add(new GroupView(buf.readVarInt(), buf.readResourceLocation(), buf.readUtf(RouterSnapshot.NAME_LENGTH),
                    buf.readVarInt(), buf.readVarInt()));
        }
        int machineCount = buf.readVarInt();
        List<MachineView> machines = new ArrayList<>(machineCount);
        for (int i = 0; i < machineCount; i++) {
            machines.add(new MachineView(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readEnum(MachineState.class)));
        }
        return new RangeView(radius, max, List.copyOf(groups), List.copyOf(machines), target, face);
    }
}
