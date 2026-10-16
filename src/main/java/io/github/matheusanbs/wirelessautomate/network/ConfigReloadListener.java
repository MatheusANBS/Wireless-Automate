package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.storage.SourceTankLevels;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Config do servidor carregada (migração do balanceamento) ou recarregada: vazão e alcance dos tiers são
 * lidos na montagem, então tudo é remontado. O FML põe a classe no barramento do mod sozinho, porque o evento é {@code IModBusEvent}.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class ConfigReloadListener {
    private static final AtomicInteger FILE_RELOADS = new AtomicInteger();

    /**
     * Quantas recargas vieram do arquivo, pela thread que o observa (gravar a config também recarrega,
     * na hora, na thread de quem grava, e essas não contam). A do arquivo chega um tempo depois da
     * gravação; um GameTest que grava espera por ela, senão ela remonta as redes no meio de outro teste.
     */
    public static int fileReloads() {
        return FILE_RELOADS.get();
    }

    /** Primeira carga da config do mundo: aplica o balanceamento novo aos valores ainda no padrão antigo. */
    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            int changed = Config.migrateBalance();
            if (changed > 0) {
                WirelessAutomate.LOGGER.info("Config: {} valores de vazão, alcance e capacidade passaram para o "
                        + "balanceamento novo (os que estavam no padrão antigo)", changed);
            }
        }
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            // Pode vir da thread que observa o arquivo: só marca, o tick remonta.
            NetworkManager.configChanged();
            SourceTankLevels.configChanged(); // os níveis dos Tanques de Source seguem a capacidade da config
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || !server.isSameThread()) {
                FILE_RELOADS.incrementAndGet();
            }
        }
    }

    private ConfigReloadListener() {
    }
}
