package io.github.matheusanbs.wirelessautomate.range;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Uma máquina remota vinculada a um roteador: a posição, o grupo ({@link MachineGroup#id()}), a
 * exceção ({@code null} = usa o perfil do grupo) e a última frente conhecida (índice de
 * {@code Direction.ordinal()}, ou −1 sem frente), relida pelo motor quando o estado do bloco muda.
 */
public final class RemoteMachine {
    private final BlockPos pos;
    private int group;
    private @Nullable MachineConfig exception;
    private int front;

    public RemoteMachine(BlockPos pos, int group, @Nullable MachineConfig exception, int front) {
        this.pos = pos.immutable();
        this.group = group;
        this.exception = exception;
        this.front = front;
    }

    public BlockPos pos() {
        return pos;
    }

    public int group() {
        return group;
    }

    void setGroup(int group) {
        this.group = group;
    }

    /** A configuração própria da máquina, ou {@code null} se ela segue o perfil do grupo. */
    public @Nullable MachineConfig exception() {
        return exception;
    }

    public boolean hasException() {
        return exception != null;
    }

    /** Põe ou tira ({@code null}) a exceção. Mude pelo roteador, que avisa o motor. */
    public void setException(@Nullable MachineConfig exception) {
        this.exception = exception;
    }

    /** Última frente conhecida (índice de {@code Direction}), ou −1 sem frente. */
    public int front() {
        return front;
    }

    public void setFront(int front) {
        this.front = front;
    }

    /** NBT com {@code pos} (long), {@code group}, {@code exception} (só se houver) e {@code front}. */
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        tag.putInt("group", group);
        if (exception != null) {
            tag.put("exception", exception.save(registries));
        }
        tag.putInt("front", front);
        return tag;
    }

    public static RemoteMachine load(CompoundTag tag, HolderLookup.Provider registries) {
        MachineConfig exception = tag.contains("exception", CompoundTag.TAG_COMPOUND)
                ? MachineConfig.load(tag.getCompound("exception"), registries)
                : null;
        int front = tag.contains("front", CompoundTag.TAG_INT) ? tag.getInt("front") : -1;
        return new RemoteMachine(BlockPos.of(tag.getLong("pos")), tag.getInt("group"), exception, front);
    }
}
