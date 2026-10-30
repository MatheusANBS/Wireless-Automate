package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideKeys;
import io.github.matheusanbs.wirelessautomate.client.guide.GuidePages;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideScreen;
import io.github.matheusanbs.wirelessautomate.item.GuideBook;
import io.github.matheusanbs.wirelessautomate.item.GuideClientHooks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.lwjgl.glfw.GLFW;

/**
 * O livro-guia no e2e: abre pelo gancho do item, primeiro onde parou (a folha de rosto e o índice) e depois cada
 * página, vira todas as folhas e captura cada par ({@code guia-<pagina>-<n>}), conferindo que nenhum texto foi
 * cortado nem ficou fora da folha (a conferência do próprio livro, {@code GuideScreen.problems()}). Em inglês e,
 * com a variante, em português.
 */
final class E2eGuideSteps {
    private E2eGuideSteps() {
    }

    /** Teto de pares de folhas por página; os que passam do fim da página viram passos instantâneos. */
    private static final int MAX_SPREADS = 16;
    /** Folhas e pares conferidos na passada (para o resumo no fim). */
    private static int checkedSheets;
    private static int checkedSpreads;

    /**
     * As páginas na ordem do índice; lidas na hora (as páginas só existem depois de abrir o livro uma vez), por isso
     * a lista de passos usa a fixa e confere contra a lida.
     */
    private static final List<String> PAGES = List.of("index", "getting-started", "router", "upgrade-cards",
            "networks", "filters", "filter-card", "linker", "configurator", "chunk-loading", "wireless-chest",
            "wireless-tank", "wireless-battery", "wireless-chemical-tank", "wireless-source-tank", "signal-quartz",
            "signal-resonator", "crystal-repairer", "network-tablet", "signal-screen", "chemicals", "source",
            "troubleshooting", "recipe-editor", "recipes");

    static void guideSteps(List<Step> list) {
        list.add(new Step("livro-guia entregue no primeiro login", STEP_TIMEOUT_MS, () -> {
        }, () -> onServer(server -> GuideBook.given(server.getPlayerList()
                .getPlayer(Minecraft.getInstance().player.getUUID()))),
                () -> "a marca guide_v2_given no jogador"));
        guidePages(list, "");
        keySteps(list);
        E2eVariants.pt(list, () -> {
            list.add(language("pt_br"));
            guidePages(list, "-pt");
            list.add(language("en_us"));
        });
    }

    private static GuideScreen book() {
        return Minecraft.getInstance().screen instanceof GuideScreen s ? s : null;
    }

    private static void guidePages(List<Step> list, String suffix) {
        list.add(new Step("guia" + suffix + ": zerar a contagem", STEP_TIMEOUT_MS, () -> {
            checkedSheets = 0;
            checkedSpreads = 0;
        }, () -> true, () -> ""));
        list.add(new Step("guia" + suffix + ": abertura pelo item", STEP_TIMEOUT_MS,
                () -> GuideClientHooks.open.accept(null), () -> book() != null,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("guia" + suffix + ": folha de rosto", STEP_TIMEOUT_MS, GuideScreen::openIndex,
                () -> book() != null && book().page() == null && book().spread() == 0,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(longKeyStep(suffix));
        list.add(new Step("guia" + suffix + ": as páginas lidas", STEP_TIMEOUT_MS, () -> {
        }, () -> GuidePages.pageIds().containsAll(PAGES) && PAGES.containsAll(GuidePages.pageIds()),
                () -> "páginas " + GuidePages.pageIds()));
        spreads(list, null, "abertura" + suffix);
        if (suffix.isEmpty()) {
            navigationSteps(list);
        }
        searchSteps(list, suffix);
        for (String page : PAGES) {
            list.add(new Step("guia" + suffix + ": " + page, STEP_TIMEOUT_MS,
                    () -> GuideClientHooks.open.accept(page + ".md"),
                    () -> book() != null && page.equals(book().page()) && book().spread() == 0,
                    () -> "tela " + describe(Minecraft.getInstance().screen)
                            + (book() == null ? "" : ", página " + book().page())));
            spreads(list, page, page + suffix);
        }
        list.add(new Step("guia" + suffix + ": tudo coube", STEP_TIMEOUT_MS, () -> {
        }, () -> checkedSpreads > PAGES.size(), () -> checkedSheets + " folhas em " + checkedSpreads
                + " pares conferidas, nada fora da folha"));
        list.add(new Step("fechar o guia" + suffix, STEP_TIMEOUT_MS, () -> Minecraft.getInstance().setScreen(null),
                () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
    }

    /**
     * A tecla G: com um roteador no inventário e o mouse sobre ele, a dica traz a linha da tecla e apertar a tecla (pelo
     * teclado de verdade, com o {@code KeyMapping} e o caractere que o GLFW manda junto) abre o livro em
     * {@code router.md} com a busca vazia; o Esc volta ao inventário. Depois o mesmo num baú, que continua aberto e
     * sincronizado (um clique nele chega ao servidor), e a busca do criativo com foco, que fica com a letra.
     */
    private static void keySteps(List<Step> list) {
        inventoryKeySteps(list);
        chestSteps(list);
        list.add(new Step("guia: tecla G, limpar o slot", STEP_TIMEOUT_MS, () -> onServer(server -> {
            var player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            player.getInventory().setItem(9, ItemStack.EMPTY);
            player.inventoryMenu.broadcastFullState();
            return null;
        }), () -> Minecraft.getInstance().player.getInventory().getItem(9).isEmpty(),
                () -> "slot 9 " + Minecraft.getInstance().player.getInventory().getItem(9) + ", servidor "
                        + onServer(server -> server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID())
                                .getInventory().getItem(9))));
        list.add(new Step("guia: tecla G, voltar ao criativo", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID()).setGameMode(GameType.CREATIVE);
            return null;
        }), () -> Minecraft.getInstance().player.isCreative(), () -> "criativo"));
        creativeSearchSteps(list);
    }

    /** No inventário: com um campo de texto focado não abre; com a dica, abre na página e o Esc volta ao inventário. */
    private static void inventoryKeySteps(List<Step> list) {
        list.add(new Step("guia: tecla G, o roteador no inventário", STEP_TIMEOUT_MS, () -> onServer(server -> {
            var player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().setItem(9, ModItems.ROUTER.get().getDefaultInstance());
            return null;
        }), () -> !Minecraft.getInstance().player.isCreative()
                && Minecraft.getInstance().player.getInventory().getItem(9).is(ModItems.ROUTER.get()),
                () -> "slot 9 " + Minecraft.getInstance().player.getInventory().getItem(9)));
        list.add(new Step("guia: tecla G com um campo de texto focado não abre", STEP_TIMEOUT_MS, () -> {
            if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) {
                Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
            }
        }, () -> {
            if (!(Minecraft.getInstance().screen instanceof InventoryScreen inventory) || stepTicks < 4) {
                return stepTicks > 4 && !(Minecraft.getInstance().screen instanceof InventoryScreen)
                        ? failNow("o livro abriu com um campo de texto focado") : false;
            }
            E2eUpgradeCardSteps.hoverSlot(inventory, 0);
            if (stepTicks == 6) {
                var box = new EditBox(Minecraft.getInstance().font, 0, 0, 50, 12, Component.empty());
                box.setFocused(true);
                inventory.setFocused(box);
            }
            if (stepTicks == 8) {
                pressGuideKey();
            }
            if (stepTicks == 16) {
                inventory.setFocused(null);
                return true;
            }
            return false;
        }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("guia: tecla G, dica e abertura", STEP_TIMEOUT_MS, () -> {
            if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) {
                Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
            }
        }, () -> {
            if (Minecraft.getInstance().screen instanceof GuideScreen) {
                return true;
            }
            if (!(Minecraft.getInstance().screen instanceof InventoryScreen inventory) || stepTicks < 4) {
                return false;
            }
            E2eUpgradeCardSteps.hoverSlot(inventory, 0);
            if (stepTicks == 8) {
                ItemStack router = Minecraft.getInstance().player.getInventory().getItem(9);
                boolean hint = router.getTooltipLines(Item.TooltipContext.of(Minecraft.getInstance().level),
                        Minecraft.getInstance().player, TooltipFlag.NORMAL).stream().anyMatch(line ->
                        line.getContents() instanceof TranslatableContents t
                                && "tooltip.wirelessautomate.open_guide".equals(t.getKey())
                                && line.getString().startsWith(Minecraft.getInstance().getLanguageManager()
                                        .getSelected().equals("pt_br") ? "Aperte " : "Press "));
                if (!hint) {
                    throw new StepFailure("a dica do roteador não tem a linha da tecla G");
                }
                pressGuideKey();
            }
            return false;
        }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("guia: tecla G, abriu em router.md com a busca vazia", STEP_TIMEOUT_MS, () -> {
        }, () -> book() != null && "router".equals(book().page()) && book().spread() == 0
                && book().searchText().isEmpty() && book().parent() instanceof InventoryScreen, E2eGuideSteps::bookState));
        list.add(new Step("guia: tecla G, o Esc volta ao inventário", STEP_TIMEOUT_MS,
                () -> book().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0),
                () -> Minecraft.getInstance().screen instanceof InventoryScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("guia: tecla G, fechar o inventário", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().screen.onClose(),
                () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
    }

    private static String bookState() {
        GuideScreen s = book();
        return s == null ? "tela " + describe(Minecraft.getInstance().screen)
                : "página " + s.page() + ", busca \"" + s.searchText() + "\", volta para " + describe(s.parent());
    }

    /** Onde o passo do baú o põe (perto do jogador, num lugar vazio) e a tela dele. */
    private static BlockPos chest;
    private static ContainerScreen chestScreen;

    private static int serverMenuId() throws Exception {
        return onServer(server -> server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID())
                .containerMenu.containerId);
    }

    /**
     * G num baú: o livro abre por cima dele e o Esc volta à tela do baú, com o mesmo menu nos dois lados; um clique
     * no slot do baú pega o roteador no servidor (com o menu dessincronizado, o clique seria descartado).
     */
    private static void chestSteps(List<Step> list) {
        list.add(new Step("guia: tecla G num baú, abrir o baú", STEP_TIMEOUT_MS, () -> chest = onServer(server -> {
            var player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            var level = player.serverLevel();
            BlockPos pos = player.blockPosition().offset(2, 0, 0);
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            ChestBlockEntity be = (ChestBlockEntity) level.getBlockEntity(pos);
            be.setItem(0, ModItems.ROUTER.get().getDefaultInstance());
            player.openMenu(be);
            return pos;
        }), () -> {
            if (Minecraft.getInstance().screen instanceof ContainerScreen screen
                    && screen.getMenu().getSlot(0).getItem().is(ModItems.ROUTER.get())) {
                chestScreen = screen;
                return true;
            }
            return false;
        }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("guia: tecla G num baú abre o livro por cima", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            if (book() != null) {
                return "router".equals(book().page()) && book().searchText().isEmpty()
                        && book().parent() == chestScreen;
            }
            if (Minecraft.getInstance().screen != chestScreen || stepTicks < 2) {
                return false;
            }
            Slot slot = chestScreen.getMenu().getSlot(0);
            moveMouse(chestScreen.getGuiLeft() + slot.x + 8, chestScreen.getGuiTop() + slot.y + 8);
            if (stepTicks == 6) {
                pressGuideKey();
            }
            return false;
        }, E2eGuideSteps::bookState));
        list.add(new Step("guia: tecla G num baú, o Esc volta ao baú aberto", STEP_TIMEOUT_MS,
                () -> book().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), () -> {
            var player = Minecraft.getInstance().player;
            int id = player.containerMenu.containerId;
            return Minecraft.getInstance().screen == chestScreen && player.containerMenu == chestScreen.getMenu()
                    && id != 0 && serverMenuId() == id;
        }, () -> "tela " + describe(Minecraft.getInstance().screen) + ", menu do cliente "
                + Minecraft.getInstance().player.containerMenu.containerId + ", do servidor " + serverMenuId()));
        list.add(new Step("guia: tecla G num baú, um clique no baú chega ao servidor", STEP_TIMEOUT_MS, () -> {
            var mc = Minecraft.getInstance();
            mc.gameMode.handleInventoryMouseClick(chestScreen.getMenu().containerId, 0, 0, ClickType.PICKUP, mc.player);
        }, () -> onServer(server -> {
            var menu = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID()).containerMenu;
            return menu.getCarried().is(ModItems.ROUTER.get()) && menu.getSlot(0).getItem().isEmpty();
        }), () -> "no servidor, na mão " + onServer(server -> server.getPlayerList()
                .getPlayer(Minecraft.getInstance().player.getUUID()).containerMenu.getCarried())));
        list.add(new Step("guia: tecla G num baú, devolver e fechar o baú", STEP_TIMEOUT_MS, () -> {
            var mc = Minecraft.getInstance();
            mc.gameMode.handleInventoryMouseClick(chestScreen.getMenu().containerId, 0, 0, ClickType.PICKUP, mc.player);
            chestScreen.onClose();
        }, () -> Minecraft.getInstance().screen == null && onServer(server -> {
            var player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            return player.containerMenu == player.inventoryMenu
                    && ((ChestBlockEntity) player.serverLevel().getBlockEntity(chest)).getItem(0)
                            .is(ModItems.ROUTER.get());
        }), () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("guia: tecla G num baú, tirar o baú", STEP_TIMEOUT_MS, () -> onServer(server -> {
            var level = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID()).serverLevel();
            ((ChestBlockEntity) level.getBlockEntity(chest)).clearContent();
            level.setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState());
            chestScreen = null;
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(chest).isAir(), () -> "bloco "
                + Minecraft.getInstance().level.getBlockState(chest)));
    }

    /** A busca do criativo antes da tecla, para conferir que ela ficou com a letra. */
    private static String searchBefore = "";

    /**
     * A busca do inventário criativo com foco (a caixa liga o foco dela sem virar o filho com foco da tela): com o
     * mouse sobre um item do mod, G escreve a letra na busca e não abre o livro.
     */
    private static void creativeSearchSteps(List<Step> list) {
        list.add(new Step("guia: tecla G na busca do criativo escreve e não abre", STEP_TIMEOUT_MS, () -> {
            var mc = Minecraft.getInstance();
            mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                    mc.options.operatorItemsTab().get()));
        }, () -> {
            if (book() != null) {
                return failNow("o livro abriu com a busca do criativo focada");
            }
            if (!(Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen screen) || stepTicks < 2) {
                return false;
            }
            EditBox box = creativeSearch(screen);
            if (stepTicks == 3 && !box.isVisible()) {
                // fora da aba de busca, a tecla do chat leva a ela (e o jogo descarta a letra)
                int chat = Minecraft.getInstance().options.keyChat.getKey().getValue();
                Minecraft.getInstance().keyboardHandler.keyPress(window(), chat, 0, GLFW.GLFW_PRESS, 0);
                typeChar(Character.toLowerCase((char) chat));
                Minecraft.getInstance().keyboardHandler.keyPress(window(), chat, 0, GLFW.GLFW_RELEASE, 0);
            }
            if (stepTicks == 4 && box.isVisible() && !box.isFocused()) {
                // o All The Tweaks (no cliente de dev, e no ATM10) tira o foco automático da busca; o jogo puro o liga
                // assim, só na caixa, sem que ela vire o filho com foco da tela: é o caso que a trava tem de pegar
                box.setFocused(true);
            }
            if (stepTicks == 5) {
                if (screen.getFocused() == box) {
                    throw new StepFailure("a busca do criativo virou o filho com foco: o teste não cobre o caso");
                }
                if (!box.isVisible() || !box.isFocused()) {
                    throw new StepFailure("a busca do criativo devia estar à vista e com foco (à vista " + box.isVisible()
                            + ", foco " + box.isFocused() + ", foco da tela " + screen.getFocused() + ")");
                }
                box.setValue("");
                type("wireless");
                searchBefore = box.getValue();
            }
            if (stepTicks >= 6 && stepTicks < 9) {
                for (Slot slot : screen.getMenu().slots) {
                    if (hasPage(slot.getItem())) {
                        moveMouse(screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8);
                        break;
                    }
                }
            }
            if (stepTicks == 9) {
                Slot under = screen.getSlotUnderMouse();
                if (under == null || !hasPage(under.getItem())) {
                    throw new StepFailure("nenhum item com página sob o mouse na busca do criativo");
                }
                pressGuideKey();
            }
            return stepTicks >= 16 && box.getValue().equals(searchBefore + "g");
        }, () -> Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen screen
                ? "busca \"" + creativeSearch(screen).getValue() + "\"" : "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("guia: tecla G, fechar o criativo", STEP_TIMEOUT_MS, () -> Minecraft.getInstance().setScreen(null),
                () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
    }

    private static boolean hasPage(ItemStack stack) {
        return !stack.isEmpty() && GuidePages.pageForItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) != null;
    }

    private static EditBox creativeSearch(CreativeModeInventoryScreen screen) {
        for (var child : screen.children()) {
            if (child instanceof EditBox box) {
                return box;
            }
        }
        throw new IllegalStateException("o inventário criativo sem a caixa de busca");
    }

    private static long window() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    /**
     * Aperta a tecla do livro como o jogo: o GLFW entrega a tecla e, na mesma rodada, o caractere dela para a tela
     * que estiver aberta naquele momento; depois a soltura.
     */
    private static void pressGuideKey() throws StepFailure {
        var keyboard = Minecraft.getInstance().keyboardHandler;
        int key = GuideKeys.OPEN_GUIDE.getKey().getValue();
        keyboard.keyPress(window(), key, 0, GLFW.GLFW_PRESS, 0);
        typeChar(Character.toLowerCase((char) key));
        keyboard.keyPress(window(), key, 0, GLFW.GLFW_RELEASE, 0);
    }

    /** O caractere pelo caminho do teclado ({@code KeyboardHandler.charTyped}, privado), com os eventos do NeoForge. */
    private static void typeChar(char c) throws StepFailure {
        try {
            Method charTyped = KeyboardHandler.class.getDeclaredMethod("charTyped", long.class, int.class, int.class);
            charTyped.setAccessible(true);
            charTyped.invoke(Minecraft.getInstance().keyboardHandler, window(), (int) c, 0);
        } catch (ReflectiveOperationException e) {
            throw new StepFailure("sem o charTyped do teclado: " + e);
        }
    }

    /**
     * A dica da folha de rosto diz o nome real da tecla: com teclas de nome longo, ela continua cabendo (a conferência
     * da própria folha de rosto, {@code problems()}). A tecla volta à de antes sem gravar as opções.
     */
    private static Step longKeyStep(String suffix) {
        return new Step("guia" + suffix + ": dica da folha de rosto com teclas de nome longo", STEP_TIMEOUT_MS, () -> {
            KeyMapping mapping = GuideKeys.OPEN_GUIDE;
            InputConstants.Key before = mapping.getKey();
            List<String> found = new ArrayList<>();
            try {
                for (int key : new int[] {GLFW.GLFW_KEY_NUM_LOCK, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_RIGHT_CONTROL,
                        GLFW.GLFW_KEY_PRINT_SCREEN}) {
                    mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
                    KeyMapping.resetMapping();
                    GuideScreen.openIndex();
                    if (book() == null || !book().problems().isEmpty()) {
                        found.add(mapping.getTranslatedKeyMessage().getString() + ": "
                                + (book() == null ? "sem livro" : String.join(" | ", book().problems())));
                    }
                }
            } finally {
                mapping.setKey(before);
                KeyMapping.resetMapping();
            }
            if (!found.isEmpty()) {
                throw new StepFailure("não coube: " + String.join("; ", found));
            }
            GuideScreen.openIndex();
        }, () -> book() != null && book().page() == null && book().problems().isEmpty(), () -> "tecla "
                + GuideKeys.OPEN_GUIDE.getTranslatedKeyMessage().getString());
    }

    private static boolean failNow(String message) throws StepFailure {
        throw new StepFailure(message);
    }

    /** O clique num link do índice, o voltar (Backspace) e as setas do teclado, na folha de rosto. */
    private static void navigationSteps(List<Step> list) {
        list.add(new Step("guia: clique no link do roteador no índice", STEP_TIMEOUT_MS, () -> {
            GuideScreen s = book();
            if (s != null) {
                s.turnTo(0);
            }
            int[] c = s == null ? null : s.linkCenter("router");
            if (c == null) {
                throw new StepFailure("o índice não tem o link do roteador à vista");
            }
            click(s, c[0], c[1]);
        }, () -> book() != null && "router".equals(book().page()) && book().spread() == 0,
                () -> book() == null ? "sem livro" : "página " + book().page()));
        list.add(new Step("guia: Backspace volta ao índice", STEP_TIMEOUT_MS,
                () -> book().keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0),
                () -> book() != null && book().page() == null && book().spread() == 0,
                () -> book() == null ? "sem livro" : "página " + book().page()));
        list.add(new Step("guia: seta para a direita vira as folhas", STEP_TIMEOUT_MS,
                () -> book().keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0),
                () -> book() != null && book().page() == null && book().spread() == 1,
                () -> book() == null ? "sem livro" : "par " + book().spread()));
        // o ícone de cada linha do índice leva à página da linha (o de "Redes" é o Vinculador, o da abertura o roteador)
        for (String target : List.of("index", "networks")) {
            list.add(new Step("guia: o ícone da linha " + target + " no índice", STEP_TIMEOUT_MS, () -> {
                GuideScreen.openIndex();
                GuideScreen s = book();
                for (int n = 0; n < s.spreads() && s.linkCenter(target) == null; n++) {
                    s.turnTo(n);
                }
                if (s.linkCenter(target) == null) {
                    throw new StepFailure("o índice não tem o link de " + target);
                }
                int[] c = s.iconCenter(target);
                if (c == null) {
                    throw new StepFailure("o ícone da linha de " + target + " leva a outra página");
                }
                click(s, c[0], c[1]);
            }, () -> book() != null && target.equals(book().page()),
                    () -> book() == null ? "sem livro" : "página " + book().page()));
        }
    }

    /**
     * A busca: digita na folha de rosto, confere que o primeiro resultado é a página do baú (em inglês, a busca por
     * "chest": o título dela não tem "bau") e captura; depois o Enter abre a página.
     */
    private static void searchSteps(List<Step> list, String suffix) {
        String query = suffix.isEmpty() ? "chest" : "bau";
        String first = "wireless-chest";
        list.add(new Step("guia" + suffix + ": digitar na busca", STEP_TIMEOUT_MS, () -> {
            moveMouse(0, 0);
            GuideScreen.openIndex();
            for (char c : query.toCharArray()) {
                book().charTyped(c, 0);
            }
        }, () -> {
            GuideScreen s = book();
            if (s == null || !query.equals(s.searchText()) || s.resultPages().isEmpty() || stepTicks < 6) {
                return false;
            }
            if (!first.equals(s.resultPages().get(0))) {
                throw new StepFailure("o primeiro resultado de \"" + query + "\" devia ser " + first + ": "
                        + s.resultPages());
            }
            if (!s.problems().isEmpty()) {
                throw new StepFailure("não coube: " + String.join(" | ", s.problems()));
            }
            capture(Minecraft.getInstance(), "guia-busca" + suffix);
            return true;
        }, () -> book() == null ? "sem livro" : "busca \"" + book().searchText() + "\": " + book().resultPages()));
        list.add(new Step("guia" + suffix + ": Enter abre o resultado marcado", STEP_TIMEOUT_MS,
                () -> book().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
                () -> book() != null && first.equals(book().page()) && book().spread() == 0,
                () -> book() == null ? "sem livro" : "página " + book().page()));
        String common = suffix.isEmpty() ? "router" : "roteador";
        list.add(new Step("guia" + suffix + ": descer até o 9º resultado", STEP_TIMEOUT_MS, () -> {
            moveMouse(0, 0);
            GuideScreen.openIndex();
            for (char c : common.toCharArray()) {
                book().charTyped(c, 0);
            }
            if (book().resultCount() <= 8) {
                throw new StepFailure("a busca \"" + common + "\" devia dar mais de 8 resultados: "
                        + book().resultCount());
            }
            for (int i = 0; i < 8; i++) {
                book().keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            }
        }, () -> {
            GuideScreen s = book();
            if (s == null || s.resultPages().isEmpty() || stepTicks < 6) {
                return false;
            }
            if (!s.resultPages().get(0).equals(s.markedResult())) {
                throw new StepFailure("o 9º resultado devia estar à vista e marcado: " + s.markedResult() + " em "
                        + s.resultPages());
            }
            if (!s.problems().isEmpty()) {
                throw new StepFailure("não coube: " + String.join(" | ", s.problems()));
            }
            capture(Minecraft.getInstance(), "guia-busca-9" + suffix);
            return true;
        }, () -> book() == null ? "sem livro" : "marcado " + book().markedResult() + " em " + book().resultPages()));
        list.add(new Step("guia" + suffix + ": busca sem resultado", STEP_TIMEOUT_MS, () -> {
            GuideScreen.openIndex();
            for (char c : "zzzzqx".toCharArray()) {
                book().charTyped(c, 0);
            }
        }, () -> book() != null && book().page() == null && "zzzzqx".equals(book().searchText())
                && book().problems().isEmpty(), () -> book() == null ? "sem livro" : book().problems().toString()));
        list.add(new Step("guia" + suffix + ": Esc limpa e depois fecha", STEP_TIMEOUT_MS, () -> {
            GuideScreen s = book();
            s.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            if (!s.searchText().isEmpty() || Minecraft.getInstance().screen != s) {
                throw new StepFailure("o primeiro Esc devia só limpar a busca");
            }
            s.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }, () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
    }

    /** Vira cada par de folhas da página aberta, confere o texto e captura; pares além do fim passam direto. */
    private static void spreads(List<Step> list, String page, String name) {
        for (int n = 0; n < MAX_SPREADS; n++) {
            int spread = n;
            String file = "guia-" + name + "-" + (n + 1);
            list.add(new Step("folhas " + file, STEP_TIMEOUT_MS, () -> {
                moveMouse(0, 0);
                GuideScreen s = book();
                if (s != null && spread < s.spreads()) {
                    s.turnTo(spread);
                }
            }, () -> {
                GuideScreen s = book();
                if (s == null || !Objects.equals(page, s.page())) {
                    return false;
                }
                if (spread >= s.spreads()) {
                    return true;
                }
                if (stepTicks < 6 || s.spread() != spread) {
                    return false;
                }
                if (!s.problems().isEmpty()) {
                    throw new StepFailure("não coube: " + String.join(" | ", s.problems()));
                }
                checkedSheets += s.visibleSheets();
                checkedSpreads++;
                capture(Minecraft.getInstance(), file);
                return true;
            }, () -> {
                GuideScreen s = book();
                return s == null ? "tela " + describe(Minecraft.getInstance().screen)
                        : "par " + s.spread() + " de " + s.spreads() + ", " + s.problems().size() + " problema(s)";
            }));
        }
    }
}
