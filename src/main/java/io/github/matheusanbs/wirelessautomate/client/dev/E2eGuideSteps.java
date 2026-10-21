package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.GuideBook;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.ClientCommandHandler;

/**
 * O livro-guia no e2e (só com o GuideME): cada página aberta pelo comando, rolada até o fim e fotografada, em
 * inglês e em português.
 */
final class E2eGuideSteps {
    private E2eGuideSteps() {
    }

    /** Páginas do guia (assets/wirelessautomate/guides/wirelessautomate/guide), na ordem da navegação. */
    private static final List<String> GUIDE_PAGES = List.of("index", "getting-started", "router", "upgrade-cards",
            "networks", "filters", "filter-card", "linker", "configurator", "network-tablet", "chunk-loading",
            "wireless-chest", "wireless-tank", "wireless-battery", "wireless-chemical-tank", "chemicals", "source",
            "wireless-source-tank", "troubleshooting", "recipe-editor", "recipes", "signal-quartz", "signal-resonator");

    /**
     * Livro-guia (só com o GuideME): abre cada página pelo comando de cliente {@code /guidemec open}
     * e salva uma captura, para conferir as cenas 3D, as receitas e o texto.
     */
    static void guideSteps(List<Step> list) {
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

    /** Teto de capturas por página do guia (a primeira e as rolagens). */
    private static final int GUIDE_MAX_PARTS = 16;
    /** Se a última rolagem do guia andou; parada no fim, as capturas seguintes da página ficam de fora. */
    private static boolean guideScrolled;

    /**
     * Rola o documento do guia uma tela para baixo pela barra de rolagem do GuideME (por reflexão: o
     * GuideME é opcional) e diz se a rolagem mudou. A roda do mouse no meio da janela cai no menu da
     * esquerda, conforme a escala, e não rolava a página.
     */
    private static boolean scrollGuide() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return false;
        }
        for (var child : screen.children()) {
            if (!child.getClass().getSimpleName().equals("GuideScrollbar")
                    || !(child instanceof net.minecraft.client.gui.components.AbstractWidget bar)) {
                continue;
            }
            try {
                var get = child.getClass().getMethod("getScrollAmount");
                var set = child.getClass().getMethod("setScrollAmount", int.class);
                int before = (int) get.invoke(child);
                set.invoke(child, before + Math.max(20, bar.getHeight() * 9 / 10));
                return (int) get.invoke(child) != before;
            } catch (ReflectiveOperationException e) {
                WirelessAutomate.LOGGER.warn("E2E: não deu para rolar o guia", e);
                return false;
            }
        }
        return false;
    }

    private static void guidePages(List<Step> list, String suffix) {
        for (String page : GUIDE_PAGES) {
            list.add(new Step("guia" + suffix + ": " + page, STEP_TIMEOUT_MS,
                    () -> ClientCommandHandler.runCommand("guidemec wirelessautomate:guide open wirelessautomate:" + page + ".md"),
                    () -> Minecraft.getInstance().screen != null
                            && Minecraft.getInstance().screen.getClass().getName().startsWith("guideme"),
                    () -> "tela " + describe(Minecraft.getInstance().screen)));
            list.add(capture("guia-" + page + suffix));
            // O resto da página: rola uma tela por vez e captura, até a rolagem não mudar mais (o fim).
            for (int part = 2; part <= GUIDE_MAX_PARTS; part++) {
                String file = "guia-" + page + suffix + "-" + part;
                list.add(new Step("rolar " + page + suffix, STEP_TIMEOUT_MS, () -> guideScrolled = scrollGuide(),
                        () -> true, () -> "tela " + describe(Minecraft.getInstance().screen)));
                list.add(new Step("captura " + file, STEP_TIMEOUT_MS, () -> moveMouse(0, 0), () -> {
                    if (!guideScrolled) {
                        return true;
                    }
                    if (stepTicks < 6) {
                        return false;
                    }
                    capture(Minecraft.getInstance(), file);
                    return true;
                }, () -> file + ".png"));
            }
        }
        list.add(new Step("fechar o guia" + suffix, STEP_TIMEOUT_MS, () -> Minecraft.getInstance().setScreen(null),
                () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
    }
}
