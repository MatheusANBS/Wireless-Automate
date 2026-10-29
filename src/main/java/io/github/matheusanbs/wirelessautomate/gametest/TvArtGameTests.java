package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.packet.TvArtsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvArtRef;
import io.github.matheusanbs.wirelessautomate.tv.TvArtSender;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvEditing;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryActions;
import io.github.matheusanbs.wirelessautomate.tv.TvManager;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As artes fora do pacote do chunk (etapa 4, Task 1c): o update tag com as artes só como referência e abaixo do
 * {@code tv.maxLayoutBytes} com uma arte de 128 × 128, o NBT do disco inteiro e o antigo lendo igual, o pacote de
 * artes a quem está perto e uma vez por mudança delas (não a quem está longe; de novo quando volta ou quando o chunk
 * é mandado de novo; na abertura do menu), o salvar sem a arte que guarda a cópia da Tela, o teto de 131.072 pixels, a arte de 128 × 128 de ida e volta
 * e a biblioteca que recusa a arte que a faria passar do pacote.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvArtGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private TvArtGameTests() {
    }

    private static void withScreen(GameTestHelper helper, UUID owner, Consumer<TvBlockEntity> body) {
        helper.setBlock(A.south(), Blocks.STONE);
        helper.setBlock(A, screen());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem block entity"))
                .thenExecute(() -> {
                    TvBlockEntity tv = (TvBlockEntity) helper.getBlockEntity(A);
                    tv.setOwner(owner);
                    body.accept(tv);
                })
                .thenSucceed();
    }

    private static BlockState screen() {
        return ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH);
    }

    /** Uma arte de pixels sorteados (a semente decide), com uma paleta de cinzas opacos. */
    private static TvPixelArt art(UUID id, String name, int w, int h, long seed) {
        int[] palette = new int[16];
        for (int i = 1; i < 16; i++) {
            palette[i] = 0xFF000000 | i * 0x111111;
        }
        byte[] pixels = new byte[w * h];
        Random random = new Random(seed);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) random.nextInt(16);
        }
        return new TvPixelArt(id, name, w, h, palette, pixels);
    }

    private static TvPiece image(UUID art, int x) {
        return new TvPiece(TvPieceType.IMAGE, x, 0, 32, 32, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(Optional.of(art), 1, TvPartStyle.VAlign.TOP)));
    }

    private static TvPiece text(String value) {
        return new TvPiece(TvPieceType.TEXT, 0, 0, 20, 8, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withText(value));
    }

    private static TvLayout withArts(String label, List<TvPixelArt> arts) {
        List<TvPiece> pieces = new ArrayList<>();
        pieces.add(text(label));
        for (int i = 0; i < arts.size(); i++) {
            pieces.add(image(arts.get(i).id(), i * 4));
        }
        return TvLayout.of(pieces, TvTheme.DEFAULT, TvBackground.DEFAULT, false, arts);
    }

    private static int bytes(Tag tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            NbtIo.writeAnyTag(tag, data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.size();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void updateTagSemPixelsEAbaixoDoTetoComArte128(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        TvPixelArt big = art(UUID.randomUUID(), "Grande", 128, 128, 1);
        TvLayout layout = withArts("t", List.of(big));
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen());
        tv.setLayout(layout);

        CompoundTag update = tv.getUpdateTag(registries);
        CompoundTag sent = update.getCompound("layout");
        helper.assertFalse(sent.contains("images"), "o update tag leva os pixels");
        List<TvArtRef> refs = TvArtRef.read(sent);
        helper.assertTrue(refs != null && refs.size() == 1, "as referências: " + refs);
        helper.assertValueEqual(refs.get(0), TvArtRef.of(big), "a referência da arte");
        helper.assertValueEqual(refs.get(0).hash(), big.contentHash(), "o resumo");
        int full = TvLayoutPayload.encodedSize(layout);
        int reduced = bytes(sent);
        helper.assertValueEqual(TvLayoutPayload.clientSize(layout), reduced, "a medida do layout que vai ao cliente");
        helper.assertTrue(reduced <= Config.tvMaxLayoutBytes(), "o update tag passa do teto: " + reduced);
        helper.assertTrue(full > 8192 && reduced < 2048, "inteiro " + full + " B, reduzido " + reduced + " B");
        WirelessAutomate.LOGGER.info("Tela de Sinal: layout com uma arte 128 × 128, inteiro {} B, no update tag {} B",
                full, reduced);

        CompoundTag disk = tv.saveWithoutMetadata(registries);
        helper.assertValueEqual(disk.getCompound("layout").getList("images", Tag.TAG_COMPOUND).getCompound(0)
                .getByteArray("pixels").length, 8192, "o disco guarda os pixels");

        TvBlockEntity client = new TvBlockEntity(helper.absolutePos(A), screen());
        client.handleUpdateTag(update, registries);
        helper.assertTrue(client.layout().images().isEmpty(), "o cliente lê arte sem os pixels");
        helper.assertValueEqual(client.artRefs(), tv.artRefs(), "as referências no cliente");
        helper.assertValueEqual(client.artKey(), tv.artKey(), "a chave no cliente");
        client.resolveArts(List.of(big));
        helper.assertValueEqual(client.layout(), layout, "com a arte, o layout do cliente é o do servidor");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nbtAntigoDaTelaLeIgual(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        TvPixelArt art = art(UUID.randomUUID(), "Antiga", 64, 64, 2);
        TvLayout layout = withArts("antes", List.of(art));
        CompoundTag old = new CompoundTag();
        old.putInt("width", 2);
        old.putInt("height", 1);
        old.putInt("anchor_col", 0);
        old.putInt("anchor_row", 0);
        old.putInt("revision", 7);
        old.put("layout", TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).getOrThrow());
        old.putUUID("owner", UUID.randomUUID());
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen());
        tv.loadWithComponents(old, registries);
        helper.assertValueEqual(tv.layout(), layout, "o layout com a arte inteira");
        helper.assertValueEqual(tv.revision(), 7, "a revisão");
        helper.assertValueEqual(tv.artRefs(), List.of(TvArtRef.of(art)), "as referências calculadas do layout");
        helper.assertFalse(tv.getUpdateTag(registries).getCompound("layout").contains("images"),
                "a Tela antiga manda o update tag sem os pixels");
        TvBlockEntity blank = new TvBlockEntity(helper.absolutePos(A), screen());
        CompoundTag older = old.copy();
        older.put("layout", TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, TvLayout.of(List.of(text("x"))))
                .getOrThrow());
        blank.loadWithComponents(older, registries);
        helper.assertTrue(blank.artRefs().isEmpty() && blank.artKey() == 0, "sem arte, sem referência");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void resumosDasArtesGravadosNoDisco(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        TvPixelArt first = art(UUID.randomUUID(), "Um", 16, 16, 4);
        TvPixelArt second = art(UUID.randomUUID(), "Dois", 32, 8, 5);
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen());
        tv.setLayout(withArts("resumos", List.of(first, second)));
        List<TvArtRef> refs = tv.artRefs();
        CompoundTag saved = tv.saveWithoutMetadata(registries);
        long[] hashes = saved.getLongArray("art_hashes");
        helper.assertTrue(hashes.length == 2 && hashes[0] == refs.get(0).hash() && hashes[1] == refs.get(1).hash(),
                "os resumos não foram ao disco");
        CompoundTag marked = saved.copy();
        marked.putLongArray("art_hashes", new long[] {42, 43});
        TvBlockEntity loaded = new TvBlockEntity(helper.absolutePos(A), screen());
        loaded.loadWithComponents(marked, registries);
        helper.assertTrue(loaded.artRefs().get(0).hash() == 42 && loaded.artRefs().get(1).hash() == 43,
                "a Tela carregada recalculou os resumos em vez de ler os gravados");
        helper.assertValueEqual(loaded.artRefs().get(1).name(), "Dois", "a referência montada com o nome da arte");
        CompoundTag wrong = saved.copy();
        wrong.putLongArray("art_hashes", new long[] {42});
        TvBlockEntity recalculated = new TvBlockEntity(helper.absolutePos(A), screen());
        recalculated.loadWithComponents(wrong, registries);
        helper.assertValueEqual(recalculated.artRefs(), refs, "resumos que não batem com as artes são recalculados");
        helper.assertTrue(tv.onlyOpCanSetNbt(), "os dados de bloco de um item entram sem ser operador");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pacoteDeArtesVaiPertoUmaVezPorMudanca(GameTestHelper helper) {
        ServerPlayer near = TestPlayers.inLevel(helper);
        near.moveTo(Vec3.atCenterOf(helper.absolutePos(A)).add(0, 0, -3));
        withScreen(helper, near.getUUID(), tv -> {
            TvPixelArt first = art(UUID.randomUUID(), "Um", 128, 128, 3);
            tv.setLayout(withArts("a", List.of(first)));
            List<ServerPlayer> players = List.of(near);
            TvArtSender.visitForTests(tv, players);
            helper.assertValueEqual(TvArtSender.sendsForTests(tv), 1L, "a primeira volta manda");
            helper.assertValueEqual(TvArtSender.payloadForTests(tv).arts(), List.of(first), "o pacote leva a arte");
            TvArtSender.visitForTests(tv, players);
            helper.assertValueEqual(TvArtSender.sendsForTests(tv), 1L, "a mesma revisão não vai de novo");

            long key = tv.artKey();
            tv.setLayout(withArts("b", List.of(first)));
            helper.assertValueEqual(tv.artKey(), key, "só o texto mudou: a chave das artes é a mesma");
            TvArtSender.visitForTests(tv, players);
            helper.assertValueEqual(TvArtSender.sendsForTests(tv), 1L, "mudar só o texto não manda as artes");

            TvPixelArt second = art(UUID.randomUUID(), "Dois", 16, 16, 4);
            tv.setLayout(withArts("b", List.of(first, second)));
            TvArtSender.visitForTests(tv, players);
            helper.assertValueEqual(TvArtSender.sendsForTests(tv), 2L, "uma arte nova manda de novo");
            helper.assertValueEqual(TvArtSender.payloadForTests(tv).arts(), List.of(first, second), "as duas");

            TvArtSender.openForTests(near, tv);
            helper.assertValueEqual(TvArtSender.sendsForTests(tv), 2L, "abrir o menu já com as artes não manda");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pacoteDeArtesNaoVaiAQuemEstaLonge(GameTestHelper helper) {
        ServerPlayer near = TestPlayers.inLevel(helper);
        ServerPlayer far = TestPlayers.inLevel(helper);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(A));
        near.moveTo(center.add(0, 0, -4));
        far.moveTo(center.add(0, 0, -(Config.tvViewDistance() + 20)));
        withScreen(helper, near.getUUID(), tv -> {
            tv.setLayout(withArts("a", List.of(art(UUID.randomUUID(), "Um", 32, 32, 5))));
            List<ServerPlayer> found = TvManager.nearForTests(tv, List.of(near, far));
            helper.assertValueEqual(found, List.of(near), "só o de perto");
            TvArtSender.visitForTests(tv, found);
            helper.assertTrue(TvArtSender.sentToForTests(tv, near.getUUID()), "o de perto recebeu");
            helper.assertFalse(TvArtSender.sentToForTests(tv, far.getUUID()), "o de longe recebeu");

            TvArtSender.visitForTests(tv, List.of());
            helper.assertValueEqual(TvArtSender.sendsForTests(tv), 0L, "sem ninguém perto a Tela não guarda nada");
            TvArtSender.visitForTests(tv, found);
            helper.assertValueEqual(TvArtSender.sendsForTests(tv), 1L, "quem volta para perto recebe de novo");

            TvBlockEntity plain = new TvBlockEntity(helper.absolutePos(A), screen());
            plain.setLayout(TvLayout.of(List.of(text("x"))));
            TvArtSender.visitForTests(plain, found);
            helper.assertValueEqual(TvArtSender.sendsForTests(plain), 0L, "Tela sem arte não manda nada");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chunkMandadoDeNovoReenviaAsArtes(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        helper.setBlock(A.south(), Blocks.STONE);
        helper.setBlock(A, screen());
        TvBlockEntity[] tv = new TvBlockEntity[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem block entity"))
                .thenExecute(() -> {
                    tv[0] = (TvBlockEntity) helper.getBlockEntity(A);
                    tv[0].setLayout(withArts("a", List.of(art(UUID.randomUUID(), "Um", 16, 16, 13))));
                    TvArtSender.visitForTests(tv[0], List.of(player));
                    helper.assertValueEqual(TvArtSender.sendsForTests(tv[0]), 1L, "a primeira volta manda");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    TvArtSender.onChunkSent(new ChunkWatchEvent.Sent(player,
                            helper.getLevel().getChunkAt(tv[0].getBlockPos()), helper.getLevel()));
                    TvArtSender.visitForTests(tv[0], List.of(player));
                    helper.assertValueEqual(TvArtSender.sendsForTests(tv[0]), 2L,
                            "o chunk mandado de novo faz as artes irem de novo");
                    TvArtSender.visitForTests(tv[0], List.of(player));
                    helper.assertValueEqual(TvArtSender.sendsForTests(tv[0]), 2L, "e só uma vez");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aberturaDoMenuMandaAsArtes(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        withScreen(helper, player.getUUID(), tv -> {
            tv.setLayout(withArts("a", List.of(art(UUID.randomUUID(), "Um", 24, 40, 6))));
            TvArtSender.openForTests(player, tv);
            helper.assertValueEqual(TvArtSender.sendsForTests(tv), 1L, "a abertura manda");
            TvArtSender.visitForTests(tv, List.of(player));
            helper.assertValueEqual(TvArtSender.sendsForTests(tv), 1L, "a volta não repete o que a abertura mandou");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void salvarSemAArteGuardaACopiaDaTela(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvPixelArt art = art(UUID.randomUUID(), "Logo", 128, 128, 7);
            helper.assertTrue(TvEditing.applyLayout(player, tv, withArts("a", List.of(art))).ok(), "a primeira");
            TvLayout withoutPixels = withArts("b", List.of(art)).withImages(List.of());
            helper.assertTrue(TvEditing.applyLayout(player, tv, withoutPixels).ok(), "salvar sem a arte");
            helper.assertValueEqual(tv.layout().images(), List.of(art), "a arte da Tela ficou");
            helper.assertValueEqual(tv.layout().pieces().size(), 2, "a Imagem ficou");
            helper.assertValueEqual(tv.layout().pieces().get(0).style().text(), "b", "o resto mudou");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_tv")
    public static void tetoDe131072PixelsPorTela(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            int before = Config.tvMaxImagePixels();
            try {
                helper.assertValueEqual(Config.TV_MAX_IMAGE_PIXELS.getDefault(), 131_072, "o padrão");
                Config.TV_MAX_IMAGE_PIXELS.set(Config.TV_MAX_IMAGE_PIXELS.getDefault());
                List<TvPixelArt> eight = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    eight.add(art(UUID.randomUUID(), "a" + i, 128, 128, 20 + i));
                }
                TvLayout full = withArts("cheio", eight);
                helper.assertTrue(TvEditing.applyLayout(player, tv, full).ok(), "oito artes de 128 × 128 cabem");
                helper.assertTrue(TvLayoutPayload.clientSize(full) <= Config.tvMaxLayoutBytes(),
                        "o update tag de oito artes passa do teto");
                List<TvPixelArt> nine = new ArrayList<>(eight);
                nine.add(art(UUID.randomUUID(), "mais", 8, 8, 30));
                TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv, withArts("demais", nine));
                helper.assertFalse(result.ok(), "aceitou 131.136 pixels");
                helper.assertValueEqual(tv.layout().images().size(), 8, "o layout mudou");
            } finally {
                Config.TV_MAX_IMAGE_PIXELS.set(before);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void arte128IdaEVolta(GameTestHelper helper) {
        TvPixelArt art = art(UUID.randomUUID(), "n".repeat(TvPixelArt.MAX_NAME), 128, 128, 8);
        Tag tag = TvPixelArt.CODEC.encodeStart(NbtOps.INSTANCE, art).getOrThrow();
        helper.assertValueEqual(((CompoundTag) tag).getByteArray("pixels").length, 8192, "128 × 128 em meio byte");
        helper.assertValueEqual(TvPixelArt.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(), art, "pelo NBT");
        CompoundTag wide = ((CompoundTag) tag).copy();
        wide.putInt("w", 129);
        helper.assertTrue(TvPixelArt.CODEC.parse(NbtOps.INSTANCE, wide).result().isEmpty(), "129 não vale");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TvArtsPayload arts = new TvArtsPayload(List.of(art, art(UUID.randomUUID(), "b", 8, 128, 9)));
        TvArtsPayload.STREAM_CODEC.encode(buf, arts);
        helper.assertValueEqual(TvArtsPayload.STREAM_CODEC.decode(buf), arts, "o pacote de artes");
        TvLayout layout = withArts("x", List.of(art));
        TvLayoutPayload.STREAM_CODEC.encode(buf, new TvLayoutPayload(2, layout));
        helper.assertValueEqual(TvLayoutPayload.STREAM_CODEC.decode(buf).layout(), layout, "o pacote de salvar");
        TvLibraryActionPayload save = TvLibraryActionPayload.saveArt(4, art);
        TvLibraryActionPayload.STREAM_CODEC.encode(buf, save);
        helper.assertValueEqual(TvLibraryActionPayload.STREAM_CODEC.decode(buf).art(), art, "o SAVE_ART");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_tv")
    public static void bibliotecaRecusaArteQuePassariaDoPacote(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            player.setPos(helper.absoluteVec(Vec3.atCenterOf(A)));
            BlockPos pos = helper.absolutePos(A);
            helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            helper.assertTrue(player.containerMenu instanceof TvMenu, "o menu não abriu");
            int menu = player.containerMenu.containerId;
            TvLibrary library = TvLibrary.get(helper.getLevel().getServer());
            try {
                TvLibraryActions.setMaxLibraryBytesForTests(4 * 1024);
                TvPixelArt small = art(UUID.randomUUID(), "Pequena", 16, 16, 10);
                helper.assertTrue(TvLibraryActions.handle(player, TvLibraryActionPayload.saveArt(menu, small)),
                        "a pequena cabe");
                TvPixelArt big = art(UUID.randomUUID(), "Grande", 128, 128, 11);
                helper.assertFalse(TvLibraryActions.handle(player, TvLibraryActionPayload.saveArt(menu, big)),
                        "a que passaria do pacote foi salva");
                helper.assertValueEqual(library.arts(player.getUUID()), List.of(small), "a biblioteca mudou");
                TvPixelArt redrawn = art(small.id(), "Pequena", 16, 16, 12);
                helper.assertTrue(TvLibraryActions.handle(player, TvLibraryActionPayload.saveArt(menu, redrawn)),
                        "trocar a de mesmo id dentro do teto vale");
            } finally {
                TvLibraryActions.setMaxLibraryBytesForTests(-1);
            }
        });
    }
}
