package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Tecla da roda do Configurador (padrão Alt esquerdo): ao apertar, com o Configurador na mão
 * principal e sem tela aberta, abre a {@link ConfiguratorWheelScreen}. Soltar a tecla na tela escolhe
 * e fecha. O tick só lê {@code consumeClick}.
 */
@Mod.EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class ConfiguratorWheelKeys {
    public static final KeyMapping WHEEL = new KeyMapping("key.wirelessautomate.configurator_wheel",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.wirelessautomate");

    private ConfiguratorWheelKeys() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
    @Mod.EventBusSubscriber(modid = WirelessAutomate.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(WHEEL);
        }
    }
}
