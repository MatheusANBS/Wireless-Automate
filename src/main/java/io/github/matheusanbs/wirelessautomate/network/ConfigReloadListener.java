package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Config do servidor recarregada: vazão e alcance dos tiers são lidos na montagem, então tudo é
 * remontado. O FML põe a classe no barramento do mod sozinho, porque o evento é {@code IModBusEvent}.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class ConfigReloadListener {
    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            // Pode vir da thread que observa o arquivo: só marca, o tick remonta.
            NetworkManager.configChanged();
        }
    }

    private ConfigReloadListener() {
    }
}
