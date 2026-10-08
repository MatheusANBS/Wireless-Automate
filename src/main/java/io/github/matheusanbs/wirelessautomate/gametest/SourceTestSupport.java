package io.github.matheusanbs.wirelessautomate.gametest;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import io.github.matheusanbs.wirelessautomate.compat.arsnouveau.ArsSources;
import java.util.List;
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

    /** Há um provider válido do {@code SourceManager} (ou uma jarra) exatamente em {@code pos}. */
    static boolean providerAt(ServerLevel level, BlockPos pos) {
        for (ISpecialSourceProvider provider : SourceUtil.canTakeSource(pos, level, 2)) {
            if (provider.isValid() && provider.getCurrentPos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /** O que uma máquina do Ars faz para gastar Source: tira {@code amount} das fontes no raio. */
    static boolean takeNearby(ServerLevel level, BlockPos center, int range, int amount) {
        return SourceUtil.takeSourceMultiple(center, level, range, amount) != null;
    }

    /** As posições que aceitam Source no raio (o que um Sourcelink procura). */
    static List<BlockPos> canGiveNearby(ServerLevel level, BlockPos center, int range) {
        return SourceUtil.canGiveSource(center, level, range).stream()
                .map(ISpecialSourceProvider::getCurrentPos).toList();
    }

    private SourceTestSupport() {
    }
}
