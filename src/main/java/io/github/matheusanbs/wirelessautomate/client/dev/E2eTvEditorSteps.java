package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvSelection;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * O editor da Tela de Sinal no e2e, na Tela 4 × 2 vazia: pela aba Editar, insere um Valor e um Texto pela paleta,
 * arrasta o Texto 8 px para a direita e 16 para baixo, puxa a alça 8 × 4 px, fotografa, escolhe os dois (o segundo
 * com Shift), agrupa, fotografa, salva e confere no servidor que o layout chegou como no rascunho. Depois confere que
 * o editor cabe na escala 3 de 1366 × 768 (455 × 256 de GUI) sem texto cortado e, abaixo do mínimo, só mostra a
 * mensagem. Tudo com o mouse da janela ({@link E2eUi}).
 */
final class E2eTvEditorSteps {
    private static final int SMALL_W = 1366 / 3;
    private static final int SMALL_H = 768 / 3;

    private E2eTvEditorSteps() {
    }

    static void editorSteps(List<Step> list, Supplier<BlockPos> tv) {
        list.add(E2eTvSteps.tab("Editor: aba Editar", TvScreen.Tab.EDIT));
        list.add(new Step("Editor: abre vazio e cabe", STEP_TIMEOUT_MS, () -> {
        }, () -> editor().fits() && editor().draft().pieces().isEmpty(),
                () -> "cabe " + editor().fits() + ", peças " + editor().draft().pieces().size()));
        list.add(new Step("Editor: inserir um Valor e um Texto", STEP_TIMEOUT_MS, () -> {
            clickAt(editor().probe().paletteCenter(TvPieceType.VALUE));
            clickAt(editor().probe().paletteCenter(TvPieceType.TEXT));
        }, () -> {
            List<TvPiece> pieces = editor().draft().pieces();
            return pieces.size() == 2 && pieces.get(0).type() == TvPieceType.VALUE && box(pieces.get(0), 0, 0, 40, 12)
                    && pieces.get(1).type() == TvPieceType.TEXT && box(pieces.get(1), 40, 0, 48, 10)
                    && editor().selection().equals(TvSelection.of(1));
        }, () -> "rascunho " + editor().draft().pieces()));
        list.add(new Step("Editor: arrastar o Texto", STEP_TIMEOUT_MS, () -> {
            int[] from = editor().probe().pieceCenter(1);
            float scale = editor().probe().canvasScale();
            drag(screen(), from[0], from[1], from[0] + Math.round(8 * scale), from[1] + Math.round(16 * scale));
        }, () -> box(editor().draft().pieces().get(1), 48, 16, 48, 10), () -> "Texto " + editor().draft().pieces()
                .get(1)));
        list.add(new Step("Editor: puxar a alça", STEP_TIMEOUT_MS, () -> {
            int[] from = editor().probe().handleCenter();
            float scale = editor().probe().canvasScale();
            drag(screen(), from[0], from[1], from[0] + Math.round(8 * scale), from[1] + Math.round(4 * scale));
        }, () -> box(editor().draft().pieces().get(1), 48, 16, 56, 14), () -> "Texto " + editor().draft().pieces()
                .get(1)));
        E2eTvSteps.shot(list, "tela-editor");
        list.add(new Step("Editor: escolher os dois e agrupar", STEP_TIMEOUT_MS, () -> {
            clickAt(editor().probe().pieceCenter(0));
            TvEditorTab.e2eShift = true;
            try {
                clickAt(editor().probe().pieceCenter(1));
            } finally {
                TvEditorTab.e2eShift = false;
            }
            if (!editor().selection().equals(TvSelection.of(0, 1))) {
                throw new StepFailure("a escolha não somou com Shift: " + editor().selection());
            }
            clickAt(editor().probe().barCenter("GROUP"));
        }, () -> {
            List<TvPiece> pieces = editor().draft().pieces();
            return pieces.size() == 2 && pieces.get(0).group() != 0 && pieces.get(0).group() == pieces.get(1).group()
                    && editor().selection().size() == 2;
        }, () -> "rascunho " + editor().draft().pieces()));
        E2eTvSteps.shot(list, "tela-editor-grupo");
        list.add(new Step("Editor: salvar e conferir no servidor", STEP_TIMEOUT_MS,
                () -> clickAt(editor().probe().barCenter("SAVE")), () -> {
                    TvLayout saved = onServer(server -> ((TvBlockEntity) server.overworld().getBlockEntity(tv.get()))
                            .layout());
                    return saved.equals(editor().draft()) && !editor().dirty() && saved.pieces().size() == 2;
                }, () -> "não salvo " + editor().dirty() + ", servidor " + savedPieces(tv)));
        list.add(new Step("Editor: Esc solta a escolha", STEP_TIMEOUT_MS,
                () -> screen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0),
                () -> editor().selection().isEmpty() && Minecraft.getInstance().screen instanceof TvScreen,
                () -> "escolha " + editor().selection()));
        E2eTvEditorViewSteps.viewSteps(list);
        E2eTvEditorPropsSteps.keySteps(list);
        E2eTvEditorPropsSteps.propsSteps(list, tv);
        E2eTvLibrarySteps.componentSteps(list);
    }

    /** O editor na escala 3 de 1366 × 768 e abaixo do mínimo; a janela volta ao tamanho dela no fim. */
    static void smallSteps(List<Step> list) {
        list.add(new Step("Editor: na escala 3 de 1366 × 768", STEP_TIMEOUT_MS,
                () -> screen().init(Minecraft.getInstance(), SMALL_W, SMALL_H),
                () -> stepTicks >= 2 && editor().fits() && GuiText.clipCount() == 0,
                () -> "cabe " + editor().fits() + ", " + GuiText.clipCount() + " texto(s) cortado(s)"));
        list.add(capture("tela-editor-escala3"));
        list.add(new Step("Editor: abaixo do mínimo só a mensagem", STEP_TIMEOUT_MS,
                () -> screen().init(Minecraft.getInstance(), 1366 / 4, 768 / 4),
                () -> stepTicks >= 2 && !editor().fits() && GuiText.clipCount() == 0,
                () -> "cabe " + editor().fits() + ", " + GuiText.clipCount() + " texto(s) cortado(s)"));
        list.add(new Step("Editor: de volta ao tamanho da janela", STEP_TIMEOUT_MS, () -> {
            var window = Minecraft.getInstance().getWindow();
            screen().init(Minecraft.getInstance(), window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }, () -> editor().fits(), () -> "cabe " + editor().fits()));
    }

    private static boolean box(TvPiece piece, int x, int y, int w, int h) {
        return piece.x() == x && piece.y() == y && piece.width() == w && piece.height() == h;
    }

    private static List<TvPiece> savedPieces(Supplier<BlockPos> tv) throws Exception {
        return onServer(server -> ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).layout().pieces());
    }

    private static TvScreen screen() throws StepFailure {
        return E2eTvSteps.screen();
    }

    private static TvEditorTab editor() throws StepFailure {
        return screen().editor();
    }

    private static void clickAt(int[] point) throws StepFailure {
        click(screen(), point[0], point[1]);
    }
}
