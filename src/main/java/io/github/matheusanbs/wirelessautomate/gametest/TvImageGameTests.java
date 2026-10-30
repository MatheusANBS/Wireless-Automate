package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.packet.TvImageHavePayload;
import io.github.matheusanbs.wirelessautomate.packet.TvImageReplyPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvImageUploadPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvEditing;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvImageConfig;
import io.github.matheusanbs.wirelessautomate.tv.TvImageFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.github.matheusanbs.wirelessautomate.tv.TvImageStore;
import io.github.matheusanbs.wirelessautomate.tv.TvImageUploads;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPictureSender;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As imagens enviadas (etapa 4, Task 4a): o envio completo em partes, o repetido que não manda bytes, o limite por
 * minuto com a trava, o teto do mundo, a cota, o banido, {@code uploaders = ops}, os bytes que não batem com o
 * declarado, o envio abandonado, o salvar que confia só no armazém e o teto por Tela, a entrega (uma vez, e nenhuma ao
 * cliente que já tem), o remover pelo comando, a limpeza depois do prazo, o NBT antigo e o update tag sem os bytes.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvImageGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final String CONFIG = "wirelessautomate_config_tv";

    private TvImageGameTests() {
    }

    /** Um PNG de verdade, com um degradê da semente (resumos diferentes por semente). */
    static byte[] png(int w, int h, int seed) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, 0xFF000000 | (x * 7 + seed) % 256 << 16 | (y * 5 + seed * 3) % 256 << 8 | seed % 256);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    /** Um resumo qualquer, que nenhum arquivo tem (o começo de um envio só declara). */
    private static String fakeHash(String seed) {
        return TvImageHash.of((seed + UUID.randomUUID()).getBytes());
    }

    private static TvImageUploadPayload start(String hash, int size, int w, int h) {
        return TvImageUploadPayload.start(hash, size, TvImageFormat.PNG.ordinal(), w, h, 1, "Foto");
    }

    private static TvImageUploadPayload start(byte[] png, int w, int h) {
        return start(TvImageHash.of(png), png.length, w, h);
    }

    private static TvImageReplyPayload.Status status(TvImageReplyPayload reply) {
        return reply == null ? null : reply.status();
    }

    private static String reason(TvImageReplyPayload reply) {
        return reply == null ? "" : reply.reason();
    }

    /** Manda os bytes em pedaços e o fim. */
    private static void sendAll(ServerPlayer player, byte[] data) {
        String hash = TvImageHash.of(data);
        int chunk = Math.max(1, data.length / 3);
        for (int at = 0; at < data.length; at += chunk) {
            TvImageUploads.handle(player, TvImageUploadPayload.chunk(hash, at,
                    Arrays.copyOfRange(data, at, Math.min(data.length, at + chunk))));
        }
        TvImageUploads.handle(player, TvImageUploadPayload.end(hash));
    }

    private static BlockState screen() {
        return ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH);
    }

    private static TvPiece image(UUID id) {
        return new TvPiece(TvPieceType.IMAGE, 0, 0, 32, 32, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(Optional.of(id), 0, TvPartStyle.VAlign.TOP)));
    }

    private static TvLayout withPictures(List<TvPicture> pictures) {
        List<TvPiece> pieces = new ArrayList<>();
        for (TvPicture picture : pictures) {
            pieces.add(image(picture.id()));
        }
        return TvLayout.of(pieces, TvTheme.DEFAULT, TvBackground.DEFAULT, false, List.of(), pictures);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void envioCompletoGuardaOArquivoEEntraNaBiblioteca(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        byte[] png = png(40, 20, 1);
        String hash = TvImageHash.of(png);
        TvImageReplyPayload reply = TvImageUploads.handle(player, start(png, 40, 20));
        helper.assertValueEqual(status(reply), TvImageReplyPayload.Status.SEND, "o servidor pede os bytes");
        sendAll(player, png);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(TvImageStore.get(server).contains(hash), "a imagem no armazém"))
                .thenExecute(() -> {
                    TvImageStore store = TvImageStore.get(server);
                    helper.assertTrue(TvLibrary.get(server).hasPicture(player.getUUID(), hash), "na biblioteca");
                    TvPicture picture = store.picture(hash);
                    helper.assertValueEqual(picture.width(), 40, "a largura do cabeçalho");
                    helper.assertValueEqual(picture.author(), Optional.of(player.getUUID()), "o autor");
                    helper.assertValueEqual(store.entry(hash).libraries(), 1, "o uso da biblioteca conta");
                    helper.assertTrue(Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("data")
                            .resolve(WirelessAutomate.MODID).resolve("images").resolve(hash + ".png")), "o arquivo");
                    helper.assertTrue(TvPictureSender.knownForTests(player.getUUID(), hash),
                            "quem enviou não recebe de volta");
                    helper.assertFalse(TvImageUploads.uploadingForTests(player.getUUID()), "o envio terminou");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void envioRepetidoNaoPedeOsBytes(GameTestHelper helper) {
        ServerPlayer first = TestPlayers.inLevel(helper);
        ServerPlayer second = TestPlayers.inLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        byte[] png = png(16, 16, 2);
        String hash = TvImageHash.of(png);
        helper.assertTrue(TvImageStore.get(server).putForTests(png, first.getUUID()), "a imagem guardada");
        TvImageReplyPayload reply = TvImageUploads.handle(second, start(png, 16, 16));
        helper.assertValueEqual(status(reply), TvImageReplyPayload.Status.OK, "já existe: OK sem bytes");
        helper.assertFalse(TvImageUploads.uploadingForTests(second.getUUID()), "nenhum envio aberto");
        helper.assertTrue(TvLibrary.get(server).hasPicture(second.getUUID(), hash), "entrou na biblioteca");
        helper.assertValueEqual(TvImageStore.get(server).picture(hash).author(), Optional.of(first.getUUID()),
                "o autor continua o primeiro");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = CONFIG)
    public static void limitePorMinutoETravaAutomatica(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        long before = TvImageConfig.bytesPerMinuteValue().get();
        try {
            TvImageConfig.bytesPerMinuteValue().set(1L << 20);
            helper.assertValueEqual(status(TvImageUploads.handle(player, start(fakeHash("a"), 700_000, 10, 10))),
                    TvImageReplyPayload.Status.SEND, "o primeiro cabe no minuto");
            for (int i = 0; i < 3; i++) {
                TvImageReplyPayload refused = TvImageUploads.handle(player, start(fakeHash("b" + i), 700_000, 10, 10));
                helper.assertValueEqual(reason(refused), "RATE", "passaria de 1 MB no minuto (" + i + ")");
            }
            TvImageReplyPayload locked = TvImageUploads.handle(player, start(fakeHash("c"), 10, 10, 10));
            helper.assertValueEqual(reason(locked), "COOLDOWN", "três batidas em 10 minutos travam");
            helper.assertTrue(TvImageUploads.refusals().getOrDefault(player.getUUID(), 0) >= 4,
                    "as recusas contam na lista");
        } finally {
            TvImageConfig.bytesPerMinuteValue().set(before);
            TvImageUploads.forget(player.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = CONFIG)
    public static void tetoDoMundoRecusaEnvioNovo(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        long before = TvImageConfig.maxWorldBytesValue().get();
        try {
            TvImageStore.dedicatedForTests = true;
            TvImageConfig.maxWorldBytesValue().set(TvImageStore.get(server).totalBytes() + 1000);
            helper.assertValueEqual(reason(TvImageUploads.handle(player, start(fakeHash("w"), 2000, 10, 10))),
                    "WORLD_FULL", "acima do teto do mundo");
            TvImageStore.dedicatedForTests = false;
            helper.assertValueEqual(status(TvImageUploads.handle(player, start(fakeHash("w2"), 2000, 10, 10))),
                    TvImageReplyPayload.Status.SEND, "no servidor integrado não há teto");
        } finally {
            TvImageStore.dedicatedForTests = false;
            TvImageConfig.maxWorldBytesValue().set(before);
            TvImageUploads.forget(player.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = CONFIG)
    public static void cotaPorJogadorQuandoLigada(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        long before = TvImageConfig.maxBytesPerPlayerValue().get();
        try {
            helper.assertValueEqual(status(TvImageUploads.handle(player, start(fakeHash("q0"), 5000, 10, 10))),
                    TvImageReplyPayload.Status.SEND, "a cota vem desligada");
            TvImageConfig.maxBytesPerPlayerValue().set(4000L);
            helper.assertValueEqual(reason(TvImageUploads.handle(player, start(fakeHash("q1"), 5000, 10, 10))),
                    "QUOTA", "acima da cota ligada");
        } finally {
            TvImageConfig.maxBytesPerPlayerValue().set(before);
            TvImageUploads.forget(player.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = CONFIG)
    public static void soOperadoresComUploadersOps(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        String before = TvImageConfig.uploadersValue().get();
        try {
            TvImageConfig.uploadersValue().set("ops");
            helper.assertValueEqual(reason(TvImageUploads.handle(player, start(fakeHash("o"), 100, 10, 10))),
                    "NOT_ALLOWED", "quem não é operador não envia");
        } finally {
            TvImageConfig.uploadersValue().set(before);
            TvImageUploads.forget(player.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = CONFIG)
    public static void desligadoRecusaTudo(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        boolean before = TvImageConfig.enabledValue().get();
        try {
            TvImageConfig.enabledValue().set(false);
            helper.assertValueEqual(reason(TvImageUploads.handle(player, start(fakeHash("d"), 100, 10, 10))),
                    "DISABLED", "imagens desligadas");
        } finally {
            TvImageConfig.enabledValue().set(before);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void banidoNaoEnviaEDesbanidoVolta(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        TvImageStore store = TvImageStore.get(helper.getLevel().getServer());
        helper.assertTrue(store.ban(player.getUUID()), "banido");
        try {
            helper.assertValueEqual(reason(TvImageUploads.handle(player, start(fakeHash("x"), 100, 10, 10))),
                    "BANNED", "banido não envia");
        } finally {
            store.unban(player.getUUID());
        }
        helper.assertValueEqual(status(TvImageUploads.handle(player, start(fakeHash("y"), 100, 10, 10))),
                TvImageReplyPayload.Status.SEND, "desbanido volta a enviar");
        TvImageUploads.forget(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cabecalhoDeclaradoForaDosTetosERecusado(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        helper.assertValueEqual(reason(TvImageUploads.handle(player, start(fakeHash("big"), 100, 4096, 10))),
                "TOO_LARGE", "lado acima de 1024");
        helper.assertValueEqual(reason(TvImageUploads.handle(player, start(fakeHash("fat"), 9 << 20, 10, 10))),
                "FILE_TOO_BIG", "arquivo acima de 8 MB");
        helper.assertValueEqual(reason(TvImageUploads.handle(player, TvImageUploadPayload.start(fakeHash("gif"),
                100, TvImageFormat.GIF.ordinal(), 10, 10, 241, "g"))), "TOO_MANY_FRAMES", "GIF com 241 quadros");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bytesQueNaoBatemComODeclaradoSaoRecusados(GameTestHelper helper) {
        ServerPlayer liar = TestPlayers.inLevel(helper);
        ServerPlayer wrongSize = TestPlayers.inLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        byte[] png = png(32, 16, 3);
        String hash = TvImageHash.of(png);
        helper.assertValueEqual(status(TvImageUploads.handle(liar, start(png, 64, 64))),
                TvImageReplyPayload.Status.SEND, "declarou 64 × 64");
        sendAll(liar, png);
        byte[] other = png(16, 16, 4);
        String otherHash = TvImageHash.of(other);
        TvImageUploads.handle(wrongSize, start(other, 16, 16));
        TvImageReplyPayload overflow = TvImageUploads.handle(wrongSize,
                TvImageUploadPayload.chunk(otherHash, 0, new byte[other.length + 10]));
        helper.assertValueEqual(reason(overflow), "MISMATCH", "pedaço além do tamanho declarado");
        helper.startSequence()
                .thenIdle(10)
                .thenWaitUntil(() -> helper.assertFalse(TvImageUploads.uploadingForTests(liar.getUUID()),
                        "o envio mentiroso terminou"))
                .thenExecute(() -> {
                    helper.assertFalse(TvImageStore.get(server).contains(hash), "o cabeçalho mentiu: nada guardado");
                    helper.assertFalse(TvImageStore.get(server).contains(otherHash), "nada guardado");
                    helper.assertFalse(TvLibrary.get(server).hasPicture(liar.getUUID(), hash), "nem na biblioteca");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = CONFIG)
    public static void envioAbandonadoEDescartado(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        AtomicLong now = new AtomicLong(1_000_000);
        TvImageUploads.setClockForTests(now::get);
        try {
            TvImageUploads.handle(player, start(fakeHash("z"), 1000, 10, 10));
            helper.assertTrue(TvImageUploads.uploadingForTests(player.getUUID()), "envio aberto");
            now.addAndGet(29_000);
            TvImageUploads.timeoutNowForTests(helper.getLevel().getServer());
            helper.assertTrue(TvImageUploads.uploadingForTests(player.getUUID()), "29 s ainda espera");
            now.addAndGet(1_000);
            TvImageUploads.timeoutNowForTests(helper.getLevel().getServer());
            helper.assertFalse(TvImageUploads.uploadingForTests(player.getUUID()), "30 s sem pedaço: descartado");
            TvImageUploads.handle(player, start(fakeHash("z2"), 1000, 10, 10));
            TvImageUploads.forget(player.getUUID());
            helper.assertFalse(TvImageUploads.uploadingForTests(player.getUUID()), "quem sai perde o envio");
        } finally {
            TvImageUploads.setClockForTests(null);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void salvarConfiaSoNoArmazemENoTetoPorTela(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        MinecraftServer server = helper.getLevel().getServer();
        byte[] png = png(24, 12, 5);
        String hash = TvImageHash.of(png);
        helper.assertTrue(TvImageStore.get(server).putForTests(png, player.getUUID()), "guardada");
        helper.setBlock(A.south(), Blocks.STONE);
        helper.setBlock(A, screen());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem a Tela"))
                .thenExecute(() -> {
                    TvBlockEntity tv = (TvBlockEntity) helper.getBlockEntity(A);
                    tv.setOwner(player.getUUID());
                    TvPicture lie = new TvPicture(hash, TvImageFormat.GIF, 999, 999, 50, Optional.empty());
                    TvPicture unknown = new TvPicture(fakeHash("u"), TvImageFormat.PNG, 8, 8, 1, Optional.empty());
                    helper.assertTrue(TvEditing.applyLayout(player, tv, withPictures(List.of(lie, unknown))).ok(),
                            "salvou");
                    TvLayout saved = tv.layout();
                    helper.assertValueEqual(saved.pictures(), List.of(TvImageStore.get(server).picture(hash)),
                            "a imagem é a do armazém; a desconhecida saiu");
                    helper.assertValueEqual(saved.pieces().size(), 2, "nenhuma peça saiu");
                    helper.assertValueEqual(saved.pieces().get(1).style().part().image(), Optional.empty(),
                            "a peça da desconhecida ficou sem imagem");
                    helper.assertValueEqual(TvImageStore.get(server).entry(hash).screens(), 1, "a Tela conta");
                    List<TvPicture> many = new ArrayList<>();
                    for (int i = 0; i < 9; i++) {
                        byte[] each = png(8, 8, 100 + i);
                        TvImageStore.get(server).putForTests(each, player.getUUID());
                        many.add(TvImageStore.get(server).picture(TvImageHash.of(each)));
                    }
                    helper.assertFalse(TvEditing.applyLayout(player, tv, withPictures(many)).ok(),
                            "9 imagens passam do teto de 8 por Tela");
                    helper.assertValueEqual(tv.layout(), saved, "a Tela não mudou");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void entregaUmaVezENadaAQuemJaTem(GameTestHelper helper) {
        ServerPlayer near = TestPlayers.inLevel(helper);
        ServerPlayer cached = TestPlayers.inLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        byte[] png = png(300, 300, 6);
        String hash = TvImageHash.of(png);
        TvImageStore.get(server).putForTests(png, near.getUUID());
        TvPictureSender.onHave(cached, new TvImageHavePayload(List.of(hash, fakeHash("nao-existe"))));
        helper.assertTrue(TvPictureSender.knownForTests(cached.getUUID(), hash), "o cliente disse que tem");
        TvPictureSender.onHave(cached, new TvImageHavePayload(List.of()));
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen());
        tv.setLayout(withPictures(List.of(TvImageStore.get(server).picture(hash))));
        long before = TvPictureSender.chunksSentForTests();
        TvPictureSender.visitForTests(tv, List.of(near, cached));
        helper.assertTrue(TvPictureSender.knownForTests(near.getUUID(), hash), "anotado para quem está perto");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    TvPictureSender.tickForTests(server);
                    helper.assertTrue(TvPictureSender.chunksSentForTests() > before, "os pedaços saíram");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    long sent = TvPictureSender.chunksSentForTests() - before;
                    int expected = (png.length + 256 * 1024 - 1) / (256 * 1024);
                    helper.assertValueEqual(sent, (long) expected, "um pedaço por 256 KB, só para quem não tinha");
                    TvPictureSender.visitForTests(tv, List.of(near, cached));
                    TvPictureSender.tickForTests(server);
                    helper.assertValueEqual(TvPictureSender.chunksSentForTests() - before, (long) expected,
                            "a segunda volta não manda de novo");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void removerPeloComandoTiraDeTudo(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        byte[] png = png(20, 20, 7);
        String hash = TvImageHash.of(png);
        TvImageStore.get(server).putForTests(png, player.getUUID());
        TvLibrary.get(server).addPicture(player.getUUID(), hash, "Foto");
        TvImageStore.syncLibrary(server, player.getUUID());
        helper.setBlock(A.south(), Blocks.STONE);
        helper.setBlock(A, screen());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem a Tela"))
                .thenExecute(() -> {
                    TvBlockEntity tv = (TvBlockEntity) helper.getBlockEntity(A);
                    TvPicture picture = TvImageStore.get(server).picture(hash);
                    tv.setLayout(TvLayout.of(List.of(image(picture.id())), TvTheme.DEFAULT,
                            new TvBackground(TvBackground.Kind.TILED, 0, 0, Optional.of(picture.id()), 2, 100), false,
                            List.of(), List.of(picture)));
                    helper.assertValueEqual(TvImageStore.get(server).entry(hash).uses(), 2, "Tela e biblioteca");
                })
                // O onLoad da Tela recém-colocada roda no tick seguinte: só então ela está entre as carregadas.
                .thenIdle(2)
                .thenExecute(() -> {
                    TvBlockEntity tv = (TvBlockEntity) helper.getBlockEntity(A);
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                            "wa screen image remove " + TvImageHash.brief(hash));
                    helper.assertFalse(TvImageStore.get(server).contains(hash), "saiu do armazém");
                    helper.assertFalse(TvLibrary.get(server).hasPicture(player.getUUID(), hash), "e da biblioteca");
                    helper.assertTrue(tv.layout().pictures().isEmpty(), "e da Tela carregada");
                    helper.assertValueEqual(tv.layout().pieces().get(0).style().part().image(), Optional.empty(),
                            "a peça fica sem imagem");
                    helper.assertValueEqual(tv.layout().background(), TvBackground.DEFAULT, "o mosaico volta ao tema");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = CONFIG)
    public static void limpezaSoDepoisDoPrazo(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        AtomicLong now = new AtomicLong(System.currentTimeMillis());
        TvImageStore.setClockForTests(now::get);
        try {
            TvImageStore store = TvImageStore.get(server);
            byte[] loose = png(10, 10, 8);
            byte[] kept = png(10, 10, 9);
            store.putForTests(loose, player.getUUID());
            store.putForTests(kept, player.getUUID());
            TvLibrary.get(server).addPicture(player.getUUID(), TvImageHash.of(kept), "Fica");
            TvImageStore.syncLibrary(server, player.getUUID());
            now.addAndGet(TvImageStore.GRACE_MS - 1);
            store.cleanupNowForTests();
            helper.assertTrue(store.contains(TvImageHash.of(loose)), "antes do prazo fica");
            now.addAndGet(1);
            store.cleanupNowForTests();
            helper.assertFalse(store.contains(TvImageHash.of(loose)), "sem uso há 10 minutos: apagada");
            helper.assertTrue(store.contains(TvImageHash.of(kept)), "a da biblioteca fica");
            TvLibrary.get(server).removePicture(player.getUUID(), TvImageHash.of(kept));
            TvImageStore.syncLibrary(server, player.getUUID());
        } finally {
            TvImageStore.setClockForTests(null);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nbtAntigoLeSemImagensEALeituraLarga(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        TvLayout old = TvLayout.of(List.of(image(UUID.randomUUID())));
        CompoundTag encoded = (CompoundTag) TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertFalse(encoded.contains("pictures"), "o layout sem imagens não grava a chave");
        CompoundTag tag = new CompoundTag();
        tag.putInt("width", 1);
        tag.putInt("height", 1);
        tag.put("layout", encoded);
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen());
        tv.loadWithComponents(tag, registries);
        helper.assertTrue(tv.layout().pictures().isEmpty(), "a Tela antiga lê sem imagens");
        byte[] png = png(8, 8, 10);
        TvPicture valid = new TvPicture(TvImageHash.of(png), TvImageFormat.PNG, 8, 8, 1, Optional.empty());
        CompoundTag withPictures = (CompoundTag) TvLayout.CODEC.encodeStart(NbtOps.INSTANCE,
                withPictures(List.of(valid))).getOrThrow();
        ListTag list = withPictures.getList("pictures", Tag.TAG_COMPOUND);
        CompoundTag broken = list.getCompound(0).copy();
        broken.putString("hash", "../../nada");
        list.add(broken);
        TvLayout read = TvLayout.CODEC.parse(NbtOps.INSTANCE, withPictures).getOrThrow();
        helper.assertValueEqual(read.pictures(), List.of(valid), "a imagem inválida sai, a boa fica");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void updateTagLevaSoOResumoDaImagem(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        byte[] png = png(512, 512, 11);
        TvPicture picture = new TvPicture(TvImageHash.of(png), TvImageFormat.PNG, 512, 512, 1,
                Optional.of(UUID.randomUUID()));
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen());
        tv.setLayout(withPictures(List.of(picture)));
        CompoundTag update = tv.getUpdateTag(registries);
        CompoundTag layout = update.getCompound("layout");
        helper.assertValueEqual(layout.getList("pictures", Tag.TAG_COMPOUND).size(), 1, "a referência vai");
        helper.assertFalse(update.getBoolean("pictures_off"), "ligadas: sem o quadro neutro");
        helper.assertTrue(update.sizeInBytes() < 4096, "o update tag fica pequeno: " + update.sizeInBytes()
                + " bytes com uma imagem de " + png.length);
        TvBlockEntity client = new TvBlockEntity(helper.absolutePos(A), screen());
        client.handleUpdateTag(update, registries);
        helper.assertValueEqual(client.layout().pictures(), List.of(picture), "o cliente lê a imagem igual");
        helper.succeed();
    }
}
