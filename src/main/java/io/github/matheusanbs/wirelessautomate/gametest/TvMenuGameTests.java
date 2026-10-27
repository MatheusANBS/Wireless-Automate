package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TvSizePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvEditing;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A tela da Tela de Sinal: abrir, o menu, aplicar o tamanho e a lista das redes da abertura. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvMenuGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private TvMenuGameTests() {
    }

    /** Uma Tela virada para o norte em {@code A}, com parede atrás dela e da célula à esquerda de quem olha. */
    private static void withScreen(GameTestHelper helper, UUID owner, Consumer<TvBlockEntity> body) {
        helper.setBlock(A.south(), Blocks.STONE);
        helper.setBlock(A.south().west(), Blocks.STONE);
        helper.setBlock(A, ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem block entity"))
                .thenExecute(() -> {
                    TvBlockEntity tv = (TvBlockEntity) helper.getBlockEntity(A);
                    tv.setOwner(owner);
                    body.accept(tv);
                })
                .thenSucceed();
    }

    private static void click(GameTestHelper helper, TestPlayers.MenuPlayer player) {
        BlockPos pos = helper.absolutePos(A);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }

    /** Põe o jogador ao lado da Tela e abre o menu pelo clique. */
    private static TvMenu opened(GameTestHelper helper, TestPlayers.MenuPlayer player) {
        player.setPos(helper.absoluteVec(Vec3.atCenterOf(A)));
        click(helper, player);
        helper.assertTrue(player.containerMenu instanceof TvMenu, "o menu não abriu");
        return (TvMenu) player.containerMenu;
    }

    private static void size(TestPlayers.MenuPlayer player, TvMenu menu, int w, int h, int col, int row) {
        TvEditing.handleSize(player, new TvSizePayload(menu.containerId, w, h, col, row));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void donoAbreOutroNao(GameTestHelper helper) {
        TestPlayers.MenuPlayer owner = TestPlayers.menuOpener(helper);
        TestPlayers.MenuPlayer other = TestPlayers.menuOpener(helper);
        withScreen(helper, owner.getUUID(), tv -> {
            opened(helper, owner);
            click(helper, other);
            helper.assertFalse(other.containerMenu instanceof TvMenu, "outro jogador abriu a tela");
            helper.assertTrue(other.actionBar != null, "sem aviso de quem é a Tela");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tamanhoValidoAplica(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            size(player, menu, 2, 1, 0, 0);
            helper.assertValueEqual(tv.area(), new TvArea(2, 1, 0, 0), "área");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tamanhoComProblemaRecusa(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            helper.setBlock(A.west(), Blocks.TORCH);
            size(player, menu, 2, 1, 0, 0);
            helper.assertValueEqual(tv.area(), TvArea.SINGLE, "a área mudou com a tocha na frente");
            helper.assertTrue(player.actionBar != null, "sem aviso do problema");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tamanhoForaDosLimitesRecusa(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            size(player, menu, 0, 1, 0, 0);
            size(player, menu, 99, 1, 0, 0);
            size(player, menu, 1, 99, 0, 0);
            size(player, menu, 2, 1, 2, 0);
            size(player, menu, 2, 1, -1, 0);
            size(player, menu, 2, 1, 0, 1);
            TvEditing.handleSize(player, new TvSizePayload(menu.containerId + 1, 2, 1, 0, 0));
            helper.assertValueEqual(tv.area(), TvArea.SINGLE, "a área mudou com um pedido inválido");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void menuInvalidoLonge(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            helper.assertTrue(menu.stillValid(player), "perto, o menu devia valer");
            player.setPos(helper.absoluteVec(Vec3.atCenterOf(A.offset(20, 0, 0))));
            helper.assertFalse(menu.stillValid(player), "a 20 blocos, o menu devia cair");
            size(player, menu, 2, 1, 0, 0);
            helper.assertValueEqual(tv.area(), TvArea.SINGLE, "aplicou de longe");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void listaDeRedesSoAsQueUsa(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork own = data.create(player.getUUID(), "teste-tv-lista-propria");
        WaNetwork foreignPublic = data.create(UUID.randomUUID(), "teste-tv-lista-publica").withPublic(true);
        data.update(foreignPublic);
        WaNetwork foreign = data.create(UUID.randomUUID(), "teste-tv-lista-privada");
        withScreen(helper, player.getUUID(), tv -> {
            try {
                opened(helper, player);
                helper.assertTrue(player.openData != null, "a abertura não escreveu os dados");
                TvMenu client = new TvMenu(1, player.getInventory(), player.openData);
                List<UUID> ids = client.networks().stream().map(TvMenu.NetworkChoice::id).toList();
                helper.assertTrue(ids.contains(own.id()), "faltou a rede própria");
                helper.assertTrue(ids.contains(foreignPublic.id()), "faltou a rede pública");
                helper.assertFalse(ids.contains(foreign.id()), "veio a rede privada de outro");
                helper.assertValueEqual(client.area(), TvArea.SINGLE, "área na abertura");
                helper.assertValueEqual(client.pos(), helper.absolutePos(A), "posição na abertura");
            } finally {
                data.remove(own.id());
                data.remove(foreignPublic.id());
                data.remove(foreign.id());
            }
        });
    }
}
