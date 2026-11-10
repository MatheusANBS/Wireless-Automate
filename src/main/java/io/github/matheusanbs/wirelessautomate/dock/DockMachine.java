package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.range.CacheOwner;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Uma máquina vinculada a um lado da Doca: a posição e a última frente conhecida, guardadas num {@link RemoteMachine}
 * (o mesmo objeto que vai ao {@code RemoteIndex}, que avisa por ele). Salva só {@code pos} (long) e {@code front}
 * (byte, −1 sem frente); o resto é de tempo de execução.
 */
public final class DockMachine {
    private final RemoteMachine remote;
    /** O lado da Doca ({@code Direction.ordinal()}) a que ela pertence. */
    private final int side;
    /** Está no alcance do tier e dentro do máximo (e no índice das remotas); não é salvo. */
    private boolean active;
    /** Tick do servidor da última entrega ou retirada; não é salvo. */
    private long lastActive;
    /** A máquina avisou mudança (o {@code setChanged} dela, a carga do chunk ou um cache invalidado); não é salvo. */
    private boolean changed;
    /** Os caches de capability, criados na primeira consulta ({@link #caches}). */
    private @Nullable MachineCaches caches;

    public DockMachine(int side, BlockPos pos, int front) {
        this.side = side;
        this.remote = new RemoteMachine(pos, 0, null, front);
    }

    public BlockPos pos() {
        return remote.pos();
    }

    public int side() {
        return side;
    }

    /** Última frente conhecida ({@code Direction.ordinal()}), ou −1 sem frente. */
    public int front() {
        return remote.front();
    }

    public void setFront(int front) {
        remote.setFront(front);
    }

    /** O registro desta máquina no {@code RemoteIndex}. */
    public RemoteMachine remote() {
        return remote;
    }

    /** Ver {@link #active}: só uma máquina ativa recebe pedidos. */
    public boolean active() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    public long lastActive() {
        return lastActive;
    }

    public void setLastActive(long tick) {
        lastActive = tick;
    }

    /** Ver {@link #changed}. */
    public boolean changed() {
        return changed;
    }

    public void markChanged() {
        changed = true;
    }

    public void clearChanged() {
        changed = false;
    }

    /** Os caches de capability da máquina, criados na primeira chamada; toda consulta confere o chunk antes. */
    public MachineCaches caches(CacheOwner owner) {
        if (caches == null) {
            caches = MachineCaches.remote(owner, remote.pos());
        }
        return caches;
    }

    /** Os caches, se já existem (para achar a máquina de um aviso sem criá-los). */
    public @Nullable MachineCaches cachesIfCreated() {
        return caches;
    }

    /** A máquina saiu (desvinculada, ou a Doca saiu do mundo): os caches deixam de valer e de avisar. */
    void discard() {
        if (caches != null) {
            caches.discard();
            caches = null;
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", remote.pos().asLong());
        tag.putByte("front", (byte) remote.front());
        return tag;
    }

    static DockMachine load(int side, CompoundTag tag) {
        int front = tag.contains("front", CompoundTag.TAG_BYTE) ? tag.getByte("front") : -1;
        return new DockMachine(side, BlockPos.of(tag.getLong("pos")), front);
    }
}
