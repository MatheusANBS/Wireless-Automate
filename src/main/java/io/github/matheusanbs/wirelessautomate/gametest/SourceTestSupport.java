package io.github.matheusanbs.wirelessautomate.gametest;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A parte dos testes de Source que usa a API do Ars Nouveau. Não é {@code @GameTestHolder} nem
 * {@code @EventBusSubscriber}: o Forge inspeciona essas classes por reflexão, e um tipo do Ars numa
 * assinatura impede o mod de carregar sem ele. Só é chamada por {@link SourceGameTests} com o Ars presente.
 *
 * <p>Porte 1.20.1 (D5): o Ars 4.12 não tem capability de Source; quem guarda Source é um block entity
 * {@link ISourceTile} (a Source Jar e o Tanque de Source), lido direto do mundo.
 */
final class SourceTestSupport {
    private static ISourceTile cap(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ISourceTile tile)) {
            throw new IllegalStateException("sem ISourceTile em " + pos.toShortString());
        }
        return tile;
    }

    static boolean hasSource(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ISourceTile;
    }

    static int amount(ServerLevel level, BlockPos pos) {
        return cap(level, pos).getSource();
    }

    static void set(ServerLevel level, BlockPos pos, int amount) {
        cap(level, pos).setSource(amount);
    }

    static int capacity(ServerLevel level, BlockPos pos) {
        return cap(level, pos).getMaxSource();
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

    /**
     * O que uma máquina do Ars faz para gastar Source: tira {@code amount} de uma fonte no raio. Porte 1.20.1: o Ars
     * 4.12 não tem o {@code takeSourceMultiple} (que juntava várias fontes e devolvia o que tirou se não bastasse);
     * o {@code takeSource} tira tudo de uma fonte só que tenha o bastante, ou nada.
     */
    static boolean takeNearby(ServerLevel level, BlockPos center, int range, int amount) {
        return SourceUtil.takeSource(center, level, range, amount) != null;
    }

    /** As posições que aceitam Source no raio (o que um Sourcelink procura). */
    static List<BlockPos> canGiveNearby(ServerLevel level, BlockPos center, int range) {
        return SourceUtil.canGiveSource(center, level, range).stream()
                .map(ISpecialSourceProvider::getCurrentPos).toList();
    }

    private SourceTestSupport() {
    }
}
