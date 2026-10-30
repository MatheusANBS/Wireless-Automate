package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.guide.GuidePages;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideScreen;
import io.github.matheusanbs.wirelessautomate.item.GuideBook;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideKeys;
import io.github.matheusanbs.wirelessautomate.item.GuideClientHooks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
                () -> "o livro não foi entregue (a marca no jogador não está lá)"));
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
     * teclado de verdade, com o {@code KeyMapping}) abre o livro em {@code router.md}.
     */
    private static void keySteps(List<Step> list) {
        list.add(new Step("guia: tecla G, o roteador no inventário", STEP_TIMEOUT_MS, () -> onServer(server -> {
            var player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().setItem(9, ModItems.ROUTER.get().getDefaultInstance());
            return null;
        }), () -> !Minecraft.getInstance().player.isCreative()
                && Minecraft.getInstance().player.getInventory().getItem(9).is(ModItems.ROUTER.get()),
                () -> "slot 9 " + Minecraft.getInstance().player.getInventory().getItem(9)));
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
                var keyboard = Minecraft.getInstance().keyboardHandler;
                long window = Minecraft.getInstance().getWindow().getWindow();
                int key = GuideKeys.OPEN_GUIDE.getKey().getValue();
                keyboard.keyPress(window, key, 0, GLFW.GLFW_PRESS, 0);
                keyboard.keyPress(window, key, 0, GLFW.GLFW_RELEASE, 0);
            }
            return false;
        }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("guia: tecla G, abriu em router.md", STEP_TIMEOUT_MS, () -> {
        }, () -> book() != null && "router".equals(book().page()) && book().spread() == 0,
                () -> book() == null ? "sem livro" : "página " + book().page()));
        list.add(new Step("guia: tecla G, fechar o livro", STEP_TIMEOUT_MS, () -> Minecraft.getInstance().setScreen(null),
                () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
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
