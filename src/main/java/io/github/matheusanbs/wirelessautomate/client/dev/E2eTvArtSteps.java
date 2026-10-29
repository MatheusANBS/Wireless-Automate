package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvPixelEditor;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * A pixel art no editor da Tela, depois do {@link E2eTvPixelSteps} (que deixa a arte "Logo" na biblioteca): na aba
 * Pixel art, Duplicar (a foto {@code tela-pixel-aba}, com as duas) e Remover a cópia em dois cliques; arrastar a arte
 * até a miniatura, que cria a Imagem 2× escolhida; na aba Estilo, a escala pela roda e pelo clique e o alinhamento em 9
 * posições (a foto {@code tela-pixel-imagem}); sem escolha, o Fundo no mosaico da mesma arte, em 1× e mais apagado (a
 * foto {@code tela-pixel-mosaico}); editar a arte e "Atualizar da biblioteca", que troca a cópia da Tela; e o salvar,
 * conferido no servidor; o exportar e importar por código ({@link E2eTvCodeSteps}); e a foto da Tela no mundo
 * ({@code tela-pixel-mundo}). No fim, o layout de antes volta e a tela abre de novo na aba Editar, como os passos
 * seguintes esperam.
 */
final class E2eTvArtSteps {
    private static final String NAME = "Logo";
    private static final String COPY = NAME + " (copy)";
    /** O pixel que a edição da arte muda (vazio na arte desenhada), com a cor dele. */
    private static final int EDIT_X = 0;
    private static final int EDIT_Y = 15;
    private static final int EDIT_COLOR = 6;

    private static TvLayout original = TvLayout.EMPTY;

    private E2eTvArtSteps() {
    }

    static void artSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv) {
        list.add(step("Arte: guardar o layout da Tela", () -> original = onServer(server ->
                ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).layout()), () -> true, () -> ""));
        libraryRows(list);
        imageSteps(list);
        mosaicSteps(list);
        refreshSteps(list, tv);
        E2eTvCodeSteps.codeSteps(list, tv);
        worldSteps(list, playerId, tv);
    }

    /** Duplicar, a foto da aba e Remover a cópia em dois cliques. */
    private static void libraryRows(List<Step> list) {
        list.add(step("Arte: aba Pixel art, Duplicar", () -> {
            clickAt(editor().probe().leftTabCenter("ARTS"));
            clickAt(pixel().artRowCenter(NAME));
            clickAt(pixel().artsButtonCenter("DUPLICATE"));
        }, () -> art(COPY) != null && art(NAME) != null, () -> "artes " + names()));
        list.add(step("Arte: a cópia escolhida", () -> clickAt(pixel().artRowCenter(COPY)), () -> true, () -> ""));
        E2eTvSteps.shot(list, "tela-pixel-aba");
        dark(list, "tela-pixel-aba-escuro");
        list.add(step("Arte: Remover arma no primeiro clique", () -> clickAt(pixel().artsButtonCenter("REMOVE")),
                () -> stepTicks >= 10 && art(COPY) != null, () -> "artes " + names()));
        list.add(step("Arte: o segundo clique remove", () -> clickAt(pixel().artsButtonCenter("REMOVE")),
                () -> art(COPY) == null && art(NAME) != null, () -> "artes " + names()));
    }

    /** Arrastar a arte até a miniatura e as opções da Imagem na aba Estilo. */
    private static void imageSteps(List<Step> list) {
        list.add(step("Arte: arrastar até a miniatura cria a Imagem", () -> {
            int[] from = pixel().artRowCenter(NAME);
            int[] to = editor().probe().canvasCenter();
            drag(screen(), from[0], from[1], to[0], to[1]);
        }, () -> {
            TvPiece piece = image();
            TvPixelArt art = art(NAME);
            return piece != null && art != null && piece.style().part().image().orElseThrow().equals(art.id())
                    && piece.style().part().scale() == 2 && piece.width() == 32 && piece.height() == 32
                    && art.equals(editor().draft().image(art.id()))
                    && editor().selection().first() == editor().draft().pieces().size() - 1;
        }, () -> "rascunho " + editor().draft().pieces()));
        list.add(step("Arte: aba Estilo", () -> clickAt(editor().probe().propsTabCenter("STYLE")),
                () -> editor().probe().propCenter("image.refresh") != null, () -> editor().probe().propsTab()));
        list.add(step("Arte: a roda na Escala desce para 1×", () -> {
            int[] at = prop("image.scale");
            screen().mouseScrolled(at[0], at[1], 0, 1);
        }, () -> image().style().part().scale() == 1, () -> "escala " + image().style().part().scale()));
        list.add(step("Arte: alinhar embaixo à direita", () -> clickAt(editor().probe().propChipCenter("image.align",
                8)), () -> image().style().align() == TvAlign.RIGHT
                        && image().style().part().valign() == TvPartStyle.VAlign.BOTTOM,
                () -> "alinhamento " + image().style().align() + " " + image().style().part().valign()));
        list.add(step("Arte: de volta ao meio e a 2×", () -> {
            clickAt(editor().probe().propChipCenter("image.align", 4));
            clickAt(prop("image.scale"));
        }, () -> image().style().align() == TvAlign.CENTER && image().style().part().scale() == 2
                && image().style().part().valign() == TvPartStyle.VAlign.MIDDLE,
                () -> "escala " + image().style().part().scale()));
        E2eTvSteps.shot(list, "tela-pixel-imagem");
        dark(list, "tela-pixel-imagem-escuro");
    }

    /** O Fundo em mosaico na base, a escala pela roda e a opacidade. */
    private static void mosaicSteps(List<Step> list) {
        list.add(step("Arte: Esc solta a Imagem", () -> press(GLFW.GLFW_KEY_ESCAPE),
                () -> editor().selection().isEmpty(), () -> "escolha " + editor().selection()));
        list.add(step("Arte: Fundo no mosaico", () -> {
            for (int i = 0; i < 4 && background().kind() != TvBackground.Kind.TILED; i++) {
                clickAt(prop("base.bg.kind"));
            }
        }, () -> background().kind() == TvBackground.Kind.TILED
                && background().image().orElseThrow().equals(art(NAME).id()), () -> "fundo " + background()));
        list.add(step("Arte: mosaico em 1× e a 40%", () -> {
            int[] scale = prop("base.mosaic.scale");
            screen().mouseScrolled(scale[0], scale[1], 0, 1);
            // o deslizante tem 64 px (a coluna menos o rótulo): 6 px à esquerda do meio é 25 / 62 dele, os 40%
            int[] opacity = prop("base.mosaic.opacity");
            clickAt(new int[] {opacity[0] - 6, opacity[1]});
        }, () -> background().scale() == 1 && background().opacity() == 40, () -> "fundo " + background()));
        E2eTvSteps.shot(list, "tela-pixel-mosaico");
        dark(list, "tela-pixel-mosaico-escuro");
    }

    /** Editar a arte na biblioteca e "Atualizar da biblioteca" na Imagem, que troca a cópia da Tela. */
    private static void refreshSteps(List<Step> list, Supplier<BlockPos> tv) {
        list.add(step("Arte: Editar a arte e mudar um pixel", () -> {
            clickAt(pixel().artRowCenter(NAME));
            clickAt(pixel().artsButtonCenter("EDIT"));
            clickAt(pixel().swatchCenter(EDIT_COLOR));
            type("b");
            clickAt(pixel().cellCenter(EDIT_X, EDIT_Y));
        }, () -> pixel().isOpen() && pixel().session().canvas().get(EDIT_X, EDIT_Y) == EDIT_COLOR, () -> "aberto "
                + pixel().isOpen()));
        list.add(step("Arte: Salvar a arte, que muda na biblioteca e não na Tela", () -> clickAt(pixel()
                .buttonCenter("SAVE")), () -> !pixel().isOpen() && art(NAME) != null
                        && art(NAME).pixel(EDIT_X, EDIT_Y) == EDIT_COLOR
                        && copy().pixel(EDIT_X, EDIT_Y) != EDIT_COLOR, () -> "aberto " + pixel().isOpen()));
        list.add(step("Arte: escolher a Imagem", () -> clickAt(editor().probe().pieceCenter(imageIndex())),
                () -> editor().selection().first() == imageIndex()
                        && editor().probe().propCenter("image.refresh") != null,
                () -> "escolha " + editor().selection()));
        list.add(step("Arte: Atualizar da biblioteca", () -> clickAt(prop("image.refresh")),
                () -> copy().pixel(EDIT_X, EDIT_Y) == EDIT_COLOR, () -> "cópia " + copy()));
        list.add(step("Arte: Salvar a Tela e conferir no servidor", () -> {
            press(GLFW.GLFW_KEY_ESCAPE);
            clickAt(editor().probe().barCenter("SAVE"));
        }, () -> {
            TvLayout saved = onServer(server -> ((TvBlockEntity) server.overworld()
                    .getBlockEntity(tv.get())).layout());
            TvPixelArt art = art(NAME);
            return !editor().dirty() && saved.images().size() == 1 && art != null
                    && art.equals(saved.image(art.id())) && saved.background().kind() == TvBackground.Kind.TILED
                    && saved.pieces().stream().anyMatch(p -> p.type() == TvPieceType.IMAGE);
        }, () -> "não salvo " + editor().dirty()));
    }

    /** A Tela no mundo com a Imagem e o mosaico; depois o layout de antes volta e a tela abre de novo. */
    private static void worldSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv) {
        list.add(close("Arte: fechar a tela"));
        list.add(E2eTvSteps.hideGui(playerId, true));
        list.add(E2eTvSteps.view("tela-pixel-mundo", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        list.add(E2eTvSteps.hideGui(playerId, false));
        list.add(step("Arte: o layout de antes volta", () -> onServer(server -> {
            ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(original);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(tv.get()) instanceof TvBlockEntity entity
                && entity.layout().equals(original), () -> "layout no cliente"));
        list.add(E2eTvSteps.open("Arte: abrir de novo"));
        list.add(E2eTvSteps.tab("Arte: aba Editar", TvScreen.Tab.EDIT));
    }

    /** A foto no escuro, só com a variante ligada. */
    private static void dark(List<Step> list, String file) {
        E2eVariants.escuro(list, () -> {
            list.add(E2eDarkSteps.themeClick("Arte: escuro", GuiTheme.DARK));
            E2eTvSteps.shot(list, file);
            list.add(E2eDarkSteps.themeClick("Arte: claro de novo", GuiTheme.LIGHT));
        });
    }

    private static TvPixelArt art(String name) {
        return TvLibraryClient.arts().stream().filter(a -> a.name().equals(name)).findFirst().orElse(null);
    }

    private static List<String> names() {
        return TvLibraryClient.arts().stream().map(TvPixelArt::name).toList();
    }

    private static int imageIndex() throws StepFailure {
        List<TvPiece> pieces = editor().draft().pieces();
        for (int i = pieces.size() - 1; i >= 0; i--) {
            if (pieces.get(i).type() == TvPieceType.IMAGE) {
                return i;
            }
        }
        throw new StepFailure("sem Imagem no rascunho");
    }

    private static TvPiece image() throws StepFailure {
        return editor().draft().pieces().get(imageIndex());
    }

    /** A cópia da arte que o rascunho guarda. */
    private static TvPixelArt copy() throws StepFailure {
        TvPixelArt copy = editor().draft().image(image().style().part().image().orElseThrow());
        if (copy == null) {
            throw new StepFailure("o rascunho não tem a cópia da arte");
        }
        return copy;
    }

    private static TvBackground background() throws StepFailure {
        return editor().draft().background();
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

    private static void press(int key) throws StepFailure {
        screen().keyPressed(key, 0, 0);
    }

    private static TvEditorTab editor() throws StepFailure {
        return screen().editor();
    }

    private static TvPixelEditor pixel() throws StepFailure {
        return editor().pixel();
    }

    private static void clickAt(int[] point) throws StepFailure {
        if (point == null) {
            throw new StepFailure("o controle não está à vista");
        }
        click(screen(), point[0], point[1]);
    }
}
