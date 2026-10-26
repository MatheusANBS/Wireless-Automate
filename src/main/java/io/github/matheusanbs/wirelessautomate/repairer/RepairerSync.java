package io.github.matheusanbs.wirelessautomate.repairer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * A sincronização da {@link RepairerView}: no servidor, só manda o update tag quando a view muda (um reparo começa ou
 * termina, o rachado de uma entrada muda), e só confere depois de {@link #markDirty()}; no cliente, guarda a recebida.
 * Nada por tick com o bloco parado.
 */
public final class RepairerSync {
    private boolean dirty;
    @Nullable
    private RepairerView sent;
    private RepairerView client = RepairerView.EMPTY;
    private int syncCount;

    public void markDirty() {
        dirty = true;
    }

    /** Manda o update tag se algo marcou a view como suja e ela de fato mudou desde a última mandada. */
    public boolean sendIfChanged(RepairerBlockEntity repairer) {
        if (!dirty) {
            return false;
        }
        dirty = false;
        Level level = repairer.getLevel();
        if (level == null || level.isClientSide || (sent != null && sent.matches(repairer))) {
            return false;
        }
        sent = RepairerView.of(repairer);
        level.sendBlockUpdated(repairer.getBlockPos(), repairer.getBlockState(), repairer.getBlockState(), Block.UPDATE_CLIENTS);
        syncCount++;
        return true;
    }

    /** A view de agora, para o update tag; guarda como mandada se nada foi mandado ainda. */
    public CompoundTag tag(RepairerBlockEntity repairer) {
        RepairerView now = RepairerView.of(repairer);
        if (sent == null) {
            sent = now;
        }
        CompoundTag tag = new CompoundTag();
        now.save(tag);
        return tag;
    }

    /** No cliente: troca a view se o tag a traz. */
    public void receive(CompoundTag tag) {
        RepairerView view = RepairerView.load(tag);
        if (view != null) {
            client = view;
        }
    }

    public RepairerView clientView() {
        return client;
    }

    public int syncCount() {
        return syncCount;
    }
}
