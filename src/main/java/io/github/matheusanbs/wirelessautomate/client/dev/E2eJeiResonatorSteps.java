package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

/**
 * As telas do JEI do Ressonador: a recarga no Núcleo (R no cristal Opaco) e a informação dos Núcleos (U no Núcleo).
 * Só roda com o JEI; sem ele, nada. Roda no e2e.
 */
final class E2eJeiResonatorSteps {
    /** A aba Informações do JEI na tela de usos do Núcleo (a segunda, depois das Tags), na captura de 1280 × 800. */
    private static final int INFO_TAB_X = 517;
    private static final int INFO_TAB_Y = 70;

    private E2eJeiResonatorSteps() {
    }

    /**
     * Põe um cristal Opaco e um Núcleo Básico no inventário e abre as telas do JEI com o mouse em cima:
     * {@code ressonador-jei-recarga} e {@code ressonador-jei-info}. Se a tela do JEI não abrir, o passo anota no
     * log e segue sem a captura.
     */
    static void jeiResonatorSteps(List<Step> list) {
        if (!ModList.get().isLoaded("jei")) {
            return;
        }
        UUID playerId = Minecraft.getInstance().player.getUUID();
        ItemStack dull = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(dull, 0);
        ItemStack[] shown = {dull, ResonanceCoreItem.withTier(ModItems.RESONANCE_CORE.get(), RouterTier.BASIC)};
        list.add(new Step("jei ressonador: no inventário", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setGameMode(GameType.SURVIVAL);
            for (int i = 0; i < shown.length; i++) {
                player.getInventory().setItem(9 + i, shown[i].copy());
            }
            return null;
        }), () -> !Minecraft.getInstance().player.isCreative()
                && Minecraft.getInstance().player.getInventory().getItem(9).getItem() instanceof SignalCrystalItem,
                () -> "slot 9 " + Minecraft.getInstance().player.getInventory().getItem(9)));
        list.add(new Step("jei ressonador: abrir o inventário", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player)),
                () -> Minecraft.getInstance().screen instanceof InventoryScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(jeiCapture("ressonador-jei-recarga", 0, GLFW.GLFW_KEY_R, 0, 0));
        list.add(jeiCapture("ressonador-jei-info", 1, GLFW.GLFW_KEY_U, INFO_TAB_X, INFO_TAB_Y));
        list.add(close("jei ressonador: fechar o inventário"));
        list.add(new Step("jei ressonador: limpar o inventário", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            for (int i = 0; i < shown.length; i++) {
                player.getInventory().setItem(9 + i, ItemStack.EMPTY);
            }
            player.setGameMode(GameType.CREATIVE);
            return null;
        }), () -> Minecraft.getInstance().player.getInventory().getItem(9).isEmpty()
                && Minecraft.getInstance().player.isCreative(), () -> "slot 9"));
    }

    /**
     * O mouse no slot {@code 9 + index}, a tecla do JEI e, com a tela dele aberta, a captura. Com {@code tabX} e
     * {@code tabY} (pixels da captura de 1280 × 800), clica antes nessa aba do JEI.
     */
    private static Step jeiCapture(String file, int index, int key, int tabX, int tabY) {
        boolean[] opened = new boolean[1];
        int[] clickedAt = new int[1];
        return new Step("jei ressonador: " + file, STEP_TIMEOUT_MS, () -> {
            opened[0] = false;
            clickedAt[0] = 0;
            if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) {
                Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
            }
        }, () -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof InventoryScreen inventory) {
                if (stepTicks < 4) {
                    return false;
                }
                E2eUpgradeCardSteps.hoverSlot(inventory, index);
                if (stepTicks == 8) {
                    var keyboard = Minecraft.getInstance().keyboardHandler;
                    long window = Minecraft.getInstance().getWindow().getWindow();
                    keyboard.keyPress(window, key, 0, GLFW.GLFW_PRESS, 0);
                    keyboard.keyPress(window, key, 0, GLFW.GLFW_RELEASE, 0);
                }
                if (stepTicks > 60) {
                    record("jei ressonador: a tela do JEI não abriu para " + file, "sem captura");
                    return true;
                }
                return false;
            }
            if (screen != null && screen.getClass().getName().startsWith("mezz.jei")) {
                if (!opened[0]) {
                    opened[0] = true;
                    moveMouse(0, 0);
                }
                if (stepTicks < 20) {
                    E2eUpgradeCardSteps.quietShowcase();
                    return false;
                }
                if (tabX > 0 && clickedAt[0] == 0) {
                    clickJei(tabX, tabY);
                    clickedAt[0] = stepTicks;
                }
                if (clickedAt[0] > 0 && stepTicks < clickedAt[0] + 15) {
                    return false;
                }
                capture(Minecraft.getInstance(), file);
                Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
                return true;
            }
            return false;
        }, () -> "tela " + describe(Minecraft.getInstance().screen));
    }

    /** Clique do mouse de verdade (o JEI escuta o evento do mouse, não o {@code mouseClicked} da tela). */
    private static void clickJei(int pixelX, int pixelY) {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        moveMouse(pixelX * window.getGuiScaledWidth() / 1280, pixelY * window.getGuiScaledHeight() / 800);
        try {
            var press = minecraft.mouseHandler.getClass().getDeclaredMethod("onPress", long.class, int.class,
                    int.class, int.class);
            press.setAccessible(true);
            press.invoke(minecraft.mouseHandler, window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
            press.invoke(minecraft.mouseHandler, window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
        } catch (ReflectiveOperationException e) {
            record("jei ressonador: clique na aba falhou", e.toString());
        }
    }
}
