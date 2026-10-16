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
    private boolean received;
    private long rechargeStart = -1;
    private int rechargeSeen;

    /** Monta a view e manda o update tag só se ela mudou desde a última mandada; devolve se mandou. */
    boolean sendIfChanged(ResonanceCoreBlockEntity core) {
        Level level = core.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        rechargeStart = resolveRechargeStart(core, level.getGameTime());
        rechargeSeen = core.rechargeProgress();
        ChamberView view = ChamberView.of(core, rechargeStart);
        if (view.equals(sentView)) {
            return false;
        }
        sentView = view;
        syncCount++;
        BlockState state = core.getBlockState();
        level.sendBlockUpdated(core.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * O início da recarga, sem guardar: o já fixado enquanto ela dura, para a view ficar a mesma entre os lotes. Só
     * o {@link #sendIfChanged} guarda o resultado. Recomeça quando a recarga para ou quando o progresso volta (um
     * Opaco cheio, o próximo começando).
     */
    private long resolveRechargeStart(ResonanceCoreBlockEntity core, long gameTime) {
        if (!ChamberView.recharging(core)) {
            return -1;
        }
        int progress = core.rechargeProgress();
        if (rechargeStart < 0 || progress < rechargeSeen) {
            return gameTime - progress;
        }
        return rechargeStart;
    }

    /** O início da recarga guardado no último {@link #sendIfChanged} (para os GameTests). */
    long rechargeStart() {
        return rechargeStart;
    }

    /** O update tag: só a view atual. Pedir de novo não muda nada, nem o início guardado da recarga. */
    CompoundTag tag(ResonanceCoreBlockEntity core) {
        CompoundTag tag = new CompoundTag();
        Level level = core.getLevel();
        ChamberView.of(core, resolveRechargeStart(core, level == null ? 0 : level.getGameTime())).save(tag);
        return tag;
    }

    /**
     * Guarda a view recebida; um tag sem câmaras (o completo do disco) não troca a que já há. A lâmpada que acende
     * entre duas views recebidas marca o tempo, para o clarão; a primeira view (a chegada do chunk) não marca.
     */
    void receive(CompoundTag tag, @Nullable Level level) {
        if (!tag.contains("chambers")) {
            return;
        }
        ChamberView view = ChamberView.load(tag);
        if (received && view.tuned() && !clientView.tuned() && level != null) {
            tunedAt = level.getGameTime();
        }
        received = true;
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
