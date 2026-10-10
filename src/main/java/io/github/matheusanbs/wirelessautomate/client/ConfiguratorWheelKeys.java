package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Tecla da roda do Configurador (padrão Alt esquerdo): ao apertar, com o Configurador na mão
 * principal e sem tela aberta, abre a {@link ConfiguratorWheelScreen}. Soltar a tecla na tela escolhe
 * e fecha. O tick só lê {@code consumeClick}.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class ConfiguratorWheelKeys {
    public static final KeyMapping WHEEL = new KeyMapping("key.wirelessautomate.configurator_wheel",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.wirelessautomate");

    private ConfiguratorWheelKeys() {
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (WHEEL.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null
                    || !minecraft.player.getMainHandItem().is(ModItems.CONFIGURATOR.get())) {
                continue;
            }
            minecraft.setScreen(ConfiguratorWheelScreen.fromKey());
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
            event.register(WHEEL);
        }
    }
}
