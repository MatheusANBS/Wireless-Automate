package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.github.matheusanbs.wirelessautomate.tv.TvImageModeration;
import io.github.matheusanbs.wirelessautomate.tv.TvImageStore;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryActions;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPictureSender;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Os tetos das imagens enviadas que a revisão curta da etapa 4 pediu: a entrega aos clientes não prende mais memória
 * que o teto, por mais imagens que sejam pedidas; o componente salvo guarda só as imagens que as peças e o fundo usam,
 * até o {@code maxPerScreen}; e remover uma imagem pelo comando a tira também dos componentes.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvImageCapGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);
    /** Num lote só dele: o teto da entrega é global e os outros testes de imagem também entregam. */
    private static final String DELIVERY = "wirelessautomate_tv_delivery";

    private TvImageCapGameTests() {
    }

    private static TvPiece image(UUID id) {
        return new TvPiece(TvPieceType.IMAGE, 0, 0, 32, 32, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(Optional.of(id), 0, TvPartStyle.VAlign.TOP)));
    }

    private static List<TvPicture> stored(MinecraftServer server, UUID author, int count, int size, int seed) {
        TvImageStore store = TvImageStore.get(server);
        List<TvPicture> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] png = TvImageGameTests.png(size, size, seed + i);
            store.putForTests(png, author);
            out.add(store.picture(TvImageHash.of(png)));
        }
        return out;
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = DELIVERY)
    public static void muitosPedidosNaoPassamDoTetoDaEntrega(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TvImageStore store = TvImageStore.get(server);
        List<ServerPlayer> players = List.of(TestPlayers.inLevel(helper), TestPlayers.inLevel(helper),
                TestPlayers.inLevel(helper));
        List<TvPicture> pictures = stored(server, players.get(0).getUUID(), 16, 120, 500);
        long biggest = 0;
        long requested = 0;
        for (TvPicture picture : pictures) {
            long bytes = store.entry(picture.hash()).bytes;
            biggest = Math.max(biggest, bytes);
            requested += bytes * players.size();
        }
        long cap = biggest * 2;
        helper.assertTrue(requested > cap * 4, "os pedidos passam bem do teto do teste");
        TvPictureSender.setCapsForTests(biggest, cap);
        TvLibrary library = TvLibrary.get(server);
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), ModBlocks.SIGNAL_SCREEN.get().defaultBlockState()
                .setValue(TvBlock.FACING, Direction.NORTH));
        for (ServerPlayer player : players) {
            for (TvPicture picture : pictures) {
                library.addPicture(player.getUUID(), picture.hash(), "Foto");
            }
            TvPictureSender.openForTests(player, tv);
        }
        long before = TvPictureSender.chunksSentForTests();
        long[] peak = new long[2];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    TvPictureSender.tickForTests(server);
                    peak[0] = Math.max(peak[0], TvPictureSender.pinnedForTests());
                    peak[1] = Math.max(peak[1], store.loadingForTests());
                    for (ServerPlayer player : players) {
                        helper.assertFalse(TvPictureSender.pendingForTests(player.getUUID()), "ainda entregando");
                    }
                })
                .thenExecute(() -> {
                    TvPictureSender.setCapsForTests(-1, -1);
                    helper.assertTrue(peak[0] > 0 && peak[0] <= cap,
                            "a memória presa ficou no teto: " + peak[0] + " de " + cap);
                    helper.assertTrue(peak[1] <= 4, "no máximo 4 leituras em curso: " + peak[1]);
                    helper.assertValueEqual(TvPictureSender.pinnedForTests(), 0L, "nada preso no fim");
                    helper.assertTrue(TvPictureSender.chunksSentForTests() - before >= (long) pictures.size()
                            * players.size(), "todas as imagens saíram para os três");
                    for (ServerPlayer player : players) {
                        for (TvPicture picture : pictures) {
                            helper.assertTrue(TvPictureSender.knownForTests(player.getUUID(), picture.hash()),
                                    "cada imagem foi a cada um");
                        }
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void componenteSalvoGuardaSoAsImagensQueUsa(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = player.getUUID();
        List<TvPicture> pictures = stored(server, id, 9, 8, 600);
        helper.setBlock(A.south(), Blocks.STONE);
        helper.setBlock(A, ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity,
                        "sem block entity"))
                .thenExecute(() -> {
                    ((TvBlockEntity) helper.getBlockEntity(A)).setOwner(id);
                    TvMenu menu = opened(helper, player);
                    TvLibrary library = TvLibrary.get(server);
                    TvComponent one = new TvComponent("Uma imagem", List.of(image(pictures.get(0).id())),
                            Optional.empty(), pictures);
                    TvLibraryActions.handle(player, TvLibraryActionPayload.saveComponent(menu.containerId, one));
                    helper.assertValueEqual(saved(library, id, "Uma imagem").pictures(), List.of(pictures.get(0)),
                            "as 8 imagens que nenhuma peça usa saíram");
                    TvComponent layout = new TvComponent("Layout", List.of(image(pictures.get(1).id())),
                            Optional.of(new TvComponent.Layout(64, 64, new TvBackground(TvBackground.Kind.TILED, 0,
                                    0, Optional.of(pictures.get(2).id()), 2, 100), false)), pictures);
                    TvLibraryActions.handle(player, TvLibraryActionPayload.saveComponent(menu.containerId, layout));
                    helper.assertValueEqual(saved(library, id, "Layout").pictures(),
                            List.of(pictures.get(1), pictures.get(2)), "o layout salvo leva a da peça e a do fundo");
                    List<TvPiece> nine = new ArrayList<>();
                    for (TvPicture picture : pictures) {
                        nine.add(image(picture.id()));
                    }
                    TvLibraryActions.handle(player, TvLibraryActionPayload.saveComponent(menu.containerId,
                            new TvComponent("Nove", nine, Optional.empty(), pictures)));
                    helper.assertTrue(library.components(id).stream().noneMatch(c -> c.name().equals("Nove")),
                            "9 imagens passam do teto de 8 e o componente é recusado");

                    TvImageModeration.remove(server, pictures.get(0).hash());
                    helper.assertTrue(saved(library, id, "Uma imagem").pictures().isEmpty(),
                            "remover pelo comando tira a imagem do componente");
                    helper.assertFalse(library.pictureHashes(id).contains(pictures.get(0).hash()),
                            "e a biblioteca não a segura mais");
                })
                .thenSucceed();
    }

    private static TvComponent saved(TvLibrary library, UUID player, String name) {
        return library.components(player).stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private static TvMenu opened(GameTestHelper helper, TestPlayers.MenuPlayer player) {
        player.setPos(helper.absoluteVec(Vec3.atCenterOf(A)));
        BlockPos pos = helper.absolutePos(A);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        helper.assertTrue(player.containerMenu instanceof TvMenu, "o menu não abriu");
        return (TvMenu) player.containerMenu;
    }
}
