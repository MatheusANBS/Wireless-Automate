package io.github.matheusanbs.wirelessautomate.range;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * A máquina presa (a do bloco na frente do roteador) como membro de um grupo de {@link RemoteMachines}: o grupo
 * ({@link MachineGroup#id()}), a exceção ({@code null} = usa o perfil do grupo) e a última frente conhecida
 * (índice de {@code Direction.ordinal()}, ou −1 sem frente). Não fica na lista das remotas: não conta no limite
 * do tier, não entra no {@link RemoteIndex} e vale sem o Upgrade de Alcance. A posição é sempre a do bloco na
 * frente do roteador.
 */
public final class AttachedMember {
    private int group;
    private @Nullable MachineConfig exception;
    private int front;

    AttachedMember(int group, @Nullable MachineConfig exception, int front) {
        this.group = group;
        this.exception = exception;
        this.front = front;
    }

    public int group() {
        return group;
    }

    void setGroup(int group) {
        this.group = group;
    }

    /** A configuração própria da presa, ou {@code null} se ela segue o perfil do grupo. */
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

    /** NBT com {@code group}, {@code exception} (só se houver) e {@code front}. */
    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("group", group);
        if (exception != null) {
            tag.put("exception", exception.save(registries));
        }
        tag.putInt("front", front);
        return tag;
    }

    /** Lê o NBT de {@link #save}; sem a chave {@code group} não há grupo a que pertencer, e devolve {@code null}. */
    static @Nullable AttachedMember load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("group", CompoundTag.TAG_INT)) {
            return null;
        }
        MachineConfig exception = tag.contains("exception", CompoundTag.TAG_COMPOUND)
                ? MachineConfig.load(tag.getCompound("exception"), registries)
                : null;
        int front = tag.contains("front", CompoundTag.TAG_INT) ? tag.getInt("front") : -1;
        return new AttachedMember(tag.getInt("group"), exception, front);
    }
}
