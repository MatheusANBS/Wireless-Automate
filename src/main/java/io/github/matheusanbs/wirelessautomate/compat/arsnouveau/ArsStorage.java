package io.github.matheusanbs.wirelessautomate.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import io.github.matheusanbs.wirelessautomate.storage.ScalarStore;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * O Tanque de Source visto pelo Ars Nouveau. Só é carregada com o Ars presente (pela ponte
 * {@code network.Sources}); as assinaturas públicas não têm tipos do Ars.
 *
 * <p>Porte 1.20.1 (Ars 4.12, sem capability de Source): com o Ars, o block entity do tanque é o
 * {@link SourceTank}, que implementa {@link ISourceTile}. Ele entra no {@link SourceManager} como provider,
 * que as máquinas do Ars (e os Sourcelinks a 5 blocos) consultam no raio delas. Os Relays do Ars 4.12 só
 * ligam em {@code AbstractSourceMachine} (conferido no bytecode do {@code RelayTile}): os mixins de
 * {@code compat/arsnouveau/mixin} entregam a eles a {@link RelayView} do tanque ({@link #forRelay}). O Ars
 * é todo em {@code int}: quantidade e capacidade aparecem no máximo
 * {@link Integer#MAX_VALUE}, mas o conteúdo continua em {@code long} no {@link ScalarStore}.
 */
public final class ArsStorage {
    /** O block entity do Tanque de Source com o Ars presente. */
    public static StorageSourceTankBlockEntity newTank(BlockPos pos, BlockState state) {
        return new SourceTank(pos, state);
    }

    /**
     * Põe o tanque no {@link SourceManager} (só no servidor). O provider deixa de valer quando o block
     * entity sai do mundo, e o Ars o descarta sozinho a cada 60 ticks.
     */
    public static void registerProvider(StorageSourceTankBlockEntity tank) {
        Level level = tank.getLevel();
        if (level == null || level.isClientSide || !(tank instanceof SourceTank sourceTank)) {
            return;
        }
        SourceManager.INSTANCE.addInterface(level, new TankProvider(sourceTank));
    }

    /**
     * Para os mixins dos Relays: o block entity que o Relay vê na posição. O Tanque de Source vira a
     * {@link RelayView} dele (um {@code AbstractSourceMachine}); qualquer outro passa igual.
     */
    public static BlockEntity forRelay(BlockEntity be) {
        return be instanceof SourceTank tank && !tank.isRemoved() ? tank.relayView() : be;
    }

    private static int clamp(long value) {
        return (int) Math.min(Math.max(0, value), Integer.MAX_VALUE);
    }

    /**
     * O Tanque de Source como {@link ISourceTile}. {@code addSource(n)} e {@code removeSource(n)} devolvem o
     * "total novo" relativo ao {@link #getSource()} de antes, para quem calcula o que passou como antes −
     * depois acertar mesmo com mais de {@link Integer#MAX_VALUE} guardado.
     */
    public static final class SourceTank extends StorageSourceTankBlockEntity implements ISourceTile {
        private @Nullable RelayView relayView;

        public SourceTank(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        /** A visão deste tanque para os Relays, criada na primeira vez e sempre no nível atual do tanque. */
        public RelayView relayView() {
            RelayView view = relayView;
            if (view == null) {
                view = relayView = new RelayView(this);
            }
            if (view.getLevel() != level) {
                view.setLevel(level);
            }
            return view;
        }

        @Override
        public int getTransferRate() {
            return Integer.MAX_VALUE;
        }

        /**
         * Só aceita se a visão em {@code int} tem espaço: o Sourcelink calcula a vazão como
         * {@code getMaxSource() − getSource()}, e com o {@code int} cheio passaria 0 para sempre.
         */
        @Override
        public boolean canAcceptSource() {
            return getSource() < getMaxSource() && store().insert(1, true) > 0;
        }

        @Override
        public int getSource() {
            return clamp(store().stored());
        }

        /** A capacidade em {@code int}: sem limite (0) ou acima do {@code int} vira {@link Integer#MAX_VALUE}. */
        @Override
        public int getMaxSource() {
            long capacity = capacity();
            return capacity <= 0 || capacity > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
        }

        /** A capacidade é a do tier: não muda por aqui. */
        @Override
        public void setMaxSource(int max) {
        }

        /**
         * O {@code setSource} do Ars, tratado como delta sobre a visão em {@code int}: o Ars só enxerga
         * {@link Integer#MAX_VALUE}, então um {@code setSource(getSource() - n)} de terceiros num tanque com
         * mais que isso tira só {@code n} e não derruba o conteúdo para o teto do {@code int}. Abaixo do teto
         * é o mesmo que trocar o valor; {@code setSource(getSource())} não muda nada.
         */
        @Override
        public int setSource(int source) {
            ScalarStore store = store();
            long stored = store.stored();
            store.replace(stored + ((long) Math.max(0, source) - clamp(stored)));
            return getSource();
        }

        /** Total novo relativo ao getSource() de antes (o Ars calcula o que passou como antes − depois). */
        @Override
        public int addSource(int source) {
            int before = getSource();
            return clamp((long) before + store().insert(Math.max(0, source), false));
        }

        @Override
        public int removeSource(int source) {
            int before = getSource();
            return (int) Math.max(0, before - store().extract(Math.max(0, source), false));
        }
    }

    /** O tanque no {@link SourceManager}: vale enquanto este block entity é o que está na posição. */
    private record TankProvider(SourceTank tank) implements ISpecialSourceProvider {
        @Override
        public ISourceTile getSource() {
            return tank;
        }

        @Override
        public boolean isValid() {
            // trocar o block entity, quebrar e descarregar o chunk passam todos por setRemoved
            return !tank.isRemoved() && tank.getLevel() != null;
        }

        @Override
        public BlockPos getCurrentPos() {
            return tank.getBlockPos();
        }
    }

    private ArsStorage() {
    }
}
