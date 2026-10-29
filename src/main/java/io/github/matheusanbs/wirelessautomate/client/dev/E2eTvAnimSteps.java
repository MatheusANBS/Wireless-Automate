package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvCodeNotice;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvPixelEditor;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelCanvas;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvCanvases;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvTimed;
import io.github.matheusanbs.wirelessautomate.gametest.TvCodeExamples;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvEditing;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
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
import org.lwjgl.glfw.GLFW;

/**
 * A pixel art animada (etapa 4, Task 5), depois do {@link E2eTvArtSteps}, com o editor da Tela aberto: no editor de
 * pixel art, três quadros pela faixa (+ Quadro, desenhar com o papel vegetal, Duplicar), o papel vegetal desligado e
 * ligado (Alt+O e o botão), a velocidade pelo ›, o modo Vai e volta pela lista, desfazer e refazer os dois, {@code ,} e
 * {@code .} trocando de quadro e arrastar uma miniatura (desfeito); as fotos {@code tela-anima-editor} e
 * {@code -escuro}; salvar e conferir os quadros na biblioteca do cliente e do servidor; importar o exemplo animado do
 * guia ({@code quadros: 2}) e removê-lo; importar um céu de 128 × 128 com 6 quadros. No mundo, a arte numa Imagem e
 * no mosaico, as fotos {@code tela-anima-1} e {@code -2} com quadros diferentes, o céu na Tela inteira tocando
 * ({@code tela-anima-128}) e a medida das texturas dos quadros. No fim, o layout de antes volta e a tela abre
 * de novo na aba Editar.
 */
final class E2eTvAnimSteps {
    private static final String NAME = "Anima";
    private static final String EXAMPLE = "Sinal piscando";
    private static final String SKY = "Céu noturno";
    private static final int CTRL = GLFW.GLFW_MOD_CONTROL;
    private static final int ALT = GLFW.GLFW_MOD_ALT;
    private static TvLayout original = TvLayout.EMPTY;
    private static String clipboard = "";
    private static int firstFrame = -1;
    private static long paintsBefore;
    private static String measured = "";
    private static boolean skySaved;

    private E2eTvAnimSteps() {
    }

    static void animSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv) {
        editorSteps(list);
        historySteps(list);
        saveSteps(list);
        worldSteps(list, playerId, tv);
    }

    private static void editorSteps(List<Step> list) {
        list.add(step("Anima: aba Artes e Nova", () -> {
            clickAt(screen().editor().probe().leftTabCenter("ARTS"));
            clickAt(pixel().artsButtonCenter("NEW"));
        }, () -> pixel().isOpen() && canvas().frames() == 1 && canvas().width() == 16, () -> "aberto "
                + pixel().isOpen()));
        list.add(step("Anima: quadro 1, um retângulo cheio", () -> {
            clickAt(pixel().swatchCenter(5));
            type("rr");
            dragCells(2, 2, 6, 6);
        }, () -> session().rectFilled() && canvas().get(4, 4) == 5, () -> "pixel " + canvas().get(4, 4)));
        list.add(step("Anima: + Quadro, vazio e com o papel vegetal", () -> clickAt(pixel().buttonCenter("FRAME_ADD")),
                () -> canvas().frames() == 2 && canvas().current() == 1 && canvas().get(4, 4) == 0
                        && session().frames().onionFrame() == 0,
                () -> canvas().frames() + " quadros, o " + canvas().current()));
        list.add(step("Anima: quadro 2", () -> {
            clickAt(pixel().swatchCenter(10));
            dragCells(9, 2, 13, 6);
        }, () -> canvas().get(11, 4) == 10 && canvas().frame(0)[4 * 16 + 4] == 5, () -> "pixel " + canvas().get(11, 4)));
        list.add(step("Anima: Duplicar", () -> clickAt(pixel().buttonCenter("FRAME_DUPLICATE")),
                () -> canvas().frames() == 3 && canvas().current() == 2 && canvas().get(11, 4) == 10,
                () -> canvas().frames() + " quadros, o " + canvas().current()));
        list.add(step("Anima: quadro 3", () -> {
            clickAt(pixel().swatchCenter(14));
            dragCells(2, 9, 13, 13);
        }, () -> canvas().get(7, 11) == 14 && canvas().frame(1)[11 * 16 + 7] == 0, () -> "pixel " + canvas().get(7, 11)));
        list.add(step("Anima: Alt+O desliga o papel vegetal", () -> press(GLFW.GLFW_KEY_O, ALT),
                () -> !session().frames().onion() && session().frames().onionFrame() == -1, () -> ""));
        list.add(step("Anima: o botão liga de novo", () -> clickAt(pixel().buttonCenter("FRAME_ONION")),
                () -> session().frames().onion() && session().frames().onionFrame() == 1, () -> ""));
        list.add(step("Anima: a velocidade pelo › (6 → 8)", () -> {
            clickAt(pixel().speedArrow(true));
            clickAt(pixel().speedArrow(true));
        }, () -> canvas().fps() == 8, () -> canvas().fps() + " q/s"));
        list.add(step("Anima: o modo Vai e volta pela lista", () -> {
            clickAt(pixel().modeCenter());
            press(GLFW.GLFW_KEY_DOWN, 0);
            press(GLFW.GLFW_KEY_ENTER, 0);
        }, () -> canvas().mode() == TvPixels.Mode.PINGPONG && !pixel().colorOpen(), () -> "modo " + canvas().mode()));
    }

    private static void historySteps(List<Step> list) {
        list.add(step("Anima: Ctrl+Z desfaz o modo e a velocidade", () -> {
            press(GLFW.GLFW_KEY_Z, CTRL);
            press(GLFW.GLFW_KEY_Z, CTRL);
        }, () -> canvas().mode() == TvPixels.Mode.LOOP && canvas().fps() == 7,
                () -> canvas().mode() + ", " + canvas().fps() + " q/s"));
        list.add(step("Anima: Ctrl+Y refaz", () -> {
            press(GLFW.GLFW_KEY_Y, CTRL);
            press(GLFW.GLFW_KEY_Y, CTRL);
        }, () -> canvas().mode() == TvPixels.Mode.PINGPONG && canvas().fps() == 8,
                () -> canvas().mode() + ", " + canvas().fps() + " q/s"));
        list.add(step("Anima: , e . trocam de quadro", () -> type(","), () -> canvas().current() == 1,
                () -> "quadro " + canvas().current()));
        list.add(step("Anima: . volta", () -> type("."), () -> canvas().current() == 2,
                () -> "quadro " + canvas().current()));
        list.add(step("Anima: arrastar o quadro 1 para o fim", () -> {
            int[] from = pixel().frameCenter(0);
            int[] to = pixel().frameCenter(2);
            drag(screen(), from[0], from[1], to[0], to[1]);
        }, () -> canvas().frame(2)[4 * 16 + 4] == 5 && canvas().frame(0)[4 * 16 + 11] == 10,
                () -> "o fim tem " + canvas().frame(2)[4 * 16 + 4]));
        list.add(step("Anima: Ctrl+Z desfaz a ordem", () -> press(GLFW.GLFW_KEY_Z, CTRL),
                () -> canvas().frame(0)[4 * 16 + 4] == 5 && canvas().frames() == 3,
                () -> "o primeiro tem " + canvas().frame(0)[4 * 16 + 4]));
        list.add(step("Anima: escolher o quadro 2 (o 1 por baixo)", () -> {
            clickAt(pixel().frameCenter(1));
            int[] at = pixel().cellCenter(8, 8);
            moveMouse(at[0], at[1]);
        }, () -> canvas().current() == 1 && session().frames().onionFrame() == 0 && stepTicks >= 4, () -> ""));
        list.add(step("Anima: tema claro para a foto (o mundo pode ter ficado no escuro)", () -> {
            if (GuiThemes.current() != GuiTheme.LIGHT) {
                GuiThemes.set(GuiTheme.LIGHT);
            }
        }, () -> GuiThemes.current() == GuiTheme.LIGHT && stepTicks >= 2, () -> "tema " + GuiThemes.current()));
        E2eTvSteps.shot(list, "tela-anima-editor");
        list.add(E2eDarkSteps.themeClick("Anima: escuro", GuiTheme.DARK));
        E2eTvSteps.shot(list, "tela-anima-editor-escuro");
        list.add(E2eDarkSteps.themeClick("Anima: claro de novo", GuiTheme.LIGHT));
    }

    private static void saveSteps(List<Step> list) {
        list.add(step("Anima: dar o nome", () -> {
            pixel().focusName();
            for (int i = 0; i < 20; i++) {
                press(GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type(NAME);
            press(GLFW.GLFW_KEY_ENTER, 0);
        }, () -> pixel().name().equals(NAME), () -> "nome " + pixel().name()));
        list.add(step("Anima: Salvar leva os três quadros à biblioteca", () -> clickAt(pixel().buttonCenter("SAVE")),
                () -> !pixel().isOpen() && animated(art(NAME), 3) && onServer(server -> TvLibrary.get(server)
                        .arts(Minecraft.getInstance().player.getUUID()).stream()
                        .anyMatch(a -> a.name().equals(NAME) && animated(a, 3))),
                () -> "aberto " + pixel().isOpen() + ", no cliente " + art(NAME)));
        list.add(step("Anima: guardar a área de transferência e colar o exemplo animado do guia", () -> {
            clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            Minecraft.getInstance().keyboardHandler.setClipboard(TvCodeExamples.ART_ANIMATED);
            clickAt(pixel().artsButtonCenter("IMPORT"));
        }, () -> {
            TvPixelArt example = art(EXAMPLE);
            return example != null && example.frames() == 2 && example.fps() == 4
                    && example.mode() == TvPixels.Mode.LOOP;
        }, () -> "artes " + TvLibraryClient.arts()));
        list.add(step("Anima: remover o exemplo em dois cliques", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(clipboard);
            clickAt(pixel().artRowCenter(EXAMPLE));
            clickAt(pixel().artsButtonCenter("REMOVE"));
            clickAt(pixel().artsButtonCenter("REMOVE"));
        }, () -> art(EXAMPLE) == null && art(NAME) != null, () -> "artes " + TvLibraryClient.arts()));
        list.add(step("Anima: importar um céu de 128 × 128 com 6 quadros e 15 cores", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(TvCodeExamples.nightSky());
            clickAt(pixel().artsButtonCenter("IMPORT"));
        }, () -> {
            TvPixelArt sky = art(SKY);
            Minecraft.getInstance().keyboardHandler.setClipboard(clipboard);
            return sky != null && sky.width() == 128 && sky.frames() == 6;
        }, () -> "artes " + TvLibraryClient.arts() + ", aviso " + TvCodeNotice.current()));
    }

    private static boolean animated(TvPixelArt art, int frames) {
        return art != null && art.frames() == frames && art.fps() == 8 && art.mode() == TvPixels.Mode.PINGPONG
                && art.pixels()[4 * 16 + 4] == 5 && art.pixels()[256 + 4 * 16 + 11] == 10
                && art.pixels()[512 + 11 * 16 + 7] == 14;
    }

    /** A arte numa Imagem e no mosaico da Tela, duas fotos com quadros diferentes, a medida e o layout de volta. */
    private static void worldSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv) {
        list.add(close("Anima: fechar a tela"));
        list.add(step("Anima: a arte na Imagem e no mosaico", () -> {
            original = entity(tv.get()).layout();
            onServer(server -> {
                TvPixelArt art = TvLibrary.get(server).arts(playerId).stream().filter(a -> a.name().equals(NAME))
                        .findFirst().orElseThrow();
                ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(layout(slow(art)));
                return null;
            });
        }, () -> {
            TvLayout shown = entity(tv.get()).layout();
            return shown.images().size() == 1 && shown.images().get(0).animated() && TvTimed.artFrameForTests() >= 0
                    && stepTicks >= 10;
        }, () -> "artes no cliente " + entity(tv.get()).layout().images().size()));
        list.add(E2eTvSteps.hideGui(playerId, true));
        list.add(E2eTvSteps.view("tela-anima-1", playerId, () -> E2eTvSteps.worldTarget().add(0, 0, -2.2),
                E2eTvSteps::worldTarget));
        list.add(step("Anima: o quadro da primeira foto", () -> {
            firstFrame = TvTimed.artFrameForTests();
            paintsBefore = TvCanvases.paintsForTests;
        }, () -> firstFrame >= 0, () -> "quadro " + firstFrame));
        list.add(step("Anima: o quadro muda sozinho com a Tela à vista", () -> {
        }, () -> TvTimed.artFrameForTests() != firstFrame && TvCanvases.paintsForTests > paintsBefore
                && stepTicks >= 2, () -> "quadro " + TvTimed.artFrameForTests() + " (antes " + firstFrame + ")"));
        list.add(E2eTvSteps.view("tela-anima-2", playerId, () -> E2eTvSteps.worldTarget().add(0, 0, -2.2),
                E2eTvSteps::worldTarget));
        skySteps(list, playerId, tv);
        list.add(E2eTvSteps.hideGui(playerId, false));
        list.add(step("Anima: medir as texturas dos quadros (128 × 128 com 8 e 32 × 32 com 64)",
                E2eTvAnimSteps::measure, () -> !measured.isEmpty(), () -> measured));
        list.add(step("Anima: o layout de antes volta", () -> onServer(server -> {
            ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(original);
            return null;
        }), () -> entity(tv.get()).layout().equals(original), () -> "layout no cliente"));
        list.add(E2eTvSteps.open("Anima: abrir de novo"));
        list.add(E2eTvSteps.tab("Anima: aba Editar", TvScreen.Tab.EDIT));
    }

    /**
     * O céu de 128 × 128 com 6 quadros (o caso do dono) numa Imagem esticada pela Tela inteira e no mosaico, salvo
     * pelo servidor como o salvar do editor e tocando: a foto {@code tela-anima-128} e o quadro que muda.
     */
    private static void skySteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv) {
        list.add(step("Anima: o céu de 128 × 128 na Tela inteira e no mosaico", () -> skySaved = onServer(server -> {
            TvPixelArt sky = TvLibrary.get(server).arts(playerId).stream().filter(a -> a.name().equals(SKY))
                    .findFirst().orElseThrow();
            TvBlockEntity entity = (TvBlockEntity) server.overworld().getBlockEntity(tv.get());
            TvArea area = entity.area();
            TvPiece full = new TvPiece(TvPieceType.IMAGE, 0, 0, area.pixelWidth(), area.pixelHeight(), 0, "",
                    Optional.empty(), TvFormat.DEFAULT, TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(
                    Optional.of(sky.id()), 0, TvPartStyle.VAlign.TOP)));
            TvLayout layout = TvLayout.of(List.of(full), TvTheme.GRAPHITE, TvBackground.tiled(sky.id(), 1, 60), false,
                    List.of(sky));
            return TvEditing.applyLayout(server.getPlayerList().getPlayer(playerId), entity, layout).ok();
        }), () -> {
            TvLayout shown = entity(tv.get()).layout();
            return skySaved && shown.images().size() == 1 && shown.images().get(0).frames() == 6 && stepTicks >= 10;
        }, () -> "salvo " + skySaved + ", artes no cliente " + entity(tv.get()).layout().images()));
        list.add(E2eTvSteps.view("tela-anima-128", playerId, () -> E2eTvSteps.worldTarget().add(0, 0, -2.2),
                E2eTvSteps::worldTarget));
        list.add(step("Anima: o céu toca", () -> firstFrame = TvTimed.artFrameForTests(), () -> stepTicks >= 1
                && TvTimed.artFrameForTests() != firstFrame, () -> "quadro " + TvTimed.artFrameForTests()));
    }

    /**
     * A arte a 1 quadro por segundo, em loop: as duas fotos, tiradas logo depois de o quadro mudar, caem em quadros
     * diferentes (a 8 q/s, a foto pode cair em qualquer um).
     */
    private static TvPixelArt slow(TvPixelArt art) {
        return new TvPixelArt(art.id(), art.name(), art.width(), art.height(), art.palette(), art.pixels(),
                art.frames(), 1, TvPixels.Mode.LOOP);
    }

    /** A arte 3× numa Imagem à esquerda e o mosaico dela, em 1× e apagado, por baixo de tudo. */
    private static TvLayout layout(TvPixelArt art) {
        TvPiece image = new TvPiece(TvPieceType.IMAGE, 8, 8, 48, 48, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(Optional.of(art.id()), 3,
                        TvPartStyle.VAlign.TOP)));
        TvPiece text = new TvPiece(TvPieceType.TEXT, 64, 24, 60, 16, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withText("Anima"));
        return TvLayout.of(List.of(image, text), TvTheme.GRAPHITE, TvBackground.tiled(art.id(), 1, 35), false,
                List.of(art));
    }

    /** As texturas de todos os quadros de duas artes no teto: o tempo de subir, quantas e a troca de quadro. */
    private static void measure() {
        StringBuilder out = new StringBuilder();
        int[][] sizes = {{128, 128, 8}, {32, 32, 64}};
        for (int[] size : sizes) {
            int w = size[0];
            int h = size[1];
            int frames = size[2];
            int[] palette = new int[16];
            for (int i = 1; i < 16; i++) {
                palette[i] = 0xFF000000 | i * 0x101010;
            }
            byte[] pixels = new byte[w * h * frames];
            Random random = new Random(w);
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (byte) random.nextInt(16);
            }
            TvPixelArt art = new TvPixelArt(UUID.randomUUID(), "medida", w, h, palette, pixels, frames, 6,
                    TvPixels.Mode.LOOP);
            long[] result = TvTimed.uploadFramesForTests(art);
            long vram = (long) w * h * 4 * 4 / 3 * result[1];
            out.append(String.format(Locale.ROOT, "%d × %d com %d quadros: %d texturas, %.0f KB de vídeo com os "
                    + "mipmaps, subidas em %.2f ms; trocar de quadro %d ns; ", w, h, frames, result[1], vram / 1024.0,
                    result[0] / 1e6, result[2]));
        }
        measured = out.toString().strip();
    }

    private static void dragCells(int x0, int y0, int x1, int y1) throws StepFailure {
        int[] from = pixel().cellCenter(x0, y0);
        int[] to = pixel().cellCenter(x1, y1);
        drag(screen(), from[0], from[1], to[0], to[1]);
    }

    private static TvPixelArt art(String name) {
        return TvLibraryClient.arts().stream().filter(a -> a.name().equals(name)).findFirst().orElse(null);
    }

    private static TvBlockEntity entity(BlockPos pos) {
        return (TvBlockEntity) Minecraft.getInstance().level.getBlockEntity(pos);
    }

    @FunctionalInterface
    private interface Do {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface Is {
        boolean test() throws Exception;
    }

    private static Step step(String name, Do action, Is check, State state) {
        return new Step(name, STEP_TIMEOUT_MS, action::run, check::test, state);
    }

    private static TvScreen screen() throws StepFailure {
        return E2eTvSteps.screen();
    }

    private static void press(int key, int modifiers) throws StepFailure {
        screen().keyPressed(key, 0, modifiers);
    }

    private static TvPixelEditor pixel() throws StepFailure {
        return screen().editor().pixel();
    }

    private static TvPixelSession session() throws StepFailure {
        TvPixelSession session = pixel().session();
        if (session == null) {
            throw new StepFailure("o editor de pixel art não está aberto");
        }
        return session;
    }

    private static TvPixelCanvas canvas() throws StepFailure {
        return session().canvas();
    }

    private static void clickAt(int[] point) throws StepFailure {
        if (point == null) {
            throw new StepFailure("o controle não está à vista");
        }
        click(screen(), point[0], point[1]);
    }
}
