package io.github.matheusanbs.wirelessautomate.gametest;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import io.github.matheusanbs.wirelessautomate.compat.arsnouveau.ArsSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * A parte dos testes de Source que usa a API do Ars Nouveau. Não é {@code @GameTestHolder} nem
 * {@code @EventBusSubscriber}: o NeoForge inspeciona essas classes por reflexão, e um tipo do Ars numa
 * assinatura impede o mod de carregar sem ele. Só é chamada por {@link SourceGameTests} com o Ars presente.
 */
final class SourceTestSupport {
    private static ISourceCap cap(ServerLevel level, BlockPos pos) {
        ISourceCap cap = level.getCapability(ArsSources.BLOCK, pos, Direction.UP);
        if (cap == null) {
            throw new IllegalStateException("sem capability de Source em " + pos.toShortString());
        }
        return cap;
    }

    static boolean hasSource(ServerLevel level, BlockPos pos) {
        return level.getCapability(ArsSources.BLOCK, pos, Direction.UP) != null;
    }

    static int amount(ServerLevel level, BlockPos pos) {
        return cap(level, pos).getSource();
    }

    static void set(ServerLevel level, BlockPos pos, int amount) {
        cap(level, pos).setSource(amount);
    }

    static int capacity(ServerLevel level, BlockPos pos) {
        return cap(level, pos).getSourceCapacity();
    }

    private SourceTestSupport() {
    }
}
