package io.github.matheusanbs.wirelessautomate.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Jogadores de teste no mundo, sempre por aqui: o {@code makeMockServerPlayerInLevel} põe o jogador
 * na lista do servidor e nada o tira, e a grade do GameTest reaproveita as posições entre os lotes.
 * Um jogador esquecido onde outro teste põe um bloco impede a colocação (o jogador ocupa o espaço).
 * Aqui o jogador sai da lista quando o teste dele termina, passando ou falhando.
 */
final class TestPlayers {
    /** Nome que o {@code makeMockServerPlayerInLevel} dá a todo jogador de teste. */
    private static final String MOCK_NAME = "test-mock-player";
    /** O teste de cada jogador ainda na lista; só a thread do servidor mexe. */
    private static final Map<UUID, GameTestInfo> OWNERS = new HashMap<>();

    private TestPlayers() {
    }

    /** Um jogador criativo no mundo do teste, tirado da lista quando o teste termina. */
    @SuppressWarnings("removal")
    static ServerPlayer inLevel(GameTestHelper helper) {
        return removedAtEnd(helper, helper.makeMockServerPlayerInLevel());
    }

    /** Tira da lista, quando o teste termina, um jogador que o teste pôs lá por conta própria. */
    static <P extends ServerPlayer> P removedAtEnd(GameTestHelper helper, P player) {
        GameTestInfo test = helper.testInfo;
        OWNERS.put(player.getUUID(), test);
        test.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {
            }

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                remove(player);
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                remove(player);
            }

            @Override
            public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            }
        });
        return player;
    }

    /**
     * Tira os jogadores de teste que sobraram dentro da caixa do teste: os de um teste já terminado ou
     * os criados fora daqui. Os de um teste ainda rodando ficam, mesmo que ele os tenha levado para cá.
     */
    static void clearLeftovers(GameTestHelper helper) {
        List<ServerPlayer> inside = helper.getLevel().getEntitiesOfClass(ServerPlayer.class,
                helper.getBounds().inflate(1.0), player -> MOCK_NAME.equals(player.getGameProfile().getName()));
        for (ServerPlayer player : inside) {
            GameTestInfo owner = OWNERS.get(player.getUUID());
            if (owner == null || owner.isDone()) {
                remove(player);
            }
        }
    }

    /** Tira da lista só se ainda está lá: o teste pode ter tirado antes, no próprio {@code finally}. */
    private static void remove(ServerPlayer player) {
        OWNERS.remove(player.getUUID());
        PlayerList list = player.getServer().getPlayerList();
        if (list.getPlayer(player.getUUID()) == player) {
            list.remove(player);
        }
    }
}
