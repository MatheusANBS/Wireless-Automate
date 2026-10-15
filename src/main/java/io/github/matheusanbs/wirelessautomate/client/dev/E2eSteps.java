package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Passos comuns a várias áreas do e2e: abrir um roteador pelo clique, fechar a tela com Esc, trocar o idioma e
 * segurar ou soltar o Shift.
 */
final class E2eSteps {
    private E2eSteps() {
    }

    /** Troca o idioma do jogo e espera a recarga dos recursos. */
    static Step language(String code) {
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
    static Step shift(boolean down) {
        return new Step(down ? "segurar Shift" : "soltar Shift", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().options.keyShift.setDown(down),
                () -> Minecraft.getInstance().player.isShiftKeyDown() == down && onServer(server -> server.getPlayerList()
                        .getPlayer(Minecraft.getInstance().player.getUUID()).isShiftKeyDown() == down),
                () -> "Shift no cliente " + Minecraft.getInstance().player.isShiftKeyDown());
    }

    static Step open(String name, java.util.function.Supplier<BlockPos> pos) {
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
    static Step close(String name) {
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

    /** Aperta ou solta o Shift como o teclado; termina quando o servidor vê o jogador agachado ou não. */
    static Step shift(String name, boolean down) {
        return new Step(name, STEP_TIMEOUT_MS, () -> Minecraft.getInstance().options.keyShift.setDown(down), () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            return onServer(server -> server.getPlayerList().getPlayer(playerId).isShiftKeyDown() == down);
        }, () -> "Shift " + (down ? "apertado" : "solto"));
    }
}
