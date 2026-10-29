package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.packet.TvArtsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvArtRef;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvCodes;
import io.github.matheusanbs.wirelessautomate.tv.TvEditing;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryActions;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
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
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A pixel art animada (Task 5 da etapa 4): os quadros no NBT e no payload, a arte parada gravada como antes, salvar
 * na biblioteca e na Tela, o teto de pixels da Tela somando os quadros e o código legível e o JSON com quadros, no
 * formato dos guias de IA.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvPixelAnimGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private TvPixelAnimGameTests() {
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

    /** Uma arte de {@code frames} quadros de pixels sorteados (a semente decide). */
    private static TvPixelArt art(UUID id, int w, int h, int frames, int fps, TvPixels.Mode mode, long seed) {
        int[] palette = new int[16];
        for (int i = 1; i < 16; i++) {
            palette[i] = 0xFF000000 | i * 0x0F0F0F;
        }
        byte[] pixels = new byte[w * h * frames];
        Random random = new Random(seed);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) random.nextInt(16);
        }
        return new TvPixelArt(id, "anima", w, h, palette, pixels, frames, fps, mode);
    }

    private static TvPiece image(UUID art) {
        return new TvPiece(TvPieceType.IMAGE, 0, 0, 16, 16, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(Optional.of(art), 1, TvPartStyle.VAlign.TOP)));
    }

    private static TvLayout withArt(TvPixelArt art) {
        return TvLayout.of(List.of(image(art.id())), TvTheme.DEFAULT, TvBackground.DEFAULT, false, List.of(art));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void quadrosNoNbtENoPayloadEArteParadaComoAntes(GameTestHelper helper) {
        TvPixelArt anim = art(UUID.randomUUID(), 12, 9, 5, 11, TvPixels.Mode.PINGPONG, 1);
        CompoundTag tag = (CompoundTag) TvPixelArt.CODEC.encodeStart(NbtOps.INSTANCE, anim).getOrThrow();
        helper.assertValueEqual(tag.getInt("frames"), 5, "os quadros");
        helper.assertValueEqual(tag.getInt("fps"), 11, "a velocidade");
        helper.assertValueEqual(tag.getInt("mode"), 1, "o modo");
        helper.assertValueEqual(tag.getByteArray("pixels").length, 12 * 9 * 5 / 2, "os cinco quadros em meio byte");
        helper.assertTrue(anim.equals(TvPixelArt.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow()), "a arte mudou");

        TvPixelArt still = art(UUID.randomUUID(), 12, 9, 1, 15, TvPixels.Mode.PINGPONG, 2);
        helper.assertValueEqual(still.fps(), TvPixels.DEFAULT_FPS, "um quadro fica com a velocidade padrão");
        CompoundTag stillTag = (CompoundTag) TvPixelArt.CODEC.encodeStart(NbtOps.INSTANCE, still).getOrThrow();
        helper.assertFalse(stillTag.contains("frames") || stillTag.contains("fps") || stillTag.contains("mode"),
                "a arte parada grava as chaves novas: " + stillTag);
        helper.assertValueEqual(still.contentHash(), TvPixels.hash(12, 9, still.palette(), still.pixels()),
                "o resumo da arte parada é o de antes");
        helper.assertTrue(anim.contentHash() != art(anim.id(), 12, 9, 5, 10, TvPixels.Mode.PINGPONG, 1).contentHash(),
                "a velocidade entra no resumo");
        helper.assertValueEqual(TvArtRef.of(anim).hash(), anim.contentHash(), "a referência leva o resumo inteiro");

        CompoundTag broken = tag.copy();
        broken.putInt("frames", 65);
        helper.assertTrue(TvPixelArt.CODEC.parse(NbtOps.INSTANCE, broken).result().isEmpty(), "65 quadros leu");
        broken.putInt("frames", 4);
        helper.assertTrue(TvPixelArt.CODEC.parse(NbtOps.INSTANCE, broken).result().isEmpty(),
                "quadros que não batem com os pixels leu");

        TvLayout layout = withArt(anim);
        helper.assertValueEqual(layout.imagePixels(), 12 * 9 * 5, "o teto conta os quadros");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TvArtsPayload.STREAM_CODEC.encode(buf, new TvArtsPayload(layout.images()));
        helper.assertTrue(TvArtsPayload.STREAM_CODEC.decode(buf).arts().equals(List.of(anim)),
                "o pacote de artes leva os quadros");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void arteGravadaAntesLeIgual(GameTestHelper helper) {
        TvPixelArt still = art(UUID.randomUUID(), 8, 8, 1, 6, TvPixels.Mode.LOOP, 3);
        CompoundTag old = new CompoundTag();
        old.putUUID("id", still.id());
        old.putString("name", still.name());
        old.putInt("w", 8);
        old.putInt("h", 8);
        old.putIntArray("palette", still.palette());
        old.putByteArray("pixels", TvPixels.pack(still.pixels()));
        TvPixelArt read = TvPixelArt.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(read.equals(still), "a arte gravada antes dos quadros leu diferente");
        helper.assertValueEqual(read.frames(), 1, "um quadro");
        helper.assertFalse(read.animated(), "parada");
        TvLayout layout = withArt(still);
        CompoundTag tag = (CompoundTag) TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).getOrThrow();
        helper.assertTrue(layout.equals(TvLayout.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow()),
                "o layout com a arte parada mudou");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void salvarArteAnimadaNaBibliotecaENaTela(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            player.setPos(helper.absoluteVec(Vec3.atCenterOf(A)));
            BlockPos pos = helper.absolutePos(A);
            helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            helper.assertTrue(player.containerMenu instanceof TvMenu, "o menu não abriu");
            TvMenu menu = (TvMenu) player.containerMenu;
            TvPixelArt anim = art(UUID.randomUUID(), 16, 16, 8, 12, TvPixels.Mode.LOOP, 4);
            helper.assertTrue(TvLibraryActions.handle(player, TvLibraryActionPayload.saveArt(menu.containerId, anim)),
                    "salvar a arte animada");
            List<TvPixelArt> arts = TvLibrary.get(helper.getLevel().getServer()).arts(player.getUUID());
            helper.assertTrue(arts.contains(anim), "a biblioteca não guardou os quadros");

            TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv, withArt(anim));
            helper.assertTrue(result.ok(), "a Tela recusou a arte animada");
            TvPixelArt placed = tv.layout().image(anim.id());
            helper.assertTrue(placed != null && placed.frames() == 8 && placed.fps() == 12,
                    "a Tela não guardou os quadros");
            helper.assertTrue(Arrays.equals(placed.pixels(), anim.pixels()), "os pixels dos quadros mudaram");
            helper.assertValueEqual(tv.artRefs().get(0).hash(), anim.contentHash(), "a referência da Tela");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_tv")
    public static void tetoDePixelsDaTelaSomaOsQuadros(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            int before = Config.tvMaxImagePixels();
            try {
                Config.TV_MAX_IMAGE_PIXELS.set(8 * 8 * 3);
                TvPixelArt three = art(UUID.randomUUID(), 8, 8, 3, 6, TvPixels.Mode.LOOP, 5);
                helper.assertTrue(TvEditing.applyLayout(player, tv, withArt(three)).ok(), "três quadros no teto");
                TvPixelArt four = art(UUID.randomUUID(), 8, 8, 4, 6, TvPixels.Mode.LOOP, 6);
                helper.assertFalse(TvEditing.applyLayout(player, tv, withArt(four)).ok(),
                        "quatro quadros de 8 × 8 passaram de 192 pixels");
                helper.assertTrue(tv.layout().image(three.id()) != null, "o layout mudou com a recusa");
            } finally {
                Config.TV_MAX_IMAGE_PIXELS.set(before);
            }
        });
    }

    /**
     * O caso do dono: um céu de 128 × 128 com 6 quadros e 15 cores colado como código legível vai à biblioteca e à
     * Tela, numa Imagem que ocupa a Tela inteira e no mosaico, com o teto no padrão (o {@code run/gametest} pode
     * guardar um valor antigo da config, então o teste o põe no padrão).
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_tv")
    public static void ceuDe128ComSeisQuadrosVaiParaATela(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            int before = Config.tvMaxImagePixels();
            try {
                Config.TV_MAX_IMAGE_PIXELS.set(Config.TV_MAX_IMAGE_PIXELS.getDefault());
                helper.assertValueEqual(Config.tvMaxImagePixels(), 131_072, "o teto padrão");
                TvCodes.Read<TvPixelArt> read = TvCodes.readArt(TvCodeExamples.nightSky());
                helper.assertTrue(read.value() != null, "o céu não leu: " + read.error() + " "
                        + Arrays.toString(read.args()));
                TvPixelArt sky = read.value();
                helper.assertValueEqual(sky.pixelCount(), 98_304, "128 × 128 × 6");
                player.setPos(helper.absoluteVec(Vec3.atCenterOf(A)));
                BlockPos pos = helper.absolutePos(A);
                helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
                TvMenu menu = (TvMenu) player.containerMenu;
                helper.assertTrue(TvLibraryActions.handle(player,
                        TvLibraryActionPayload.saveArt(menu.containerId, sky)), "a biblioteca recusou o céu");
                TvPiece full = new TvPiece(TvPieceType.IMAGE, 0, 0, 32, 32, 0, "", Optional.empty(),
                        TvFormat.DEFAULT, TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(Optional.of(sky.id()),
                        0, TvPartStyle.VAlign.TOP)));
                TvLayout layout = TvLayout.of(List.of(full), TvTheme.DEFAULT, TvBackground.tiled(sky.id(), 1, 50),
                        false, List.of(sky));
                helper.assertTrue(TvEditing.applyLayout(player, tv, layout).ok(), "a Tela recusou o céu");
                helper.assertValueEqual(tv.layout().image(sky.id()).frames(), 6, "os quadros na Tela");
                helper.assertTrue(new TvArtsPayload(tv.layout().images()).encodedSize() < TvArtsPayload.MAX_BYTES,
                        "o pacote de artes não cabe");
            } finally {
                Config.TV_MAX_IMAGE_PIXELS.set(before);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void codigoDaArteAnimadaNoFormatoDosGuias(GameTestHelper helper) {
        TvCodes.Read<TvPixelArt> example = TvCodes.readArt(TvCodeExamples.ART_ANIMATED);
        helper.assertTrue(example.value() != null, "o exemplo do guia não leu: " + example.error());
        TvPixelArt art = example.value();
        helper.assertValueEqual(art.frames(), 2, "dois quadros");
        helper.assertValueEqual(art.fps(), 4, "a velocidade");
        helper.assertValueEqual(art.name(), "Sinal piscando", "o nome");
        for (boolean english : new boolean[] {false, true}) {
            String text = TvCodes.artText(art, english);
            helper.assertTrue(text.contains(english ? "frames: 2" : "quadros: 2"), "o texto sem os quadros: " + text);
            TvPixelArt back = TvCodes.readArt(text).value();
            helper.assertTrue(back != null && back.copy(art.id(), art.name()).equals(art), "o texto não voltou igual");
        }
        TvPixelArt compressed = TvCodes.readArt(TvCodes.art(art)).value();
        helper.assertTrue(art.equals(compressed), "o código comprimido não voltou igual");

        TvCodes.Read<TvCodes.Layout> layout = TvCodes.readLayout(TvCodeExamples.LAYOUT_ANIMATED);
        helper.assertTrue(layout.value() != null, "o layout com quadros não leu: " + layout.error());
        TvPixelArt inJson = layout.value().layout().images().get(0);
        helper.assertValueEqual(inJson.frames(), 3, "os quadros do JSON");
        helper.assertValueEqual(inJson.fps(), 3, "a velocidade do JSON");
        helper.assertValueEqual(inJson.mode(), TvPixels.Mode.PINGPONG, "o modo do JSON");
        String exported = TvCodes.layoutText(layout.value().layout(), 64, 32, false, false);
        helper.assertTrue(exported != null && exported.contains("\"quadros\"") && exported.contains("\"vaivem\""),
                "o JSON exportado sem os quadros: " + exported);
        TvPixelArt again = TvCodes.readLayout(exported).value().layout().images().get(0);
        helper.assertTrue(again.equals(inJson), "o JSON exportado não voltou igual");

        TvCodes.Read<TvCodes.Layout> bad = TvCodes.readLayout(TvCodeExamples.LAYOUT_ANIMATED
                .replace("\"........\", \"........\", \"........\", \"...11...\"",
                        "\"........\", \"........\", \"...11...\""));
        helper.assertValueEqual(bad.error(), "code.layout.art.frame_rows", "o quadro com uma fileira a menos");
        helper.assertValueEqual(bad.args()[1], 3, "diz o quadro");
        helper.succeed();
    }
}
