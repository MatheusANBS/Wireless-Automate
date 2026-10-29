package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageDecoder;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageDisk;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageReceiver;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageUploader;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvPictureTextures;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.github.matheusanbs.wirelessautomate.tv.TvImageStore;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * As imagens enviadas no mundo real (etapa 4, Task 4a): uma foto gerada aqui (degradê, sol, ondas e listras finas de
 * 1 px, para o filtro aparecer) sobe pelo envio em partes do cliente, entra numa Imagem esticada e numa em escala 1 da
 * Tela 4 × 2, e um ladrilho guardado direto no armazém do servidor (que o cliente não tem) chega pela entrega e vira o
 * mosaico do fundo. As fotos: {@code tela-imagem-foto} de perto (a Imagem em escala 1 ampliada, com o pixel duro) e
 * {@code tela-imagem-foto-reduzida} de longe (a esticada reduzida, lisa pelos mipmaps). As medidas vão ao
 * {@code result.txt}: o tempo de decodificar um PNG e um JPG de 1024, a memória de vídeo de uma imagem de 1024 e o
 * tamanho de uma foto típica em JPG. No fim, o layout volta e as texturas das imagens saem.
 */
final class E2eTvPictureSteps {
    private static final int PHOTO = 512;
    private static final double FAR = 7;
    private static TvLayout original = TvLayout.EMPTY;
    private static byte[] photo = new byte[0];
    private static String photoHash = "";
    private static String tileHash = "";
    private static final String[] OUTCOME = {""};
    private static int receivedBefore;
    private static String measured = "";
    private static CompletableFuture<Boolean> disk = CompletableFuture.completedFuture(false);

    private E2eTvPictureSteps() {
    }

    static void pictureSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv) {
        list.add(new Step("Imagem enviada: medir a decodificação de PNG e JPG de 1024", STEP_TIMEOUT_MS,
                E2eTvPictureSteps::measure, () -> !measured.isEmpty(), () -> measured));
        list.add(new Step("Imagem enviada: enviar a foto pelo cliente, em partes", STEP_TIMEOUT_MS, () -> {
            photo = encode(picture(PHOTO, PHOTO), "png");
            photoHash = TvImageHash.of(photo);
            OUTCOME[0] = "";
            disk = CompletableFuture.completedFuture(false);
            original = entity(tv.get()).layout();
            TvImageUploader.upload(photo, "Foto do e2e", outcome -> OUTCOME[0] = outcome.ok() ? "ok"
                    : String.valueOf(outcome.refusal()));
        }, () -> "ok".equals(OUTCOME[0]) && onServer(server -> TvImageStore.get(server).contains(photoHash))
                && onDisk(photoHash),
                () -> "resposta " + OUTCOME[0] + ", " + photo.length + " bytes"));
        list.add(new Step("Imagem enviada: um ladrilho só no servidor, que vem pela entrega", STEP_TIMEOUT_MS, () -> {
            byte[] tile = encode(tile(), "png");
            tileHash = TvImageHash.of(tile);
            receivedBefore = TvImageReceiver.receivedForTests;
            onServer(server -> TvImageStore.get(server).putForTests(tile, playerId));
            onServer(server -> {
                TvImageStore store = TvImageStore.get(server);
                ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(layout(store.picture(photoHash),
                        store.picture(tileHash)));
                return null;
            });
        }, () -> TvImageReceiver.receivedForTests > receivedBefore && TvPictureTextures.readyForTests(photoHash)
                && TvPictureTextures.readyForTests(tileHash) && stepTicks >= 10,
                () -> "entregues " + (TvImageReceiver.receivedForTests - receivedBefore) + ", foto pronta "
                        + TvPictureTextures.readyForTests(photoHash) + ", ladrilho pronto "
                        + TvPictureTextures.readyForTests(tileHash)));
        list.add(E2eTvSteps.hideGui(playerId, true));
        list.add(E2eTvSteps.view("tela-imagem-foto", playerId, () -> E2eTvSteps.worldTarget().add(0, 0, -2.2),
                E2eTvSteps::worldTarget));
        list.add(E2eTvSteps.view("tela-imagem-foto-reduzida", playerId,
                () -> E2eTvSteps.worldTarget().add(0, 0, -FAR), E2eTvSteps::worldTarget));
        list.add(E2eTvSteps.hideGui(playerId, false));
        list.add(new Step("Imagem enviada: layout de volta e as texturas soltas", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(original);
            return null;
        }), () -> entity(tv.get()).layout().equals(original) && TvPictureTextures.readyForTests() == 0,
                () -> TvPictureTextures.readyForTests() + " textura(s) de imagem"));
    }

    /** Se a imagem já está no cache em disco: pergunta à thread do disco e lê a resposta num tick seguinte. */
    private static boolean onDisk(String hash) {
        if (!disk.isDone()) {
            return false;
        }
        if (disk.join()) {
            return true;
        }
        disk = TvImageDisk.hasForTests(hash);
        return false;
    }

    private static TvBlockEntity entity(BlockPos pos) {
        return (TvBlockEntity) Minecraft.getInstance().level.getBlockEntity(pos);
    }

    /**
     * A foto à esquerda esticada na caixa de 64 × 64 (de 512 px: reduzida), a mesma em escala 1 à direita (o meio dela,
     * ampliado quando a Tela aparece grande) e o ladrilho em mosaico no fundo, com um texto.
     */
    private static TvLayout layout(TvPicture photo, TvPicture tile) {
        TvPiece stretched = image(photo.id(), 0, 0, 64, 64, 0);
        TvPiece natural = image(photo.id(), 66, 2, 60, 46, 1);
        TvPiece label = new TvPiece(TvPieceType.TEXT, 66, 50, 60, 12, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withText("foto 512").withSize(10).withColor(TvColor.theme(TvColor.TEXT)));
        return TvLayout.of(List.of(stretched, natural, label), TvTheme.GRAPHITE,
                new TvBackground(TvBackground.Kind.TILED, 0, 0, Optional.of(tile.id()), 1, 70), false, List.of(),
                List.of(photo, tile));
    }

    private static TvPiece image(UUID id, int x, int y, int w, int h, int scale) {
        TvStyle style = TvStyle.DEFAULT.withAlign(TvAlign.CENTER).withBorder(TvColor.theme(TvColor.MUTED), 1)
                .withPart(TvPartStyle.DEFAULT.withImage(Optional.of(id), scale, TvPartStyle.VAlign.MIDDLE));
        return new TvPiece(TvPieceType.IMAGE, x, y, w, h, 0, "", Optional.empty(), TvFormat.DEFAULT, style);
    }

    /**
     * Uma foto sintética: céu em degradê, um sol com borda suave, ondas, o quinto de baixo com listras de 1 px (o que
     * serrilha sem mipmap) e um xadrez fino com uma diagonal no miolo, para o filtro aparecer nas fotos.
     */
    private static BufferedImage picture(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double u = x / (double) w;
                double v = y / (double) h;
                int r = (int) (40 + 180 * v);
                int g = (int) (90 + 100 * (1 - v));
                int b = (int) (200 - 120 * v);
                double sun = Math.hypot(u - 0.7, v - 0.3);
                if (sun < 0.14) {
                    double edge = Math.min(1, (0.14 - sun) / 0.02);
                    r = (int) (r + (255 - r) * edge);
                    g = (int) (g + (210 - g) * edge);
                    b = (int) (b + (90 - b) * edge);
                }
                double wave = 0.62 + 0.04 * Math.sin(u * 18);
                if (v > wave) {
                    r = 20;
                    g = (int) (70 + 60 * Math.sin(u * 40 + v * 30) * 0.5);
                    b = 110;
                }
                if (v > 0.8) {
                    boolean line = ((x + y / 4) & 3) < 1;
                    r = line ? 240 : 30;
                    g = line ? 240 : 30;
                    b = line ? 240 : 40;
                }
                if (Math.abs(u - 0.5) < 0.07 && Math.abs(v - 0.5) < 0.06) {
                    // O miolo, que a Imagem em escala 1 mostra ampliado: xadrez de 2 px e uma diagonal de 1 px.
                    boolean light = ((x >> 1) + (y >> 1) & 1) == 0;
                    r = light ? 250 : 200;
                    g = light ? 230 : 40;
                    b = light ? 120 : 60;
                    if (x - y == 0 || x - y == 1) {
                        r = 20;
                        g = 20;
                        b = 30;
                    }
                }
                image.setRGB(x, y, r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    /** Um ladrilho 32 × 32 de dois azuis com uma cruz clara, para o mosaico. */
    private static BufferedImage tile() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                boolean cross = x == 15 || x == 16 || y == 15 || y == 16;
                boolean dark = (x < 16) == (y < 16);
                image.setRGB(x, y, cross ? 0xFF7FB8E8 : dark ? 0xFF14283F : 0xFF1C3A5C);
            }
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    /** Decodifica um PNG e um JPG de 1024 aqui mesmo (mede o leitor, que no jogo roda num executor). */
    private static void measure() {
        BufferedImage big = picture(1024, 1024);
        byte[] png = encode(big, "png");
        BufferedImage rgb = picture(1024, 768);
        byte[] jpg = encode(rgb, "jpg");
        long pngNanos = decode(png);
        long jpgNanos = decode(jpg);
        long vram = 1024L * 1024 * 4 * 4 / 3;
        measured = String.format(Locale.ROOT, "PNG 1024×1024 de %d KB em %.1f ms; JPG 1024×768 de %d KB em %.1f ms; "
                + "VRAM de 1024×1024 com mipmaps %.1f MB", png.length / 1024, pngNanos / 1e6, jpg.length / 1024,
                jpgNanos / 1e6, vram / (1024.0 * 1024));
    }

    /** O melhor de três decodificações (a primeira aquece o JIT). */
    private static long decode(byte[] data) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            try (TvImageDecoder.Decoded decoded = TvImageDecoder.decode(data)) {
                if (decoded == null) {
                    throw new IllegalStateException("o leitor recusou a imagem de teste");
                }
                best = Math.min(best, decoded.nanos);
            }
        }
        return best;
    }
}
