package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.List;
import java.util.UUID;
import org.lwjgl.glfw.GLFW;

/**
 * A grade de artes, dentro do {@link E2eTvArtSteps} (a arte "Logo" na biblioteca e nenhuma escolhida na aba Pixel
 * art): a Imagem pelo botão da paleta de Peças nasce sem arte (a foto {@code tela-pixel-grade-vazia}, com o pedido da
 * arte e o contorno tracejado); Duplicar a arte, escolher "Logo" na grade da seção Peça e trocar pela cópia (a cópia
 * antiga sai do layout); remover a cópia da biblioteca, e ela fica na grade como "Da Tela" (a foto
 * {@code tela-pixel-grade}); desfazer tudo. No mosaico da base: Duplicar de novo, escolher a cópia pela
 * grade (a foto {@code tela-pixel-grade-mosaico}), voltar ao "Logo" (a cópia sai do layout) e remover a cópia.
 */
final class E2eTvArtGridSteps {
    private static final String NAME = "Logo";
    private static final String COPY = NAME + " (copy)";
    private static final String PIECE_GRID = "image.arts";
    private static final String MOSAIC_GRID = "base.mosaic.arts";
    private static int undoBefore;
    private static TvLayout draftBefore = TvLayout.EMPTY;
    private static UUID copyId;

    private E2eTvArtGridSteps() {
    }

    /** A Imagem pela paleta, a grade da seção Peça e a arte "Da Tela"; no fim, o rascunho volta ao de antes. */
    static void pieceSteps(List<Step> list) {
        list.add(step("Grade: Imagem pela paleta, sem arte escolhida", () -> {
            undoBefore = editor().probe().undoSize();
            draftBefore = editor().draft();
            clickAt(editor().probe().paletteCenter(TvPieceType.IMAGE));
        }, () -> {
            TvPiece piece = last();
            return piece.type() == TvPieceType.IMAGE && piece.style().part().image().isEmpty()
                    && piece.width() == 32 && piece.height() == 32
                    && editor().selection().first() == editor().draft().pieces().size() - 1;
        }, () -> "rascunho " + editor().draft().pieces()));
        list.add(step("Grade: aba Estilo com a grade", () -> clickAt(editor().probe().propsTabCenter("STYLE")),
                () -> editor().probe().propCenter(PIECE_GRID) != null, () -> editor().probe().propsTab()));
        E2eTvSteps.shot(list, "tela-pixel-grade-vazia");
        duplicate(list, "Grade: Duplicar a arte");
        list.add(step("Grade: escolher a arte", () -> clickAt(editor().probe().artCellCenter(PIECE_GRID,
                art(NAME).id())), () -> {
                    TvPixelArt art = art(NAME);
                    TvPiece piece = last();
                    return art.id().equals(piece.style().part().image().orElse(null))
                            && art.equals(editor().draft().image(art.id())) && piece.style().part().scale() == 2;
                }, () -> "Imagem " + last().style().part()));
        list.add(step("Grade: trocar por outra", () -> {
            copyId = art(COPY).id();
            clickAt(editor().probe().artCellCenter(PIECE_GRID, copyId));
        }, () -> copyId.equals(last().style().part().image().orElse(null))
                && editor().draft().image(copyId) != null && editor().draft().image(art(NAME).id()) == null,
                () -> "artes do rascunho " + editor().draft().images()));
        remove(list, "Grade: a cópia sai da biblioteca");
        list.add(step("Grade: a cópia fica na grade como da Tela", () -> clickAt(editor().probe()
                .leftTabCenter("PIECES")), () -> editor().probe().artOnScreenOnly(PIECE_GRID, copyId)
                        && editor().probe().artCellCenter(PIECE_GRID, copyId) != null, () -> "grade"));
        E2eTvSteps.shot(list, "tela-pixel-grade");
        dark(list, "tela-pixel-grade-escuro");
        list.add(step("Grade: desfazer até o começo, de volta à aba Pixel art", () -> {
            for (int i = 0; i < 10 && editor().probe().undoSize() > undoBefore; i++) {
                screen().keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
            }
            clickAt(editor().probe().leftTabCenter("ARTS"));
        }, () -> editor().draft().equals(draftBefore), () -> "rascunho " + editor().draft().pieces()));
    }

    /** A arte do mosaico pela grade da base, logo depois de o Fundo virar mosaico do "Logo". */
    static void mosaicSteps(List<Step> list) {
        duplicate(list, "Grade: Duplicar para o mosaico");
        list.add(step("Grade: mosaico pela grade", () -> {
            copyId = art(COPY).id();
            clickAt(editor().probe().artCellCenter(MOSAIC_GRID, copyId));
        }, () -> copyId.equals(editor().draft().background().image().orElse(null))
                && editor().draft().image(copyId) != null, () -> "fundo " + editor().draft().background()));
        E2eTvSteps.shot(list, "tela-pixel-grade-mosaico");
        dark(list, "tela-pixel-grade-mosaico-escuro");
        list.add(step("Grade: mosaico de volta ao Logo", () -> clickAt(editor().probe().artCellCenter(MOSAIC_GRID,
                art(NAME).id())), () -> art(NAME).id().equals(editor().draft().background().image().orElse(null))
                        && editor().draft().image(copyId) == null, () -> "fundo " + editor().draft().background()));
        remove(list, "Grade: a cópia do mosaico sai da biblioteca");
    }

    /** Na aba Pixel art, Duplicar o "Logo". */
    private static void duplicate(List<Step> list, String name) {
        list.add(step(name, () -> {
            clickAt(editor().probe().leftTabCenter("ARTS"));
            clickAt(editor().pixel().artRowCenter(NAME));
            clickAt(editor().pixel().artsButtonCenter("DUPLICATE"));
        }, () -> art(COPY) != null && art(NAME) != null, () -> "artes " + TvLibraryClient.arts()));
    }

    /** Remove a cópia da biblioteca em dois cliques. */
    private static void remove(List<Step> list, String name) {
        list.add(step(name + ": arma", () -> {
            clickAt(editor().probe().leftTabCenter("ARTS"));
            clickAt(editor().pixel().artRowCenter(COPY));
            clickAt(editor().pixel().artsButtonCenter("REMOVE"));
        }, () -> stepTicks >= 10, () -> ""));
        list.add(step(name + ": remove", () -> clickAt(editor().pixel().artsButtonCenter("REMOVE")),
                () -> art(COPY) == null && art(NAME) != null, () -> "artes " + TvLibraryClient.arts()));
    }

    /** A foto no escuro, só com a variante ligada. */
    private static void dark(List<Step> list, String file) {
        E2eVariants.escuro(list, () -> {
            list.add(E2eDarkSteps.themeClick("Grade: escuro", GuiTheme.DARK));
            E2eTvSteps.shot(list, file);
            list.add(E2eDarkSteps.themeClick("Grade: claro de novo", GuiTheme.LIGHT));
        });
    }

    private static TvPixelArt art(String name) {
        return TvLibraryClient.arts().stream().filter(a -> a.name().equals(name)).findFirst().orElse(null);
    }

    private static TvPiece last() throws StepFailure {
        List<TvPiece> pieces = editor().draft().pieces();
        if (pieces.isEmpty()) {
            throw new StepFailure("rascunho vazio");
        }
        return pieces.get(pieces.size() - 1);
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
