package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.E2eScript.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eShowcase.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;

/**
 * Teste de ponta a ponta num mundo de verdade (cliente + servidor integrado). Só roda com a
 * variável de ambiente {@code WA_E2E} (diretório de saída); sem ela, não faz nada.
 *
 * <pre>./scripts/e2e.sh                     # roda tudo e sai com 0 (OK) ou 1 (falha)
 * WA_E2E=$PWD/run/e2e xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient</pre>
 *
 * <p>Na tela de título cria o mundo {@value #WORLD} (plano, criativo, com cheats; apaga o anterior)
 * pelo caminho normal do jogo ({@code createFreshLevel}) e, já no mundo, roda um roteiro, um passo
 * por vez a cada tick do cliente, com tempo-limite por passo:
 * <ol>
 *   <li>no servidor: duas redes do jogador, dois baús com um roteador em cima de cada (na rede
 *       principal) e 64 pedras no baú A;</li>
 *   <li>clique direito no roteador A ({@code gameMode.useItemOn}, mão vazia), face Cima, Extrai;</li>
 *   <li>fecha, abre o roteador B, Insere; espera as pedras chegarem ao baú B;</li>
 *   <li>reabre A (a vazão é contada na origem), põe mais 64 pedras e espera a vazão aparecer na tela;</li>
 *   <li>Editar abre o filtro; na aba Regra, o encantamento pelo nome (digita {@code prot}, Enter, nível 37);
 *       uma regra por tag pelo campo de texto; Voltar;</li>
 *   <li>renomeia o nó e troca a rede da aba Itens pelo seletor na linha das abas; depois troca só a
 *       da aba Energia e confere que a de Itens e a de Fluidos não mudaram;</li>
 *   <li>põe um Cartão de Filtro num slot da face Norte pela tela (pega no inventário, solta no slot);</li>
 *   <li>põe o Upgrade de chunk loading no slot de upgrade pela tela e confere o ticket no servidor e
 *       o estado na tela; tira com Shift + clique e confere que o ticket saiu;</li>
 *   <li>transporte por aba: pedras em A não vão para B enquanto os itens dos dois estão em redes
 *       diferentes, e chegam quando os itens de A entram na rede dos de B, com a energia de cada um
 *       em outra rede;</li>
 *   <li>Tablet de rede: abre pelo item, acha o roteador B pela busca, cria um grupo com a rede dos
 *       itens, pausa o grupo e confere que as pedras param, retoma e confere que chegam, e abre o
 *       roteador B à distância pela lista (20 blocos, longe demais para a tela comum).</li>
 *   <li>Configurador (sem tela): copia A com Shift + clique, passa ao modo Área com Shift + clique
 *       no ar, marca o baú A e o roteador B com cliques e cola a cópia em B com um clique no ar
 *       (os dois estão em baús, a mesma máquina);</li>
 *   <li>Vinculador por área (por último): Shift + clique no ar passa para Área, Shift + clique em
 *       dois blocos marca os cantos em volta dos roteadores, clique no ar abre a tela, que escolhe a
 *       rede e o tipo Fluidos e vincula; confere no servidor que só a aba de fluidos dos dois mudou.</li>
 *   <li>Baú Wireless: os 12 milhões de pedregulho de um Baú para outro com roteadores Ultimate, a
 *       tela (lista, busca, pegar e devolver pelo clique, redimensionar pela alça, filtro de entrada e Voltar), o Cartão de
 *       Upgrade no bloco, quebrar e recolocar com o conteúdo e uma foto dos blocos.</li>
 * </ol>
 * Os cliques passam pelo mesmo caminho do mouse ({@code screen.mouseClicked} no centro do widget) e
 * cada passo só termina quando o servidor aplicou (lido no block entity, na thread do servidor) e o
 * snapshot novo chegou à tela. Salva capturas em pontos-chave e escreve {@code result.txt} com
 * {@code OK} ou {@code FALHA: <passo> <motivo>} na primeira linha e o log dos passos embaixo;
 * depois sai do mundo e fecha o jogo.
 *
 * <p>Os widgets são achados pela mensagem ou pela dica (as chaves de tradução); os controles do painel
 * do nó ({@link NodePanel}), que não são widgets, pelos ganchos dele (o centro de cada um).
 *
 * <p>Esta classe é o motor (fila de passos, tick, espera, capturas e {@code result.txt}). A ordem do roteiro
 * fica no {@link E2eScript}, a vitrine no {@link E2eShowcase} e cada área de passos numa classe
 * {@code E2e*Steps} deste pacote.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class DevEndToEnd {
    /** Vitrine para a página do CurseForge: com {@code WA_SHOWCASE=<dir>}, monta uma fábrica e fotografa. */
    static final String SHOWCASE = System.getenv("WA_SHOWCASE");
    static final E2eSelection.Profile PROFILE = E2eSelection.profile(System.getenv("WA_E2E_PROFILE"));
    private static final String CAPTURE = envOr("WA_E2E_CAPTURE", PROFILE.capture());
    private static final String OUTPUT = System.getenv("WA_E2E") != null ? System.getenv("WA_E2E") : SHOWCASE;
    /**
     * Ampliação das fotos da vitrine: o quadro é renderizado fora da tela em 1280x800 vezes este fator, com a
     * interface na escala 2 vezes ele (o mesmo layout da escala 2 em 1280x800, com mais pixels). A janela
     * continua pequena; só o framebuffer cresce. {@code WA_SHOWCASE_SCALE} muda o fator (1 desliga).
     */
    private static final int SHOWCASE_SCALE = SHOWCASE == null ? 1
            : Math.max(1, Integer.parseInt(System.getenv().getOrDefault("WA_SHOWCASE_SCALE", "3")));
    static final String WORLD = "wa-e2e";
    static final long STEP_TIMEOUT_MS = 20_000;
    private static final long WORLD_TIMEOUT_MS = 180_000;

    private DevEndToEnd() {
    }

    private enum Phase { STARTING, LOADING, SETTLING, RUNNING, DONE }

    /** Ação de um passo; pode falhar com {@link StepFailure}. */
    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    /** Condição de fim de um passo, avaliada a cada tick. */
    @FunctionalInterface
    interface Check {
        boolean done() throws Exception;
    }

    /**
     * Um passo do roteiro: a ação roda uma vez e a condição a cada tick até valer ou o tempo
     * acabar; {@code state} descreve o que se via quando o tempo acabou.
     */
    record Step(String name, long timeoutMs, Action action, Check check, State state) {
    }

    /** O que o passo vê agora, para o log. */
    @FunctionalInterface
    interface State {
        String describe() throws Exception;
    }

    static final class StepFailure extends Exception {
        StepFailure(String message) {
            super(message);
        }
    }

    private static Phase phase = Phase.STARTING;
    private static int ticks;
    private static long phaseStart;
    private static final List<String> log = new ArrayList<>();
    private static List<Step> steps;
    private static int stepIndex;
    private static boolean stepStarted;
    static long stepStart;
    static int stepTicks;
    /** O tamanho da janela da última escolha da escala da GUI. */
    private static int scaledWidth = -1;
    private static int scaledHeight = -1;
    /** Maior vazão de itens que a tela aberta já mostrou. */
    static long maxItemRate;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (OUTPUT == null || phase == Phase.DONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ticks++;
        if (ticks == 1) {
            minecraft.getWindow().setWindowed(1280, 800);
            // no Xvfb a janela não tem foco: sem isto o jogo pausaria sozinho
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.onboardAccessibility = false;
            // sem as dicas do tutorial no canto das capturas
            minecraft.getTutorial().setStep(TutorialSteps.NONE);
        }
        if (minecraft.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            // sem options.txt o jogo abre a tela de acessibilidade, e a flag acima chega tarde: troca pela de título
            minecraft.setScreen(new TitleScreen(true));
        }
        if (SHOWCASE_SCALE > 1 && ticks >= 3) {
            // a janela pode mudar de tamanho depois (o GLFW avisa atrasado): reimpõe o framebuffer grande
            var window = minecraft.getWindow();
            if (window.getWidth() != 1280 * SHOWCASE_SCALE || window.getHeight() != 800 * SHOWCASE_SCALE) {
                window.setWidth(1280 * SHOWCASE_SCALE);
                window.setHeight(800 * SHOWCASE_SCALE);
                minecraft.options.guiScale().set(2 * SHOWCASE_SCALE);
                minecraft.resizeDisplay();
                WirelessAutomate.LOGGER.info("vitrine: framebuffer {}x{}, escala de GUI {}", window.getWidth(),
                        window.getHeight(), window.getGuiScale());
            }
        } else if (ticks >= 3 && (minecraft.getWindow().getWidth() != scaledWidth
                || minecraft.getWindow().getHeight() != scaledHeight)) {
            // no Windows a janela de 1280x800 sai menor e a escala 3 não deixa espaço para as
            // telas crescerem: escolhe a maior escala que caiba, sem salvar as opções do usuário. Refeito
            // quando a janela muda de tamanho: no Xvfb ela às vezes só chega a 1280x800 depois do tick 3
            var window = minecraft.getWindow();
            scaledWidth = window.getWidth();
            scaledHeight = window.getHeight();
            int scale = 1;
            for (int s = 3; s >= 1; s--) {
                if (window.getHeight() / s >= 330 && window.getWidth() / s >= 480) {
                    scale = s;
                    break;
                }
            }
            if (scale != minecraft.options.guiScale().get() || window.getGuiScale() != scale) {
                minecraft.options.guiScale().set(scale);
                minecraft.resizeDisplay();
            }
            WirelessAutomate.LOGGER.info("e2e: escala de GUI {} (janela {}x{})", scale, window.getWidth(),
                    window.getHeight());
        }
        try {
            switch (phase) {
                case STARTING -> {
                    if (ticks > 20 && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen title) {
                        createWorld(minecraft, title);
                        enter(Phase.LOADING);
                    }
                }
                case LOADING -> {
                    if (minecraft.level != null && minecraft.player != null && minecraft.getSingleplayerServer() != null
                            && minecraft.screen == null) {
                        record("mundo", "entrou no mundo " + WORLD);
                        enter(Phase.SETTLING);
                    } else if (elapsed() > WORLD_TIMEOUT_MS) {
                        finish(minecraft, "mundo", "não entrou no mundo em " + WORLD_TIMEOUT_MS + " ms (tela: "
                                + describe(minecraft.screen) + ")");
                    }
                }
                case SETTLING -> {
                    // deixa os chunks em volta chegarem ao cliente
                    if (elapsed() > 2_000) {
                        steps = SHOWCASE != null ? showcase() : script();
                        enter(Phase.RUNNING);
                    }
                }
                case RUNNING -> runSteps(minecraft);
                default -> {
                }
            }
        } catch (Throwable e) {
            WirelessAutomate.LOGGER.error("Teste de ponta a ponta falhou com exceção", e);
            String step = phase == Phase.RUNNING && steps != null && stepIndex < steps.size()
                    ? steps.get(stepIndex).name() : phase.name().toLowerCase();
            finish(minecraft, step, "exceção: " + e);
        }
    }

    private static void enter(Phase next) {
        phase = next;
        phaseStart = System.currentTimeMillis();
    }

    private static long elapsed() {
        return System.currentTimeMillis() - phaseStart;
    }

    private static void runSteps(Minecraft minecraft) throws Exception {
        if (stepIndex >= steps.size()) {
            finish(minecraft, null, null);
            return;
        }
        Step step = steps.get(stepIndex);
        if (!stepStarted) {
            stepStarted = true;
            stepStart = System.currentTimeMillis();
            stepTicks = 0;
            try {
                step.action().run();
            } catch (StepFailure e) {
                finish(minecraft, step.name(), e.getMessage());
                return;
            }
        }
        stepTicks++;
        if (minecraft.screen instanceof RouterScreen screen) {
            maxItemRate = Math.max(maxItemRate, screen.getMenu().throughput()[ResourceType.ITEM.ordinal()]);
        }
        boolean done;
        try {
            done = step.check().done();
        } catch (StepFailure e) {
            finish(minecraft, step.name(), e.getMessage());
            return;
        }
        long spent = System.currentTimeMillis() - stepStart;
        if (done) {
            record(step.name(), spent + " ms, " + stepTicks + " ticks; " + describe(step));
            stepIndex++;
            stepStarted = false;
        } else if (spent > step.timeoutMs()) {
            finish(minecraft, step.name(), "tempo esgotado (" + step.timeoutMs() + " ms); estado: "
                    + describe(step));
        }
    }

    static String describe(Step step) {
        try {
            return step.state().describe();
        } catch (Exception e) {
            return "(estado indisponível: " + e.getMessage() + ")";
        }
    }

    static void record(String step, String detail) {
        String line = "[ OK  ] " + step + ": " + detail;
        log.add(line);
        WirelessAutomate.LOGGER.info("E2E {}", line);
    }

    /** Escreve o resultado (falha se {@code failedStep} não for nulo), sai do mundo e fecha o jogo. */
    private static void finish(Minecraft minecraft, @Nullable String failedStep, @Nullable String reason) {
        phase = Phase.DONE;
        E2eResonatorSteps.restoreFrameSettings();
        // um e2e que falhou no escuro não deixa o cliente de dev escuro
        if (GuiThemes.current() != GuiTheme.LIGHT) {
            GuiThemes.set(GuiTheme.LIGHT);
        }
        String head = failedStep == null ? "OK" : "FALHA: " + failedStep + " " + reason;
        if (failedStep != null) {
            log.add("[FALHA] " + failedStep + ": " + reason);
            capture(minecraft, "falha");
        }
        WirelessAutomate.LOGGER.info("E2E resultado: {}", head);
        try {
            Path dir = Path.of(OUTPUT);
            Files.createDirectories(dir);
            List<String> lines = new ArrayList<>();
            lines.add(head);
            lines.addAll(log);
            Files.write(dir.resolve("result.txt"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            WirelessAutomate.LOGGER.error("Falha ao escrever result.txt", e);
        }
        // fora do tick: o mesmo caminho do "Salvar e sair" do menu de pausa
        minecraft.tell(() -> {
            if (minecraft.level != null) {
                if (minecraft.screen != null) {
                    minecraft.screen.onClose();
                }
                minecraft.level.disconnect();
                minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
            }
            minecraft.stop();
        });
    }

    /** Mundo plano, criativo e com cheats, apagando o da rodada anterior. */
    private static void createWorld(Minecraft minecraft, Screen parent) throws IOException {
        Path dir = minecraft.getLevelSource().getBaseDir().resolve(WORLD);
        if (Files.exists(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        record("criar mundo", "plano, criativo, com cheats, em " + dir);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                parent);
    }

    static Step wait(String name, int waitTicks) {
        return new Step("esperar " + name, STEP_TIMEOUT_MS, () -> {
        }, () -> stepTicks >= waitTicks, () -> waitTicks + " ticks");
    }

    /** Alguns quadros depois (mouse fora da tela, sem dicas), salva a captura. */
    static Step capture(String file) {
        if (!captureWanted(file)) {
            return new Step("captura " + file + " (pulada)", STEP_TIMEOUT_MS, () -> {
            }, () -> true, () -> file + ".png");
        }
        return new Step("captura " + file, STEP_TIMEOUT_MS, () -> {
            moveMouse(0, 0);
            if (SHOWCASE != null) {
                // vitrine: sem avisos de conquista nem mensagens do chat por cima das telas
                Minecraft.getInstance().getToasts().clear();
                Minecraft.getInstance().gui.getChat().clearMessages(false);
            }
        }, () -> {
            if (stepTicks < 6) {
                return false;
            }
            capture(Minecraft.getInstance(), file);
            return true;
        }, () -> file + ".png");
    }

    /**
     * Se algum texto foi cortado no último quadro, põe o mouse nele e confere que o tooltip tem o
     * texto inteiro.
     */
    static Step clipCheck(String name) {
        return new Step(name + ": textos cortados com tooltip", STEP_TIMEOUT_MS, () -> {
            int[] center = GuiText.firstClipCenter();
            if (GuiText.clipCount() > 0 && center != null) {
                moveMouse(center[0], center[1]);
            }
        }, () -> {
            int[] center = GuiText.firstClipCenter();
            return GuiText.clipCount() == 0 || (center != null && GuiText.clipAt(center[0], center[1]) != null);
        }, () -> GuiText.clipCount() + " texto(s) cortado(s) sem tooltip");
    }

    static String describe(@Nullable Screen screen) {
        return screen == null ? "nenhuma" : screen.getClass().getSimpleName();
    }

    static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** A vitrine e a captura de falha sempre saem; as demais pelo {@code WA_E2E_CAPTURE}. */
    private static boolean captureWanted(String file) {
        return SHOWCASE != null || "falha".equals(file) || E2eSelection.captureWanted(CAPTURE, file);
    }

    static void capture(Minecraft minecraft, String name) {
        if (!captureWanted(name)) {
            return;
        }
        Path dir = Path.of(OUTPUT);
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Files.createDirectories(dir);
            image.writeToFile(dir.resolve(name + ".png"));
            WirelessAutomate.LOGGER.info("E2E: captura salva em {}", dir.resolve(name + ".png"));
        } catch (IOException e) {
            WirelessAutomate.LOGGER.error("E2E: falha ao salvar a captura {}", name, e);
        }
    }
}
