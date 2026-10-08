package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.chunk.RouterChunkLoader;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.menu.RemoteRouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.item.GuideBook;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import net.minecraft.world.level.block.state.BlockState;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageChemicalTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

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
 *   <li>Editar abre o filtro; uma regra por tag pelo campo de texto; Voltar;</li>
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
 * <p>Os widgets são achados pela mensagem ou pela dica (as chaves de tradução); só a linha da lista
 * de redes usa a geometria da {@link RouterScreen} (altura da linha, {@link #DROPDOWN_ROW}, e o
 * título da lista na primeira linha).
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class DevEndToEnd {
    /** Vitrine para a página do CurseForge: com {@code WA_SHOWCASE=<dir>}, monta uma fábrica e fotografa. */
    private static final String SHOWCASE = System.getenv("WA_SHOWCASE");
    private static final String OUTPUT = System.getenv("WA_E2E") != null ? System.getenv("WA_E2E") : SHOWCASE;
    static final String WORLD = "wa-e2e";
    private static final String MAIN_NETWORK = "E2E Principal";
    private static final String OTHER_NETWORK = "E2E Outra";
    private static final String ENERGY_NETWORK = "E2E Energia";
    private static final String NODE_NAME = "Baú B E2E";
    private static final String TAG_RULE = "#c:stones";
    /** Altura de uma linha da lista de redes da {@link RouterScreen}. */
    private static final int DROPDOWN_ROW = 12;
    private static final long STEP_TIMEOUT_MS = 20_000;
    private static final long WORLD_TIMEOUT_MS = 180_000;

    private DevEndToEnd() {
    }

    private enum Phase { STARTING, LOADING, SETTLING, RUNNING, DONE }

    /** Ação de um passo; pode falhar com {@link StepFailure}. */
    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    /** Condição de fim de um passo, avaliada a cada tick. */
    @FunctionalInterface
    private interface Check {
        boolean done() throws Exception;
    }

    /**
     * Um passo do roteiro: a ação roda uma vez e a condição a cada tick até valer ou o tempo
     * acabar; {@code state} descreve o que se via quando o tempo acabou.
     */
    private record Step(String name, long timeoutMs, Action action, Check check, State state) {
    }

    /** O que o passo vê agora, para o log. */
    @FunctionalInterface
    private interface State {
        String describe() throws Exception;
    }

    private static final class StepFailure extends Exception {
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
    private static long stepStart;
    private static int stepTicks;

    // Estado do mundo de teste, montado no servidor.
    private static BlockPos chestA;
    private static BlockPos chestB;
    private static BlockPos routerA;
    private static BlockPos routerB;
    private static UUID mainNetwork;
    private static UUID otherNetwork;
    private static UUID energyNetwork;
    private static int versionBefore;
    /** Maior vazão de itens que a tela aberta já mostrou. */
    private static long maxItemRate;

    // ------------------------------------------------------------------ ciclo

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
        if (ticks == 3) {
            // no Windows a janela de 1280x800 sai menor e a escala 3 não deixa espaço para as
            // telas crescerem: escolhe a maior escala que caiba, sem salvar as opções do usuário
            var window = minecraft.getWindow();
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

    private static String describe(Step step) {
        try {
            return step.state().describe();
        } catch (Exception e) {
            return "(estado indisponível: " + e.getMessage() + ")";
        }
    }

    private static void record(String step, String detail) {
        String line = "[ OK  ] " + step + ": " + detail;
        log.add(line);
        WirelessAutomate.LOGGER.info("E2E {}", line);
    }

    /** Escreve o resultado (falha se {@code failedStep} não for nulo), sai do mundo e fecha o jogo. */
    private static void finish(Minecraft minecraft, @Nullable String failedStep, @Nullable String reason) {
        phase = Phase.DONE;
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

    // ------------------------------------------------------------------ mundo

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

    // ------------------------------------------------------------------ roteiro

    private static List<Step> script() {
        List<Step> list = new ArrayList<>();
        list.add(new Step("preparar", STEP_TIMEOUT_MS, DevEndToEnd::setUpWorld,
                () -> onServer(server -> NetworkManager.get().contains(router(server, routerA))
                        && NetworkManager.get().contains(router(server, routerB)))
                        && clientSees(routerA) && clientSees(routerB),
                () -> "roteadores em " + routerA.toShortString() + " e " + routerB.toShortString() + " na rede "
                        + mainNetwork));

        list.add(open("abrir A", () -> routerA));
        list.add(new Step("A: Cima e Extrai", STEP_TIMEOUT_MS, () -> {
            versionBefore = routerScreen().getMenu().version();
            click(widget(byMessage(face(Direction.UP)), "face Cima"));
            click(widget(byMessage(mode(PortMode.EXTRACT)), "Extrai"));
        }, () -> onServer(server -> router(server, routerA).face(ResourceType.ITEM, Direction.UP).mode())
                == PortMode.EXTRACT && clientMode() == PortMode.EXTRACT && routerScreen().getMenu().version() > versionBefore,
                () -> "servidor e tela em " + clientModeText()));
        list.add(capture("1-roteador-a-extrai"));
        list.add(close("fechar A"));

        list.add(open("abrir B", () -> routerB));
        list.add(new Step("B: Cima e Insere", STEP_TIMEOUT_MS, () -> {
            versionBefore = routerScreen().getMenu().version();
            click(widget(byMessage(face(Direction.UP)), "face Cima"));
            click(widget(byMessage(mode(PortMode.INSERT)), "Insere"));
        }, () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).mode())
                == PortMode.INSERT && clientMode() == PortMode.INSERT && routerScreen().getMenu().version() > versionBefore,
                () -> "servidor e tela em " + clientModeText()));
        list.add(new Step("pedras chegam ao baú B", 30_000, () -> {
        }, () -> onServer(server -> count(server, chestA, Items.STONE) == 0 && count(server, chestB, Items.STONE) == 64),
                () -> onServer(server -> "A=" + count(server, chestA, Items.STONE) + " B="
                        + count(server, chestB, Items.STONE)) + ", vazão na tela "
                        + routerScreen().getMenu().throughput()[ResourceType.ITEM.ordinal()] + "/s"));
        list.add(capture("2-roteador-b-insere"));
        list.add(close("fechar B"));

        // a vazão é contada na origem: com a tela de A aberta, mais 64 pedras no baú A
        list.add(open("abrir A de novo", () -> routerA));
        list.add(new Step("vazão chega à tela de A", STEP_TIMEOUT_MS, () -> {
            maxItemRate = 0;
            onServer(server -> {
                ((Container) server.overworld().getBlockEntity(chestA)).setItem(0, new ItemStack(Items.STONE, 64));
                return null;
            });
        }, () -> maxItemRate > 0 && onServer(server -> count(server, chestB, Items.STONE) == 128),
                () -> "pico de " + maxItemRate + " itens/s, agora " + safeThroughput() + "/s; B="
                        + onServer(server -> count(server, chestB, Items.STONE))));
        list.add(capture("3-roteador-a-vazao"));
        list.add(close("fechar A de novo"));
        list.add(open("abrir B de novo", () -> routerB));

        list.add(new Step("abrir filtro (Editar)", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.filter.edit")), "Editar")),
                () -> Minecraft.getInstance().screen instanceof FilterScreen screen
                        && screen.getMenu().view().router().equals(Optional.of(routerB))
                        && screen.getMenu().view().face().equals(Optional.of(Direction.UP)),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        // os widgets de uma aba só aparecem no quadro seguinte, como para o jogador
        list.add(new Step("filtro: aba Tags", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.tab.tags")), "aba Tags")),
                () -> filterScreen().tab() == FilterScreen.Tab.TAGS && filterScreen().tagSearchBox().isFocused(),
                () -> "aba " + filterScreen().tab()));
        list.add(new Step("filtro: buscar a tag", STEP_TIMEOUT_MS, () -> type(TAG_RULE.substring(3)),
                () -> filterScreen().candidateLabels().contains(TAG_RULE),
                () -> "linhas " + filterScreen().candidateLabels()));
        list.add(new Step("filtro: marcar a tag", STEP_TIMEOUT_MS, () -> {
            int index = filterScreen().candidateLabels().indexOf(TAG_RULE);
            int[] row = filterScreen().candidateCenter(index);
            click(Minecraft.getInstance().screen, row[0], row[1]);
        }, () -> filterScreen().checkedLabels().equals(List.of(TAG_RULE))
                && find(byMessage(Component.translatable("gui.wirelessautomate.filter.tags.add.action"))) instanceof AbstractWidget add
                && add.active,
                () -> "marcadas " + filterScreen().checkedLabels()));
        list.add(new Step("filtro: adicionar a tag", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.tags.add.action")), "Adicionar")),
                () -> onServer(server -> hasTag(router(server, routerB).face(ResourceType.ITEM, Direction.UP).filter().entries()))
                        && hasTag(filterScreen().getMenu().view().filter().entries()),
                () -> "filtro no servidor: " + onServer(server -> router(server, routerB)
                        .face(ResourceType.ITEM, Direction.UP).filter().entries().toString())));
        list.add(capture("4-filtro"));
        list.add(new Step("filtro: inspecionar uma picareta", STEP_TIMEOUT_MS,
                () -> filterScreen().inspect(new ItemStack(Items.DIAMOND_PICKAXE)),
                () -> filterScreen().candidateLabels().contains("#minecraft:pickaxes")
                        && filterScreen().candidateLabels().contains("@minecraft"),
                () -> "tags " + filterScreen().candidateLabels()));
        list.add(capture("4b-filtro-inspetor"));
        list.add(new Step("filtro: aba Regra", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.tab.rule")), "aba Regra")),
                () -> filterScreen().tab() == FilterScreen.Tab.RULE
                        && find(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.yes"))) != null,
                () -> "aba " + filterScreen().tab()));
        list.add(new Step("filtro: regra Encantado em picaretas", STEP_TIMEOUT_MS, () -> {
            // o primeiro "Sim" é o de Encantado (as condições vêm na ordem da tela)
            click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.yes")), "Sim de Encantado"));
            EditBox scope = filterScreen().scopeBox();
            click(Minecraft.getInstance().screen, scope.getX() + 4, scope.getY() + 3);
            type("#minecraft:pickaxes");
        }, () -> {
            AbstractWidget add = find(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.add")));
            return add != null && add.active;
        }, () -> "Adicionar regra ativo"));
        list.add(capture("4c-filtro-regra"));
        list.add(new Step("filtro: adicionar a regra", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.add")), "Adicionar regra")),
                () -> onServer(server -> hasPickaxeRule(router(server, routerB).face(ResourceType.ITEM, Direction.UP).filter()))
                        && filterScreen().tab() == FilterScreen.Tab.ENTRY,
                () -> "filtro no servidor: " + onServer(server -> router(server, routerB)
                        .face(ResourceType.ITEM, Direction.UP).filter().entries().toString())));
        int[] filterSize = new int[2];
        list.add(new Step("filtro: aumentar a janela", STEP_TIMEOUT_MS, () -> {
            filterSize[0] = filterScreen().panelWidth();
            filterSize[1] = filterScreen().panelHeight();
            dragGrip(filterScreen(), 40, 36);
        }, () -> filterScreen().panelWidth() > filterSize[0] && filterScreen().panelHeight() > filterSize[1]
                && filterScreen().inventoryFollows() && centered(filterScreen()),
                () -> "tamanho " + filterScreen().panelWidth() + "x" + filterScreen().panelHeight()));
        list.add(capture("4d-filtro-maior"));
        list.add(new Step("filtro: tamanho de volta", STEP_TIMEOUT_MS,
                // a janela cresce dos dois lados (fica no centro): metade da diferença em cada borda
                () -> dragGrip(filterScreen(), (filterSize[0] - filterScreen().panelWidth()) / 2,
                        (filterSize[1] - filterScreen().panelHeight()) / 2),
                () -> filterScreen().panelWidth() == filterSize[0] && filterScreen().panelHeight() == filterSize[1]
                        && filterScreen().inventoryFollows() && centered(filterScreen()),
                () -> "tamanho " + filterScreen().panelWidth() + "x" + filterScreen().panelHeight()));
        list.add(new Step("voltar ao roteador", STEP_TIMEOUT_MS,
                () -> click(widget(byTooltip(Component.translatable("gui.wirelessautomate.filter.back.tooltip")), "Voltar")),
                () -> Minecraft.getInstance().screen instanceof RouterScreen screen
                        && screen.getMenu().snapshot().pos().equals(routerB)
                        && screen.getMenu().snapshot().face(ResourceType.ITEM, Direction.UP).filterSize() == 2,
                () -> "tela " + describe(Minecraft.getInstance().screen)));

        list.add(new Step("renomear", STEP_TIMEOUT_MS, () -> {
            click(widget(byTooltip(Component.translatable("gui.wirelessautomate.router.rename.tooltip")), "nome do nó"));
            type(NODE_NAME);
            routerScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> NODE_NAME.equals(onServer(server -> router(server, routerB).name()))
                && NODE_NAME.equals(routerScreen().getMenu().snapshot().name()),
                () -> "servidor \"" + onServer(server -> router(server, routerB).name()) + "\", tela \""
                        + routerScreen().getMenu().snapshot().name() + "\""));
        list.add(new Step("trocar a rede da aba Itens", STEP_TIMEOUT_MS,
                () -> chooseNetwork(otherNetwork, OTHER_NETWORK),
                () -> otherNetwork.equals(onServer(server -> router(server, routerB).networkId(ResourceType.ITEM)))
                        && routerScreen().getMenu().snapshot().network(ResourceType.ITEM).equals(Optional.of(otherNetwork)),
                () -> "servidor " + serverNetworks(routerB) + ", tela " + clientNetworks()));
        list.add(capture("5-roteador-b-rede-itens"));
        list.add(new Step("aba Energia", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.type.energy")), "aba Energia")),
                () -> {
                    UUID playerId = Minecraft.getInstance().player.getUUID();
                    return routerScreen().getMenu().selectedType() == ResourceType.ENERGY && onServer(server ->
                            server.getPlayerList().getPlayer(playerId).containerMenu instanceof RouterMenu menu
                                    && menu.selectedType() == ResourceType.ENERGY);
                }, () -> "aba na tela " + routerScreen().getMenu().selectedType()));
        list.add(new Step("trocar só a rede da aba Energia", STEP_TIMEOUT_MS,
                () -> chooseNetwork(energyNetwork, ENERGY_NETWORK),
                () -> onServer(server -> {
                    RouterBlockEntity router = router(server, routerB);
                    return energyNetwork.equals(router.networkId(ResourceType.ENERGY))
                            && otherNetwork.equals(router.networkId(ResourceType.ITEM))
                            && mainNetwork.equals(router.networkId(ResourceType.FLUID));
                }) && routerScreen().getMenu().snapshot().network(ResourceType.ENERGY).equals(Optional.of(energyNetwork))
                        && routerScreen().getMenu().snapshot().network(ResourceType.ITEM).equals(Optional.of(otherNetwork)),
                () -> "servidor " + serverNetworks(routerB) + ", tela " + clientNetworks()));
        list.add(capture("5b-roteador-b-rede-energia"));
        list.add(new Step("de volta à aba Itens", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.type.item")), "aba Itens")),
                () -> routerScreen().getMenu().selectedType() == ResourceType.ITEM,
                () -> "aba na tela " + routerScreen().getMenu().selectedType()));
        if (LoadedTypes.contains(ResourceType.SOURCE)) {
            list.add(new Step("aba Source", STEP_TIMEOUT_MS,
                    () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.type.source")), "aba Source")),
                    () -> routerScreen().getMenu().selectedType() == ResourceType.SOURCE,
                    () -> "aba na tela " + routerScreen().getMenu().selectedType()));
            list.add(clipCheck("aba Source"));
            list.add(capture("1d-roteador-aba-source"));
            list.add(new Step("de volta à aba Itens depois da Source", STEP_TIMEOUT_MS,
                    () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.type.item")), "aba Itens")),
                    () -> routerScreen().getMenu().selectedType() == ResourceType.ITEM,
                    () -> "aba na tela " + routerScreen().getMenu().selectedType()));
        }
        // Com 5 tipos ou mais (Mekanism e Ars no runClient) as abas não cabem com todos os nomes no
        // mínimo (300) nem em 420: só a ativa tem nome. Com menos tipos cabem em 420.
        boolean manyTypes = LoadedTypes.LIST.size() >= 5;
        TabLayout.Mode tabsAtMinimum = TabLayout.Mode.ACTIVE_NAME;
        TabLayout.Mode tabsAtLarge = manyTypes ? TabLayout.Mode.ACTIVE_NAME : TabLayout.Mode.FULL;
        list.add(new Step("abas no tamanho mínimo", STEP_TIMEOUT_MS,
                () -> { },
                () -> routerScreen().tabMode() == tabsAtMinimum,
                () -> "modo das abas " + routerScreen().tabMode()));
        list.add(capture("1b-roteador-abas"));
        // Maior (420 x 300): a largura extra vai para o visor; a coluna da direita e o inventário vão
        // junto com a borda direita.
        list.add(new Step("roteador maior", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(420, 300),
                () -> routerScreen().size()[0] == Math.min(420, Math.max(300, routerScreen().width - 8))
                        && routerScreen().tabMode() == tabsAtLarge
                        && routerScreen().getMenu().slots.get(RouterMenu.INVENTORY_START).x
                                == routerScreen().size()[0] - 9 - 162 + 1,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", abas " + routerScreen().tabMode()));
        list.add(capture("1c-roteador-grande"));
        if (manyTypes) {
            // Bem largo, com os 5 tipos, todos os nomes passam a caber.
            list.add(new Step("roteador largo, abas com todos os nomes", STEP_TIMEOUT_MS,
                    () -> routerScreen().previewResize(Math.min(600, routerScreen().width - 8), 300),
                    () -> routerScreen().tabMode() == TabLayout.Mode.FULL,
                    () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", abas " + routerScreen().tabMode()));
            list.add(capture("1f-roteador-largo"));
        }
        list.add(new Step("roteador de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(300, 240),
                () -> routerScreen().size()[0] == 300 && routerScreen().size()[1] == 240,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size())));
        cardSteps(list);
        chunkUpgradeSteps(list);
        list.add(close("fechar B"));
        typeNetworkTransferSteps(list);
        tabletSteps(list);
        configuratorSteps(list);
        linkerAreaSteps(list);
        portugueseSteps(list);
        storageChestSteps(list);
        if (StorageKind.SOURCE_TANK.loaded()) {
            sourceTankSteps(list);
        }
        if (ModList.get().isLoaded("guideme")) {
            guideSteps(list);
        }
        return list;
    }

    /**
     * Cartão de Filtro num slot de face pela tela: o servidor dá um cartão (lista branca de
     * diamante) ao jogador, a tela muda para a face Norte (o servidor troca o que os slots mostram)
     * e o cartão vai do inventário para o primeiro slot de cartão com dois cliques, como o jogador
     * faria; confere no roteador e no conjunto de filtros que o motor usa.
     */
    private static void cardSteps(List<Step> list) {
        RelativeSide north = RelativeSide.fromAbsolute(Direction.UP, Direction.NORTH);
        list.add(new Step("cartão no inventário", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());
                FilterCardItem.setContents(card, new FilterCardItem.Contents(ResourceType.ITEM, new Filter(
                        Filter.ListMode.WHITELIST, false, List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 0)))));
                server.getPlayerList().getPlayer(playerId).getInventory().setItem(0, card);
                return null;
            });
        }, () -> inventoryCardSlot() != null, () -> "cartão no slot " + inventoryCardSlot()));
        list.add(new Step("face Norte", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(face(Direction.NORTH)), "face Norte")),
                () -> {
                    UUID playerId = Minecraft.getInstance().player.getUUID();
                    return routerScreen().getMenu().selectedFace() == Direction.NORTH && onServer(server ->
                            server.getPlayerList().getPlayer(playerId).containerMenu instanceof RouterMenu menu
                                    && menu.selectedFace() == Direction.NORTH && menu.selectedType() == ResourceType.ITEM);
                }, () -> "seleção na tela " + routerScreen().getMenu().selectedFace()));
        list.add(new Step("cartão no slot da face", STEP_TIMEOUT_MS, () -> {
            RouterScreen screen = routerScreen();
            Slot from = inventoryCardSlot();
            if (from == null) {
                throw new StepFailure("o cartão sumiu do inventário");
            }
            click(screen, screen.getGuiLeft() + from.x + 8, screen.getGuiTop() + from.y + 8);
            Slot to = screen.getMenu().getSlot(0);
            click(screen, screen.getGuiLeft() + to.x + 8, screen.getGuiTop() + to.y + 8);
        }, () -> onServer(server -> {
            RouterBlockEntity router = router(server, routerB);
            return FilterCardItem.isCard(router.card(ResourceType.ITEM, north, 0))
                    && router.filterSet(ResourceType.ITEM, Direction.NORTH).testItem(new ItemStack(Items.DIAMOND))
                    && !router.filterSet(ResourceType.ITEM, Direction.NORTH).testItem(new ItemStack(Items.STONE));
        }) && routerScreen().getMenu().getSlot(0).hasItem() && routerScreen().getMenu().getCarried().isEmpty()
                && inventoryCardSlot() == null,
                () -> "servidor " + onServer(server -> router(server, routerB).card(ResourceType.ITEM, north, 0).toString())
                        + ", slot na tela " + routerScreen().getMenu().getSlot(0).getItem()
                        + ", na mão " + routerScreen().getMenu().getCarried()));
        list.add(capture("6-cartao-no-slot"));
    }

    /**
     * Upgrade de chunk loading pela tela: o servidor dá o upgrade ao jogador, que o pega no inventário
     * e solta no slot de upgrade; o roteador B fica com o upgrade, o jogador como dono e o ticket no
     * chunk dele, e a tela mostra "ativo". Depois Shift + clique (o mesmo pacote do vanilla) tira o
     * upgrade de volta para o inventário e o ticket sai.
     */
    private static void chunkUpgradeSteps(List<Step> list) {
        list.add(new Step("upgrade no inventário", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).getInventory()
                        .setItem(1, new ItemStack(ModItems.CHUNK_LOADER_UPGRADE.get()));
                return null;
            });
        }, () -> inventoryUpgradeSlot() != null, () -> "upgrade no slot " + inventoryUpgradeSlot()));
        list.add(new Step("upgrade no slot de upgrade", STEP_TIMEOUT_MS, () -> {
            RouterScreen screen = routerScreen();
            Slot from = inventoryUpgradeSlot();
            if (from == null) {
                throw new StepFailure("o upgrade sumiu do inventário");
            }
            click(screen, screen.getGuiLeft() + from.x + 8, screen.getGuiTop() + from.y + 8);
            int[] to = screen.previewUpgradeSlotCenter();
            click(screen, to[0], to[1]);
        }, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            return onServer(server -> {
                RouterBlockEntity router = router(server, routerB);
                return router.hasChunkUpgrade() && playerId.equals(router.upgradeOwner())
                        && router.chunkLoadState() == ChunkLoadState.ACTIVE
                        && RouterChunkLoader.hasTicket(server.overworld(), routerB, ChunkPos.asLong(routerB));
            }) && routerScreen().getMenu().getSlot(RouterMenu.UPGRADE_SLOT).hasItem()
                    && routerScreen().getMenu().snapshot().chunkLoad() == ChunkLoadState.ACTIVE
                    && routerScreen().getMenu().getCarried().isEmpty();
        }, () -> "servidor " + onServer(server -> router(server, routerB).upgrade() + " "
                + router(server, routerB).chunkLoadState()) + ", tela " + routerScreen().getMenu().snapshot().chunkLoad()
                + ", na mão " + routerScreen().getMenu().getCarried()));
        list.add(new Step("captura 7-upgrade-no-slot", STEP_TIMEOUT_MS, () -> {
            int[] center = routerScreen().previewUpgradeSlotCenter();
            moveMouse(center[0], center[1]);
        }, () -> {
            if (stepTicks < 6) {
                return false;
            }
            capture(Minecraft.getInstance(), "7-upgrade-no-slot");
            return true;
        }, () -> "7-upgrade-no-slot.png"));
        list.add(new Step("tirar o upgrade com Shift + clique", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.handleInventoryMouseClick(routerScreen().getMenu().containerId, RouterMenu.UPGRADE_SLOT,
                    0, ClickType.QUICK_MOVE, minecraft.player);
        }, () -> onServer(server -> {
            RouterBlockEntity router = router(server, routerB);
            return !router.hasChunkUpgrade() && router.chunkLoadState() == ChunkLoadState.NONE
                    && !RouterChunkLoader.hasTicket(server.overworld(), routerB, ChunkPos.asLong(routerB));
        }) && !routerScreen().getMenu().getSlot(RouterMenu.UPGRADE_SLOT).hasItem() && inventoryUpgradeSlot() != null
                && routerScreen().getMenu().snapshot().chunkLoad() == ChunkLoadState.NONE,
                () -> "servidor " + onServer(server -> router(server, routerB).upgrade().toString())
                        + ", no inventário " + inventoryUpgradeSlot()));
    }

    /** O slot do inventário (na tela do roteador aberta) com o Upgrade de chunk loading, ou {@code null}. */
    private static @Nullable Slot inventoryUpgradeSlot() throws StepFailure {
        RouterMenu menu = routerScreen().getMenu();
        for (int i = RouterMenu.CARD_SLOT_COUNT; i < RouterMenu.UPGRADE_SLOT; i++) {
            if (menu.getSlot(i).getItem().is(ModItems.CHUNK_LOADER_UPGRADE.get())) {
                return menu.getSlot(i);
            }
        }
        return null;
    }

    /**
     * Abre o seletor de rede da aba atual (na linha das abas) e clica na linha da rede: a lista abre
     * logo abaixo da pílula, com 2 px de folga, 2 px de borda, o título e uma linha por rede.
     */
    private static void chooseNetwork(UUID network, String name) throws StepFailure {
        RouterScreen screen = routerScreen();
        AbstractWidget pill = widget(byMessageKey("gui.wirelessautomate.router.network.tab.narration"),
                "seletor de rede da aba");
        if (pill.getY() - screen.getGuiTop() > 45) {
            throw new StepFailure("o seletor de rede não está na linha das abas (y " + (pill.getY() - screen.getGuiTop()) + ")");
        }
        click(pill);
        List<NetworkEntry> networks = screen.getMenu().snapshot().networks();
        int row = -1;
        for (int i = 0; i < networks.size(); i++) {
            if (networks.get(i).id().equals(network)) {
                row = i;
            }
        }
        if (row < 0) {
            throw new StepFailure("a rede " + name + " não está na lista da tela: " + networks);
        }
        int x = pill.getX() + pill.getWidth() / 2;
        int y = pill.getY() + pill.getHeight() + 2 + 2 + (row + 1) * DROPDOWN_ROW + DROPDOWN_ROW / 2;
        click(screen, x, y);
    }

    /** Rede de cada aba no servidor, para o log. */
    private static String serverNetworks(BlockPos pos) throws Exception {
        return onServer(server -> serverNetworksText(server, pos));
    }

    /** Na thread do servidor. */
    private static String serverNetworksText(MinecraftServer server, BlockPos pos) {
        RouterBlockEntity router = router(server, pos);
        return "itens " + networkName(router.networkId(ResourceType.ITEM)) + ", fluidos "
                + networkName(router.networkId(ResourceType.FLUID)) + ", energia "
                + networkName(router.networkId(ResourceType.ENERGY));
    }

    /** Rede de cada aba no snapshot da tela, para o log. */
    private static String clientNetworks() throws StepFailure {
        RouterSnapshot s = routerScreen().getMenu().snapshot();
        return "itens " + networkName(s.network(ResourceType.ITEM).orElse(null)) + ", fluidos "
                + networkName(s.network(ResourceType.FLUID).orElse(null)) + ", energia "
                + networkName(s.network(ResourceType.ENERGY).orElse(null));
    }

    private static String networkName(@Nullable UUID id) {
        if (id == null) {
            return "nenhuma";
        }
        return id.equals(mainNetwork) ? MAIN_NETWORK : id.equals(otherNetwork) ? OTHER_NETWORK
                : id.equals(energyNetwork) ? ENERGY_NETWORK : id.toString();
    }

    /**
     * Transporte por aba: com os itens de B na "E2E Outra" e os de A ainda na principal, pedras
     * postas em A ficam paradas; com os itens de A também na "E2E Outra" elas chegam a B, mesmo com a
     * energia de A na principal e a de B na "E2E Energia".
     */
    private static void typeNetworkTransferSteps(List<Step> list) {
        list.add(new Step("itens de A e B em redes diferentes ficam parados", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ((Container) server.overworld().getBlockEntity(chestA)).setItem(0, new ItemStack(Items.STONE, 64));
            return null;
        }), () -> {
            if (onServer(server -> count(server, chestB, Items.STONE)) != 128) {
                throw new StepFailure("pedras chegaram a B com os itens em redes diferentes");
            }
            // 2 s parados bastam: na mesma rede as 64 pedras passaram em menos de 1 s
            return stepTicks >= 40 && onServer(server -> count(server, chestA, Items.STONE) == 64);
        }, () -> onServer(server -> "A=" + count(server, chestA, Items.STONE) + " B=" + count(server, chestB, Items.STONE)
                + "; A: " + serverNetworksText(server, routerA) + "; B: " + serverNetworksText(server, routerB))));
        list.add(new Step("itens de A na rede dos de B chegam, energia em outras redes", 30_000, () -> onServer(server -> {
            router(server, routerA).setNetworkId(ResourceType.ITEM, otherNetwork);
            return null;
        }), () -> onServer(server -> count(server, chestA, Items.STONE) == 0 && count(server, chestB, Items.STONE) == 192
                && mainNetwork.equals(router(server, routerA).networkId(ResourceType.ENERGY))
                && energyNetwork.equals(router(server, routerB).networkId(ResourceType.ENERGY))),
                () -> onServer(server -> "A=" + count(server, chestA, Items.STONE) + " B=" + count(server, chestB, Items.STONE)
                        + "; A: " + serverNetworksText(server, routerA) + "; B: " + serverNetworksText(server, routerB))));
    }

    // ------------------------------------------------------------------ Tablet de rede

    private static final String GROUP_NAME = "E2E Grupo";
    /** Posição do jogador antes de se afastar para abrir B à distância. */
    private static Vec3 beforeTablet = Vec3.ZERO;

    /**
     * Tablet de rede: abre pelo item na mão, busca o nó B pelo nome, cria um grupo com a rede "E2E
     * Outra" (a dos itens de A e B), pausa o grupo e confere que pedras postas em A ficam paradas,
     * retoma e confere que chegam a B, e por fim abre B à distância clicando na linha dele.
     */
    private static void tabletSteps(List<Step> list) {
        list.add(new Step("Tablet na mão", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.NETWORK_TABLET.get()));
                return null;
            });
        }, () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.NETWORK_TABLET.get()),
                () -> "na mão: " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(new Step("abrir o Tablet", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof TabletScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("achar B pela busca", STEP_TIMEOUT_MS, () -> {
            click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.search")), "busca"));
            type(NODE_NAME);
        }, () -> {
            TabletScreen screen = tabletScreen();
            TabletSnapshot snapshot = screen.getMenu().snapshot();
            return snapshot.query().search().equals(NODE_NAME) && snapshot.nodes().size() == 1
                    && screen.nodeRowCenter(nodeB()) != null;
        }, () -> "busca \"" + tabletScreen().getMenu().snapshot().query().search() + "\", nós "
                + tabletScreen().getMenu().snapshot().nodes().stream().map(n -> n.key().pos().toShortString()).toList()));
        list.add(capture("7-tablet-lista"));
        list.add(new Step("aba Estatísticas", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.tablet.tab.stats"), "aba Estatísticas")),
                () -> tabletScreen().cardCenter(ResourceType.ITEM) != null,
                () -> "aba " + tabletScreen().currentTab()));
        list.add(capture("7c-tablet-estatisticas"));
        list.add(new Step("Tablet maior", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(470, 260),
                () -> tabletScreen().size()[0] > 300 && tabletScreen().cardCenter(ResourceType.ITEM) != null,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(capture("7d-tablet-estatisticas-grande"));
        list.add(new Step("cartão de energia filtra a Lista", STEP_TIMEOUT_MS,
                () -> {
                    int[] c = tabletScreen().cardCenter(ResourceType.ENERGY);
                    click(tabletScreen(), c[0], c[1]);
                },
                () -> tabletScreen().currentTab() == TabletScreen.Tab.LIST
                        && tabletScreen().getMenu().snapshot().query().type().equals(Optional.of(ResourceType.ENERGY)),
                () -> "aba " + tabletScreen().currentTab() + ", consulta " + tabletScreen().getMenu().snapshot().query()));
        list.add(capture("7e-tablet-lista-energia"));
        list.add(new Step("Tablet de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(300, 240),
                () -> tabletScreen().size()[0] == 300,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        // o chip "Só Energia ✕" tira o filtro: os passos seguintes procuram B na Lista sem filtro de tipo
        list.add(new Step("tirar o filtro de tipo", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.tablet.list.type.tooltip"), "chip do tipo")),
                () -> tabletScreen().getMenu().snapshot().query().type().isEmpty(),
                () -> "consulta " + tabletScreen().getMenu().snapshot().query()));

        // os widgets da aba só aparecem no quadro seguinte, como para o jogador
        list.add(new Step("aba Grupos", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.tab.groups")), "aba Grupos")),
                () -> find(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.new"))) != null,
                () -> "Novo grupo visível"));
        list.add(new Step("novo grupo", STEP_TIMEOUT_MS, () -> {
            click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.new")), "Novo grupo"));
            type(GROUP_NAME);
            tabletScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> group() != null && tabletScreen().getMenu().snapshot().groups().stream()
                .anyMatch(g -> g.name().equals(GROUP_NAME)),
                () -> "grupos na tela " + tabletScreen().getMenu().snapshot().groups()));
        list.add(new Step("rede dos itens no grupo", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            int[] box = screen.groupNetworkCenter(otherNetwork);
            if (box == null) {
                throw new StepFailure("a rede " + OTHER_NETWORK + " não aparece na coluna do grupo");
            }
            click(screen, box[0], box[1]);
        }, () -> {
            WaGroup group = group();
            // e o botão Pausar já redesenhado como ativo (a tela o atualiza a cada quadro)
            AbstractWidget pause = find(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.pause")));
            return group != null && group.networks().contains(otherNetwork) && pause != null && pause.active
                    && tabletScreen().getMenu().snapshot().groups().stream()
                            .anyMatch(g -> g.name().equals(GROUP_NAME) && g.networks().contains(otherNetwork));
        }, () -> "grupo no servidor " + group()));
        list.add(new Step("pausar o grupo", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.pause")),
                        "Pausar o grupo")),
                () -> onServer(server -> NetworkSavedData.get(server).isPaused(otherNetwork))
                        && tabletScreen().getMenu().snapshot().groups().stream()
                                .anyMatch(g -> g.name().equals(GROUP_NAME) && g.paused()),
                () -> "pausada no servidor: " + onServer(server -> NetworkSavedData.get(server).isPaused(otherNetwork))));
        list.add(capture("8-tablet-grupo-pausado"));
        list.add(new Step("rede pausada: pedras param", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ((Container) server.overworld().getBlockEntity(chestA)).setItem(0, new ItemStack(Items.STONE, 64));
            return null;
        }), () -> {
            if (onServer(server -> count(server, chestB, Items.STONE)) != 192) {
                throw new StepFailure("pedras chegaram a B com a rede pausada");
            }
            return stepTicks >= 40 && onServer(server -> count(server, chestA, Items.STONE) == 64);
        }, () -> onServer(server -> "A=" + count(server, chestA, Items.STONE) + " B=" + count(server, chestB, Items.STONE))));
        list.add(new Step("retomar: pedras chegam", 30_000,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.pause")),
                        "Retomar o grupo")),
                () -> onServer(server -> !NetworkSavedData.get(server).isPaused(otherNetwork)
                        && count(server, chestA, Items.STONE) == 0 && count(server, chestB, Items.STONE) == 256),
                () -> onServer(server -> "pausada " + NetworkSavedData.get(server).isPaused(otherNetwork) + ", A="
                        + count(server, chestA, Items.STONE) + " B=" + count(server, chestB, Items.STONE))));

        list.add(new Step("afastar 20 blocos", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                beforeTablet = player.position();
                player.teleportTo(player.serverLevel(), routerB.getX() + 0.5, routerB.getY(), routerB.getZ() + 20.5,
                        180f, 10f);
                return null;
            });
        }, () -> Minecraft.getInstance().player.position().distanceTo(Vec3.atCenterOf(routerB)) > 15,
                () -> "distância " + Minecraft.getInstance().player.position().distanceTo(Vec3.atCenterOf(routerB))));
        list.add(new Step("abrir B à distância pela lista", STEP_TIMEOUT_MS, () -> {
            click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.tab.list")), "aba Lista"));
            TabletScreen screen = tabletScreen();
            int[] row = screen.nodeRowCenter(nodeB());
            if (row == null) {
                throw new StepFailure("B não está na lista");
            }
            click(screen, row[0], row[1]);
        }, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            // passa alguns ticks aberta: a tela comum fecharia a mais de 8 blocos
            return stepTicks >= 20 && Minecraft.getInstance().screen instanceof RouterScreen screen
                    && screen.getMenu().snapshot().pos().equals(routerB)
                    && onServer(server -> server.getPlayerList().getPlayer(playerId).containerMenu
                            instanceof RemoteRouterMenu);
        }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("9-roteador-a-distancia"));
        list.add(close("fechar B à distância"));
        // de volta para onde estava: os passos seguintes clicam em blocos ao alcance da mão
        list.add(new Step("voltar para perto dos baús", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.teleportTo(player.serverLevel(), beforeTablet.x, beforeTablet.y, beforeTablet.z, 180f, 30f);
                return null;
            });
        }, () -> Minecraft.getInstance().player.position().distanceTo(beforeTablet) < 0.5,
                () -> "distância " + Minecraft.getInstance().player.position().distanceTo(beforeTablet)));
    }

    private static TabletScreen tabletScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof TabletScreen screen) {
            return screen;
        }
        throw new StepFailure("o Tablet não está aberto (tela: " + describe(Minecraft.getInstance().screen) + ")");
    }

    private static NodeIndex.NodeKey nodeB() {
        return new NodeIndex.NodeKey(net.minecraft.world.level.Level.OVERWORLD, routerB);
    }

    /** O grupo do teste no servidor, ou {@code null}. */
    private static @Nullable WaGroup group() throws Exception {
        return onServer(server -> NetworkSavedData.get(server).groups().stream()
                .filter(g -> g.name().equals(GROUP_NAME)).findFirst().orElse(null));
    }

    // ------------------------------------------------------------------ Configurador

    /**
     * Configurador pelos cliques de verdade, sem tela: Shift + clique direito no roteador A copia
     * (e grava a máquina, um baú); Shift + clique direito no ar passa ao modo Área; cliques no baú A
     * e no roteador B marcam a área; clique direito no ar cola a cópia de A em B, preso a outro baú.
     */
    private static void configuratorSteps(List<Step> list) {
        list.add(new Step("Configurador na mão", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CONFIGURATOR.get()));
            return null;
        }), () -> Minecraft.getInstance().player.getMainHandItem().getItem() instanceof ConfiguratorItem,
                () -> "na mão: " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(shift(true));
        list.add(new Step("Shift + clique copia A", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(routerA), Direction.UP, routerA, false);
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        }, () -> onServer(server -> serverWand(server).has(ModDataComponents.PRESET.get())
                && Blocks.CHEST.builtInRegistryHolder().key().location().equals(ConfiguratorItem.machine(serverWand(server)))),
                () -> "varinha: " + onServer(server -> String.valueOf(serverWand(server).get(ModDataComponents.PRESET.get())))));
        list.add(new Step("Shift + clique no ar: modo Área", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> onServer(server -> ConfiguratorItem.mode(serverWand(server)) == LinkerMode.AREA)
                && ConfiguratorItem.mode(Minecraft.getInstance().player.getMainHandItem()) == LinkerMode.AREA,
                () -> "modo: " + ConfiguratorItem.mode(Minecraft.getInstance().player.getMainHandItem())));
        list.add(shift(false));
        list.add(new Step("marcar a área (baú A e roteador B)", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            for (BlockPos corner : List.of(chestA, routerB)) {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(corner), Direction.UP, corner, false);
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
            }
        }, () -> Minecraft.getInstance().screen == null && onServer(server -> {
            LinkerArea area = ConfiguratorItem.area(serverWand(server));
            return area != null && area.complete();
        }), () -> "área: " + onServer(server -> String.valueOf(ConfiguratorItem.area(serverWand(server))))
                + "; tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("10-configurador-area-no-mundo"));
        list.add(new Step("clique no ar cola em B", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).mode() == PortMode.EXTRACT
                && router(server, routerB).face(ResourceType.ITEM, Direction.UP).filter().isEmpty()),
                () -> "B: " + onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).toString())));
    }

    // ------------------------------------------------------------------ Vitrine (WA_SHOWCASE)

    private static BlockPos showBase;
    private static final List<BlockPos> showRouters = new ArrayList<>();
    private static BlockPos showInput;
    private static BlockPos showFurnace;
    private static final List<BlockPos> showStorage = new ArrayList<>();

    /**
     * Fotos da página do CurseForge: uma pequena fábrica (fornalhas acesas com roteadores dos quatro
     * tiers e filtro de minérios, um baú de entrada cheio, um barril Armazém e um baú de saída), em
     * redes com nomes de verdade. Fotografa a fábrica, a área do Vinculador, a tela do roteador com
     * itens passando, o filtro, o Tablet e o guia. As imagens finais saem de
     * scripts/curseforge/gerar_imagens.py.
     */
    private static List<Step> showcase() {
        List<Step> list = new ArrayList<>();
        list.add(new Step("montar a vitrine", STEP_TIMEOUT_MS, DevEndToEnd::showcaseWorld,
                () -> onServer(server -> showRouters.stream().allMatch(p -> NetworkManager.get().contains(router(server, p))))
                        && showRouters.stream().allMatch(DevEndToEnd::clientSees),
                () -> showRouters.size() + " roteadores"));
        // Vista de cima da fábrica, sem a interface.
        list.add(showCamera("câmera na fábrica", 0, 3.4, -0.6, 180f, 40f));
        list.add(wait("chunks e itens", 60));
        list.add(capture("s1-fabrica"));
        // Vinculador na mão, em modo Área, com a fileira de fornalhas marcada.
        list.add(new Step("área do Vinculador", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ItemStack linker = new ItemStack(ModItems.LINKER.get());
                LinkerItem.setMode(linker, LinkerMode.AREA);
                LinkerItem.setArea(linker, new LinkerArea(player.level().dimension(), showBase.offset(-5, 0, -5),
                        java.util.Optional.of(showBase.offset(5, 1, -5))));
                player.setItemInHand(InteractionHand.MAIN_HAND, linker);
                return null;
            });
        }, () -> LinkerItem.area(Minecraft.getInstance().player.getMainHandItem()) != null, () -> "Vinculador na mão"));
        list.add(wait("contorno", 10));
        list.add(capture("s2-vinculador-area"));
        list.add(new Step("mão vazia e interface", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = false;
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return null;
            });
        }, () -> Minecraft.getInstance().player.getMainHandItem().isEmpty(), () -> "mão"));
        // Tela do baú de entrada, com a vazão aparecendo (o jogador vai para perto dele).
        list.add(showCamera("perto da entrada", -3, 0, -6, 180f, 35f));
        list.add(new Step("interface de volta", STEP_TIMEOUT_MS, () -> Minecraft.getInstance().options.hideGui = false,
                () -> true, () -> "interface"));
        list.add(new Step("repor os itens", STEP_TIMEOUT_MS, () -> onServer(server -> {
            refillShowcase(server.overworld());
            return null;
        }), () -> true, () -> "itens"));
        list.add(open("abrir a entrada", () -> showInput.above()));
        list.add(new Step("vazão na tela", 15_000, () -> {
        }, () -> routerScreen().getMenu().throughput()[ResourceType.ITEM.ordinal()] > 0,
                () -> "vazão " + routerScreen().getMenu().throughput()[ResourceType.ITEM.ordinal()]));
        list.add(capture("s3-roteador"));
        list.add(close("fechar a entrada"));
        // Filtro da fornalha (face de cima, itens).
        list.add(showCamera("perto da fornalha", 0, 0, -3, 180f, 35f));
        list.add(new Step("interface de volta 2", STEP_TIMEOUT_MS, () -> Minecraft.getInstance().options.hideGui = false,
                () -> true, () -> "interface"));
        list.add(open("abrir a fornalha", () -> showFurnace.above()));
        list.add(new Step("filtro da fornalha", STEP_TIMEOUT_MS, () -> {
            click(widget(byMessage(face(Direction.UP)), "face Cima"));
        }, () -> find(byMessage(Component.translatable("gui.wirelessautomate.router.filter.edit"))) != null, () -> "Editar"));
        list.add(capture("s4-roteador-fornalha"));
        list.add(new Step("abrir o filtro", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                showcaseInventory(server, server.getPlayerList().getPlayer(playerId));
                return null;
            });
            click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.filter.edit")), "Editar"));
        }, () -> Minecraft.getInstance().screen instanceof FilterScreen, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("s5-filtro"));
        list.add(new Step("filtro: tags do minério de ferro", STEP_TIMEOUT_MS, () -> {
            FilterScreen screen = filterScreen();
            screen.previewInspect(new ItemStack(Items.RAW_IRON));
            List<String> labels = screen.candidateLabels();
            for (String label : List.of("#c:raw_materials", "#c:raw_materials/iron")) {
                if (labels.contains(label)) {
                    screen.previewCheck(labels.indexOf(label));
                }
            }
            int[] row = screen.candidateCenter(Math.max(0, labels.indexOf("#c:raw_materials")));
            moveMouse(row[0], row[1]);
        }, () -> filterScreen().tab() == FilterScreen.Tab.TAGS, () -> "aba " + filterScreen().tab()));
        list.add(wait("prévia da tag", 5));
        list.add(capture("s5b-filtro-tags"));
        list.add(new Step("filtro: regra de reparo", STEP_TIMEOUT_MS, () -> {
            filterScreen().previewRule(ItemRule.EMPTY.withFlag(ItemRule.Property.ENCHANTED, true)
                    .withDurability(java.util.Optional.of(new ItemRule.Durability(false, 50))));
            moveMouse(0, 0);
        }, () -> filterScreen().tab() == FilterScreen.Tab.RULE, () -> "aba " + filterScreen().tab()));
        list.add(wait("inventário aceso", 5));
        list.add(capture("s5c-filtro-regra"));
        list.add(close("fechar o filtro"));
        // Os quatro armazenamentos do mod, cheios, com roteadores.
        list.add(showCamera("câmera nos armazenamentos", 13.5, 2.2, -1.2, 180f, 30f));
        list.add(wait("armazenamentos", 40));
        list.add(capture("s9-armazenamentos"));
        list.add(showCamera("perto do Baú", 11, 0, -3, 180f, 40f));
        list.add(new Step("interface de volta 3", STEP_TIMEOUT_MS, () -> Minecraft.getInstance().options.hideGui = false,
                () -> true, () -> "interface"));
        list.add(new Step("abrir o Baú Wireless", STEP_TIMEOUT_MS, () -> useOn(showStorage.get(0)),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen screen && screen.shownCount() > 20,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(wait("lista do Baú", 10));
        list.add(capture("s10-bau"));
        list.add(close("fechar o Baú"));
        // Tablet: lista e mapa.
        list.add(new Step("Tablet na mão (vitrine)", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.NETWORK_TABLET.get()));
                return null;
            });
        }, () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.NETWORK_TABLET.get()), () -> "tablet"));
        list.add(new Step("abrir o Tablet", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof TabletScreen screen
                && screen.getMenu().snapshot().nodes().size() >= showRouters.size(),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("s6-tablet-lista"));
        list.add(new Step("aba Mapa", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.tab.map")), "aba Mapa")),
                () -> true, () -> "mapa"));
        list.add(wait("mapa desenhado", 10));
        list.add(capture("s7-tablet-mapa"));
        list.add(close("fechar o Tablet"));
        if (ModList.get().isLoaded("guideme")) {
            list.add(new Step("guia", STEP_TIMEOUT_MS,
                    () -> ClientCommandHandler.runCommand("guidemec wirelessautomate:guide open wirelessautomate:router.md"),
                    () -> Minecraft.getInstance().screen != null
                            && Minecraft.getInstance().screen.getClass().getName().startsWith("guideme"),
                    () -> "tela " + describe(Minecraft.getInstance().screen)));
            list.add(capture("s8-guia"));
        }
        return list;
    }

    /** Teleporta o jogador para um ponto relativo à base, olhando na direção dada, sem interface. */
    private static Step showCamera(String name, double dx, double dy, double dz, float yaw, float pitch) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = true;
            UUID playerId = Minecraft.getInstance().player.getUUID();
            // A rotação vai também para o cliente: o teleporte do servidor não a aplica na hora.
            Minecraft.getInstance().player.setYRot(yaw);
            Minecraft.getInstance().player.setXRot(pitch);
            Minecraft.getInstance().player.yRotO = yaw;
            Minecraft.getInstance().player.xRotO = pitch;
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), showBase.getX() + 0.5 + dx, showBase.getY() + dy,
                        showBase.getZ() + 0.5 + dz, yaw, pitch);
                return null;
            });
        }, () -> Math.abs(Minecraft.getInstance().player.getXRot() - pitch) < 0.5f
                && Minecraft.getInstance().player.position().distanceToSqr(showBase.getX() + 0.5 + dx, showBase.getY() + dy,
                        showBase.getZ() + 0.5 + dz) < 0.5, () -> "câmera em " + Minecraft.getInstance().player.position());
    }

    private static Step wait(String name, int waitTicks) {
        return new Step("esperar " + name, STEP_TIMEOUT_MS, () -> {
        }, () -> stepTicks >= waitTicks, () -> waitTicks + " ticks");
    }

    private static void showcaseWorld() throws Exception {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            showBase = player.blockPosition();
            level.setDayTime(6000);
            NetworkSavedData data = NetworkSavedData.get(server);
            WaNetwork smelting = data.create(player.getUUID(), "Smelting Line");
            WaNetwork storage = data.create(player.getUUID(), "Storage");
            data.create(player.getUUID(), "Base Power");
            data.setActiveNetwork(player.getUUID(), smelting.id());
            // Piso de pedra lisa com borda e lanternas nos cantos, sob a fábrica.
            for (int x = -7; x <= 7; x++) {
                for (int z = -10; z <= -3; z++) {
                    boolean edge = x == -7 || x == 7 || z == -10 || z == -3;
                    level.setBlockAndUpdate(showBase.offset(x, -1, z),
                            (edge ? Blocks.POLISHED_DEEPSLATE : Blocks.SMOOTH_STONE).defaultBlockState());
                }
            }
            for (int[] c : new int[][] {{-7, -10}, {7, -10}, {-7, -3}, {7, -3}}) {
                level.setBlockAndUpdate(showBase.offset(c[0], 0, c[1]), Blocks.LANTERN.defaultBlockState());
            }
            // Fileira de fornalhas acesas, uma por tier (a do meio repete o Elite).
            String[] tiers = {"basic", "advanced", "elite", "ultimate", "elite"};
            Filter ores = new Filter(Filter.ListMode.WHITELIST, false, List.of(
                    new FilterEntry.TagEntry(ResourceLocation.parse("c:ores"), 0),
                    new FilterEntry.ItemEntry(new ItemStack(Items.RAW_IRON), 0),
                    new FilterEntry.ItemEntry(new ItemStack(Items.RAW_GOLD), 0),
                    new FilterEntry.ItemEntry(new ItemStack(Items.RAW_COPPER), 0),
                    new FilterEntry.ItemEntry(new ItemStack(Items.ANCIENT_DEBRIS), 16)));
            for (int i = 0; i < tiers.length; i++) {
                BlockPos furnace = showBase.offset(-4 + 2 * i, 0, -5);
                level.setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.FurnaceBlock.FACING, Direction.SOUTH)
                        .setValue(net.minecraft.world.level.block.FurnaceBlock.LIT, true));
                placeShowRouter(server, level, furnace, tiers[i], "Furnace " + (i + 1), smelting.id());
                RouterBlockEntity router = router(server, furnace.above());
                router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                router.setFilter(ResourceType.ITEM, Direction.UP, ores);
                router.setMode(ResourceType.ITEM, Direction.DOWN, PortMode.EXTRACT);
                if (i == 2) {
                    showFurnace = furnace;
                }
            }
            // Armazenamento: entrada cheia, buffer e saída.
            showInput = showBase.offset(-3, 0, -8);
            BlockPos buffer = showBase.offset(0, 0, -8);
            BlockPos output = showBase.offset(3, 0, -8);
            level.setBlockAndUpdate(showInput, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(buffer, Blocks.BARREL.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
            level.setBlockAndUpdate(output, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
            placeShowRouter(server, level, showInput, "basic", "Ore Input", storage.id());
            placeShowRouter(server, level, buffer, "elite", "Buffer", storage.id());
            placeShowRouter(server, level, output, "advanced", "Output", storage.id());
            router(server, showInput.above()).setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
            router(server, buffer.above()).setMode(ResourceType.ITEM, Direction.UP, PortMode.BOTH);
            router(server, output.above()).setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
            refillShowcase(level);
            showcaseStorage(server, level, storage.id());
            return null;
        });
    }

    /**
     * Os quatro armazenamentos do mod numa fileira a leste da fábrica, num piso próprio: Baú
     * Ultimate com dezenas de tipos e milhões de itens, Tanque, Bateria e Tanque Químico (com o
     * Mekanism), cada um com um roteador em cima.
     */
    private static void showcaseStorage(MinecraftServer server, ServerLevel level, UUID network) {
        for (int x = 9; x <= 18; x++) {
            for (int z = -7; z <= -2; z++) {
                boolean edge = x == 9 || x == 18 || z == -7 || z == -2;
                level.setBlockAndUpdate(showBase.offset(x, -1, z),
                        (edge ? Blocks.POLISHED_DEEPSLATE : Blocks.SMOOTH_STONE).defaultBlockState());
            }
        }
        StorageKind[] kinds = {StorageKind.CHEST, StorageKind.TANK, StorageKind.BATTERY, StorageKind.CHEMICAL_TANK};
        RouterTier[] tiers = {RouterTier.ULTIMATE, RouterTier.ELITE, RouterTier.ADVANCED, RouterTier.ELITE};
        String[] names = {"Main Storage", "Lava Tank", "Base Battery", "Hydrogen"};
        showStorage.clear();
        for (int i = 0; i < kinds.length; i++) {
            if (!kinds[i].loaded()) {
                continue;
            }
            BlockPos pos = showBase.offset(11 + 2 * i, 0, -5);
            level.setBlockAndUpdate(pos, ModBlocks.STORAGE.get(kinds[i]).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, tiers[i]));
            showStorage.add(pos);
            switch (level.getBlockEntity(pos)) {
                case StorageChestBlockEntity chest -> {
                    Item[] items = {Items.COBBLESTONE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.DIAMOND,
                            Items.EMERALD, Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL, Items.QUARTZ, Items.OAK_LOG,
                            Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.DIRT, Items.SAND, Items.GRAVEL, Items.GLASS,
                            Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER, Items.NETHERITE_INGOT, Items.ANCIENT_DEBRIS,
                            Items.OBSIDIAN, Items.GLOWSTONE_DUST, Items.AMETHYST_SHARD, Items.BONE, Items.STRING,
                            Items.GUNPOWDER, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.SLIME_BALL, Items.WHEAT,
                            Items.CARROT, Items.POTATO, Items.SUGAR_CANE, Items.BAMBOO, Items.KELP, Items.CLAY_BALL,
                            Items.FLINT, Items.LEATHER, Items.FEATHER, Items.EGG, Items.APPLE, Items.TORCH};
                    long amount = 12_600_000L;
                    for (Item item : items) {
                        chest.storage().insert(new ItemStack(item), amount, false);
                        amount = Math.max(1, amount * 7 / 10);
                    }
                }
                case StorageTankBlockEntity tank -> {
                    tank.storage().insert(new FluidStack(Fluids.LAVA, 1), 48_000_000L, false);
                    tank.storage().insert(new FluidStack(Fluids.WATER, 1), 120_000_000L, false);
                }
                case StorageBatteryBlockEntity battery -> battery.store().insert(2_500_000_000L, false);
                case StorageChemicalTankBlockEntity chemical ->
                        chemical.storage().insert(ResourceLocation.parse("mekanism:hydrogen"), 64_000_000L, false);
                default -> {
                }
            }
            placeShowRouter(server, level, pos, "ultimate", names[i], network);
            RouterBlockEntity router = router(server, pos.above());
            ResourceType type = switch (kinds[i]) {
                case CHEST -> ResourceType.ITEM;
                case TANK -> ResourceType.FLUID;
                case BATTERY -> ResourceType.ENERGY;
                case CHEMICAL_TANK -> ResourceType.CHEMICAL;
                case SOURCE_TANK -> ResourceType.SOURCE;
            };
            router.setMode(type, Direction.UP, PortMode.BOTH);
        }
    }

    /**
     * Inventário de exemplo para as fotos do filtro: ferramentas encantadas gastas e novas, uma
     * comum gasta, livro encantado e minérios, para a regra "Encantado + durabilidade < 50%" acender
     * só as que vão para o reparo.
     */
    private static void showcaseInventory(MinecraftServer server, ServerPlayer player) {
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack wornPick = new ItemStack(Items.DIAMOND_PICKAXE);
        wornPick.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE), 3);
        wornPick.setDamageValue(1300);
        ItemStack newPick = new ItemStack(Items.DIAMOND_PICKAXE);
        newPick.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 5);
        ItemStack wornSword = new ItemStack(Items.NETHERITE_SWORD);
        wornSword.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
        wornSword.setDamageValue(1500);
        ItemStack plainWorn = new ItemStack(Items.IRON_PICKAXE);
        plainWorn.setDamageValue(200);
        ItemStack wornChest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        wornChest.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 4);
        wornChest.setDamageValue(400);
        ItemStack[] items = {wornPick, newPick, wornSword, plainWorn, wornChest,
                net.minecraft.world.item.EnchantedBookItem.createForEnchantment(new net.minecraft.world.item.enchantment.EnchantmentInstance(
                        enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING), 1)),
                new ItemStack(Items.RAW_IRON, 48), new ItemStack(Items.RAW_GOLD, 23), new ItemStack(Items.COAL, 64),
                new ItemStack(Items.IRON_ORE, 12), new ItemStack(Items.DEEPSLATE_DIAMOND_ORE, 3), new ItemStack(Items.TORCH, 40)};
        player.getInventory().clearContent();
        for (int i = 0; i < items.length; i++) {
            player.getInventory().setItem(9 + i, items[i]);
        }
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
    }

    /** Entrada cheia e destinos vazios, para a tela mostrar itens passando. */
    private static void refillShowcase(ServerLevel level) {
        for (BlockPos pos : List.of(showBase.offset(0, 0, -8), showBase.offset(3, 0, -8))) {
            ((Container) level.getBlockEntity(pos)).clearContent();
        }
        Container input = (Container) level.getBlockEntity(showInput);
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            input.setItem(slot, new ItemStack(slot % 3 == 0 ? Items.COBBLESTONE : slot % 3 == 1 ? Items.IRON_INGOT
                    : Items.REDSTONE, 64));
        }
    }

    private static void placeShowRouter(MinecraftServer server, ServerLevel level, BlockPos machine, String tier, String name,
            UUID network) {
        BlockPos pos = machine.above();
        level.setBlockAndUpdate(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, io.github.matheusanbs.wirelessautomate.block.RouterTier.valueOf(tier.toUpperCase(java.util.Locale.ROOT))));
        RouterBlockEntity router = router(server, pos);
        router.setNetworkId(network);
        router.setName(name);
        showRouters.add(pos);
    }

    // ------------------------------------------------------------------ Baú Wireless

    /** Total guardado nos dois Baús do roteiro: 12 milhões de pedregulho, 64 diamantes e 1.000 de terra. */
    private static final long CHEST_TOTAL = 12_000_000L + 64 + 1_000;
    private static BlockPos storageA = BlockPos.ZERO;
    private static BlockPos storageB = BlockPos.ZERO;

    /**
     * Baú Wireless num mundo real: dois Baús (Elite e Ultimate) com roteadores Ultimate na rede
     * principal; os 12 milhões de pedregulho do teste do dono passam de um para o outro. Depois a
     * tela: lista, busca, pegar e devolver uma pilha pelo clique, o filtro de entrada e Voltar. Por
     * fim o Cartão de Upgrade no Baú, quebrar e recolocar com o conteúdo e uma foto dos blocos.
     */
    private static void storageChestSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        long[] started = new long[1];
        boolean[] clicked = new boolean[3];
        list.add(new Step("Baú: 12 milhões de um Baú para outro", 30_000, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            BlockPos base = player.blockPosition();
            // Ao alcance do clique (o servidor recusa de longe) e longe dos baús A e B do roteiro.
            storageA = base.offset(-2, 0, -1);
            storageB = base.offset(2, 0, -1);
            level.setBlockAndUpdate(storageA, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.ELITE));
            level.setBlockAndUpdate(storageB, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
            ItemStorage storage = storageChest(server, storageA).storage();
            storage.insert(new ItemStack(Items.COBBLESTONE), 12_000_000L, false);
            storage.insert(new ItemStack(Items.DIAMOND), 64, false);
            storage.insert(new ItemStack(Items.DIRT), 1_000, false);
            for (BlockPos chest : List.of(storageA, storageB)) {
                level.setBlockAndUpdate(chest.above(), ModBlocks.ROUTER.get().defaultBlockState()
                        .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
                RouterBlockEntity router = router(server, chest.above());
                router.setNetworkId(ResourceType.ITEM, mainNetwork);
                router.setMode(ResourceType.ITEM, Direction.UP, chest == storageA ? PortMode.EXTRACT : PortMode.INSERT);
            }
            started[0] = server.getTickCount();
            return null;
        }), () -> onServer(server -> storageChest(server, storageA).storage().isEmpty()
                && storageChest(server, storageB).storage().total() == CHEST_TOTAL),
                () -> onServer(server -> "A=" + storageChest(server, storageA).storage().total() + " B="
                        + storageChest(server, storageB).storage().total() + " em "
                        + (server.getTickCount() - started[0]) + " ticks")));

        list.add(new Step("Baú: abrir a tela", STEP_TIMEOUT_MS, () -> useOn(storageB),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen screen
                        && screen.getMenu().view().types() == 3 && screen.getMenu().view().header().total() == CHEST_TOTAL,
                () -> Minecraft.getInstance().screen instanceof StorageListScreen screen
                        ? "tipos " + screen.getMenu().view().types() + ", total " + screen.getMenu().view().header().total()
                        : "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("bau-1-tela"));
        list.add(new Step("Baú: busca", STEP_TIMEOUT_MS, () -> chestScreen().searchBox().setValue("diam"),
                () -> chestScreen().shownCount() == 1, () -> "na lista " + chestScreen().shownCount()));
        list.add(capture("bau-2-busca"));
        list.add(new Step("Baú: pegar uma pilha pelo clique", STEP_TIMEOUT_MS, () -> {
            int[] cell = chestScreen().cellCenter(0);
            click(chestScreen(), cell[0], cell[1]);
        }, () -> chestScreen().getMenu().getCarried().is(Items.DIAMOND) && chestScreen().getMenu().getCarried().getCount() == 64
                && onServer(server -> storageChest(server, storageB).storage().count(new ItemStack(Items.DIAMOND)) == 0),
                () -> "cursor " + chestScreen().getMenu().getCarried()));
        list.add(new Step("Baú: devolver pelo clique na lista", STEP_TIMEOUT_MS, () -> {
            int[] cell = chestScreen().cellCenter(3);
            click(chestScreen(), cell[0], cell[1]);
        }, () -> chestScreen().getMenu().getCarried().isEmpty()
                && onServer(server -> storageChest(server, storageB).storage().total() == CHEST_TOTAL),
                () -> "cursor " + chestScreen().getMenu().getCarried()));
        list.add(new Step("Baú: limpar a busca", STEP_TIMEOUT_MS, () -> chestScreen().searchBox().setValue(""),
                () -> chestScreen().shownCount() == 3, () -> "na lista " + chestScreen().shownCount()));
        int[] before = new int[2];
        list.add(new Step("Baú: aumentar pela alça (+4 colunas, +2 linhas)", STEP_TIMEOUT_MS, () -> {
            StorageListScreen screen = chestScreen();
            before[0] = screen.cols();
            before[1] = screen.rows();
            // Cresce em torno do centro: cada lado anda o que o mouse andou, então 2 células de
            // mouse dão 4 colunas.
            dragGrip(screen, 2 * 18, 18);
        }, () -> chestScreen().cols() == before[0] + 4 && chestScreen().rows() == before[1] + 2
                && inventoryFollows(chestScreen()),
                () -> "grade " + chestScreen().cols() + "x" + chestScreen().rows() + ", antes " + before[0] + "x" + before[1]));
        list.add(capture("bau-6-maior"));
        list.add(new Step("Baú: Shift + clique no inventário na posição nova", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).getInventory().setItem(9, new ItemStack(Items.EMERALD, 5));
                return null;
            });
        }, () -> {
            StorageListScreen screen = chestScreen();
            Slot slot = screen.getMenu().slots.stream().filter(s -> s.getContainerSlot() == 9).findFirst().orElseThrow();
            if (!clicked[2]) {
                if (!slot.getItem().is(Items.EMERALD)) {
                    return false;
                }
                clicked[2] = true;
                // O mesmo pacote do Shift + clique da tela (que lê o teclado de verdade), pelo índice do
                // slot trocado: ele precisa apontar para o mesmo slot no servidor.
                Minecraft.getInstance().gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0,
                        ClickType.QUICK_MOVE, Minecraft.getInstance().player);
            }
            return onServer(server -> storageChest(server, storageB).storage().count(new ItemStack(Items.EMERALD)) == 5);
        }, () -> onServer(server -> "esmeraldas no Baú " + storageChest(server, storageB).storage().count(new ItemStack(Items.EMERALD)))));
        list.add(new Step("Baú: diminuir até o mínimo pela alça", STEP_TIMEOUT_MS,
                () -> dragGrip(chestScreen(), -40 * 18, -40 * 18),
                () -> chestScreen().cols() == 9 && chestScreen().rows() == 3 && inventoryFollows(chestScreen()),
                () -> "grade " + chestScreen().cols() + "x" + chestScreen().rows()));
        list.add(capture("bau-7-minimo"));
        list.add(new Step("Baú: tamanho padrão de volta", STEP_TIMEOUT_MS,
                () -> dragGrip(chestScreen(), (before[0] - 9) * 9, (before[1] - 3) * 9),
                () -> chestScreen().cols() == before[0] && chestScreen().rows() == before[1],
                () -> "grade " + chestScreen().cols() + "x" + chestScreen().rows()));
        list.add(new Step("Baú: abrir o filtro de entrada", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.storage.filter")), "botão Filtro")),
                () -> Minecraft.getInstance().screen instanceof FilterScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("bau-3-filtro"));
        list.add(new Step("Baú: voltar do filtro", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.back")), "Voltar")),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(close("Baú: fechar"));

        list.add(new Step("Baú: Cartão de Upgrade no bloco", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.TIER_CORES.get(RouterTier.ULTIMATE).get()));
                return null;
            });
        }, () -> {
            if (!Minecraft.getInstance().player.getMainHandItem().is(ModItems.TIER_CORES.get(RouterTier.ULTIMATE).get())) {
                return false;
            }
            if (!clicked[0]) {
                clicked[0] = true;
                useOn(storageA);
            }
            return onServer(server -> server.overworld().getBlockState(storageA).getValue(RouterBlock.TIER) == RouterTier.ULTIMATE);
        }, () -> onServer(server -> "tier " + server.overworld().getBlockState(storageA).getValue(RouterBlock.TIER))));

        list.add(new Step("Baú: quebrar e recolocar com o conteúdo", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            level.destroyBlock(storageB.above(), false);
            level.destroyBlock(storageB, true);
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(storageB).inflate(2),
                    entity -> entity.getItem().is(ModItems.STORAGE_CHEST.get()));
            if (drops.size() != 1) {
                throw new IllegalStateException("Baús no chão: " + drops.size());
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, drops.get(0).getItem().copy());
            drops.get(0).discard();
            return null;
        }), () -> {
            if (!Minecraft.getInstance().player.getMainHandItem().is(ModItems.STORAGE_CHEST.get())) {
                return false;
            }
            if (!clicked[1]) {
                clicked[1] = true;
                BlockPos floor = storageB.below();
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
                Minecraft.getInstance().gameMode.useItemOn(Minecraft.getInstance().player, InteractionHand.MAIN_HAND, hit);
            }
            // + as 5 esmeraldas guardadas pelo Shift + clique depois de redimensionar
            return onServer(server -> server.overworld().getBlockEntity(storageB) instanceof StorageChestBlockEntity chest
                    && chest.storage().total() == CHEST_TOTAL + 5);
        }, () -> onServer(server -> server.overworld().getBlockEntity(storageB) instanceof StorageChestBlockEntity chest
                ? "total " + chest.storage().total() : "sem Baú em " + storageB.toShortString())));
        list.add(capture("bau-4-tooltip-mao"));

        list.add(new Step("Baú: olhar os blocos", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerLevel level = player.serverLevel();
            level.setBlockAndUpdate(storageB.above(), ModBlocks.ROUTER.get().defaultBlockState()
                    .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
            BlockPos look = storageA.offset(2, 0, 4);
            player.teleportTo(level, look.getX() + 0.5, look.getY(), look.getZ() + 0.5, 180f, 25f);
            return null;
        }), () -> stepTicks >= 20, () -> "esperando o chunk desenhar"));
        list.add(capture("bau-5-blocos"));

        // Tanque e Bateria: a tela de cada um, com conteúdo.
        BlockPos[] more = new BlockPos[2];
        list.add(new Step("Tanque e Bateria: preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            BlockPos base = player.blockPosition();
            more[0] = base.offset(-1, 0, -2);
            more[1] = base.offset(1, 0, -2);
            level.setBlockAndUpdate(more[0], ModBlocks.STORAGE.get(StorageKind.TANK).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.ELITE));
            level.setBlockAndUpdate(more[1], ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.ADVANCED));
            StorageTankBlockEntity tank = (StorageTankBlockEntity) level.getBlockEntity(more[0]);
            tank.storage().insert(new FluidStack(Fluids.WATER, 1), 1_250_000_000L, false);
            tank.storage().insert(new FluidStack(Fluids.LAVA, 1), 48_000_000L, false);
            StorageBatteryBlockEntity battery = (StorageBatteryBlockEntity) level.getBlockEntity(more[1]);
            battery.store().insert(640_000_000L, false);
            return null;
        }), () -> {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.level != null && minecraft.level.getBlockState(more[0]).getBlock() instanceof StorageBlock
                    && minecraft.level.getBlockState(more[1]).getBlock() instanceof StorageBlock;
        }, () -> "blocos no cliente"));
        list.add(new Step("Tanque: abrir a tela", STEP_TIMEOUT_MS, () -> useOn(more[0]),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen screen && screen.getMenu().view().types() == 2,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("tanque-1-tela"));
        list.add(close("Tanque: fechar"));
        list.add(new Step("Bateria: abrir a tela", STEP_TIMEOUT_MS, () -> useOn(more[1]),
                () -> Minecraft.getInstance().screen instanceof StorageScalarScreen screen && screen.getMenu().received()
                        && screen.getMenu().stored() == 640_000_000L,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("bateria-1-tela"));
        list.add(close("Bateria: fechar"));
        if (StorageKind.SOURCE_TANK.loaded()) {
            scalarSourceSteps(list, () -> more[1].offset(2, 0, 0));
        }
    }

    /**
     * A tela de um valor só do Tanque de Source (Avançado, 1.640.000 de Source): em inglês e, depois de
     * trocar o idioma, em português, as duas com a conferência dos textos cortados. A variação fica em
     * "Parado" (o tanque não se mexe).
     */
    private static void scalarSourceSteps(List<Step> list, java.util.function.Supplier<BlockPos> where) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] at = new BlockPos[1];
        list.add(new Step("Tanque de Source (tela): preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            BlockPos pos = at[0] = where.get();
            ServerLevel level = server.getPlayerList().getPlayer(playerId).serverLevel();
            level.setBlockAndUpdate(pos, ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.ADVANCED));
            ((StorageSourceTankBlockEntity) level.getBlockEntity(pos)).store().insert(1_640_000L, false);
            return null;
        }), () -> Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockState(at[0]).getBlock() instanceof StorageSourceTankBlock,
                () -> "bloco no cliente"));
        for (String code : new String[] {"en_us", "pt_br"}) {
            String suffix = code.equals("pt_br") ? "-pt" : "";
            String label = code.equals("pt_br") ? "pt: " : "";
            if (code.equals("pt_br")) {
                list.add(language("pt_br"));
            }
            list.add(new Step(label + "Tanque de Source: abrir a tela", STEP_TIMEOUT_MS, () -> useOn(at[0]),
                    () -> Minecraft.getInstance().screen instanceof StorageScalarScreen screen && screen.getMenu().received()
                            && screen.getMenu().kind() == StorageKind.SOURCE_TANK && screen.getMenu().stored() == 1_640_000L,
                    () -> "tela " + describe(Minecraft.getInstance().screen)));
            list.add(new Step(label + "Tanque de Source: nenhum texto cortado", STEP_TIMEOUT_MS, () -> {
            }, () -> GuiText.clipCount() == 0, () -> GuiText.clipCount() + " texto(s) cortado(s) na tela do tanque"));
            list.add(capture("tanque-source-1-tela" + suffix));
            list.add(close(label + "Tanque de Source: fechar"));
            if (code.equals("pt_br")) {
                list.add(language("en_us"));
            }
        }
        list.add(new Step("Tanque de Source (tela): limpar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.overworld().setBlockAndUpdate(at[0], Blocks.AIR.defaultBlockState());
            return null;
        }), () -> true, () -> ""));
    }

    /**
     * Tanque de Source no mundo: os quatro tiers lado a lado com níveis diferentes (vazio, 25%, 60%, cheio),
     * a vista de perto, o contorno de seleção com a mira num deles e os itens na mão e na barra.
     */
    private static void sourceTankSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] tanks = new BlockPos[4];
        BlockPos[] origin = new BlockPos[1];
        list.add(new Step("Tanque de Source: preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            origin[0] = player.blockPosition();
            RouterTier[] tiers = RouterTier.values();
            long[] contents = {0L, 640_000L, 24_576_000L, 3_000_000_000L};
            for (int i = 0; i < 4; i++) {
                tanks[i] = origin[0].offset(-3 + 2 * i, 0, 4);
                level.setBlockAndUpdate(tanks[i], ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get().defaultBlockState()
                        .setValue(RouterBlock.TIER, tiers[i]));
                if (contents[i] > 0) {
                    ((StorageSourceTankBlockEntity) level.getBlockEntity(tanks[i])).store().insert(contents[i], false);
                }
            }
            for (int i = 0; i < 4; i++) {
                player.getInventory().setItem(i, StorageBlockItem.withTier(
                        ModItems.STORAGE.get(StorageKind.SOURCE_TANK).get(), tiers[i]));
            }
            return null;
        }), () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return false;
            }
            int[] expected = {0, 3, 6, 10};
            for (int i = 0; i < 4; i++) {
                BlockState state = minecraft.level.getBlockState(tanks[i]);
                if (!(state.getBlock() instanceof StorageSourceTankBlock)
                        || state.getValue(StorageSourceTankBlock.FILL) != expected[i]) {
                    return false;
                }
            }
            return true;
        }, () -> "tanques no cliente com os níveis 0, 3, 6 e 10"));
        list.add(sourceCamera("Tanque de Source: câmera geral", () -> origin[0], -2.0, 0, 0.3, 1.5, false, 0));
        list.add(wait("chunk do tanque", 20));
        list.add(capture("tanque-source-mundo"));
        list.add(sourceCamera("Tanque de Source: câmera de perto", () -> origin[0], 0.5, 0.6, 2.0, 1.5, false, 0));
        list.add(wait("tanque de perto", 10));
        list.add(capture("tanque-source-perto"));
        // Mira no tanque Elite, com a interface (mira e barra) e o item do Avançado na mão.
        list.add(sourceCamera("Tanque de Source: mira", () -> origin[0], 1.5, 0.4, 2.4, 2, true, 1));
        list.add(new Step("Tanque de Source: a mira está no tanque Elite", STEP_TIMEOUT_MS, () -> {
        }, () -> Minecraft.getInstance().hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(tanks[2]),
                () -> "mira em " + Minecraft.getInstance().hitResult));
        list.add(wait("contorno", 10));
        list.add(capture("tanque-source-contorno"));
        list.add(new Step("Tanque de Source: limpar", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = false;
            onServer(server -> {
                for (int i = 0; i < 4; i++) {
                    server.getPlayerList().getPlayer(playerId).getInventory().setItem(i, ItemStack.EMPTY);
                    server.overworld().setBlockAndUpdate(tanks[i], Blocks.AIR.defaultBlockState());
                }
                return null;
            });
        }, () -> true, () -> ""));
    }

    /**
     * Câmera do jogador em {@code origem + (dx, dy, dz)}, olhando o tanque de índice {@code tanque}
     * (o olho fica 1,62 acima do pé); {@code gui} liga a interface e {@code slot} escolhe a barra.
     */
    private static Step sourceCamera(String name, java.util.function.Supplier<BlockPos> origin, double dx, double dy,
                                     double dz, double tanque, boolean gui, int slot) {
        double[] cam = new double[3];
        float[] ang = new float[2];
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            BlockPos base = origin.get();
            // alvo: o centro do tanque de índice `tanque` (x = -3 + 2i, z = 4) e, de perto, só a altura do meio
            double tx = base.getX() + 0.5 + (-3 + 2 * tanque), ty = base.getY() + 0.6, tz = base.getZ() + 4.5;
            cam[0] = base.getX() + 0.5 + dx;
            cam[1] = base.getY() + dy;
            cam[2] = base.getZ() + 0.5 + dz;
            double ex = tx - cam[0], ey = ty - (cam[1] + 1.62), ez = tz - cam[2];
            ang[0] = (float) (Math.toDegrees(Math.atan2(-ex, ez)));
            ang[1] = (float) (-Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez))));
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.hideGui = !gui;
            minecraft.player.getInventory().selected = slot;
            minecraft.player.setYRot(ang[0]);
            minecraft.player.setXRot(ang[1]);
            minecraft.player.yRotO = ang[0];
            minecraft.player.xRotO = ang[1];
            UUID playerId = minecraft.player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.getInventory().selected = slot;
                player.teleportTo(player.serverLevel(), cam[0], cam[1], cam[2], ang[0], ang[1]);
                return null;
            });
        }, () -> Math.abs(Minecraft.getInstance().player.getXRot() - ang[1]) < 0.5f
                && Minecraft.getInstance().player.position().distanceToSqr(cam[0], cam[1], cam[2]) < 0.5,
                () -> "câmera em " + Minecraft.getInstance().player.position());
    }

    /** Arrasta a alça de redimensionar do Baú como o mouse: aperta, arrasta {@code dx, dy} e solta. */
    private static void dragGrip(StorageListScreen screen, int dx, int dy) {
        int[] grip = screen.gripPoint();
        screen.mouseClicked(grip[0], grip[1], 0);
        screen.mouseDragged(grip[0] + dx, grip[1] + dy, 0, dx, dy);
        screen.mouseReleased(grip[0] + dx, grip[1] + dy, 0);
    }

    /**
     * Depois de redimensionar: o painel continua no centro da tela e o inventário do jogador
     * acompanhou, centralizado embaixo dele.
     */
    private static boolean inventoryFollows(StorageListScreen screen) {
        Slot first = screen.getMenu().slots.stream().filter(s -> s.getContainerSlot() == 9).findFirst().orElseThrow();
        int center = first.x + 9 * 18 / 2 - 1;
        boolean centered = Math.abs(screen.getGuiLeft() + screen.getXSize() / 2 - screen.width / 2) <= 1
                && Math.abs(screen.getGuiTop() + screen.getYSize() / 2 - screen.height / 2) <= 1;
        return centered && Math.abs(center - screen.getXSize() / 2) <= 1
                && first.y + 3 * 18 + 4 + 18 + 8 + 8 >= screen.getYSize() - 8;
    }

    private static StorageListScreen chestScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof StorageListScreen screen) {
            return screen;
        }
        throw new StepFailure("a tela do Baú não está aberta: " + describe(Minecraft.getInstance().screen));
    }

    /** Na thread do servidor. */
    private static StorageChestBlockEntity storageChest(MinecraftServer server, BlockPos pos) {
        if (!(server.overworld().getBlockEntity(pos) instanceof StorageChestBlockEntity chest)) {
            throw new IllegalStateException("sem Baú Wireless em " + pos.toShortString());
        }
        return chest;
    }

    /** Clique direito do jogador no bloco, pela face de cima, com o que estiver na mão. */
    private static void useOn(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    // ------------------------------------------------------------------ Guia (GuideME)

    /** Páginas do guia (assets/wirelessautomate/guides/wirelessautomate/guide), na ordem da navegação. */
    private static final List<String> GUIDE_PAGES = List.of("index", "getting-started", "router", "upgrade-cards",
            "networks", "filters", "filter-card", "linker", "configurator", "network-tablet", "chunk-loading",
            "wireless-chest", "wireless-tank", "wireless-battery", "wireless-chemical-tank", "chemicals", "source",
            "wireless-source-tank", "troubleshooting", "recipes");

    /**
     * Livro-guia (só com o GuideME): abre cada página pelo comando de cliente {@code /guidemec open}
     * e salva uma captura, para conferir as cenas 3D, as receitas e o texto.
     */
    private static void guideSteps(List<Step> list) {
        list.add(new Step("livro-guia entregue no primeiro login", STEP_TIMEOUT_MS, () -> {
        }, () -> GuideBook.create().isPresent() && onServer(server -> GuideBook.given(server.getPlayerList()
                .getPlayer(Minecraft.getInstance().player.getUUID()))),
                () -> "o livro não foi entregue (a marca no jogador não está lá)"));
        guidePages(list, "");
        // De novo em português (as páginas de _pt_br/), e de volta ao idioma de antes.
        list.add(language("pt_br"));
        guidePages(list, "-pt");
        list.add(language("en_us"));
    }

    private static void guidePages(List<Step> list, String suffix) {
        for (String page : GUIDE_PAGES) {
            list.add(new Step("guia" + suffix + ": " + page, STEP_TIMEOUT_MS,
                    () -> ClientCommandHandler.runCommand("guidemec wirelessautomate:guide open wirelessautomate:" + page + ".md"),
                    () -> Minecraft.getInstance().screen != null
                            && Minecraft.getInstance().screen.getClass().getName().startsWith("guideme"),
                    () -> "tela " + describe(Minecraft.getInstance().screen)));
            list.add(capture("guia-" + page + suffix));
            // O resto da página: rola e captura de novo (as páginas longas passam de uma tela).
            for (int part = 2; part <= 4; part++) {
                list.add(new Step("rolar " + page + suffix, STEP_TIMEOUT_MS, () -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    Screen screen = minecraft.screen;
                    if (screen != null) {
                        screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -20);
                    }
                }, () -> true, () -> "tela " + describe(Minecraft.getInstance().screen)));
                list.add(capture("guia-" + page + suffix + "-" + part));
            }
        }
        list.add(new Step("fechar o guia" + suffix, STEP_TIMEOUT_MS, () -> Minecraft.getInstance().setScreen(null),
                () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
    }

    /** Troca o idioma do jogo e espera a recarga dos recursos. */
    private static Step language(String code) {
        java.util.concurrent.CompletableFuture<?>[] reload = new java.util.concurrent.CompletableFuture<?>[1];
        return new Step("idioma " + code, 120_000, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.languageCode = code;
            minecraft.getLanguageManager().setSelected(code);
            reload[0] = minecraft.reloadResourcePacks();
        }, () -> reload[0] != null && reload[0].isDone() && Minecraft.getInstance().getOverlay() == null,
                () -> "recarregando os recursos");
    }

    /** Segura ou solta o Shift como o teclado; termina quando o servidor vê o jogador agachado ou não. */
    private static Step shift(boolean down) {
        return new Step(down ? "segurar Shift" : "soltar Shift", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().options.keyShift.setDown(down),
                () -> Minecraft.getInstance().player.isShiftKeyDown() == down && onServer(server -> server.getPlayerList()
                        .getPlayer(Minecraft.getInstance().player.getUUID()).isShiftKeyDown() == down),
                () -> "Shift no cliente " + Minecraft.getInstance().player.isShiftKeyDown());
    }

    private static ItemStack serverWand(MinecraftServer server) {
        return server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID()).getMainHandItem();
    }

    /** O slot do inventário (na tela do roteador aberta) com um Cartão de Filtro, ou {@code null}. */
    private static @Nullable Slot inventoryCardSlot() throws StepFailure {
        RouterMenu menu = routerScreen().getMenu();
        for (int i = RouterMenu.CARD_SLOT_COUNT; i < menu.slots.size(); i++) {
            if (FilterCardItem.isCard(menu.getSlot(i).getItem())) {
                return menu.getSlot(i);
            }
        }
        return null;
    }

    /** Servidor: redes, baús e roteadores na frente do jogador e 64 pedras no baú A. */
    private static void setUpWorld() throws Exception {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            BlockPos base = player.blockPosition();
            level.setDayTime(6000);
            player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 180f, 30f);
            NetworkSavedData data = NetworkSavedData.get(server);
            WaNetwork main = data.create(player.getUUID(), MAIN_NETWORK);
            WaNetwork other = data.create(player.getUUID(), OTHER_NETWORK);
            WaNetwork energy = data.create(player.getUUID(), ENERGY_NETWORK);
            data.setActiveNetwork(player.getUUID(), main.id());
            mainNetwork = main.id();
            otherNetwork = other.id();
            energyNetwork = energy.id();
            chestA = base.offset(-1, 0, -3);
            chestB = base.offset(1, 0, -3);
            routerA = chestA.above();
            routerB = chestB.above();
            for (BlockPos chest : List.of(chestA, chestB)) {
                level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
                level.setBlockAndUpdate(chest.above(),
                        ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
                router(server, chest.above()).setNetworkId(main.id());
            }
            ((Container) level.getBlockEntity(chestA)).setItem(0, new ItemStack(Items.STONE, 64));
            return null;
        });
    }

    private static Step open(String name, java.util.function.Supplier<BlockPos> pos) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            BlockPos target = pos.get();
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        }, () -> Minecraft.getInstance().screen instanceof RouterScreen screen
                && screen.getMenu().snapshot().pos().equals(pos.get()),
                () -> "tela " + describe(Minecraft.getInstance().screen));
    }

    /** Esc, como o jogador fecharia; termina quando o servidor também fechou o menu. */
    private static Step close(String name) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) {
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
        }, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            return Minecraft.getInstance().screen == null && onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                return player.containerMenu == player.inventoryMenu;
            });
        }, () -> "tela " + describe(Minecraft.getInstance().screen));
    }

    /** Alguns quadros depois (mouse fora da tela, sem dicas), salva a captura. */
    private static Step capture(String file) {
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

    // ------------------------------------------------------------------ português, nome longo

    private static final String LONG_NETWORK = "Rede de teste com um nome bem comprido 40";
    private static UUID longNetwork;

    /**
     * Volta em português com uma rede de nome longo: roteador (mínimo e máximo), Vinculador e Tablet
     * (mínimo e máximo), com as capturas {@code *-pt-*} e, em cada tela, a conferência de que todo
     * texto cortado tem o texto inteiro no tooltip.
     */
    private static void portugueseSteps(List<Step> list) {
        list.add(language("pt_br"));
        list.add(new Step("rede de nome longo no roteador B", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                longNetwork = NetworkSavedData.get(server).create(playerId, LONG_NETWORK).id();
                NetworkSavedData.get(server).setActiveNetwork(playerId, longNetwork);
                router(server, routerB).setNetworkId(ResourceType.ITEM, longNetwork);
                // mão vazia: com o Vinculador na mão, o clique vincularia o roteador em vez de abrir a tela
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return null;
            });
        }, () -> onServer(server -> longNetwork.equals(router(server, routerB).networkId(ResourceType.ITEM)))
                && Minecraft.getInstance().player.getMainHandItem().isEmpty(),
                () -> "rede " + longNetwork));

        list.add(open("pt: abrir B", () -> routerB));
        list.add(new Step("pt: roteador no mínimo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(300, 240),
                () -> routerScreen().size()[0] == 300 && routerScreen().size()[1] == 240
                        && routerScreen().getMenu().snapshot().network(ResourceType.ITEM).equals(Optional.of(longNetwork)),
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", rede "
                        + routerScreen().getMenu().snapshot().network(ResourceType.ITEM)));
        list.add(new Step("pt: nome longo cortado com tooltip inteiro", STEP_TIMEOUT_MS, () -> {
            int[] center = GuiText.firstClipCenter();
            if (center != null) {
                moveMouse(center[0], center[1]);
            }
        }, () -> {
            for (int[] c : GuiText.clipCenters()) {
                Component full = GuiText.clipAt(c[0], c[1]);
                if (full != null && full.getString().contains(LONG_NETWORK)) {
                    return true;
                }
            }
            return false;
        }, () -> GuiText.clipCount() + " texto(s) cortado(s), nenhum com o nome longo inteiro"));
        list.add(capture("1d-roteador-pt-min"));
        list.add(new Step("pt: roteador no máximo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(10_000, 10_000),
                () -> routerScreen().size()[0] == routerScreen().width - 8 && routerScreen().size()[1] > 240,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size())));
        list.add(clipCheck("pt: roteador máximo"));
        list.add(capture("1e-roteador-pt-max"));
        list.add(new Step("pt: roteador de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(300, 240),
                () -> routerScreen().size()[0] == 300,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size())));
        list.add(close("pt: fechar B"));

        // Vinculador em modo Área, com três abas marcadas
        list.add(new Step("pt: Vinculador na mão", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ItemStack wand = new ItemStack(ModItems.LINKER.get());
            // modo Área com os dois roteadores dentro: o botão de vincular aparece
            LinkerItem.setMode(wand, LinkerMode.AREA);
            LinkerItem.setArea(wand, LinkerArea.firstCorner(net.minecraft.world.level.Level.OVERWORLD, areaCorner1)
                    .withSecond(areaCorner2));
            server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID())
                    .setItemInHand(InteractionHand.MAIN_HAND, wand);
            return null;
        }), () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.LINKER.get())
                && LinkerItem.mode(Minecraft.getInstance().player.getMainHandItem()) == LinkerMode.AREA,
                () -> "na mão " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(new Step("pt: abrir o Vinculador", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof LinkerScreen screen
                && screen.getMenu().snapshot().active().equals(Optional.of(longNetwork)),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        // três abas (sem Químicos): o título "Abas: Itens + Fluidos + Energia" é o texto mais longo
        list.add(new Step("pt: três abas", STEP_TIMEOUT_MS,
                () -> {
                    for (ResourceType type : new ResourceType[] {ResourceType.CHEMICAL, ResourceType.SOURCE}) {
                        if (linkerScreen().getMenu().snapshot().available().contains(type)) {
                            click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.type."
                                    + type.key())), "aba " + type));
                        }
                    }
                },
                () -> serverLinker(stack -> LinkerItem.effectiveTabs(stack).size() == 3),
                () -> "abas " + linkerScreen().getMenu().snapshot().tabs()));
        list.add(clipCheck("pt: Vinculador"));
        list.add(capture("8c-vinculador-pt"));
        list.add(close("pt: fechar o Vinculador"));

        list.add(new Step("pt: Tablet na mão", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID())
                    .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.NETWORK_TABLET.get()));
            return null;
        }), () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.NETWORK_TABLET.get()),
                () -> "na mão " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(new Step("pt: abrir o Tablet", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof TabletScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("pt: Tablet no mínimo, Lista", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(300, 240),
                () -> tabletScreen().size()[0] == 300 && stepTicks >= 10,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(clipCheck("pt: Tablet Lista"));
        list.add(new Step("pt: aba Estatísticas", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.tablet.tab.stats"), "aba Estatísticas")),
                () -> tabletScreen().cardCenter(ResourceType.ITEM) != null,
                () -> "aba " + tabletScreen().currentTab()));
        list.add(clipCheck("pt: Tablet mínimo"));
        list.add(capture("7f-tablet-pt-min"));
        list.add(new Step("pt: Tablet no máximo", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(10_000, 10_000),
                () -> tabletScreen().size()[0] == tabletScreen().width - 8 && tabletScreen().cardCenter(ResourceType.ITEM) != null,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(clipCheck("pt: Tablet máximo"));
        list.add(capture("7g-tablet-pt-max"));
        list.add(new Step("pt: Tablet de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(300, 240),
                () -> tabletScreen().size()[0] == 300,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(close("pt: fechar o Tablet"));
        list.add(language("en_us"));
    }

    /**
     * Se algum texto foi cortado no último quadro, põe o mouse nele e confere que o tooltip tem o
     * texto inteiro.
     */
    private static Step clipCheck(String name) {
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

    // ------------------------------------------------------------------ Vinculador por área

    private static final String AREA_NETWORK = "E2E Área";
    private static UUID areaNetwork;
    private static BlockPos areaCorner1;
    private static BlockPos areaCorner2;
    /** Rede de itens de A e de B antes do Vincular (só a de fluidos pode mudar). */
    private static UUID itemsA;
    private static UUID itemsB;

    /**
     * Vinculador em modo Área pela tela: com o Vinculador na mão e o Shift pressionado (o mesmo
     * pacote do teclado), clique no ar passa para Área e dois cliques marcam os cantos (o chão
     * diante do baú A e o roteador B), pegando os dois roteadores; solta o Shift, clique no ar abre a
     * tela, que mostra os dois na área; escolhe a rede "E2E Área" na lista, o tipo Fluidos e clica em
     * Vincular. Confere no servidor que só a aba de fluidos dos dois mudou e que a tela mostra o
     * resultado.
     */
    private static void linkerAreaSteps(List<Step> list) {
        list.add(new Step("Vinculador na mão", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                areaNetwork = NetworkSavedData.get(server).create(player.getUUID(), AREA_NETWORK).id();
                itemsA = router(server, routerA).networkId(ResourceType.ITEM);
                itemsB = router(server, routerB).networkId(ResourceType.ITEM);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
                return null;
            });
            areaCorner1 = chestA.offset(-1, -1, -1);
            areaCorner2 = routerB;
        }, () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.LINKER.get()),
                () -> "na mão " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(shift("Shift pressionado", true));
        list.add(new Step("Shift + clique no ar: modo Área", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> serverLinker(stack -> LinkerItem.mode(stack) == LinkerMode.AREA)
                && LinkerItem.mode(Minecraft.getInstance().player.getMainHandItem()) == LinkerMode.AREA,
                () -> "modo no servidor " + serverLinker(stack -> LinkerItem.mode(stack).name())));
        list.add(new Step("Shift + clique nos dois cantos", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            for (BlockPos corner : List.of(areaCorner1, areaCorner2)) {
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(corner), Direction.UP, corner, false));
            }
        }, () -> serverLinker(stack -> {
            LinkerArea area = LinkerItem.area(stack);
            return area != null && area.first().equals(areaCorner1) && area.second().equals(Optional.of(areaCorner2));
        }) && LinkerItem.area(Minecraft.getInstance().player.getMainHandItem()) != null
                && LinkerItem.area(Minecraft.getInstance().player.getMainHandItem()).complete(),
                () -> "área no servidor " + serverLinker(stack -> String.valueOf(LinkerItem.area(stack)))));
        list.add(shift("Shift solto", false));
        list.add(capture("7b-vinculador-contorno"));
        list.add(new Step("abrir o Vinculador (clique no ar)", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof LinkerScreen screen
                && screen.getMenu().snapshot().inside() == 2
                && screen.getMenu().snapshot().problem() == LinkerProblem.NONE,
                () -> Minecraft.getInstance().screen instanceof LinkerScreen screen
                        ? "na área " + screen.getMenu().snapshot().inside() + ", " + screen.getMenu().snapshot().problem()
                        : "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("8-vinculador-area"));
        list.add(new Step("escolher a rede " + AREA_NETWORK, STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.literal(AREA_NETWORK)), "linha da rede " + AREA_NETWORK)),
                () -> {
                    UUID playerId = Minecraft.getInstance().player.getUUID();
                    return areaNetwork.equals(onServer(server -> NetworkSavedData.get(server).activeNetwork(playerId)))
                            && linkerScreen().getMenu().snapshot().active().equals(Optional.of(areaNetwork));
                }, () -> "ativa na tela " + linkerScreen().getMenu().snapshot().active()));
        // de Todos, o botão deixa só a primeira aba; apertado de novo, marca tudo outra vez
        list.add(new Step("Todos com tudo marcado deixa uma aba", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.type.all"), "Todos")),
                () -> serverLinker(stack -> LinkerItem.effectiveTabs(stack).size() == 1),
                () -> "abas " + linkerScreen().getMenu().snapshot().tabs()));
        list.add(new Step("Todos marca tudo", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.type.all"), "Todos")),
                () -> serverLinker(stack -> LinkerItem.tabs(stack).isAll(LoadedTypes.LIST)),
                () -> "abas " + linkerScreen().getMenu().snapshot().tabs()));
        list.add(capture("8b-vinculador-chips"));
        list.add(new Step("Vinculador maior", STEP_TIMEOUT_MS,
                () -> linkerScreen().previewResize(460, 300),
                () -> linkerScreen().size()[0] > 300 && linkerScreen().size()[1] > 204,
                () -> "tamanho " + java.util.Arrays.toString(linkerScreen().size())));
        list.add(capture("8d-vinculador-grande"));
        list.add(new Step("Vinculador de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> linkerScreen().previewResize(300, 204),
                () -> linkerScreen().size()[0] == 300 && linkerScreen().size()[1] == 204,
                () -> "tamanho " + java.util.Arrays.toString(linkerScreen().size())));
        list.add(new Step("só a aba Fluidos", STEP_TIMEOUT_MS,
                () -> {
                    // de Todos, desmarca as outras caixas (só aparecem os tipos disponíveis)
                    for (ResourceType type : new ResourceType[] {ResourceType.ITEM, ResourceType.ENERGY,
                            ResourceType.CHEMICAL, ResourceType.SOURCE}) {
                        if (linkerScreen().getMenu().snapshot().available().contains(type)) {
                            click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.type."
                                    + type.key())), "aba " + type));
                        }
                    }
                },
                () -> serverLinker(stack -> LinkerItem.effectiveTabs(stack).equals(List.of(ResourceType.FLUID)))
                        && linkerScreen().getMenu().snapshot().effectiveTabs().equals(List.of(ResourceType.FLUID))
                        && linkerScreen().getMenu().snapshot().already() == 0,
                () -> "abas na tela " + linkerScreen().getMenu().snapshot().tabs()));
        list.add(new Step("Vincular", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.link.count"), "Vincular")),
                () -> onServer(server -> {
                    RouterBlockEntity a = router(server, routerA);
                    RouterBlockEntity b = router(server, routerB);
                    return areaNetwork.equals(a.networkId(ResourceType.FLUID))
                            && areaNetwork.equals(b.networkId(ResourceType.FLUID))
                            && java.util.Objects.equals(itemsA, a.networkId(ResourceType.ITEM))
                            && java.util.Objects.equals(itemsB, b.networkId(ResourceType.ITEM));
                }) && linkerScreen().getMenu().snapshot().outcome().map(o -> o.linked() == 2).orElse(false)
                        && linkerScreen().getMenu().snapshot().already() == 2,
                () -> "A: " + serverNetworks(routerA) + "; B: " + serverNetworks(routerB) + "; resultado na tela "
                        + linkerScreen().getMenu().snapshot().outcome()));
        list.add(capture("9-vinculador-vinculado"));
        list.add(close("fechar o Vinculador"));
    }

    /** Aperta ou solta o Shift como o teclado; termina quando o servidor vê o jogador agachado ou não. */
    private static Step shift(String name, boolean down) {
        return new Step(name, STEP_TIMEOUT_MS, () -> Minecraft.getInstance().options.keyShift.setDown(down), () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            return onServer(server -> server.getPlayerList().getPlayer(playerId).isShiftKeyDown() == down);
        }, () -> "Shift " + (down ? "apertado" : "solto"));
    }

    /** Lê o Vinculador da mão principal do jogador no servidor. */
    private static <T> T serverLinker(Function<ItemStack, T> read) throws Exception {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        return onServer(server -> read.apply(server.getPlayerList().getPlayer(playerId).getMainHandItem()));
    }

    private static LinkerScreen linkerScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof LinkerScreen screen) {
            return screen;
        }
        throw new StepFailure("a tela do Vinculador não está aberta (tela: " + describe(Minecraft.getInstance().screen) + ")");
    }

    // ------------------------------------------------------------------ cliente

    private static RouterScreen routerScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof RouterScreen screen) {
            return screen;
        }
        throw new StepFailure("a tela do roteador não está aberta (tela: " + describe(Minecraft.getInstance().screen) + ")");
    }

    private static PortMode clientMode() throws StepFailure {
        return routerScreen().getMenu().snapshot().face(ResourceType.ITEM, Direction.UP).mode();
    }

    private static String clientModeText() {
        try {
            return clientMode().name();
        } catch (StepFailure e) {
            return e.getMessage();
        }
    }

    private static String safeThroughput() {
        return Minecraft.getInstance().screen instanceof RouterScreen screen
                ? Long.toString(screen.getMenu().throughput()[ResourceType.ITEM.ordinal()]) : "?";
    }

    private static boolean clientSees(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getBlockState(pos).getBlock() instanceof RouterBlock;
    }

    private static Component face(Direction direction) {
        return Component.translatable("gui.wirelessautomate.router.face." + direction.getName());
    }

    private static Component mode(PortMode mode) {
        return Component.translatable("gui.wirelessautomate.router.mode." + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * O filtro tem a regra "Encantado só em #minecraft:pickaxes", e ela sozinha não pega a picareta
     * comum nem o livro encantado (fora da tag).
     */
    private static boolean hasPickaxeRule(Filter filter) {
        List<FilterEntry> rules = filter.entries().stream().filter(e -> e instanceof FilterEntry.RuleEntry r
                && Boolean.TRUE.equals(r.rule().flag(ItemRule.Property.ENCHANTED))
                && r.rule().scope().equals("#minecraft:pickaxes")).toList();
        Filter only = new Filter(Filter.ListMode.WHITELIST, false, rules);
        return !rules.isEmpty() && !only.testItem(new ItemStack(Items.DIAMOND_PICKAXE))
                && !only.testItem(new ItemStack(Items.ENCHANTED_BOOK));
    }

    private static FilterScreen filterScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof FilterScreen screen) {
            return screen;
        }
        throw new StepFailure("tela " + describe(Minecraft.getInstance().screen));
    }

    /** O painel está no centro da janela do jogo. */
    private static boolean centered(AbstractContainerScreen<?> screen) {
        return Math.abs(screen.getGuiLeft() + screen.getXSize() / 2 - screen.width / 2) <= 1
                && Math.abs(screen.getGuiTop() + screen.getYSize() / 2 - screen.height / 2) <= 1;
    }

    /** Arrasta a alça de redimensionar da tela de filtro como o mouse. */
    private static void dragGrip(FilterScreen screen, int dx, int dy) {
        int[] grip = screen.gripPoint();
        screen.mouseClicked(grip[0], grip[1], 0);
        screen.mouseDragged(grip[0] + dx, grip[1] + dy, 0, dx, dy);
        screen.mouseReleased(grip[0] + dx, grip[1] + dy, 0);
    }

    private static boolean hasTag(List<FilterEntry> entries) {
        ResourceLocation tag = ResourceLocation.parse(TAG_RULE.substring(1));
        return entries.stream().anyMatch(e -> e instanceof FilterEntry.TagEntry t && t.tag().equals(tag));
    }

    private static Predicate<AbstractWidget> byMessage(Component message) {
        return widget -> widget.getMessage().equals(message);
    }

    private static Predicate<AbstractWidget> byMessageKey(String key) {
        return widget -> widget.getMessage().getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key);
    }

    private static Predicate<AbstractWidget> byTooltip(Component tooltip) {
        return widget -> widget instanceof FlatButton button && tooltip.equals(button.currentTooltip());
    }

    /** O widget visível e ativo da tela aberta que satisfaz o filtro. */
    private static AbstractWidget widget(Predicate<AbstractWidget> filter, String what) throws StepFailure {
        AbstractWidget widget = find(filter);
        if (widget == null) {
            throw new StepFailure("não achei " + what + " em " + describe(Minecraft.getInstance().screen));
        }
        if (!widget.active) {
            throw new StepFailure(what + " está desativado");
        }
        return widget;
    }

    private static @Nullable AbstractWidget find(Predicate<AbstractWidget> filter) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.visible && filter.test(widget)) {
                    return widget;
                }
            }
        }
        return null;
    }

    /** Clique esquerdo no centro do widget, pelo mesmo caminho do mouse. */
    private static void click(AbstractWidget widget) throws StepFailure {
        click(Minecraft.getInstance().screen, widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2);
    }

    private static void click(Screen screen, int x, int y) throws StepFailure {
        if (screen == null) {
            throw new StepFailure("sem tela para clicar");
        }
        moveMouse(x, y);
        screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** Digita na tela aberta como o teclado faria ({@code charTyped} letra a letra). */
    private static void type(String text) throws StepFailure {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            throw new StepFailure("sem tela para digitar");
        }
        text.codePoints().forEach(c -> screen.charTyped((char) c, 0));
    }

    /**
     * Põe o cursor do jogo no ponto (coordenadas da GUI), para o realce e as dicas das capturas
     * seguirem os cliques. O GLFW ignora o próprio {@code glfwSetCursorPos} no X11, então o campo
     * do {@code MouseHandler} é acertado por reflexão; se falhar, só a aparência das capturas muda.
     */
    private static void moveMouse(int guiX, int guiY) {
        Minecraft minecraft = Minecraft.getInstance();
        double scale = minecraft.getWindow().getGuiScale();
        try {
            Field x = minecraft.mouseHandler.getClass().getDeclaredField("xpos");
            Field y = minecraft.mouseHandler.getClass().getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(minecraft.mouseHandler, guiX * scale);
            y.setDouble(minecraft.mouseHandler, guiY * scale);
        } catch (ReflectiveOperationException | RuntimeException e) {
            WirelessAutomate.LOGGER.debug("E2E: não deu para mover o cursor", e);
        }
    }

    private static String describe(@Nullable Screen screen) {
        return screen == null ? "nenhuma" : screen.getClass().getSimpleName();
    }

    private static void capture(Minecraft minecraft, String name) {
        Path dir = Path.of(OUTPUT);
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Files.createDirectories(dir);
            image.writeToFile(dir.resolve(name + ".png"));
            WirelessAutomate.LOGGER.info("E2E: captura salva em {}", dir.resolve(name + ".png"));
        } catch (IOException e) {
            WirelessAutomate.LOGGER.error("E2E: falha ao salvar a captura {}", name, e);
        }
    }

    // ------------------------------------------------------------------ servidor

    /** Roda na thread do servidor integrado e espera o resultado (o servidor nunca espera o cliente). */
    private static <T> T onServer(Function<MinecraftServer, T> task) throws Exception {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            throw new StepFailure("sem servidor integrado");
        }
        return server.submit(() -> task.apply(server)).get(5, TimeUnit.SECONDS);
    }

    private static RouterBlockEntity router(MinecraftServer server, BlockPos pos) {
        if (server.overworld().getBlockEntity(pos) instanceof RouterBlockEntity router) {
            return router;
        }
        throw new IllegalStateException("sem roteador em " + pos.toShortString());
    }

    private static int count(MinecraftServer server, BlockPos pos, Item item) {
        if (!(server.overworld().getBlockEntity(pos) instanceof Container container)) {
            throw new IllegalStateException("sem baú em " + pos.toShortString());
        }
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
