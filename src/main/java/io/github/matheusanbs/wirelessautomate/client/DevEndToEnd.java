package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
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
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

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
 *   <li>renomeia o nó e troca de rede pelo seletor;</li>
 *   <li>põe um Cartão de Filtro num slot da face Norte pela tela (pega no inventário, solta no slot).</li>
 * </ol>
 * Os cliques passam pelo mesmo caminho do mouse ({@code screen.mouseClicked} no centro do widget) e
 * cada passo só termina quando o servidor aplicou (lido no block entity, na thread do servidor) e o
 * snapshot novo chegou à tela. Salva capturas em pontos-chave e escreve {@code result.txt} com
 * {@code OK} ou {@code FALHA: <passo> <motivo>} na primeira linha e o log dos passos embaixo;
 * depois sai do mundo e fecha o jogo.
 *
 * <p>Os widgets são achados pela mensagem ou pela dica (as chaves de tradução); só a linha da lista
 * de redes usa a geometria da {@link RouterScreen} (altura da linha, {@link #DROPDOWN_ROW}).
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class DevEndToEnd {
    private static final String OUTPUT = System.getenv("WA_E2E");
    static final String WORLD = "wa-e2e";
    private static final String MAIN_NETWORK = "E2E Principal";
    private static final String OTHER_NETWORK = "E2E Outra";
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
                        steps = script();
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
        // os widgets de "Mais" só aparecem no quadro seguinte, como para o jogador
        list.add(new Step("abrir Mais do filtro", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.more")), "Mais do filtro")),
                () -> find(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.add"))) != null,
                () -> "Adicionar visível"));
        list.add(new Step("digitar a regra", STEP_TIMEOUT_MS, () -> type(TAG_RULE), () -> {
            AbstractWidget add = find(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.add")));
            return add != null && add.active;
        }, () -> "Adicionar ativo com \"" + TAG_RULE + "\""));
        list.add(new Step("regra por tag", STEP_TIMEOUT_MS, () -> {
            click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.add")), "Adicionar"));
        }, () -> onServer(server -> hasTag(router(server, routerB).face(ResourceType.ITEM, Direction.UP).filter().entries()))
                && Minecraft.getInstance().screen instanceof FilterScreen screen
                && hasTag(screen.getMenu().view().filter().entries()),
                () -> "filtro no servidor: " + onServer(server -> router(server, routerB)
                        .face(ResourceType.ITEM, Direction.UP).filter().entries().toString())));
        list.add(capture("4-filtro"));
        list.add(new Step("voltar ao roteador", STEP_TIMEOUT_MS,
                () -> click(widget(byTooltip(Component.translatable("gui.wirelessautomate.filter.back.tooltip")), "Voltar")),
                () -> Minecraft.getInstance().screen instanceof RouterScreen screen
                        && screen.getMenu().snapshot().pos().equals(routerB)
                        && screen.getMenu().snapshot().face(ResourceType.ITEM, Direction.UP).filterSize() == 1,
                () -> "tela " + describe(Minecraft.getInstance().screen)));

        list.add(new Step("renomear", STEP_TIMEOUT_MS, () -> {
            click(widget(byTooltip(Component.translatable("gui.wirelessautomate.router.rename.tooltip")), "nome do nó"));
            type(NODE_NAME);
            routerScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> NODE_NAME.equals(onServer(server -> router(server, routerB).name()))
                && NODE_NAME.equals(routerScreen().getMenu().snapshot().name()),
                () -> "servidor \"" + onServer(server -> router(server, routerB).name()) + "\", tela \""
                        + routerScreen().getMenu().snapshot().name() + "\""));
        list.add(new Step("trocar de rede", STEP_TIMEOUT_MS, () -> {
            RouterScreen screen = routerScreen();
            AbstractWidget pill = widget(byTooltip(Component.translatable("gui.wirelessautomate.router.network.tooltip")),
                    "seletor de rede");
            click(pill);
            List<NetworkEntry> networks = screen.getMenu().snapshot().networks();
            int row = -1;
            for (int i = 0; i < networks.size(); i++) {
                if (networks.get(i).id().equals(otherNetwork)) {
                    row = i;
                }
            }
            if (row < 0) {
                throw new StepFailure("a rede " + OTHER_NETWORK + " não está na lista da tela: " + networks);
            }
            // a lista abre logo abaixo da pílula: 2 px de folga, 2 px de borda e uma linha por rede
            int x = pill.getX() + pill.getWidth() / 2;
            int y = pill.getY() + pill.getHeight() + 2 + 2 + row * DROPDOWN_ROW + DROPDOWN_ROW / 2;
            click(screen, x, y);
        }, () -> otherNetwork.equals(onServer(server -> router(server, routerB).networkId()))
                && routerScreen().getMenu().snapshot().network().equals(Optional.of(otherNetwork)),
                () -> "servidor " + onServer(server -> String.valueOf(router(server, routerB).networkId()))
                        + ", tela " + routerScreen().getMenu().snapshot().network()));
        list.add(capture("5-roteador-b-final"));
        cardSteps(list);
        list.add(close("fechar B"));
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
            data.setActiveNetwork(player.getUUID(), main.id());
            mainNetwork = main.id();
            otherNetwork = other.id();
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
        return new Step("captura " + file, STEP_TIMEOUT_MS, () -> moveMouse(0, 0), () -> {
            if (stepTicks < 6) {
                return false;
            }
            capture(Minecraft.getInstance(), file);
            return true;
        }, () -> file + ".png");
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

    private static boolean hasTag(List<FilterEntry> entries) {
        ResourceLocation tag = ResourceLocation.parse(TAG_RULE.substring(1));
        return entries.stream().anyMatch(e -> e instanceof FilterEntry.TagEntry t && t.tag().equals(tag));
    }

    private static Predicate<AbstractWidget> byMessage(Component message) {
        return widget -> widget.getMessage().equals(message);
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
