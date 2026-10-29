package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvCodeNotice;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.gametest.TvCodeExamples;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvCodes;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Exportar e importar por código, depois de o {@link E2eTvArtSteps} salvar a Tela com a Imagem e o mosaico da arte
 * "Logo": Exportar o layout, esvaziar a Tela pelo servidor, Importar de volta (as peças e a arte voltam), desfazer e
 * refazer, salvar e conferir no servidor; colar lixo não muda nada (a foto {@code tela-codigo}, com o aviso); Copiar
 * como texto e colar o layout legível escrito à mão ({@link TvCodeExamples}, a foto {@code tela-codigo-legivel}),
 * desfeito em seguida; na aba Pixel art, Exportar a arte e Importar, que cria uma arte nova, o lixo, que não cria
 * nada, Copiar como texto e colar a arte escrita à mão (as importadas são removidas). A área de transferência do
 * sistema volta ao que era no fim.
 */
final class E2eTvCodeSteps {
    private static final String NAME = "Logo";
    private static String clipboard = "";
    private static TvLayout exported = TvLayout.EMPTY;
    private static TvLayout before = TvLayout.EMPTY;
    private static Set<UUID> artsBefore = Set.of();
    private static UUID imported;

    private E2eTvCodeSteps() {
    }

    static void codeSteps(List<Step> list, Supplier<BlockPos> tv) {
        list.add(step("Código: guardar a área de transferência", () -> clipboard = keyboard().getClipboard(),
                () -> true, () -> ""));
        layoutSteps(list, tv);
        artSteps(list);
        list.add(step("Código: devolver a área de transferência", () -> keyboard().setClipboard(clipboard),
                () -> true, () -> ""));
    }

    private static void layoutSteps(List<Step> list, Supplier<BlockPos> tv) {
        list.add(step("Código: Exportar o layout da Tela", () -> clickAt(prop("base.layout.export")), () -> {
            TvCodes.Layout read = TvCodes.readLayout(keyboard().getClipboard()).value();
            if (read == null || TvCodeNotice.current() != TvCodeNotice.Kind.COPIED) {
                return false;
            }
            exported = read.layout();
            return exported.equals(editor().draft().withoutUnusedImages()) && exported.images().size() == 1
                    && exported.pieces().stream().anyMatch(p -> p.type() == TvPieceType.IMAGE);
        }, () -> "aviso " + TvCodeNotice.current()));
        list.add(step("Código: a Tela fica vazia", () -> onServer(server -> {
            ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(TvLayout.EMPTY);
            return null;
        }), () -> editor().draft().blank() && !editor().dirty(), () -> "rascunho " + editor().draft().pieces()));
        list.add(step("Código: Importar devolve as peças e a arte", () -> clickAt(prop("base.layout.import")),
                () -> sameAsExported(editor().draft()) && editor().dirty(),
                () -> "rascunho " + editor().draft().pieces()));
        list.add(step("Código: desfazer volta à Tela vazia", () -> ctrl(GLFW.GLFW_KEY_Z),
                () -> editor().draft().blank(), () -> "rascunho " + editor().draft().pieces()));
        list.add(step("Código: refazer", () -> ctrl(GLFW.GLFW_KEY_Y), () -> sameAsExported(editor().draft()),
                () -> "rascunho " + editor().draft().pieces()));
        list.add(step("Código: Salvar e conferir no servidor", () -> clickAt(editor().probe().barCenter("SAVE")),
                () -> !editor().dirty() && sameAsExported(onServer(server -> ((TvBlockEntity) server.overworld()
                        .getBlockEntity(tv.get())).layout())), () -> "não salvo " + editor().dirty()));
        list.add(step("Código: colar lixo não muda o rascunho", () -> {
            before = editor().draft();
            keyboard().setClipboard("lixo");
            clickAt(prop("base.layout.import"));
        }, () -> editor().draft() == before && TvCodeNotice.current() == TvCodeNotice.Kind.INVALID,
                () -> "aviso " + TvCodeNotice.current()));
        E2eTvSteps.shot(list, "tela-codigo");
        list.add(step("Código: Copiar como texto", () -> clickAt(prop("base.layout.text")), () -> {
            String text = keyboard().getClipboard();
            TvCodes.Layout read = TvCodes.readLayout(text).value();
            return text.startsWith("WA-TELA1\n") && read != null && sameAsExported(read.layout());
        }, () -> "aviso " + TvCodeNotice.current()));
        list.add(step("Código: colar o layout escrito à mão", () -> {
            keyboard().setClipboard(TvCodeExamples.LAYOUT);
            clickAt(prop("base.layout.import"));
        }, () -> {
            TvLayout draft = editor().draft();
            return draft.pieces().size() == 3 && draft.pieces().get(0).type() == TvPieceType.TEXT
                    && draft.pieces().get(1).binding().isPresent()
                    && draft.pieces().get(2).type() == TvPieceType.IMAGE && draft.images().size() == 1
                    && draft.images().get(0).name().equals("Coração") && draft.prunedImages().equals(draft);
        }, () -> "rascunho " + editor().draft().pieces() + " aviso " + TvCodeNotice.current()));
        E2eTvSteps.shot(list, "tela-codigo-legivel");
        list.add(step("Código: desfazer volta ao salvo", () -> ctrl(GLFW.GLFW_KEY_Z),
                () -> sameAsExported(editor().draft()) && !editor().dirty(), () -> "não salvo " + editor().dirty()));
    }

    private static void artSteps(List<Step> list) {
        list.add(step("Código: Exportar a arte", () -> {
            clickAt(editor().probe().leftTabCenter("ARTS"));
            clickAt(editor().pixel().artRowCenter(NAME));
            clickAt(editor().pixel().artsButtonCenter("EXPORT"));
        }, () -> art(NAME) != null && art(NAME).equals(TvCodes.readArt(keyboard().getClipboard()).value())
                && TvCodeNotice.current() == TvCodeNotice.Kind.COPIED, () -> "aviso " + TvCodeNotice.current()));
        list.add(step("Código: Importar cria uma arte nova", () -> {
            artsBefore = TvLibraryClient.arts().stream().map(TvPixelArt::id).collect(Collectors.toSet());
            clickAt(editor().pixel().artsButtonCenter("IMPORT"));
        }, () -> {
            TvPixelArt logo = art(NAME);
            for (TvPixelArt art : TvLibraryClient.arts()) {
                if (!artsBefore.contains(art.id()) && art.name().equals(NAME) && art.width() == logo.width()
                        && art.height() == logo.height() && Arrays.equals(art.palette(), logo.palette())
                        && Arrays.equals(art.pixels(), logo.pixels())) {
                    imported = art.id();
                    return true;
                }
            }
            return false;
        }, () -> "artes " + TvLibraryClient.arts()));
        list.add(step("Código: colar lixo não cria arte", () -> {
            keyboard().setClipboard(TvCodes.ART_PREFIX + "lixo");
            clickAt(editor().pixel().artsButtonCenter("IMPORT"));
        }, () -> stepTicks >= 10 && TvLibraryClient.arts().size() == artsBefore.size() + 1
                && TvCodeNotice.current() == TvCodeNotice.Kind.INVALID, () -> "artes " + TvLibraryClient.arts()));
        remove(list);
        list.add(step("Código: Copiar a arte como texto", () -> {
            clickAt(editor().pixel().artRowCenter(NAME));
            clickAt(editor().pixel().artsButtonCenter("TEXT"));
        }, () -> {
            String text = keyboard().getClipboard();
            TvPixelArt read = TvCodes.readArt(text).value();
            return text.startsWith("WA-ARTE1\n") && read != null && Arrays.equals(read.pixels(), art(NAME).pixels());
        }, () -> "aviso " + TvCodeNotice.current()));
        list.add(step("Código: colar a arte escrita à mão", () -> {
            keyboard().setClipboard(TvCodeExamples.ART);
            clickAt(editor().pixel().artsButtonCenter("IMPORT"));
        }, () -> {
            TvPixelArt heart = art("Coração");
            if (heart == null) {
                return false;
            }
            imported = heart.id();
            return heart.width() == 16 && heart.pixel(2, 4) == 5;
        }, () -> "artes " + TvLibraryClient.arts() + " aviso " + TvCodeNotice.current()));
        remove(list);
    }

    /** Remove a arte importada em dois cliques. */
    private static void remove(List<Step> list) {
        list.add(step("Código: Remover a arte importada arma", () -> {
            clickAt(editor().pixel().artRowCenter(imported));
            clickAt(editor().pixel().artsButtonCenter("REMOVE"));
        }, () -> stepTicks >= 10, () -> ""));
        list.add(step("Código: o segundo clique remove", () -> clickAt(editor().pixel().artsButtonCenter("REMOVE")),
                () -> TvLibraryClient.arts().stream().noneMatch(a -> a.id().equals(imported)) && art(NAME) != null,
                () -> "artes " + TvLibraryClient.arts()));
    }

    /** As peças, as artes, o fundo e o tema do layout exportado. */
    private static boolean sameAsExported(TvLayout layout) {
        return layout.pieces().equals(exported.pieces()) && layout.images().equals(exported.images())
                && layout.background().equals(exported.background()) && layout.theme().equals(exported.theme());
    }

    private static TvPixelArt art(String name) {
        return TvLibraryClient.arts().stream().filter(a -> a.name().equals(name)).findFirst().orElse(null);
    }

    private static KeyboardHandler keyboard() {
        return Minecraft.getInstance().keyboardHandler;
    }

    private static int[] prop(String id) throws StepFailure {
        int[] at = editor().probe().propCenter(id);
        if (at == null) {
            throw new StepFailure("o controle " + id + " não está à vista");
        }
        return at;
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

    private static void ctrl(int key) throws StepFailure {
        screen().keyPressed(key, 0, GLFW.GLFW_MOD_CONTROL);
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
