package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.packet.OpenTabletPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Tecla de atalho do Tablet de rede: abre a tela se houver um Tablet em qualquer lugar do
 * inventário. Vem sem tecla (o ATM10 tem centenas de atalhos; o jogador escolhe uma em Controles,
 * categoria Wireless Automate), só vale no jogo, sem tela aberta, e o servidor confere o Tablet de novo.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class TabletKeys {
    public static final KeyMapping OPEN_TABLET = new KeyMapping("key.wirelessautomate.open_tablet",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.wirelessautomate");

    private TabletKeys() {
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_TABLET.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null) {
                continue;
            }
            if (!TabletMenu.hasTablet(minecraft.player)) {
                minecraft.player.displayClientMessage(Component.translatable("key.wirelessautomate.open_tablet.missing"),
                        true);
                continue;
            }
            ClientPacketListener connection = minecraft.getConnection();
            if (connection != null && connection.hasChannel(OpenTabletPayload.TYPE)) {
                PacketDistributor.sendToServer(OpenTabletPayload.INSTANCE);
            }
        }
    }

    /** Registro da tecla, no barramento do mod. */
    // bus explícito: o FML já o deduz do evento e marcou o atributo para remoção, mas aqui ele deixa claro
    @SuppressWarnings("removal")
    @EventBusSubscriber(modid = WirelessAutomate.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        static void register(RegisterKeyMappingsEvent event) {
            event.register(OPEN_TABLET);
        }
    }
}
