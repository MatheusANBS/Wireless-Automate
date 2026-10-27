package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvSelection;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvRuleOp;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * O resto do editor da Tela de Sinal no e2e, depois do salvar de {@link E2eTvEditorSteps} (a Tela 4 × 2 com um Valor e
 * um Texto agrupados): os atalhos pelo teclado (Ctrl+A, Delete, Ctrl+Z, Ctrl+Y e Ctrl+Shift+Z, as setas, Ctrl+C e
 * Ctrl+V), as abas Dado, Formato, Estilo e Regras ligando o Valor a uma rede dos Fluxos, trocando formato, cor do tema
 * e cor fixa e escrevendo uma regra, o Ctrl+S que confere o layout no servidor, e o colar numa segunda Tela (a 1 × 1).
 * Tudo pelos mesmos caminhos do mouse e do teclado ({@link E2eUi}).
 */
final class E2eTvEditorPropsSteps {
    private static final int CTRL = GLFW.GLFW_MOD_CONTROL;

    private E2eTvEditorPropsSteps() {
    }

    /** Os atalhos do teclado sobre o layout salvo (Valor e Texto agrupados), que voltam ao salvo no fim. */
    static void keySteps(List<Step> list) {
        list.add(step("Atalhos: Ctrl+A escolhe tudo", () -> press(GLFW.GLFW_KEY_A, CTRL),
                () -> editor().selection().size() == 2, () -> "escolha " + editor().selection()));
        list.add(step("Atalhos: Delete remove", () -> press(GLFW.GLFW_KEY_DELETE, 0),
                () -> editor().draft().pieces().isEmpty() && editor().dirty(),
                () -> "peças " + editor().draft().pieces().size()));
        list.add(step("Atalhos: Ctrl+Z desfaz", () -> press(GLFW.GLFW_KEY_Z, CTRL),
                () -> editor().draft().pieces().size() == 2 && !editor().dirty(),
                () -> "peças " + editor().draft().pieces().size() + ", mudou " + editor().dirty()));
        list.add(step("Atalhos: Ctrl+Y refaz", () -> press(GLFW.GLFW_KEY_Y, CTRL),
                () -> editor().draft().pieces().isEmpty(), () -> "peças " + editor().draft().pieces().size()));
        list.add(step("Atalhos: Ctrl+Z de novo", () -> press(GLFW.GLFW_KEY_Z, CTRL),
                () -> editor().draft().pieces().size() == 2, () -> "peças " + editor().draft().pieces().size()));
        list.add(step("Atalhos: Ctrl+Shift+Z refaz", () -> press(GLFW.GLFW_KEY_Z, CTRL | GLFW.GLFW_MOD_SHIFT),
                () -> editor().draft().pieces().isEmpty(), () -> "peças " + editor().draft().pieces().size()));
        list.add(step("Atalhos: Ctrl+Z volta ao salvo", () -> press(GLFW.GLFW_KEY_Z, CTRL),
                () -> editor().draft().pieces().size() == 2 && !editor().dirty(),
                () -> "peças " + editor().draft().pieces().size()));
        int[] before = new int[1];
        list.add(step("Atalhos: as setas movem 4 px e, com Alt, 1 px", () -> {
            before[0] = editor().draft().pieces().get(1).x();
            press(GLFW.GLFW_KEY_RIGHT, 0);
            press(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_MOD_ALT);
        }, () -> editor().draft().pieces().get(1).x() == before[0] + 5,
                () -> "x " + editor().draft().pieces().get(1).x() + " de " + before[0]));
        list.add(step("Atalhos: um Ctrl+Z desfaz as duas setas (mudança só)", () -> press(GLFW.GLFW_KEY_Z, CTRL),
                () -> editor().draft().pieces().get(1).x() == before[0] && !editor().dirty(),
                () -> "x " + editor().draft().pieces().get(1).x()));
        list.add(step("Atalhos: Ctrl+C e Ctrl+V colam o grupo com grupo novo", () -> {
            press(GLFW.GLFW_KEY_C, CTRL);
            press(GLFW.GLFW_KEY_V, CTRL);
        }, () -> {
            List<TvPiece> pieces = editor().draft().pieces();
            return pieces.size() == 4 && pieces.get(2).group() != 0 && pieces.get(2).group() == pieces.get(3).group()
                    && pieces.get(2).group() != pieces.get(0).group() && editor().selection().size() == 2;
        }, () -> "peças " + editor().draft().pieces()));
        list.add(step("Atalhos: Ctrl+Z tira a cópia", () -> press(GLFW.GLFW_KEY_Z, CTRL),
                () -> editor().draft().pieces().size() == 2 && !editor().dirty(),
                () -> "peças " + editor().draft().pieces().size()));
    }

    /** As quatro abas: liga o Valor à rede dos Fluxos, formata, pinta, escreve uma regra e salva com Ctrl+S. */
    static void propsSteps(List<Step> list, Supplier<BlockPos> tv) {
        list.add(step("Abas: escolher o Valor pelas Camadas", () -> {
            click(screen(), editor().layerCenter(0)[0], editor().layerCenter(0)[1]);
            clickAt(editor().propsTabCenter("DATA"));
        }, () -> editor().selection().equals(TvSelection.of(0)) && "DATA".equals(editor().propsTab()),
                () -> "escolha " + editor().selection() + ", aba " + editor().propsTab()));
        list.add(step("Dado: escolher a rede dos Fluxos", () -> {
            UUID network = E2eFlowsSteps.flowsNetworkId();
            int[] at = editor().propNetworkCenter("data.network", network);
            if (at == null) {
                throw new StepFailure("a rede dos Fluxos não está na lista");
            }
            clickAt(at);
        }, () -> editor().draft().pieces().get(0).binding().map(b -> b.network().equals(E2eFlowsSteps.flowsNetworkId()))
                .orElse(false), () -> "ligação " + editor().draft().pieces().get(0).binding()));
        list.add(step("Dado: a métrica avança para Sai", () -> clickAt(editor().propCenter("data.metric")),
                () -> editor().draft().pieces().get(0).binding().map(b -> b.metric() == TvMetric.OUT).orElse(false),
                () -> "ligação " + editor().draft().pieces().get(0).binding()));
        E2eTvSteps.shot(list, "tela-editor-dado");
        list.add(step("Formato: aba, casas e abreviar", () -> {
            clickAt(editor().propsTabCenter("FORMAT"));
            clickAt(editor().propArrowCenter("format.decimals", true));
            clickAt(editor().propCenter("format.abbreviate"));
        }, () -> {
            var format = editor().draft().pieces().get(0).format();
            return format.decimals() == 2 && !format.abbreviate();
        }, () -> "formato " + editor().draft().pieces().get(0).format()));
        E2eTvSteps.shot(list, "tela-editor-formato");
        list.add(step("Estilo: cor do tema, cor fixa e hexadecimal", () -> {
            clickAt(editor().propsTabCenter("STYLE"));
            clickAt(editor().propSwatchCenter("style.color.sw", TvColor.HIGH));
            clickAt(editor().propSwatchCenter("style.background.sw", 0xFFE39A2E));
            if (!editor().focusProp("style.border.hex")) {
                throw new StepFailure("o campo hexadecimal não abriu");
            }
            type("#336699");
            screen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> {
            var style = editor().draft().pieces().get(0).style();
            return style.color() == TvColor.theme(TvColor.HIGH) && style.background() == 0xFFE39A2E
                    && style.border() == 0xFF336699 && !editor().textFocused();
        }, () -> "estilo " + editor().draft().pieces().get(0).style() + ", foco " + editor().textFocused()));
        list.add(step("Estilo: de volta ao topo da aba", () -> clickAt(editor().propsTabCenter("STYLE")),
                () -> true, () -> "aba " + editor().propsTab()));
        E2eTvSteps.shot(list, "tela-editor-estilo");
        list.add(step("Regras: nova regra, comparação e valor", () -> {
            clickAt(editor().propsTabCenter("RULES"));
            clickAt(editor().propCenter("rules.add"));
        }, () -> editor().draft().pieces().get(0).rules().size() == 1,
                () -> "regras " + editor().draft().pieces().get(0).rules()));
        list.add(step("Regras: maior que 5", () -> {
            clickAt(editor().propChipCenter("rule0.op", 1));
            if (!editor().focusProp("rule0.value")) {
                throw new StepFailure("o campo do valor não abriu");
            }
            type("5");
            screen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> {
            var rules = editor().draft().pieces().get(0).rules();
            return rules.size() == 1 && rules.get(0).op() == TvRuleOp.GT && rules.get(0).value() == 5;
        }, () -> "regras " + editor().draft().pieces().get(0).rules()));
        E2eTvSteps.shot(list, "tela-editor-regras");
        list.add(step("Salvar com Ctrl+S e conferir no servidor", () -> press(GLFW.GLFW_KEY_S, CTRL), () -> {
            TvLayout saved = onServer(server -> ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).layout());
            return saved.equals(editor().draft()) && !editor().dirty() && saved.pieces().get(0).binding().isPresent()
                    && saved.pieces().get(0).rules().size() == 1;
        }, () -> "não salvo " + editor().dirty() + ", servidor " + serverPiece(tv)));
    }

    /** Copia tudo, fecha, abre a Tela 1 × 1 (a segunda), cola, fotografa e desfaz o colar. */
    static void pasteSteps(List<Step> list, Supplier<BlockPos> small) {
        list.add(step("Colar: Ctrl+A, Ctrl+C e soltar a escolha", () -> {
            press(GLFW.GLFW_KEY_A, CTRL);
            press(GLFW.GLFW_KEY_C, CTRL);
            screen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }, () -> editor().selection().isEmpty(), () -> "escolha " + editor().selection()));
        list.add(E2eSteps.close("Colar: fechar a primeira Tela"));
        list.add(new Step("Colar: abrir a segunda Tela", STEP_TIMEOUT_MS, () -> useOn(small.get()),
                () -> Minecraft.getInstance().screen instanceof TvScreen s && s.getMenu().pos().equals(small.get()),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(E2eTvSteps.tab("Colar: aba Editar da segunda Tela", TvScreen.Tab.EDIT));
        list.add(step("Colar: Ctrl+V na segunda Tela", () -> press(GLFW.GLFW_KEY_V, CTRL), () -> {
            List<TvPiece> pieces = editor().draft().pieces();
            return pieces.size() == 2 && pieces.stream().allMatch(p -> p.x() + p.width() <= 32 && p.y() + p.height() <= 32
                    && p.group() != 0) && editor().dirty();
        }, () -> "peças " + editor().draft().pieces()));
        E2eTvSteps.shot(list, "tela-editor-colado");
        list.add(step("Colar: Ctrl+Z desfaz o colar", () -> press(GLFW.GLFW_KEY_Z, CTRL),
                () -> editor().draft().pieces().isEmpty() && !editor().dirty(),
                () -> "peças " + editor().draft().pieces().size()));
    }

    private static List<TvPiece> serverPiece(Supplier<BlockPos> tv) throws Exception {
        return onServer(server -> ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).layout().pieces());
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

    private static void press(int key, int modifiers) throws StepFailure {
        screen().keyPressed(key, 0, modifiers);
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
