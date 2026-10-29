package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvGifReader;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageDecoder;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageUploader;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvPictureTextures;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvImageConfig;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.github.matheusanbs.wirelessautomate.tv.TvImageState;
import io.github.matheusanbs.wirelessautomate.tv.TvImageStore;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Os GIFs e a aba Imagens (etapa 4, Task 4b), depois do {@link E2eTvPictureSteps}: um GIF de quatro quadros gerado
 * aqui ({@link E2eGif}) sobe pelo envio do cliente e toca na Tela (duas fotos em tempos diferentes,
 * {@code tela-imagem-gif-1} e {@code -2}, com quadros diferentes); uma imagem transparente (um anel coral com a borda
 * suave sobre o transparente preto) reduzida em três caixas sobre o fundo claro mostra que a borda não escurece
 * ({@code tela-imagem-transparente-reduzida}, de longe); desligar {@code tv.images.enabled} com o mundo rodando troca
 * as imagens pelo quadro neutro na hora ({@code tela-imagem-desligada}) e religar as traz de volta. No editor, a parte
 * Imagens da aba Artes ({@code tela-imagens-aba}) e arrastar o GIF até a miniatura, com a grade das artes e imagens na
 * aba Estilo ({@code tela-imagens-grade}). No fim, o layout de antes volta e as texturas saem.
 */
final class E2eTvGifSteps {
    private static final String GIF_NAME = "GIF do e2e";
    private static final double FAR = 7;
    private static final int GIF_W = 64;
    private static final int GIF_H = 48;
    private static TvLayout original = TvLayout.EMPTY;
    private static String gifHash = "";
    private static String ringHash = "";
    private static final String[] OUTCOME = {""};
    private static int firstFrame = -1;
    private static String measured = "";

    private E2eTvGifSteps() {
    }

    static void gifSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv) {
        list.add(new Step("GIF: medir a decodificação de um GIF de 480 × 270 com 60 quadros", STEP_TIMEOUT_MS,
                E2eTvGifSteps::measure, () -> !measured.isEmpty(), () -> measured));
        worldSteps(list, playerId, tv);
        editorSteps(list, tv);
    }

    private static void worldSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv) {
        list.add(new Step("GIF: enviar pelo cliente", STEP_TIMEOUT_MS, () -> {
            byte[] gif = gif();
            gifHash = TvImageHash.of(gif);
            OUTCOME[0] = "";
            original = entity(tv.get()).layout();
            TvImageUploader.upload(gif, GIF_NAME, outcome -> OUTCOME[0] = outcome.ok() ? "ok"
                    : String.valueOf(outcome.refusal()));
        }, () -> "ok".equals(OUTCOME[0]) && onServer(server -> TvImageStore.get(server).contains(gifHash)),
                () -> "resposta " + OUTCOME[0]));
        list.add(new Step("GIF: a imagem transparente no armazém e na biblioteca, e o layout", STEP_TIMEOUT_MS, () -> {
            byte[] ring = encode(ring(256));
            ringHash = TvImageHash.of(ring);
            onServer(server -> {
                TvImageStore store = TvImageStore.get(server);
                store.putForTests(ring, playerId);
                TvLibrary.get(server).addPicture(playerId, ringHash, "Anel alfa");
                TvImageStore.syncLibrary(server, playerId);
                ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(layout(store.picture(gifHash),
                        store.picture(ringHash)));
                return null;
            });
        }, () -> TvPictureTextures.readyForTests(gifHash) && TvPictureTextures.readyForTests(ringHash)
                && TvPictureTextures.shownFrameForTests(gifHash) >= 0 && stepTicks >= 10,
                () -> "GIF pronto " + TvPictureTextures.readyForTests(gifHash) + ", anel pronto "
                        + TvPictureTextures.readyForTests(ringHash)));
        list.add(E2eTvSteps.hideGui(playerId, true));
        list.add(E2eTvSteps.view("tela-imagem-gif-1", playerId, () -> E2eTvSteps.worldTarget().add(0, 0, -2.2),
                E2eTvSteps::worldTarget));
        list.add(new Step("GIF: o quadro da primeira foto", STEP_TIMEOUT_MS,
                () -> firstFrame = TvPictureTextures.shownFrameForTests(gifHash), () -> firstFrame >= 0,
                () -> "quadro " + firstFrame));
        list.add(new Step("GIF: o quadro muda sozinho com a Tela à vista", STEP_TIMEOUT_MS, () -> {
        }, () -> TvPictureTextures.shownFrameForTests(gifHash) != firstFrame && stepTicks >= 4,
                () -> "quadro " + TvPictureTextures.shownFrameForTests(gifHash) + " (antes " + firstFrame + ")"));
        list.add(E2eTvSteps.view("tela-imagem-gif-2", playerId, () -> E2eTvSteps.worldTarget().add(0, 0, -2.2),
                E2eTvSteps::worldTarget));
        list.add(E2eTvSteps.view("tela-imagem-transparente-reduzida", playerId,
                () -> E2eTvSteps.worldTarget().add(0, 0, -FAR), E2eTvSteps::worldTarget));
        list.add(new Step("GIF: desligar as imagens na config, com o mundo rodando", STEP_TIMEOUT_MS,
                () -> onServer(server -> {
                    TvImageConfig.enabledValue().set(false);
                    TvImageState.configChanged();
                    return null;
                }), () -> TvPictureTextures.serverOff() && stepTicks >= 6,
                () -> "desligadas no cliente " + TvPictureTextures.serverOff()));
        list.add(E2eTvSteps.view("tela-imagem-desligada", playerId, () -> E2eTvSteps.worldTarget().add(0, 0, -2.2),
                E2eTvSteps::worldTarget));
        list.add(new Step("GIF: religar as imagens", STEP_TIMEOUT_MS, () -> onServer(server -> {
            TvImageConfig.enabledValue().set(true);
            TvImageState.configChanged();
            return null;
        }), () -> !TvPictureTextures.serverOff() && stepTicks >= 4,
                () -> "desligadas no cliente " + TvPictureTextures.serverOff()));
        list.add(E2eTvSteps.hideGui(playerId, false));
    }

    private static void editorSteps(List<Step> list, Supplier<BlockPos> tv) {
        list.add(E2eTvSteps.open("GIF: abrir a tela"));
        list.add(E2eTvSteps.tab("GIF: aba Editar", TvScreen.Tab.EDIT));
        list.add(new Step("GIF: a parte Imagens da aba Artes", STEP_TIMEOUT_MS,
                () -> clickAt(editor().probe().librarySegmentCenter("PICTURES")),
                () -> editor().probe().libraryPart().equals("PICTURES") && picture(GIF_NAME) != null
                        && editor().probe().pictureRowCenter(GIF_NAME) != null,
                () -> "parte " + editor().probe().libraryPart() + ", imagens " + TvLibraryClient.pictures().size()));
        list.add(new Step("GIF: escolher o GIF na lista", STEP_TIMEOUT_MS,
                () -> clickAt(editor().probe().pictureRowCenter(GIF_NAME)), () -> stepTicks >= 10, () -> ""));
        E2eTvSteps.shot(list, "tela-imagens-aba");
        dark(list, "tela-imagens-aba-escuro");
        list.add(new Step("GIF: arrastar o GIF até a miniatura cria a Imagem", STEP_TIMEOUT_MS, () -> {
            int[] from = editor().probe().pictureRowCenter(GIF_NAME);
            int[] to = editor().probe().canvasCenter();
            drag(screen(), from[0], from[1], to[0], to[1]);
        }, () -> {
            TvPicture gif = picture(GIF_NAME);
            return gif != null && editor().draft().picture(gif.id()) != null
                    && editor().draft().pieces().stream().anyMatch(p -> p.type() == TvPieceType.IMAGE
                    && p.style().part().image().filter(gif.id()::equals).isPresent());
        }, () -> "rascunho " + editor().draft().pieces().size() + " peças"));
        list.add(new Step("GIF: aba Estilo com a grade", STEP_TIMEOUT_MS,
                () -> clickAt(editor().probe().propsTabCenter("STYLE")),
                () -> editor().probe().propCenter("image.arts") != null && stepTicks >= 10,
                () -> editor().probe().propsTab()));
        E2eTvSteps.shot(list, "tela-imagens-grade");
        dark(list, "tela-imagens-grade-escuro");
        list.add(new Step("GIF: desfazer a Imagem e soltar a escolha", STEP_TIMEOUT_MS, () -> {
            clickAt(editor().probe().barCenter("UNDO"));
            if (!editor().selection().isEmpty()) {
                screen().keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
        }, () -> !editor().dirty() && editor().selection().isEmpty(),
                () -> "rascunho " + editor().draft().pieces().size() + " peças, não salvo " + editor().dirty()));
        list.add(close("GIF: fechar sem salvar"));
        list.add(new Step("GIF: o layout de antes volta e as texturas saem", 30_000, () -> onServer(server -> {
            ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(original);
            return null;
        }), () -> entity(tv.get()).layout().equals(original) && TvPictureTextures.readyForTests() == 0
                && TvPictureTextures.gifRamForTests() == 0,
                () -> TvPictureTextures.readyForTests() + " textura(s), " + TvPictureTextures.gifRamForTests()
                        + " bytes de GIF"));
    }

    /** A foto no escuro, só com a variante ligada. */
    private static void dark(List<Step> list, String file) {
        E2eVariants.escuro(list, () -> {
            list.add(E2eDarkSteps.themeClick("GIF: escuro", GuiTheme.DARK));
            E2eTvSteps.shot(list, file);
            list.add(E2eDarkSteps.themeClick("GIF: claro de novo", GuiTheme.LIGHT));
        });
    }

    /**
     * O layout no fundo claro (Porcelana): o GIF em escala 1 à esquerda, com a borda, e a imagem transparente de 256
     * px esticada em caixas de 32, 16 e 8 px à direita, reduzida pelos mipmaps; um texto embaixo.
     */
    private static TvLayout layout(TvPicture gif, TvPicture ring) {
        List<TvPiece> pieces = new ArrayList<>();
        pieces.add(image(gif.id(), 4, 8, GIF_W, GIF_H, 1));
        pieces.add(image(ring.id(), 74, 8, 32, 32, 0));
        pieces.add(image(ring.id(), 108, 8, 16, 16, 0));
        pieces.add(image(ring.id(), 108, 28, 8, 8, 0));
        pieces.add(new TvPiece(TvPieceType.TEXT, 72, 44, 54, 12, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withText("GIF + alfa").withSize(9).withColor(TvColor.theme(TvColor.TEXT))));
        return TvLayout.of(pieces, TvTheme.PORCELAIN, TvBackground.DEFAULT, false, List.of(), List.of(gif, ring));
    }

    private static TvPiece image(UUID id, int x, int y, int w, int h, int scale) {
        TvStyle style = TvStyle.DEFAULT.withAlign(TvAlign.CENTER).withPart(TvPartStyle.DEFAULT.withImage(
                Optional.of(id), scale, TvPartStyle.VAlign.MIDDLE));
        return new TvPiece(TvPieceType.IMAGE, x, y, w, h, 0, "", Optional.empty(), TvFormat.DEFAULT, style);
    }

    /**
     * Decodifica aqui um GIF de 480 × 270 com 60 quadros (no jogo roda num executor): com todos os quadros na memória,
     * pelo STB, e no modo comprimido, o primeiro quadro e os 59 seguintes pelo {@link TvGifReader}, um de cada
     * vez.
     */
    private static void measure() {
        int w = 480;
        int h = 270;
        int[] palette = new int[16];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = (i * 16) << 16 | (255 - i * 16) << 8 | (i * 37 & 0xFF);
        }
        List<E2eGif.Frame> frames = new ArrayList<>();
        for (int f = 0; f < 60; f++) {
            int[] indices = new int[w * h];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = ((i % w) / 6 + (i / w) / 6 + f) & 15;
            }
            frames.add(E2eGif.Frame.full(w, h, indices, 4));
        }
        byte[] gif = E2eGif.write(w, h, palette, frames);
        long start = System.nanoTime();
        try (TvImageDecoder.Decoded all = TvImageDecoder.decode(gif, Long.MAX_VALUE)) {
            long allNanos = System.nanoTime() - start;
            start = System.nanoTime();
            TvGifReader reader = new TvGifReader(gif);
            int read = 0;
            while (reader.next()) {
                read++;
            }
            long streamNanos = System.nanoTime() - start;
            measured = String.format(Locale.ROOT, "GIF de %d KB, %d quadros: todos pelo STB em %.1f ms "
                    + "(%.1f MB na memória); %d quadros um a um pelo leitor em %.1f ms (%.2f ms por quadro)",
                    gif.length / 1024, all == null ? 0 : all.frames, allNanos / 1e6,
                    all == null ? 0 : all.bytes() / (1024.0 * 1024), read, streamNanos / 1e6, streamNanos / 1e6 / read);
        }
    }

    /**
     * Um GIF de 64 × 48 em quatro quadros de 400 ms: listras de quatro cores que andam e um quadrado claro que
     * atravessa a imagem, para cada quadro ser diferente nas fotos. O segundo quadro só repinta o quadrado (o resto
     * transparente deixa ver o primeiro).
     */
    static byte[] gif() {
        int[] palette = {0x1C2B3A, 0xE8654A, 0x3FA7D6, 0xF2C14E, 0x7BC67E, 0xF4F1EA, 0x101216, 0x000000};
        List<E2eGif.Frame> frames = new ArrayList<>();
        for (int f = 0; f < 4; f++) {
            int[] indices = new int[GIF_W * GIF_H];
            for (int y = 0; y < GIF_H; y++) {
                for (int x = 0; x < GIF_W; x++) {
                    int stripe = 1 + Math.floorMod((x + y) / 8 - f, 4);
                    boolean square = x >= 8 + f * 12 && x < 20 + f * 12 && y >= 16 && y < 32;
                    boolean frame = x == 0 || y == 0 || x == GIF_W - 1 || y == GIF_H - 1;
                    indices[y * GIF_W + x] = frame ? 6 : square ? 5 : stripe;
                }
            }
            frames.add(E2eGif.Frame.full(GIF_W, GIF_H, indices, 40));
        }
        return E2eGif.write(GIF_W, GIF_H, palette, frames);
    }

    /**
     * Um anel coral com o miolo branco, a borda suave em 3 px, sobre o transparente preto (o que o {@code ImageIO}
     * grava no pixel transparente): sem o alfa pré-multiplicado nos mipmaps, a borda reduzida escureceria.
     */
    static BufferedImage ring(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double c = size / 2.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double d = Math.hypot(x + 0.5 - c, y + 0.5 - c) / c;
                double outer = Math.min(1, Math.max(0, (0.95 - d) * c / 3));
                double inner = Math.min(1, Math.max(0, (d - 0.45) * c / 3));
                double alpha = Math.min(outer, Math.max(inner, d < 0.3 ? 1 : 0));
                int a = (int) Math.round(alpha * 255);
                int rgb = d < 0.4 ? 0xFFFFFF : 0xE8654A;
                image.setRGB(x, y, a == 0 ? 0 : a << 24 | rgb);
            }
        }
        return image;
    }

    private static byte[] encode(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static TvPicture picture(String name) {
        for (TvLibraryPicture entry : TvLibraryClient.pictures()) {
            if (entry.name().equals(name)) {
                return entry.picture();
            }
        }
        return null;
    }

    private static TvBlockEntity entity(BlockPos pos) {
        return (TvBlockEntity) Minecraft.getInstance().level.getBlockEntity(pos);
    }

    private static TvScreen screen() throws StepFailure {
        return E2eTvSteps.screen();
    }

    private static TvEditorTab editor() throws StepFailure {
        return screen().editor();
    }

    private static void clickAt(int[] point) throws StepFailure {
        if (point == null) {
            throw new StepFailure("o controle não está à vista");
        }
        click(screen(), point[0], point[1]);
    }
}
