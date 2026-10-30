package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.guide.GuidePages;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideScreen;
import io.github.matheusanbs.wirelessautomate.item.GuideBook;
import io.github.matheusanbs.wirelessautomate.item.GuideClientHooks;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
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
