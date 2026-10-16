package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import org.lwjgl.glfw.GLFW;

/**
 * O Cartão de Upgrade com origem no inventário: o tooltip e, com o JEI, as telas de receitas dele. Roda no e2e
 * e na vitrine.
 */
final class E2eUpgradeCardSteps {
    private E2eUpgradeCardSteps() {
    }

    /**
     * Cartão de Upgrade com origem: o tooltip do cartão Básico → Elite no inventário ({@code cartao-1-tooltip}) e,
     * com o JEI, a tela de receitas dele pela tecla do JEI (R) com o mouse em cima: o cartão Avançado → Elite (o
     * cartão de um degrau, {@code cartao-2-jei-um-degrau}), o roteador Elite (a receita direta e o par bloco +
     * cartão, {@code cartao-3-jei-direta}) e o Básico → Elite (o cartão que pula, {@code cartao-4-jei-pula}). Se a
     * tela do JEI não abrir, o passo anota no log e segue sem a captura.
     */
    static void upgradeCardSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        ItemStack[] shown = {TierCoreItem.card(RouterTier.ELITE, RouterTier.BASIC),
                TierCoreItem.card(RouterTier.ELITE, RouterTier.ADVANCED),
                RouterBlockItem.withTier(ModItems.ROUTER.get(), RouterTier.ELITE)};
        list.add(new Step("cartão: no inventário", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // Em sobrevivência: no criativo o inventário abre a tela criativa, na aba que estava.
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            for (int i = 0; i < shown.length; i++) {
                player.getInventory().setItem(9 + i, shown[i].copy());
            }
            return null;
        }), () -> !Minecraft.getInstance().player.isCreative()
                && Minecraft.getInstance().player.getInventory().getItem(9).getItem() instanceof TierCoreItem
                && TierCoreItem.origin(Minecraft.getInstance().player.getInventory().getItem(9)) == RouterTier.BASIC,
                () -> "slot 9 " + Minecraft.getInstance().player.getInventory().getItem(9)));
        list.add(new Step("cartão: abrir o inventário", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player)),
                () -> Minecraft.getInstance().screen instanceof InventoryScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        String[] files = {"cartao-4-jei-pula", "cartao-2-jei-um-degrau", "cartao-3-jei-direta"};
        list.add(hoverCapture("cartao-1-tooltip", 0, () -> Minecraft.getInstance().screen instanceof InventoryScreen));
        if (!ModList.get().isLoaded("jei")) {
            files = new String[0];
        }
        for (int i = 0; i < files.length; i++) {
            int index = i;
            String file = files[i];
            boolean[] opened = new boolean[1];
            list.add(new Step("cartão: receitas do JEI (" + file + ")", STEP_TIMEOUT_MS, () -> {
                if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) {
                    Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
                }
            }, () -> {
                Screen screen = Minecraft.getInstance().screen;
                if (screen instanceof InventoryScreen inventory) {
                    if (stepTicks < 4) {
                        return false;
                    }
                    hoverSlot(inventory, index);
                    if (stepTicks == 8) {
                        // A tecla do JEI passa pelo teclado (o JEI escuta o evento da tela, não o keyPressed).
                        var keyboard = Minecraft.getInstance().keyboardHandler;
                        long window = Minecraft.getInstance().getWindow().getWindow();
                        keyboard.keyPress(window, GLFW.GLFW_KEY_R, 0, GLFW.GLFW_PRESS, 0);
                        keyboard.keyPress(window, GLFW.GLFW_KEY_R, 0, GLFW.GLFW_RELEASE, 0);
                    }
                    if (stepTicks > 60) {
                        record("cartão: a tela do JEI não abriu para " + file, "sem captura");
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
                        quietShowcase();
                        return false;
                    }
                    capture(Minecraft.getInstance(), file);
                    Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
                    return true;
                }
                return false;
            }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        }
        list.add(close("cartão: fechar o inventário"));
        list.add(new Step("cartão: limpar o inventário", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            for (int i = 0; i < shown.length; i++) {
                player.getInventory().setItem(9 + i, ItemStack.EMPTY);
            }
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            return null;
        }), () -> Minecraft.getInstance().player.getInventory().getItem(9).isEmpty()
                && Minecraft.getInstance().player.isCreative(), () -> "slot 9"));
    }

    /** Na vitrine, tira os avisos (receitas desbloqueadas) e o chat de cima das telas antes de uma captura. */
    static void quietShowcase() {
        if (SHOWCASE != null) {
            Minecraft.getInstance().getToasts().clear();
            Minecraft.getInstance().gui.getChat().clearMessages(false);
        }
    }

    /** O mouse sobre o slot {@code 9 + index} do inventário (a primeira linha de cima). */
    static void hoverSlot(InventoryScreen screen, int index) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory && slot.getContainerSlot() == 9 + index) {
                moveMouse(screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8);
                return;
            }
        }
    }

    /** Põe o mouse sobre o slot {@code 9 + index} e, alguns quadros depois, salva a captura com o tooltip. */
    private static Step hoverCapture(String file, int index, java.util.function.BooleanSupplier ready) {
        return new Step("captura " + file, STEP_TIMEOUT_MS, () -> { }, () -> {
            if (!ready.getAsBoolean() || !(Minecraft.getInstance().screen instanceof InventoryScreen screen)) {
                return false;
            }
            hoverSlot(screen, index);
            if (stepTicks < 8) {
                quietShowcase();
                return false;
            }
            capture(Minecraft.getInstance(), file);
            return true;
        }, () -> file + ".png");
    }
}
