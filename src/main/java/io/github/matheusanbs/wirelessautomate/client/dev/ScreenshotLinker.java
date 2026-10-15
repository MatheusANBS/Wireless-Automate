package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevScreenshot.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotFilter.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotTablet.*;

import io.github.matheusanbs.wirelessautomate.client.linker.LinkerScreen;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot.RouterDot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;

/**
 * As capturas da tela do Vinculador: modo Área com a prévia, o resultado, o modo Único, os problemas da área, a
 * rede nova e o desvincular.
 */
final class ScreenshotLinker {
    private ScreenshotLinker() {
    }

    /** Desenhada no lugar das outras a partir do primeiro passo do Vinculador. */
    static LinkerScreen linkerScreen;

    /**
     * Tela do Vinculador: modo Área com a prévia (dica do Vincular), o resultado depois de vincular,
     * o modo Único, Área sem cantos, só com o canto 1, área grande demais e criando uma rede.
     */
    static List<Step> linkerSteps() {
        return List.of(
                new Step(() -> {
                    filterScreen = null;
                    tabletScreen = null;
                    linkerScreen = linkerScreen(linkerSample());
                    int[] center = linkerScreen.previewLinkCenter();
                    mouseX = center[0];
                    mouseY = center[1];
                }, "l1-vinculador-area"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(s.networks(), s.active(), false, s.tabs(),
                            s.available(), s.mode(), s.first(), s.second(), false, s.inside(), s.inside(),
                            s.unloadedChunks(),
                            s.routers().stream().map(d -> new RouterDot(d.x(), d.z(), 0xBA68C8, true)).toList(),
                            LinkerProblem.NONE, s.maxVolume(), s.maxDistance(),
                            Optional.of(new LinkerSnapshot.Outcome(s.inside() - s.already(), s.already(), 0, 1,
                                    s.tabs(), false, "Linha 5x", 0xBA68C8))));
                }, "l2-vinculador-resultado"),
                new Step(() -> {
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(s.networks(), s.active(), false,
                            LinkerTabs.ALL, s.available(), LinkerMode.SINGLE, s.first(), s.second(), false, s.inside(),
                            s.already(), s.unloadedChunks(), s.routers(), s.problem(), s.maxVolume(), s.maxDistance(),
                            Optional.empty()));
                }, "l3-vinculador-unico"),
                new Step(() -> {
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(s.networks(), s.active(), false, s.tabs(),
                            s.available(), s.mode(), Optional.empty(), Optional.empty(), false, 0, 0, 0, List.of(),
                            LinkerProblem.NO_AREA,
                            s.maxVolume(), s.maxDistance(), Optional.empty()));
                }, "l4-vinculador-sem-area"),
                new Step(() -> {
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(s.networks(), s.active(), false, s.tabs(),
                            s.available(), s.mode(), s.first(), Optional.empty(), false, 0, 0, 0, List.of(),
                            LinkerProblem.INCOMPLETE,
                            s.maxVolume(), s.maxDistance(), Optional.empty()));
                }, "l5-vinculador-canto1"),
                new Step(() -> {
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(s.networks(), s.active(), false, s.tabs(),
                            s.available(), s.mode(), s.first(), Optional.of(new BlockPos(300, 70, 200)), false, 0, 0, 0,
                            List.of(),
                            LinkerProblem.TOO_BIG, s.maxVolume(), s.maxDistance(), Optional.empty()));
                }, "l6-vinculador-grande"),
                new Step(() -> {
                    linkerScreen.getMenu().applySnapshot(linkerSample());
                    linkerScreen.previewCreate("Linha de fundição");
                }, "l7-vinculador-nova-rede"),
                new Step(() -> {
                    // Nenhuma (desvincular) com Itens + Fluidos + Químicos: a Energia fica
                    linkerScreen.previewCancelCreate();
                    LinkerSnapshot s = linkerSample();
                    linkerScreen.getMenu().applySnapshot(new LinkerSnapshot(s.networks(), s.active(), true,
                            LinkerTabs.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.CHEMICAL),
                            LinkerTabs.available(LoadedTypes.LIST),
                            s.mode(), s.first(), s.second(), false, s.inside(), 2, s.unloadedChunks(),
                            s.routers().stream().map(d -> new RouterDot(d.x(), d.z(), d.color(), d.color() < 0))
                                    .toList(),
                            LinkerProblem.NONE, s.maxVolume(), s.maxDistance(), Optional.empty()));
                }, "l8-vinculador-desvincular"));
    }

    static LinkerScreen linkerScreen(LinkerSnapshot snapshot) {
        LinkerScreen created = new LinkerScreen(new LinkerMenu(0, InteractionHand.MAIN_HAND, snapshot),
                new Inventory(null), Component.translatable("item.wirelessautomate.linker"), true);
        Minecraft minecraft = Minecraft.getInstance();
        created.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        return created;
    }

    /**
     * Área de 30×20 blocos com 14 roteadores: os da linha 5x já na rede ativa ("Linha 5x", itens),
     * os outros na "Base" ou sem rede; um chunk descarregado.
     */
    private static LinkerSnapshot linkerSample() {
        UUID base = UUID.nameUUIDFromBytes("base".getBytes());
        UUID line = UUID.nameUUIDFromBytes("linha".getBytes());
        List<NetworkEntry> networks = List.of(
                new NetworkEntry(base, "Base", 0x3D8BFF, true),
                new NetworkEntry(line, "Linha 5x", 0xBA68C8, true),
                new NetworkEntry(UUID.nameUUIDFromBytes("fluidos".getBytes()), "Fluidos", 0x45D6CC, true),
                new NetworkEntry(UUID.nameUUIDFromBytes("minerio".getBytes()), "Minério", 0xD8875A, true),
                new NetworkEntry(LONG, "Processamento de minérios do lado norte", 0x81C784, true),
                new NetworkEntry(UUID.nameUUIDFromBytes("fundicao".getBytes()), "Fundição", 0xFFF176, true),
                new NetworkEntry(UUID.nameUUIDFromBytes("energia".getBytes()), "Energia", 0xFFB020, false));
        List<RouterDot> dots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            dots.add(new RouterDot(-4 + i * 3, 0, 0xBA68C8, true));
            dots.add(new RouterDot(-4 + i * 3, 4, i % 2 == 0 ? 0x3D8BFF : -1, false));
        }
        dots.add(new RouterDot(14, 9, 0x3D8BFF, false));
        dots.add(new RouterDot(16, 9, 0x3D8BFF, false));
        dots.add(new RouterDot(19, 11, 0x45D6CC, false));
        dots.add(new RouterDot(-7, 10, -1, false));
        return new LinkerSnapshot(networks, Optional.of(line), false, LinkerTabs.of(ResourceType.ITEM),
                LinkerTabs.available(LoadedTypes.LIST),
                LinkerMode.AREA,
                Optional.of(new BlockPos(-8, 64, -6)), Optional.of(new BlockPos(21, 66, 13)), false, dots.size(), 5, 1,
                List.copyOf(dots), LinkerProblem.NONE, 262_144, 64, Optional.empty());
    }
}
