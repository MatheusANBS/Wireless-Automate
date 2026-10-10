package io.github.matheusanbs.wirelessautomate.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.ScalarAccess;
import io.github.matheusanbs.wirelessautomate.storage.BulkSource;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * O que o mod usa da API do Ars Nouveau para a Source. Só é carregado com o Ars presente: o resto do
 * mod passa por {@code network.Sources}.
 *
 * <p>Porte 1.20.1 (Ars 4.12): não há capability de Source. As jarras, os Relays, os Sourcelinks e as
 * máquinas do Ars são block entities que implementam {@link ISourceTile} (em {@code int} e sem simulação),
 * e o roteador os acha pelo próprio block entity ({@link #sourceTile}), guardado no {@code CapCache} da
 * face: nada de {@code getBlockEntity} por tick.
 */
public final class ArsSources {
    /** O {@link ISourceTile} do block entity (como {@code Object}), ou {@code null} se ele não for um. */
    public static @Nullable Object sourceTile(BlockEntity be) {
        return be instanceof ISourceTile ? be : null;
    }

    /**
     * A Source para o laço de um valor só. O Tanque de Source do mod ({@link BulkSource}) vem primeiro: o
     * lado dele é em {@code long}, e entre dois tanques bilhões passam numa visita. Com as jarras e
     * máquinas do Ars, o {@link ISourceTile} é em {@code int} e não simula: a simulação é feita aqui, pelo
     * que cabe ({@code getMaxSource() − getSource()}) e pelo que tem ({@code getSource()}), e corta no teto
     * do {@code int}. Entre simular e executar o valor não muda, porque o laço roda num tick só.
     */
    public static final ScalarAccess ACCESS = new ScalarAccess() {
        @Override
        public @Nullable Object handler(RouterBlockEntity node, Direction machineFace) {
            BulkSource bulk = node.bulkSource(machineFace);
            return bulk != null ? bulk : node.arsSource(machineFace);
        }

        @Override
        public boolean canExtract(Object handler) {
            return true; // o ISourceTile não diz se dá; o extract devolve 0 quando está vazio
        }

        @Override
        public boolean canReceive(Object handler) {
            return handler instanceof BulkSource || ((ISourceTile) handler).canAcceptSource();
        }

        @Override
        public long extract(Object handler, long amount, boolean simulate) {
            if (handler instanceof BulkSource bulk) {
                return bulk.extract(amount, simulate);
            }
            ISourceTile tile = (ISourceTile) handler;
            int taken = Math.min(clamp(amount), Math.max(0, tile.getSource()));
            if (!simulate && taken > 0) {
                tile.removeSource(taken);
            }
            return taken;
        }

        @Override
        public long insert(Object handler, long amount, boolean simulate) {
            if (handler instanceof BulkSource bulk) {
                return bulk.insert(amount, simulate);
            }
            ISourceTile tile = (ISourceTile) handler;
            // Sem o canAcceptSource o espaço pode ser negativo (getSource acima do máximo): vira 0.
            long room = (long) tile.getMaxSource() - tile.getSource();
            int accepted = (int) Math.max(0, Math.min(clamp(amount), room));
            if (!simulate && accepted > 0) {
                tile.addSource(accepted);
            }
            return accepted;
        }
    };

    private static int clamp(long amount) {
        return (int) Math.min(Math.max(0, amount), Integer.MAX_VALUE);
    }

    private ArsSources() {
    }
}
