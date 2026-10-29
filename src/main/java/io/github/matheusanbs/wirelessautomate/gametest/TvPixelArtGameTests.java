package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvEditing;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryActions;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A pixel art da Tela de Sinal (etapa 4): o layout com imagens e o NBT antigo, a arte inválida largada, o
 * {@code applyLayout} resolvendo as imagens, os tetos {@code tv.maxImagePixels} e {@code tv.maxArts} e as ações da
 * biblioteca.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvPixelArtGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private TvPixelArtGameTests() {
    }

    private static void withScreen(GameTestHelper helper, UUID owner, Consumer<TvBlockEntity> body) {
        helper.setBlock(A.south(), Blocks.STONE);
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

    private static TvMenu opened(GameTestHelper helper, TestPlayers.MenuPlayer player) {
        player.setPos(helper.absoluteVec(Vec3.atCenterOf(A)));
        BlockPos pos = helper.absolutePos(A);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        helper.assertTrue(player.containerMenu instanceof TvMenu, "o menu não abriu");
        return (TvMenu) player.containerMenu;
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

    private static TvPiece image(UUID art, int x, int y, int w, int h, int scale) {
        return new TvPiece(TvPieceType.IMAGE, x, y, w, h, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(Optional.of(art), scale,
                        TvPartStyle.VAlign.TOP)));
    }

    private static TvPiece text(int x) {
        return new TvPiece(TvPieceType.TEXT, x, 0, 20, 8, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withText("t"));
    }

    private static CompoundTag encode(TvLayout layout) {
        return (CompoundTag) TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).getOrThrow();
    }

    private static TvLayout decode(Tag tag) {
        return TvLayout.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutComImagensIdaEVolta(GameTestHelper helper) {
        TvPixelArt small = art(UUID.randomUUID(), "pequena", 9, 9, 1);
        TvPixelArt big = art(UUID.randomUUID(), "grande", 64, 64, 2);
        TvLayout layout = TvLayout.of(List.of(image(small.id(), 4, 4, 40, 30, 3), text(50)), TvTheme.GRAPHITE,
                TvBackground.tiled(big.id(), 4, 60), true, List.of(small, big, art(small.id(), "repetida", 8, 8, 3)));
        helper.assertValueEqual(layout.images(), List.of(small, big), "a arte de id repetido ficou de fora");
        helper.assertTrue(layout.image(big.id()) == big, "a arte pelo id");
        helper.assertValueEqual(layout.imagePixels(), 81 + 64 * 64, "pixels somados");

        CompoundTag tag = encode(layout);
        ListTag images = tag.getList("images", Tag.TAG_COMPOUND);
        helper.assertValueEqual(images.size(), 2, "artes no NBT");
        helper.assertValueEqual(images.getCompound(0).getByteArray("pixels").length, 41, "9 × 9 em meio byte");
        helper.assertValueEqual(images.getCompound(1).getByteArray("pixels").length, 2048, "64 × 64 em meio byte");
        helper.assertValueEqual(images.getCompound(0).getIntArray("palette").length, 16, "a paleta");
        TvLayout read = decode(tag);
        helper.assertTrue(read.equals(layout), "o layout lido é outro");
        helper.assertValueEqual(read.background().opacity(), 60, "a opacidade do mosaico");
        helper.assertValueEqual(read.pieces().get(0).style().part().scale(), 3, "a escala da Imagem");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TvLayoutPayload.STREAM_CODEC.encode(buf, new TvLayoutPayload(3, layout));
        helper.assertTrue(TvLayoutPayload.STREAM_CODEC.decode(buf).layout().equals(layout), "o payload do layout");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nbtAntigoLeIgualESemChavesNovas(GameTestHelper helper) {
        TvLayout old = TvLayout.of(List.of(text(0)), TvTheme.AMBER,
                new TvBackground(TvBackground.Kind.GRADIENT, 0xFF000000, 0xFF112233), false);
        CompoundTag tag = encode(old);
        helper.assertFalse(tag.contains("images"), "um layout sem imagens grava a chave images");
        CompoundTag background = tag.getCompound("background");
        helper.assertFalse(background.contains("image") || background.contains("scale")
                || background.contains("opacity"), "o fundo grava os campos do mosaico: " + background);
        CompoundTag style = tag.getList("pieces", Tag.TAG_COMPOUND).getCompound(0).getCompound("style");
        helper.assertFalse(style.contains("image") || style.contains("scale") || style.contains("valign"),
                "a peça grava os campos da Imagem: " + style);

        CompoundTag stage2 = new CompoundTag();
        CompoundTag gradient = new CompoundTag();
        gradient.putString("kind", "gradient");
        gradient.putInt("top", 0xFF000000);
        gradient.putInt("bottom", 0xFF112233);
        stage2.put("background", gradient);
        stage2.put("pieces", tag.getList("pieces", Tag.TAG_COMPOUND));
        stage2.put("theme", tag.getCompound("theme"));
        TvLayout read = decode(stage2);
        helper.assertTrue(read.equals(old), "o NBT da etapa 2 lê diferente: " + read);
        helper.assertTrue(read.images().isEmpty() && read.background().image().isEmpty()
                && read.background().scale() == 2 && read.background().opacity() == 100, "padrões do mosaico");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void arteInvalidaELargadaSemDerrubarAsOutras(GameTestHelper helper) {
        TvPixelArt valid = art(UUID.randomUUID(), "boa", 16, 8, 4);
        CompoundTag good = (CompoundTag) TvPixelArt.CODEC.encodeStart(NbtOps.INSTANCE, valid).getOrThrow();
        CompoundTag wide = good.copy();
        wide.putInt("w", 100);
        CompoundTag shortPalette = good.copy();
        shortPalette.putIntArray("palette", new int[15]);
        CompoundTag fewPixels = good.copy();
        fewPixels.putByteArray("pixels", new byte[3]);
        CompoundTag tiny = good.copy();
        tiny.putInt("h", 4);
        ListTag images = new ListTag();
        images.add(wide);
        images.add(good);
        images.add(shortPalette);
        images.add(fewPixels);
        images.add(tiny);
        CompoundTag tag = encode(TvLayout.EMPTY);
        tag.put("images", images);
        helper.assertValueEqual(decode(tag).images(), List.of(valid), "só a arte boa fica");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void salvarLargaArteOrfaEImagemComArteQueNaoExisteFicaSemArte(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvPixelArt used = art(UUID.randomUUID(), "usada", 8, 8, 5);
            TvPixelArt orphan = art(UUID.randomUUID(), "órfã", 8, 8, 6);
            TvPiece blank = image(used.id(), 0, 16, 16, 16, 1);
            blank = blank.withStyle(blank.style().withPart(blank.style().part().withImage(Optional.empty(), 1,
                    TvPartStyle.VAlign.MIDDLE)));
            TvLayout layout = TvLayout.of(List.of(image(used.id(), 0, 0, 16, 16, 1),
                    image(UUID.randomUUID(), 20, 0, 16, 16, 1), text(40), blank), TvTheme.DEFAULT,
                    TvBackground.tiled(UUID.randomUUID(), 2, 100), false, List.of(used, orphan));
            TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv, layout);
            helper.assertTrue(result.ok(), "recusou");
            TvLayout saved = tv.layout();
            helper.assertValueEqual(saved.images(), List.of(used), "a arte que ninguém usa saiu");
            helper.assertValueEqual(saved.pieces().size(), 4, "uma peça saiu");
            helper.assertValueEqual(saved.pieces().get(0).style().part().image(), Optional.of(used.id()),
                    "a Imagem com arte ficou");
            TvPiece lost = saved.pieces().get(1);
            helper.assertTrue(lost.type() == TvPieceType.IMAGE && lost.style().part().image().isEmpty()
                    && lost.width() == 16, "a Imagem com arte que não existe virou a Imagem sem arte, no lugar dela");
            TvPiece kept = saved.pieces().get(3);
            helper.assertTrue(kept.type() == TvPieceType.IMAGE && kept.style().part().image().isEmpty(),
                    "a Imagem sem arte (nascida da paleta) ficou, sem arte");
            helper.assertValueEqual(saved.background(), TvBackground.DEFAULT, "o mosaico sem arte volta ao tema");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_tv")
    public static void layoutQueApontaMaisArtesQueOTetoERecusado(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            int before = Config.tvMaxElements();
            try {
                Config.TV_MAX_ELEMENTS.set(TvLayout.HARD_CAP);
                List<TvPiece> pieces = new ArrayList<>();
                for (int i = 0; i <= TvLayout.MAX_IMAGES; i++) {
                    pieces.add(image(UUID.randomUUID(), 0, 0, 8, 8, 1));
                }
                TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv, TvLayout.of(pieces));
                helper.assertFalse(result.ok(), "aceitou " + pieces.size() + " artes apontadas");
                helper.assertTrue(tv.layout().pieces().isEmpty(), "o layout mudou");
            } finally {
                Config.TV_MAX_ELEMENTS.set(before);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_tv")
    public static void tetoDePixelsDaTela(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            int before = Config.tvMaxImagePixels();
            try {
                Config.TV_MAX_IMAGE_PIXELS.set(128);
                List<TvPixelArt> arts = new ArrayList<>();
                List<TvPiece> pieces = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    TvPixelArt art = art(UUID.randomUUID(), "a" + i, 8, 8, 10 + i);
                    arts.add(art);
                    pieces.add(image(art.id(), i * 10, 0, 8, 8, 1));
                }
                TvLayout two = TvLayout.of(pieces.subList(0, 2), TvTheme.DEFAULT, TvBackground.DEFAULT, false,
                        arts.subList(0, 2));
                helper.assertTrue(TvEditing.applyLayout(player, tv, two).ok(), "128 pixels no teto de 128");
                TvLayout three = TvLayout.of(pieces, TvTheme.DEFAULT, TvBackground.DEFAULT, false, arts);
                TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv, three);
                helper.assertFalse(result.ok(), "aceitou 192 pixels no teto de 128");
                helper.assertValueEqual(tv.layout().images().size(), 2, "o layout mudou");
                TvLayout unusedThird = TvLayout.of(pieces.subList(0, 2), TvTheme.DEFAULT, TvBackground.DEFAULT,
                        false, arts);
                helper.assertTrue(TvEditing.applyLayout(player, tv, unusedThird).ok(),
                        "a arte que ninguém usa não conta no teto");
            } finally {
                Config.TV_MAX_IMAGE_PIXELS.set(before);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_tv")
    public static void tetoDeArtesNaBiblioteca(GameTestHelper helper) {
        int before = Config.tvMaxArts();
        try {
            Config.TV_MAX_ARTS.set(2);
            TvLibrary library = new TvLibrary();
            UUID id = UUID.randomUUID();
            TvPixelArt a = art(UUID.randomUUID(), "A", 8, 8, 20);
            helper.assertValueEqual(library.saveArt(id, a), TvLibrary.Result.OK, "A");
            helper.assertValueEqual(library.saveArt(id, art(UUID.randomUUID(), "B", 64, 64, 21)),
                    TvLibrary.Result.OK, "B");
            helper.assertValueEqual(library.saveArt(id, art(UUID.randomUUID(), "C", 8, 8, 22)),
                    TvLibrary.Result.FULL, "C");
            TvPixelArt redrawn = art(a.id(), "A de novo", 12, 10, 23);
            helper.assertValueEqual(library.saveArt(id, redrawn), TvLibrary.Result.OK, "trocar pelo id no teto");
            helper.assertValueEqual(library.arts(id).size(), 2, "artes");
            helper.assertValueEqual(library.arts(id).get(0), redrawn, "a trocada fica no mesmo lugar");
            helper.assertValueEqual(library.saveArt(id, art(UUID.randomUUID(), "  ", 8, 8, 24)),
                    TvLibrary.Result.INVALID, "nome vazio");
            helper.succeed();
        } finally {
            Config.TV_MAX_ARTS.set(before);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void salvarRenomearERemoverArtePeloMenu(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            TvLibrary library = TvLibrary.get(helper.getLevel().getServer());
            UUID owner = player.getUUID();
            TvPixelArt art = art(UUID.randomUUID(), "Logo", 32, 16, 30);
            helper.assertTrue(TvLibraryActions.handle(player, TvLibraryActionPayload.saveArt(menu.containerId, art)),
                    "salvar a arte manda a biblioteca");
            helper.assertValueEqual(library.arts(owner), List.of(art), "salva");
            TvPixelArt edited = art(art.id(), "Logo", 32, 16, 31);
            TvLibraryActions.handle(player, TvLibraryActionPayload.saveArt(menu.containerId, edited));
            helper.assertValueEqual(library.arts(owner), List.of(edited), "trocada pelo id");

            TvLibraryActions.handle(player, TvLibraryActionPayload.renameArt(menu.containerId, art.id(), "Marca"));
            helper.assertValueEqual(library.arts(owner).get(0).name(), "Marca", "renomeada");
            helper.assertTrue(java.util.Arrays.equals(library.arts(owner).get(0).pixels(), edited.pixels()),
                    "renomear mudou os pixels");
            TvLibraryPayload sent = TvLibraryActions.payload(player, menu.containerId, library);
            helper.assertValueEqual(sent.arts().size(), 1, "o payload leva as artes");

            helper.assertFalse(TvLibraryActions.handle(player, new TvLibraryActionPayload(menu.containerId,
                    TvLibraryActionPayload.Action.REMOVE_ART, TvLibraryActionPayload.Kind.COMPONENT, "não é um id", "",
                    new byte[0])), "remover com id inválido");
            helper.assertTrue(TvLibraryActions.handle(player, TvLibraryActionPayload.removeArt(menu.containerId,
                    art.id())), "remover");
            helper.assertTrue(library.arts(owner).isEmpty(), "não removeu");
            helper.assertFalse(TvLibraryActions.handle(player, TvLibraryActionPayload.removeArt(menu.containerId,
                    art.id())), "remover o que não existe não manda nada");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bibliotecaComArtesIdaEVoltaECabeNoPacote(GameTestHelper helper) {
        TvLibrary library = new TvLibrary();
        UUID id = UUID.randomUUID();
        TvPixelArt a = art(UUID.randomUUID(), "A", 8, 8, 40);
        TvPixelArt b = art(UUID.randomUUID(), "B", 63, 17, 41);
        library.saveArt(id, a);
        library.saveArt(id, b);
        CompoundTag tag = library.save(new CompoundTag(), helper.getLevel().registryAccess());
        TvLibrary loaded = TvLibrary.load(tag, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.arts(id), List.of(a, b), "artes");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TvLibraryPayload payload = new TvLibraryPayload(5, List.of(), List.of(), List.of(a, b));
        TvLibraryPayload.STREAM_CODEC.encode(buf, payload);
        helper.assertValueEqual(TvLibraryPayload.STREAM_CODEC.decode(buf), payload, "payload com artes");

        int maxArts = (Integer) Config.TV_MAX_ARTS.getSpec().getRange().getMax();
        int maxThemes = (Integer) Config.TV_MAX_THEMES.getSpec().getRange().getMax();
        List<TvPixelArt> arts = new ArrayList<>();
        for (int i = 0; i < maxArts; i++) {
            arts.add(art(UUID.randomUUID(), "n".repeat(TvPixelArt.MAX_NAME), 128, 128, i));
        }
        List<TvTheme> themes = new ArrayList<>();
        for (int i = 0; i < maxThemes; i++) {
            themes.add(new TvTheme("t".repeat(TvTheme.MAX_NAME), 1, 2, 3, 4, 5, 6));
        }
        int full = new TvLibraryPayload(0, List.of(), themes, arts).encodedSize()
                + TvLibrary.MAX_COMPONENT_BYTES_PER_PLAYER;
        helper.assertTrue(full < TvLibraryPayload.MAX_BYTES,
                "artes, temas e componentes no teto não cabem no pacote: " + full);
        helper.succeed();
    }
}
