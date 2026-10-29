package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.tv.render.TvArtCache;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvCanvases;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A pixel art na Tela, montada direto no block entity no servidor: a peça Imagem (em escala inteira alinhada,
 * esticada e cortada pela caixa) e o fundo em mosaico na Tela 4 × 2, fotografados, cada passo esperando a arte chegar
 * ao cliente pelo pacote de artes (o update tag só a referencia); e a medida da decisão 8 da spec: uma Tela 32 × 32
 * no céu com o mosaico de uma arte 128 × 128 de pixels sorteados em escala 1 (64 cópias da arte, um quadrilátero só
 * com a textura dela repetida), redesenhada algumas vezes, com o tempo de cada redesenho no {@code result.txt}.
 * Depois o jogador vai longe (o chunk da Tela grande sai do cliente), o cliente esquece as artes e, de volta, a arte
 * chega de novo pelo pacote e a Tela redesenha. No fim, o layout da Tela 4 × 2 volta, a parede e a Tela grande saem e
 * as texturas das artes, sem ninguém que as use, também.
 */
final class E2eTvImageSteps {
    private static final int SIZE = 32;
    private static final int LIFT = 24;
    private static final int SOUTH = 40;
    private static final double CAMERA_DISTANCE = 30;
    private static final double FRONT = 13.0 / 16.0;
    private static final int REPAINTS = 3;
    /** Longe o bastante para o chunk da Tela grande sair do cliente com qualquer distância de renderização. */
    private static final int AWAY = 1024;
    private static final float[] LOOK = {0f, 0f};

    private static TvLayout original = TvLayout.EMPTY;
    private static BlockPos base = BlockPos.ZERO;
    private static Vec3 savedPos = Vec3.ZERO;
    private static final float[] savedLook = new float[2];
    private static final StringBuilder MEASURED = new StringBuilder();
    private static int receivedBefore;

    private E2eTvImageSteps() {
    }

    static void imageSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv, Supplier<BlockPos> origin) {
        TvPixelArt logo = logo();
        TvPixelArt tile = tile();
        list.add(new Step("Imagem: guardar o layout da Tela e o lugar do jogador", STEP_TIMEOUT_MS, () -> {
            original = entity(tv.get()).layout();
            savedPos = Minecraft.getInstance().player.position();
            savedLook[0] = Minecraft.getInstance().player.getYRot();
            savedLook[1] = Minecraft.getInstance().player.getXRot();
        }, () -> true, () -> ""));
        list.add(set(tv, "Imagem: alinhada, esticada e cortada", () -> TvLayout.of(List.of(
                image(logo, 4, 4, 56, 56, 3, TvAlign.CENTER, TvPartStyle.VAlign.MIDDLE, 100),
                image(logo, 66, 4, 28, 56, 0, TvAlign.LEFT, TvPartStyle.VAlign.TOP, 100),
                image(logo, 98, 4, 26, 26, 2, TvAlign.RIGHT, TvPartStyle.VAlign.BOTTOM, 100),
                image(logo, 98, 34, 26, 26, 1, TvAlign.LEFT, TvPartStyle.VAlign.TOP, 50)),
                TvTheme.GRAPHITE, TvBackground.DEFAULT, false, List.of(logo))));
        list.add(E2eTvSteps.hideGui(playerId, true));
        list.add(E2eTvSteps.view("tela-imagem", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        list.add(set(tv, "Imagem: fundo em mosaico", () -> TvLayout.of(List.of(card(), image(logo, 38, 12, 52, 40, 2,
                TvAlign.CENTER, TvPartStyle.VAlign.MIDDLE, 100)), TvTheme.GRAPHITE,
                TvBackground.tiled(tile.id(), 2, 60), true, List.of(logo, tile))));
        list.add(E2eTvSteps.view("tela-mosaico", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        TvPixelArt noise = noise();
        list.add(big(playerId, origin, noise));
        list.add(E2eTvSteps.view("tela-mosaico-32x32", playerId, E2eTvImageSteps::eye, E2eTvImageSteps::target));
        list.add(new Step("Imagem: zerar a medida", STEP_TIMEOUT_MS, () -> MEASURED.setLength(0), () -> true,
                () -> ""));
        for (int i = 1; i <= REPAINTS; i++) {
            list.add(repaint(i, noise));
        }
        list.add(new Step("Imagem: redesenho da Tela 32 × 32 com mosaico 128 × 128 em escala 1", STEP_TIMEOUT_MS,
                () -> {
                }, () -> true, () -> "redesenhos " + MEASURED));
        reloadSteps(list, playerId, noise);
        list.add(E2eTvSteps.hideGui(playerId, false));
        list.add(new Step("Imagem: parede e Tela grande fora, layout de volta", STEP_TIMEOUT_MS,
                () -> onServer(server -> {
                    ServerLevel level = server.overworld();
                    level.setBlockAndUpdate(anchor(), Blocks.AIR.defaultBlockState());
                    wall(level, Blocks.AIR.defaultBlockState());
                    ((TvBlockEntity) level.getBlockEntity(tv.get())).setLayout(original);
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    player.teleportTo(level, savedPos.x, savedPos.y, savedPos.z, savedLook[0], savedLook[1]);
                    return null;
                }), () -> {
                    E2eRepairerSteps.look(savedLook);
                    return Minecraft.getInstance().level.getBlockState(anchor()).isAir()
                            && entity(tv.get()).layout().equals(original)
                            && Minecraft.getInstance().player.position().distanceToSqr(savedPos) < 0.01;
                }, () -> "Tela grande e jogador no cliente"));
        list.add(new Step("Imagem: texturas das artes soltas", STEP_TIMEOUT_MS, () -> {
        }, () -> TvCanvases.artTexturesForTests() == 0,
                () -> TvCanvases.artTexturesForTests() + " textura(s) de arte"));
    }

    /**
     * Longe da Tela grande até o chunk dela sair do cliente; o cliente esquece as artes; de volta, a arte chega pelo
     * pacote (o contador do {@link TvArtCache} sobe), entra no layout e a Tela redesenha com o mosaico.
     */
    private static void reloadSteps(List<Step> list, UUID playerId, TvPixelArt noise) {
        list.add(new Step("Imagem: longe, o chunk da Tela grande sai do cliente", STEP_TIMEOUT_MS,
                () -> teleport(playerId, eye().add(AWAY, 0, 0)),
                () -> Minecraft.getInstance().level.getBlockEntity(anchor()) == null, () -> "a Tela grande no cliente"));
        list.add(new Step("Imagem: o cliente esquece as artes", STEP_TIMEOUT_MS, () -> {
            TvArtCache.clear();
            receivedBefore = TvArtCache.receivedForTests;
        }, () -> TvArtCache.sizeForTests() == 0, () -> TvArtCache.sizeForTests() + " arte(s)"));
        list.add(new Step("Imagem: de volta, a arte chega pelo pacote e o mosaico aparece", STEP_TIMEOUT_MS, () -> {
            TvCanvases.paintsForTests = 0;
            teleport(playerId, eye());
        }, () -> {
            E2eRepairerSteps.look(LOOK);
            return Minecraft.getInstance().level.getBlockEntity(anchor()) instanceof TvBlockEntity big
                    && big.layout().image(noise.id()) != null && TvArtCache.receivedForTests > receivedBefore
                    && TvCanvases.paintsForTests >= 1 && stepTicks >= 10;
        }, () -> "pacotes " + (TvArtCache.receivedForTests - receivedBefore) + ", redesenhos "
                + TvCanvases.paintsForTests));
        list.add(E2eTvSteps.view("tela-mosaico-recarregado", playerId, E2eTvImageSteps::eye, E2eTvImageSteps::target));
    }

    /** Leva o jogador (voando) com os olhos em {@code eye}, olhando para o sul. */
    private static void teleport(UUID playerId, Vec3 eye) throws Exception {
        Vec3 feet = eye.subtract(0, E2eRepairerSteps.EYE_HEIGHT, 0);
        onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(server.overworld(), feet.x, feet.y, feet.z, LOOK[0], LOOK[1]);
            return null;
        });
    }

    private static TvBlockEntity entity(BlockPos pos) {
        return (TvBlockEntity) Minecraft.getInstance().level.getBlockEntity(pos);
    }

    /** Aplica o layout no servidor e espera o cliente receber igual. */
    private static Step set(Supplier<BlockPos> tv, String name, Supplier<TvLayout> layout) {
        TvLayout[] applied = new TvLayout[1];
        return new Step(name, STEP_TIMEOUT_MS, () -> onServer(server -> {
            applied[0] = layout.get();
            ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(applied[0]);
            return null;
        }), () -> entity(tv.get()).layout().equals(applied[0]), () -> "layout no cliente");
    }

    /**
     * A parede de pedra 32 × 32 no céu, 40 blocos ao sul da origem da suíte, e a Tela 32 × 32 nela com o mosaico do
     * ruído em escala 1 e um Texto por cima, que muda a cada redesenho medido.
     */
    private static Step big(UUID playerId, Supplier<BlockPos> origin, TvPixelArt noise) {
        return new Step("Imagem: Tela 32 × 32 com o mosaico do ruído 128 × 128", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            base = origin.get().offset(0, LIFT, SOUTH);
            wall(level, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(anchor(), ModBlocks.SIGNAL_SCREEN.get().defaultBlockState()
                    .setValue(TvBlock.FACING, Direction.NORTH));
            TvBlockEntity big = (TvBlockEntity) level.getBlockEntity(anchor());
            big.setOwner(playerId);
            big.setArea(TvArea.grown(SIZE, SIZE, TvArea.Grow.CENTER));
            big.setLayout(noiseLayout(noise, 0));
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(anchor()) instanceof TvBlockEntity big
                && big.area().width() == SIZE && big.layout().images().size() == 1, () -> "Tela grande no cliente");
    }

    /** Troca o texto (a Tela redesenha) e espera o redesenho, guardando o tempo dele. */
    private static Step repaint(int n, TvPixelArt noise) {
        return new Step("Imagem: redesenho " + n + " do mosaico", STEP_TIMEOUT_MS, () -> {
            TvCanvases.paintsForTests = 0;
            TvCanvases.paintNanosForTests = 0;
            TvCanvases.maxPaintNanosForTests = 0;
            onServer(server -> {
                ((TvBlockEntity) server.overworld().getBlockEntity(anchor())).setLayout(noiseLayout(noise, n));
                return null;
            });
        }, () -> {
            E2eRepairerSteps.look(LOOK);
            if (!entity(anchor()).layout().equals(noiseLayout(noise, n)) || TvCanvases.paintsForTests < 1
                    || stepTicks < 10) {
                return false;
            }
            MEASURED.append(String.format(Locale.ROOT, "%s%.3f ms", MEASURED.length() == 0 ? "" : ", ",
                    TvCanvases.maxPaintNanosForTests / 1_000_000.0));
            return true;
        }, () -> TvCanvases.paintsForTests + " redesenho(s), maior "
                + String.format(Locale.ROOT, "%.3f ms", TvCanvases.maxPaintNanosForTests / 1_000_000.0));
    }

    private static TvLayout noiseLayout(TvPixelArt noise, int n) {
        TvPiece label = new TvPiece(TvPieceType.TEXT, 16, 16, 256, 32, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withText("mosaico " + n).withSize(32).withColor(TvColor.theme(TvColor.TEXT)));
        return TvLayout.of(List.of(label), TvTheme.GRAPHITE, TvBackground.tiled(noise.id(), 1, 100), false,
                List.of(noise));
    }

    private static void wall(ServerLevel level, BlockState state) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -SIZE / 2; x < SIZE / 2; x++) {
            for (int y = 0; y < SIZE; y++) {
                level.setBlock(pos.set(base.getX() + x, base.getY() + y, base.getZ()), state, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** O bloco da Tela grande, no centro da área dela ({@code TvArea.Grow.CENTER}), na frente da parede. */
    private static BlockPos anchor() {
        return base.offset(0, (SIZE - 1) / 2, -1);
    }

    private static Vec3 target() {
        return new Vec3(base.getX(), base.getY() + SIZE / 2.0, base.getZ() - 1 + FRONT);
    }

    private static Vec3 eye() {
        return target().add(0, 0, -CAMERA_DISTANCE);
    }

    private static TvPiece image(TvPixelArt art, int x, int y, int w, int h, int scale, TvAlign align,
            TvPartStyle.VAlign valign, int opacity) {
        TvStyle style = TvStyle.DEFAULT.withAlign(align).withOpacity(opacity).withBorder(TvColor.theme(TvColor.MUTED), 1)
                .withPart(TvPartStyle.DEFAULT.withImage(Optional.of(art.id()), scale, valign));
        return new TvPiece(TvPieceType.IMAGE, x, y, w, h, 0, "", Optional.empty(), TvFormat.DEFAULT, style);
    }

    /** Um cartão arredondado do tema no meio, para mostrar as peças por cima do mosaico. */
    private static TvPiece card() {
        return new TvPiece(TvPieceType.SHAPE, 34, 8, 60, 48, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withBackground(TvColor.theme(TvColor.CARD)).withRadius(4));
    }

    /** Um coração 16 × 16 de três cores com contorno, com o fundo transparente. */
    private static TvPixelArt logo() {
        String[] rows = {
                "................",
                "..1111....1111..",
                ".122221..122221.",
                "1223322112222221",
                "1233332222222221",
                "1233322222222221",
                "1232222222222221",
                "1222222222222221",
                ".12222222222221.",
                "..122222222221..",
                "...1222222221...",
                "....12222221....",
                ".....122221.....",
                "......1221......",
                ".......11.......",
                "................"};
        int[] palette = new int[16];
        palette[1] = 0xFF2A0A12;
        palette[2] = 0xFFE0304A;
        palette[3] = 0xFFFFC0C8;
        return fromRows(UUID.nameUUIDFromBytes("e2e-logo".getBytes()), "Coração", rows, palette);
    }

    /** Um ladrilho 8 × 8 xadrez de dois azuis com um ponto claro, para o mosaico. */
    private static TvPixelArt tile() {
        String[] rows = {
                "11112222",
                "11112222",
                "11312222",
                "11112222",
                "22221111",
                "22221111",
                "22221131",
                "22221111"};
        int[] palette = new int[16];
        palette[1] = 0xFF16304F;
        palette[2] = 0xFF1F4470;
        palette[3] = 0xFF8FD3FF;
        return fromRows(UUID.nameUUIDFromBytes("e2e-tile".getBytes()), "Ladrilho", rows, palette);
    }

    /** A arte 128 × 128 (o maior tamanho) de pixels sorteados de 1 a 15. */
    private static TvPixelArt noise() {
        int[] palette = new int[16];
        for (int i = 1; i < 16; i++) {
            palette[i] = 0xFF000000 | (i * 37 & 0xFF) << 16 | (i * 91 & 0xFF) << 8 | (i * 53 & 0xFF);
        }
        byte[] pixels = new byte[128 * 128];
        Random random = new Random(128);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) (1 + random.nextInt(15));
        }
        return new TvPixelArt(UUID.nameUUIDFromBytes("e2e-noise".getBytes()), "Ruído", 128, 128, palette, pixels);
    }

    /** Uma arte pelas linhas: {@code .} e espaço são transparentes, os dígitos são o índice da paleta. */
    private static TvPixelArt fromRows(UUID id, String name, String[] rows, int[] palette) {
        int w = rows[0].length();
        byte[] pixels = new byte[w * rows.length];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < w; x++) {
                char c = rows[y].charAt(x);
                pixels[y * w + x] = (byte) (Character.isDigit(c) ? c - '0' : 0);
            }
        }
        return new TvPixelArt(id, name, w, rows.length, palette, pixels);
    }
}
