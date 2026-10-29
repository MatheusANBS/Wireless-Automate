package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvTemplates;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Os componentes e os temas da biblioteca no editor da Tela de Sinal, no e2e. Na primeira Tela (4 × 2, com o Valor
 * ligado e o Texto salvos): salva a escolha como componente "Meu cartao", insere o Cartão de número pronto (o texto de
 * exemplo vem no idioma de quem insere) e desfaz. Na segunda (1 × 1, aberta pelo colar): insere o componente do jogador
 * (a ligação vai junto), põe a base (o tema Fósforo pela lista aberta, com a foto dela nos dois temas da interface, uma cor
 * da paleta editada, "Salvar tema" com o nome, trocar o tema de ida e volta, fundo em degradê e linhas) e salva a Tela; a foto na parede ({@code tela-tema-proprio}) é do
 * {@link E2eTvSteps}. Tudo pelo mouse e pelo teclado.
 */
final class E2eTvLibrarySteps {
    private static final String COMPONENT = "Meu cartao";
    private static final String THEME = "Meu tema";
    private static final int PINK = 0xFFFF2D95;
    private static final int CTRL = GLFW.GLFW_MOD_CONTROL;

    private E2eTvLibrarySteps() {
    }

    /** Na primeira Tela: salvar um componente, inserir um pronto e desfazer. */
    static void componentSteps(List<Step> list) {
        list.add(step("Componentes: escolher tudo e salvar como componente", () -> {
            press(GLFW.GLFW_KEY_A, CTRL);
            clickAt(editor().probe().barCenter("SAVE_COMPONENT"));
        }, () -> editor().selection().size() == 2 && editor().probe().componentNaming(),
                () -> "escolha " + editor().selection() + ", nomeando " + editor().probe().componentNaming()));
        list.add(step("Componentes: dar o nome e confirmar", () -> {
            type(COMPONENT);
            press(GLFW.GLFW_KEY_ENTER, 0);
        }, () -> !editor().probe().componentNaming() && inClient(COMPONENT) && inServer(COMPONENT),
                () -> "no cliente " + inClient(COMPONENT) + ", no servidor " + inServer(COMPONENT)));
        list.add(step("Componentes: inserir o Cartão de número pronto", () -> clickAt(editor().probe().componentCenter(
                TvTemplates.NUMBER_CARD)), () -> {
            List<TvPiece> pieces = editor().draft().pieces();
            return pieces.size() == 5 && pieces.get(2).group() != 0 && pieces.get(2).group() == pieces.get(4).group()
                    && pieces.get(3).style().text().equals("Label") && editor().selection().size() == 3;
        }, () -> "peças " + editor().draft().pieces()));
        E2eTvSteps.shot(list, "tela-editor-componentes");
        list.add(step("Componentes: Ctrl+Z tira o pronto", () -> press(GLFW.GLFW_KEY_Z, CTRL),
                () -> editor().draft().pieces().size() == 2 && !editor().dirty(),
                () -> "peças " + editor().draft().pieces().size()));
    }

    /** Na segunda Tela, vazia e aberta no Editar: o componente do jogador, o tema próprio e o salvar. */
    static void themeSteps(List<Step> list, Supplier<BlockPos> small) {
        list.add(step("Biblioteca: o componente chegou à segunda Tela", () -> {
        }, () -> inClient(COMPONENT), () -> "componentes " + TvLibraryClient.components().size()));
        list.add(step("Componentes: inserir o do jogador (a ligação vai junto)", () -> clickAt(editor()
                .probe().componentCenter(COMPONENT)), () -> {
            List<TvPiece> pieces = editor().draft().pieces();
            return pieces.size() == 2 && pieces.get(0).group() != 0 && pieces.get(0).group() == pieces.get(1).group()
                    && pieces.get(0).binding().isPresent();
        }, () -> "peças " + editor().draft().pieces()));
        list.add(step("Base: soltar a escolha mostra o tema e o fundo", () -> editor().escape(),
                () -> editor().selection().isEmpty() && editor().probe().propCenter("base.theme") != null,
                () -> "escolha " + editor().selection()));
        list.add(E2eTvDropdown.openStep("Tema: abrir a lista", "base.theme"));
        E2eTvSteps.shot(list, "tela-dropdown");
        list.add(step("Tema: escolher o Fósforo pelo mouse", () -> clickAt(editor().probe().dropdownRowCenter(
                TvTheme.PRESETS.indexOf(TvTheme.PHOSPHOR))), () -> editor().draft().theme().sameColors(TvTheme.PHOSPHOR)
                        && !editor().probe().popupOpen(), () -> "tema " + editor().draft().theme()));
        E2eVariants.escuro(list, () -> {
            list.add(E2eDarkSteps.themeClick("Tema: escuro", GuiTheme.DARK));
            list.add(E2eTvDropdown.openStep("Tema: abrir a lista no escuro", "base.theme"));
            E2eTvSteps.shot(list, "tela-dropdown-escuro");
            list.add(step("Tema: Esc fecha a lista sem mudar", () -> editor().escape(),
                    () -> !editor().probe().popupOpen() && editor().draft().theme().sameColors(TvTheme.PHOSPHOR),
                    () -> "tema " + editor().draft().theme()));
            list.add(E2eDarkSteps.themeClick("Tema: claro de novo", GuiTheme.LIGHT));
        });
        list.add(step("Tema: editar a cor Alta pelo hexadecimal", () -> {
            if (!editor().probe().focusProp("base.color" + 5)) {
                throw new StepFailure("o campo da cor Alta não abriu");
            }
            press(GLFW.GLFW_KEY_END, 0);
            for (int i = 0; i < 9; i++) {
                press(GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type("#FF2D95");
            press(GLFW.GLFW_KEY_ENTER, 0);
        }, () -> editor().draft().theme().high() == PINK && !editor().textFocused(),
                () -> "tema " + editor().draft().theme() + ", foco " + editor().textFocused()));
        list.add(step("Tema: Salvar tema com o nome", () -> {
            if (!editor().probe().focusProp("base.theme_name")) {
                throw new StepFailure("o campo do nome não abriu");
            }
            type(THEME);
            press(GLFW.GLFW_KEY_ENTER, 0);
            clickAt(editor().probe().propCenter("base.theme.save"));
        }, () -> themeInClient() != null && themeInServer(),
                () -> "no cliente " + (themeInClient() != null) + ", no servidor " + themeInServer()));
        list.add(step("Tema: fundo em degradê e linhas de varredura", () -> {
            E2eTvDropdown.pick("base.bg.kind", TvBackground.Kind.GRADIENT.ordinal());
            clickAt(editor().probe().propCenter("base.scanlines"));
        }, () -> editor().draft().background().kind() == TvBackground.Kind.GRADIENT && editor().draft().scanlines(),
                () -> "fundo " + editor().draft().background() + ", linhas " + editor().draft().scanlines()));
        list.add(step("Tema: voltar ao topo da base", () -> editor().probe().propCenter("base.theme"), () -> true, () -> ""));
        E2eTvSteps.shot(list, "tela-editor-tema");
        list.add(step("Tema: trocar para o Grafite", () -> E2eTvDropdown.pick("base.theme",
                TvTheme.PRESETS.indexOf(TvTheme.GRAPHITE)), () -> editor().draft().theme().sameColors(TvTheme.GRAPHITE),
                () -> "tema " + editor().draft().theme()));
        list.add(step("Tema: de volta ao do jogador pela lista", () -> E2eTvDropdown.pick("base.theme", THEME), () -> {
            TvTheme mine = themeInClient();
            return mine != null && editor().draft().theme().sameColors(mine) && mine.high() == PINK;
        }, () -> "tema " + editor().draft().theme()));
        list.add(step("Tema: salvar com Ctrl+S e conferir no servidor", () -> press(GLFW.GLFW_KEY_S, CTRL), () -> {
            TvLayout saved = onServer(server -> ((TvBlockEntity) server.overworld().getBlockEntity(small.get()))
                    .layout());
            return saved.equals(editor().draft()) && !editor().dirty() && saved.theme().high() == PINK
                    && saved.pieces().size() == 2;
        }, () -> "não salvo " + editor().dirty()));
    }

    private static boolean inClient(String name) {
        return TvLibraryClient.components().stream().anyMatch(c -> c.name().equals(name));
    }

    private static boolean inServer(String name) throws Exception {
        UUID player = Minecraft.getInstance().player.getUUID();
        return onServer(server -> TvLibrary.get(server).components(player).stream()
                .anyMatch((TvComponent c) -> c.name().equals(name)));
    }

    private static TvTheme themeInClient() {
        return TvLibraryClient.themes().stream().filter(t -> t.name().equals(THEME)).findFirst().orElse(null);
    }

    private static boolean themeInServer() throws Exception {
        UUID player = Minecraft.getInstance().player.getUUID();
        return onServer(server -> TvLibrary.get(server).themes(player).stream()
                .anyMatch(t -> t.name().equals(THEME) && t.high() == PINK));
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
        E2eTvSteps.screen().keyPressed(key, 0, modifiers);
    }

    private static TvEditorTab editor() throws StepFailure {
        return E2eTvSteps.screen().editor();
    }

    private static void clickAt(int[] point) throws StepFailure {
        if (point == null) {
            throw new StepFailure("o controle não está à vista");
        }
        click(E2eTvSteps.screen(), point[0], point[1]);
    }
}
