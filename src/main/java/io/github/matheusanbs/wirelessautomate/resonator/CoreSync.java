package io.github.matheusanbs.wirelessautomate.resonator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A câmara do Núcleo no cliente: no servidor, a última {@link ChamberView} mandada e quantas vezes mandou; no
 * cliente, a view recebida e o tempo em que a lâmpada acendeu. Nada daqui vai para o NBT.
 */
final class CoreSync {
    private @Nullable ChamberView sentView;
    private int syncCount;
    private ChamberView clientView = ChamberView.EMPTY;
    private long tunedAt = -1;

    /** Monta a view e manda o update tag só se ela mudou desde a última mandada; devolve se mandou. */
    boolean sendIfChanged(ResonanceCoreBlockEntity core) {
        Level level = core.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        ChamberView view = ChamberView.of(core, level.getGameTime());
        if (view.equals(sentView)) {
            return false;
        }
        sentView = view;
        syncCount++;
        BlockState state = core.getBlockState();
        level.sendBlockUpdated(core.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        return true;
    }

    /** O update tag: só a view atual. */
    static CompoundTag tag(ResonanceCoreBlockEntity core) {
        CompoundTag tag = new CompoundTag();
        Level level = core.getLevel();
        ChamberView.of(core, level == null ? 0 : level.getGameTime()).save(tag);
        return tag;
    }

    /**
     * Guarda a view recebida; um tag sem câmaras (o completo do disco) não troca a que já há. A lâmpada que acende
     * marca o tempo, para o clarão.
     */
    void receive(CompoundTag tag, @Nullable Level level) {
        if (!tag.contains("chambers")) {
            return;
        }
        ChamberView view = ChamberView.load(tag);
        if (view.tuned() && !clientView.tuned() && level != null) {
            tunedAt = level.getGameTime();
        }
        clientView = view;
    }

    int syncCount() {
        return syncCount;
    }

    ChamberView clientView() {
        return clientView;
    }

    long tunedAt() {
        return tunedAt;
    }
}
