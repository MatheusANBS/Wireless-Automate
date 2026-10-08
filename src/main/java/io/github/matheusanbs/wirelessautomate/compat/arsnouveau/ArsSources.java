package io.github.matheusanbs.wirelessautomate.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.ScalarAccess;
import io.github.matheusanbs.wirelessautomate.storage.BulkSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * O que o mod usa da API do Ars Nouveau para a Source. Só é carregado com o Ars presente: o resto do
 * mod passa por {@code network.Sources}.
 *
 * <p>A capability é criada pelo mesmo nome e tipo que o Ars usa ({@code ars_nouveau:source},
 * {@link ISourceCap}); o NeoForge devolve a mesma instância. Ela é registrada pelo Ars para a Source
 * Jar, a jarra criativa, os Sourcelinks, os Relays e a Imbuement Chamber.
 */
public final class ArsSources {
    public static final BlockCapability<ISourceCap, @Nullable Direction> BLOCK = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source"), ISourceCap.class);

    /**
     * A Source para o laço de um valor só. O Tanque de Source do mod ({@link BulkSource}) vem primeiro: o
     * lado dele é em {@code long}, e entre dois tanques bilhões passam numa visita. Com as jarras e
     * máquinas do Ars, a {@link ISourceCap} é em {@code int}: corta no teto dela.
     */
    public static final ScalarAccess ACCESS = new ScalarAccess() {
        @Override
        public @Nullable Object handler(RouterBlockEntity node, Direction machineFace) {
            BulkSource bulk = node.bulkSource(machineFace);
            return bulk != null ? bulk : node.arsSource(machineFace);
        }

        @Override
        public boolean canExtract(Object handler) {
            return handler instanceof BulkSource || ((ISourceCap) handler).canExtract();
        }

        @Override
        public boolean canReceive(Object handler) {
            return handler instanceof BulkSource || ((ISourceCap) handler).canReceive();
        }

        @Override
        public long extract(Object handler, long amount, boolean simulate) {
            return handler instanceof BulkSource bulk ? bulk.extract(amount, simulate)
                    : ((ISourceCap) handler).extractSource(clamp(amount), simulate);
        }

        @Override
        public long insert(Object handler, long amount, boolean simulate) {
            return handler instanceof BulkSource bulk ? bulk.insert(amount, simulate)
                    : ((ISourceCap) handler).receiveSource(clamp(amount), simulate);
        }
    };

    private static int clamp(long amount) {
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    private ArsSources() {
    }
}
