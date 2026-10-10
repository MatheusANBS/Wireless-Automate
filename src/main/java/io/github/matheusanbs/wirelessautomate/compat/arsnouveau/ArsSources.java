package io.github.matheusanbs.wirelessautomate.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.common.block.tile.CreativeSourceJarTile;
import com.hollingsworth.arsnouveau.common.block.tile.SourcelinkTile;
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
 *
 * <p>Quem é origem e quem é destino segue a capability do main (Ars 5.13), refeita aqui sobre o
 * {@link ISourceTile}: a Imbuement Chamber não é drenada, os Sourcelinks não recebem e a Creative Jar é ralo
 * e fonte infinitos (como o {@code SourceStorage} dela no 5.13, que devolve o pedido inteiro nos dois
 * sentidos).
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

        /**
         * Como a capability do main (Ars 5.13): só é origem quem tem vazão de saída. A Imbuement Chamber
         * ({@code getTransferRate() = 0}, o {@code maxExtract 0} do 5.13) só recebe, e o roteador não a drena.
         */
        @Override
        public boolean canExtract(Object handler) {
            return handler instanceof BulkSource || ((ISourceTile) handler).getTransferRate() > 0;
        }

        /**
         * Como a capability do main: os Sourcelinks geram Source e não recebem ({@code canReceive false} no
         * 5.13), embora o {@code canAcceptSource} do 4.12 diga que sim. A Creative Jar recebe sempre (ralo).
         */
        @Override
        public boolean canReceive(Object handler) {
            if (handler instanceof BulkSource || handler instanceof CreativeSourceJarTile) {
                return true;
            }
            ISourceTile tile = (ISourceTile) handler;
            return tile.canAcceptSource() && !(tile instanceof SourcelinkTile);
        }

        /** A Creative Jar é fonte infinita: devolve o pedido (até o teto do {@code int}) sem tirar nada. */
        @Override
        public long extract(Object handler, long amount, boolean simulate) {
            if (handler instanceof BulkSource bulk) {
                return bulk.extract(amount, simulate);
            }
            if (handler instanceof CreativeSourceJarTile) {
                return clamp(amount);
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
            if (handler instanceof CreativeSourceJarTile) {
                return clamp(amount); // ralo: aceita tudo (até o teto do int) e não muda nada
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
