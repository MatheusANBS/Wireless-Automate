package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import io.github.matheusanbs.wirelessautomate.client.ConfiguratorWheelScreen;
import io.github.matheusanbs.wirelessautomate.client.GuiText;

/**
 * O Configurador no e2e, sem tela: copiar, a roda radial e colar a cópia em B.
 */
final class E2eConfiguratorSteps {
    private E2eConfiguratorSteps() {
    }

    /**
     * Configurador pelos cliques de verdade, sem tela: Shift + clique direito no roteador A copia (e grava a
     * máquina, um baú); a roda escolhe o tipo Itens; clique direito no roteador B, preso a outro baú, cola
     * só a aba de itens.
     */
    static void configuratorSteps(List<Step> list) {
        list.add(new Step("Configurador na mão", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CONFIGURATOR.get()));
            return null;
        }), () -> Minecraft.getInstance().player.getMainHandItem().getItem() instanceof ConfiguratorItem,
                () -> "na mão: " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(shift(true));
        list.add(new Step("Shift + clique copia A", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(routerA), Direction.UP, routerA, false);
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        }, () -> onServer(server -> serverWand(server).has(ModDataComponents.PRESET.get())
                && Blocks.CHEST.builtInRegistryHolder().key().location().equals(ConfiguratorItem.machine(serverWand(server)))),
                () -> "varinha: " + onServer(server -> String.valueOf(serverWand(server).get(ModDataComponents.PRESET.get())))));
        list.add(shift(false));
        wheelSteps(list, "", "10b-configurador-roda");
        list.add(new Step("roda: escolher Itens", STEP_TIMEOUT_MS,
                () -> wheelScreen().choose(wheelScreen().sliceOf(ResourceType.ITEM)),
                () -> onServer(server -> ConfiguratorItem.type(serverWand(server)) == ResourceType.ITEM),
                () -> "varinha: " + onServer(server -> String.valueOf(ConfiguratorItem.type(serverWand(server))))));
        list.add(close("fechar a roda"));
        list.add(new Step("clique direito cola em B", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(routerB), Direction.UP, routerB, false);
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        }, () -> onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).mode() == PortMode.EXTRACT
                && router(server, routerB).face(ResourceType.ITEM, Direction.UP).filter().isEmpty()),
                () -> "B: " + onServer(server -> router(server, routerB).face(ResourceType.ITEM, Direction.UP).toString())));
    }

    private static ConfiguratorWheelScreen wheelScreen() {
        return (ConfiguratorWheelScreen) Minecraft.getInstance().screen;
    }

    /**
     * Roda do Configurador aberta direto (sem a tecla, então ela não fecha por a tecla estar solta),
     * com o mouse na fatia do tipo Itens: o centro mostra o nome e a descrição dela.
     * Confere que nenhum texto da roda fica cortado e fotografa.
     */
    static void wheelSteps(List<Step> list, String label, String file) {
        list.add(new Step(label + "abrir a roda do Configurador", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().setScreen(new ConfiguratorWheelScreen()),
                () -> Minecraft.getInstance().screen instanceof ConfiguratorWheelScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step(label + "roda: mouse no tipo Itens", STEP_TIMEOUT_MS, () -> {
            int[] point = wheelScreen().pointOf(wheelScreen().sliceOf(ResourceType.ITEM));
            moveMouse(point[0], point[1]);
            wheelScreen().previewHover(wheelScreen().sliceOf(ResourceType.ITEM));
        }, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            var window = minecraft.getWindow();
            double x = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
            double y = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
            return wheelScreen().sliceOf(ResourceType.ITEM) == wheelScreen().sliceAt(x, y);
        }, () -> "fatia sob o mouse"));
        list.add(DevEndToEnd.wait(label + "abertura da roda", 6));
        list.add(new Step(label + "roda: nenhum texto cortado", STEP_TIMEOUT_MS, () -> {
        }, () -> GuiText.clipCount() == 0, () -> GuiText.clipCount() + " texto(s) cortado(s) na roda"));
        list.add(capture(file));
    }

    private static ItemStack serverWand(MinecraftServer server) {
        return server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID()).getMainHandItem();
    }
}
