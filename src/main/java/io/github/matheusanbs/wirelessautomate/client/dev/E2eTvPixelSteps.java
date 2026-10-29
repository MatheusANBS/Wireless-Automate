package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvPixelEditor;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelCanvas;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * O editor de pixel art no e2e, tudo pelo mouse e pelo teclado, com o editor da Tela aberto: a aba Pixel art, Nova
 * (16 × 16), um retângulo, o balde por dentro, uma elipse cheia, uma linha, o lápis, o clarear numa faixa da elipse,
 * desfazer e refazer, o zoom no ponto do mouse, arrastar a vista com o botão do meio e enquadrar com 0; a foto
 * {@code tela-pixel-editor}; o nome e o Salvar, conferido na biblioteca do cliente e do servidor; e Editar, que abre
 * a arte salva de volta (no escuro, a foto {@code tela-pixel-editor-escuro}), e o Esc sem mudança, que volta ao
 * editor da Tela. O resto da aba, a Imagem e o mosaico ficam no {@link E2eTvArtSteps}.
 */
final class E2eTvPixelSteps {
    private static final String NAME = "Logo";
    private static final int CTRL = GLFW.GLFW_MOD_CONTROL;
    private static int zoomBefore;
    private static int panBefore;
    /** Onde o mouse estava ao girar a roda: o pixel sob ele fica parado. */
    private static final int[] MOUSE = new int[2];

    private E2eTvPixelSteps() {
    }

    static void pixelSteps(List<Step> list) {
        list.add(step("Pixel: aba Pixel art e Nova", () -> {
            clickAt(editor().probe().leftTabCenter("ARTS"));
            clickAt(pixel().artsButtonCenter("NEW"));
        }, () -> pixel().isOpen() && canvas().width() == 16 && canvas().height() == 16 && !pixel().name().isEmpty(),
                () -> "aberto " + pixel().isOpen()));
        list.add(step("Pixel: retângulo de contorno", () -> {
            clickAt(pixel().swatchCenter(1));
            type("r");
            dragCells(2, 2, 13, 13);
        }, () -> canvas().get(2, 2) == 1 && canvas().get(13, 2) == 1 && canvas().get(7, 13) == 1
                && canvas().get(7, 7) == 0, () -> row(2) + " / " + row(7)));
        list.add(step("Pixel: balde por dentro", () -> {
            clickAt(pixel().swatchCenter(10));
            type("g");
            clickAt(pixel().cellCenter(7, 7));
        }, () -> canvas().get(7, 7) == 10 && canvas().get(3, 3) == 10 && canvas().get(0, 0) == 0,
                () -> row(7)));
        list.add(step("Pixel: elipse cheia (O duas vezes)", () -> {
            clickAt(pixel().swatchCenter(6));
            type("oo");
            dragCells(4, 4, 11, 11);
        }, () -> session().ellipseFilled() && canvas().get(7, 7) == 6 && canvas().get(4, 4) == 10,
                () -> row(7)));
        list.add(step("Pixel: linha", () -> {
            clickAt(pixel().swatchCenter(3));
            type("l");
            dragCells(3, 12, 12, 3);
        }, () -> canvas().get(8, 7) == 3 && canvas().get(3, 12) == 3 && canvas().get(12, 3) == 3, () -> row(7)));
        list.add(step("Pixel: lápis", () -> {
            clickAt(pixel().swatchCenter(14));
            type("b");
            dragCells(2, 0, 13, 0);
        }, () -> canvas().get(2, 0) == 14 && canvas().get(13, 0) == 14 && canvas().get(1, 0) == 0, () -> row(0)));
        list.add(step("Pixel: clarear uma faixa da elipse", () -> {
            type("d");
            dragCells(4, 7, 11, 7);
        }, () -> canvas().get(5, 7) == 7 && canvas().get(8, 7) == 3, () -> row(7)));
        list.add(step("Pixel: Ctrl+Z desfaz o clarear", () -> press(GLFW.GLFW_KEY_Z, CTRL),
                () -> canvas().get(5, 7) == 6 && session().history().canRedo(), () -> row(7)));
        list.add(step("Pixel: Ctrl+Y refaz", () -> press(GLFW.GLFW_KEY_Y, CTRL), () -> canvas().get(5, 7) == 7,
                () -> row(7)));
        viewSteps(list);
    }

    /** A roda, o botão do meio e o 0 no quadro; a foto e o salvar. */
    private static void viewSteps(List<Step> list) {
        list.add(step("Pixel: a roda dá zoom no ponto do mouse", () -> {
            zoomBefore = session().view().zoom();
            int[] at = pixel().cellCenter(5, 6);
            MOUSE[0] = at[0];
            MOUSE[1] = at[1];
            moveMouse(at[0], at[1]);
            screen().mouseScrolled(at[0], at[1], 0, 1);
        }, () -> {
            int[] at = pixel().cellCenter(5, 6);
            int half = session().view().zoom() / 2;
            return session().view().zoom() > zoomBefore && Math.abs(at[0] - MOUSE[0]) <= half
                    && Math.abs(at[1] - MOUSE[1]) <= half;
        }, () -> "zoom " + zoomBefore + " → " + session().view().zoom()));
        list.add(step("Pixel: o botão do meio arrasta a vista", () -> {
            panBefore = session().view().panX();
            int[] at = pixel().boardCenter();
            screen().mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            screen().mouseDragged(at[0] + 10, at[1] + 6, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 10, 6);
            screen().mouseDragged(at[0] + 20, at[1] + 12, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 10, 6);
            screen().mouseReleased(at[0] + 20, at[1] + 12, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        }, () -> session().view().panX() == panBefore + 20 && canvas().get(5, 7) == 7,
                () -> "vista " + panBefore + " → " + session().view().panX()));
        list.add(step("Pixel: 0 enquadra", () -> type("0"), () -> session().view().zoom() < 48, () -> ""));
        list.add(step("Pixel: o mouse no meio da arte", () -> {
            int[] at = pixel().cellCenter(9, 9);
            moveMouse(at[0], at[1]);
        }, () -> true, () -> ""));
        E2eTvSteps.shot(list, "tela-pixel-editor");
        saveSteps(list);
    }

    /** O nome, o Salvar, o Editar da arte salva (a foto no escuro) e o Esc de volta ao editor da Tela. */
    private static void saveSteps(List<Step> list) {
        list.add(step("Pixel: dar o nome", () -> {
            pixel().focusName();
            for (int i = 0; i < 20; i++) {
                press(GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type(NAME);
            press(GLFW.GLFW_KEY_ENTER, 0);
        }, () -> pixel().name().equals(NAME), () -> "nome " + pixel().name()));
        list.add(step("Pixel: Salvar volta ao editor da Tela e a arte chega à biblioteca", () -> clickAt(
                pixel().buttonCenter("SAVE")), () -> !pixel().isOpen() && inClient() != null && inServer(),
                () -> "aberto " + pixel().isOpen() + ", no cliente " + (inClient() != null)));
        list.add(step("Pixel: Editar abre a arte salva", () -> {
            clickAt(pixel().artRowCenter(NAME));
            clickAt(pixel().artsButtonCenter("EDIT"));
        }, () -> pixel().isOpen() && canvas().get(5, 7) == 7 && pixel().name().equals(NAME),
                () -> "aberto " + pixel().isOpen()));
        E2eVariants.escuro(list, () -> {
            list.add(E2eDarkSteps.themeClick("Pixel: escuro", GuiTheme.DARK));
            E2eTvSteps.shot(list, "tela-pixel-editor-escuro");
            list.add(E2eDarkSteps.themeClick("Pixel: claro de novo", GuiTheme.LIGHT));
        });
        list.add(step("Pixel: Esc sem mudança volta ao editor da Tela", () -> press(GLFW.GLFW_KEY_ESCAPE, 0),
                () -> !pixel().isOpen() && Minecraft.getInstance().screen instanceof TvScreen,
                () -> "aberto " + pixel().isOpen()));
        list.add(step("Pixel: de volta à aba Peças", () -> clickAt(editor().probe().leftTabCenter("PIECES")),
                () -> editor().probe().leftTab().equals("PIECES"), () -> editor().probe().leftTab()));
    }

    private static void dragCells(int x0, int y0, int x1, int y1) throws StepFailure {
        int[] from = pixel().cellCenter(x0, y0);
        int[] to = pixel().cellCenter(x1, y1);
        drag(screen(), from[0], from[1], to[0], to[1]);
    }

    private static String row(int y) throws StepFailure {
        StringBuilder out = new StringBuilder("linha " + y + ": ");
        for (int x = 0; x < canvas().width(); x++) {
            out.append(Character.forDigit(canvas().get(x, y), 16));
        }
        return out.toString();
    }

    private static TvPixelArt inClient() {
        return TvLibraryClient.arts().stream().filter(a -> a.name().equals(NAME)).findFirst().orElse(null);
    }

    private static boolean inServer() throws Exception {
        UUID player = Minecraft.getInstance().player.getUUID();
        return onServer(server -> TvLibrary.get(server).arts(player).stream()
                .anyMatch(a -> a.name().equals(NAME) && a.pixel(5, 7) == 7 && a.pixel(8, 7) == 3));
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

    private static TvEditorTab editor() throws StepFailure {
        return screen().editor();
    }

    private static TvPixelEditor pixel() throws StepFailure {
        return editor().pixel();
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
