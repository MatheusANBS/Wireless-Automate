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
    /** Está na fila da volta ({@link DockReturn}), para já ou em recuo; não é salvo. */
    private boolean returnAwake;
    /**
     * Tick do servidor da próxima revisita da volta, com a máquina em recuo (a última visita não achou nada); 0 para
     * já. Não é salvo: na carga, a Doca põe todas as ativas na fila de novo.
     */
    private long returnAt;
    /** O intervalo do recuo da volta, em ticks: dobra a cada visita vazia, até o teto; 0 recomeça. Não é salvo. */
    private int returnInterval;
    /** Tick do servidor até o qual a máquina dorme (recusou um pedido); zerado quando ela muda. Não é salvo. */
    private long sleepUntil;
    /** Os caches de capability, criados na primeira consulta ({@link #caches}). */
    private @Nullable MachineCaches caches;
    /**
     * A conferência da Doca achou a máquina no lugar e ligou a vigia do cache ({@link MachineCaches#watch}), que avisa
     * quando a block entity sai; um aviso de invalidação a desliga. Não é salvo.
     */
    private boolean watched;

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

    /** Ver {@link #returnAwake}. */
    boolean returnAwake() {
        return returnAwake;
    }

    void setReturnAwake(boolean awake) {
        returnAwake = awake;
    }

    /** Ver {@link #returnAt}. */
    long returnAt() {
        return returnAt;
    }

    void setReturnAt(long tick) {
        returnAt = tick;
    }

    /** Ver {@link #returnInterval}. */
    int returnInterval() {
        return returnInterval;
    }

    void setReturnInterval(int ticks) {
        returnInterval = ticks;
    }

    /** Dorme (fica fora da escolha) até o tick {@code tick}, ou até mudar ({@link #wake}). */
    public void sleepUntil(long tick) {
        sleepUntil = tick;
    }

    public void wake() {
        sleepUntil = 0;
    }

    /** Está dormindo no tick {@code now}. */
    public boolean asleep(long now) {
        return now < sleepUntil;
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

    /** Ver {@link #watched}. */
    boolean watched() {
        return watched;
    }

    void setWatched(boolean watched) {
        this.watched = watched;
    }

    /** A máquina saiu (desvinculada, ou a Doca saiu do mundo): os caches deixam de valer e de avisar. */
    void discard() {
        watched = false;
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
