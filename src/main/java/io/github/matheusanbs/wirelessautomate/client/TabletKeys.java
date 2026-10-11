package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.net.PacketDistributor;
import io.github.matheusanbs.wirelessautomate.packet.OpenTabletPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Tecla de atalho do Tablet de rede: abre a tela se houver um Tablet em qualquer lugar do
 * inventário. Vem sem tecla (o ATM10 tem centenas de atalhos; o jogador escolhe uma em Controles,
 * categoria Wireless Automate), só vale no jogo, sem tela aberta, e o servidor confere o Tablet de novo.
 */
@Mod.EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class TabletKeys {
    public static final KeyMapping OPEN_TABLET = new KeyMapping("key.wirelessautomate.open_tablet",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.wirelessautomate");

    private TabletKeys() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_TABLET.consumeClick()) {
            pressed(minecraft);
        }
    }

    /** Um aperto da tecla (o e2e chama direto, porque a tecla vem sem atalho). */
    static void pressed(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!TabletMenu.hasTablet(minecraft.player)) {
            minecraft.player.displayClientMessage(Component.translatable("key.wirelessautomate.open_tablet.missing"),
                    true);
            return;
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null && PacketDistributor.hasChannel(connection.getConnection(), OpenTabletPayload.TYPE)) {
            PacketDistributor.sendToServer(OpenTabletPayload.INSTANCE);
        }
    }

    /** Registro da tecla, no barramento do mod. */
    @Mod.EventBusSubscriber(modid = WirelessAutomate.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(OPEN_TABLET);
        }
    }
}
