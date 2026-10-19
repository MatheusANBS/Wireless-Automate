package io.github.matheusanbs.wirelessautomate.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

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

    /** Um {@link MenuPlayer} criativo no mundo do teste, tirado da lista quando o teste termina. */
    static MenuPlayer menuOpener(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), MOCK_NAME),
                false);
        MenuPlayer player = new MenuPlayer(helper, cookie);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return removedAtEnd(helper, player);
    }

    /** Um {@link MenuPlayer} em sobrevivência (o alcance do jogo comum), tirado da lista quando o teste termina. */
    static MenuPlayer survivalMenuOpener(GameTestHelper helper) {
        MenuPlayer player = menuOpener(helper);
        player.creative = false;
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /**
     * Jogador falso como o do {@link GameTestHelper#makeMockServerPlayerInLevel()}, mas que abre telas sem mandar o
     * pacote de abertura da NeoForge (a conexão falsa não negocia canais, e o pacote derrubaria o servidor) e guarda
     * os dados extras da abertura, para o teste lê-los como o cliente leria.
     */
    static final class MenuPlayer extends ServerPlayer {
        private static int nextContainerId = 100;
        @Nullable RegistryFriendlyByteBuf openData;
        /** O último texto mandado à barra de ação, para o teste ler (nulo: nenhum). */
        @Nullable Component actionBar;
        private boolean creative = true;

        private MenuPlayer(GameTestHelper helper, CommonListenerCookie cookie) {
            super(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            if (actionBar) {
                this.actionBar = message;
            }
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return creative;
        }

        @Override
        public OptionalInt openMenu(@Nullable MenuProvider provider,
                @Nullable Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
            if (provider == null) {
                return OptionalInt.empty();
            }
            if (containerMenu != inventoryMenu) {
                doCloseContainer();
            }
            int containerId = nextContainerId++;
            AbstractContainerMenu menu = provider.createMenu(containerId, getInventory(), this);
            if (menu == null) {
                return OptionalInt.empty();
            }
            openData = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
            if (extraDataWriter != null) {
                extraDataWriter.accept(openData);
            }
            containerMenu = menu;
            return OptionalInt.of(containerId);
        }
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
