package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevScreenshot.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotFilter.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotTablet.*;

import io.github.matheusanbs.wirelessautomate.client.linker.LinkerOpenScreen;
import io.github.matheusanbs.wirelessautomate.client.linker.LinkerScreen;
import io.github.matheusanbs.wirelessautomate.linker.LinkerOpen;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot.Dot;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot.RouterInfo;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot.Status;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;

/**
 * As capturas do Vinculador: a tela com a prévia, o resultado, o desvincular, os estados sem roteador, sem
 * upgrade, sem área, só com o canto 1 e com a área grande demais, e a lista do modo Abrir.
 */
final class ScreenshotLinker {
    private ScreenshotLinker() {
    }

    /** Desenhada no lugar das outras a partir do primeiro passo do Vinculador. */
    static LinkerScreen linkerScreen;
    /** A lista do modo Abrir, quando é ela que está sendo fotografada. */
    static LinkerOpenScreen openScreen;

    private static final RouterInfo READY = new RouterInfo(Status.READY, "Linha de minério", new BlockPos(2, 64, 3), 7,
            32, LinkRefusal.NONE);

    /**
     * Tela do Vinculador: Vincular com a prévia (dica do botão), o resultado, Desvincular, sem roteador, o
     * roteador sem o Upgrade de Alcance, sem área, só com o canto 1, área grande demais e a lista do Abrir.
     */
    static List<Step> linkerSteps() {
        return List.of(
                new Step(() -> {
                    filterScreen = null;
                    tabletScreen = null;
                    openScreen = null;
                    linkerScreen = linkerScreen(linkerSample());
                    int[] center = linkerScreen.previewLinkCenter();
                    mouseX = center[0];
                    mouseY = center[1];
                }, "l1-vinculador-area"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(false, s.router(), s.first(), s.second(),
                            false, LinkerProblem.NONE, s.unloadedChunks(), s.linkedInside(), s.machines(),
                            s.maxVolume(), s.maxDistance(),
                            Optional.of(new LinkerSnapshot.Outcome(false, 9, 2, 3, 1, 0, 0))));
                }, "l2-vinculador-resultado"),
                new Step(() -> {
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(false, noRouter(), s.first(),
                            s.second(), false, s.problem(), s.unloadedChunks(), 0, List.of(), s.maxVolume(),
                            s.maxDistance(), Optional.empty()));
                }, "l3-vinculador-sem-roteador"),
                new Step(() -> {
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(false,
                            new RouterInfo(Status.READY, "Linha de minério", new BlockPos(2, 64, 3), 0, 32,
                                    LinkRefusal.NO_UPGRADE),
                            s.first(), s.second(), false, s.problem(), s.unloadedChunks(), 0, List.of(), s.maxVolume(),
                            s.maxDistance(), Optional.empty()));
                }, "l4-vinculador-sem-upgrade"),
                new Step(() -> {
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(false, s.router(), Optional.empty(),
                            Optional.empty(), false, LinkerProblem.NO_AREA, 0, 0, List.of(), s.maxVolume(),
                            s.maxDistance(), Optional.empty()));
                }, "l5-vinculador-sem-area"),
                new Step(() -> {
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(false, s.router(), s.first(),
                            Optional.empty(), false, LinkerProblem.INCOMPLETE, 0, 0, List.of(), s.maxVolume(),
                            s.maxDistance(), Optional.empty()));
                }, "l6-vinculador-canto1"),
                new Step(() -> {
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(false, s.router(), s.first(),
                            Optional.of(new BlockPos(300, 70, 200)), false, LinkerProblem.TOO_BIG, 0, 0, List.of(),
                            s.maxVolume(), s.maxDistance(), Optional.empty()));
                }, "l7-vinculador-grande"),
                new Step(() -> {
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(true, s.router(), s.first(), s.second(),
                            false, LinkerProblem.NONE, s.unloadedChunks(), s.linkedInside(), s.machines(),
                            s.maxVolume(), s.maxDistance(), Optional.empty()));
                }, "l8-vinculador-desvincular"),
                new Step(() -> {
                    linkerScreen = null;
                    openScreen = new LinkerOpenScreen(new BlockPos(4, 64, 3), List.of(
                            new LinkerOpen.Choice(new BlockPos(4, 65, 3), LinkerOpen.Kind.ATTACHED, 1,
                                    "Roteador da fornalha"),
                            new LinkerOpen.Choice(new BlockPos(2, 64, 3), LinkerOpen.Kind.REMOTE, 3,
                                    "Linha de minério"),
                            new LinkerOpen.Choice(new BlockPos(-9, 66, 8), LinkerOpen.Kind.REMOTE, 14,
                                    "Casa de máquinas do lado norte da base")), true);
                    Minecraft minecraft = Minecraft.getInstance();
                    openScreen.init(minecraft, minecraft.getWindow().getGuiScaledWidth(),
                            minecraft.getWindow().getGuiScaledHeight());
                    mouseX = mouseY = -1;
                }, "l9-vinculador-abrir-lista"));
    }

    private static RouterInfo noRouter() {
        return new RouterInfo(Status.NONE, "", BlockPos.ZERO, 0, 0, LinkRefusal.NONE);
    }

    static LinkerScreen linkerScreen(LinkerSnapshot snapshot) {
        LinkerScreen created = new LinkerScreen(new LinkerMenu(0, InteractionHand.MAIN_HAND, snapshot),
                new Inventory(null), Component.translatable("item.wirelessautomate.linker"), true);
        Minecraft minecraft = Minecraft.getInstance();
        created.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        return created;
    }

    /**
     * Área de 30×20 blocos com 11 máquinas do roteador "Linha de minério" dentro dela, 7 de 32 vagas usadas e
     * um chunk descarregado.
     */
    private static LinkerSnapshot linkerSample() {
        List<Dot> dots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            dots.add(new Dot(-4 + i * 3, 0));
            dots.add(new Dot(-4 + i * 3, 4));
        }
        dots.add(new Dot(14, 9));
        return new LinkerSnapshot(false, READY, Optional.of(new BlockPos(-8, 64, -6)),
                Optional.of(new BlockPos(21, 66, 13)), false, LinkerProblem.NONE, 1, dots.size(), List.copyOf(dots),
                262_144, 64, Optional.empty());
    }
}
